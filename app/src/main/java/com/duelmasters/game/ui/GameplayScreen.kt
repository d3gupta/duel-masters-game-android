package com.duelmasters.game.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import com.duelmasters.game.data.local.CardEntity
import com.duelmasters.game.data.local.DuelMastersDatabase
import com.duelmasters.game.engine.BasicDuelMastersAi
import com.duelmasters.game.engine.CardKind
import com.duelmasters.game.engine.DuelMastersEngine
import com.duelmasters.game.engine.GameAction
import com.duelmasters.game.engine.GameCard
import com.duelmasters.game.engine.GameState
import com.duelmasters.game.engine.DeckValidator
import org.json.JSONArray
import com.duelmasters.game.engine.ActionResult
import kotlinx.coroutines.delay

private enum class MatchMode { AI, LOCAL }

@Composable
fun GameplayScreen(database: DuelMastersDatabase, modifier: Modifier = Modifier) {
    val catalog by remember(database) {
        database.cardDao().search("", "", "", null, "", "", "", 5000, 0)
    }.collectAsState(initial = emptyList())
    val playableCards = remember(catalog) { catalog.mapNotNull { it.toGameCard() } }
    val starterDeck = remember(playableCards) { playableCards.distinctBy { it.name.trim().lowercase() }.take(40) }
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
    val selectedDeckValidation = remember(selectedDeckCards) { DeckValidator.validate(selectedDeckCards) }
    var mode by remember { mutableStateOf(MatchMode.AI) }
    var game by remember { mutableStateOf<GameState?>(null) }
    var message by remember { mutableStateOf("Choose a mode and start a match.") }
    var showPrivateHand by remember { mutableStateOf(false) }

    LaunchedEffect(game?.activePlayer, game?.turn, mode) {
        val initial = game
        if (initial != null && mode == MatchMode.AI && initial.activePlayer == 1 && initial.winner == null) {
            var current = initial
            repeat(30) {
                if (current.activePlayer != 1 || current.winner != null) return@LaunchedEffect
                val action = BasicDuelMastersAi.chooseAction(current)
                val result = DuelMastersEngine.apply(current, action)
                if (result is ActionResult.Success) current = result.state
                game = current
                if (action == GameAction.EndTurn) return@LaunchedEffect
                delay(300)
            }
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
            Text("Playable card definitions: ${playableCards.size}")
            if (savedDecks.isNotEmpty()) {
                Text("Choose a saved deck", style = MaterialTheme.typography.titleSmall)
                savedDecks.forEach { saved ->
                    val count = runCatching { JSONArray(saved.cardIdsJson).length() }.getOrDefault(0)
                    if (selectedSavedDeck?.id == saved.id) Button(onClick = { selectedDeckId = saved.id }) { Text("${saved.name} • $count") }
                    else OutlinedButton(onClick = { selectedDeckId = saved.id }) { Text("${saved.name} • $count") }
                }
            } else {
                Text("No saved deck selected. A temporary 40-card starter deck from the catalogue will be used.")
            }
            Text("Selected deck: ${selectedDeckCards.size} cards • ${if (selectedDeckValidation.isValid) "valid" else selectedDeckValidation.errors.joinToString()}", style = MaterialTheme.typography.bodySmall)
            Button(
                onClick = {
                    if (selectedDeckValidation.isValid) {
                        val deck = selectedDeckCards
                        game = DuelMastersEngine.newGame(deck, deck, "Player 1", if (mode == MatchMode.AI) "AI" else "Player 2")
                        message = "Match started."
                    } else message = "The selected deck is invalid. Check the minimum 40-card size and four-copy limit."
                },
                enabled = selectedDeckValidation.isValid
            ) { Text("Start match") }
            Text(message, style = MaterialTheme.typography.bodySmall)
        } else {
            val state = game!!
            val me = state.players[if (mode == MatchMode.LOCAL && showPrivateHand) 1 else 0]
            val rival = state.players[1 - me.id]
            Text("Turn ${state.turn} • ${if (state.activePlayer == 0) "Player 1" else if (mode == MatchMode.AI) "AI" else "Player 2"}")
            if (state.winner != null) {
                Text("${state.players[state.winner!!].name} wins!", style = MaterialTheme.typography.headlineSmall)
                Button(onClick = { game = null; showPrivateHand = false }) { Text("New match") }
            } else {
                ZoneSummary("Opponent: ${rival.name}", rival.deck.size, rival.shields.size, rival.mana.size, rival.battle.size)
                Text("Opponent battle zone", style = MaterialTheme.typography.labelLarge)
                CardRow(rival.battle.map { it.card.name + if (it.tapped) " (tapped)" else "" })
                Spacer(Modifier.height(4.dp))
                ZoneSummary("Your zones: ${me.name}", me.deck.size, me.shields.size, me.mana.size, me.battle.size)
                Text("Your battle zone", style = MaterialTheme.typography.labelLarge)
                CardRow(me.battle.map { it.card.name + if (it.tapped) " (tapped)" else "" })
                Text("Your hand (${me.hand.size})", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    me.hand.forEach { instance ->
                        Card(
                            modifier = Modifier.width(156.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(instance.card.name, style = MaterialTheme.typography.titleSmall)
                                Text("${instance.card.kind} • Cost ${instance.card.cost}", style = MaterialTheme.typography.labelSmall)
                                if (state.activePlayer == me.id) {
                                    OutlinedButton(
                                        onClick = { applyAction(GameAction.ChargeMana(instance.instanceId), state) { game = it; message = "Mana charged." } },
                                        enabled = !state.manaChargedThisTurn
                                    ) { Text("Charge") }
                                    if (instance.card.kind == CardKind.CREATURE) {
                                        Button(onClick = { applyAction(GameAction.Summon(instance.instanceId), state) { game = it; message = "Creature summoned." } }) { Text("Summon") }
                                    } else {
                                        Button(onClick = { applyAction(GameAction.CastSpell(instance.instanceId), state) { game = it; message = "Spell cast (effect resolution pending)." } }) { Text("Cast") }
                                    }
                                }
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = state.activePlayer == me.id,
                        onClick = {
                            val result = DuelMastersEngine.apply(state, GameAction.EndTurn)
                            if (result is ActionResult.Success) {
                                game = result.state
                                if (mode == MatchMode.LOCAL) {
                                    showPrivateHand = !showPrivateHand
                                    message = "Pass the device to ${if (showPrivateHand) "Player 2" else "Player 1"}."
                                } else message = "Turn ended."
                            } else if (result is ActionResult.Rejected) message = result.reason
                        }
                    ) { Text(if (mode == MatchMode.LOCAL) "End turn & pass device" else "End turn") }
                    OutlinedButton(onClick = { game = null; showPrivateHand = false }) { Text("Quit") }
                }
                if (state.activePlayer == me.id) {
                    Text("Ready creatures can attack shields:", style = MaterialTheme.typography.labelMedium)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        me.battle.filter { !it.tapped && (!it.summoningSick || "SPEED_ATTACKER" in it.card.keywords) }.forEach { creature ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                OutlinedButton(onClick = {
                                    applyAction(GameAction.AttackShield(creature.instanceId), state) { game = it; message = "Shield attacked." }
                                }) { Text("Shield: ${creature.card.name}") }
                                rival.battle.filter { it.tapped }.forEach { target ->
                                    OutlinedButton(onClick = {
                                        applyAction(GameAction.AttackCreature(creature.instanceId, target.instanceId), state) { game = it; message = "Creature battle resolved." }
                                    }) { Text("Attack ${target.card.name}") }
                                }
                            }
                        }
                    }
                }
                Text(message, style = MaterialTheme.typography.bodySmall)
                Text("Recent actions", style = MaterialTheme.typography.labelLarge)
                state.log.takeLast(4).reversed().forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun ZoneSummary(title: String, deck: Int, shields: Int, mana: Int, battle: Int) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text("Deck $deck   •   Shields $shields   •   Mana $mana   •   Creatures $battle", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CardRow(names: List<String>) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (names.isEmpty()) Text("Empty", style = MaterialTheme.typography.bodySmall)
        names.forEach { name ->
            Text(name, Modifier.background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp)).padding(8.dp), style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun applyAction(action: GameAction, state: GameState, onSuccess: (GameState) -> Unit) {
    when (val result = DuelMastersEngine.apply(state, action)) {
        is ActionResult.Success -> onSuccess(result.state)
        is ActionResult.Rejected -> Unit
    }
}

internal fun CardEntity.toGameCard(): GameCard? {
    val kind = when (type) {
        "CREATURE", "EVOLUTION_CREATURE" -> CardKind.CREATURE
        "SPELL" -> CardKind.SPELL
        else -> return null
    }
    val mechanics = runCatching {
        val json = JSONArray(mechanicsJson)
        (0 until json.length()).map { json.optString(it).uppercase().replace(' ', '_') }.toSet()
    }.getOrDefault(emptySet())
    val keywordSet = mechanics + if (rulesText.contains("speed attacker", ignoreCase = true)) setOf("SPEED_ATTACKER") else emptySet()
    val powerValue = Regex("\\d+").find(power.orEmpty())?.value?.toIntOrNull() ?: 0
    return GameCard(
        id = cardId,
        name = name,
        kind = kind,
        cost = cost ?: 0,
        power = powerValue,
        keywords = keywordSet,
        shieldTrigger = rulesText.contains("shield trigger", ignoreCase = true)
    )
}
