package com.duelmasters.game.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "decks")
data class DeckEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val cardIdsJson: String,
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)
