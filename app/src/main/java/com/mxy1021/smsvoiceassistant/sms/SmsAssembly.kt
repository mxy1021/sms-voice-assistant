package com.mxy1021.smsvoiceassistant.sms

import java.security.MessageDigest

/** Transient input only. The framework delivers UDH-reassembled parts in sequence order. */
data class SmsPart(val sender: String?, val timestamp: Long, val body: String?)

object SmsAssembly {
    fun assemble(parts: List<SmsPart>): String? {
        if (parts.isEmpty() || parts.size > 16 || parts.any { it.sender.isNullOrBlank() || it.body == null }) return null
        if (parts.map { it.sender }.distinct().size != 1 || parts.map { it.timestamp }.distinct().size != 1) return null
        if (parts.sumOf { it.body!!.length.toLong() } > SpeechFormatter.MAX_INPUT_CHARS) return null
        return parts.joinToString("") { it.body!! }.takeUnless { it.isBlank() }
    }
}

/** Bounded, process-local replay protection. Nothing is persisted, logged, or uploaded. */
class SmsReplayGuard {
    private val salt = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
    private val seen = LinkedHashMap<String, Long>()
    @Synchronized fun accept(parts: List<SmsPart>, now: Long): Boolean {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        parts.forEach { part -> listOf(part.sender.orEmpty(), part.timestamp.toString(), part.body.orEmpty()).forEach {
            val bytes = it.toByteArray(Charsets.UTF_8)
            digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.size).array()); digest.update(bytes)
        } }
        val key = java.util.Base64.getEncoder().encodeToString(digest.digest())
        seen.entries.removeAll { now < it.value || now - it.value > SmsSafetyWindow.MAX_PDU_AGE_MS }
        if (seen.containsKey(key)) return false
        seen[key] = now
        while (seen.size > 32) seen.remove(seen.keys.first())
        return true
    }
}
