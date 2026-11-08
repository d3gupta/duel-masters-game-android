package com.duelmasters.game.engine

import com.duelmasters.game.data.local.CardEntity

/**
 * Conservative rules-text adapter. It only emits executable effects for phrases
 * whose meaning is unambiguous in the supported rules subset; all other text remains
 * available on CardEntity and is not silently guessed.
 */
object CardRulesParser {
    fun fromCatalogue(card: CardEntity): GameCard {
        val text = card.rulesText.lowercase()
        val abilities = buildSet {
            if (text.contains("blocker")) add(Ability.BLOCKER)
            if (text.contains("slayer")) add(Ability.SLAYER)
            if (text.contains("speed attacker")) add(Ability.SPEED_ATTACKER)
            if (text.contains("double breaker")) add(Ability.DOUBLE_BREAKER)
            if (text.contains("triple breaker")) add(Ability.TRIPLE_BREAKER)
            if (text.contains("unblockable") || text.contains("can't be blocked")) add(Ability.UNBLOCKABLE)
            if (Regex("power attacker \\+?2,?000").containsMatchIn(text)) add(Ability.POWER_ATTACKER_2000)
        }
        val effects = mutableListOf<CardEffect>()
        Regex("draw (\\d+) cards?").find(text)?.groupValues?.get(1)?.toIntOrNull()?.let {
            effects += CardEffect(EffectKind.DRAW, it)
        } ?: if (Regex("draw a card").containsMatchIn(text)) {
            effects += CardEffect(EffectKind.DRAW, 1)
        } else Unit
        if (text.contains("destroy target creature") || text.contains("destroy one of your opponent's creatures")) {
            effects += CardEffect(EffectKind.DESTROY_TARGET_CREATURE)
        }
        if (text.contains("return target creature to its owner's hand") ||
            text.contains("return one of your opponent's creatures to their hand")) {
            effects += CardEffect(EffectKind.RETURN_TARGET_CREATURE_TO_HAND)
        }
        if (text.contains("put a card from your hand into your mana zone") ||
            text.contains("put the top card of your deck into your mana zone")) {
            effects += CardEffect(EffectKind.ADD_MANA)
        }
        Regex("gets? \\+([0-9,]+) power").find(text)?.groupValues?.get(1)
            ?.replace(",", "")?.toIntOrNull()?.let {
                effects += CardEffect(EffectKind.POWER_UP_TARGET_CREATURE, it)
            }
        val kind = if (card.type.contains("spell", ignoreCase = true)) CardKind.SPELL else CardKind.CREATURE
        val power = card.power?.replace(",", "")?.toIntOrNull() ?: 0
        return GameCard(
            id = card.cardId,
            name = card.name,
            cost = card.cost ?: 0,
            kind = kind,
            power = power,
            abilities = abilities,
            effects = effects,
            hasShieldTrigger = text.contains("shield trigger")
        )
    }
}
