package com.finetract

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import com.finetract.R
import com.finetract.TransactionManager
import com.google.android.material.textfield.TextInputEditText

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Edit Limit
        view.findViewById<Button>(R.id.btn_edit_limit).setOnClickListener {
            showEditLimitDialog()
        }
        
        // Large Payment Threshold
        val etThreshold = view.findViewById<TextInputEditText>(R.id.et_large_threshold)
        etThreshold.setText(TransactionManager.getLargePaymentThreshold(requireContext()).toString())
        etThreshold.addTextChangedListener {
            val value = it.toString().toFloatOrNull() ?: 0f
            TransactionManager.setLargePaymentThreshold(requireContext(), value)
        }
        
        // System Fix Buttons
        view.findViewById<Button>(R.id.btn_fix_notif).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        view.findViewById<Button>(R.id.btn_fix_battery).setOnClickListener {
             if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                 try {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                 } catch (e: Exception) {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                 }
             }
        }
        
        // Debug Log Observer
        val tvDebugLogs = view.findViewById<TextView>(R.id.tv_debug_logs)
        DebugLogManager.logs.observe(viewLifecycleOwner) { logs ->
            tvDebugLogs.text = logs.ifEmpty { "Waiting for notifications..." }
        }
        
        // Test Button - Verify logging system works
        view.findViewById<Button>(R.id.btn_test_log).setOnClickListener {
            DebugLogManager.log("🧪 Test button clicked - Logging works!")
            DebugLogManager.log("Checking if service is connected...")
            // The service should log "Service Linked & Ready!" when connected
        }
        
        // Clear Logs Button
        view.findViewById<Button>(R.id.btn_clear_logs).setOnClickListener {
            DebugLogManager.clear()
        }
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun updateUI() {
        val context = requireContext()
        val limit = TransactionManager.getDailyLimit(context)
        view?.findViewById<TextView>(R.id.tv_current_limit)?.text = "₹$limit"
        
        // Check Permissions
        checkHardeningStatus()
    }
    
    private fun checkHardeningStatus() {
        val context = requireContext()
        val view = view ?: return
        
        // Notification Access
        val hasNotif = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        val tvNotif = view.findViewById<TextView>(R.id.tv_status_notif)
        val btnFixNotif = view.findViewById<Button>(R.id.btn_fix_notif)
        
        if (hasNotif) {
            tvNotif.text = getString(R.string.status_enabled)
            tvNotif.setTextColor(ContextCompat.getColor(context, R.color.success_green))
            btnFixNotif.visibility = View.GONE
        } else {
            tvNotif.text = getString(R.string.status_disabled)
            tvNotif.setTextColor(ContextCompat.getColor(context, R.color.danger_red))
            btnFixNotif.visibility = View.VISIBLE
        }
        
        // Battery Opt
        val tvBattery = view.findViewById<TextView>(R.id.tv_status_battery)
        val btnFixBattery = view.findViewById<Button>(R.id.btn_fix_battery)
        var isOptimized = false
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
             val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
             isOptimized = !pm.isIgnoringBatteryOptimizations(context.packageName)
        }

        if (!isOptimized) {
            tvBattery.text = getString(R.string.status_disabled) // Desirable
            tvBattery.setTextColor(ContextCompat.getColor(context, R.color.success_green))
            btnFixBattery.visibility = View.GONE
        } else {
            tvBattery.text = getString(R.string.status_enabled) // Undesirable
            tvBattery.setTextColor(ContextCompat.getColor(context, R.color.warning_yellow))
            btnFixBattery.visibility = View.VISIBLE
        }
    }

    private fun showEditLimitDialog() {
        val context = requireContext()
        val input = EditText(context)
        input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        input.setText(TransactionManager.getDailyLimit(context).toString())

        AlertDialog.Builder(context)
            .setTitle(R.string.dialog_update_limit)
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                val newLimit = input.text.toString().toFloatOrNull() ?: 500f
                TransactionManager.setDailyLimit(context, newLimit)
                updateUI()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
