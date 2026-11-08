package com.duelmasters.game.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.duelmasters.game.data.catalog.CardCatalogRepository
import com.duelmasters.game.data.local.DuelMastersDatabaseProvider

@Composable
fun DuelMastersApp() {
    Scaffold { innerPadding ->
        CardCatalogueScreen(modifier = Modifier.padding(innerPadding))
    }
}

@Composable
private fun CardCatalogueScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val database = remember(context) { DuelMastersDatabaseProvider.get(context) }
    val repository = remember(database) { CardCatalogRepository(database.cardDao()) }
    var query by rememberSaveable { mutableStateOf("") }
    val cardsFlow = remember(query, repository) {
        repository.search(query = query, limit = 80, offset = 0)
    }
    val cards by cardsFlow.collectAsState(initial = emptyList())

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Duel Masters", style = MaterialTheme.typography.headlineLarge)
        Text("Offline English card catalogue", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search card names and rules text") },
            singleLine = true
        )
        Text(
            text = if (cards.isEmpty()) "No cards found yet. The catalogue imports on first launch."
            else "Showing ${cards.size} cards",
            style = MaterialTheme.typography.labelMedium
        )
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(cards, key = { it.cardId }) { card ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(card.name, style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(card.type.replace('_', ' '), style = MaterialTheme.typography.labelMedium)
                            Text("Cost: ${card.cost ?: "—"}", style = MaterialTheme.typography.labelMedium)
                            Text(card.releaseClass.replace('_', ' '), style = MaterialTheme.typography.labelSmall)
                        }
                        if (card.civilizationsJson != "[]") {
                            Text(card.civilizationsJson, style = MaterialTheme.typography.labelSmall)
                        }
                        if (card.rulesText.isNotBlank()) {
                            Text(card.rulesText, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
