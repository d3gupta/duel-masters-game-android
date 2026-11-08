package com.duelmasters.game.engine

/**
 * Pure, offline rules core. All UI and AI decisions are applied through [apply].
 * Pending choices and their continuation queue are part of immutable GameState,
 * so a match can be paused safely while the defender makes a decision.
 */
enum class CardKind { CREATURE, SPELL }
enum class Zone { DECK, HAND, MANA, BATTLE, SHIELD, GRAVEYARD }
enum class AttackDestination { SHIELDS, CREATURE }

enum class EffectKind {
    DRAW_CARDS,
    DESTROY_WEAKEST_OPPONENT,
    RETURN_WEAKEST_OPPONENT,
    ADD_TOP_CARD_TO_MANA,
    BOOST_FRIENDLY_CREATURES,
    DESTROY_TARGET_OPPONENT,
    RETURN_TARGET_OPPONENT,
    TAP_TARGET_OPPONENT,
    UNTAP_FRIENDLY_CREATURES,
    ADD_TOP_CARD_TO_HAND,
    ADD_TOP_CARD_TO_SHIELD,
    DESTROY_OPPONENT_CREATURES_POWER_OR_LESS
}

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
    val effects: List<CardEffect> = emptyList(),
    val races: Set<String> = emptySet(),
    val evolutionRace: String? = null,
    val isEvolution: Boolean = false,
    val powerAttackBonus: Int = 0
)

data class CardInstance(
    val instanceId: String,
    val card: GameCard,
    val tapped: Boolean = false,
    val summoningSick: Boolean = true,
    val powerModifier: Int = 0,
    /** Cards underneath an evolution creature, in bottom-to-top order. */
    val evolvedCards: List<CardInstance> = emptyList(),
    /** Absolute turn number on which this temporary modifier expires. */
    val powerModifierExpiresAtTurn: Int? = null
) {
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

sealed interface PendingChoice {
    val chooserPlayerId: Int

    data class Blocker(
        val attackingPlayerId: Int,
        val attackerInstanceId: String,
        val destination: AttackDestination,
        val targetInstanceId: String?,
        val blockerInstanceIds: List<String>
    ) : PendingChoice {
        override val chooserPlayerId: Int get() = 1 - attackingPlayerId
    }

    data class ShieldTrigger(
        val ownerPlayerId: Int,
        val shield: CardInstance
    ) : PendingChoice {
        override val chooserPlayerId: Int get() = ownerPlayerId
    }

    data class EffectTarget(
        val ownerPlayerId: Int,
        val targetPlayerId: Int,
        val effect: CardEffect,
        val targetInstanceIds: List<String>
    ) : PendingChoice {
        override val chooserPlayerId: Int get() = ownerPlayerId
    }
}

sealed interface ResolutionStep {
    data class BreakShield(val defenderPlayerId: Int) : ResolutionStep
    data class ResolveEffect(val ownerPlayerId: Int, val effect: CardEffect) : ResolutionStep
}

data class GameState(
    val players: List<PlayerState>,
    val activePlayer: Int = 0,
    val turn: Int = 1,
    val firstTurn: Boolean = true,
    val manaChargedThisTurn: Boolean = false,
    val winner: Int? = null,
    val log: List<String> = emptyList(),
    val pendingChoice: PendingChoice? = null,
    val resolutionQueue: List<ResolutionStep> = emptyList()
)

sealed class GameAction {
    data object EndTurn : GameAction()
    data class ChargeMana(val handInstanceId: String) : GameAction()
    data class Summon(val handInstanceId: String, val evolutionTargetInstanceId: String? = null) : GameAction()
    data class CastSpell(val handInstanceId: String) : GameAction()
    data class AttackShield(val attackerInstanceId: String) : GameAction()
    data class AttackCreature(val attackerInstanceId: String, val defenderInstanceId: String) : GameAction()
    /** null means the defender explicitly declined to block. */
    data class ChooseBlocker(val blockerInstanceId: String?) : GameAction()
    data class ResolveShieldTrigger(val useTrigger: Boolean) : GameAction()
    data class ChooseEffectTarget(val targetInstanceId: String) : GameAction()
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
    private const val UNBLOCKABLE = "UNBLOCKABLE"

    fun newGame(
        first: List<GameCard>,
        second: List<GameCard>,
        firstName: String = "Player 1",
        secondName: String = "Player 2"
    ): GameState {
        val firstValidation = DeckValidator.validate(first)
        val secondValidation = DeckValidator.validate(second)
        require(firstValidation.isValid) { firstValidation.errors.joinToString(" ") }
        require(secondValidation.isValid) { secondValidation.errors.joinToString(" ") }
        val p1 = makePlayer(0, firstName, first)
        val p2 = makePlayer(1, secondName, second)
        var state = GameState(listOf(p1, p2))
        repeat(5) { state = draw(state, 0); state = draw(state, 1) }
        state = state.copy(
            players = state.players.map { player ->
                player.copy(shields = player.deck.take(5), deck = player.deck.drop(5))
            },
            log = listOf("Game started. Each player has five shields and five cards.")
        )
        return state
    }

    private fun makePlayer(id: Int, name: String, cards: List<GameCard>): PlayerState {
        val instances = cards.shuffled().mapIndexed { index, card -> CardInstance("$id-$index", card) }
        return PlayerState(id, name, instances)
    }

    private fun draw(state: GameState, playerId: Int): GameState {
        if (state.winner != null) return state
        val player = state.players[playerId]
        if (player.deck.isEmpty()) {
            return state.copy(
                players = state.players.mapIndexed { index, current ->
                    if (index == playerId) current.copy(lost = true) else current
                },
                winner = 1 - playerId
            ).addLog("${player.name} could not draw and loses.")
        }
        val card = player.deck.first()
        return state.replace(player.copy(deck = player.deck.drop(1), hand = player.hand + card))
    }

    fun apply(state: GameState, action: GameAction): ActionResult {
        if (state.winner != null) return ActionResult.Rejected("The game has ended.")
        val pending = state.pendingChoice
        if (pending != null) {
            return when {
                pending is PendingChoice.Blocker && action is GameAction.ChooseBlocker ->
                    success(resolveBlockerChoice(state, pending, action.blockerInstanceId))
                pending is PendingChoice.ShieldTrigger && action is GameAction.ResolveShieldTrigger ->
                    success(resolveShieldChoice(state, pending, action.useTrigger))
                pending is PendingChoice.EffectTarget && action is GameAction.ChooseEffectTarget ->
                    success(resolveEffectTarget(state, pending, action.targetInstanceId))
                else -> ActionResult.Rejected("Resolve the pending decision before taking another action.")
            }
        }
        if (state.resolutionQueue.isNotEmpty()) return ActionResult.Rejected("The previous effect is still resolving.")

        val player = state.players[state.activePlayer]
        val opponent = state.players[1 - state.activePlayer]
        fun reject(reason: String) = ActionResult.Rejected(reason)
        fun success(updated: GameState) = ActionResult.Success(updated)

        return when (action) {
            GameAction.EndTurn -> {
                val cleaned = state.replace(
                    player.copy(battle = player.battle.map { card ->
                        if ((card.powerModifierExpiresAtTurn ?: Int.MAX_VALUE) <= state.turn) {
                            card.copy(summoningSick = false, powerModifier = 0, powerModifierExpiresAtTurn = null)
                        } else card.copy(summoningSick = false)
                    })
                )
                var next = cleaned.copy(
                    activePlayer = opponent.id,
                    turn = state.turn + 1,
                    firstTurn = false,
                    manaChargedThisTurn = false
                )
                next = next.replace(
                    next.players[opponent.id].copy(
                        mana = next.players[opponent.id].mana.map { it.copy(tapped = false) },
                        battle = next.players[opponent.id].battle.map { it.copy(tapped = false, summoningSick = false) }
                    )
                )
                next = draw(next, opponent.id)
                success(next.addLog("${opponent.name}'s turn begins."))
            }

            is GameAction.ChargeMana -> {
                if (state.manaChargedThisTurn) return reject("You may charge only one mana card per turn.")
                val card = player.hand.find { it.instanceId == action.handInstanceId }
                    ?: return reject("That card is not in your hand.")
                success(
                    state.replace(player.copy(
                        hand = player.hand - card,
                        mana = player.mana + card.copy(tapped = false, summoningSick = false)
                    )).copy(manaChargedThisTurn = true).addLog("${card.card.name} charged to mana.")
                )
            }

            is GameAction.Summon -> {
                val card = player.hand.find { it.instanceId == action.handInstanceId }
                    ?: return reject("That card is not in your hand.")
                if (card.card.kind != CardKind.CREATURE) return reject("Only creatures can be summoned.")
                if (player.availableMana < card.card.cost) return reject("Not enough untapped mana.")

                var host: CardInstance? = null
                if (card.card.isEvolution || card.card.evolutionRace != null) {
                    val race = card.card.evolutionRace
                        ?: return reject("This evolution requirement is not supported yet.")
                    host = player.battle.firstOrNull { it.instanceId == action.evolutionTargetInstanceId }
                        ?: return reject("Choose a creature to evolve.")
                    if (host.card.races.none { it.equals(race, ignoreCase = true) }) {
                        return reject("Evolution requires a $race creature.")
                    }
                } else if (action.evolutionTargetInstanceId != null) {
                    return reject("A normal creature cannot evolve onto another creature.")
                }

                val paid = payMana(player, card.card.cost)
                val remainingBattle = if (host == null) paid.battle else paid.battle - host
                val evolved = card.copy(
                    tapped = false,
                    summoningSick = host == null && SPEED_ATTACKER !in card.card.keywords,
                    evolvedCards = host?.let { it.evolvedCards + it.copy(evolvedCards = emptyList()) }.orEmpty()
                )
                success(
                    state.replace(paid.copy(
                        hand = paid.hand - card,
                        battle = remainingBattle + evolved
                    )).addLog(
                        if (host == null) "${card.card.name} summoned."
                        else "${card.card.name} evolved from ${host.card.name}."
                    )
                )
            }

            is GameAction.CastSpell -> {
                val card = player.hand.find { it.instanceId == action.handInstanceId }
                    ?: return reject("That card is not in your hand.")
                if (card.card.kind != CardKind.SPELL) return reject("Only spells can be cast.")
                if (player.availableMana < card.card.cost) return reject("Not enough untapped mana.")
                val paid = payMana(player, card.card.cost)
                val base = state.replace(paid.copy(hand = paid.hand - card, graveyard = paid.graveyard + card))
                val steps = card.card.effects.map { ResolutionStep.ResolveEffect(player.id, it) }
                success(processResolution(base.addLog("${card.card.name} cast."), steps))
            }

            is GameAction.AttackShield -> {
                val attacker = player.battle.find { it.instanceId == action.attackerInstanceId }
                    ?: return reject("Attacker is not on your battle zone.")
                if (!canAttack(attacker)) return reject("That creature cannot attack yet.")
                val blockers = availableBlockers(opponent, attacker)
                if (blockers.isNotEmpty()) {
                    success(state.copy(pendingChoice = PendingChoice.Blocker(
                        player.id, attacker.instanceId, AttackDestination.SHIELDS, null,
                        blockers.map { it.instanceId }
                    )).addLog("Choose whether to block ${attacker.card.name}'s attack."))
                } else {
                    success(resolveShieldAttack(state, player.id, attacker.instanceId))
                }
            }

            is GameAction.AttackCreature -> {
                val attacker = player.battle.find { it.instanceId == action.attackerInstanceId }
                    ?: return reject("Attacker is not on your battle zone.")
                val defender = opponent.battle.find { it.instanceId == action.defenderInstanceId }
                    ?: return reject("Defender is not on the opponent's battle zone.")
                if (!canAttack(attacker)) return reject("That creature cannot attack yet.")
                if (!defender.tapped) return reject("You can only attack a tapped creature.")
                val blockers = availableBlockers(opponent, attacker)
                if (blockers.isNotEmpty()) {
                    success(state.copy(pendingChoice = PendingChoice.Blocker(
                        player.id, attacker.instanceId, AttackDestination.CREATURE, defender.instanceId,
                        blockers.map { it.instanceId }
                    )).addLog("Choose whether to block ${attacker.card.name}'s attack."))
                } else {
                    val resolved = resolveBattle(state, player.id, attacker.instanceId, opponent.id, defender.instanceId)
                    success(resolved.addLog("${attacker.card.name} attacked ${defender.card.name}."))
                }
            }

            is GameAction.ChooseBlocker,
            is GameAction.ResolveShieldTrigger,
            is GameAction.ChooseEffectTarget -> reject("There is no pending decision.")
        }
    }

    private fun resolveBlockerChoice(
        state: GameState,
        choice: PendingChoice.Blocker,
        blockerInstanceId: String?
    ): GameState {
        val attackerPlayer = state.players[choice.attackingPlayerId]
        val defenderPlayer = state.players[1 - choice.attackingPlayerId]
        val attacker = attackerPlayer.battle.firstOrNull { it.instanceId == choice.attackerInstanceId }
            ?: return state.copy(pendingChoice = null).addLog("Attack cancelled: attacker no longer exists.")
        val blocker = blockerInstanceId?.let { id ->
            defenderPlayer.battle.firstOrNull {
                it.instanceId == id && id in choice.blockerInstanceIds && !it.tapped
            }
        }
        if (blockerInstanceId != null && blocker == null) {
            return state.copy(pendingChoice = null).addLog("The chosen blocker is no longer available; attack cancelled.")
        }
        val cleared = state.copy(pendingChoice = null)
        if (blocker != null) {
            return resolveBattle(cleared, attackerPlayer.id, attacker.instanceId, defenderPlayer.id, blocker.instanceId)
                .addLog("${blocker.card.name} blocked ${attacker.card.name}.")
        }
        return when (choice.destination) {
            AttackDestination.SHIELDS -> resolveShieldAttack(cleared, attackerPlayer.id, attacker.instanceId)
            AttackDestination.CREATURE -> {
                val targetId = choice.targetInstanceId ?: return cleared
                resolveBattle(cleared, attackerPlayer.id, attacker.instanceId, defenderPlayer.id, targetId)
                    .addLog("${attacker.card.name} attacked ${defenderPlayer.battle.firstOrNull { it.instanceId == targetId }?.card?.name ?: "the creature"}.")
            }
        }
    }

    private fun availableBlockers(defender: PlayerState, attacker: CardInstance): List<CardInstance> =
        if (UNBLOCKABLE in attacker.card.keywords) emptyList()
        else defender.battle.filter { BLOCKER in it.card.keywords && !it.tapped }

    private fun resolveShieldAttack(state: GameState, attackerPlayerId: Int, attackerInstanceId: String): GameState {
        val attackerPlayer = state.players[attackerPlayerId]
        val defenderPlayer = state.players[1 - attackerPlayerId]
        val attacker = attackerPlayer.battle.firstOrNull { it.instanceId == attackerInstanceId } ?: return state
        val tappedState = state.replace(attackerPlayer.copy(
            battle = attackerPlayer.battle.map { if (it.instanceId == attackerInstanceId) it.copy(tapped = true) else it }
        ))
        if (defenderPlayer.shields.isEmpty()) {
            return tappedState.copy(winner = attackerPlayerId)
                .addLog("${attacker.card.name} attacked directly. ${attackerPlayer.name} wins!")
        }
        val breakerCount = when {
            TRIPLE_BREAKER in attacker.card.keywords -> 3
            DOUBLE_BREAKER in attacker.card.keywords -> 2
            else -> 1
        }.coerceAtMost(defenderPlayer.shields.size)
        val steps = List(breakerCount) { ResolutionStep.BreakShield(defenderPlayer.id) }
        return processResolution(tappedState, steps)
            .addLog("${attacker.card.name} attacked shields.")
    }

    private fun resolveShieldChoice(
        state: GameState,
        choice: PendingChoice.ShieldTrigger,
        useTrigger: Boolean
    ): GameState {
        val owner = state.players[choice.ownerPlayerId]
        var next = state.copy(pendingChoice = null)
        if (!useTrigger) {
            next = next.replace(owner.copy(hand = owner.hand + choice.shield))
                .addLog("Shield Trigger declined; ${choice.shield.card.name} added to hand.")
        } else if (choice.shield.card.kind == CardKind.CREATURE) {
            next = next.replace(owner.copy(battle = owner.battle + choice.shield.copy(
                tapped = false, summoningSick = true
            ))).addLog("Shield Trigger: ${choice.shield.card.name} summoned for free.")
        } else {
            next = next.replace(owner.copy(graveyard = owner.graveyard + choice.shield))
                .addLog("Shield Trigger: ${choice.shield.card.name} cast for free.")
            val effectSteps = choice.shield.card.effects.map {
                ResolutionStep.ResolveEffect(owner.id, it)
            }
            next = next.copy(resolutionQueue = effectSteps + next.resolutionQueue)
        }
        return processResolution(next, next.resolutionQueue)
    }

    private fun resolveEffectTarget(
        state: GameState,
        choice: PendingChoice.EffectTarget,
        targetInstanceId: String
    ): GameState {
        if (targetInstanceId !in choice.targetInstanceIds) {
            return state.copy(pendingChoice = null, resolutionQueue = emptyList())
                .addLog("Effect cancelled: the selected target is not valid.")
        }
        val targetPlayer = state.players[choice.targetPlayerId]
        val target = targetPlayer.battle.firstOrNull { it.instanceId == targetInstanceId }
            ?: return processResolution(state.copy(pendingChoice = null), state.resolutionQueue)
        val remaining = when (choice.effect.kind) {
            EffectKind.DESTROY_TARGET_OPPONENT ->
                state.replace(targetPlayer.copy(
                    battle = targetPlayer.battle - target,
                    graveyard = targetPlayer.graveyard + flattenStack(target)
                ))
            EffectKind.RETURN_TARGET_OPPONENT ->
                state.replace(targetPlayer.copy(
                    battle = targetPlayer.battle - target,
                    hand = targetPlayer.hand + flattenStack(target)
                ))
            EffectKind.TAP_TARGET_OPPONENT ->
                state.replace(targetPlayer.copy(
                    battle = targetPlayer.battle.map { if (it.instanceId == targetInstanceId) it.copy(tapped = true) else it }
                ))
            else -> state
        }
        return processResolution(remaining.copy(pendingChoice = null), remaining.resolutionQueue)
            .addLog("${target.card.name} was affected by a spell.")
    }

    private fun processResolution(initial: GameState, steps: List<ResolutionStep>): GameState {
        var state = initial.copy(resolutionQueue = steps)
        while (state.resolutionQueue.isNotEmpty() && state.pendingChoice == null && state.winner == null) {
            val step = state.resolutionQueue.first()
            state = state.copy(resolutionQueue = state.resolutionQueue.drop(1))
            when (step) {
                is ResolutionStep.BreakShield -> {
                    val defender = state.players[step.defenderPlayerId]
                    val shield = defender.shields.firstOrNull() ?: continue
                    state = state.replace(defender.copy(shields = defender.shields.drop(1)))
                    if (shield.card.shieldTrigger) {
                        state = state.copy(pendingChoice = PendingChoice.ShieldTrigger(defender.id, shield))
                            .addLog("${shield.card.name} is a Shield Trigger. The defender may use it or add it to hand.")
                    } else {
                        val updated = state.players[defender.id]
                        state = state.replace(updated.copy(hand = updated.hand + shield))
                    }
                }
                is ResolutionStep.ResolveEffect -> state = resolveEffect(state, step.ownerPlayerId, step.effect)
            }
        }
        return state
    }

    private fun resolveEffect(initial: GameState, ownerId: Int, effect: CardEffect): GameState {
        var state = initial
        val owner = state.players[ownerId]
        val opponentId = 1 - ownerId
        val opponent = state.players[opponentId]
        when (effect.kind) {
            EffectKind.DRAW_CARDS -> repeat(effect.amount.coerceAtLeast(1)) { state = draw(state, ownerId) }
            EffectKind.DESTROY_WEAKEST_OPPONENT -> repeat(effect.amount.coerceAtLeast(1)) {
                val currentOpponent = state.players[opponentId]
                val target = currentOpponent.battle.minByOrNull { it.currentPower }
                if (target != null) state = state.replace(currentOpponent.copy(
                    battle = currentOpponent.battle - target,
                    graveyard = currentOpponent.graveyard + flattenStack(target)
                ))
            }
            EffectKind.RETURN_WEAKEST_OPPONENT -> repeat(effect.amount.coerceAtLeast(1)) {
                val currentOpponent = state.players[opponentId]
                val target = currentOpponent.battle.minByOrNull { it.currentPower }
                if (target != null) state = state.replace(currentOpponent.copy(
                    battle = currentOpponent.battle - target,
                    hand = currentOpponent.hand + flattenStack(target)
                ))
            }
            EffectKind.ADD_TOP_CARD_TO_MANA -> repeat(effect.amount.coerceAtLeast(1)) {
                val current = state.players[ownerId]
                current.deck.firstOrNull()?.let { top ->
                    state = state.replace(current.copy(deck = current.deck.drop(1), mana = current.mana + top))
                }
            }
            EffectKind.BOOST_FRIENDLY_CREATURES -> {
                val expiry = if (state.activePlayer == ownerId) state.turn else state.turn + 1
                state = state.replace(owner.copy(battle = owner.battle.map { card ->
                    card.copy(
                        powerModifier = card.powerModifier + effect.amount,
                        powerModifierExpiresAtTurn = maxOf(card.powerModifierExpiresAtTurn ?: Int.MIN_VALUE, expiry)
                    )
                }))
            }
            EffectKind.DESTROY_TARGET_OPPONENT,
            EffectKind.RETURN_TARGET_OPPONENT,
            EffectKind.TAP_TARGET_OPPONENT -> {
                val candidates = opponent.battle.map { it.instanceId }
                if (candidates.isNotEmpty()) {
                    state = state.copy(pendingChoice = PendingChoice.EffectTarget(
                        ownerId, opponentId, effect, candidates
                    ))
                }
            }
            EffectKind.UNTAP_FRIENDLY_CREATURES -> {
                state = state.replace(owner.copy(battle = owner.battle.map { it.copy(tapped = false) }))
            }
            EffectKind.ADD_TOP_CARD_TO_HAND -> {
                val current = state.players[ownerId]
                current.deck.firstOrNull()?.let { top ->
                    state = state.replace(current.copy(deck = current.deck.drop(1), hand = current.hand + top))
                }
            }
            EffectKind.ADD_TOP_CARD_TO_SHIELD -> {
                val current = state.players[ownerId]
                current.deck.firstOrNull()?.let { top ->
                    state = state.replace(current.copy(deck = current.deck.drop(1), shields = listOf(top) + current.shields))
                }
            }
            EffectKind.DESTROY_OPPONENT_CREATURES_POWER_OR_LESS -> {
                val currentOpponent = state.players[opponentId]
                val destroyed = currentOpponent.battle.filter { it.currentPower <= effect.amount }
                state = state.replace(currentOpponent.copy(
                    battle = currentOpponent.battle - destroyed.toSet(),
                    graveyard = currentOpponent.graveyard + destroyed.flatMap(::flattenStack)
                ))
            }
        }
        return state
    }

    private fun resolveBattle(
        initial: GameState,
        attackerPlayerId: Int,
        attackerInstanceId: String,
        defenderPlayerId: Int,
        defenderInstanceId: String
    ): GameState {
        val attackerPlayer = initial.players[attackerPlayerId]
        val defenderPlayer = initial.players[defenderPlayerId]
        val attacker = attackerPlayer.battle.firstOrNull { it.instanceId == attackerInstanceId } ?: return initial
        val defender = defenderPlayer.battle.firstOrNull { it.instanceId == defenderInstanceId } ?: return initial
        val attackPower = attacker.currentPower + attacker.card.powerAttackBonus
        val defensePower = defender.currentPower
        val attackerDies = attackPower <= defensePower || SLAYER in defender.card.keywords
        val defenderDies = attackPower >= defensePower || SLAYER in attacker.card.keywords
        val nextAttackerBattle = attackerPlayer.battle.map { if (it.instanceId == attackerInstanceId) it.copy(tapped = true) else it }
            .filterNot { attackerDies && it.instanceId == attackerInstanceId }
        val nextDefenderBattle = defenderPlayer.battle.map { if (it.instanceId == defenderInstanceId) it.copy(tapped = true) else it }
            .filterNot { defenderDies && it.instanceId == defenderInstanceId }
        val nextAttackerGraveyard = if (attackerDies) attackerPlayer.graveyard + flattenStack(attacker) else attackerPlayer.graveyard
        val nextDefenderGraveyard = if (defenderDies) defenderPlayer.graveyard + flattenStack(defender) else defenderPlayer.graveyard
        return initial
            .replace(attackerPlayer.copy(battle = nextAttackerBattle, graveyard = nextAttackerGraveyard))
            .replace(defenderPlayer.copy(battle = nextDefenderBattle, graveyard = nextDefenderGraveyard))
    }

    private fun flattenStack(top: CardInstance): List<CardInstance> =
        top.evolvedCards + top.copy(evolvedCards = emptyList())

    private fun canAttack(card: CardInstance): Boolean =
        !card.tapped && (!card.summoningSick || SPEED_ATTACKER in card.card.keywords)

    private fun payMana(player: PlayerState, cost: Int): PlayerState {
        var remaining = cost.coerceAtLeast(0)
        val mana = player.mana.map { card ->
            if (!card.tapped && remaining > 0) {
                remaining--
                card.copy(tapped = true)
            } else card
        }
        return player.copy(mana = mana)
    }

    private fun GameState.replace(player: PlayerState): GameState =
        copy(players = players.map { if (it.id == player.id) player else it })

    private fun GameState.addLog(message: String): GameState =
        copy(log = (log + message).takeLast(80))

    private fun success(state: GameState): ActionResult = ActionResult.Success(state)
}
