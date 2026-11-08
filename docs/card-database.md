# Expanded English Duel Masters card catalogue

This package prepares the repository for an offline, searchable English card catalogue.
It deliberately does **not** bundle third-party card text, images, or a scraped database:
the redistribution terms for the candidate community datasets have not yet been verified.

## Scope

- Target: English-language Duel Masters TCG entries through DM-35, plus English promos where source data is available.
- DM-01–DM-12: original English releases.
- DM-13–DM-35: many entries are community-produced English translations/versions; preserve this provenance in the UI and data.
- Exclude Japanese-only cards unless an English version is intentionally added later.
- The app must remain fully offline after installation.

## Candidate data sources

1. `https://github.com/bmenneni/SimpleJavaDmdb`
   - Its README describes 2,600+ English card entries through DM-35.
   - The repository contains a SQLite database (`duelmasters.db`).
   - The project owner has confirmed that required redistribution permissions are handled offline. Use only the authorized offline copy supplied for this project; this repository does not independently re-verify those arrangements.
2. `https://db.duelmasters.us/`
   - Searchable card reference with set, civilization, race, type, rarity and rules-text filters.
   - Useful for spot-checking imported records.
3. `https://github.com/Latepate64/duel-masters-json`
   - Useful JSON shape and historical reference for DM-01–DM-12 and English promos.
   - Its README explicitly describes that narrower coverage; do not mistake it for the expanded DM-35 dataset.

Card artwork/image permissions are separate from card-data permissions. Only package image assets supplied under the project's authorized offline arrangements.

## Recommended repository layout

```text
app/src/main/assets/cards/
  card-catalog.json
  card-catalog.schema.json
app/src/main/java/com/duelmasters/game/data/
  catalog/
    CardCatalogModels.kt
    CardCatalogImporter.kt
  local/
    CardEntity.kt
    CardPrintingEntity.kt
    CardDao.kt
```

The bundled SimpleJavaDmdb SQLite snapshot is the import source; Room is the runtime query store. The SQLite file is included as base64 text in app assets and decoded locally on first import. The app does not query a remote website at runtime and works offline.

The source repository describes 2,600+ English card entries through DM-35 and 180+ card animations. The Android importer reads the Card and RulesText tables, groups identical card definitions, and imports each source row as a printing. The current importer does not import the video animations or artwork. Runtime card/printing totals are recorded in card_catalog_metadata after import. The JSON Schema in this directory defines the portable normalized interchange format for future catalogue exports and additional authorized data sources.

## Data model rules

- A **card definition** represents the game object and its rules text.
- A **printing** represents a particular set/collector-number/rarity/illustrator appearance.
- One card definition can have multiple printings.
- Use a stable internal `cardId` independent of display name, because names and printings can vary.
- Preserve source text verbatim in `rulesText`; never overwrite it with a guessed interpretation.
- Keep `mechanics` as descriptive tags for filtering and AI hints, not executable rules.
- Keep executable effects separate in the game engine. A parser must never silently turn arbitrary card text into executable code.
- `implementationStatus` must be one of `METADATA_ONLY`, `PARTIAL`, `IMPLEMENTED`, `RULES_VERIFIED`.
- A card is playable only when every required effect is implemented and verified for the selected rules profile. The collection browser may still show metadata-only cards.
- Preserve provenance at card and printing level: source URL, source record ID, language, release class, verification date and notes.

## Import pipeline

1. Obtain a source export and explicit permission/license terms for redistribution and modification.
2. Keep an untouched copy of the source export outside the app source tree for auditability.
3. Write a deterministic converter from source schema to `card-catalog.json`.
4. Normalize civilization/race/type values without discarding the original source values.
5. Validate every record against `card-catalog.schema.json`.
6. Validate uniqueness of `cardId` and `(setCode, collectorNumber)`.
7. Generate a report: total definitions, total printings, counts by set, missing fields, duplicate keys and provenance gaps.
8. Import the bundled JSON into Room on first launch using a versioned catalog importer.
9. Make imports idempotent: re-running the same catalog version must not duplicate cards or printings.
10. Add tests for parsing, migration, duplicate handling, multi-civilization cards, unusual power values and empty optional fields.

## Room integration guidance

The current repository has a Room database at version 1 with `DeckEntity` only. Add catalog entities through a proper Room migration; do not use `fallbackToDestructiveMigration()` because it could erase player decks.

Suggested tables:

- `cards`: `cardId` primary key, name, type, civilizations JSON, cost, power text, races JSON, rules text, flavor text, mechanics JSON, implementation status, provenance JSON.
- `card_printings`: composite unique key `(setCode, collectorNumber)`, foreign key to `cards`, rarity, illustrator, release class, image asset key (nullable).
- Keep user collection counts in a separate `owned_cards` table keyed by `cardId` (and optionally printing key), rather than mutating the catalogue.

Room should expose paginated/filterable queries by name, set, civilization, cost, type, race, rarity and rules text. Avoid loading the entire catalogue into Compose state.

## Acceptance criteria

- App cold-starts and opens the card browser with airplane mode enabled.
- Import happens once and is safe to resume after interruption.
- Every imported row has provenance.
- The UI distinguishes official English releases from community English translations.
- Metadata availability is not presented as gameplay support.
- Deck builder can select only cards allowed by the chosen format and supported-rules profile.
- Existing saved decks survive database upgrades.
