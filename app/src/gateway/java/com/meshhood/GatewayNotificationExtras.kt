package com.meshhood

import android.app.PendingIntent
import androidx.core.app.NotificationCompat

/** Gateway flavor: add headless notification tap target and Official alerts action. */
internal fun applyGatewayHeadlessNotificationActions(
    builder: NotificationCompat.Builder,
    service: MeshService,
    openPending: PendingIntent,
) {
    builder.setContentIntent(openPending)
    builder.addAction(
        0,
        service.getString(R.string.gateway_notification_open_official_alerts),
        openPending,
    )
}
