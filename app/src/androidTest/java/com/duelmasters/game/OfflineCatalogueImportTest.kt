package com.duelmasters.game

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duelmasters.game.data.catalog.CardCatalogImporter
import com.duelmasters.game.data.local.DuelMastersDatabaseProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineCatalogueImportTest {
    @Test
    fun bundledCatalogueImportsIntoRoomWithoutNetworkAccess() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = DuelMastersDatabaseProvider.get(context)
        val result = CardCatalogImporter(context, database).importIfNeeded(force = true)

        assertTrue("The bundled catalogue should contain card definitions.", result.cardCount > 0)
        assertTrue("The bundled catalogue should contain printings.", result.printingCount > 0)
        assertEquals(result.cardCount, database.cardDao().countCards())
        assertEquals(result.printingCount, database.cardDao().countPrintings())
        assertTrue("The import should be a local asset import.", result.imported)
    }
}
