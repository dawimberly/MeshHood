package com.meshhood

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton

/**
 * Lock-screen reachable SOS — shows over the keyguard when launched from the widget
 * or app shortcut without unlocking first.
 */
class EmergencyActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        setContentView(R.layout.activity_emergency)

        findViewById<MaterialButton>(R.id.emergencyConfirmButton).setOnClickListener {
            ContextCompat.startForegroundService(
                this,
                Intent(this, MeshService::class.java).apply { action = MeshService.ACTION_EMERGENCY },
            )
            Toast.makeText(this, R.string.emergency_sent_toast, Toast.LENGTH_LONG).show()
            finish()
        }

        findViewById<MaterialButton>(R.id.emergencyCancelButton).setOnClickListener { finish() }
    }
}
