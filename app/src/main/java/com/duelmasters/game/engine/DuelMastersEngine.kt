package com.duelmasters.game.engine

/**
 * Platform-independent Duel Masters rules core. The UI and AI must issue the same
 * actions against this state machine; no action mutates state unless it is legal.
 */
enum class CardKind { CREATURE, SPELL }
enum class Zone { DECK, HAND, MANA, BATTLE, SHIELD, GRAVEYARD }

data class GameCard(
    val id: String,
    val name: String,
    val kind: CardKind,
    val cost: Int,
    val power: Int = 0,
    val civilizations: Set<String> = emptySet(),
    val keywords: Set<String> = emptySet(),
    val shieldTrigger: Boolean = false
)

data class CardInstance(val instanceId: String, val card: GameCard, val tapped: Boolean = false, val summoningSick: Boolean = true)

data class PlayerState(
    val id: Int,
    val name: String,
    val deck: List<CardInstance>,
    val hand: List<CardInstance> = emptyList(),
    val mana: List<CardInstance> = emptyList(),
    val battle: List<CardInstance> = emptyList(),
    val shields: List<CardInstance> = emptyList(),
    val graveyard: List<CardInstance> = emptyList(),
    val lost: Boolean = false
) {
    val tappedMana: Int get() = mana.count { it.tapped }
    val availableMana: Int get() = mana.size - tappedMana
}

data class GameState(
    val players: List<PlayerState>,
    val activePlayer: Int = 0,
    val turn: Int = 1,
    val firstTurn: Boolean = true,
    val winner: Int? = null,
    val log: List<String> = emptyList()
)

sealed class GameAction {
    data object EndTurn : GameAction()
    data class ChargeMana(val handInstanceId: String) : GameAction()
    data class Summon(val handInstanceId: String) : GameAction()
    data class CastSpell(val handInstanceId: String) : GameAction()
    data class AttackShield(val attackerInstanceId: String) : GameAction()
    data class AttackCreature(val attackerInstanceId: String, val defenderInstanceId: String) : GameAction()
    data class Block(val blockerInstanceId: String) : GameAction()
}

sealed class ActionResult {
    data class Success(val state: GameState) : ActionResult()
    data class Rejected(val reason: String) : ActionResult()
}

object DuelMastersEngine {
    fun newGame(first: List<GameCard>, second: List<GameCard>, firstName: String = "Player 1", secondName: String = "Player 2"): GameState {
        require(first.size == 40 && second.size == 40) { "Each deck must contain exactly 40 cards." }
        val p1 = makePlayer(0, firstName, first)
        val p2 = makePlayer(1, secondName, second)
        return GameState(listOf(p1, p2)).let { state ->
            var next = state
            repeat(5) { next = draw(next, 0); next = draw(next, 1) }
            next.copy(players = next.players.mapIndexed { i, p ->
                if (i == 0) p.copy(shields = p.deck.take(5), deck = p.deck.drop(5))
                else p.copy(shields = p.deck.take(5), deck = p.deck.drop(5))
            }, log = listOf("Game started. Each player has five shields and five cards."))
        }
    }

    private fun makePlayer(id: Int, name: String, cards: List<GameCard>): PlayerState {
        val instances = cards.mapIndexed { index, card -> CardInstance("$id-$index", card) }
        return PlayerState(id, name, instances)
    }

    private fun draw(state: GameState, playerId: Int): GameState {
        val player = state.players[playerId]
        if (player.deck.isEmpty()) return state.copy(players = state.players.mapIndexed { i, p -> if (i == playerId) p.copy(lost = true) else p }, winner = 1 - playerId)
        val card = player.deck.first()
        return state.replace(player.copy(deck = player.deck.drop(1), hand = player.hand + card))
    }

    fun apply(state: GameState, action: GameAction): ActionResult {
        if (state.winner != null) return ActionResult.Rejected("The game has ended.")
        val player = state.players[state.activePlayer]
        val opponent = state.players[1 - state.activePlayer]
        fun reject(message: String) = ActionResult.Rejected(message)
        fun success(updated: GameState) = ActionResult.Success(updated)
        return when (action) {
            GameAction.EndTurn -> {
                val nextId = opponent.id
                var next = state.copy(
                    players = state.players.map { p ->
                        if (p.id == player.id) p.copy(battle = p.battle.map { it.copy(summoningSick = false) })
                        else p
                    },
                    activePlayer = nextId,
                    turn = state.turn + 1,
                    firstTurn = false
                )
                next = next.copy(players = next.players.map { p -> if (p.id == nextId) p.copy(mana = p.mana.map { it.copy(tapped = false) }, battle = p.battle.map { it.copy(tapped = false) }) else p })
                next = draw(next, nextId)
                success(next.addLog("${opponent.name}'s turn begins."))
            }
            is GameAction.ChargeMana -> {
                val card = player.hand.find { it.instanceId == action.handInstanceId } ?: return reject("That card is not in your hand.")
                success(state.replace(player.copy(hand = player.hand - card, mana = player.mana + card.copy(tapped = false))).addLog("${card.card.name} charged to mana."))
            }
            is GameAction.Summon -> {
                val card = player.hand.find { it.instanceId == action.handInstanceId } ?: return reject("That card is not in your hand.")
                if (card.card.kind != CardKind.CREATURE) return reject("Only creatures can be summoned.")
                if (player.availableMana < card.card.cost) return reject("Not enough untapped mana.")
                val paid = payMana(player, card.card.cost)
                success(state.replace(paid.copy(hand = paid.hand - card, battle = paid.battle + card.copy(tapped = false, summoningSick = true))).addLog("${card.card.name} summoned."))
            }
            is GameAction.CastSpell -> {
                val card = player.hand.find { it.instanceId == action.handInstanceId } ?: return reject("That card is not in your hand.")
                if (card.card.kind != CardKind.SPELL) return reject("Only spells can be cast.")
                if (player.availableMana < card.card.cost) return reject("Not enough untapped mana.")
                val paid = payMana(player, card.card.cost)
                val caster = paid.copy(hand = paid.hand - card, graveyard = paid.graveyard + card)
                success(state.replace(caster).addLog("${card.card.name} cast. Spell text resolution is delegated to the effect resolver."))
            }
            is GameAction.AttackShield -> {
                val attacker = player.battle.find { it.instanceId == action.attackerInstanceId } ?: return reject("Attacker is not on your battle zone.")
                if (!canAttack(attacker)) return reject("That creature cannot attack yet.")
                val tappedAttacker = attacker.copy(tapped = true)
                val updatedPlayer = player.copy(battle = player.battle.map { if (it.instanceId == attacker.instanceId) tappedAttacker else it })
                val updatedOpponent = if (opponent.shields.isEmpty()) opponent.copy(lost = true) else {
                    val shield = opponent.shields.first()
                    val remaining = opponent.shields.drop(1)
                    if (shield.card.shieldTrigger) opponent.copy(shields = remaining, hand = opponent.hand + shield)
                    else opponent.copy(shields = remaining, hand = opponent.hand + shield)
                }
                val win = if (updatedOpponent.lost || updatedOpponent.shields.isEmpty() && updatedOpponent.lost) player.id else null
                success(state.replace(updatedPlayer).replace(updatedOpponent).copy(winner = win).addLog("${attacker.card.name} breaks a shield."))
            }
            is GameAction.AttackCreature -> {
                val attacker = player.battle.find { it.instanceId == action.attackerInstanceId } ?: return reject("Attacker is not on your battle zone.")
                val defender = opponent.battle.find { it.instanceId == action.defenderInstanceId } ?: return reject("Defender is not on the opponent's battle zone.")
                if (!canAttack(attacker)) return reject("That creature cannot attack yet.")
                val a = attacker.copy(tapped = true)
                val attackerWon = attacker.card.power > defender.card.power
                val defenderWon = defender.card.power > attacker.card.power
                val p = player.copy(battle = player.battle.map { if (it.instanceId == a.instanceId) a else it })
                val o = opponent.copy(
                    battle = if (attackerWon || attacker.card.power == defender.card.power) opponent.battle.filterNot { it.instanceId == defender.instanceId } else opponent.battle,
                    graveyard = opponent.graveyard + (if (attackerWon || attacker.card.power == defender.card.power) listOf(defender) else emptyList())
                )
                val p2 = if (defenderWon || attacker.card.power == defender.card.power) p.copy(battle = p.battle.filterNot { it.instanceId == attacker.instanceId }, graveyard = p.graveyard + attacker) else p
                val o2 = if (defenderWon) o else o
                success(state.replace(p2).replace(o2).addLog("${attacker.card.name} battles ${defender.card.name}."))
            }
            is GameAction.Block -> {
                val blocker = opponent.battle.find { it.instanceId == action.blockerInstanceId && "BLOCKER" in it.card.keywords } ?: return reject("No legal blocker with that id.")
                if (blocker.tapped) return reject("A tapped creature cannot block.")
                success(state.addLog("${blocker.card.name} is ready to block; choose the battle resolution in the combat flow."))
            }
        }
    }

    private fun canAttack(card: CardInstance) = !card.tapped && (!card.summoningSick || "SPEED_ATTACKER" in card.card.keywords)
    private fun payMana(player: PlayerState, cost: Int): PlayerState {
        var remaining = cost
        val mana = player.mana.map { card ->
            if (!card.tapped && remaining > 0) { remaining--; card.copy(tapped = true) } else card
        }
        return player.copy(mana = mana)
    }
    private fun GameState.replace(player: PlayerState) = copy(players = players.map { if (it.id == player.id) player else it })
    private fun GameState.addLog(message: String) = copy(log = (log + message).takeLast(80))
}
