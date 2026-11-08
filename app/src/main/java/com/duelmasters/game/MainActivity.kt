package com.duelmasters.game

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.duelmasters.game.data.catalog.CardCatalogImporter
import com.duelmasters.game.data.local.DuelMastersDatabaseProvider
import com.duelmasters.game.ui.DuelMastersApp
import com.duelmasters.game.ui.theme.DuelMastersTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val database = DuelMastersDatabaseProvider.get(applicationContext)
                    val result = CardCatalogImporter(applicationContext, database).importIfNeeded()
                    Log.i("DuelMasters", "Card catalogue: ${result.cardCount} definitions, ${result.printingCount} printings; imported=${result.imported}")
                }
            } catch (error: Exception) {
                Log.e("DuelMasters", "Unable to import bundled card catalogue", error)
            }
        }

        setContent {
            DuelMastersTheme {
                DuelMastersApp()
            }
        }
    }
}
