package com.duelmasters.game.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {
    @Query("""
        SELECT * FROM cards
        WHERE (:query = '' OR name LIKE '%' || :query || '%' COLLATE NOCASE
            OR rulesText LIKE '%' || :query || '%' COLLATE NOCASE)
          AND (:type = '' OR type = :type)
          AND (:civilization = '' OR civilizationsJson LIKE '%' || :civilization || '%')
          AND (:maxCost IS NULL OR cost <= :maxCost)
          AND (:race = '' OR racesJson LIKE '%' || :race || '%')
          AND (:setCode = '' OR EXISTS (
              SELECT 1 FROM card_printings p
              WHERE p.cardId = cards.cardId AND p.setCode = :setCode
          ))
          AND (:rarity = '' OR EXISTS (
              SELECT 1 FROM card_printings p
              WHERE p.cardId = cards.cardId AND p.rarity = :rarity
          ))
        ORDER BY name COLLATE NOCASE
        LIMIT :limit OFFSET :offset
    """)
    fun search(
        query: String,
        type: String,
        civilization: String,
        maxCost: Int?,
        race: String,
        setCode: String,
        rarity: String,
        limit: Int,
        offset: Int
    ): Flow<List<CardEntity>>

    @Query("SELECT * FROM cards WHERE cardId = :cardId")
    suspend fun getById(cardId: String): CardEntity?

    @Query("SELECT * FROM card_printings WHERE cardId = :cardId ORDER BY setCode, collectorNumber")
    suspend fun getPrintings(cardId: String): List<CardPrintingEntity>

    @Query("SELECT COUNT(*) FROM cards")
    suspend fun countCards(): Int

    @Query("SELECT COUNT(*) FROM cards WHERE implementationStatus = 'SUPPORTED' AND type IN ('CREATURE', 'SPELL', 'EVOLUTION_CREATURE')")
    suspend fun countSupportedCards(): Int

    @Query("SELECT COUNT(*) FROM card_printings")
    suspend fun countPrintings(): Int

    @Query("SELECT * FROM card_catalog_metadata WHERE id = 1")
    suspend fun getMetadata(): CardCatalogMetadataEntity?

    @Upsert
    suspend fun upsertCards(cards: List<CardEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPrintings(printings: List<CardPrintingEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveMetadata(metadata: CardCatalogMetadataEntity)

    @Query("DELETE FROM card_printings")
    suspend fun clearPrintings()

}
