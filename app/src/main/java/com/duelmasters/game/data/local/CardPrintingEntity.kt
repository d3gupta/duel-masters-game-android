package com.duelmasters.game.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "card_printings",
    primaryKeys = ["cardId", "setCode", "collectorNumber"],
    foreignKeys = [ForeignKey(
        entity = CardEntity::class,
        parentColumns = ["cardId"],
        childColumns = ["cardId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("cardId"), Index("setCode"), Index("collectorNumber")]
)
data class CardPrintingEntity(
    val cardId: String,
    val setCode: String,
    val setName: String?,
    val collectorNumber: String,
    val rarity: String?,
    val illustrator: String?,
    val releaseClass: String,
    val imageAssetKey: String?,
    val sourceUrl: String?
)
