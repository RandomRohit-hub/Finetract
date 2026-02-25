package com.finetract

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButtonToggleGroup
import java.text.SimpleDateFormat
import java.util.*

class HistoryFragment : Fragment(R.layout.fragment_history) {

    private val calendar = Calendar.getInstance()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupMonthNavigator(view)
        setupViewToggle(view)

        // Load default view
        if (savedInstanceState == null) {
            showChartView()
        }

        updateMonthDisplay(view)
    }

    private fun setupMonthNavigator(view: View) {
        view.findViewById<View>(R.id.btn_prev_month).setOnClickListener {
            calendar.add(Calendar.MONTH, -1)
            updateMonthDisplay(view)
            refreshCurrentView()
        }
        view.findViewById<View>(R.id.btn_next_month).setOnClickListener {
            calendar.add(Calendar.MONTH, 1)
            updateMonthDisplay(view)
            refreshCurrentView()
        }
    }

    private fun setupViewToggle(view: View) {
        val toggleGroup = view.findViewById<MaterialButtonToggleGroup>(R.id.toggle_view)
        toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btn_view_chart    -> showChartView()
                    R.id.btn_view_calendar -> showCalendarView()
                }
            }
        }
    }

    private fun updateMonthDisplay(view: View) {
        val fmt = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        view.findViewById<TextView>(R.id.tv_month_year).text = fmt.format(calendar.time)
    }

    /** Show full transaction chart and list for the selected month */
    private fun showChartView() {
        val fragment = HistoryChartView.forMonth(calendar)
        childFragmentManager.beginTransaction()
            .replace(R.id.history_content_container, fragment)
            .commit()
    }

    /** Show calendar heat-map view for the selected month */
    private fun showCalendarView() {
        val fragment = HistoryCalendarView.forMonth(calendar)
        childFragmentManager.beginTransaction()
            .replace(R.id.history_content_container, fragment)
            .commit()
    }

    private fun refreshCurrentView() {
        val current = childFragmentManager.findFragmentById(R.id.history_content_container)
        when (current) {
            is HistoryChartView    -> showChartView()
            is HistoryCalendarView -> showCalendarView()
            else                   -> showChartView()
        }
    }
}
