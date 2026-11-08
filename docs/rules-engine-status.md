# Offline rules engine

The Android app contains a platform-independent Kotlin rules core in
`com.duelmasters.game.engine`. UI and opponent policies call the same
`DuelMastersEngine.apply` function; illegal actions return a rejection without
changing the immutable source state.

## Current vertical slice

- 40-card deck validation at game creation.
- Five opening cards and five shields per player.
- Player hand, deck, mana, battle, shield and graveyard zones.
- One mana charge per turn.
- Untap at turn start, draw, creature summoning with mana payment, summoning
  sickness, Speed Attacker permission, basic creature-vs-creature power combat,
  shield breaking, and direct-attack victory when shields are already gone.
- A deterministic baseline AI policy that chooses actions through the same
  action model.

## Not yet rules-complete

This is the first engine slice, not a complete tournament-rules implementation.
The effect resolver for arbitrary card text, automatic Shield Trigger choice
and resolution, blocker interception/combat redirection, civilization-specific
cost rules, multi-breaker attack resolution, Slayer and continuous/triggered
effects, and the full card-by-card effect library remain to be implemented.
The AI is currently a baseline policy, not the three requested difficulty
profiles. The current catalogue UI is not yet connected to deck construction
or live matches. These are tracked as explicit gaps rather than simulated
features.
