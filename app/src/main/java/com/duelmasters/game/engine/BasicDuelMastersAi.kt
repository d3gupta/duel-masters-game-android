package com.duelmasters.game.engine

/**
 * Deterministic baseline opponent. It only submits normal engine actions and
 * never reads deck order, hidden shields, or the opponent's hand.
 */
object BasicDuelMastersAi {
    fun chooseAction(state: GameState): GameAction {
        val player = state.players[state.activePlayer]
        val playableCreature = player.hand
            .filter { it.card.kind == CardKind.CREATURE && it.card.cost <= player.availableMana }
            .maxByOrNull { it.card.power }
        if (playableCreature != null) return GameAction.Summon(playableCreature.instanceId)

        val playableSpell = player.hand
            .filter { it.card.kind == CardKind.SPELL && it.card.cost <= player.availableMana }
            .minByOrNull { it.card.cost }
        if (playableSpell != null) return GameAction.CastSpell(playableSpell.instanceId)

        val attacker = player.battle.firstOrNull {
            !it.tapped && (!it.summoningSick || "SPEED_ATTACKER" in it.card.keywords)
        }
        if (attacker != null) return GameAction.AttackShield(attacker.instanceId)

        val manaCard = player.hand.firstOrNull()
        if (manaCard != null && player.mana.size < 10) return GameAction.ChargeMana(manaCard.instanceId)
        return GameAction.EndTurn
    }
}
