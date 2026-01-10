package com.finetract

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat

class MainActivity : AppCompatActivity() {

    private lateinit var tvTodaySpend: TextView
    private lateinit var tvLimitStatus: TextView
    private lateinit var tvDailyLimit: TextView
    private lateinit var layoutPermission: LinearLayout
    private lateinit var layoutHome: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Reset check whenever app opens
        TransactionManager.checkAndReset(this)
        
        setContentView(R.layout.activity_main)

        tvTodaySpend = findViewById(R.id.tv_today_spend)
        tvLimitStatus = findViewById(R.id.tv_limit_status)
        tvDailyLimit = findViewById(R.id.tv_daily_limit)
        layoutPermission = findViewById(R.id.layout_permission)
        layoutHome = findViewById(R.id.layout_home)

        findViewById<Button>(R.id.btn_grant_permission).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        findViewById<Button>(R.id.btn_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // Request Notification POST permission for Android 13+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkPermissionAndUI()
    }

    private fun checkPermissionAndUI() {
        if (!isNotificationServiceEnabled()) {
            layoutPermission.visibility = View.VISIBLE
            layoutHome.visibility = View.GONE
        } else {
            layoutPermission.visibility = View.GONE
            layoutHome.visibility = View.VISIBLE
            updateDashboard()
            
            // Observe logs
            val tvLogs = findViewById<TextView>(R.id.tv_debug_log)
            DebugLogManager.logs.observe(this) { logs ->
                tvLogs.text = logs
            }
        }
    }

    private fun updateDashboard() {
        val spend = TransactionManager.getTodaySpend(this)
        val limit = TransactionManager.getDailyLimit(this)
        val isExceeded = spend > limit

        tvTodaySpend.text = "₹$spend"
        tvDailyLimit.text = "Daily Limit: ₹$limit"

        if (isExceeded) {
            tvLimitStatus.text = "Limit Crossed!"
            tvLimitStatus.setTextColor(Color.RED)
        } else {
            tvLimitStatus.text = "Within Limit"
            tvLimitStatus.setTextColor(Color.GREEN)
        }
    }

    private fun isNotificationServiceEnabled(): Boolean {
        return NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
    }
}
