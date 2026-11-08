package com.duelmasters.game.engine

/**
 * Conservative rules-text adapter. The catalogue retains every entry, but a
 * card is playable only when every non-empty rules clause is implemented here.
 */
object CardTextParser {
    private val supportedKeywords = setOf(
        "BLOCKER", "SLAYER", "SPEED_ATTACKER", "DOUBLE_BREAKER",
        "TRIPLE_BREAKER", "SHIELD_TRIGGER", "UNBLOCKABLE"
    )

    fun keywords(rulesText: String, catalogMechanics: Set<String> = emptySet()): Set<String> {
        val text = normalize(rulesText)
        return buildSet {
            addAll(catalogMechanics.map(::normalizeKeyword))
            val keywordPatterns = mapOf(
                "BLOCKER" to "\\bblocker\\b",
                "SLAYER" to "\\bslayer\\b",
                "SPEED_ATTACKER" to "\\bspeed attacker\\b",
                "DOUBLE_BREAKER" to "\\bdouble breaker\\b",
                "TRIPLE_BREAKER" to "\\btriple breaker\\b",
                "SHIELD_TRIGGER" to "\\bshield trigger\\b",
                "UNBLOCKABLE" to "\\b(unblockable|can't be blocked|cannot be blocked)\\b"
            )
            keywordPatterns.forEach { (key, pattern) ->
                if (Regex(pattern).containsMatchIn(text)) add(key)
            }
            if (powerAttackBonus(text) >= 2000) add("POWER_ATTACKER_2000")
        }
    }

    fun effects(rulesText: String): List<CardEffect> {
        val text = normalize(rulesText)
        val result = mutableListOf<CardEffect>()

        // Optional "up to N" draws require a player choice and are intentionally
        // not coerced to a fixed draw count.
        val draw = Regex("draw (\\d+) cards?").find(text)?.groupValues?.get(1)?.toIntOrNull()
            ?: if (Regex("draw (?:a|one) card").containsMatchIn(text)) 1 else 0
        if (draw > 0) result += CardEffect(EffectKind.DRAW_CARDS, draw)

        when {
            Regex("destroy (?:the )?(?:opponent's|your opponent's) weakest creature").containsMatchIn(text) ->
                result += CardEffect(EffectKind.DESTROY_WEAKEST_OPPONENT)
            Regex("destroy (?:one of your opponent's|an opponent's) creatures?").containsMatchIn(text) ->
                result += CardEffect(EffectKind.DESTROY_TARGET_OPPONENT)
            Regex("return (?:the )?(?:opponent's|your opponent's) weakest creature to (?:its|their) hand").containsMatchIn(text) ->
                result += CardEffect(EffectKind.RETURN_WEAKEST_OPPONENT)
            Regex("return (?:one of your opponent's|an opponent's) creatures? to (?:its|their) hand").containsMatchIn(text) ->
                result += CardEffect(EffectKind.RETURN_TARGET_OPPONENT)
            Regex("tap (?:one of your opponent's|an opponent's) creatures?").containsMatchIn(text) ->
                result += CardEffect(EffectKind.TAP_TARGET_OPPONENT)
        }

        if (Regex("put the top card of your deck into your mana(?: zone)?").containsMatchIn(text) ||
            Regex("add the top card of your deck to your mana").containsMatchIn(text)) {
            result += CardEffect(EffectKind.ADD_TOP_CARD_TO_MANA)
        }
        if (Regex("put the top card of your deck into your hand").containsMatchIn(text) ||
            Regex("add the top card of your deck to your hand").containsMatchIn(text)) {
            result += CardEffect(EffectKind.ADD_TOP_CARD_TO_HAND)
        }
        if (Regex("put the top card of your deck into your shield zone").containsMatchIn(text) ||
            Regex("add the top card of your deck to your shields").containsMatchIn(text)) {
            result += CardEffect(EffectKind.ADD_TOP_CARD_TO_SHIELD)
        }
        if (Regex("untap all of your creatures").containsMatchIn(text)) {
            result += CardEffect(EffectKind.UNTAP_FRIENDLY_CREATURES)
        }

        val boost = Regex(
            "(?:all of )?your creatures? (?:get|gets) \\+([0-9,]+) power until the end of the turn"
        ).find(text)?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull()
        if (boost != null) result += CardEffect(EffectKind.BOOST_FRIENDLY_CREATURES, boost)

        val threshold = Regex(
            "destroy all (?:of )?your opponent's creatures? with (?:a )?power of? ([0-9,]+) or less"
        ).find(text)?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull()
        if (threshold != null) {
            result += CardEffect(EffectKind.DESTROY_OPPONENT_CREATURES_POWER_OR_LESS, threshold)
        }
        return result.distinct()
    }

    fun powerAttackBonus(rulesText: String): Int =
        Regex("(?:power attacker|gets?)\\s*\\+\\s*([0-9,]+)(?:\\s*power)?")
            .find(normalize(rulesText))?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull() ?: 0

    fun evolutionRace(rulesText: String): String? {
        val match = Regex(
            "evolution\\s*[-:—]?\\s*(?:put (?:this creature )?on )?(?:one of your|a) ([a-z][a-z -]*?) creatures?"
        ).find(normalize(rulesText)) ?: return null
        return match.groupValues[1].trim().replace(Regex("\\s+"), " ")
            .split(' ')
            .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
            .takeIf(String::isNotBlank)
    }

    fun supportStatus(
        cardType: String,
        rulesText: String,
        catalogMechanics: Set<String> = emptySet()
    ): String {
        if (cardType !in setOf("CREATURE", "SPELL", "EVOLUTION_CREATURE")) return "METADATA_ONLY"
        if (cardType == "EVOLUTION_CREATURE" && evolutionRace(rulesText) == null) return "METADATA_ONLY"
        val text = normalize(rulesText)
        if (text.isBlank()) return "SUPPORTED"
        val clauses = text.split(Regex("[.;\\n]+"))
            .map { it.trim().trim(',', ':', '—', '-') }
            .filter(String::isNotBlank)
        return if (clauses.all { isSupportedClause(it, catalogMechanics) }) "SUPPORTED" else "METADATA_ONLY"
    }

    private fun isSupportedClause(clause: String, mechanics: Set<String>): Boolean {
        val keyword = Regex(
            "(?i)(?:(?:this creature|it) )?(?:has |is a |gets? )?" +
                "(?:blocker|slayer|speed attacker|double breaker|triple breaker|shield trigger|" +
                "unblockable|power attacker(?: \\+? ?[0-9,]+)?)"
        )
        if (keyword.matches(clause)) return true
        if (mechanics.any { normalizeKeyword(it) in supportedKeywords } && keyword.matches(clause)) return true

        val supportedClauses = listOf(
            Regex("draw \\d+ cards?"),
            Regex("draw (?:a|one) card"),
            Regex("destroy (?:the )?(?:opponent's|your opponent's) weakest creature"),
            Regex("destroy (?:one of your opponent's|an opponent's) creatures?"),
            Regex("return (?:the )?(?:opponent's|your opponent's) weakest creature to (?:its|their) hand"),
            Regex("return (?:one of your opponent's|an opponent's) creatures? to (?:its|their) hand"),
            Regex("tap (?:one of your opponent's|an opponent's) creatures?"),
            Regex("put the top card of your deck into your mana(?: zone)?"),
            Regex("add the top card of your deck to your mana"),
            Regex("put the top card of your deck into your hand"),
            Regex("add the top card of your deck to your hand"),
            Regex("put the top card of your deck into your shield zone"),
            Regex("add the top card of your deck to your shields"),
            Regex("untap all of your creatures"),
            Regex("(?:all of )?your creatures? (?:get|gets) \\+[0-9,]+ power until the end of the turn"),
            Regex("destroy all (?:of )?your opponent's creatures? with (?:a )?power of? [0-9,]+ or less"),
            Regex("evolution\\s*[-:—]?\\s*(?:put (?:this creature )?on )?(?:one of your|a) [a-z][a-z -]*? creatures?")
        )
        return supportedClauses.any { it.matches(clause) }
    }

    private fun normalize(value: String): String = value.lowercase()
        .replace('’', '\'')
        .replace('–', '-')
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun normalizeKeyword(value: String): String =
        value.uppercase().replace(Regex("[^A-Z0-9]+"), "_").trim('_')
}
