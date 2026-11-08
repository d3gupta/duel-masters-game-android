# Duel Masters Game (Android)

An offline-first Duel Masters card game for Android. The Android client is designed for airplane-mode use and does not request network access.

## Planned game modes

- Single-player against AI (Easy / Normal / Hard).
- Local two-player pass-and-play on one device, with private-hand transitions.

## Current implementation

- Kotlin, Jetpack Compose and Material 3 Android app.
- Room database with a versioned migration that preserves saved decks.
- Bundled English Duel Masters SQLite card source, imported locally into Room.
- Searchable card browser with name and rules-text search.
- Offline AI and local pass-and-play battlefield with private-hand transitions, blocker choices, Shield Trigger choices, target selection and evolution plays.
- Offline deck builder with minimum-40/no-maximum validation, four-copy-per-name enforcement, and Room saving; saved decks can be selected for matches.
- Basic deck validation: minimum 40 cards, no maximum size, and at most four copies per card name.
- Platform-independent rules-engine slice: game setup, zones, mana charging, summoning, summoning sickness, blockers, slayer combat, breaker abilities and supported Shield Trigger effects.
- Easy / Normal / Hard AI policies, including defender and effect-target decisions, all enforced by the same rules engine.
- Card and printing records retain set, collector number, rarity and official/community/promotional provenance.
- Card metadata is not treated as rules support: only definitions classified SUPPORTED are deck-eligible. Other catalogue entries remain searchable as browse-only cards.

The bundled source describes 2,600+ English card entries through DM-35 and English promotional entries. The importer groups repeated printings into card definitions. Artwork and video assets are not bundled.

## Release build

The optimized Android release variant is built with R8 and resource shrinking. CI runs JVM rule tests, an Android emulator catalogue-import test, and packages the release APK. See [release and signing instructions](docs/release.md).

A production-signed APK requires a release keystore. When no signing values are configured, CI deliberately emits an unsigned release APK rather than signing with a debug key. This is an offline playable rules slice, not a claim that every card through DM-35 has complete effect support.

## Stack

| Layer | Technology |
|-------|------------|
| **Android UI** | Kotlin, Jetpack Compose, Material 3 |
| **Local storage** | Room / SQLite |
| **Card source** | Bundled SQLite asset, imported offline |
| **Backend** | Ktor scaffold retained for possible future use; not required by the Android app |

## Project structure

```text
duel-masters-game-android/
├── app/          # Offline Android client (Compose + Room)
├── backend/      # Optional legacy Ktor scaffold; not used by offline gameplay
└── docs/         # Architecture and card catalogue notes
```

## Getting started

1. Open this repository in Android Studio.
2. Sync Gradle.
3. Run the `app` configuration on an emulator or Android device.
4. On first launch, the bundled card database is decoded and imported into Room. No network connection is needed.

## Development roadmap

- [x] Bundle English card data through DM-35 and promotional entries.
- [x] Add Room card, printing, collection and catalogue-metadata tables.
- [x] Add searchable offline card browser.
- [x] Add the initial rules-engine slice and unit tests; full classic rules coverage remains in progress.
- [x] Add deck editing and deletion. Collection-aware building and advanced format restrictions remain future work.
- [x] Add Easy / Normal / Hard heuristic AI difficulty levels.
- [x] Add local two-player pass-and-play with private-hand transitions and turn/decision handoffs.
- [x] Add interactive blocker, Shield Trigger and target choices; implement the supported evolution stack and mark unimplemented card text as browse-only.
- [ ] Continue expanding per-card rules coverage and test full match sequences; the complete DM-35 rules set is not yet implemented.

## License

MIT
