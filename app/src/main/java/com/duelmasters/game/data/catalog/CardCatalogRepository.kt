package com.duelmasters.game.data.catalog

import com.duelmasters.game.data.local.CardDao
import com.duelmasters.game.data.local.CardEntity
import kotlinx.coroutines.flow.Flow

class CardCatalogRepository(private val dao: CardDao) {
    fun search(
        query: String = "",
        type: String = "",
        civilization: String = "",
        maxCost: Int? = null,
        limit: Int = 50,
        offset: Int = 0
    ): Flow<List<CardEntity>> = dao.search(
        query = query.trim(),
        type = type,
        civilization = civilization,
        maxCost = maxCost,
        limit = limit.coerceIn(1, 200),
        offset = offset.coerceAtLeast(0)
    )

    suspend fun getCard(cardId: String): CardEntity? = dao.getById(cardId)
    suspend fun getPrintings(cardId: String) = dao.getPrintings(cardId)
    suspend fun count(): Int = dao.countCards()
}
