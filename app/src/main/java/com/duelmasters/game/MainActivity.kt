package com.duelmasters.game

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.duelmasters.game.ui.DuelMastersApp
import com.duelmasters.game.ui.theme.DuelMastersTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DuelMastersTheme {
                DuelMastersApp()
            }
        }
    }
}
