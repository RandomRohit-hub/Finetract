package com.finetract

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlin.math.roundToInt

class HomeFragment : Fragment(R.layout.fragment_home) {

    override fun onResume() {
        super.onResume()
        updateDashboard()
    }

    private fun updateDashboard() {
        val context = requireContext()
        // Ensure data is fresh
        TransactionManager.checkAndReset(context)

        val spend = TransactionManager.getTodaySpend(context)
        val limit = TransactionManager.getDailyLimit(context)
        val remaining = limit - spend
        
        // Progress Logic
        val progress = if (limit > 0) ((spend / limit) * 100).toInt() else 0
        
        // Color Logic
        val colorRes = when {
            spend > limit -> R.color.danger_red
            progress >= 90 -> R.color.warning_yellow
            else -> R.color.success_green
        }
        val statusColor = ContextCompat.getColor(context, colorRes)

        // Bind UI
        view?.findViewById<TextView>(R.id.tv_spend_amount)?.text = "₹${spend.toInt()}"
        view?.findViewById<TextView>(R.id.tv_limit_info)?.text = getString(R.string.label_spent_of) + " ₹${limit.toInt()}"
        
        val progressBar = view?.findViewById<com.google.android.material.progressindicator.CircularProgressIndicator>(R.id.progress_limit)
        progressBar?.progress = progress.coerceIn(0, 100)
        progressBar?.setIndicatorColor(statusColor)
        
        // Bind Remaining View
        val tvRemaining = view?.findViewById<TextView>(R.id.tv_remaining_amount)
        if (remaining < 0) {
            tvRemaining?.text = "-₹${kotlin.math.abs(remaining.toInt())}"
            tvRemaining?.setTextColor(ContextCompat.getColor(context, R.color.danger_red))
        } else {
            tvRemaining?.text = "₹${remaining.toInt()}"
            tvRemaining?.setTextColor(ContextCompat.getColor(context, R.color.primary))
        }

        // Status Message
        val statusMsg = view?.findViewById<TextView>(R.id.tv_status_message)
        if (spend > limit) {
             statusMsg?.text = getString(R.string.status_alert)
             statusMsg?.setTextColor(statusColor)
        } else {
             statusMsg?.text = getString(R.string.positive_reinforcement)
             statusMsg?.setTextColor(ContextCompat.getColor(context, R.color.success_green))
        }

        // Add Click Listener to open Analytics
        view?.findViewById<View>(R.id.container_progress)?.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, InsightsFragment())
                .addToBackStack(null)
                .commit()
        }
    }
}
