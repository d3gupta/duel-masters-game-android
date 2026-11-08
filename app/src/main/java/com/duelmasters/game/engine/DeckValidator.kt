package com.duelmasters.game.engine

data class DeckValidation(
    val isValid: Boolean,
    val errors: List<String>,
    val cardCount: Int,
    val distinctCardCount: Int
)

object DeckValidator {
    const val MINIMUM_SIZE = 40
    const val MAX_COPIES_PER_CARD = 4

    fun validate(cards: List<GameCard>): DeckValidation {
        val errors = mutableListOf<String>()
        if (cards.size < MINIMUM_SIZE) errors += "A deck must contain at least $MINIMUM_SIZE cards (currently ${cards.size})."
        cards.groupingBy { it.name.trim().lowercase() }.eachCount().filterValues { it > MAX_COPIES_PER_CARD }.forEach { (name, count) ->
            errors += "$name appears $count times; the limit is $MAX_COPIES_PER_CARD copies of a card name."
        }
        return DeckValidation(errors.isEmpty(), errors, cards.size, cards.map { it.id }.distinct().size)
    }
}
