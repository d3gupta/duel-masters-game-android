package com.duelmasters.game.engine

import java.util.UUID
import kotlin.random.Random

enum class CardKind { CREATURE, SPELL }
enum class Ability { BLOCKER, SLAYER, SPEED_ATTACKER, DOUBLE_BREAKER, TRIPLE_BREAKER, POWER_ATTACKER_2000, UNBLOCKABLE }
enum class EffectKind { DRAW, DESTROY_TARGET_CREATURE, RETURN_TARGET_CREATURE_TO_HAND, ADD_MANA, POWER_UP_TARGET_CREATURE }

data class CardEffect(val kind: EffectKind, val amount: Int = 1)

data class GameCard(
    val id: String,
    val name: String,
    val cost: Int = 0,
    val kind: CardKind = CardKind.CREATURE,
    val power: Int = 0,
    val abilities: Set<Ability> = emptySet(),
    val effects: List<CardEffect> = emptyList(),
    val hasShieldTrigger: Boolean = false
)

data class CardInstance(val instanceId: String = UUID.randomUUID().toString(), val card: GameCard, val tapped: Boolean = false, val summonedThisTurn: Boolean = false, val powerModifier: Int = 0) {
    val currentPower: Int get() = card.power + powerModifier
}

data class PlayerState(
    val id: Int,
    val deck: List<CardInstance>,
    val hand: List<CardInstance> = emptyList(),
    val shields: List<CardInstance> = emptyList(),
    val mana: List<CardInstance> = emptyList(),
    val battle: List<CardInstance> = emptyList(),
    val graveyard: List<CardInstance> = emptyList()
)

data class GameState(
    val players: List<PlayerState>,
    val activePlayer: Int = 1,
    val turn: Int = 1,
    val phase: TurnPhase = TurnPhase.START,
    val winner: Int? = null,
    val log: List<String> = emptyList()
) {
    fun player(id: Int): PlayerState = players.first { it.id == id }
    fun opponent(id: Int): PlayerState = players.first { it.id != id }
}
enum class TurnPhase { START, DRAW, MAIN, END, FINISHED }

sealed interface GameResult {
    data class Success(val state: GameState) : GameResult
    data class Rejected(val reason: String) : GameResult
}

object GameEngine {
    fun newGame(firstDeck: List<GameCard>, secondDeck: List<GameCard>, random: Random = Random.Default): GameState {
        require(firstDeck.size >= 40 && secondDeck.size >= 40) { "Both decks must contain at least 40 cards." }
        val p1 = setupPlayer(1, firstDeck, random)
        val p2 = setupPlayer(2, secondDeck, random)
        return GameState(listOf(p1, p2), log = listOf("Game started. Each player drew five cards and set five shields."))
    }

    private fun setupPlayer(id: Int, cards: List<GameCard>, random: Random): PlayerState {
        val shuffled = cards.shuffled(random).map { CardInstance(card = it) }
        return PlayerState(id, deck = shuffled.drop(10), hand = shuffled.drop(5).take(5), shields = shuffled.take(5))
    }

    fun beginTurn(state: GameState): GameResult {
        if (state.winner != null) return GameResult.Rejected("The game has ended.")
        val current = state.player(state.activePlayer)
        val readied = current.copy(
            mana = current.mana.map { it.copy(tapped = false) },
            battle = current.battle.map { it.copy(tapped = false, summonedThisTurn = false, powerModifier = 0) }
        )
        var next = replace(state, readied).copy(phase = TurnPhase.DRAW)
        if (state.turn > 1 && current.deck.isNotEmpty()) {
            val updated = next.player(current.id)
            next = replace(next, updated.copy(deck = updated.deck.drop(1), hand = updated.hand + updated.deck.first()))
        }
        return GameResult.Success(next.copy(phase = TurnPhase.MAIN, log = next.log + "Player ${current.id}'s turn."))
    }

    fun chargeMana(state: GameState, handInstanceId: String): GameResult {
        val p = state.player(state.activePlayer)
        val card = p.hand.firstOrNull { it.instanceId == handInstanceId } ?: return GameResult.Rejected("Card is not in your hand.")
        return GameResult.Success(replace(state, p.copy(hand = p.hand - card, mana = p.mana + card.copy(tapped = false))).logged("${card.card.name} charged as mana."))
    }

    fun summonCreature(state: GameState, handInstanceId: String): GameResult {
        val p = state.player(state.activePlayer)
        val card = p.hand.firstOrNull { it.instanceId == handInstanceId } ?: return GameResult.Rejected("Card is not in your hand.")
        if (card.card.kind != CardKind.CREATURE) return GameResult.Rejected("Only creatures can be summoned.")
        val paid = payMana(p, card.card.cost) ?: return GameResult.Rejected("Not enough untapped mana.")
        val speed = Ability.SPEED_ATTACKER in card.card.abilities
        val creature = card.copy(summonedThisTurn = !speed)
        return GameResult.Success(replace(state, paid.copy(hand = paid.hand - card, battle = paid.battle + creature)).logged("${card.card.name} summoned."))
    }

    fun castSpell(state: GameState, handInstanceId: String, targetCreatureId: String? = null): GameResult {
        val p = state.player(state.activePlayer)
        val card = p.hand.firstOrNull { it.instanceId == handInstanceId } ?: return GameResult.Rejected("Card is not in your hand.")
        if (card.card.kind != CardKind.SPELL) return GameResult.Rejected("That card is not a spell.")
        val paid = payMana(p, card.card.cost) ?: return GameResult.Rejected("Not enough untapped mana.")
        val resolved = resolveEffects(state, paid, card.card.effects, targetCreatureId)
        val finalPlayer = resolved.first.copy(graveyard = resolved.first.graveyard + card)
        return GameResult.Success(replace(resolved.second, finalPlayer).logged("${card.card.name} cast."))
    }

    fun attack(state: GameState, attackerId: String, targetCreatureId: String? = null): GameResult {
        val p = state.player(state.activePlayer)
        val enemy = state.opponent(p.id)
        val attacker = p.battle.firstOrNull { it.instanceId == attackerId } ?: return GameResult.Rejected("Attacker is not on your battle zone.")
        if (attacker.tapped) return GameResult.Rejected("That creature is already tapped.")
        if (attacker.summonedThisTurn && Ability.SPEED_ATTACKER !in attacker.card.abilities) return GameResult.Rejected("A creature cannot attack the turn it is summoned.")
        if (targetCreatureId != null) {
            val target = enemy.battle.firstOrNull { it.instanceId == targetCreatureId } ?: return GameResult.Rejected("Target creature not found.")
            val availableBlocker = enemy.battle.firstOrNull { Ability.BLOCKER in it.card.abilities && !it.tapped && Ability.UNBLOCKABLE !in attacker.card.abilities }
            if (!target.tapped && availableBlocker == null) return GameResult.Rejected("You can only attack a tapped creature.")
            val defender = availableBlocker
            val actualTarget = defender ?: target
            val attackPower = attacker.currentPower + if (Ability.POWER_ATTACKER_2000 in attacker.card.abilities) 2000 else 0
            val defensePower = actualTarget.currentPower
            var aBattle = p.battle.map { if (it.instanceId == attackerId) it.copy(tapped = true) else it }
            var eBattle = enemy.battle
            var aGrave = p.graveyard
            var eGrave = enemy.graveyard
            if (attackPower < defensePower || Ability.SLAYER in actualTarget.card.abilities) {
                val dead = aBattle.first { it.instanceId == attackerId }
                aBattle = aBattle - dead
                aGrave = aGrave + dead
            }
            if (attackPower > defensePower || Ability.SLAYER in attacker.card.abilities) {
                val dead = eBattle.first { it.instanceId == actualTarget.instanceId }
                eBattle = eBattle - dead
                eGrave = eGrave + dead
            }
            val updated = replace(replace(state, p.copy(battle = aBattle, graveyard = aGrave)), enemy.copy(battle = eBattle, graveyard = eGrave))
            return GameResult.Success(updated.logged("${attacker.card.name} battled ${actualTarget.card.name}."))
        }
        val faceBlocker = enemy.battle.firstOrNull {
            Ability.BLOCKER in it.card.abilities && !it.tapped &&
                Ability.UNBLOCKABLE !in attacker.card.abilities
        }
        if (faceBlocker != null) {
            return resolveBlockerBattle(state, p, enemy, attacker, faceBlocker)
        }
        if (enemy.shields.isNotEmpty()) {
            val breaks = when {
                Ability.TRIPLE_BREAKER in attacker.card.abilities -> 3
                Ability.DOUBLE_BREAKER in attacker.card.abilities -> 2
                else -> 1
            }.coerceAtMost(enemy.shields.size)
            var defender = enemy
            var next = replace(state, p.copy(battle = p.battle.map { if (it.instanceId == attackerId) it.copy(tapped = true) else it }))
            repeat(breaks) {
                val shield = defender.shields.first()
                defender = defender.copy(shields = defender.shields.drop(1))
                if (shield.card.hasShieldTrigger) {
                    defender = defender.copy(hand = defender.hand + shield)
                    next = replace(next, defender).logged("Shield Trigger revealed: ${shield.card.name}.")
                    val triggerResult = resolveShieldTrigger(next, defender.id, shield)
                    next = triggerResult
                    defender = next.player(defender.id)
                } else defender = defender.copy(hand = defender.hand + shield)
            }
            next = replace(next, defender).logged("${attacker.card.name} broke $breaks shield(s).")
            return GameResult.Success(next)
        }
        val tapped = p.battle.map { if (it.instanceId == attackerId) it.copy(tapped = true) else it }
        return GameResult.Success(replace(state, p.copy(battle = tapped)).copy(winner = p.id, phase = TurnPhase.FINISHED).logged("Player ${p.id} attacked directly and won."))
    }

    private fun resolveBlockerBattle(state: GameState, attackerPlayer: PlayerState, defenderPlayer: PlayerState, attacker: CardInstance, blocker: CardInstance): GameResult {
        val attackPower = attacker.currentPower + if (Ability.POWER_ATTACKER_2000 in attacker.card.abilities) 2000 else 0
        var attackingBattle = attackerPlayer.battle.map { if (it.instanceId == attacker.instanceId) it.copy(tapped = true) else it }
        var defendingBattle = defenderPlayer.battle.map { if (it.instanceId == blocker.instanceId) it.copy(tapped = true) else it }
        var attackingGrave = attackerPlayer.graveyard
        var defendingGrave = defenderPlayer.graveyard
        if (attackPower < blocker.currentPower || Ability.SLAYER in blocker.card.abilities) {
            attackingBattle.firstOrNull { it.instanceId == attacker.instanceId }?.let { attackingBattle = attackingBattle - it; attackingGrave = attackingGrave + it }
        }
        if (attackPower > blocker.currentPower || Ability.SLAYER in attacker.card.abilities) {
            defendingBattle.firstOrNull { it.instanceId == blocker.instanceId }?.let { defendingBattle = defendingBattle - it; defendingGrave = defendingGrave + it }
        }
        val next = replace(replace(state, attackerPlayer.copy(battle = attackingBattle, graveyard = attackingGrave)), defenderPlayer.copy(battle = defendingBattle, graveyard = defendingGrave)).logged(blocker.card.name + " blocked " + attacker.card.name + ".")
        return GameResult.Success(next)
    }
    private fun resolveShieldTrigger(state: GameState, ownerId: Int, shield: CardInstance): GameState {
        val owner = state.player(ownerId)
        if (shield.card.kind == CardKind.SPELL) {
            val target = state.opponent(ownerId).battle.firstOrNull()?.instanceId
            val (updated, intermediate) = resolveEffects(state, owner, shield.card.effects, target)
            return replace(intermediate, updated.copy(hand = updated.hand - shield, graveyard = updated.graveyard + shield))
                .logged("Shield Trigger spell resolved for free.")
        }
        // Creature Shield Triggers are played without paying mana.
        return replace(state, owner.copy(hand = owner.hand - shield, battle = owner.battle + shield.copy(summonedThisTurn = true)))
            .logged("Shield Trigger creature summoned for free.")
    }

    private fun resolveEffects(state: GameState, owner: PlayerState, effects: List<CardEffect>, targetId: String?): Pair<PlayerState, GameState> {
        var p = owner
        var s = state
        effects.forEach { effect ->
            when (effect.kind) {
                EffectKind.DRAW -> repeat(effect.amount.coerceAtLeast(0)) {
                    if (p.deck.isNotEmpty()) { p = p.copy(hand = p.hand + p.deck.first(), deck = p.deck.drop(1)) }
                    else { s = s.copy(winner = s.opponent(p.id).id, phase = TurnPhase.FINISHED) }
                }
                EffectKind.ADD_MANA -> repeat(effect.amount.coerceAtLeast(0)) {
                    if (p.hand.isNotEmpty()) { val c = p.hand.first(); p = p.copy(hand = p.hand.drop(1), mana = p.mana + c) }
                }
                EffectKind.DESTROY_TARGET_CREATURE, EffectKind.RETURN_TARGET_CREATURE_TO_HAND, EffectKind.POWER_UP_TARGET_CREATURE -> {
                    val enemy = s.opponent(p.id)
                    val target = enemy.battle.firstOrNull { it.instanceId == targetId }
                    if (target != null) when (effect.kind) {
                        EffectKind.DESTROY_TARGET_CREATURE -> s = replace(s, enemy.copy(battle = enemy.battle - target, graveyard = enemy.graveyard + target))
                        EffectKind.RETURN_TARGET_CREATURE_TO_HAND -> s = replace(s, enemy.copy(battle = enemy.battle - target, hand = enemy.hand + target))
                        EffectKind.POWER_UP_TARGET_CREATURE -> s = replace(s, enemy.copy(battle = enemy.battle.map { if (it.instanceId == targetId) it.copy(powerModifier = it.powerModifier + effect.amount) else it }))
                        else -> Unit
                    }
                }
            }
        }
        return p to s
    }

    fun endTurn(state: GameState): GameState = state.copy(activePlayer = state.opponent(state.activePlayer).id, turn = state.turn + 1, phase = TurnPhase.START).logged("Turn ended.")

    private fun payMana(player: PlayerState, cost: Int): PlayerState? {
        if (cost < 0) return null
        val ready = player.mana.filterNot { it.tapped }
        if (ready.size < cost) return null
        val ids = ready.take(cost).map { it.instanceId }.toSet()
        return player.copy(mana = player.mana.map { if (it.instanceId in ids) it.copy(tapped = true) else it })
    }
    private fun replace(state: GameState, player: PlayerState): GameState = state.copy(players = state.players.map { if (it.id == player.id) player else it })
    private fun GameState.logged(line: String) = copy(log = log + line)
}
