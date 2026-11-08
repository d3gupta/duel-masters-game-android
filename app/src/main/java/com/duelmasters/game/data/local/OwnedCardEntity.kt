package com.duelmasters.game.data.local

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "owned_cards",
    primaryKeys = ["cardId", "printingSetCode", "printingCollectorNumber"],
    foreignKeys = [ForeignKey(
        entity = CardEntity::class,
        parentColumns = ["cardId"],
        childColumns = ["cardId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class OwnedCardEntity(
    val cardId: String,
    val printingSetCode: String,
    val printingCollectorNumber: String,
    val quantity: Int = 0
)
