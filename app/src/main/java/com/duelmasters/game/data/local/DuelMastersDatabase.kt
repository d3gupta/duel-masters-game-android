package com.duelmasters.game.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [DeckEntity::class],
    version = 1,
    exportSchema = false
)
abstract class DuelMastersDatabase : RoomDatabase() {
    abstract fun deckDao(): DeckDao
}
