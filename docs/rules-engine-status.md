# Offline rules engine and gameplay UI

The Android client is offline-only. The pure Kotlin rules engine is shared by human
players and AI; actions are validated by the same immutable state machine.

## Deck construction

- Minimum 40 cards; there is no maximum deck size.
- Maximum four copies of a card name, even across separate catalogue IDs/printings.
- Match setup and deck editing apply the same validator.
- The default starter deck contains up to ten supported names, four copies each;
  it is enabled only when the catalogue has enough supported entries.

## Implemented engine mechanics

- Five opening cards and five shields per player; turn draw, deck-out loss, one mana
  charge per turn, mana payment, untap and turn progression.
- Paper mana payment checks each printed civilization using a distinct source card;
  a multicolored source can satisfy only one civilization. Multicolored cards enter
  mana tapped; five-civilization sources provide zero numeric mana but can satisfy
  one civilization requirement.
- Evolution keeps the source creature's tapped/untapped state and ongoing temporary
  power modifiers; the evolution creature itself has no summoning sickness.
- Summoning sickness, Speed Attacker, evolution onto a matching race, and stacked
  evolution cards moving together when destroyed or returned.
- Attacks to shields and tapped creatures; direct victory after shields are gone.
- Defender-selected Blocker interception; Unblockable bypasses Blockers.
- Slayer combat, Power Attacker bonuses, Double Breaker and Triple Breaker.
- Defender can use a Shield Trigger for free or add that shield to hand. A trigger
  spell's supported effects resolve before the rest of a multi-shield attack.
- Opponent-specific targeted destroy/return/tap effects pause for target selection.
  Ambiguous all-player targets remain browse-only. Supported weakest
  creature effects, draw, add-top-card-to-mana/hand/shield, untap, temporary power
  boosts, and power-threshold mass destruction.
- Temporary power modifiers expire at the end of the affected owner's next turn,
  including when created by a Shield Trigger during the opponent's turn.
- AI Easy / Normal / Hard action policies, full-match simulations for all three
  difficulties, and AI decisions for Blockers, Shield
  Triggers, and effect targets.
- Local two-player pass-and-play pauses for private hand and defender-choice
  handoffs. Opponent hands and shield identities remain hidden.
- Battlefield includes mana and battle zones, tapped-state animation, and
  evolution-stack indicators.

## Catalogue and playability contract

The app imports the bundled English card source into Room offline. Card records and
printings remain searchable even when their effects are not implemented. The importer
uses a conservative rules-text classifier:

- SUPPORTED: all recognized clauses fit the engine's implemented mechanics; the
  card can be used in decks.
- METADATA_ONLY: the rules text includes an unsupported clause, the card type is
  outside the implemented slice, or an evolution requirement cannot be parsed.
  The card remains visible in the catalogue but is excluded from deck construction
  and match setup.

This is intentionally conservative. A false-negative makes a card browse-only; a
false-positive could misplay the card. The parser is not a general Duel Masters
rules interpreter.

## Known limitations before a full classic-rules release

- Creature rules text that resembles a spell effect is not treated as a summon effect;
  generic effects in the current engine resolve only for supported spells. Evolution
  cards with Shield Trigger remain catalogue-only until the trigger can select a valid
  evolution host.
- Many unique card effects, when/whenever triggers, replacement effects,
  civilization-specific costs, race-specific effects beyond parsed evolution,
  attack restrictions, continuous abilities, and complicated conditional clauses
  are not implemented. The corresponding cards remain browse-only when detected.
- Temporary power boosts apply only to creatures already on the battlefield when
  the effect resolves; later creatures entering that turn do not automatically inherit
  the boost. Optional draw quantities are not treated as fixed draws.
- Destroying an evolution creature moves its full stack to the graveyard. Returning an
  evolution creature moves only the top card to its owner's hand and sends its sources
  to the graveyard.
- Format-specific banned/restricted lists and separate rule-set profiles are absent.
- The bundled source does not include card artwork. The UI uses information panels
  rather than official card images.
- JVM tests and the emulator database-import test are automated. Physical-device
  usability, long-duration match stability, accessibility review, and release-key
  signing still require owner-side validation.

Do not describe this implementation as having complete coverage for all cards
through DM-35. Only cards marked SUPPORTED are deck eligible.


For the parser's exact supported effect patterns, common unsupported wording families and the SQL query that produces the current per-card catalogue-only list, see [card rules support](card-rules-support.md). The end-user workflow is documented in the [player guide](user-guide.md) and [gameplay flow](gameplay-flow.md).
