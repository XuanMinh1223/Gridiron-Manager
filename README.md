# Gridiron Manager

Welcome to **Gridiron Manager**, a Kotlin Multiplatform (KMP) American football management and simulation game targeting Android, iOS, and Desktop (JVM) using Compose Multiplatform.

> [!NOTE]
> **For AI Agents & LLMs**: This README serves as the primary system reference for understanding the project's domain model, simulation math, architectural boundaries, and testing patterns. Read sections 3 through 7 carefully before proposing code modifications.
>
> **For Human Developers**: This guide provides onboarding instructions, core architectural breakdowns, build commands, and testing workflows to help you navigate and extend the codebase efficiently.

---

## 1. Quick Start & Running the Apps

Use the run configurations provided by the run widget in your IDE's toolbar, or run the following Gradle commands:

- **Android app**: `./gradlew :androidApp:assembleDebug`
- **Desktop app (JVM)**:
  - Hot reload: `./gradlew :desktopApp:hotRun --auto`
  - Standard run: `./gradlew :desktopApp:run`
- **iOS app**: Open the [/iosApp](./iosApp) directory in Xcode and run it from there.
- **Run All Tests**: `./gradlew :shared:allTests`

---

## 2. Project Architecture & Codebase Layout

Gridiron Manager is structured as a Kotlin Multiplatform project with shared business logic and platform-specific entry points:

```
[Root Directory]
├── shared/                   # KMP Shared Source Code
│   ├── src/commonMain/       # Shared business logic, domain models, simulation & UI
│   ├── src/commonTest/       # Shared unit & integration tests
│   ├── src/androidMain/      # Android-specific platform implementations
│   ├── src/iosMain/          # iOS platform-specific implementations & SwiftUI bridge
│   └── src/jvmMain/          # Desktop JVM platform entry points
├── androidApp/               # Android Application Module
├── desktopApp/               # Desktop Application Module (JVM)
└── iosApp/                   # iOS Application Module (Xcode / SwiftUI)
```

- [/iosApp](./iosApp/iosApp) contains the iOS application entry point and SwiftUI code.
- [/shared](./shared/src) contains core code shared across all Compose Multiplatform targets (`commonMain`, `iosMain`, `jvmMain`, `androidMain`).

---

## 3. Core Domain & Simulation Reference

All simulation and rule logic lives in `shared/src/commonMain/kotlin/com/xuan/gridironmanager/domain/`.

### A. Match Engine (`MatchEngine.kt`)
- **Responsibility**: Resolves macro play outcomes (`RUN`, `PASS`, `SPECIAL_TEAMS`) based on offensive and defensive roster attribute clashes.
- **Run Mechanics**: Compares offensive RB speed/strength vs. defensive LB strength/tackle.
- **Pass Mechanics**: Evaluates pass rush success (DL vs. QB) and pass coverage success (WR route running/speed vs. CB speed/awareness).

### B. Drive Engine (`DriveEngine.kt`)
- **Responsibility**: Manages American football game rules and state transitions.
- **Key Rules**: Down and distance tracking, 1st down conversions, 4th down turnover on downs, yard line progression (0-100), touchdowns (7 points including XP), touchbacks (25 yard line after kickoffs, 20 after punts), and quarter clock countdowns.

### C. Movement & Physics (`MovementEngine.kt`, `BallTrajectory.kt`)
- **Responsibility**: Real-time spatial tracking during plays using `Vector3D`.
- **MovementEngine**: Moves players along `Route` waypoints based on player speed (`speedYdsPerSec`) and tick delta time.

### D. AI Decision Making (`QbBrain.kt`)
- **Responsibility**: Quarterback decision AI for selecting target receivers and reading defensive coverages during passing plays.

---

## 4. Data Models (`domain/model/`)

- **`Team` & `Player`**: Represent rosters, player positions (`QB`, `RB`, `WR`, `DL`, `LB`, `CB`, etc.), and attributes (speed, strength, tackle, route running, awareness).
- **`GameState`**: Represents the current state of a match (home/away score, yard line, down, distance, quarter, clock, possession).
- **`CareerState`**: Manages career/franchise progress.
- **`Route` & `Vector3D`**: Defines player running paths and 3D field coordinates.

---

## 5. UI & Presentation Layer (`ui/` & `presentation/`)

Built with Compose Multiplatform for shared reactive UI across Android, iOS, and Desktop:

- **Screens**: `DashboardScreen`, `LiveGameScreen`, `MatchScreen`, `RosterScreen`.
- **State & Presentation**: `GameSimViewModel`, `MatchPresenter`, and `MatchUiState`.
- **Rendering**: `FieldCanvas` for live 2D/3D representation of player coordinates on the football field.

---

## 6. Testing Strategy (`shared/src/commonTest/`)

The project maintains a rigorous suite of common Kotlin tests:
- `DriveEngineTest`: Verifies down/distance logic, scoring, turnovers, special teams.
- `MatchEngineIntegrationTest`: Tests end-to-end play resolution and match flow.
- `RouteRunnerTest`: Validates player movement along waypoint routes.
- `MatchSimulationTest` & `RosterPresenterTest`: UI and presenter state verification.

---

## 7. Learn More

- Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html).
