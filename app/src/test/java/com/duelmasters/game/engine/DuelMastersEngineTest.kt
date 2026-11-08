package com.duelmasters.game.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DuelMastersEngineTest {
    private fun deck(prefix: String) = List(40) { i ->
        GameCard("$prefix-$i", "Creature $i", CardKind.CREATURE, cost = 1, power = 1000)
    }

    @Test fun deckValidatorAcceptsFortyOrMoreCardsAndCapsCopiesAtFour() {
        val valid = deck("same").mapIndexed { index, card -> card.copy(id = "card-$index") }
        assertTrue(DeckValidator.validate(valid).isValid)
        assertTrue(DeckValidator.validate(valid + valid.take(20)).isValid)
        val tooShort = DeckValidator.validate(valid.drop(1))
        assertTrue(tooShort.errors.any { it.contains("at least 40") })
        val fiveSameName = List(5) { index -> GameCard("variant-$index", "Same Card", CardKind.CREATURE, 1, 1000) } + valid.take(35)
        val tooManyCopies = DeckValidator.validate(fiveSameName)
        assertTrue(tooManyCopies.errors.any { it.contains("limit is 4") })
    }

    @Test fun newGameAcceptsDecksLargerThanFortyCards() {
        val largeDeck = List(60) { i -> GameCard("large-$i", "Large $i", CardKind.CREATURE, 1, 1000) }
        val game = DuelMastersEngine.newGame(largeDeck, largeDeck)
        assertEquals(5, game.players[0].hand.size)
        assertEquals(5, game.players[0].shields.size)
        assertEquals(50, game.players[0].deck.size)
    }

    @Test fun newGameRejectsMoreThanFourCopiesByCardName() {
        val repeated = List(5) { index -> GameCard("different-id-$index", "Same Card", CardKind.CREATURE, 1, 1000) } +
            List(35) { index -> GameCard("other-$index", "Other $index", CardKind.CREATURE, 1, 1000) }
        try {
            DuelMastersEngine.newGame(repeated, deck("valid"))
            throw AssertionError("Expected copy limit validation to fail")
        } catch (_: IllegalArgumentException) {
            assertTrue(true)
        }
    }

    @Test fun newGameRejectsDecksSmallerThanFortyCards() {
        val smallDeck = deck("small").drop(1)
        try {
            DuelMastersEngine.newGame(smallDeck, deck("valid"))
            throw AssertionError("Expected deck size validation to fail")
        } catch (_: IllegalArgumentException) {
            assertTrue(true)
        }
    }

    @Test fun newGameDealsFiveCardsAndFiveShieldsPerPlayer() {
        val game = DuelMastersEngine.newGame(deck("a"), deck("b"))
        assertEquals(5, game.players[0].hand.size)
        assertEquals(5, game.players[0].shields.size)
        assertEquals(30, game.players[0].deck.size)
        assertEquals(5, game.players[1].hand.size)
        assertEquals(5, game.players[1].shields.size)
    }

    @Test fun chargingManaMovesCardFromHandToMana() {
        val game = DuelMastersEngine.newGame(deck("a"), deck("b"))
        val card = game.players[0].hand.first()
        val result = DuelMastersEngine.apply(game, GameAction.ChargeMana(card.instanceId))
        assertTrue(result is ActionResult.Success)
        val next = (result as ActionResult.Success).state.players[0]
        assertEquals(4, next.hand.size)
        assertEquals(1, next.mana.size)
    }

    @Test fun onlyOneManaCardCanBeChargedPerTurn() {
        val game = DuelMastersEngine.newGame(deck("a"), deck("b"))
        val first = game.players[0].hand[0]
        val second = game.players[0].hand[1]
        val charged = DuelMastersEngine.apply(game, GameAction.ChargeMana(first.instanceId)) as ActionResult.Success
        val rejected = DuelMastersEngine.apply(charged.state, GameAction.ChargeMana(second.instanceId))
        assertTrue(rejected is ActionResult.Rejected)
        assertEquals(1, charged.state.players[0].mana.size)
    }

    @Test fun cannotSummonWithoutEnoughMana() {
        val game = DuelMastersEngine.newGame(deck("a"), deck("b"))
        val result = DuelMastersEngine.apply(game, GameAction.Summon(game.players[0].hand.first().instanceId))
        assertTrue(result is ActionResult.Rejected)
    }

    @Test fun summonedCreatureHasSummoningSickness() {
        val card = GameCard("heavy", "Heavy", CardKind.CREATURE, 1, 3000)
        val player = PlayerState(
            id = 0, name = "P1", deck = emptyList(),
            hand = listOf(CardInstance("heavy-1", card)),
            mana = listOf(CardInstance("mana-1", GameCard("mana", "Mana", CardKind.CREATURE, 1), tapped = false))
        )
        val opponent = PlayerState(1, "P2", emptyList())
        val game = GameState(listOf(player, opponent))
        val result = DuelMastersEngine.apply(game, GameAction.Summon("heavy-1")) as ActionResult.Success
        val summoned = result.state.players[0].battle.single()
        assertTrue(summoned.summoningSick)
        assertTrue(summoned.tapped.not())
        assertEquals(0, result.state.players[0].availableMana)
    }

    @Test fun cannotAttackAnUntappedCreature() {
        val attacker = CardInstance("attacker", GameCard("a", "Attacker", CardKind.CREATURE, 1, 3000), summoningSick = false)
        val defender = CardInstance("defender", GameCard("d", "Defender", CardKind.CREATURE, 1, 1000), tapped = false, summoningSick = false)
        val game = GameState(listOf(
            PlayerState(0, "P1", emptyList(), battle = listOf(attacker)),
            PlayerState(1, "P2", emptyList(), battle = listOf(defender))
        ))
        val result = DuelMastersEngine.apply(game, GameAction.AttackCreature("attacker", "defender"))
        assertTrue(result is ActionResult.Rejected)
    }

    @Test fun rejectedActionDoesNotMutateOriginalState() {
        val game = DuelMastersEngine.newGame(deck("a"), deck("b"))
        val before = game.players[0]
        DuelMastersEngine.apply(game, GameAction.Summon(game.players[0].hand.first().instanceId))
        assertEquals(before, game.players[0])
    }
    @Test fun doubleBreakerBreaksTwoShields() {
        val breaker = GameCard("breaker", "Breaker", CardKind.CREATURE, 1, 3000, keywords = setOf("DOUBLE_BREAKER"))
        val attacker = CardInstance("attacker", breaker, summoningSick = false)
        val shields = listOf(
            CardInstance("s1", GameCard("s1", "Shield 1", CardKind.CREATURE, 1)),
            CardInstance("s2", GameCard("s2", "Shield 2", CardKind.CREATURE, 1))
        )
        val state = GameState(listOf(
            PlayerState(0, "P1", emptyList(), battle = listOf(attacker)),
            PlayerState(1, "P2", emptyList(), shields = shields)
        ))
        val result = DuelMastersEngine.apply(state, GameAction.AttackShield("attacker")) as ActionResult.Success
        assertEquals(0, result.state.players[1].shields.size)
        assertEquals(2, result.state.players[1].hand.size)
    }

    @Test fun shieldTriggerCreatureIsSummonedWithoutPayingMana() {
        val attacker = CardInstance("attacker", GameCard("a", "Attacker", CardKind.CREATURE, 1), summoningSick = false)
        val trigger = GameCard("trigger", "Trigger Creature", CardKind.CREATURE, 7, shieldTrigger = true)
        val state = GameState(listOf(
            PlayerState(0, "P1", emptyList(), battle = listOf(attacker)),
            PlayerState(1, "P2", emptyList(), shields = listOf(CardInstance("trigger-1", trigger)))
        ))
        val declared = (DuelMastersEngine.apply(state, GameAction.AttackShield("attacker")) as ActionResult.Success).state
        assertTrue(declared.pendingChoice is PendingChoice.ShieldTrigger)
        val result = DuelMastersEngine.apply(declared, GameAction.ResolveShieldTrigger(true)) as ActionResult.Success
        assertEquals(1, result.state.players[1].battle.size)
        assertTrue(result.state.players[1].hand.isEmpty())
        assertEquals(0, result.state.players[1].mana.size)
    }

    @Test fun shieldTriggerSpellResolvesItsDrawEffect() {
        val attacker = CardInstance("attacker", GameCard("a", "Attacker", CardKind.CREATURE, 1), summoningSick = false)
        val trigger = GameCard("trigger", "Trigger Spell", CardKind.SPELL, 5, shieldTrigger = true,
            effects = listOf(CardEffect(EffectKind.DRAW_CARDS)))
        val drawn = CardInstance("drawn", GameCard("drawn", "Drawn Card", CardKind.CREATURE, 1))
        val state = GameState(listOf(
            PlayerState(0, "P1", emptyList(), battle = listOf(attacker)),
            PlayerState(1, "P2", listOf(drawn), shields = listOf(CardInstance("trigger-1", trigger)))
        ))
        val declared = (DuelMastersEngine.apply(state, GameAction.AttackShield("attacker")) as ActionResult.Success).state
        val result = DuelMastersEngine.apply(declared, GameAction.ResolveShieldTrigger(true)) as ActionResult.Success
        assertEquals("Drawn Card", result.state.players[1].hand.single().card.name)
        assertEquals("Trigger Spell", result.state.players[1].graveyard.single().card.name)
    }

    @Test fun blockerInterceptsShieldAttack() {
        val attacker = CardInstance("attacker", GameCard("a", "Attacker", CardKind.CREATURE, 1, 1000), summoningSick = false)
        val blocker = CardInstance("blocker", GameCard("b", "Blocker", CardKind.CREATURE, 1, 3000, keywords = setOf("BLOCKER")))
        val shield = CardInstance("shield", GameCard("s", "Shield", CardKind.CREATURE, 1))
        val state = GameState(listOf(
            PlayerState(0, "P1", emptyList(), battle = listOf(attacker)),
            PlayerState(1, "P2", emptyList(), shields = listOf(shield), battle = listOf(blocker))
        ))
        val declared = (DuelMastersEngine.apply(state, GameAction.AttackShield("attacker")) as ActionResult.Success).state
        assertTrue(declared.pendingChoice is PendingChoice.Blocker)
        val result = DuelMastersEngine.apply(declared, GameAction.ChooseBlocker("blocker")) as ActionResult.Success
        assertEquals(1, result.state.players[1].shields.size)
        assertTrue(result.state.players[0].battle.isEmpty())
    }

    @Test fun slayerDestroysAttackerEvenWhenSlayerHasLowerPower() {
        val attacker = CardInstance("attacker", GameCard("a", "Attacker", CardKind.CREATURE, 1, 8000), summoningSick = false)
        val slayer = CardInstance("slayer", GameCard("s", "Slayer", CardKind.CREATURE, 1, 1000, keywords = setOf("SLAYER")), tapped = true)
        val state = GameState(listOf(
            PlayerState(0, "P1", emptyList(), battle = listOf(attacker)),
            PlayerState(1, "P2", emptyList(), battle = listOf(slayer))
        ))
        val result = DuelMastersEngine.apply(state, GameAction.AttackCreature("attacker", "slayer")) as ActionResult.Success
        assertTrue(result.state.players[0].battle.isEmpty())
        assertEquals(1, result.state.players[0].graveyard.size)
        assertTrue(result.state.players[1].battle.isEmpty())
        assertEquals(1, result.state.players[1].graveyard.size)
    }

    @Test fun supportedCardTextKeywordsAreRecognized() {
        val keywords = CardTextParser.keywords("This creature has Blocker, Slayer, Double Breaker and Speed Attacker.")
        assertTrue(keywords.containsAll(setOf("BLOCKER", "SLAYER", "DOUBLE_BREAKER", "SPEED_ATTACKER")))
    }

    @Test fun spellEffectDestroysWeakestOpponentCreature() {
        val spell = GameCard("spell", "Creature Removal", CardKind.SPELL, 1,
            effects = listOf(CardEffect(EffectKind.DESTROY_WEAKEST_OPPONENT)))
        val spellInstance = CardInstance("spell-instance", spell)
        val mana = CardInstance("mana", GameCard("mana-card", "Mana", CardKind.CREATURE, 1))
        val target = CardInstance("target", GameCard("target-card", "Target", CardKind.CREATURE, 1, 1000))
        val state = GameState(listOf(
            PlayerState(0, "P1", emptyList(), hand = listOf(spellInstance), mana = listOf(mana)),
            PlayerState(1, "P2", emptyList(), battle = listOf(target))
        ))
        val result = DuelMastersEngine.apply(state, GameAction.CastSpell("spell-instance")) as ActionResult.Success
        assertTrue(result.state.players[1].battle.isEmpty())
        assertEquals(1, result.state.players[1].graveyard.size)
        assertEquals(1, result.state.players[0].graveyard.size)
    }

    @Test fun shieldTriggerCreatureCanAttackOnItsOwnersNextTurn() {
        val attacker = CardInstance("attacker", GameCard("a", "Attacker", CardKind.CREATURE, 1), summoningSick = false)
        val trigger = GameCard("trigger", "Trigger Creature", CardKind.CREATURE, 7, shieldTrigger = true)
        val drawCard = CardInstance("draw", GameCard("draw-card", "Draw", CardKind.CREATURE, 1))
        val state = GameState(listOf(
            PlayerState(0, "P1", emptyList(), battle = listOf(attacker)),
            PlayerState(1, "P2", listOf(drawCard), shields = listOf(CardInstance("trigger-1", trigger)))
        ))
        val declared = (DuelMastersEngine.apply(state, GameAction.AttackShield("attacker")) as ActionResult.Success).state
        val attacked = (DuelMastersEngine.apply(declared, GameAction.ResolveShieldTrigger(true)) as ActionResult.Success).state
        assertTrue(attacked.players[1].battle.single().summoningSick)
        val nextTurn = (DuelMastersEngine.apply(attacked, GameAction.EndTurn) as ActionResult.Success).state
        assertTrue(!nextTurn.players[1].battle.single().summoningSick)
    }

    @Test fun tripleBreakerBreaksUpToThreeShields() {
        val attacker = CardInstance("attacker", GameCard("triple", "Triple", CardKind.CREATURE, 1, 5000,
            keywords = setOf("TRIPLE_BREAKER")), summoningSick = false)
        val shields = (1..3).map { n -> CardInstance("s$n", GameCard("s$n", "Shield $n", CardKind.CREATURE, 1)) }
        val state = GameState(listOf(
            PlayerState(0, "P1", emptyList(), battle = listOf(attacker)),
            PlayerState(1, "P2", emptyList(), shields = shields)
        ))
        val result = DuelMastersEngine.apply(state, GameAction.AttackShield("attacker")) as ActionResult.Success
        assertEquals(0, result.state.players[1].shields.size)
        assertEquals(3, result.state.players[1].hand.size)
    }

    @Test fun allAiDifficultiesChooseActionsAcceptedByTheEngine() {
        val deck = List(40) { index -> GameCard("ai-$index", "AI Card $index", CardKind.CREATURE, 1, 1000) }
        val state = DuelMastersEngine.newGame(deck, deck)
        AiDifficulty.values().forEach { difficulty ->
            val action = DuelMastersAi.chooseAction(state, difficulty, kotlin.random.Random(7))
            assertTrue(DuelMastersEngine.apply(state, action) is ActionResult.Success)
        }
    }

    @Test fun blockerPreventsDirectAttackVictoryWhenShieldsAreGone() {
        val attacker = CardInstance("attacker", GameCard("a", "Attacker", CardKind.CREATURE, 1, 1000), summoningSick = false)
        val blocker = CardInstance("blocker", GameCard("b", "Blocker", CardKind.CREATURE, 1, 3000, keywords = setOf("BLOCKER")))
        val state = GameState(listOf(
            PlayerState(0, "P1", emptyList(), battle = listOf(attacker)),
            PlayerState(1, "P2", emptyList(), battle = listOf(blocker))
        ))
        val declared = (DuelMastersEngine.apply(state, GameAction.AttackShield("attacker")) as ActionResult.Success).state
        val result = DuelMastersEngine.apply(declared, GameAction.ChooseBlocker("blocker")) as ActionResult.Success
        assertTrue(result.state.winner == null)
        assertTrue(result.state.players[0].battle.isEmpty())
        assertEquals(1, result.state.players[1].battle.size)
    }

    @Test fun powerBoostUsesAmountAsPowerNotAsRepeatCount() {
        val spell = CardInstance("boost-spell", GameCard("boost", "Boost", CardKind.SPELL, 1,
            effects = listOf(CardEffect(EffectKind.BOOST_FRIENDLY_CREATURES, 2000))))
        val mana = CardInstance("mana", GameCard("mana-card", "Mana", CardKind.CREATURE, 1))
        val creature = CardInstance("creature", GameCard("creature-card", "Creature", CardKind.CREATURE, 1, 1000))
        val state = GameState(listOf(
            PlayerState(0, "P1", emptyList(), hand = listOf(spell), mana = listOf(mana), battle = listOf(creature)),
            PlayerState(1, "P2", emptyList())
        ))
        val result = DuelMastersEngine.apply(state, GameAction.CastSpell("boost-spell")) as ActionResult.Success
        assertEquals(3000, result.state.players[0].battle.single().currentPower)
        val ended = (DuelMastersEngine.apply(result.state, GameAction.EndTurn) as ActionResult.Success).state
        assertEquals(1000, ended.players[0].battle.single().currentPower)
    }

    @Test fun temporaryBoostGrantedDuringOpponentTurnSurvivesUntilOwnersTurnEnds() {
        val attacker = CardInstance("attacker", GameCard("a", "Attacker", CardKind.CREATURE, 1), summoningSick = false)
        val triggerSpell = GameCard("trigger-boost", "Shield Boost", CardKind.SPELL, 5, shieldTrigger = true,
            effects = listOf(CardEffect(EffectKind.BOOST_FRIENDLY_CREATURES, 2000)))
        val friendly = CardInstance("friendly", GameCard("friendly-card", "Friendly", CardKind.CREATURE, 1, 1000), summoningSick = false)
        val drawCard = CardInstance("draw", GameCard("draw-card", "Draw", CardKind.CREATURE, 1))
        val state = GameState(listOf(
            PlayerState(0, "P1", emptyList(), battle = listOf(attacker)),
            PlayerState(1, "P2", listOf(drawCard), battle = listOf(friendly),
                shields = listOf(CardInstance("trigger-shield", triggerSpell)))
        ))
        val declared = (DuelMastersEngine.apply(state, GameAction.AttackShield("attacker")) as ActionResult.Success).state
        val afterShield = (DuelMastersEngine.apply(declared, GameAction.ResolveShieldTrigger(true)) as ActionResult.Success).state
        assertEquals(3000, afterShield.players[1].battle.single().currentPower)
        val ownersTurn = (DuelMastersEngine.apply(afterShield, GameAction.EndTurn) as ActionResult.Success).state
        assertEquals(3000, ownersTurn.players[1].battle.single().currentPower)
        val afterOwnersTurn = (DuelMastersEngine.apply(ownersTurn, GameAction.EndTurn) as ActionResult.Success).state
        assertEquals(1000, afterOwnersTurn.players[1].battle.single().currentPower)
    }

    @Test fun shieldTriggerKeywordFromCatalogueMechanicsIsHonoured() {
        val keywords = CardTextParser.keywords("", setOf("Shield Trigger"))
        assertTrue("SHIELD_TRIGGER" in keywords)
    }

    @Test fun defenderCanDeclineAShieldTriggerAndTakeTheCardIntoHand() {
        val attacker = CardInstance("attacker", GameCard("a", "Attacker", CardKind.CREATURE, 1), summoningSick = false)
        val shieldCard = GameCard("trigger", "Trigger Spell", CardKind.SPELL, 5, shieldTrigger = true,
            effects = listOf(CardEffect(EffectKind.DRAW_CARDS)))
        val state = GameState(listOf(
            PlayerState(0, "P1", emptyList(), battle = listOf(attacker)),
            PlayerState(1, "P2", emptyList(), shields = listOf(CardInstance("shield", shieldCard)))
        ))
        val declared = (DuelMastersEngine.apply(state, GameAction.AttackShield("attacker")) as ActionResult.Success).state
        val declined = (DuelMastersEngine.apply(declared, GameAction.ResolveShieldTrigger(false)) as ActionResult.Success).state
        assertEquals("Trigger Spell", declined.players[1].hand.single().card.name)
        assertTrue(declined.players[1].graveyard.isEmpty())
        assertTrue(declined.players[1].deck.isEmpty())
    }

    @Test fun defenderChoosesWhichOfSeveralBlockersIntercepts() {
        val attacker = CardInstance("attacker", GameCard("a", "Attacker", CardKind.CREATURE, 1, 3000), summoningSick = false)
        val first = CardInstance("first", GameCard("f", "First Blocker", CardKind.CREATURE, 1, 1000, keywords = setOf("BLOCKER")))
        val second = CardInstance("second", GameCard("s", "Second Blocker", CardKind.CREATURE, 1, 5000, keywords = setOf("BLOCKER")))
        val shield = CardInstance("shield", GameCard("shield", "Shield", CardKind.CREATURE, 1))
        val state = GameState(listOf(
            PlayerState(0, "P1", emptyList(), battle = listOf(attacker)),
            PlayerState(1, "P2", emptyList(), shields = listOf(shield), battle = listOf(first, second))
        ))
        val declared = (DuelMastersEngine.apply(state, GameAction.AttackShield("attacker")) as ActionResult.Success).state
        val resolved = (DuelMastersEngine.apply(declared, GameAction.ChooseBlocker("second")) as ActionResult.Success).state
        assertEquals(1, resolved.players[1].shields.size)
        // The selected 5,000-power blocker intercepts and survives the 3,000-power attacker.
        assertTrue(resolved.players[1].battle.any { it.instanceId == "second" && it.tapped })
        assertTrue(resolved.players[1].battle.any { it.instanceId == "first" && !it.tapped })
        assertTrue(resolved.players[0].battle.none { it.instanceId == "attacker" })
        assertTrue(resolved.pendingChoice == null)
    }

    @Test fun destroyTargetEffectWaitsForChosenCreature() {
        val spell = CardInstance("spell", GameCard("spell", "Removal", CardKind.SPELL, 1,
            effects = listOf(CardEffect(EffectKind.DESTROY_TARGET_OPPONENT))))
        val mana = CardInstance("mana", GameCard("mana", "Mana", CardKind.CREATURE, 1))
        val small = CardInstance("small", GameCard("small", "Small", CardKind.CREATURE, 1, 1000))
        val large = CardInstance("large", GameCard("large", "Large", CardKind.CREATURE, 1, 5000))
        val state = GameState(listOf(
            PlayerState(0, "P1", emptyList(), hand = listOf(spell), mana = listOf(mana)),
            PlayerState(1, "P2", emptyList(), battle = listOf(small, large))
        ))
        val cast = (DuelMastersEngine.apply(state, GameAction.CastSpell("spell")) as ActionResult.Success).state
        assertTrue(cast.pendingChoice is PendingChoice.EffectTarget)
        val resolved = (DuelMastersEngine.apply(cast, GameAction.ChooseEffectTarget("large")) as ActionResult.Success).state
        assertEquals(listOf("small"), resolved.players[1].battle.map { it.instanceId })
        assertEquals("large", resolved.players[1].graveyard.single().instanceId)
    }

    @Test fun evolutionRequiresMatchingRaceAndCarriesTheHostCardInItsStack() {
        val evolution = GameCard(
            "evo", "Evolution", CardKind.CREATURE, 2, 5000,
            races = setOf("Phoenix"), evolutionRace = "Beast Folk", isEvolution = true
        )
        val host = CardInstance("host", GameCard("host-card", "Host", CardKind.CREATURE, 1, 2000, races = setOf("Beast Folk")))
        val wrongHost = CardInstance("wrong", GameCard("wrong-card", "Wrong", CardKind.CREATURE, 1, 1000, races = setOf("Guardian")))
        val hand = CardInstance("evo-hand", evolution)
        val mana = listOf(
            CardInstance("m1", GameCard("m1", "M1", CardKind.CREATURE, 1)),
            CardInstance("m2", GameCard("m2", "M2", CardKind.CREATURE, 1))
        )
        val state = GameState(listOf(
            PlayerState(0, "P1", emptyList(), hand = listOf(hand), mana = mana, battle = listOf(host, wrongHost)),
            PlayerState(1, "P2", emptyList())
        ))
        val rejected = DuelMastersEngine.apply(state, GameAction.Summon("evo-hand", "wrong"))
        assertTrue(rejected is ActionResult.Rejected)
        val summoned = (DuelMastersEngine.apply(state, GameAction.Summon("evo-hand", "host")) as ActionResult.Success).state
        val evolved = summoned.players[0].battle.single { it.card.id == "evo" }
        assertEquals(1, evolved.evolvedCards.size)
        assertTrue(!evolved.summoningSick)
        assertTrue(summoned.players[0].battle.none { it.instanceId == "host" })
    }

    @Test fun destroyingAnEvolutionCreatureMovesItsWholeStackToGraveyard() {
        val base = CardInstance("base", GameCard("base", "Base", CardKind.CREATURE, 1, 1000))
        val evolution = CardInstance("evo", GameCard("evo", "Evolution", CardKind.CREATURE, 3, 5000),
            summoningSick = false, evolvedCards = listOf(base))
        val targetSpell = CardEffect(EffectKind.DESTROY_WEAKEST_OPPONENT)
        val spell = CardInstance("spell", GameCard("spell", "Destroy", CardKind.SPELL, 1, effects = listOf(targetSpell)))
        val mana = CardInstance("mana", GameCard("mana", "Mana", CardKind.CREATURE, 1))
        val state = GameState(listOf(
            PlayerState(0, "P1", emptyList(), hand = listOf(spell), mana = listOf(mana)),
            PlayerState(1, "P2", emptyList(), battle = listOf(evolution))
        ))
        val result = (DuelMastersEngine.apply(state, GameAction.CastSpell("spell")) as ActionResult.Success).state
        assertEquals(setOf("base", "evo"), result.players[1].graveyard.map { it.instanceId }.toSet())
    }

    @Test fun aiCanChooseAValidDefensiveBlocker() {
        val attacker = CardInstance("attacker", GameCard("a", "Attacker", CardKind.CREATURE, 1, 1000), summoningSick = false)
        val blocker = CardInstance("blocker", GameCard("b", "Blocker", CardKind.CREATURE, 1, 3000, keywords = setOf("BLOCKER")))
        val shield = CardInstance("shield", GameCard("shield", "Shield", CardKind.CREATURE, 1))
        val state = GameState(listOf(
            PlayerState(0, "P1", emptyList(), battle = listOf(attacker)),
            PlayerState(1, "P2", emptyList(), shields = listOf(shield), battle = listOf(blocker))
        ))
        val declared = (DuelMastersEngine.apply(state, GameAction.AttackShield("attacker")) as ActionResult.Success).state
        val response = DuelMastersAi.respondToChoice(declared, AiDifficulty.HARD)
        assertTrue(response is GameAction.ChooseBlocker)
        assertTrue(DuelMastersEngine.apply(declared, response!!) is ActionResult.Success)
    }


    @Test fun parserOnlyMarksFullyRecognizedRulesTextAsPlayable() {
        assertEquals("SUPPORTED", CardTextParser.supportStatus("SPELL", "Destroy one of your opponent's creatures."))
        assertEquals("METADATA_ONLY", CardTextParser.supportStatus("SPELL", "Destroy target creature."))
        assertEquals("METADATA_ONLY", CardTextParser.supportStatus("SPELL", "Draw up to 2 cards."))
        assertEquals("SUPPORTED", CardTextParser.supportStatus("CREATURE", "This creature has Blocker."))
        assertEquals("METADATA_ONLY", CardTextParser.supportStatus(
            "SPELL", "Search your deck for a creature and put it into your hand."
        ))
        assertEquals("METADATA_ONLY", CardTextParser.supportStatus("CROSS_GEAR", ""))
    }

    @Test fun parserReadsEvolutionRaceAndPowerAttackerBonus() {
        assertEquals(
            "Beast Folk",
            CardTextParser.evolutionRace("Evolution—Put on one of your Beast Folk creatures.")
        )
        assertEquals(3000, CardTextParser.powerAttackBonus("Power Attacker +3,000"))
        assertEquals(2000, CardTextParser.powerAttackBonus("Power Attacker +2000"))
    }

    @Test fun parserRejectsCardsWithMissingOrVariableCoreStats() {
        assertEquals(
            "METADATA_ONLY",
            CardTextParser.supportStatus(
                "CREATURE", "", cost = null, powerText = "3000", requireCoreStats = true
            )
        )
        assertEquals(
            "METADATA_ONLY",
            CardTextParser.supportStatus(
                "CREATURE", "", cost = 3, powerText = "3000+", requireCoreStats = true
            )
        )
        assertEquals(
            "METADATA_ONLY",
            CardTextParser.supportStatus(
                "SPELL", "", cost = null, requireCoreStats = true
            )
        )
        assertEquals(
            "SUPPORTED",
            CardTextParser.supportStatus(
                "CREATURE", "", cost = 3, powerText = "4000", requireCoreStats = true
            )
        )
        assertEquals(
            "SUPPORTED",
            CardTextParser.supportStatus(
                "SPELL", "", cost = 2, requireCoreStats = true
            )
        )
    }

    @Test fun evolutionInheritsTapStateAndTemporaryPowerFromItsSource() {
        val host = CardInstance(
            "host",
            GameCard("host-card", "Tapped Host", CardKind.CREATURE, 1, 3000, races = setOf("Beast Folk")),
            tapped = true,
            summoningSick = true,
            powerModifier = 2000,
            powerModifierExpiresAtTurn = 4
        )
        val evolution = CardInstance(
            "evo-hand",
            GameCard(
                "evo", "Evolution", CardKind.CREATURE, 2, 5000,
                races = setOf("Phoenix"), evolutionRace = "Beast Folk", isEvolution = true
            )
        )
        val mana = listOf(
            CardInstance("m1", GameCard("m1", "M1", CardKind.CREATURE, 1)),
            CardInstance("m2", GameCard("m2", "M2", CardKind.CREATURE, 1))
        )
        val state = GameState(listOf(
            PlayerState(0, "P1", emptyList(), hand = listOf(evolution), mana = mana, battle = listOf(host)),
            PlayerState(1, "P2", emptyList())
        ))

        val result = (DuelMastersEngine.apply(state, GameAction.Summon("evo-hand", "host")) as ActionResult.Success).state
        val evolved = result.players[0].battle.single { it.card.id == "evo" }
        assertTrue("Evolution must inherit the source creature's tapped state.", evolved.tapped)
        assertFalse("Evolution creatures do not have summoning sickness.", evolved.summoningSick)
        assertEquals(2000, evolved.powerModifier)
        assertEquals(4, evolved.powerModifierExpiresAtTurn)
        assertEquals(7000, evolved.currentPower)
    }

    @Test fun manaPaymentRequiresEveryCardCivilizationFromDistinctSources() {
        val spell = CardInstance(
            "spell",
            GameCard("spell-card", "Water Darkness Spell", CardKind.SPELL, 2,
                civilizations = setOf("WATER", "DARKNESS"))
        )
        val sharedSource = CardInstance(
            "shared",
            GameCard("shared-card", "Water Darkness Mana", CardKind.CREATURE, 1,
                civilizations = setOf("WATER", "DARKNESS"))
        )
        val insufficient = GameState(listOf(
            PlayerState(0, "P1", emptyList(), hand = listOf(spell), mana = listOf(sharedSource)),
            PlayerState(1, "P2", emptyList())
        ))
        assertTrue(DuelMastersEngine.apply(insufficient, GameAction.CastSpell("spell")) is ActionResult.Rejected)

        val darknessSource = CardInstance("dark", GameCard("dark-card", "Darkness Mana", CardKind.CREATURE, 1,
            civilizations = setOf("DARKNESS")))
        val sufficient = insufficient.copy(
            players = listOf(
                insufficient.players[0].copy(mana = listOf(sharedSource, darknessSource)),
                insufficient.players[1]
            )
        )
        val result = DuelMastersEngine.apply(sufficient, GameAction.CastSpell("spell")) as ActionResult.Success
        assertTrue(result.state.players[0].mana.first { it.instanceId == "shared" }.tapped)
        assertTrue(result.state.players[0].mana.first { it.instanceId == "dark" }.tapped)
    }

    @Test fun fiveCivilizationManaCanPayOneColorButHasNoNumericValue() {
        val spell = CardInstance(
            "spell",
            GameCard("spell-card", "Water Spell", CardKind.SPELL, 1, civilizations = setOf("WATER"))
        )
        val fiveColorMana = CardInstance(
            "five-color",
            GameCard(
                "five-color-card", "Five Civilization Mana", CardKind.CREATURE, 5,
                civilizations = setOf("LIGHT", "WATER", "DARKNESS", "FIRE", "NATURE")
            )
        )
        val fireMana = CardInstance(
            "fire",
            GameCard("fire-card", "Fire Mana", CardKind.CREATURE, 1, civilizations = setOf("FIRE"))
        )
        val state = GameState(listOf(
            PlayerState(0, "P1", emptyList(), hand = listOf(spell), mana = listOf(fiveColorMana, fireMana)),
            PlayerState(1, "P2", emptyList())
        ))
        val result = DuelMastersEngine.apply(state, GameAction.CastSpell("spell")) as ActionResult.Success
        assertTrue(result.state.players[0].mana.first { it.instanceId == "five-color" }.tapped)
        assertTrue(result.state.players[0].mana.first { it.instanceId == "fire" }.tapped)
        assertEquals(0, GameCard(
            "five", "Five", CardKind.CREATURE, 5,
            civilizations = setOf("LIGHT", "WATER", "DARKNESS", "FIRE", "NATURE")
        ).manaValue)
    }

    @Test fun zeroNumericCostStillRequiresItsCivilizationSource() {
        val zeroCostWaterSpell = CardInstance(
            "zero-spell",
            GameCard("zero-spell-card", "Zero Cost Water", CardKind.SPELL, 0, civilizations = setOf("WATER"))
        )
        val fireMana = CardInstance(
            "fire",
            GameCard("fire-card", "Fire Mana", CardKind.CREATURE, 1, civilizations = setOf("FIRE"))
        )
        val valid = GameState(listOf(
            PlayerState(0, "P1", emptyList(), hand = listOf(zeroCostWaterSpell), mana = listOf(fireMana)),
            PlayerState(1, "P2", emptyList())
        ))
        assertTrue(DuelMastersEngine.apply(valid, GameAction.CastSpell("zero-spell")) is ActionResult.Rejected)
    }

    @Test fun wrongCivilizationManaCannotPayForSpell() {
        val spell = CardInstance(
            "spell",
            GameCard("spell-card", "Water Spell", CardKind.SPELL, 1, civilizations = setOf("WATER"))
        )
        val fireMana = CardInstance(
            "fire-mana",
            GameCard("fire-card", "Fire Mana", CardKind.CREATURE, 1, civilizations = setOf("FIRE"))
        )
        val state = GameState(listOf(
            PlayerState(0, "P1", emptyList(), hand = listOf(spell), mana = listOf(fireMana)),
            PlayerState(1, "P2", emptyList())
        ))
        assertTrue(DuelMastersEngine.apply(state, GameAction.CastSpell("spell")) is ActionResult.Rejected)
    }

    @Test fun multicoloredManaEntersTappedAndFiveCivilizationManaHasZeroValue() {
        val multicolored = CardInstance(
            "multi",
            GameCard("multi-card", "Light Water Mana", CardKind.CREATURE, 2,
                civilizations = setOf("LIGHT", "WATER"))
        )
        val fiveCivilization = GameCard(
            "five", "Five Civilization Mana", CardKind.CREATURE, 5,
            civilizations = setOf("LIGHT", "WATER", "DARKNESS", "FIRE", "NATURE")
        )
        val handState = GameState(listOf(
            PlayerState(0, "P1", emptyList(), hand = listOf(multicolored)),
            PlayerState(1, "P2", emptyList())
        ))
        val charged = (DuelMastersEngine.apply(handState, GameAction.ChargeMana("multi")) as ActionResult.Success).state
        assertTrue(charged.players[0].mana.single().tapped)
        assertEquals(0, charged.players[0].availableMana)
        assertEquals(0, fiveCivilization.manaValue)
    }

    @Test fun shieldTriggerResolutionContinuesRemainingDoubleBreakerShieldSteps() {
        val attacker = CardInstance(
            "attacker",
            GameCard("attacker-card", "Double Breaker", CardKind.CREATURE, 1, 3000,
                keywords = setOf("DOUBLE_BREAKER")),
            summoningSick = false
        )
        val trigger = GameCard(
            "trigger", "Shield Draw", CardKind.SPELL, 4, shieldTrigger = true,
            effects = listOf(CardEffect(EffectKind.DRAW_CARDS))
        )
        val normalShield = CardInstance("normal", GameCard("normal", "Normal Shield", CardKind.CREATURE, 1))
        val drawn = CardInstance("drawn", GameCard("drawn", "Drawn Card", CardKind.CREATURE, 1))
        val state = GameState(listOf(
            PlayerState(0, "P1", emptyList(), battle = listOf(attacker)),
            PlayerState(1, "P2", listOf(drawn), shields = listOf(CardInstance("trigger-shield", trigger), normalShield))
        ))
        val declared = (DuelMastersEngine.apply(state, GameAction.AttackShield("attacker")) as ActionResult.Success).state
        assertTrue(declared.pendingChoice is PendingChoice.ShieldTrigger)
        val resolved = (DuelMastersEngine.apply(declared, GameAction.ResolveShieldTrigger(true)) as ActionResult.Success).state
        assertTrue(resolved.pendingChoice == null)
        assertEquals(0, resolved.players[1].shields.size)
        assertEquals("Drawn Card", resolved.players[1].hand.first().card.name)
        assertEquals("Normal Shield", resolved.players[1].hand.last().card.name)
    }

    @Test fun tappedTargetEffectOnlyOffersUntappedCreatures() {
        val spell = CardInstance("spell", GameCard("spell-card", "Tap", CardKind.SPELL, 1,
            effects = listOf(CardEffect(EffectKind.TAP_TARGET_OPPONENT))))
        val mana = CardInstance("mana", GameCard("mana-card", "Mana", CardKind.CREATURE, 1))
        val alreadyTapped = CardInstance("tapped", GameCard("tapped-card", "Tapped", CardKind.CREATURE, 1), tapped = true)
        val ready = CardInstance("ready", GameCard("ready-card", "Ready", CardKind.CREATURE, 1))
        val state = GameState(listOf(
            PlayerState(0, "P1", emptyList(), hand = listOf(spell), mana = listOf(mana)),
            PlayerState(1, "P2", emptyList(), battle = listOf(alreadyTapped, ready))
        ))
        val cast = (DuelMastersEngine.apply(state, GameAction.CastSpell("spell")) as ActionResult.Success).state
        val choice = cast.pendingChoice as PendingChoice.EffectTarget
        assertEquals(listOf("ready"), choice.targetInstanceIds)
        val resolved = (DuelMastersEngine.apply(cast, GameAction.ChooseEffectTarget("ready")) as ActionResult.Success).state
        assertTrue(resolved.players[1].battle.first { it.instanceId == "ready" }.tapped)
        assertTrue(resolved.players[1].battle.first { it.instanceId == "tapped" }.tapped)
    }


    @Test fun returningAnEvolutionCreatureSendsOnlyTheTopCardToHand() {
        val base = CardInstance("base", GameCard("base", "Base", CardKind.CREATURE, 1, 1000))
        val evolution = CardInstance("evo", GameCard("evo", "Evolution", CardKind.CREATURE, 3, 5000),
            summoningSick = false, evolvedCards = listOf(base))
        val spell = CardInstance("spell", GameCard("spell", "Bounce", CardKind.SPELL, 1,
            effects = listOf(CardEffect(EffectKind.RETURN_WEAKEST_OPPONENT))))
        val mana = CardInstance("mana", GameCard("mana-card", "Mana", CardKind.CREATURE, 1))
        val state = GameState(listOf(
            PlayerState(0, "P1", emptyList(), hand = listOf(spell), mana = listOf(mana)),
            PlayerState(1, "P2", emptyList(), battle = listOf(evolution))
        ))
        val result = (DuelMastersEngine.apply(state, GameAction.CastSpell("spell")) as ActionResult.Success).state
        assertEquals(listOf("evo"), result.players[1].hand.map { it.instanceId })
        assertEquals(listOf("base"), result.players[1].graveyard.map { it.instanceId })
        assertTrue(result.players[1].battle.isEmpty())
    }

    @Test fun hardAiCompletesAFullOfflineMatchThroughPendingChoices() {
        val definitions = listOf(
            GameCard("vanilla", "Vanilla", CardKind.CREATURE, 1, 1000),
            GameCard("fighter", "Fighter", CardKind.CREATURE, 2, 3000),
            GameCard("blocker", "Blocker", CardKind.CREATURE, 2, 2000, keywords = setOf("BLOCKER")),
            GameCard("speed", "Speed", CardKind.CREATURE, 2, 2000, keywords = setOf("SPEED_ATTACKER")),
            GameCard("double", "Double", CardKind.CREATURE, 4, 5000, keywords = setOf("DOUBLE_BREAKER")),
            GameCard("slayer", "Slayer", CardKind.CREATURE, 3, 1000, keywords = setOf("SLAYER")),
            GameCard("draw", "Draw Spell", CardKind.SPELL, 2, effects = listOf(CardEffect(EffectKind.DRAW_CARDS))),
            GameCard("boost", "Boost Spell", CardKind.SPELL, 2,
                effects = listOf(CardEffect(EffectKind.BOOST_FRIENDLY_CREATURES, 1000))),
            GameCard("trigger", "Trigger Spell", CardKind.SPELL, 4, shieldTrigger = true,
                effects = listOf(CardEffect(EffectKind.DRAW_CARDS))),
            GameCard("trigger-creature", "Trigger Creature", CardKind.CREATURE, 5, 4000, shieldTrigger = true)
        )
        val deck = definitions.flatMap { definition -> List(4) { definition } }
        var state = DuelMastersEngine.newGame(deck, deck, "AI One", "AI Two")
        var actionCount = 0

        while (state.winner == null && state.turn < 80 && actionCount < 4000) {
            val action = if (state.pendingChoice != null) {
                DuelMastersAi.respondToChoice(state, AiDifficulty.HARD, kotlin.random.Random(actionCount))
            } else {
                DuelMastersAi.chooseAction(state, AiDifficulty.HARD, kotlin.random.Random(actionCount))
            }
            assertTrue("AI must always provide a response to a pending choice.", action != null)
            when (val result = DuelMastersEngine.apply(state, action!!)) {
                is ActionResult.Success -> state = result.state
                is ActionResult.Rejected -> throw AssertionError("AI produced an illegal action: ${result.reason}")
            }
            actionCount++
        }

        assertTrue("The simulated match should reach a winner before the safety cap.", state.winner != null)
        assertTrue("The simulation should complete within the action safety cap.", actionCount < 4000)
    }


}
