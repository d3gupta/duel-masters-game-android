# Offline rules engine and gameplay UI

The Android app contains a platform-independent Kotlin rules core in
`com.duelmasters.game.engine`. Human and AI actions are submitted to
`DuelMastersEngine.apply`; rejected actions leave the immutable source state
unchanged.

## Deck construction rules

- Minimum deck size: 40 cards.
- No maximum deck size.
- Maximum four copies of a card name, regardless of distinct catalogue record IDs.
- The deck builder and match setup both use `DeckValidator`.

Card-specific banned, restricted, and format-dependent exceptions are not yet
represented.

## Implemented rules slice

- Five opening cards and five shields per player.
- Deck, hand, mana, battle, shield, and graveyard zones.
- One mana charge per turn, untap, draw, creature summoning and mana payment.
- Summoning sickness and Speed Attacker.
- Attacks against shields and tapped creatures.
- Blocker interception, including prevention of a direct-attack win.
- Slayer combat, Power Attacker +2000, Double Breaker, and Triple Breaker.
- Shield Trigger creatures enter the battle zone for free; Shield Trigger spells
  resolve their supported effects without paying mana. Catalogue mechanics and rules
  text both contribute to Shield Trigger detection.
- A conservative rules-text parser maps recognized catalogue text to keywords
  and supported effect definitions.
- Supported spell effects: draw cards, destroy or return the opponent's weakest
  creature, add the top card of the deck to mana, and boost friendly creatures.
- Easy, Normal, and Hard AI action policies submit actions through the same engine.
- Compose battlefield cards display cost, power, abilities and tapped state;
  tapping animates the card's rotation.
- Local two-player pass-and-play includes a hand-reveal transition.
- Deck builder supports creation, editing/renaming, deletion, and saved-deck use.

## Still incomplete / not yet verified as rules-complete

- The parser intentionally recognizes only a subset of English card text.
  Many unique card effects, target choices, conditional clauses, evolution
  requirements, civilization-specific costs, and continuous/triggered effects
  still need explicit implementations.
- Shield Trigger resolution currently uses automatic resolution; there is no player
  prompt to choose optional trigger effects. Temporary power effects created by a
  Shield Trigger persist through the recipient's turn and expire at its end.
- Blockers automatically intercept using the first available blocker rather
  than prompting the defender to choose among multiple blockers.
- The UI battlefield is a functional card-panel layout, not final card-art
  rendering. The imported catalogue does not bundle card artwork.
- No full card-by-card rules coverage or on-device gameplay verification has
  been completed. Run the Android build and unit-test workflow before treating
  this branch as a verified release.
