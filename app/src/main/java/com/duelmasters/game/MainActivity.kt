package com.duelmasters.game

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.duelmasters.game.data.catalog.CardCatalogImporter
import com.duelmasters.game.data.local.DuelMastersDatabaseProvider
import com.duelmasters.game.ui.DuelMastersApp
import com.duelmasters.game.ui.theme.DuelMastersTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DuelMastersTheme {
                CatalogueStartupGate()
            }
        }
    }
}

@Composable
private fun CatalogueStartupGate() {
    val context = LocalContext.current.applicationContext
    var attempt by remember { mutableIntStateOf(0) }
    var importing by remember { mutableStateOf(true) }
    var failure by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(attempt) {
        importing = true
        failure = null
        try {
            withContext(Dispatchers.IO) {
                val database = DuelMastersDatabaseProvider.get(context)
                CardCatalogImporter(context, database).importIfNeeded()
            }
            importing = false
        } catch (error: Exception) {
            failure = error.message ?: "The offline card catalogue could not be loaded."
            importing = false
        }
    }

    if (importing || failure != null) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (importing) {
                    CircularProgressIndicator()
                    Text(
                        "Preparing the offline card catalogue…",
                        modifier = Modifier.padding(top = 18.dp),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "This happens locally on first launch. No internet connection is needed.",
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Text("Catalogue setup failed", style = MaterialTheme.typography.titleLarge)
                    Text(
                        failure.orEmpty(),
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(onClick = { attempt++ }) { Text("Retry catalogue setup") }
                }
            }
        }
    } else {
        DuelMastersApp()
    }
}
