package com.duelmasters.game.ui

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.duelmasters.game.data.local.DeckEntity
import com.duelmasters.game.data.local.DuelMastersDatabase
import com.duelmasters.game.engine.DeckValidator
import kotlinx.coroutines.launch
import org.json.JSONArray

@Composable
fun DeckBuilderScreen(database: DuelMastersDatabase, modifier: Modifier = Modifier) {
    val allCards by remember(database) {
        database.cardDao().search("", "", "", null, "", "", "", 5000, 0)
    }.collectAsState(initial = emptyList())
    val cards = remember(allCards) { allCards.mapNotNull { entity -> entity.toGameCard()?.let { entity to it } } }
    val savedDecksFlow = remember(database) { database.deckDao().observeAll() }
    val savedDecks by savedDecksFlow.collectAsState(initial = emptyList())
    var deckName by rememberSaveable { mutableStateOf("My Deck") }
    var editingDeckId by rememberSaveable { mutableStateOf<Long?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var quantities by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var status by remember { mutableStateOf("Add cards to build a deck. There is no maximum deck size.") }
    val scope = rememberCoroutineScope()

    val selectedCards = cards.flatMap { (entity, gameCard) ->
        List(quantities[entity.cardId] ?: 0) { gameCard }
    }
    val validation = DeckValidator.validate(selectedCards)
    val filtered = cards.filter { (entity, _) ->
        query.isBlank() || entity.name.contains(query, ignoreCase = true)
    }

    Column(
        modifier = modifier.fillMaxSize().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Deck builder", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(
            value = deckName,
            onValueChange = { deckName = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Deck name") },
            singleLine = true
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Find cards") },
            singleLine = true
        )
        Text("Cards: ${selectedCards.size} • Unique names: ${selectedCards.map { it.name.lowercase() }.distinct().size}")
        Text(
            if (validation.isValid) "Deck is valid" else validation.errors.joinToString(" "),
            color = if (validation.isValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = deckName.isNotBlank() && validation.isValid,
                onClick = {
                    val ids = JSONArray()
                    cards.forEach { (entity, _) ->
                        repeat(quantities[entity.cardId] ?: 0) { ids.put(entity.cardId) }
                    }
                    scope.launch {
                        database.deckDao().upsert(
                            DeckEntity(id = editingDeckId ?: 0L, name = deckName.trim(), cardIdsJson = ids.toString())
                        )
                        status = if (editingDeckId == null) "Saved deck: $deckName" else "Updated deck: $deckName"
                        editingDeckId = null
                        quantities = emptyMap()
                    }
                }
            ) { Text("Save deck") }
            OutlinedButton(onClick = { quantities = emptyMap(); editingDeckId = null; deckName = "My Deck"; status = "New deck started." }) { Text("New") }
        }
        Text(status, style = MaterialTheme.typography.bodySmall)
        Text("Card pool", style = MaterialTheme.typography.titleMedium)
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(filtered, key = { it.first.cardId }) { (entity, gameCard) ->
                val count = quantities[entity.cardId] ?: 0
                val sameNameCount = selectedCards.count { it.name.equals(gameCard.name, ignoreCase = true) }
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(9.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(entity.name, style = MaterialTheme.typography.titleSmall)
                            Text("${entity.type.replace('_', ' ')} • Cost ${entity.cost ?: "—"} • ${entity.releaseClass}", style = MaterialTheme.typography.labelSmall)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedButton(
                                enabled = count > 0,
                                onClick = {
                                    quantities = quantities.toMutableMap().also { map ->
                                        if (count == 1) map.remove(entity.cardId) else map[entity.cardId] = count - 1
                                    }
                                }
                            ) { Text("−") }
                            Text(count.toString(), modifier = Modifier.padding(top = 12.dp))
                            Button(
                                enabled = sameNameCount < DeckValidator.MAX_COPIES_PER_CARD,
                                onClick = { quantities = quantities + (entity.cardId to (count + 1)) }
                            ) { Text("+") }
                        }
                    }
                }
            }
        }
        Text("Saved decks (${savedDecks.size})", style = MaterialTheme.typography.titleSmall)
        savedDecks.forEach { deck ->
            val count = runCatching { JSONArray(deck.cardIdsJson).length() }.getOrDefault(0)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${deck.name} • $count cards", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = {
                    val restored = runCatching {
                        val ids = JSONArray(deck.cardIdsJson)
                        buildMap {
                            repeat(ids.length()) { index ->
                                val id = ids.getString(index)
                                put(id, (get(id) ?: 0) + 1)
                            }
                        }
                    }.getOrDefault(emptyMap())
                    quantities = restored
                    deckName = deck.name
                    editingDeckId = deck.id
                    status = "Editing ${deck.name}"
                }) { Text("Edit") }
                OutlinedButton(onClick = {
                    scope.launch {
                        database.deckDao().deleteById(deck.id)
                        if (editingDeckId == deck.id) { editingDeckId = null; quantities = emptyMap() }
                        status = "Deleted ${deck.name}"
                    }
                }) { Text("Delete") }
            }
        }
    }
}
