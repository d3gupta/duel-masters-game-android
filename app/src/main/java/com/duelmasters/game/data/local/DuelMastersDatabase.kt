package com.duelmasters.game.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        DeckEntity::class,
        CardEntity::class,
        CardPrintingEntity::class,
        OwnedCardEntity::class,
        CardCatalogMetadataEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class DuelMastersDatabase : RoomDatabase() {
    abstract fun deckDao(): DeckDao
    abstract fun cardDao(): CardDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS cards (
                        cardId TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        type TEXT NOT NULL,
                        civilizationsJson TEXT NOT NULL,
                        cost INTEGER,
                        power TEXT,
                        racesJson TEXT NOT NULL,
                        rulesText TEXT NOT NULL,
                        flavorText TEXT,
                        mechanicsJson TEXT NOT NULL,
                        implementationStatus TEXT NOT NULL,
                        effectDefinitionIdsJson TEXT NOT NULL,
                        sourceName TEXT NOT NULL,
                        sourceUrl TEXT NOT NULL,
                        releaseClass TEXT NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS card_printings (
                        cardId TEXT NOT NULL,
                        setCode TEXT NOT NULL,
                        setName TEXT,
                        collectorNumber TEXT NOT NULL,
                        rarity TEXT,
                        illustrator TEXT,
                        releaseClass TEXT NOT NULL,
                        imageAssetKey TEXT,
                        sourceUrl TEXT,
                        PRIMARY KEY(cardId, setCode, collectorNumber),
                        FOREIGN KEY(cardId) REFERENCES cards(cardId) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_card_printings_setCode ON card_printings(setCode)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_card_printings_collectorNumber ON card_printings(collectorNumber)")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS owned_cards (
                        cardId TEXT NOT NULL,
                        printingSetCode TEXT NOT NULL,
                        printingCollectorNumber TEXT NOT NULL,
                        quantity INTEGER NOT NULL,
                        PRIMARY KEY(cardId, printingSetCode, printingCollectorNumber),
                        FOREIGN KEY(cardId) REFERENCES cards(cardId) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS card_catalog_metadata (
                        id INTEGER NOT NULL PRIMARY KEY,
                        catalogVersion INTEGER NOT NULL,
                        importedAtEpochMs INTEGER NOT NULL,
                        cardCount INTEGER NOT NULL,
                        printingCount INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }
    }
}
