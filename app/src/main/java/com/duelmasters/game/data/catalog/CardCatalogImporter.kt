package com.duelmasters.game.data.catalog

import android.content.Context
import androidx.room.withTransaction
import com.duelmasters.game.data.local.CardCatalogMetadataEntity
import com.duelmasters.game.data.local.CardEntity
import com.duelmasters.game.data.local.CardPrintingEntity
import com.duelmasters.game.data.local.DuelMastersDatabase
import org.json.JSONArray
import org.json.JSONObject

/** Imports the bundled, versioned English catalogue into Room without network access. */
class CardCatalogImporter(
    private val context: Context,
    private val database: DuelMastersDatabase
) {
    suspend fun importIfNeeded(force: Boolean = false): ImportResult {
        val root = context.assets.open("cards/card-catalog.json").bufferedReader().use { it.readText() }
        val json = JSONObject(root)
        val version = json.getInt("catalogVersion")
        val existing = database.cardDao().getMetadata()
        if (!force && existing?.catalogVersion == version) {
            return ImportResult(existing.cardCount, existing.printingCount, imported = false)
        }

        val cardRows = mutableListOf<CardEntity>()
        val printingRows = mutableListOf<CardPrintingEntity>()
        val cards = json.getJSONArray("cards")
        for (index in 0 until cards.length()) {
            val card = cards.getJSONObject(index)
            val provenance = card.getJSONObject("provenance")
            cardRows += CardEntity(
                cardId = card.getString("cardId"),
                name = card.getString("name"),
                type = card.getString("type"),
                civilizationsJson = card.getJSONArrayOrEmpty("civilizations").toString(),
                cost = if (card.isNull("cost")) null else card.optInt("cost"),
                power = card.optString("power").takeUnless { it == "null" || it.isBlank() },
                racesJson = card.getJSONArrayOrEmpty("races").toString(),
                rulesText = card.optString("rulesText"),
                flavorText = card.optString("flavorText").takeUnless { it == "null" },
                mechanicsJson = card.getJSONArrayOrEmpty("mechanics").toString(),
                implementationStatus = card.optString("implementationStatus", "METADATA_ONLY"),
                effectDefinitionIdsJson = card.getJSONArrayOrEmpty("effectDefinitionIds").toString(),
                sourceName = provenance.optString("sourceName"),
                sourceUrl = provenance.optString("sourceUrl"),
                releaseClass = provenance.optString("releaseClass", "UNVERIFIED")
            )
            val printings = card.optJSONArray("printings") ?: JSONArray()
            for (p in 0 until printings.length()) {
                val printing = printings.getJSONObject(p)
                printingRows += CardPrintingEntity(
                    cardId = card.getString("cardId"),
                    setCode = printing.optString("setCode", "UNKNOWN"),
                    setName = printing.optString("setName").takeUnless { it == "null" },
                    collectorNumber = printing.optString("collectorNumber", p.toString()),
                    rarity = printing.optString("rarity").takeUnless { it == "null" },
                    illustrator = printing.optString("illustrator").takeUnless { it == "null" },
                    releaseClass = printing.optString("releaseClass", "UNVERIFIED"),
                    imageAssetKey = printing.optString("imageAssetKey").takeUnless { it == "null" },
                    sourceUrl = printing.optString("sourceUrl").takeUnless { it == "null" }
                )
            }
        }

        database.withTransaction {
            // Upsert card definitions rather than deleting them: collection rows reference card IDs.
            cardRows.chunked(250).forEach { database.cardDao().upsertCards(it) }
            database.cardDao().clearPrintings()
            printingRows.chunked(400).forEach { database.cardDao().upsertPrintings(it) }
            database.cardDao().saveMetadata(
                CardCatalogMetadataEntity(
                    catalogVersion = version,
                    importedAtEpochMs = System.currentTimeMillis(),
                    cardCount = cardRows.size,
                    printingCount = printingRows.size
                )
            )
        }
        return ImportResult(cardRows.size, printingRows.size, imported = true)
    }

    private fun JSONObject.getJSONArrayOrEmpty(key: String): JSONArray =
        optJSONArray(key) ?: JSONArray()
}

data class ImportResult(val cardCount: Int, val printingCount: Int, val imported: Boolean)
