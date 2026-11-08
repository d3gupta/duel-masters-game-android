package com.duelmasters.game.engine

import kotlin.random.Random

enum class AiDifficulty { EASY, NORMAL, HARD }

/** Deterministic rules-aware policies; the engine remains the authority on legality. */
object DuelMastersAi {
    fun chooseAction(
        state: GameState,
        difficulty: AiDifficulty = AiDifficulty.NORMAL,
        random: Random = Random.Default
    ): GameAction {
        require(state.pendingChoice == null) { "Use respondToChoice while a player decision is pending." }
        val me = state.players[state.activePlayer]
        val enemy = state.players[1 - state.activePlayer]
        val legal = mutableListOf<GameAction>()

        if (!state.manaChargedThisTurn) {
            me.hand.forEach { legal += GameAction.ChargeMana(it.instanceId) }
        }

        me.hand.filter { it.card.kind == CardKind.CREATURE && it.card.cost <= me.availableMana }.forEach { card ->
            if (card.card.isEvolution || card.card.evolutionRace != null) {
                val race = card.card.evolutionRace
                if (race != null) me.battle.filter { host ->
                    host.card.races.any { it.equals(race, ignoreCase = true) }
                }.forEach { host -> legal += GameAction.Summon(card.instanceId, host.instanceId) }
            } else {
                legal += GameAction.Summon(card.instanceId)
            }
        }
        me.hand.filter { it.card.kind == CardKind.SPELL && it.card.cost <= me.availableMana }
            .forEach { legal += GameAction.CastSpell(it.instanceId) }

        me.battle.filter { !it.tapped && (!it.summoningSick || "SPEED_ATTACKER" in it.card.keywords) }.forEach { attacker ->
            legal += GameAction.AttackShield(attacker.instanceId)
            enemy.battle.filter { it.tapped }.forEach { target ->
                legal += GameAction.AttackCreature(attacker.instanceId, target.instanceId)
            }
        }
        legal += GameAction.EndTurn

        if (difficulty == AiDifficulty.EASY) return legal[random.nextInt(legal.size)]
        val ranked = legal.map { it to score(it, me, enemy, difficulty) }
        val highest = ranked.maxOf { it.second }
        val tied = ranked.filter { it.second == highest }
        return if (difficulty == AiDifficulty.HARD) tied.last().first else tied.first().first
    }

    /** AI's response to a defender/caster choice. */
    fun respondToChoice(
        state: GameState,
        difficulty: AiDifficulty = AiDifficulty.NORMAL,
        random: Random = Random.Default
    ): GameAction? = when (val choice = state.pendingChoice) {
        is PendingChoice.Blocker -> {
            val attacker = state.players[choice.attackingPlayerId].battle
                .firstOrNull { it.instanceId == choice.attackerInstanceId } ?: return null
            val defender = state.players[choice.chooserPlayerId]
            val blockers = choice.blockerInstanceIds.mapNotNull { id ->
                defender.battle.firstOrNull { it.instanceId == id && !it.tapped }
            }
            if (blockers.isEmpty()) GameAction.ChooseBlocker(null)
            else {
                val selected = when (difficulty) {
                    AiDifficulty.EASY -> blockers.random(random)
                    AiDifficulty.NORMAL -> blockers.maxByOrNull {
                        val canKill = it.currentPower + it.card.powerAttackBonus >= attacker.currentPower
                        (if (canKill) 100 else 0) + it.currentPower / 100
                    }?.takeIf { it.currentPower + it.card.powerAttackBonus >= attacker.currentPower }
                    AiDifficulty.HARD -> blockers.maxByOrNull {
                        val attackerDies = it.currentPower + it.card.powerAttackBonus >= attacker.currentPower || "SLAYER" in it.card.keywords
                        val blockerDies = attacker.currentPower + attacker.card.powerAttackBonus >= it.currentPower || "SLAYER" in attacker.card.keywords
                        (if (attackerDies) 1000 else 0) - (if (blockerDies) 300 else 0) + it.currentPower / 100
                    }?.takeIf {
                        it.currentPower + it.card.powerAttackBonus >= attacker.currentPower ||
                            "SLAYER" in it.card.keywords
                    }
                }
                GameAction.ChooseBlocker(selected?.instanceId)
            }
        }
        is PendingChoice.ShieldTrigger -> {
            val shouldUse = when (difficulty) {
                AiDifficulty.EASY -> random.nextBoolean()
                AiDifficulty.NORMAL -> choice.shield.card.kind == CardKind.CREATURE ||
                    choice.shield.card.effects.any { it.kind != EffectKind.DESTROY_TARGET_OPPONENT &&
                        it.kind != EffectKind.RETURN_TARGET_OPPONENT && it.kind != EffectKind.TAP_TARGET_OPPONENT } ||
                    state.players[1 - choice.chooserPlayerId].battle.isNotEmpty()
                AiDifficulty.HARD -> true
            }
            GameAction.ResolveShieldTrigger(shouldUse)
        }
        is PendingChoice.EffectTarget -> {
            val targets = state.players[choice.targetPlayerId].battle.filter { it.instanceId in choice.targetInstanceIds }
            if (targets.isEmpty()) null
            else {
                val target = when (choice.effect.kind) {
                    EffectKind.TAP_TARGET_OPPONENT -> targets.maxByOrNull { it.currentPower }
                    else -> targets.maxByOrNull { it.currentPower }
                } ?: return null
                GameAction.ChooseEffectTarget(target.instanceId)
            }
        }
        null -> null
    }

    private fun score(
        action: GameAction,
        me: PlayerState,
        enemy: PlayerState,
        difficulty: AiDifficulty
    ): Int = when (action) {
        is GameAction.ChargeMana -> {
            val card = me.hand.firstOrNull { it.instanceId == action.handInstanceId }?.card
            when {
                card == null -> -100
                me.mana.size < 6 -> 100 + card.cost
                me.mana.size < 8 -> 65 + card.cost
                me.hand.none { it.card.cost > me.mana.size } && me.mana.size >= 8 -> 0
                else -> 20 + card.cost
            }
        }
        is GameAction.Summon -> {
            val card = me.hand.first { it.instanceId == action.handInstanceId }.card
            90 + card.power / 100 + card.cost * if (difficulty == AiDifficulty.HARD) 3 else 1 +
                if (action.evolutionTargetInstanceId != null) 25 else 0
        }
        is GameAction.CastSpell -> {
            val card = me.hand.first { it.instanceId == action.handInstanceId }.card
            val expectedValue = card.effects.sumOf { effect ->
                when (effect.kind) {
                    EffectKind.DRAW_CARDS, EffectKind.ADD_TOP_CARD_TO_MANA, EffectKind.ADD_TOP_CARD_TO_HAND -> 20 * effect.amount
                    EffectKind.DESTROY_WEAKEST_OPPONENT, EffectKind.DESTROY_TARGET_OPPONENT,
                    EffectKind.RETURN_WEAKEST_OPPONENT, EffectKind.RETURN_TARGET_OPPONENT,
                    EffectKind.DESTROY_OPPONENT_CREATURES_POWER_OR_LESS -> 45
                    EffectKind.BOOST_FRIENDLY_CREATURES -> me.battle.size * 8
                    else -> 15
                }
            }
            75 + expectedValue - card.cost
        }
        is GameAction.AttackShield -> {
            val card = me.battle.first { it.instanceId == action.attackerInstanceId }.card
            80 + when {
                "TRIPLE_BREAKER" in card.keywords -> 45
                "DOUBLE_BREAKER" in card.keywords -> 25
                else -> 0
            } + if (enemy.shields.isEmpty()) 1000 else 0
        }
        is GameAction.AttackCreature -> {
            val attacker = me.battle.first { it.instanceId == action.attackerInstanceId }
            val target = enemy.battle.first { it.instanceId == action.defenderInstanceId }
            70 + target.currentPower / 100 -
                (if (attacker.currentPower + attacker.card.powerAttackBonus < target.currentPower &&
                    "SLAYER" !in attacker.card.keywords) 25 else 0)
        }
        GameAction.EndTurn -> 1
        is GameAction.ChooseBlocker, is GameAction.ResolveShieldTrigger, is GameAction.ChooseEffectTarget -> -1000
    }
}
