package com.meshhood.debug

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.meshhood.BuildConfig
import com.meshhood.MeshService

/** adb broadcast entry — binds to [MeshService] and forwards JSON to [DebugInject.injectDebugPayload]. */
class DebugInjectReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (!BuildConfig.DEBUG || intent == null) return
        val json = DebugInject.envelopeFrom(intent) ?: return
        val app = context.applicationContext

        ContextCompat.startForegroundService(app, Intent(app, MeshService::class.java))

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val service = (binder as? MeshService.LocalBinder)?.service ?: return
                DebugInject.injectDebugPayload(service, json)
                app.unbindService(this)
            }

            override fun onServiceDisconnected(name: ComponentName?) = Unit
        }
        app.bindService(Intent(app, MeshService::class.java), connection, Context.BIND_AUTO_CREATE)
    }
}
