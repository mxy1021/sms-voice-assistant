package com.mxy1021.smsvoiceassistant.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.CancellationSignal
import android.os.Handler
import android.provider.ContactsContract.PhoneLookup
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** One bounded lookup for this event, never a directory scan, cache, or persisted result. */
class ContactNameResolver(private val context: Context, private val main: Handler) {
    companion object { private val querying = AtomicBoolean(false) }
    fun resolve(sender: String?, done: (String?) -> Unit) {
        fun granted() = context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        if (!granted() || ContactMatchPolicy.canonical(sender) == null) { done(null); return }
        // A slow/cancelled provider must not cause an accumulating set of Binder workers.
        if (!querying.compareAndSet(false, true)) { done(null); return }
        val cancellation = CancellationSignal()
        val worker = Executors.newSingleThreadExecutor()
        var ended = false // main looper only
        fun finish(name: String?, cancel: Boolean = false) {
            if (ended) return
            ended = true
            // CancellationSignal.cancel may call Binder; do not block the main looper.
            if (cancel) Thread { runCatching { cancellation.cancel() } }.start()
            worker.shutdownNow()
            done(if (granted()) name else null)
        }
        val timeout = Runnable { finish(null, cancel = true) }
        main.postDelayed(timeout, 250)
        worker.execute {
            val name = try { ContactLookup(::granted) { number ->
                val uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
                context.contentResolver.query(uri, arrayOf(PhoneLookup.NUMBER, PhoneLookup.DISPLAY_NAME),
                    null, null, null, cancellation)?.use { cursor ->
                    val rows = mutableListOf<Pair<String?, String?>>()
                    // Ambiguous or excessive results are rejected without enumerating a directory.
                    while (cursor.moveToNext()) {
                        if (rows.size >= 8 || !granted() || cancellation.isCanceled) return@use emptyList()
                        rows.add(cursor.getString(0) to cursor.getString(1))
                    }
                    rows
                } ?: emptyList()
            }.resolve(sender) } finally { querying.set(false) }
            main.post { main.removeCallbacks(timeout); finish(name) }
        }
    }
}
