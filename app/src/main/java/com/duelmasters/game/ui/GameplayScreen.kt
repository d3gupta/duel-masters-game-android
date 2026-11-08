package com.duelmasters.game.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.duelmasters.game.data.local.CardEntity
import com.duelmasters.game.data.local.DuelMastersDatabase
import com.duelmasters.game.engine.ActionResult
import com.duelmasters.game.engine.AiDifficulty
import com.duelmasters.game.engine.CardInstance
import com.duelmasters.game.engine.CardKind
import com.duelmasters.game.engine.CardTextParser
import com.duelmasters.game.engine.DeckValidator
import com.duelmasters.game.engine.DuelMastersAi
import com.duelmasters.game.engine.DuelMastersEngine
import com.duelmasters.game.engine.GameAction
import com.duelmasters.game.engine.GameCard
import com.duelmasters.game.engine.GameState
import com.duelmasters.game.engine.PendingChoice
import kotlinx.coroutines.delay
import org.json.JSONArray

private enum class MatchMode { AI, LOCAL }

@Composable
fun GameplayScreen(database: DuelMastersDatabase, modifier: Modifier = Modifier) {
    val catalog by remember(database) {
        database.cardDao().search("", "", "", null, "", "", "", 5000, 0)
    }.collectAsState(initial = emptyList())
    val playableCards = remember(catalog) { catalog.mapNotNull { it.toGameCard() } }
    val starterDeck = remember(playableCards) { playableCards.distinctBy { it.name.trim().lowercase() }.take(10).flatMap { card -> List(4) { card } } }
    val savedDecks by remember(database) { database.deckDao().observeAll() }.collectAsState(initial = emptyList())
    var selectedDeckId by remember { mutableStateOf(-1L) }
    val selectedSavedDeck = savedDecks.firstOrNull { it.id == selectedDeckId } ?: savedDecks.firstOrNull()
    val cardById = remember(playableCards) { playableCards.associateBy { it.id } }
    val selectedDeckCards = remember(selectedSavedDeck, cardById) {
        selectedSavedDeck?.let { saved ->
            runCatching {
                val ids = JSONArray(saved.cardIdsJson)
                (0 until ids.length()).mapNotNull { cardById[ids.optString(it)] }
            }.getOrDefault(emptyList())
        } ?: starterDeck
    }
    val deckValidation = remember(selectedDeckCards) { DeckValidator.validate(selectedDeckCards) }

    var mode by remember { mutableStateOf(MatchMode.AI) }
    var difficulty by remember { mutableStateOf(AiDifficulty.NORMAL) }
    var game by remember { mutableStateOf<GameState?>(null) }
    var message by remember { mutableStateOf("Choose a mode and start a match.") }
    var waitingForTurnReveal by remember { mutableStateOf(false) }
    var revealedChoiceKey by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(game?.activePlayer, game?.turn, game?.pendingChoice, mode, difficulty) {
        var current = game ?: return@LaunchedEffect
        if (mode != MatchMode.AI || current.activePlayer != 1 ||
            current.winner != null || current.pendingChoice != null) return@LaunchedEffect
        repeat(100) {
            if (current.activePlayer != 1 || current.winner != null || current.pendingChoice != null) return@LaunchedEffect
            val action = DuelMastersAi.chooseAction(current, difficulty)
            when (val result = DuelMastersEngine.apply(current, action)) {
                is ActionResult.Success -> current = result.state
                is ActionResult.Rejected -> {
                    game = current.copy(log = (current.log + ("AI action rejected: " + result.reason)).takeLast(80))
                    message = result.reason
                    return@LaunchedEffect
                }
            }
            game = current
            if (action == GameAction.EndTurn || current.pendingChoice != null) return@LaunchedEffect
            delay(250)
        }
        if (current.activePlayer == 1 && current.winner == null && current.pendingChoice == null) {
            message = "AI turn safety limit reached."
        }
    }

    // AI also makes defensive choices and selects targets for its own effects.
    LaunchedEffect(game?.pendingChoice, mode, difficulty) {
        val current = game ?: return@LaunchedEffect
        if (mode != MatchMode.AI || current.pendingChoice?.chooserPlayerId != 1) return@LaunchedEffect
        val response = DuelMastersAi.respondToChoice(current, difficulty) ?: return@LaunchedEffect
        when (val result = DuelMastersEngine.apply(current, response)) {
            is ActionResult.Success -> game = result.state
            is ActionResult.Rejected -> message = "AI decision rejected: " + result.reason
        }
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Battlefield", style = MaterialTheme.typography.headlineMedium)
        if (game == null) {
            Text("Choose your match type", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (mode == MatchMode.AI) Button(onClick = { mode = MatchMode.AI }) { Text("Vs AI") }
                else OutlinedButton(onClick = { mode = MatchMode.AI }) { Text("Vs AI") }
                if (mode == MatchMode.LOCAL) Button(onClick = { mode = MatchMode.LOCAL }) { Text("Local 2 Player") }
                else OutlinedButton(onClick = { mode = MatchMode.LOCAL }) { Text("Local 2 Player") }
            }
            if (mode == MatchMode.AI) {
                Text("AI difficulty", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AiDifficulty.values().forEach { level ->
                        val label = level.name.lowercase().replaceFirstChar { it.uppercase() }
                        if (difficulty == level) Button(onClick = { difficulty = level }) { Text(label) }
                        else OutlinedButton(onClick = { difficulty = level }) { Text(label) }
                    }
                }
            }
            Text("Playable cards: " + playableCards.size + " / " + catalog.size + " catalogue entries")
            if (savedDecks.isNotEmpty()) {
                Text("Choose a saved deck", style = MaterialTheme.typography.titleSmall)
                savedDecks.forEach { saved ->
                    val count = runCatching { JSONArray(saved.cardIdsJson).length() }.getOrDefault(0)
                    if (selectedSavedDeck?.id == saved.id) Button(onClick = { selectedDeckId = saved.id }) {
                        Text(saved.name + " • " + count)
                    } else OutlinedButton(onClick = { selectedDeckId = saved.id }) {
                        Text(saved.name + " • " + count)
                    }
                }
            } else Text("No saved deck selected. A temporary starter deck will be used.")
            Text(
                "Selected deck: " + selectedDeckCards.size + " cards • " +
                    (if (deckValidation.isValid) "valid" else deckValidation.errors.joinToString()),
                style = MaterialTheme.typography.bodySmall,
                color = if (deckValidation.isValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            Button(
                enabled = deckValidation.isValid,
                onClick = {
                    game = DuelMastersEngine.newGame(
                        selectedDeckCards, selectedDeckCards, "Player 1",
                        if (mode == MatchMode.AI) "AI" else "Player 2"
                    )
                    waitingForTurnReveal = false
                    revealedChoiceKey = null
                    message = "Match started."
                }
            ) { Text("Start match") }
            Text(message, style = MaterialTheme.typography.bodySmall)
        } else {
            val state = game!!
            val pending = state.pendingChoice
            val visiblePlayerId = when {
                mode == MatchMode.AI -> 0
                pending != null -> pending.chooserPlayerId
                else -> state.activePlayer
            }
            val me = state.players[visiblePlayerId]
            val rival = state.players[1 - visiblePlayerId]
            val choiceKey = pending?.toString()
            val needsChoiceHandoff = mode == MatchMode.LOCAL && pending != null &&
                visiblePlayerId != state.activePlayer && revealedChoiceKey != choiceKey

            Text("Turn " + state.turn + " • " + state.players[state.activePlayer].name)
            if (state.winner != null) {
                Text(state.players[state.winner!!].name + " wins!", style = MaterialTheme.typography.headlineSmall)
                Button(onClick = {
                    game = null
                    waitingForTurnReveal = false
                    revealedChoiceKey = null
                }) { Text("New match") }
            } else if (needsChoiceHandoff) {
                PassDeviceCard(
                    playerName = me.name,
                    title = "Defender decision",
                    body = "Pass the device to " + me.name + ". Their hand and decision controls stay hidden until they confirm."
                ) { revealedChoiceKey = choiceKey }
            } else if (mode == MatchMode.LOCAL && pending == null && waitingForTurnReveal) {
                PassDeviceCard(
                    playerName = me.name,
                    title = "Pass the device",
                    body = "Give the device to " + me.name + ". Their hand stays hidden until they confirm."
                ) { waitingForTurnReveal = false }
            } else {
                ZoneSummary("Opponent: " + rival.name, rival.deck.size, rival.shields.size, rival.mana.size, rival.battle.size)
                Text("Opponent battle zone", style = MaterialTheme.typography.labelLarge)
                BattlefieldCardRow(rival.battle)
                Text("Opponent mana zone", style = MaterialTheme.typography.labelLarge)
                BattlefieldCardRow(rival.mana)

                Spacer(Modifier.height(4.dp))
                ZoneSummary("Your zones: " + me.name, me.deck.size, me.shields.size, me.mana.size, me.battle.size)
                Text("Your mana zone", style = MaterialTheme.typography.labelLarge)
                BattlefieldCardRow(me.mana)
                Text("Your battle zone", style = MaterialTheme.typography.labelLarge)
                BattlefieldCardRow(me.battle)

                when (pending) {
                    is PendingChoice.Blocker -> {
                        val attackingName = state.players[pending.attackingPlayerId].battle
                            .firstOrNull { it.instanceId == pending.attackerInstanceId }?.card?.name ?: "attack"
                        Text("Block " + attackingName + "?", style = MaterialTheme.typography.titleMedium)
                        pending.blockerInstanceIds.mapNotNull { id ->
                            me.battle.firstOrNull { it.instanceId == id && !it.tapped }
                        }.forEach { blocker ->
                            Button(onClick = {
                                submit(GameAction.ChooseBlocker(blocker.instanceId), state,
                                    onSuccess = { game = it; revealedChoiceKey = it.pendingChoice?.toString(); message = "Blocker chosen." },
                                    onFailure = { message = it })
                            }) { Text("Block with " + blocker.card.name + " (" + blocker.currentPower + ")") }
                        }
                        OutlinedButton(onClick = {
                            submit(GameAction.ChooseBlocker(null), state,
                                onSuccess = { game = it; revealedChoiceKey = it.pendingChoice?.toString(); message = "Attack not blocked." },
                                onFailure = { message = it })
                        }) { Text("Do not block") }
                    }
                    is PendingChoice.ShieldTrigger -> {
                        Text("Shield Trigger: " + pending.shield.card.name, style = MaterialTheme.typography.titleMedium)
                        if (pending.shield.card.effects.isNotEmpty()) {
                            Text(pending.shield.card.effects.joinToString {
                                it.kind.name.replace('_', ' ').lowercase()
                            }, style = MaterialTheme.typography.bodySmall)
                        }
                        Button(onClick = {
                            submit(GameAction.ResolveShieldTrigger(true), state,
                                onSuccess = { game = it; revealedChoiceKey = it.pendingChoice?.toString(); message = "Shield Trigger used." },
                                onFailure = { message = it })
                        }) { Text("Use Shield Trigger for free") }
                        OutlinedButton(onClick = {
                            submit(GameAction.ResolveShieldTrigger(false), state,
                                onSuccess = { game = it; revealedChoiceKey = it.pendingChoice?.toString(); message = "Shield added to hand." },
                                onFailure = { message = it })
                        }) { Text("Add shield to hand") }
                    }
                    is PendingChoice.EffectTarget -> {
                        Text("Choose a creature to affect", style = MaterialTheme.typography.titleMedium)
                        pending.targetInstanceIds.mapNotNull { id ->
                            state.players[pending.targetPlayerId].battle.firstOrNull { it.instanceId == id }
                        }.forEach { target ->
                            Button(onClick = {
                                submit(GameAction.ChooseEffectTarget(target.instanceId), state,
                                    onSuccess = { game = it; revealedChoiceKey = it.pendingChoice?.toString(); message = target.card.name + " selected." },
                                    onFailure = { message = it })
                            }) { Text(target.card.name + " • Power " + target.currentPower) }
                        }
                    }
                    null -> {
                        Text("Your hand (" + me.hand.size + ")", style = MaterialTheme.typography.titleMedium)
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            me.hand.forEach { instance ->
                                Card(
                                    modifier = Modifier.width(164.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Column(Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Text(instance.card.name, style = MaterialTheme.typography.titleSmall)
                                        Text(instance.card.kind.toString() + " • Cost " + instance.card.cost,
                                            style = MaterialTheme.typography.labelSmall)
                                        if (state.activePlayer == me.id) {
                                            OutlinedButton(
                                                enabled = !state.manaChargedThisTurn,
                                                onClick = {
                                                    submit(GameAction.ChargeMana(instance.instanceId), state,
                                                        onSuccess = { game = it; message = "Mana charged." },
                                                        onFailure = { message = it })
                                                }
                                            ) { Text("Charge mana") }
                                            if (instance.card.kind == CardKind.CREATURE) {
                                                if (instance.card.isEvolution) {
                                                    val race = instance.card.evolutionRace
                                                    val hosts = if (race == null) emptyList() else me.battle.filter { host ->
                                                        host.card.races.any { it.equals(race, ignoreCase = true) }
                                                    }
                                                    Text("Evolution: " + (race ?: "unsupported"), style = MaterialTheme.typography.labelSmall)
                                                    hosts.forEach { host ->
                                                        Button(onClick = {
                                                            submit(GameAction.Summon(instance.instanceId, host.instanceId), state,
                                                                onSuccess = { game = it; message = "Evolution summoned." },
                                                                onFailure = { message = it })
                                                        }) { Text("Evolve onto " + host.card.name) }
                                                    }
                                                } else {
                                                    Button(onClick = {
                                                        submit(GameAction.Summon(instance.instanceId), state,
                                                            onSuccess = { game = it; message = "Creature summoned." },
                                                            onFailure = { message = it })
                                                    }) { Text("Summon") }
                                                }
                                            } else {
                                                Button(onClick = {
                                                    submit(GameAction.CastSpell(instance.instanceId), state,
                                                        onSuccess = { game = it; message = "Spell cast; resolve any requested targets." },
                                                        onFailure = { message = it })
                                                }) { Text("Cast") }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (pending == null && state.activePlayer == me.id) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            submit(GameAction.EndTurn, state,
                                onSuccess = {
                                    game = it
                                    if (mode == MatchMode.LOCAL) waitingForTurnReveal = true
                                    message = "Turn ended."
                                },
                                onFailure = { message = it })
                        }) { Text(if (mode == MatchMode.LOCAL) "End turn & pass device" else "End turn") }
                        OutlinedButton(onClick = {
                            game = null
                            waitingForTurnReveal = false
                            revealedChoiceKey = null
                        }) { Text("Quit") }
                    }
                    Text("Ready creatures can attack:", style = MaterialTheme.typography.labelMedium)
                    me.battle.filter { !it.tapped && (!it.summoningSick || "SPEED_ATTACKER" in it.card.keywords) }
                        .forEach { creature ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                OutlinedButton(onClick = {
                                    submit(GameAction.AttackShield(creature.instanceId), state,
                                        onSuccess = { game = it; message = "Attack declared; resolve any defensive choice." },
                                        onFailure = { message = it })
                                }) { Text("Attack shields: " + creature.card.name) }
                                rival.battle.filter { it.tapped }.forEach { target ->
                                    OutlinedButton(onClick = {
                                        submit(GameAction.AttackCreature(creature.instanceId, target.instanceId), state,
                                            onSuccess = { game = it; message = "Creature attack declared." },
                                            onFailure = { message = it })
                                    }) { Text("Attack " + target.card.name) }
                                }
                            }
                        }
                }
                Text(message, style = MaterialTheme.typography.bodySmall)
                Text("Recent actions", style = MaterialTheme.typography.labelLarge)
                state.log.takeLast(5).reversed().forEach { Text("• " + it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun PassDeviceCard(playerName: String, title: String, body: String, onReveal: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(body)
            Button(onClick = onReveal) { Text(playerName + " — reveal decision") }
        }
    }
}

@Composable
private fun ZoneSummary(title: String, deck: Int, shields: Int, mana: Int, battle: Int) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text("Deck " + deck + " • Shields " + shields + " • Mana " + mana + " • Creatures " + battle,
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun BattlefieldCardRow(cards: List<CardInstance>) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (cards.isEmpty()) Text("Empty", style = MaterialTheme.typography.bodySmall)
        cards.forEach { instance ->
            val rotation by animateFloatAsState(
                targetValue = if (instance.tapped) 90f else 0f,
                label = "card-tap-rotation"
            )
            Card(
                modifier = Modifier.width(124.dp).height(156.dp).animateContentSize()
                    .graphicsLayer { rotationZ = rotation },
                colors = CardDefaults.cardColors(
                    containerColor = if (instance.tapped) MaterialTheme.colorScheme.surfaceVariant
                    else MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(Modifier.fillMaxSize().padding(9.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    Text(instance.card.name, style = MaterialTheme.typography.titleSmall)
                    if (instance.card.civilizations.isNotEmpty()) {
                        Text(instance.card.civilizations.joinToString(" / "), style = MaterialTheme.typography.labelSmall)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Cost " + instance.card.cost, style = MaterialTheme.typography.labelSmall)
                        if (instance.card.kind == CardKind.CREATURE) {
                            Text("Power " + instance.currentPower, style = MaterialTheme.typography.labelMedium)
                            if (instance.evolvedCards.isNotEmpty()) {
                                Text("Evolution stack: " + instance.evolvedCards.size, style = MaterialTheme.typography.labelSmall)
                            }
                        } else Text("SPELL", style = MaterialTheme.typography.labelMedium)
                        if (instance.card.keywords.isNotEmpty()) {
                            Text(instance.card.keywords.joinToString(" • ") { it.replace('_', ' ') },
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Text(if (instance.tapped) "TAPPED" else "READY", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

private fun submit(
    action: GameAction,
    state: GameState,
    onSuccess: (GameState) -> Unit,
    onFailure: (String) -> Unit
) {
    when (val result = DuelMastersEngine.apply(state, action)) {
        is ActionResult.Success -> onSuccess(result.state)
        is ActionResult.Rejected -> onFailure(result.reason)
    }
}

internal fun CardEntity.toGameCard(): GameCard? {
    if (implementationStatus != "SUPPORTED") return null
    val kind = when (type) {
        "CREATURE", "EVOLUTION_CREATURE" -> CardKind.CREATURE
        "SPELL" -> CardKind.SPELL
        else -> return null
    }
    val mechanics = runCatching {
        val json = JSONArray(mechanicsJson)
        (0 until json.length()).map { json.optString(it) }.toSet()
    }.getOrDefault(emptySet())
    val keywordSet = CardTextParser.keywords(rulesText, mechanics)
    val powerValue = Regex("\\d+").find(power.orEmpty())?.value?.toIntOrNull() ?: 0
    val civilizations = runCatching {
        val json = JSONArray(civilizationsJson)
        (0 until json.length()).map { json.optString(it) }.toSet()
    }.getOrDefault(emptySet())
    val races = runCatching {
        val json = JSONArray(racesJson)
        (0 until json.length()).map { json.optString(it).trim() }.filter { it.isNotBlank() }.toSet()
    }.getOrDefault(emptySet())
    return GameCard(
        id = cardId,
        name = name,
        kind = kind,
        cost = cost ?: 0,
        power = powerValue,
        civilizations = civilizations,
        keywords = keywordSet,
        shieldTrigger = "SHIELD_TRIGGER" in keywordSet,
        effects = CardTextParser.effects(rulesText),
        races = races,
        evolutionRace = if (type == "EVOLUTION_CREATURE") CardTextParser.evolutionRace(rulesText) else null,
        isEvolution = type == "EVOLUTION_CREATURE",
        powerAttackBonus = CardTextParser.powerAttackBonus(rulesText)
    )
}
