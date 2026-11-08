package com.duelmasters.game.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.duelmasters.game.data.catalog.CardCatalogRepository
import com.duelmasters.game.data.local.CardEntity
import com.duelmasters.game.data.local.CardPrintingEntity
import com.duelmasters.game.data.local.DuelMastersDatabaseProvider
import kotlinx.coroutines.flow.first
import org.json.JSONArray

@Composable
fun DuelMastersApp() {
    val context = LocalContext.current
    val database = remember(context) { DuelMastersDatabaseProvider.get(context) }
    var selectedTab by rememberSaveable { mutableStateOf("Cards") }
    Scaffold { innerPadding ->
        Column(Modifier.padding(innerPadding).fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (selectedTab == "Cards") androidx.compose.material3.Button(onClick = { selectedTab = "Cards" }) { Text("Card catalogue") }
                else androidx.compose.material3.OutlinedButton(onClick = { selectedTab = "Cards" }) { Text("Card catalogue") }
                if (selectedTab == "Decks") androidx.compose.material3.Button(onClick = { selectedTab = "Decks" }) { Text("Decks") }
                else androidx.compose.material3.OutlinedButton(onClick = { selectedTab = "Decks" }) { Text("Decks") }
                if (selectedTab == "Play") androidx.compose.material3.Button(onClick = { selectedTab = "Play" }) { Text("Play") }
                else androidx.compose.material3.OutlinedButton(onClick = { selectedTab = "Play" }) { Text("Play") }
            }
            when (selectedTab) {
                "Cards" -> CardCatalogueScreen(modifier = Modifier.weight(1f))
                "Decks" -> DeckBuilderScreen(database = database, modifier = Modifier.weight(1f))
                else -> GameplayScreen(database = database, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CardCatalogueScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val database = remember(context) { DuelMastersDatabaseProvider.get(context) }
    val repository = remember(database) { CardCatalogRepository(database.cardDao()) }
    var query by rememberSaveable { mutableStateOf("") }
    var offset by remember(query) { mutableIntStateOf(0) }
    var cards by remember(query) { mutableStateOf(emptyList<CardEntity>()) }
    var hasMore by remember(query) { mutableStateOf(true) }
    var loading by remember { mutableStateOf(false) }
    var selectedCardId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedPrintings by remember { mutableStateOf<List<CardPrintingEntity>>(emptyList()) }

    LaunchedEffect(query, offset) {
        loading = true
        try {
            val nextPage = repository.search(query = query, limit = PAGE_SIZE, offset = offset).first()
            cards = if (offset == 0) nextPage else (cards + nextPage).distinctBy { it.cardId }
            hasMore = nextPage.size == PAGE_SIZE
        } finally {
            loading = false
        }
    }

    LaunchedEffect(selectedCardId) {
        selectedPrintings = selectedCardId?.let { database.cardDao().getPrintings(it) }.orEmpty()
    }

    val selectedCard = cards.firstOrNull { it.cardId == selectedCardId }

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
            onValueChange = {
                query = it
                selectedCardId = null
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search card names and rules text") },
            singleLine = true
        )
        Text(
            text = when {
                loading && cards.isEmpty() -> "Loading catalogue…"
                cards.isEmpty() -> "No cards found."
                else -> "Showing ${cards.size} cards"
            },
            style = MaterialTheme.typography.labelMedium
        )
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(cards, key = { it.cardId }) { card ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { selectedCardId = card.cardId },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(card.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (card.implementationStatus == "SUPPORTED") "Playable • supported rules"
                            else "Catalogue only • rules not implemented",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (card.implementationStatus == "SUPPORTED") MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(card.type.replace('_', ' '), style = MaterialTheme.typography.labelMedium)
                            Text("Cost: ${card.cost ?: "—"}", style = MaterialTheme.typography.labelMedium)
                            Text(card.releaseClass.replace('_', ' '), style = MaterialTheme.typography.labelSmall)
                        }
                        Text("Tap for full card details and printings", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (hasMore) {
                item(key = "catalogue-load-more") {
                    Button(
                        onClick = { if (!loading) offset += PAGE_SIZE },
                        enabled = !loading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (loading) "Loading…" else "Load more cards")
                    }
                }
            }
        }
    }

    if (selectedCard != null) {
        CardDetailsDialog(
            card = selectedCard,
            printings = selectedPrintings,
            onDismiss = { selectedCardId = null }
        )
    }
}

@Composable
private fun CardDetailsDialog(
    card: CardEntity,
    printings: List<CardPrintingEntity>,
    onDismiss: () -> Unit
) {
    val civilizations = jsonStringList(card.civilizationsJson)
    val races = jsonStringList(card.racesJson)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(card.name) },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    if (card.implementationStatus == "SUPPORTED") "Playable • supported rules"
                    else "Catalogue only • rules not implemented",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (card.implementationStatus == "SUPPORTED") MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )
                Text("Type: ${card.type.replace('_', ' ')}")
                Text("Civilizations: ${civilizations.joinToString(" / ").ifBlank { "—" }}")
                Text("Cost: ${card.cost ?: "—"}")
                Text("Power: ${card.power ?: "—"}")
                Text("Races: ${races.joinToString(", ").ifBlank { "—" }}")
                Text("Release class: ${card.releaseClass.replace('_', ' ')}")
                Text("Rules text", style = MaterialTheme.typography.titleSmall)
                Text(card.rulesText.ifBlank { "No additional rules text in the bundled source." })
                Text("Printings (${printings.size})", style = MaterialTheme.typography.titleSmall)
                if (printings.isEmpty()) {
                    Text("No printing records were found.")
                } else {
                    printings.forEach { printing ->
                        Text(
                            listOfNotNull(
                                printing.setCode,
                                printing.collectorNumber.takeIf { it.isNotBlank() }?.let { "#$it" },
                                printing.rarity
                            ).joinToString(" • "),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

private fun jsonStringList(value: String): List<String> = runCatching {
    val array = JSONArray(value)
    (0 until array.length()).map { array.optString(it) }.filter { it.isNotBlank() }
}.getOrDefault(emptyList())

private const val PAGE_SIZE = 80
