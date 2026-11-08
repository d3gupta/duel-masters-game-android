package com.duelmasters.game.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "card_catalog_metadata")
data class CardCatalogMetadataEntity(
    @PrimaryKey val id: Int = 1,
    val catalogVersion: Int,
    val importedAtEpochMs: Long,
    val cardCount: Int,
    val printingCount: Int
)
