package com.duelmasters.game.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GameEngineTest {
    private fun creature(name: String = "Test Creature", abilities: Set<Ability> = emptySet(), power: Int = 2000) =
        GameCard(id = name, name = name, cost = 1, kind = CardKind.CREATURE, power = power, abilities = abilities)

    @Test fun gameSetupUsesFiveShieldsAndFiveCardsInHand() {
        val deck = List(40) { creature("Card$it") }
        val state = GameEngine.newGame(deck, deck, Random(1))
        state.players.forEach {
            assertEquals(5, it.shields.size)
            assertEquals(5, it.hand.size)
            assertEquals(30, it.deck.size)
        }
    }

    @Test fun deckValidatorHasMinimumButNoMaximum() {
        val fortyOne = List(41) { creature("Card$it") }
        assertTrue(DeckValidator.validate(fortyOne).isValid)
        assertFalse(DeckValidator.validate(fortyOne.take(39)).isValid)
    }

    @Test fun doubleBreakerBreaksTwoShields() {
        val attacker = CardInstance(card = creature("Double", setOf(Ability.DOUBLE_BREAKER)))
        val shields = listOf(CardInstance(card = creature("Shield1")), CardInstance(card = creature("Shield2")))
        val p1 = PlayerState(1, emptyList(), battle = listOf(attacker))
        val p2 = PlayerState(2, emptyList(), shields = shields)
        val state = GameState(listOf(p1, p2))
        val result = GameEngine.attack(state, attacker.instanceId) as GameResult.Success
        assertEquals(0, result.state.player(2).shields.size)
        assertEquals(2, result.state.player(2).hand.size)
    }

    @Test fun shieldTriggerCreatureIsPlayedForFree() {
        val attacker = CardInstance(card = creature("Attacker"))
        val trigger = CardInstance(card = creature("Trigger").copy(hasShieldTrigger = true))
        val p1 = PlayerState(1, emptyList(), battle = listOf(attacker))
        val p2 = PlayerState(2, emptyList(), shields = listOf(trigger))
        val result = GameEngine.attack(GameState(listOf(p1, p2)), attacker.instanceId) as GameResult.Success
        assertEquals(1, result.state.player(2).battle.size)
        assertTrue(result.state.player(2).hand.isEmpty())
        assertTrue(result.state.log.any { it.contains("Shield Trigger") })
    }

    @Test fun slayerDestroysAttackingCreatureEvenWhenItHasMorePower() {
        val attacker = CardInstance(card = creature("Attacker", power = 8000))
        val slayer = CardInstance(card = creature("Slayer", setOf(Ability.SLAYER), power = 1000), tapped = true)
        val p1 = PlayerState(1, emptyList(), battle = listOf(attacker))
        val p2 = PlayerState(2, emptyList(), battle = listOf(slayer))
        val result = GameEngine.attack(GameState(listOf(p1, p2)), attacker.instanceId, slayer.instanceId) as GameResult.Success
        assertTrue(result.state.player(1).battle.isEmpty())
        assertTrue(result.state.player(2).battle.isEmpty())
        assertEquals(1, result.state.player(1).graveyard.size)
        assertEquals(1, result.state.player(2).graveyard.size)
    }
    @Test fun blockerInterceptsAttackOnShields() {
        val attacker = CardInstance(card = creature("Attacker", power = 3000))
        val blocker = CardInstance(card = creature("Blocker", setOf(Ability.BLOCKER), power = 2000))
        val shield = CardInstance(card = creature("Shield"))
        val p1 = PlayerState(1, emptyList(), battle = listOf(attacker))
        val p2 = PlayerState(2, emptyList(), shields = listOf(shield), battle = listOf(blocker))
        val result = GameEngine.attack(GameState(listOf(p1, p2)), attacker.instanceId) as GameResult.Success
        assertEquals(1, result.state.player(2).shields.size)
        assertTrue(result.state.player(2).battle.isEmpty())
        assertEquals(1, result.state.player(1).battle.size)
        assertTrue(result.state.player(1).battle.single().tapped)
    }

}
