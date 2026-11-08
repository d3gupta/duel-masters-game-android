package com.duelmasters.game.engine

import kotlin.random.Random

enum class AiDifficulty { EASY, NORMAL, HARD }

object DuelMastersAi {
    fun chooseAction(state: GameState, difficulty: AiDifficulty = AiDifficulty.NORMAL, random: Random = Random.Default): GameAction {
        val me = state.players[state.activePlayer]
        val enemy = state.players[1 - state.activePlayer]
        val legal = buildList<GameAction> {
            if (!state.manaChargedThisTurn && me.hand.isNotEmpty()) {
                add(GameAction.ChargeMana(me.hand.first().instanceId))
            }
            me.hand.filter { it.card.kind == CardKind.CREATURE && it.card.cost <= me.availableMana }
                .forEach { add(GameAction.Summon(it.instanceId)) }
            me.hand.filter { it.card.kind == CardKind.SPELL && it.card.cost <= me.availableMana }
                .forEach { add(GameAction.CastSpell(it.instanceId)) }
            me.battle.filter { !it.tapped && (!it.summoningSick || "SPEED_ATTACKER" in it.card.keywords) }.forEach { attacker ->
                add(GameAction.AttackShield(attacker.instanceId))
                enemy.battle.filter { it.tapped }.forEach { target ->
                    add(GameAction.AttackCreature(attacker.instanceId, target.instanceId))
                }
            }
        }
        if (legal.isEmpty()) return GameAction.EndTurn
        if (difficulty == AiDifficulty.EASY) return legal[random.nextInt(legal.size)]

        val scored = legal.map { action -> action to score(action, me, enemy, difficulty) }
        val highest = scored.maxOf { it.second }
        return scored.filter { it.second == highest }.let { tied ->
            if (difficulty == AiDifficulty.HARD) tied.last().first else tied.first().first
        }
    }

    private fun score(action: GameAction, me: PlayerState, enemy: PlayerState, difficulty: AiDifficulty): Int = when (action) {
        is GameAction.ChargeMana -> if (me.mana.size < if (difficulty == AiDifficulty.HARD) 8 else 5) 100 else 20
        is GameAction.Summon -> {
            val card = me.hand.first { it.instanceId == action.handInstanceId }.card
            80 + card.power / 100 + card.cost * if (difficulty == AiDifficulty.HARD) 2 else 1
        }
        is GameAction.CastSpell -> {
            val card = me.hand.first { it.instanceId == action.handInstanceId }.card
            70 + card.effects.sumOf { it.amount } * 5
        }
        is GameAction.AttackShield -> {
            val card = me.battle.first { it.instanceId == action.attackerInstanceId }.card
            90 + when {
                "TRIPLE_BREAKER" in card.keywords -> 30
                "DOUBLE_BREAKER" in card.keywords -> 20
                else -> 0
            } + if (enemy.shields.isEmpty()) 1000 else 0
        }
        is GameAction.AttackCreature -> {
            val attacker = me.battle.first { it.instanceId == action.attackerInstanceId }
            val target = enemy.battle.first { it.instanceId == action.defenderInstanceId }
            60 + (target.currentPower - attacker.currentPower).coerceAtMost(0) / 100 +
                if ("SLAYER" in attacker.card.keywords) 15 else 0
        }
        GameAction.EndTurn -> 0
    }
}
