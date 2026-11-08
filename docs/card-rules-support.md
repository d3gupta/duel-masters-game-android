# Card rules support and parser coverage

## Important distinction

The bundled catalogue is broader than the implemented rules engine. The parser does not understand all Duel Masters wording, and there are no per-card name-specific scripts for every card in DM-35. Instead, the importer classifies each card definition from its type, rules text and catalogue mechanics:

- `SUPPORTED` — all non-empty rules clauses match the implemented patterns and the card type is supported. Deck eligible.
- `METADATA_ONLY` — the type or at least one rules clause is outside the supported slice, or an evolution requirement cannot be parsed. Searchable, but not deck eligible.

A card with one supported clause and one unsupported clause is still `METADATA_ONLY`. Do not infer that a card is playable merely because one keyword is recognized.

## Implemented keyword / combat patterns

- Blocker
- Slayer
- Speed Attacker
- Double Breaker
- Triple Breaker
- Shield Trigger
- Unblockable / cannot be blocked
- Power Attacker numeric bonus (engine applies the parsed numeric bonus)

Evolution support is limited to an evolution requirement that can be parsed as placing the card on one of your creatures of a named race. The engine checks the host creature's race and retains the evolution stack.

## Implemented spell/effect patterns

The current text adapter recognizes these effect families when the wording matches its explicit patterns:

| Pattern | Engine behavior |
|---|---|
| Draw a card / draw N cards | Draw a fixed number of cards |
| Destroy opponent's weakest creature | Destroy the lowest-current-power opposing creature |
| Destroy one of your opponent's creatures | Pause for a target selection, then destroy the chosen opposing creature |
| Return opponent's weakest creature to hand | Return the lowest-current-power opposing creature |
| Return one of your opponent's creatures to hand | Pause for a target selection, then return the chosen opposing creature |
| Tap one of your opponent's creatures | Pause for a target selection; only untapped creatures are legal |
| Put/add top card of your deck to mana | Move the top card into mana |
| Put/add top card of your deck to hand | Move the top card into hand |
| Put/add top card of your deck to shields | Move the top card into shields |
| Untap all of your creatures | Untap your battle zone |
| Your creatures get +N power until end of turn | Apply a temporary power modifier to creatures currently in your battle zone |
| Destroy all opponent creatures with power N or less | Destroy all opposing creatures at or below the threshold |

Effects from a Shield Trigger resolve through the same supported effect handlers. Multi-shield attacks resume after the trigger decision and its queued effects finish.

## What the parser deliberately does not recognize

The current classifier rejects unsupported wording rather than guessing. Common unsupported families include:

- Generic target wording such as "target creature" when the target scope or legality is not explicit in the supported templates.
- Optional or variable quantities such as "draw up to N cards".
- Search, tutor, reveal, choose-from-deck, or shuffle instructions.
- Effects that move cards between zones in ways not listed above.
- Costs, additional costs, alternative costs, payment choices or civilization-specific cost modifications.
- "When", "whenever", "at the start/end of", "after", "if", "unless", "instead", "while" and other triggered, conditional, replacement or continuous-effect clauses.
- Effects affecting both players, multiple zones or an unspecified player.
- Race/civilization restrictions or interactions beyond the parsed evolution host race.
- Attack, summon, blocking or play restrictions not listed in the supported keywords.
- Cards with a missing/negative cost, or creature cards whose printed power is not a plain integer. These remain catalogue-only rather than being treated as free/zero-power cards.
- Cross Gear, Castle and other card types outside the current creature/spell slice.
- Evolution cards whose source wording cannot be parsed into the supported race requirement, or that also have Shield Trigger (the evolution-host choice is not supported during trigger resolution).
- Evolution requirement text attached to a non-evolution card type.
- Creature cards whose text matches a spell-effect pattern; the current effect executor resolves those handlers only for spells.
- Spell cards with creature-only combat keywords such as Blocker, Slayer or Speed Attacker.
- Any card with more than one clause if at least one clause is unsupported.

This is a description of parser coverage, not a claim that every unsupported wording family has been exhaustively enumerated. New source wording may reveal additional false negatives or edge cases.

## Get the exact unsupported card-name list

The importer stores the classification on each Room `cards` row in `implementationStatus`. Run this query against the app's Room database (for example, using Android Studio's Database Inspector while the app is running) to list every catalogue-only definition:

```sql
SELECT
    name,
    type,
    rulesText,
    cardId,
    sourceRecordId
FROM cards
WHERE implementationStatus <> 'SUPPORTED'
ORDER BY type, name, cardId;
```

To count the current split:

```sql
SELECT implementationStatus, COUNT(*) AS card_count
FROM cards
GROUP BY implementationStatus
ORDER BY implementationStatus;
```

The CI emulator test exports `card-rules-support-report.csv` (Catalogue-only entries), `card-rules-support-full-report.csv` (all entries and their statuses), and `card-rules-support-summary.txt`. The workflow uploads these in a versioned `duel-masters-rules-coverage-<commit>` artifact. The full report includes names, types, cost, power, source IDs, metadata mechanics and exact rules text. Review all rows marked `SUPPORTED` for possible false positives as well as the Catalogue-only list for false negatives.

## How to add support safely

1. Add or refine a narrowly scoped parser pattern.
2. Map it to an explicit engine effect or keyword; do not infer an effect from a card name alone.
3. Add unit tests for the exact wording and near-miss/unsupported wording.
4. Add engine tests for target selection, timing, zones, interactions and edge cases.
5. Re-import by incrementing the catalogue version if classification data changes.
6. Verify the card is marked Playable only when every clause is implemented.
7. Add the card to a regression fixture/report with its source record and printing provenance.
