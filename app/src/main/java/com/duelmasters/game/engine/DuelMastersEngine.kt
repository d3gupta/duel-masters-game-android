package com.duelmasters.game.engine

/**
 * Platform-independent rules core. The Compose UI and all AI levels submit the
 * same actions to this state machine.
 */
enum class CardKind { CREATURE, SPELL }
enum class Zone { DECK, HAND, MANA, BATTLE, SHIELD, GRAVEYARD }
enum class EffectKind { DRAW_CARDS, DESTROY_WEAKEST_OPPONENT, RETURN_WEAKEST_OPPONENT, ADD_TOP_CARD_TO_MANA, BOOST_FRIENDLY_CREATURES }
data class CardEffect(val kind: EffectKind, val amount: Int = 1)

data class GameCard(
    val id: String,
    val name: String,
    val kind: CardKind,
    val cost: Int,
    val power: Int = 0,
    val civilizations: Set<String> = emptySet(),
    val keywords: Set<String> = emptySet(),
    val shieldTrigger: Boolean = false,
    val effects: List<CardEffect> = emptyList()
)

data class CardInstance(val instanceId: String, val card: GameCard, val tapped: Boolean = false, val summoningSick: Boolean = true, val powerModifier: Int = 0) {
    val currentPower: Int get() = card.power + powerModifier
}

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
    val manaChargedThisTurn: Boolean = false,
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
    private const val SPEED_ATTACKER = "SPEED_ATTACKER"
    private const val BLOCKER = "BLOCKER"
    private const val SLAYER = "SLAYER"
    private const val DOUBLE_BREAKER = "DOUBLE_BREAKER"
    private const val TRIPLE_BREAKER = "TRIPLE_BREAKER"
    private const val POWER_ATTACKER_2000 = "POWER_ATTACKER_2000"
    private const val UNBLOCKABLE = "UNBLOCKABLE"

    fun newGame(first: List<GameCard>, second: List<GameCard>, firstName: String = "Player 1", secondName: String = "Player 2"): GameState {
        val firstValidation = DeckValidator.validate(first)
        val secondValidation = DeckValidator.validate(second)
        require(firstValidation.isValid) { firstValidation.errors.joinToString(" ") }
        require(secondValidation.isValid) { secondValidation.errors.joinToString(" ") }
        val p1 = makePlayer(0, firstName, first)
        val p2 = makePlayer(1, secondName, second)
        var state = GameState(listOf(p1, p2))
        repeat(5) { state = draw(state, 0); state = draw(state, 1) }
        state = state.copy(players = state.players.map { p ->
            p.copy(shields = p.deck.take(5), deck = p.deck.drop(5))
        }, log = listOf("Game started. Each player has five shields and five cards."))
        return state
    }

    private fun makePlayer(id: Int, name: String, cards: List<GameCard>): PlayerState {
        val instances = cards.shuffled().mapIndexed { index, card -> CardInstance("$id-$index", card) }
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
                    players = state.players.map { p -> if (p.id == player.id) p.copy(battle = p.battle.map { it.copy(summoningSick = false, powerModifier = 0) }) else p },
                    activePlayer = nextId, turn = state.turn + 1, firstTurn = false, manaChargedThisTurn = false
                )
                next = next.copy(players = next.players.map { p -> if (p.id == nextId) p.copy(mana = p.mana.map { it.copy(tapped = false) }, battle = p.battle.map { it.copy(tapped = false, summoningSick = false) }) else p })
                next = draw(next, nextId)
                success(next.addLog("${opponent.name}'s turn begins."))
            }
            is GameAction.ChargeMana -> {
                if (state.manaChargedThisTurn) return reject("You may charge only one mana card per turn.")
                val card = player.hand.find { it.instanceId == action.handInstanceId } ?: return reject("That card is not in your hand.")
                success(state.replace(player.copy(hand = player.hand - card, mana = player.mana + card.copy(tapped = false))).copy(manaChargedThisTurn = true).addLog("${card.card.name} charged to mana."))
            }
            is GameAction.Summon -> {
                val card = player.hand.find { it.instanceId == action.handInstanceId } ?: return reject("That card is not in your hand.")
                if (card.card.kind != CardKind.CREATURE) return reject("Only creatures can be summoned.")
                if (player.availableMana < card.card.cost) return reject("Not enough untapped mana.")
                val paid = payMana(player, card.card.cost)
                success(state.replace(paid.copy(hand = paid.hand - card, battle = paid.battle + card.copy(tapped = false, summoningSick = SPEED_ATTACKER !in card.card.keywords))).addLog("${card.card.name} summoned."))
            }
            is GameAction.CastSpell -> {
                val card = player.hand.find { it.instanceId == action.handInstanceId } ?: return reject("That card is not in your hand.")
                if (card.card.kind != CardKind.SPELL) return reject("Only spells can be cast.")
                if (player.availableMana < card.card.cost) return reject("Not enough untapped mana.")
                val paid = payMana(player, card.card.cost)
                val base = state.replace(paid.copy(hand = paid.hand - card, graveyard = paid.graveyard + card))
                success(resolveEffects(base, player.id, card.card.effects).addLog("${card.card.name} cast and resolved."))
            }
            is GameAction.AttackShield -> {
                val attacker = player.battle.find { it.instanceId == action.attackerInstanceId } ?: return reject("Attacker is not on your battle zone.")
                if (!canAttack(attacker)) return reject("That creature cannot attack yet.")
                val blocker = opponent.battle.firstOrNull { BLOCKER in it.card.keywords && !it.tapped }?.takeIf { UNBLOCKABLE !in attacker.card.keywords }
                if (blocker != null) {
                    success(resolveBattle(state, player, opponent, attacker, blocker).addLog("${blocker.card.name} blocked ${attacker.card.name}."))
                } else {
                    val breakerCount = when {
                        TRIPLE_BREAKER in attacker.card.keywords -> 3
                        DOUBLE_BREAKER in attacker.card.keywords -> 2
                        else -> 1
                    }.coerceAtMost(opponent.shields.size)
                    if (opponent.shields.isEmpty()) {
                        success(state.replace(player.copy(battle = player.battle.map { if (it.instanceId == attacker.instanceId) it.copy(tapped = true) else it })).copy(winner = player.id).addLog("${attacker.card.name} attacked directly. ${player.name} wins!"))
                    } else {
                        var next = state.replace(player.copy(battle = player.battle.map { if (it.instanceId == attacker.instanceId) it.copy(tapped = true) else it }))
                        repeat(breakerCount) {
                            if (next.winner != null) return@repeat
                            val defending = next.players[opponent.id]
                            val shield = defending.shields.first()
                            next = next.replace(defending.copy(shields = defending.shields.drop(1)))
                            if (shield.card.shieldTrigger) {
                                next = resolveShieldTrigger(next, opponent.id, shield)
                            } else {
                                val after = next.players[opponent.id]
                                next = next.replace(after.copy(hand = after.hand + shield))
                            }
                        }
                        success(next.addLog("${attacker.card.name} broke $breakerCount shield(s)."))
                    }
                }
            }
            is GameAction.AttackCreature -> {
                val attacker = player.battle.find { it.instanceId == action.attackerInstanceId } ?: return reject("Attacker is not on your battle zone.")
                val defender = opponent.battle.find { it.instanceId == action.defenderInstanceId } ?: return reject("Defender is not on the opponent's battle zone.")
                if (!canAttack(attacker)) return reject("That creature cannot attack yet.")
                if (!defender.tapped) return reject("You can only attack a tapped creature.")
                val blocker = opponent.battle.firstOrNull { BLOCKER in it.card.keywords && !it.tapped }?.takeIf { UNBLOCKABLE !in attacker.card.keywords }
                success(resolveBattle(state, player, opponent, attacker, blocker ?: defender).addLog("${attacker.card.name} attacked ${(blocker ?: defender).card.name}."))
            }
            is GameAction.Block -> reject("Blockers resolve automatically when an attack is declared.")
        }
    }

    private fun resolveShieldTrigger(state: GameState, ownerId: Int, shield: CardInstance): GameState {
        val owner = state.players[ownerId]
        return if (shield.card.kind == CardKind.CREATURE) {
            state.replace(owner.copy(battle = owner.battle + shield.copy(tapped = false, summoningSick = true)))
                .addLog("Shield Trigger: ${shield.card.name} was summoned for free.")
        } else {
            val moved = state.replace(owner.copy(graveyard = owner.graveyard + shield))
            resolveEffects(moved, ownerId, shield.card.effects).addLog("Shield Trigger: ${shield.card.name} resolved for free.")
        }
    }

    private fun resolveEffects(initial: GameState, ownerId: Int, effects: List<CardEffect>): GameState {
        var state = initial
        effects.forEach { effect ->
            val repetitions = if (effect.kind == EffectKind.BOOST_FRIENDLY_CREATURES) 1 else effect.amount.coerceAtLeast(1)
            repeat(repetitions) {
                if (state.winner != null) return state
                val owner = state.players[ownerId]
                val opponent = state.players[1 - ownerId]
                when (effect.kind) {
                    EffectKind.DRAW_CARDS -> state = draw(state, ownerId)
                    EffectKind.DESTROY_WEAKEST_OPPONENT -> {
                        val target = opponent.battle.minByOrNull { it.currentPower }
                        if (target != null) state = state.replace(opponent.copy(battle = opponent.battle - target, graveyard = opponent.graveyard + target))
                    }
                    EffectKind.RETURN_WEAKEST_OPPONENT -> {
                        val target = opponent.battle.minByOrNull { it.currentPower }
                        if (target != null) state = state.replace(opponent.copy(battle = opponent.battle - target, hand = opponent.hand + target))
                    }
                    EffectKind.ADD_TOP_CARD_TO_MANA -> {
                        if (owner.deck.isNotEmpty()) {
                            val top = owner.deck.first()
                            state = state.replace(owner.copy(deck = owner.deck.drop(1), mana = owner.mana + top))
                        }
                    }
                    EffectKind.BOOST_FRIENDLY_CREATURES -> state = state.replace(owner.copy(battle = owner.battle.map { it.copy(powerModifier = it.powerModifier + effect.amount) }))
                }
            }
        }
        return state
    }

    private fun resolveBattle(state: GameState, attackerPlayer: PlayerState, defenderPlayer: PlayerState, attacker: CardInstance, defender: CardInstance): GameState {
        val attackPower = attacker.currentPower + if (POWER_ATTACKER_2000 in attacker.card.keywords) 2000 else 0
        val defensePower = defender.currentPower
        var aBattle = attackerPlayer.battle.map { if (it.instanceId == attacker.instanceId) it.copy(tapped = true) else it }
        var dBattle = defenderPlayer.battle.map { if (it.instanceId == defender.instanceId) it.copy(tapped = true) else it }
        var aGrave = attackerPlayer.graveyard
        var dGrave = defenderPlayer.graveyard
        if (attackPower <= defensePower || SLAYER in defender.card.keywords) {
            aBattle.firstOrNull { it.instanceId == attacker.instanceId }?.let { aBattle = aBattle - it; aGrave = aGrave + it }
        }
        if (attackPower >= defensePower || SLAYER in attacker.card.keywords) {
            dBattle.firstOrNull { it.instanceId == defender.instanceId }?.let { dBattle = dBattle - it; dGrave = dGrave + it }
        }
        return state.replace(attackerPlayer.copy(battle = aBattle, graveyard = aGrave))
            .replace(defenderPlayer.copy(battle = dBattle, graveyard = dGrave))
    }

    private fun canAttack(card: CardInstance) = !card.tapped && (!card.summoningSick || SPEED_ATTACKER in card.card.keywords)
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
