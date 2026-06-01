package com.meshhood

import android.app.PendingIntent
import androidx.core.app.NotificationCompat

/** Consumer flavor: no gateway headless notification actions. */
internal fun applyGatewayHeadlessNotificationActions(
    builder: NotificationCompat.Builder,
    service: MeshService,
    openPending: PendingIntent,
) = Unit
