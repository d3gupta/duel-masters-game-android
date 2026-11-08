package com.duelmasters.game

import android.Manifest
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duelmasters.game.data.catalog.CardCatalogImporter
import com.duelmasters.game.data.local.DuelMastersDatabaseProvider
import java.io.File
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineCatalogueImportTest {
    @Test
    fun bundledCatalogueImportsIntoRoomWithoutNetworkAccess() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val requestedPermissions = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions.orEmpty()
        assertFalse("The offline app must not request internet access.", Manifest.permission.INTERNET in requestedPermissions)
        assertFalse(
            "Local decks must not be included in Android cloud/device backup.",
            (context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP) != 0
        )

        val database = DuelMastersDatabaseProvider.get(context)
        val result = CardCatalogImporter(context, database).importIfNeeded(force = true)
        val dao = database.cardDao()

        assertTrue("The bundled catalogue should contain card definitions.", result.cardCount > 0)
        assertTrue("The bundled catalogue should contain printings.", result.printingCount > 0)
        assertTrue("At least ten supported definitions are required for the starter deck.", dao.countSupportedCards() >= 10)
        assertEquals(result.cardCount, dao.countCards())
        assertEquals(result.printingCount, dao.countPrintings())
        assertTrue("The import should be a local asset import.", result.imported)

        // Export the exact classifier result from Room; CI retrieves it as an audit artifact.
        val allCards = dao.getAllCards()
        val catalogueOnly = dao.getCatalogueOnlyCards()
        val supportedCount = dao.countSupportedCards()
        assertEquals(
            "Every imported definition must be classified exactly once.",
            result.cardCount,
            supportedCount + catalogueOnly.size
        )
        assertEquals("The full audit must cover every imported card.", result.cardCount, allCards.size)

        val metadata = requireNotNull(dao.getMetadata())
        fun auditCsv(cards: List<com.duelmasters.game.data.local.CardEntity>): String = buildString {
            appendLine("catalog_version,card_id,name,type,cost,power,implementation_status,source_record_id,mechanics,rules_text")
            cards.forEach { card ->
                val values = listOf(
                    metadata.catalogVersion.toString(),
                    card.cardId,
                    card.name,
                    card.type,
                    card.cost?.toString().orEmpty(),
                    card.power.orEmpty(),
                    card.implementationStatus,
                    card.sourceRecordId,
                    card.mechanicsJson,
                    card.rulesText
                )
                appendLine(values.joinToString(",") { csvField(it) })
            }
        }
        File(context.filesDir, "card-rules-support-report.csv")
            .writeText(auditCsv(catalogueOnly), StandardCharsets.UTF_8)
        File(context.filesDir, "card-rules-support-full-report.csv")
            .writeText(auditCsv(allCards), StandardCharsets.UTF_8)
        File(context.filesDir, "card-rules-support-summary.txt").writeText(
            "catalogVersion=${metadata.catalogVersion}\n" +
                "totalCards=${result.cardCount}\n" +
                "playableCards=${dao.countSupportedCards()}\n" +
                "catalogueOnlyCards=${catalogueOnly.size}\n" +
                "printings=${result.printingCount}\n",
            StandardCharsets.UTF_8
        )
    }
}

private fun csvField(value: String): String =
    "\"" + value.replace("\"", "\"\"") + "\""
