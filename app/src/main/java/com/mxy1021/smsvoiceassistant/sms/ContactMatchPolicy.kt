package com.mxy1021.smsvoiceassistant.sms

/** Conservative exact matching, never fuzzy suffix matching. Unsupported senders read body only. */
object ContactMatchPolicy {
    fun canonical(number: String?): String? {
        if (number == null || !Regex("[+0-9 ()-]+").matches(number)) return null
        val value = number.replace(Regex("[ ()-]"), "")
        val domestic = value.removePrefix("+86").removePrefix("0086")
        if (Regex("1[3-9][0-9]{9}").matches(domestic)) return "+86$domestic"
        return value.takeIf { Regex("\\+[1-9][0-9]{9,14}").matches(it) && !it.startsWith("+86") }
    }
    fun safeName(name: String?): String? = name?.trim()?.takeIf {
        it.length in 1..30 && it.any { ch -> ch.isLetter() } &&
            it.count { ch -> ch.isDigit() } < 7 &&
            it.none { ch -> ch.isISOControl() } &&
            !Regex("[0-9][0-9 ()+-]{5,}[0-9]|https?://|www\\.", RegexOption.IGNORE_CASE).containsMatchIn(it)
    }
    fun match(sender: String?, candidates: List<Pair<String?, String?>>): String? {
        val exact = canonical(sender) ?: return null
        val names = candidates.filter { canonical(it.first) == exact }.map { safeName(it.second) }
        // A nameless or conflicting exact match is ambiguous: never guess a contact.
        return if (names.isNotEmpty() && names.none { it == null } && names.distinct().size == 1) names.first() else null
    }
}

/** Re-check access after the provider returns; a revoked permission never yields a name. */
class ContactLookup(private val permitted: () -> Boolean,
    private val query: (String) -> List<Pair<String?, String?>>) {
    fun resolve(sender: String?): String? {
        if (!permitted() || ContactMatchPolicy.canonical(sender) == null) return null
        return try {
            val rows = query(checkNotNull(sender))
            if (permitted()) ContactMatchPolicy.match(sender, rows) else null
        } catch (_: Exception) { null }
    }
}
