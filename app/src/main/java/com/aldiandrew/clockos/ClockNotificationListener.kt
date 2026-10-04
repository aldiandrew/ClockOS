package com.aldiandrew.clockos

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class ClockNotificationListener : NotificationListenerService() {

    private val ranking = Ranking()

    override fun onListenerConnected() {
        super.onListenerConnected()

        val active = try {
            activeNotifications
        } catch (_: Throwable) {
            null
        }

        NotificationIconStore.replaceAll(
            active.orEmpty()
                .mapNotNull { snapshot(it) }
        )
    }

    override fun onListenerDisconnected() {
        NotificationIconStore.clear()
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(
        sbn: StatusBarNotification
    ) {
        onNotificationPosted(sbn, null)
    }

    override fun onNotificationPosted(
        sbn: StatusBarNotification,
        rankingMap: RankingMap?
    ) {
        if (shouldShow(sbn)) {
            NotificationIconStore.upsert(
                snapshot(
                    sbn,
                    rankingMap
                ) ?: return
            )
        } else {
            NotificationIconStore.remove(sbn.key)
        }
    }

    override fun onNotificationRemoved(
        sbn: StatusBarNotification
    ) {
        NotificationIconStore.remove(sbn.key)
    }

    override fun onNotificationRankingUpdate(
        rankingMap: RankingMap
    ) {
        NotificationIconStore.updateRanks { key ->
            if (rankingMap.getRanking(key, ranking)) {
                ranking.rank
            } else {
                Int.MAX_VALUE
            }
        }
    }

    private fun snapshot(
        sbn: StatusBarNotification,
        rankingMap: RankingMap? = null
    ): ClockNotificationEntry? {
        if (!shouldShow(sbn)) return null

        val rank =
            when {
                rankingMap != null &&
                    rankingMap.getRanking(
                        sbn.key,
                        ranking
                    ) -> ranking.rank

                currentRanking.getRanking(
                    sbn.key,
                    ranking
                ) -> ranking.rank

                else -> Int.MAX_VALUE
            }

        return ClockNotificationEntry(
            key = sbn.key,
            notification = sbn,
            rank = rank
        )
    }

    private fun shouldShow(
        sbn: StatusBarNotification
    ): Boolean {
        if (sbn.packageName == packageName) return false

        val notification = sbn.notification
        if (notification.smallIcon == null) return false

        // SystemUI normally renders group summaries as part of the group,
        // rather than as an independent status-bar icon.
        if (
            notification.flags and
                Notification.FLAG_GROUP_SUMMARY != 0
        ) {
            return false
        }

        return true
    }
}
