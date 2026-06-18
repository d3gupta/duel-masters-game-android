# Duel Masters Game (Android)

A Duel Masters card game for Android with local and online multiplayer support.

## Stack

| Layer | Technology |
|-------|------------|
| **Android UI** | Kotlin, Jetpack Compose, Material 3 |
| **Local storage** | Room Database |
| **Backend** | Kotlin, Ktor |
| **Online database** | PostgreSQL |

## Project structure

```
duel-masters-game-android/
├── app/          # Android client (Compose + Room)
├── backend/      # Ktor server for online multiplayer
└── docs/         # Architecture and setup notes
```

## Prerequisites

- **Android Studio** (Ladybug or newer recommended)
- **JDK 17+**
- **PostgreSQL 15+** (for online multiplayer backend)
- **Gradle 8.9+** (wrapper included)

## Getting started

### Android app

1. Open this repository in Android Studio.
2. Sync Gradle when prompted.
3. Run the `app` configuration on an emulator or device.

### Backend (Ktor)

1. Start PostgreSQL and create a database:

```sql
CREATE DATABASE duel_masters;
```

2. Copy the backend env template and set your connection details:

```bash
cp backend/.env.example backend/.env
```

3. Run the server:

```bash
./gradlew :backend:run
```

The server starts on `http://localhost:8080` by default.

## Development roadmap

- [ ] Core card game rules engine
- [ ] Local single-player vs AI
- [ ] Room persistence for decks and match history
- [ ] Online matchmaking and real-time sync via Ktor WebSockets
- [ ] PostgreSQL-backed player accounts and rankings

## License

MIT
