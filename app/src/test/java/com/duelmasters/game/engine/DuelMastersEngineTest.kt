package com.duelmasters.game.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DuelMastersEngineTest {
    private fun deck(prefix: String) = List(40) { i ->
        GameCard("$prefix-$i", "Creature $i", CardKind.CREATURE, cost = 1, power = 1000)
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

    @Test fun rejectedActionDoesNotMutateOriginalState() {
        val game = DuelMastersEngine.newGame(deck("a"), deck("b"))
        val before = game.players[0]
        DuelMastersEngine.apply(game, GameAction.Summon(game.players[0].hand.first().instanceId))
        assertEquals(before, game.players[0])
    }
}
