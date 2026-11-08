package com.duelmasters.game.ui

import org.json.JSONArray
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.duelmasters.game.data.local.CardEntity
import com.duelmasters.game.data.local.DeckEntity
import com.duelmasters.game.data.local.DuelMastersDatabase
import kotlinx.coroutines.launch

@Composable
fun DeckBuilderScreen(database: DuelMastersDatabase, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val savedDecks by database.deckDao().observeAll().collectAsState(initial = emptyList())
    var search by rememberSaveable { mutableStateOf("") }
    var deckName by rememberSaveable { mutableStateOf("My Duel Masters Deck") }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var message by remember { mutableStateOf("Add cards until your deck contains at least 40 cards.") }
    val selected = remember { mutableStateListOf<CardEntity>() }
    val results by remember(search) {
        database.cardDao().search(search, "", "", null, "", "", "", 60, 0)
    }.collectAsState(initial = emptyList())

    LaunchedEffect(editingId) {
        val id = editingId ?: return@LaunchedEffect
        val deck = savedDecks.firstOrNull { it.id == id } ?: return@LaunchedEffect
        deckName = deck.name
        selected.clear()
        val ids = runCatching {
            val json = JSONArray(deck.cardIdsJson)
            (0 until json.length()).map { json.getString(it) }
        }.getOrDefault(emptyList())
        ids.forEach { cardId -> database.cardDao().getById(cardId)?.let(selected::add) }
    }

    val copiesExceeded = selected.groupingBy { it.name.trim().lowercase() }.eachCount().any { it.value > 4 }
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Deck builder", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(deckName, { deckName = it }, label = { Text("Deck name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Text("Cards: ${selected.size} • Minimum 40 • No maximum • Maximum 4 copies per card name")
        Text(message, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = deckName.isNotBlank() && selected.size >= 40 && !copiesExceeded,
                onClick = {
                    scope.launch {
                        val ids = JSONArray().apply { selected.forEach { put(it.cardId) } }.toString()
                        database.deckDao().upsert(DeckEntity(id = editingId ?: 0L, name = deckName.trim(), cardIdsJson = ids))
                        editingId = null
                        selected.clear()
                        message = "Deck saved."
                    }
                }
            ) { Text(if (editingId == null) "Save deck" else "Update deck") }
            OutlinedButton(onClick = { editingId = null; selected.clear(); deckName = "My Duel Masters Deck" }) { Text("New deck") }
        }
        if (copiesExceeded) Text("A card name cannot appear more than four times.", color = MaterialTheme.colorScheme.error)
        Text("Your decks", style = MaterialTheme.typography.titleMedium)
        LazyColumn(modifier = Modifier.fillMaxWidth().weight(0.7f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(savedDecks, key = { it.id }) { deck ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(deck.name, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { editingId = deck.id; message = "Editing saved deck." }) { Text("Edit") }
                    OutlinedButton(onClick = { scope.launch { database.deckDao().deleteById(deck.id) } }) { Text("Delete") }
                }
            }
        }
        OutlinedTextField(search, { search = it }, label = { Text("Search cards") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Text("Card catalogue", style = MaterialTheme.typography.titleMedium)
        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1.3f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(results, key = { it.cardId }) { card ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(card.name, style = MaterialTheme.typography.titleSmall)
                            Text("Cost ${card.cost ?: "—"} • ${card.type}", style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedButton(onClick = {
                            val count = selected.count { it.name.equals(card.name, ignoreCase = true) }
                            if (count >= 4) message = "Four-copy limit reached for ${card.name}."
                            else { selected.add(card); message = "${card.name} added." }
                        }) { Text("Add") }
                    }
                }
            }
        }
        if (selected.isNotEmpty()) {
            Text("Selected cards", style = MaterialTheme.typography.titleMedium)
            LazyColumn(Modifier.fillMaxWidth().weight(0.8f)) {
                items(selected.toList().withIndex().toList(), key = { "${it.value.cardId}-${it.index}" }) { indexed ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${indexed.value.name}", modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = { selected.removeAt(indexed.index) }) { Text("Remove") }
                    }
                }
            }
        }
    }
}

@Composable
fun GameplayScreen(database: DuelMastersDatabase, modifier: Modifier = Modifier) {
    val decks by database.deckDao().observeAll().collectAsState(initial = emptyList())
    var mode by rememberSaveable { mutableStateOf("AI") }
    var selectedDeck by rememberSaveable { mutableStateOf<Long?>(null) }
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Play Duel Masters", style = MaterialTheme.typography.headlineMedium)
        Text("Choose a local game mode")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (mode == "AI") Button(onClick = { mode = "AI" }) { Text("Vs AI") }
            else OutlinedButton(onClick = { mode = "AI" }) { Text("Vs AI") }
            if (mode == "LOCAL") Button(onClick = { mode = "LOCAL" }) { Text("2 Players") }
            else OutlinedButton(onClick = { mode = "LOCAL" }) { Text("2 Players") }
        }
        Text("Choose a deck", style = MaterialTheme.typography.titleMedium)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(decks, key = { it.id }) { deck ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(deck.name, modifier = Modifier.weight(1f))
                        if (selectedDeck == deck.id) Button(onClick = { selectedDeck = deck.id }) { Text("Selected") }
                        else OutlinedButton(onClick = { selectedDeck = deck.id }) { Text("Select") }
                    }
                }
            }
        }
        Text(
            if (selectedDeck == null) "Select a saved deck to prepare a match."
            else "Deck selected. The full match screen and complete rules integration are still under development.",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            if (mode == "AI") "Mode: single-player vs AI" else "Mode: local pass-and-play",
            style = MaterialTheme.typography.labelLarge
        )
    }
}
