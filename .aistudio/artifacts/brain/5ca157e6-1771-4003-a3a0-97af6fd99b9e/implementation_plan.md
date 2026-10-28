# Cheesse — ChessSoundManager (PCM Wooden Audio), Unified ChessBoard, Live Clocks & UCI Service

**Cheesse** is a distraction-free chess analysis, game review, training, and live challenge workbench built with Jetpack Compose. This update introduces a dedicated **`ChessSoundManager`** that synthesizes low-latency wooden board **`move`** and **`capture`** audio effects via Android `AudioTrack` 16-bit PCM synthesis and triggers them across live moves, drag-and-drop placement, undo/redo navigation, and Board Editor piece placement.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> The following architectural and UX decisions incorporate your confirmed choices for the `ChessSoundManager`, `ChessBoard` Jetpack Compose component, synchronized move timers, bound UCI Engine Service, and zero-login startup flow:

- **Confirmed Decision 1 — Synthesized Wooden Board Clicks via `AudioTrack` PCM**: `ChessSoundManager` synthesizes 16-bit mono PCM waveforms in memory (`22,050 Hz`) to produce a warm resonant wooden tap for **`move`** and a crisp dual-transient wood-on-wood snap for **`capture`**, played asynchronously via Android `AudioTrack` with zero external asset overhead.
- **Confirmed Decision 2 — Comprehensive Board Action Triggers**: Audio effects trigger automatically when pieces are placed during live board moves (tap-to-move, drag-and-drop, engine replies, autoplay), **Undo / Redo** transitions (`undoMove`, `redoMove`), and **Board Editor** piece placement (`onEditorSquareTapped`), while respecting the global `Sound` preference toggle.
- **Confirmed Decision 3 — Unified `ChessBoard` Layout + Synchronized Clocks & Undo/Redo**: The `ChessBoard` component frames the interactive 8×8 canvas with a live **Engine Status Header** above the board (featuring synchronized White & Black countdown clocks, clock pause/reset, and Undo/Redo buttons) and a scrollable **Move Notation Strip** below the board.
- **Confirmed Decision 4 — Bound Android UCI Engine Service & Direct Launch (No Sign-In)**: The app binds `UciEngineService` on launch and opens directly into the active `ChessBoard` workbench with local Room SQLite persistence and no login screen.

---

## 1. Overview & Core Concept

- **What It Does**: Provides tactile acoustic feedback for every piece placement and capture on the board through a self-contained `ChessSoundManager`, paired with the interactive `ChessBoard` Jetpack Compose UI component, synchronized player countdown clocks, Undo/Redo state restoration, and bound UCI engine evaluations.
- **Target Audience / Persona**: Tournament players, tactical analysts, and chess learners who rely on immediate auditory confirmation of moves, captures, and ply scrubbing during analysis and play.
- **Key Value**: Delivers responsive, natural-sounding wooden chess piece audio cues (`move` and `capture`) across all board interactions without latency or missing audio files.

---

## 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Live Move & Capture Audio Feedback**:
     - When a player drags and drops or taps a piece onto an empty legal square, `ChessSoundManager.playMove()` triggers a warm, short wooden tap (`~45 ms` damped low-mid acoustic transient).
     - When a move captures an opponent's piece (or removes/replaces a piece), `ChessSoundManager.playCapture()` triggers a sharper, punchier dual-impact wooden snap (`~65 ms` layered transient + resonant body).
  2. **Undo, Redo & Board Editor Audio Feedback**:
     - Tapping **Undo** (`↶`) or **Redo** (`↷`) in the Engine Status Header or Move Notation Strip restores the target ply, synchronizes both players' countdown clocks, and triggers `playCapture()` if the transitioned move was a capture or `playMove()` otherwise.
     - Tapping squares in the **Board Editor** triggers `playMove()` when placing a piece on a square and `playCapture()` when erasing or replacing an existing piece.
  3. **Sound Toggle & Audition Controls**:
     - Users can toggle sound effects on/off from the Settings screen or directly test the `Move` and `Capture` sounds, while `ChessSoundManager` tracks the last triggered sound effect (`MOVE` or `CAPTURE`) for UI feedback and deterministic testing.

- **Visual Identity & Theme**:
  - *Aesthetic Direction*: **Tactical Slate & Tournament Precision** (`#0E1318` Obsidian Slate background, `#161E26` elevated panels, `#10B981` Best-Move Emerald, `#38BDF8` Tactical Cyan).
  - *Typography & Hierarchy*: `Space Grotesk` for headers and player banners; `Roboto Mono` for countdown clocks, algebraic/figurine notation, and UCI engine telemetry.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Procedural 16-Bit PCM Synthesis via Android `AudioTrack`**
  - *Chosen Approach*: Pre-compute two compact 16-bit PCM sample buffers (`movePcmBuffer` and `capturePcmBuffer` at `22,050 Hz`) inside `ChessSoundManager`:
    - **Move Sound (`MOVE`)**: Exponentially decaying fundamental wood resonance (`185 Hz` + `370 Hz` harmonic + subtle initial click burst) modeling a weighted wooden piece settling on a tournament board.
    - **Capture Sound (`CAPTURE`)**: Two-stage percussive transient (`12 ms` initial piece-strike clack followed by a higher-energy `240 Hz` + `520 Hz` resonant wood thud and filtered impact noise) modeling a decisive capture.
  - *Why*: Guarantees crisp, pleasant wooden chess acoustics rather than harsh robotic beeps (`ToneGenerator`), requires zero external `.wav`/`.mp3` files, and executes reliably on both physical devices and local JVM/Robolectric tests.
  - *Alternatives Considered*: `ToneGenerator` (produces harsh telecom beeps) or external raw media files (requires binary asset management and asynchronous decoder setup).

- **Decision 2: Centralized Sound Dispatch in `CheesseViewModel` via `ChessSoundManager`**
  - *Chosen Approach*: Integrate `ChessSoundManager` into `CheesseViewModel` so `executeMoveOnBoard`, `executeEngineReply`, `undoMove`, `redoMove`, and `onEditorSquareTapped` automatically inspect whether a capture occurred and trigger `playCapture()` or `playMove()` whenever `soundEnabled` is true.
  - *Why*: Ensures consistent sound behavior across drag-and-drop, tap-to-move, engine responses, Undo/Redo buttons, and Board Editor interactions.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

### Architecture & Component Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       MainActivity (Zero Sign-In Gate)                      │
│     Binds UciEngineService on start -> Launches CheesseStudioScreen         │
└───────────────────┬─────────────────────────────────────┬───────────────────┘
                    │ IBinder                             │ StateFlow<CheesseUiState>
                    ▼                                     ▼
┌───────────────────────────────────────┐ ┌───────────────────────────────────┐
│      UciEngineService (Android)       │ │         CheesseViewModel          │
│ ───────────────────────────────────── │ │ ───────────────────────────────── │
│ • sendUciCommand(uci/isready/go/stop) │◄┼─► • Syncs Clocks & Move History   │
│ • evaluatePosition / evaluateMove     │ │   • Handles Drag-and-Drop & Tap   │
│ • serviceState: Flow<UciServiceState> │ │   • Executes Undo / Redo          │
└───────────────────────────────────────┘ │   • Dispatches Audio Events       │
                                          └───────┬───────────────────┬───────┘
                                                  │                   │
                       ┌──────────────────────────┘                   ▼
                       ▼                                ┌─────────────────────┐
┌─────────────────────────────────────────────────────┐ │  ChessSoundManager  │
│        ChessBoard Jetpack Compose Component         │ │ ─────────────────── │
│ ─────────────────────────────────────────────────── │ │ • AudioTrack PCM    │
│ • Engine Status Header + Player Countdown Clocks    │ │   22.05kHz 16-bit   │
│ • Undo (↶) & Redo (↷) Controls                      │ │ • playMove()        │
│ • Interactive 8x8 Canvas (Drag-and-Drop + Legal)    │ │ • playCapture()     │
│ • Move Notation Strip (SAN / Figurine + Scrubbing)  │ │ • playForMove(move) │
└─────────────────────────────────────────────────────┘ └─────────────────────┘
```

### Interactive Component & State Mapping

- **`ChessSoundManager` (`ChessSoundManager.kt`)**:
  - Defines `enum class ChessSoundEffect { MOVE, CAPTURE }`.
  - Pre-generates 16-bit PCM `ShortArray` buffers for `MOVE` (wooden tap) and `CAPTURE` (dual-transient capture snap).
  - Exposes `playMove(enabled: Boolean)`, `playCapture(enabled: Boolean)`, and `playForMove(move: ChessMove, enabled: Boolean)` (which checks `move.captured != null` to select `CAPTURE` vs `MOVE`).
  - Records `lastPlayedEffect: ChessSoundEffect?` and `playCount: Int` so both UI status indicators and automated Robolectric tests can verify sound triggers deterministically.
- **`CheesseViewModel` Integration**:
  - `executeMoveOnBoard(move)` & `executeEngineReply(move)`: Calls `soundManager.playForMove(move, state.soundEnabled)`.
  - `undoMove()` & `redoMove()`: Identifies the move being undone or redone and calls `soundManager.playForMove(transitionedMove, state.soundEnabled)`.
  - `onEditorSquareTapped(square)`: Calls `soundManager.playCapture(state.soundEnabled)` when erasing or overwriting an occupied square, or `soundManager.playMove(state.soundEnabled)` when placing a piece onto an empty square.
