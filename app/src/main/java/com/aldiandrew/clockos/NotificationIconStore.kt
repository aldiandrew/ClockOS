package com.aldiandrew.clockos

import android.service.notification.StatusBarNotification

data class ClockNotificationEntry(
    val key: String,
    val notification: StatusBarNotification,
    val rank: Int
)

object NotificationIconStore {

    private val lock = Any()
    private val entries = LinkedHashMap<String, ClockNotificationEntry>()
    private val listeners = LinkedHashSet<() -> Unit>()

    fun register(listener: () -> Unit) {
        synchronized(lock) {
            listeners += listener
        }
        listener()
    }

    fun unregister(listener: () -> Unit) {
        synchronized(lock) {
            listeners -= listener
        }
    }

    fun snapshot(): List<ClockNotificationEntry> =
        synchronized(lock) {
            entries.values
                .sortedWith(
                    compareBy<ClockNotificationEntry> { it.rank }
                        .thenBy { it.notification.postTime }
                        .thenBy { it.key }
                )
        }

    fun replaceAll(
        notifications: List<ClockNotificationEntry>
    ) {
        val callbacks: List<() -> Unit>
        synchronized(lock) {
            entries.clear()
            notifications.forEach { entry ->
                entries[entry.key] = entry
            }
            callbacks = listeners.toList()
        }
        callbacks.forEach { it() }
    }

    fun upsert(
        entry: ClockNotificationEntry
    ) {
        val callbacks: List<() -> Unit>
        synchronized(lock) {
            entries[entry.key] = entry
            callbacks = listeners.toList()
        }
        callbacks.forEach { it() }
    }

    fun remove(key: String) {
        val changed: Boolean
        val callbacks: List<() -> Unit>
        synchronized(lock) {
            changed = entries.remove(key) != null
            callbacks = if (changed) {
                listeners.toList()
            } else {
                emptyList()
            }
        }
        callbacks.forEach { it() }
    }

    fun updateRanks(
        rankProvider: (String) -> Int
    ) {
        val callbacks: List<() -> Unit>
        synchronized(lock) {
            val updated = entries.values.map { entry ->
                entry.copy(
                    rank = rankProvider(entry.key)
                )
            }
            entries.clear()
            updated.forEach { entry ->
                entries[entry.key] = entry
            }
            callbacks = listeners.toList()
        }
        callbacks.forEach { it() }
    }

    fun clear() {
        val callbacks: List<() -> Unit>
        synchronized(lock) {
            if (entries.isEmpty()) return
            entries.clear()
            callbacks = listeners.toList()
        }
        callbacks.forEach { it() }
    }
}
