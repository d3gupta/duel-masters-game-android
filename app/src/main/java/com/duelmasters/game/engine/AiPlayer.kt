package com.duelmasters.game.engine

import kotlin.random.Random

enum class AiDifficulty { EASY, NORMAL, HARD }
sealed interface AiAction {
    data class ChargeMana(val cardInstanceId: String) : AiAction
    data class Summon(val cardInstanceId: String) : AiAction
    data class Cast(val cardInstanceId: String, val targetCreatureId: String? = null) : AiAction
    data class Attack(val attackerInstanceId: String, val targetCreatureId: String? = null) : AiAction
    data object EndTurn : AiAction
}

/**
 * Chooses one legal next action. It never edits state directly; callers execute the
 * returned action through GameEngine so the AI follows the same rules as a human.
 */
class AiPlayer(private val difficulty: AiDifficulty, private val random: Random = Random.Default) {
    fun takeTurnStep(state: GameState): GameResult {
        return when (val action = chooseAction(state)) {
            is AiAction.ChargeMana -> GameEngine.chargeMana(state, action.cardInstanceId)
            is AiAction.Summon -> GameEngine.summonCreature(state, action.cardInstanceId)
            is AiAction.Cast -> GameEngine.castSpell(state, action.cardInstanceId, action.targetCreatureId)
            is AiAction.Attack -> GameEngine.attack(state, action.attackerInstanceId, action.targetCreatureId)
            AiAction.EndTurn -> GameResult.Success(GameEngine.endTurn(state))
        }
    }

    fun chooseAction(state: GameState): AiAction {
        val me = state.player(state.activePlayer)
        val enemy = state.opponent(me.id)
        val playable = me.hand.filter { it.card.cost <= me.mana.count { mana -> !mana.tapped } }
        val candidates = buildList {
            me.hand.firstOrNull()?.let { add(AiAction.ChargeMana(it.instanceId)) }
            playable.filter { it.card.kind == CardKind.CREATURE }.forEach { add(AiAction.Summon(it.instanceId)) }
            playable.filter { it.card.kind == CardKind.SPELL }.forEach { spell ->
                val target = enemy.battle.maxByOrNull { it.currentPower }?.instanceId
                add(AiAction.Cast(spell.instanceId, target))
            }
            me.battle.filter { !it.tapped && (!it.summonedThisTurn || Ability.SPEED_ATTACKER in it.card.abilities) }.forEach { creature ->
                val canBreakShields = enemy.shields.isNotEmpty()
                val target = if (difficulty == AiDifficulty.HARD && !canBreakShields) {
                    enemy.battle.minByOrNull { it.currentPower }?.instanceId
                } else null
                add(AiAction.Attack(creature.instanceId, target))
            }
        }
        if (candidates.isEmpty()) return AiAction.EndTurn
        if (difficulty == AiDifficulty.EASY) return candidates[random.nextInt(candidates.size)]
        val scored = candidates.map { it to score(it, me, enemy) }
        val best = scored.maxOf { it.second }
        val tied = scored.filter { it.second == best }.map { it.first }
        return if (difficulty == AiDifficulty.NORMAL) tied.first() else tied.last()
    }

    private fun score(action: AiAction, me: PlayerState, enemy: PlayerState): Int = when (action) {
        is AiAction.ChargeMana -> if (me.mana.size < 6) 80 - me.mana.size else 15
        is AiAction.Summon -> {
            val card = me.hand.first { it.instanceId == action.cardInstanceId }.card
            100 + card.power / 100 + card.cost
        }
        is AiAction.Cast -> {
            val card = me.hand.first { it.instanceId == action.cardInstanceId }.card
            90 + card.effects.sumOf { it.amount } * 5
        }
        is AiAction.Attack -> {
            val creature = me.battle.first { it.instanceId == action.attackerInstanceId }
            if (action.targetCreatureId == null) 120 + if (enemy.shields.isEmpty()) 1000 else 0
            else {
                val target = enemy.battle.first { it.instanceId == action.targetCreatureId }
                70 + (target.currentPower - creature.currentPower).coerceAtMost(0) / 100
            }
        }
        AiAction.EndTurn -> 0
    }
}
