# Expanded English Duel Masters card catalogue

This implementation bundles an authorized offline English card-data source and imports it into Room. It does not bundle card artwork or video media; those assets are separate and are not required for catalogue browsing.

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
  duelmasters-source.db.base64

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

1. Use the authorized bundled SQLite source; permission arrangements have been confirmed by the project owner offline.
2. Preserve the original source asset and its upstream provenance.
3. Decode the base64 asset locally and open it read-only; never access the network.
4. Read `Card` and `RulesText`, normalize civilization/race/type/rarity values, and preserve original rules text.
5. Group identical card definitions while retaining each source row as a printing.
6. Record official English, community English and promotional release classes separately.
7. Import in a Room transaction using a versioned, idempotent importer.
8. Use Room upserts that do not delete owned-card references when definitions are refreshed.
9. Keep the JSON Schema as the normalized interchange/export contract.
10. Add tests for parsing, migration, duplicate handling, multi-civilization cards, unusual power values and empty optional fields.

## Room integration guidance

The repository now has Room database version 2 with a migration from version 1. It preserves the existing `decks` table and adds catalogue, printing, collection and import-metadata tables. Do not use `fallbackToDestructiveMigration()` because it could erase player decks.

Suggested tables:

- `cards`: `cardId` primary key, name, type, civilizations JSON, cost, power text, races JSON, rules text, flavor text, mechanics JSON, implementation status, provenance JSON.
- `card_printings`: composite unique key `(setCode, collectorNumber)`, foreign key to `cards`, rarity, illustrator, release class, image asset key (nullable).
- Keep user collection counts in a separate `owned_cards` table keyed by `cardId` (and optionally printing key), rather than mutating the catalogue.

Room exposes paginated search/filter queries by name, set, civilization, cost, type, race, rarity and rules text. Avoid loading the entire catalogue into Compose state.

## Acceptance criteria

- App cold-starts and opens the card browser with airplane mode enabled.
- Import happens once and is safe to resume after interruption.
- Every imported row has provenance.
- The UI distinguishes official English releases from community English translations.
- Metadata availability is not presented as gameplay support.
- Deck builder can select only cards allowed by the chosen format and supported-rules profile.
- Existing saved decks survive database upgrades.
