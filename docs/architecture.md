# Architecture

## Overview

This repository is a monorepo for the Duel Masters Android game and its online multiplayer backend.

```
Android App (Compose)  <--REST/WebSocket-->  Ktor Backend  -->  PostgreSQL
        |
     Room DB (local decks, offline cache)
```

## Android client (`app/`)

- **UI**: Jetpack Compose with Material 3
- **Navigation**: Navigation Compose (to be expanded)
- **Local persistence**: Room for decks and cached game data
- **Networking**: Ktor Client for REST and WebSocket communication with the backend

## Backend (`backend/`)

- **Framework**: Ktor on Netty
- **API**: REST endpoints for accounts/matches; WebSockets for real-time duel sync
- **Database**: PostgreSQL with Flyway migrations
- **Connection pooling**: HikariCP

## Data flow (online multiplayer)

1. Player authenticates and opens a match lobby via REST.
2. Match is created in PostgreSQL with status `pending`.
3. Both clients connect to a WebSocket room for the match ID.
4. Game state updates are broadcast between clients through Ktor.
5. Final match result is persisted to PostgreSQL.

## Next implementation steps

1. Game rules engine (shared logic module or duplicated with tests)
2. Deck builder UI backed by Room
3. Matchmaking REST API
4. WebSocket protocol for turn-based actions
5. Player registration and session tokens
