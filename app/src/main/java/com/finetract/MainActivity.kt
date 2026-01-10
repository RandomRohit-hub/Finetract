package com.finetract

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Reset check whenever app opens
        TransactionManager.checkAndReset(this)
        
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btn_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // Banners
        findViewById<Button>(R.id.btn_fix_notification).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        findViewById<Button>(R.id.btn_fix_battery).setOnClickListener {
             if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                 try {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                 } catch (e: Exception) {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                 }
             }
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
        
        // Check Permissions UI
        val hasPermission = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        val bannerNotif = findViewById<View>(R.id.banner_notification_warning)
        
        if (hasPermission) {
            bannerNotif.visibility = View.GONE
        } else {
            bannerNotif.visibility = View.VISIBLE
        }
        
        val bannerBattery = findViewById<View>(R.id.banner_battery_warning)
        var isOptimized = false
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val pm = getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
            isOptimized = !pm.isIgnoringBatteryOptimizations(packageName)
        }
        
        if (isOptimized) {
            bannerBattery.visibility = View.VISIBLE
        } else {
            bannerBattery.visibility = View.GONE
        }
        
        // Update Dashboard
        val tvSpend = findViewById<TextView>(R.id.tv_today_spend)
        val tvDailyLimitText = findViewById<TextView>(R.id.tv_daily_limit)
        val tvStatus = findViewById<TextView>(R.id.tv_limit_status)

        val spend = TransactionManager.getTodaySpend(this)
        val limit = TransactionManager.getDailyLimit(this)
        val isExceeded = spend > limit

        tvSpend.text = "₹$spend"
        tvDailyLimitText.text = "Daily Limit: ₹$limit"

        if (isExceeded) {
            tvStatus.text = "Limit Crossed!"
            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
        } else {
            tvStatus.text = "Within Limit"
            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
        }
    }
}
