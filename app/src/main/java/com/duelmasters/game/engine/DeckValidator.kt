package com.duelmasters.game.engine

data class DeckValidation(
    val isValid: Boolean,
    val errors: List<String>,
    val cardCount: Int,
    val distinctCardCount: Int
)

object DeckValidator {
    const val REQUIRED_SIZE = 40
    const val MAX_COPIES_PER_CARD = 4

    fun validate(cards: List<GameCard>): DeckValidation {
        val errors = mutableListOf<String>()
        if (cards.size != REQUIRED_SIZE) errors += "A deck must contain exactly $REQUIRED_SIZE cards (currently ${cards.size})."
        cards.groupingBy { it.id }.eachCount().filterValues { it > MAX_COPIES_PER_CARD }.forEach { (id, count) ->
            errors += "Card $id appears $count times; the limit is $MAX_COPIES_PER_CARD."
        }
        return DeckValidation(errors.isEmpty(), errors, cards.size, cards.map { it.id }.distinct().size)
    }
}
