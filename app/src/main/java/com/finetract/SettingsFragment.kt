package com.finetract

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.finetract.data.repository.PrivacyRepository
import com.finetract.utils.PermissionManager
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SettingsFragment : Fragment(R.layout.fragment_settings) {

    @Inject lateinit var privacyRepository: PrivacyRepository
    @Inject lateinit var permissionManager: PermissionManager

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupBudgetSection(view)
        setupPrivacySection(view)
        setupPermissionSection(view)
        setupDataSection(view)
    }

    private fun setupBudgetSection(view: View) {
        val etLimit = view.findViewById<TextInputEditText>(R.id.et_daily_limit)
        val btnSave = view.findViewById<Button>(R.id.btn_save_limit)
        
        val currentLimit = TransactionManager.getDailyLimit(requireContext())
        etLimit.setText(currentLimit.toInt().toString())

        etLimit.addTextChangedListener {
            btnSave.visibility = View.VISIBLE
        }

        btnSave.setOnClickListener {
            val newLimit = etLimit.text.toString().toFloatOrNull() ?: currentLimit
            TransactionManager.setDailyLimit(requireContext(), newLimit)
            btnSave.visibility = View.GONE
            etLimit.clearFocus()
        }
    }

    private fun setupPrivacySection(view: View) {
        val switchPrivacy = view.findViewById<MaterialSwitch>(R.id.switch_privacy_mode)
        
        viewLifecycleOwner.lifecycleScope.launch {
            privacyRepository.isPrivacyMode.collectLatest { isChecked ->
                switchPrivacy.isChecked = isChecked
            }
        }

        switchPrivacy.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch {
                privacyRepository.setPrivacyMode(isChecked)
            }
        }
    }

    private fun setupPermissionSection(view: View) {
        updatePermissionUI()

        view.findViewById<View>(R.id.row_notification_access).setOnClickListener {
            permissionManager.openNotificationAccessSettings(requireContext())
        }

        view.findViewById<View>(R.id.row_battery_optimization).setOnClickListener {
            permissionManager.openBatteryOptimizationSettings(requireContext())
        }
    }

    private fun updatePermissionUI() {
        val root = view ?: return
        val notifGranted = permissionManager.isNotificationAccessGranted(requireContext())
        val batteryIgnored = permissionManager.isBatteryOptimizationIgnored(requireContext())

        // Notification row
        root.findViewById<TextView>(R.id.tv_notification_status).apply {
            text = if (notifGranted) "Granted" else "Not granted — tap to enable"
            setTextColor(ContextCompat.getColor(context, if (notifGranted) R.color.success_green else R.color.danger_red))
        }
        root.findViewById<TextView>(R.id.btn_notification_action).apply {
            text = if (notifGranted) "Granted ✓" else "Enable"
            isEnabled = !notifGranted
        }

        // Battery row
        root.findViewById<TextView>(R.id.tv_battery_status).apply {
            text = if (batteryIgnored) "Running unrestricted" else "Battery optimization active"
            setTextColor(ContextCompat.getColor(context, if (batteryIgnored) R.color.success_green else R.color.warning_yellow))
        }
        root.findViewById<TextView>(R.id.btn_battery_action).apply {
            text = if (batteryIgnored) "OK ✓" else "Fix"
            isEnabled = !batteryIgnored
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionUI()
    }

    private fun setupDataSection(view: View) {
        view.findViewById<Button>(R.id.btn_reset_data).setOnClickListener {
            TransactionManager.clearAll(requireContext())
        }
    }
}
