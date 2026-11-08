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
}
