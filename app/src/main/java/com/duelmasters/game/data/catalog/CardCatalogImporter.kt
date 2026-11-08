package com.duelmasters.game.data.catalog

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Base64
import androidx.room.withTransaction
import com.duelmasters.game.engine.CardTextParser
import com.duelmasters.game.data.local.CardCatalogMetadataEntity
import com.duelmasters.game.data.local.CardEntity
import com.duelmasters.game.data.local.CardPrintingEntity
import com.duelmasters.game.data.local.DuelMastersDatabase
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream

private const val CATALOG_VERSION = 5
private const val SOURCE_REPOSITORY = "https://github.com/bmenneni/SimpleJavaDmdb"

/** Imports the bundled English SQLite catalogue into Room. All work is offline. */
class CardCatalogImporter(
    private val context: Context,
    private val database: DuelMastersDatabase
) {
    suspend fun importIfNeeded(force: Boolean = false): ImportResult {
        val existing = database.cardDao().getMetadata()
        if (!force && existing?.catalogVersion == CATALOG_VERSION) {
            return ImportResult(existing.cardCount, existing.printingCount, imported = false)
        }

        val sourceFile = unpackSourceDatabase()
        val importedCards = readSourceCards(sourceFile)
        require(importedCards.isNotEmpty()) { "The bundled card database contains no importable cards." }

        val cardRows = importedCards.map { card ->
            CardEntity(
                cardId = card.cardId,
                name = card.name,
                type = card.type,
                civilizationsJson = JSONArray(card.civilizations).toString(),
                cost = card.cost,
                power = card.power,
                racesJson = JSONArray(card.races).toString(),
                rulesText = card.rulesText,
                flavorText = null,
                mechanicsJson = JSONArray(card.mechanics).toString(),
                implementationStatus = CardTextParser.supportStatus(card.type, card.rulesText, card.mechanics.toSet()),
                effectDefinitionIdsJson = "[]",
                sourceName = "SimpleJavaDmdb",
                sourceUrl = SOURCE_REPOSITORY,
                sourceRecordId = card.sourceRecordId,
                language = "en",
                verifiedAt = null,
                releaseClass = card.releaseClass
            )
        }
        val printingRows = importedCards.flatMap { it.printings }
        require(printingRows.isNotEmpty()) { "The bundled card database contains no printings." }

        database.withTransaction {
            // @Upsert updates rows in place, preserving decks and owned-card references.
            cardRows.chunked(250).forEach { database.cardDao().upsertCards(it) }
            database.cardDao().clearPrintings()
            printingRows.chunked(400).forEach { database.cardDao().upsertPrintings(it) }
            database.cardDao().saveMetadata(
                CardCatalogMetadataEntity(
                    catalogVersion = CATALOG_VERSION,
                    importedAtEpochMs = System.currentTimeMillis(),
                    cardCount = cardRows.size,
                    printingCount = printingRows.size
                )
            )
        }
        return ImportResult(cardRows.size, printingRows.size, imported = true)
    }

    private fun unpackSourceDatabase(): File {
        val destination = File(context.filesDir, "duelmasters-source.db")

        val encoded = context.assets.open("cards/duelmasters-source.db.base64")
            .bufferedReader()
            .use { it.readText() }
        val bytes = Base64.decode(encoded, Base64.DEFAULT)
        FileOutputStream(destination).use { it.write(bytes) }
        return destination
    }

    private fun readSourceCards(sourceFile: File): List<ImportedCard> {
        val sourceDb = SQLiteDatabase.openDatabase(
            sourceFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY
        )
        val grouped = LinkedHashMap<String, ImportedCard>()
        try {
            val sql = """
                SELECT
                    Card.card_id AS card_id,
                    Card.card_name AS card_name,
                    Card.cost AS cost,
                    Card.civilization AS civilization,
                    Card.card_type AS card_type,
                    Card.race AS race,
                    Card.power AS power,
                    Card.rarity AS rarity,
                    Card.coll_num AS coll_num,
                    Card.card_set AS card_set,
                    Card.keywords AS keywords,
                    Card.categories AS categories,
                    RulesText.card_text AS card_text
                FROM Card
                LEFT JOIN RulesText USING (card_id)
                ORDER BY Card.card_id
            """.trimIndent()
            sourceDb.rawQuery(sql, null).use { cursor ->
                while (cursor.moveToNext()) {
                    val sourceId = cursor.string("card_id") ?: continue
                    val name = cursor.string("card_name")?.trim().orEmpty()
                    if (name.isBlank()) continue
                    val civilizationRaw = cursor.string("civilization").orEmpty()
                    val typeRaw = cursor.string("card_type").orEmpty()
                    val raceRaw = cursor.string("race").orEmpty()
                    val power = cursor.string("power")
                    val cost = cursor.intOrNull("cost")
                    val rulesText = cursor.string("card_text").orEmpty()
                    val setRaw = cursor.string("card_set").orEmpty()
                    val setCode = normalizeSetCode(setRaw)
                    val releaseClass = releaseClassForSet(setCode, sourceId.toIntOrNull())
                    val cardType = normalizeCardType(typeRaw)
                    val civilizations = civilizationRaw.split('/')
                        .mapNotNull { normalizeCivilization(it) }
                    val races = raceRaw.split('/')
                        .map { it.trim().replace('_', ' ') }
                        .filter { it.isNotBlank() }
                    val mechanics = (
                        cursor.string("keywords").orEmpty().split('/') +
                            cursor.string("categories").orEmpty().split('/')
                        )
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .distinct()

                    // Identical rules/identity rows are printings of the same card definition.
                    val identity = listOf(
                        name, cardType, civilizationRaw, cost?.toString().orEmpty(),
                        power.orEmpty(), raceRaw, rulesText
                    ).joinToString("\u001f")
                    val card = grouped.getOrPut(identity) {
                        ImportedCard(
                            cardId = "dmdb-$sourceId",
                            name = name,
                            type = cardType,
                            civilizations = civilizations,
                            cost = cost,
                            power = power,
                            races = races,
                            rulesText = rulesText,
                            mechanics = mechanics,
                            releaseClass = releaseClass,
                            sourceRecordId = sourceId,
                            printings = mutableListOf()
                        )
                    }
                    card.printings += CardPrintingEntity(
                        cardId = card.cardId,
                        setCode = setCode,
                        setName = setRaw.ifBlank { null },
                        collectorNumber = cursor.string("coll_num") ?: sourceId,
                        rarity = normalizeRarity(cursor.string("rarity")),
                        illustrator = null,
                        releaseClass = releaseClass,
                        imageAssetKey = null,
                        sourceUrl = SOURCE_REPOSITORY,
                        sourceRecordId = sourceId
                    )
                }
            }
        } finally {
            sourceDb.close()
        }
        return grouped.values.toList()
    }

    private fun Cursor.string(column: String): String? =
        getColumnIndex(column).takeIf { it >= 0 }?.let { if (isNull(it)) null else getString(it) }

    private fun Cursor.intOrNull(column: String): Int? =
        getColumnIndex(column).takeIf { it >= 0 }?.let { if (isNull(it)) null else getInt(it) }

    private fun normalizeSetCode(raw: String): String {
        val match = Regex("(?i)dm[-_ ]?(\\d+)").find(raw)
        if (match != null) return "DM-${match.groupValues[1].padStart(2, '0')}"
        if (raw.contains("promo", ignoreCase = true) || raw.contains("promotional", ignoreCase = true)) {
            return "PROMO"
        }
        return raw.trim().uppercase().ifBlank { "UNKNOWN" }
    }

    private fun releaseClassForSet(setCode: String, sourceId: Int?): String {
        if (setCode == "PROMO" || sourceId != null && sourceId in 9001..9080) return "PROMOTIONAL"
        val number = Regex("DM-(\\d+)").find(setCode)?.groupValues?.get(1)?.toIntOrNull()
        return when {
            number == null -> "UNVERIFIED"
            number <= 12 -> "OFFICIAL_ENGLISH"
            number <= 35 -> "COMMUNITY_ENGLISH_VERSION"
            else -> "UNVERIFIED"
        }
    }

    private fun normalizeCivilization(value: String): String? = when (value.trim().lowercase()) {
        "light" -> "LIGHT"
        "water" -> "WATER"
        "darkness" -> "DARKNESS"
        "fire" -> "FIRE"
        "nature" -> "NATURE"
        else -> null
    }

    private fun normalizeCardType(value: String): String {
        val normalized = value.lowercase()
        return when {
            "evolution" in normalized -> "EVOLUTION_CREATURE"
            "creature" in normalized -> "CREATURE"
            "spell" in normalized -> "SPELL"
            "cross" in normalized -> "CROSS_GEAR"
            "castle" in normalized -> "CASTLE"
            else -> "OTHER"
        }
    }

    private fun normalizeRarity(value: String?): String? = when (value?.lowercase()) {
        "c" -> "Common"
        "u" -> "Uncommon"
        "r" -> "Rare"
        "vr" -> "Very Rare"
        "sr" -> "Super Rare"
        "nr" -> "No Rarity"
        null, "" -> null
        else -> value
    }

    private data class ImportedCard(
        val cardId: String,
        val name: String,
        val type: String,
        val civilizations: List<String>,
        val cost: Int?,
        val power: String?,
        val races: List<String>,
        val rulesText: String,
        val mechanics: List<String>,
        val releaseClass: String,
        val sourceRecordId: String,
        val printings: MutableList<CardPrintingEntity>
    )
}

data class ImportResult(val cardCount: Int, val printingCount: Int, val imported: Boolean)
