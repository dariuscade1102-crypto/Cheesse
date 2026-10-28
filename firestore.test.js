const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_1";
const BOB_UID = "bob_uid_2";
const CHARLIE_UID = "charlie_uid_3";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function validGamePayload(userId) {
  return {
    userId,
    title: "Sicilian Najdorf Analysis",
    whitePlayer: "AliceChess",
    blackPlayer: "GrandmasterBot",
    pgn: "1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6",
    finalFen: "rnbqkb1r/1p2pppp/p2p1n2/8/3NP3/2N5/PPP2PPP/R1BQKB1R w KQkq - 0 6",
    whiteAccuracy: 91,
    blackAccuracy: 88,
    whiteAvgCpl: 18,
    blackAvgCpl: 24,
    mistakeCount: 1,
    blunderCount: 0,
    analyzed: true,
    createdAt: new Date(Date.now() - 1000),
    updatedAt: new Date(Date.now() - 1000),
  };
}

function validChallengePayload(hostUid) {
  return {
    inviteCode: "CHESS9",
    hostUid,
    hostName: "Alice",
    guestUid: "",
    guestName: "",
    currentFen: "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
    movesUci: "",
    lastMoveStrength: "Book",
    status: "open",
    createdAt: new Date(Date.now() - 1000),
    updatedAt: new Date(Date.now() - 1000),
  };
}

function validLeaderboardPayload(userId) {
  return {
    userId,
    username: "AliceTactics",
    titleBadge: "IM",
    rating: 2140,
    accuracyScore: 93.4,
    gamesWon: 42,
    gamesPlayed: 55,
    winStreak: 5,
    playingStyle: "TACTICAL_AGGRESSIVE",
    visibility: "public",
    createdAt: new Date(Date.now() - 1000),
    updatedAt: new Date(Date.now() - 1000),
  };
}

test("Unauthenticated user: cannot read or create games or leaderboard entries", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("games").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("games").doc("game_1").set(validGamePayload(ALICE_UID))
  );
  await assertFails(
    unauthDb.collection("leaderboard").where("visibility", "==", "public").get()
  );
  await assertFails(
    unauthDb.collection("leaderboard").doc(ALICE_UID).set(validLeaderboardPayload(ALICE_UID))
  );
});

test("Authenticated owner: can create, read, list, and update own analyzed game", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const docRef = aliceDb.collection("users").doc(ALICE_UID).collection("games").doc("game_1");
  await assertSucceeds(docRef.set(validGamePayload(ALICE_UID)));
  await assertSucceeds(docRef.get());
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("games").get());
  await assertSucceeds(
    docRef.update({
      whiteAccuracy: 95,
      analyzed: true,
      updatedAt: new Date(),
    })
  );
});

test("Cross-user isolation: Bob cannot read or modify Alice's analyzed games", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx
      .firestore()
      .collection("users")
      .doc(ALICE_UID)
      .collection("games")
      .doc("game_1")
      .set(validGamePayload(ALICE_UID));
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("games").doc("game_1").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("games").get()
  );
});

test("Leaderboard: authenticated owner can create/update own leaderboard doc, and users can query public entries", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  const aliceLbRef = aliceDb.collection("leaderboard").doc(ALICE_UID);
  await assertSucceeds(aliceLbRef.set(validLeaderboardPayload(ALICE_UID)));

  // Bob cannot overwrite Alice's leaderboard entry
  await assertFails(
    bobDb.collection("leaderboard").doc(ALICE_UID).update({
      rating: 3000,
      updatedAt: new Date(),
    })
  );

  // Bob can query public leaderboard entries with visibility == 'public'
  await assertSucceeds(
    bobDb.collection("leaderboard").where("visibility", "==", "public").get()
  );

  // Alice can update her own rating and gamesWon
  await assertSucceeds(
    aliceLbRef.update({
      rating: 2165,
      gamesWon: 43,
      gamesPlayed: 56,
      winStreak: 6,
      updatedAt: new Date(),
    })
  );
});

test("Shadow update rejection: cannot inject undeclared fields into game or leaderboard", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const badPayload = { ...validGamePayload(ALICE_UID), isAdmin: true };
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("games").doc("game_shadow").set(badPayload)
  );
  const badLbPayload = { ...validLeaderboardPayload(ALICE_UID), isTaintedByEngine: true };
  await assertFails(
    aliceDb.collection("leaderboard").doc(ALICE_UID).set(badLbPayload)
  );
});

test("Friend challenges: host creates open room, guest joins, and terminal completed lock prevents further edits", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();

  const roomRefAlice = aliceDb.collection("challenges").doc("room_1");
  await assertSucceeds(roomRefAlice.set(validChallengePayload(ALICE_UID)));

  // Query alignment: open challenges can be listed with status == 'open'
  await assertSucceeds(
    bobDb.collection("challenges").where("status", "==", "open").get()
  );

  // Guest Bob joins open challenge
  const roomRefBob = bobDb.collection("challenges").doc("room_1");
  await assertSucceeds(
    roomRefBob.update({
      guestUid: BOB_UID,
      guestName: "Bob",
      status: "active",
      updatedAt: new Date(),
    })
  );

  // Outsider Charlie cannot make a move in Alice & Bob's active game
  await assertFails(
    charlieDb.collection("challenges").doc("room_1").update({
      movesUci: "e2e4",
      updatedAt: new Date(),
    })
  );

  // Alice completes the game
  await assertSucceeds(
    roomRefAlice.update({
      movesUci: "e2e4 e7e5",
      lastMoveStrength: "Best",
      status: "completed",
      updatedAt: new Date(),
    })
  );

  // Terminal state lock: neither Alice nor Bob can mutate a completed challenge
  await assertFails(
    roomRefBob.update({
      movesUci: "e2e4 e7e5 g1f3",
      updatedAt: new Date(),
    })
  );
});
