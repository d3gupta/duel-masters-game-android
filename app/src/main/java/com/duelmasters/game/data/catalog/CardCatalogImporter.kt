package com.duelmasters.game.data.catalog

import android.content.Context
import androidx.room.withTransaction
import com.duelmasters.game.data.local.CardCatalogMetadataEntity
import com.duelmasters.game.data.local.CardEntity
import com.duelmasters.game.data.local.CardPrintingEntity
import com.duelmasters.game.data.local.DuelMastersDatabase
import org.json.JSONArray
import org.json.JSONObject

private const val SOURCE_REPOSITORY = "https://github.com/Latepate64/duel-masters-json"

/** Imports the bundled English JSON catalogue into Room. No network is used at runtime. */
class CardCatalogImporter(
    private val context: Context,
    private val database: DuelMastersDatabase
) {
    suspend fun importIfNeeded(force: Boolean = false): ImportResult {
        val json = context.assets.open("cards/card-catalog.json")
            .bufferedReader()
            .use { JSONObject(it.readText()) }
        require(json.optString("language") == "en") { "Only English catalogues are supported." }
        val version = json.getInt("catalogVersion")
        val existing = database.cardDao().getMetadata()
        if (!force && existing?.catalogVersion == version) {
            return ImportResult(existing.cardCount, existing.printingCount, imported = false)
        }

        val cards = json.getJSONArray("cards")
        require(cards.length() > 0) { "Bundled card catalogue is empty." }
        val cardRows = ArrayList<CardEntity>(cards.length())
        val printingRows = mutableListOf<CardPrintingEntity>()
        val seenIds = HashSet<String>(cards.length())

        for (index in 0 until cards.length()) {
            val card = cards.getJSONObject(index)
            val cardId = card.getString("cardId")
            require(seenIds.add(cardId)) { "Duplicate cardId in catalogue: $cardId" }
            val provenance = card.getJSONObject("provenance")
            cardRows += CardEntity(
                cardId = cardId,
                name = card.getString("name"),
                type = card.optString("type", "OTHER"),
                civilizationsJson = card.optJSONArray("civilizations").orEmptyJson().toString(),
                cost = card.intOrNull("cost"),
                power = card.stringOrNull("power"),
                racesJson = card.optJSONArray("races").orEmptyJson().toString(),
                rulesText = card.optString("rulesText"),
                flavorText = card.stringOrNull("flavorText"),
                mechanicsJson = card.optJSONArray("mechanics").orEmptyJson().toString(),
                implementationStatus = card.optString("implementationStatus", "METADATA_ONLY"),
                effectDefinitionIdsJson = card.optJSONArray("effectDefinitionIds").orEmptyJson().toString(),
                sourceName = provenance.optString("sourceName", "Unknown"),
                sourceUrl = provenance.optString("sourceUrl", SOURCE_REPOSITORY),
                sourceRecordId = provenance.optString("sourceRecordId", cardId),
                language = provenance.optString("language", "en"),
                verifiedAt = provenance.stringOrNull("verifiedAt"),
                releaseClass = provenance.optString("releaseClass", "UNVERIFIED")
            )

            val printings = card.optJSONArray("printings") ?: JSONArray()
            for (printingIndex in 0 until printings.length()) {
                val printing = printings.getJSONObject(printingIndex)
                printingRows += CardPrintingEntity(
                    cardId = cardId,
                    setCode = printing.optString("setCode", "UNKNOWN"),
                    setName = printing.stringOrNull("setName"),
                    collectorNumber = printing.optString("collectorNumber", printingIndex.toString()),
                    rarity = printing.stringOrNull("rarity"),
                    illustrator = printing.stringOrNull("illustrator"),
                    releaseClass = printing.optString("releaseClass", "UNVERIFIED"),
                    imageAssetKey = printing.stringOrNull("imageAssetKey"),
                    sourceUrl = printing.stringOrNull("sourceUrl"),
                    sourceRecordId = printing.optString("sourceRecordId", "$cardId:$printingIndex")
                )
            }
        }

        database.withTransaction {
            // @Upsert updates rows in place, preserving foreign-keyed collection records.
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

    private fun JSONArray?.orEmptyJson(): JSONArray = this ?: JSONArray()

    private fun JSONObject.intOrNull(key: String): Int? =
        if (!has(key) || isNull(key)) null else optInt(key)

    private fun JSONObject.stringOrNull(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() && it != "null" }
}

data class ImportResult(val cardCount: Int, val printingCount: Int, val imported: Boolean)
