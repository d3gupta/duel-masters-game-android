package com.duelmasters.game.engine

object CardTextParser {
    fun keywords(rulesText: String, catalogMechanics: Set<String> = emptySet()): Set<String> {
        val text = rulesText.lowercase()
        return buildSet {
            addAll(catalogMechanics.map { it.uppercase().replace(' ', '_').replace('-', '_') })
            if (text.contains("blocker")) add("BLOCKER")
            if (text.contains("slayer")) add("SLAYER")
            if (text.contains("speed attacker")) add("SPEED_ATTACKER")
            if (text.contains("double breaker")) add("DOUBLE_BREAKER")
            if (text.contains("triple breaker")) add("TRIPLE_BREAKER")
            if (text.contains("shield trigger")) add("SHIELD_TRIGGER")
            if (text.contains("unblockable") || text.contains("can't be blocked") || text.contains("cannot be blocked")) add("UNBLOCKABLE")
            if (Regex("power attacker\\s*\\+?\\s*2,?000").containsMatchIn(text)) add("POWER_ATTACKER_2000")
        }
    }

    fun effects(rulesText: String): List<CardEffect> {
        val text = rulesText.lowercase()
        val effects = mutableListOf<CardEffect>()
        val drawCount = Regex("draw (?:up to )?(\\d+) cards?").find(text)?.groupValues?.get(1)?.toIntOrNull()
            ?: if (text.contains("draw a card")) 1 else 0
        if (drawCount > 0) effects += CardEffect(EffectKind.DRAW_CARDS, drawCount)
        if (text.contains("destroy target creature") || text.contains("destroy one of your opponent's creatures") ||
            text.contains("destroy an opponent's creature")) {
            effects += CardEffect(EffectKind.DESTROY_WEAKEST_OPPONENT)
        }
        if (text.contains("return target creature") && text.contains("hand") ||
            text.contains("return one of your opponent's creatures to their hand")) {
            effects += CardEffect(EffectKind.RETURN_WEAKEST_OPPONENT)
        }
        if (text.contains("put the top card of your deck into your mana zone") ||
            text.contains("put the top card of your deck into your mana")) {
            effects += CardEffect(EffectKind.ADD_TOP_CARD_TO_MANA)
        }
        val boost = Regex("your creatures? (?:get|gets) \\+([0-9,]+) power").find(text)
            ?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull()
        if (boost != null) effects += CardEffect(EffectKind.BOOST_FRIENDLY_CREATURES, boost)
        return effects
    }
}
