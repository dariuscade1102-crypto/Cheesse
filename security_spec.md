# Cheesse Firestore Security Specification

## 1. Data Invariants
1. **User-Owned Analyzed Games (`/users/{userId}/games/{gameId}`)**:
   - Every game record belongs strictly to `userId` (`request.auth.uid == userId` and `data.userId == request.auth.uid`).
   - Required fields (`userId`, `title`, `whitePlayer`, `blackPlayer`, `pgn`, `finalFen`, `whiteAccuracy`, `blackAccuracy`, `whiteAvgCpl`, `blackAvgCpl`, `mistakeCount`, `blunderCount`, `analyzed`, `createdAt`, `updatedAt`) are strictly type-checked and size-bounded.
   - `userId` and `createdAt` are immutable on update.
2. **User-Owned Preferences (`/users/{userId}/settings/{settingId}`)**:
   - Synced engine and board preferences belong strictly to `userId` (`request.auth.uid == userId` and `data.userId == request.auth.uid`).
   - `userId` and `createdAt` are immutable on update.
3. **Live Friend Challenges (`/challenges/{challengeId}`)**:
   - Created by an authenticated host (`incoming().hostUid == request.auth.uid`) in `'open'` status.
   - Readable (`get`, `list`) only if the room is `'open'` or the caller is `hostUid` or `guestUid`.
   - Terminal State Lock: Once `existing().status == 'completed'`, no further updates are permitted.
   - Action-based updates enforce strict `affectedKeys().hasOnly(...)` for joining an open challenge or playing moves in an active challenge.

## 2. The Dirty Dozen Payloads
1. **Unauthenticated Read**: Reading `/users/alice/games` without auth -> REJECTED.
2. **Cross-User Game Read**: Bob reading `/users/alice/games/g1` -> REJECTED.
3. **Identity Spoofing on Create**: Alice creating `/users/alice/games/g1` with `userId: "bob"` -> REJECTED.
4. **Shadow/Ghost Field Injection**: Adding `isAdmin: true` to `/users/alice/games/g1` -> REJECTED via `hasOnly`.
5. **Immutable Field Mutation**: Changing `createdAt` or `userId` during game update -> REJECTED.
6. **Oversized Payload (Denial of Wallet)**: Sending a 50KB `title` string -> REJECTED via `.size() <= 200`.
7. **Invalid Path ID Poisoning**: Creating a document with special characters in ID -> REJECTED via `isValidId()`.
8. **Unfiltered List Query on Private Challenges**: Querying `/challenges` without matching `status == 'open'` or participant filter -> REJECTED.
9. **Terminal State Bypass**: Updating a challenge where `existing().status == 'completed'` -> REJECTED.
10. **Unauthorized Challenge Move**: A third user (`charlie`) attempting to update moves in an active challenge between `alice` and `bob` -> REJECTED.
11. **Value Poisoning on Update**: Updating `whiteAccuracy` with a string instead of int/number -> REJECTED via `isValidAnalyzedGame(incoming())`.
12. **Future Timestamp Spoofing**: Writing `createdAt` in the future (`> request.time`) -> REJECTED.
