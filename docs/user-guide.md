# Player guide

## 1. First launch

The app imports its bundled English card catalogue into local storage the first time it starts, and again when the catalogue version changes. Keep the app open until the import completes. No account or internet connection is required. If import fails, use the Retry action on the startup error screen.

## 2. Browse cards

Open the card catalogue to search by card name or rules text. Open a result to inspect its civilization, type, cost, power, race, rules text and printing information.

Each card is labelled as one of:

- **Playable** — its type and all recognized rules clauses fit the currently implemented rules engine.
- **Catalogue only** — the card is still searchable, but one or more rules clauses are not implemented or cannot be interpreted safely.

Catalogue-only cards cannot be added to a deck. This is deliberate: the app must not silently approximate an unknown card effect.

## 3. Build a deck

1. Open Deck Builder and create a deck.
2. Search for a card and add copies from the Playable catalogue.
3. The deck must contain **at least 40 cards**. There is no maximum deck size.
4. You may include at most **four copies of a card name**. Different printings of the same card name count together.
5. Save the deck. Saved decks are stored locally on the device.

The app validates the same rules when a deck is selected for a match. A deck below 40 cards or with more than four copies of a name cannot start a match.

## 4. Start a solo duel

Choose the solo mode, select a saved deck and choose the AI difficulty:

- **Easy** — less consistent action selection and simpler defensive choices.
- **Normal** — heuristic action scoring and basic defensive decisions.
- **Hard** — stronger action scoring and more deliberate blocker choices.

The AI is rules-aware but is not a perfect strategic opponent.

## 5. Start a local two-player duel

Choose local/pass-and-play mode and select a deck for each player. When the device changes hands, follow the private-hand handoff screen. The next player should only reveal their hand when they have the device. Keep the device positioned so the other player cannot see the private hand.

## 6. Play a turn

The normal turn loop is:

1. Untap your cards at the start of your turn.
2. Draw a card (the first-player opening-turn exception is handled by the engine).
3. Optionally charge one card from hand to mana.
4. Tap available mana to summon a creature or cast a spell.
5. Attack with eligible creatures.
6. End your turn.

A creature normally cannot attack on the turn it is summoned unless it has Speed Attacker. An evolution creature must be placed on a creature with the required race.

## 7. Respond to decisions

The game can pause for a decision that belongs to the defending player:

- **Blocker:** choose one available untapped Blocker or decline to block.
- **Shield Trigger:** choose whether to use the trigger for free or add the shield card to hand.
- **Effect target:** choose one of the legal creatures identified by the effect.

A pending decision must be resolved before another action can be taken. Multi-shield attacks continue after a Shield Trigger decision resolves.

## 8. Win condition

A player loses if they must draw from an empty deck. A player also loses when an opponent attacks directly while they have no shields remaining.

## 9. Offline data and privacy

The Android app does not request internet access. Card catalogue data and saved decks are local to the app. Android app-data backup is disabled; uninstalling the app or clearing its data can therefore remove locally saved decks.
