package com.finetract

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.finetract.data.repository.PrivacyRepository
import com.finetract.domain.BudgetAlertLevel
import com.finetract.domain.ComputeDailyInsightUseCase
import com.finetract.domain.DailyInsight
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.CircularProgressIndicator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class TodayFragment : Fragment(R.layout.fragment_today) {

    @Inject lateinit var privacyRepository: PrivacyRepository
    @Inject lateinit var computeDailyInsightUseCase: ComputeDailyInsightUseCase
    
    private val viewModel: HomeViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupHeader(view)
        setupBudgetRing(view)
        setupFAB(view)

        observeViewModel()
        observePrivacyMode()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun setupHeader(view: View) {
        val dateStr = SimpleDateFormat("EEEE, d MMM", Locale.getDefault()).format(Date())
        view.findViewById<TextView>(R.id.tv_date).text = dateStr

        view.findViewById<ImageView>(R.id.iv_privacy_toggle).setOnClickListener {
            lifecycleScope.launch {
                privacyRepository.togglePrivacyMode()
            }
        }
    }

    private fun setupBudgetRing(view: View) {
        view.findViewById<View>(R.id.progress_budget_ring).setOnClickListener {
            showDayDetailBottomSheet()
        }
    }

    private fun setupFAB(view: View) {
        view.findViewById<View>(R.id.fab_add_expense).setOnClickListener {
            showAddTransactionBottomSheet()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.budgetAlert.collect { state ->
                    state ?: return@collect
                    
                    // Show notification/alert based on level
                    when (state.level) {
                        BudgetAlertLevel.EXCEEDED -> {
                            BudgetNotificationHelper.showNotification(
                                requireContext(),
                                1001,
                                "Budget Exceeded",
                                "You've spent ₹${state.spentToday.toInt()} of your ₹${state.dailyLimit.toInt()} limit"
                            )
                        }
                        BudgetAlertLevel.APPROACHING -> {
                            BudgetNotificationHelper.showNotification(
                                requireContext(),
                                1002,
                                "Approaching Limit",
                                "You've used ${state.percentUsed.toInt()}% of today's budget"
                            )
                        }
                        else -> {}
                    }
                    updateUI() // Keep UI synced with reactive data
                }
            }
        }
    }

    private fun observePrivacyMode() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                privacyRepository.isPrivacyMode.collectLatest { isChecked ->
                    updatePrivacyUI(isChecked)
                    updateUI()
                }
            }
        }
    }

    private fun updatePrivacyUI(isPrivate: Boolean) {
        val toggle = view?.findViewById<ImageView>(R.id.iv_privacy_toggle)
        toggle?.setImageResource(if (isPrivate) R.drawable.ic_eye_off else R.drawable.ic_eye_on)
    }

    private fun updateUI() {
        val root = view ?: return
        val context = requireContext()
        val spend = TransactionManager.getTodaySpend(context)
        val limit = TransactionManager.getDailyLimit(context)
        val stats = TransactionManager.getTodayStats(context)

        // Budget Ring
        val progress = if (limit > 0) ((spend / limit) * 100).toInt() else 0
        val ring = root.findViewById<CircularProgressIndicator>(R.id.progress_budget_ring)
        ring.setProgress(progress.coerceIn(0, 100), true)
        
        val colorRes = when {
            spend > limit -> R.color.danger_red
            progress >= 75 -> R.color.warning_yellow
            else -> R.color.success_green
        }
        ring.setIndicatorColor(ContextCompat.getColor(context, colorRes))

        root.findViewById<TextView>(R.id.tv_spend_today).text = formatMoney(spend)
        root.findViewById<TextView>(R.id.tv_limit_info).text = "of ${formatMoney(limit)} limit"

        // Stats
        root.findViewById<TextView>(R.id.tv_stat_count).text = stats.count.toString()
        root.findViewById<TextView>(R.id.tv_stat_highest).text = if (stats.maxAmount > 0) formatMoney(stats.maxAmount) else "-"
        
        val remaining = limit - spend
        val tvRemaining = root.findViewById<TextView>(R.id.tv_stat_remaining)
        val tvRemainingLabel = root.findViewById<TextView>(R.id.tv_stat_remaining_label)
        
        if (remaining < 0) {
            tvRemaining.text = formatMoney(Math.abs(remaining))
            tvRemaining.setTextColor(ContextCompat.getColor(context, R.color.danger_red))
            tvRemainingLabel.text = "Over"
            tvRemainingLabel.setTextColor(ContextCompat.getColor(context, R.color.danger_red))
        } else {
            tvRemaining.text = formatMoney(remaining)
            tvRemaining.setTextColor(ContextCompat.getColor(context, R.color.black))
            tvRemainingLabel.text = "Left"
            tvRemainingLabel.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
        }

        updateInsightCard(root)
    }

    private fun updateInsightCard(root: View) {
        val insight = computeDailyInsightUseCase()
        val card = root.findViewById<MaterialCardView>(R.id.card_insight)
        val text = root.findViewById<TextView>(R.id.tv_insight_text)
        val icon = root.findViewById<ImageView>(R.id.iv_insight_icon)

        if (insight is DailyInsight.None) {
            card.visibility = View.GONE
            return
        }

        card.visibility = View.VISIBLE
        when (insight) {
            is DailyInsight.OverLimit -> {
                text.text = "You've exceeded today's limit by ${formatMoney(insight.amount)}."
                icon.setImageResource(android.R.drawable.ic_dialog_alert)
                icon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.danger_red))
            }
            is DailyInsight.NearLimit -> {
                text.text = "You're close to today's limit. ${formatMoney(insight.remaining)} remaining."
                icon.setImageResource(android.R.drawable.ic_dialog_info)
                icon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.warning_yellow))
            }
            is DailyInsight.PositiveStreak -> {
                text.text = "${insight.days} days under budget. Keep going!"
                icon.setImageResource(android.R.drawable.btn_star_big_on)
                icon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.success_green))
            }
            is DailyInsight.NoTransactions -> {
                text.text = "No spending recorded today."
                icon.setImageResource(android.R.drawable.ic_dialog_info)
                icon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.primary))
            }
            else -> card.visibility = View.GONE
        }
    }

    private fun formatMoney(amount: Float): String {
        return "₹${amount.toInt()}" 
    }

    private fun showDayDetailBottomSheet() {
        DayDetailBottomSheet().show(parentFragmentManager, "DayDetail")
    }

    private fun showAddTransactionBottomSheet() {
        AddTransactionBottomSheet {
            updateUI()
        }.show(parentFragmentManager, "AddTransaction")
    }
}
