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
- Initial battlefield screen for AI and local pass-and-play, using catalogue-derived cards.
- Offline deck builder with minimum-40/no-maximum validation, four-copy-per-name enforcement, and Room saving; saved decks can be selected for matches.
- Basic deck validation: minimum 40 cards, no maximum size, and at most four copies per card name.
- Platform-independent rules-engine slice: game setup, zones, mana charging, summoning, summoning sickness, blockers, slayer combat, breaker abilities and supported Shield Trigger effects.
- Baseline AI action policy using the same game-action model.
- Card and printing records retain set, collector number, rarity and official/community/promotional provenance.
- Card metadata is not yet equivalent to rules-engine support: imported cards are marked `METADATA_ONLY` until their effects are implemented and verified.

The bundled source describes 2,600+ English card entries through DM-35 and English promotional entries. The importer groups repeated printings into card definitions. Artwork and video assets are not bundled.

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
- [x] Add initial local two-player pass-and-play handoff UI; private-hand polish and complete rules are still in progress.
- [ ] Expand card effect coverage and mark verified mechanics accurately; this is not yet a complete classic-rules implementation.

## License

MIT
