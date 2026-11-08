package com.duelmasters.game.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cards")
data class CardEntity(
    @PrimaryKey val cardId: String,
    val name: String,
    val type: String,
    val civilizationsJson: String,
    val cost: Int?,
    val power: String?,
    val racesJson: String,
    val rulesText: String,
    val flavorText: String?,
    val mechanicsJson: String,
    val implementationStatus: String,
    val effectDefinitionIdsJson: String,
    val sourceName: String,
    val sourceUrl: String,
    val releaseClass: String
)
