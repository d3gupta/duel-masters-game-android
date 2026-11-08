# Offline rules engine and gameplay UI

The Android app contains a platform-independent Kotlin rules core in
`com.duelmasters.game.engine`. UI and opponent policies call the same
`DuelMastersEngine.apply` function; illegal actions return a rejection without
changing the immutable source state.

## Deck construction rules currently enforced

- Minimum deck size: 40 cards.
- No maximum deck size.
- Maximum four copies of a card name, regardless of distinct catalogue record IDs.
- The same validation is applied by the deck builder and when starting a match.

This is a baseline classic-format restriction. Card-specific banned, restricted,
or format-dependent exceptions are not yet represented.

## Current playable vertical slice

- Five opening cards and five shields per player.
- Player hand, deck, mana, battle, shield and graveyard zones.
- One mana charge per turn.
- Untap at turn start, draw, creature summoning with mana payment, summoning
  sickness, Speed Attacker permission, basic creature-vs-creature power combat,
  shield breaking, and direct-attack victory when shields are already gone.
- Deterministic baseline AI policy using the same action model as a human player.
- Compose battlefield screen with AI and local pass-and-play modes.
- Deck builder searches the offline catalogue, validates the deck, and saves
  the card-ID list in Room. Saved decks can be selected when starting a match.
- When no saved deck exists, the match screen offers a temporary 40-card
  starter deck built from unique card names in the catalogue.

## Not yet rules-complete

The effect resolver for arbitrary card text, automatic Shield Trigger choice
and resolution, blocker interception/combat redirection, civilization-specific
cost rules, multi-breaker attack resolution, Slayer and continuous/triggered
effects, and the full card-by-card effect library remain to be implemented.
The AI is currently a baseline policy, not the three requested difficulty
profiles. Deck editing/deletion, collection-aware deck construction, and
polished card-art battlefield presentation are also outstanding.
