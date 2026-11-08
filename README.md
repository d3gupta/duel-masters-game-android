# Duel Masters — Android

An offline-first, single-device Duel Masters card game for Android, built with Kotlin, Jetpack Compose, Material 3 and Room.

> **Implementation scope:** the app contains an expanded English card catalogue through DM-35 and promotional entries, but it does **not** implement every card effect. Only cards marked **Playable** by the conservative rules-text classifier can enter a deck. Other cards remain searchable as **Catalogue only**.

## What the app does

- **Solo duel:** play against Easy, Normal or Hard AI.
- **Local duel:** two people play pass-and-play on one Android device. Private-hand handoffs conceal the previous player's hand.
- **Card catalogue:** browse and search the bundled English catalogue offline, including set/printing metadata.
- **Deck builder:** create, edit, save and delete local decks.
- **Deck rules:** at least 40 cards, no maximum size, no more than four copies of a card name (counted across printings).
- **Rules engine:** a deterministic Kotlin state machine shared by the UI and AI. Unsupported card text is not guessed at runtime.
- **Privacy:** Android client requests no internet permission and disables Android app-data backup. The Android game does not depend on the optional Ktor backend.

## Player journey

1. Launch the app. On first launch, the bundled SQLite catalogue is imported into the local Room database.
2. Browse the card catalogue and inspect each card's Playable / Catalogue only status.
3. Open Deck Builder, create a deck, add at least 40 playable cards and save it.
4. Start a Solo duel and select an AI difficulty, or start a Local duel for pass-and-play.
5. Play turns using the battlefield controls. The game pauses when a defender must choose a Blocker, decide whether to use a Shield Trigger, or select an effect target.
6. The match ends when a player loses by deck-out or an opponent attacks directly after all shields are gone.

See the [player guide](docs/user-guide.md) and [gameplay flow](docs/gameplay-flow.md).

## Current supported rules slice

The current engine includes turn progression, draw/deck-out, shields, mana charging and payment, summoning sickness, Speed Attacker, evolution onto a matching race, evolution stacks, attacks, Blocker selection, Slayer combat, Power Attacker, Double/Triple Breaker, Unblockable, supported Shield Triggers and a limited set of spell effects.

The exact effect patterns and known exclusions are documented in [rules-engine status](docs/rules-engine-status.md) and [card rules support](docs/card-rules-support.md). The classifier is intentionally conservative: a card with even one unrecognized rules clause is Catalogue only.

## Build and test

- Android Gradle Plugin 8.9.1; Gradle 8.11.1
- JDK 17
- compile / target SDK 36
- min SDK 26 (Android 8.0)
- Application ID: `com.duelmasters.game`
- Version: `1.0.0`

Open the repository in Android Studio, sync Gradle and run the `app` configuration. First-launch catalogue import is local and does not require a network connection.

For CI, release signing and the release checklist, see [release documentation](docs/release.md). CI builds debug and optimized release variants, runs JVM tests and runs an API 36 emulator catalogue-import test. A production release must be signed with the long-lived owner-controlled keystore; CI must never use a debug key.

## Project structure

```text
app/       Android application: UI, rules engine, Room persistence and bundled card data
backend/  Retained Ktor scaffold; not used by the offline Android client
docs/     Architecture, player guide, rules coverage, data and release documentation
```

## Documentation index

| Document | Purpose |
|---|---|
| [Player guide](docs/user-guide.md) | First launch, catalogue, decks, solo and local matches |
| [Gameplay flow](docs/gameplay-flow.md) | Match setup, turn lifecycle and decision handoffs |
| [Architecture](docs/architecture.md) | App layers, boundaries and data flow |
| [Rules-engine status](docs/rules-engine-status.md) | Implemented mechanics and known limitations |
| [Card rules support](docs/card-rules-support.md) | Parser coverage, unsupported patterns and SQL inventory query |
| [Card database](docs/card-database.md) | Catalogue structure and provenance |
| [Release](docs/release.md) | Build, signing and CI instructions |\n| [QA sign-off](docs/qa-release-checklist.md) | Automated, gameplay, device and distribution release gates |

## Important release caveat

This is a playable rules subset backed by a broad catalogue, not a complete DM-35 simulator. Before distributing it as a faithful rules implementation, the remaining card effects, timing interactions, edge cases, physical-device QA and final signed release must be completed and verified.

## License

MIT for this repository's original code. Refer to the card-data source and individual asset provenance before redistributing bundled catalogue data or adding artwork.
