package com.duelmasters.game.data.local

import android.content.Context
import androidx.room.Room

object DuelMastersDatabaseProvider {
    @Volatile private var instance: DuelMastersDatabase? = null

    fun get(context: Context): DuelMastersDatabase =
        instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                DuelMastersDatabase::class.java,
                "duel_masters.db"
            )
                .addMigrations(DuelMastersDatabase.MIGRATION_1_2)
                .build()
                .also { instance = it }
        }
}
