package com.finetract

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import java.text.SimpleDateFormat
import java.util.*

/**
 * A simple calendar heat-map view showing daily spending for the selected month.
 */
class HistoryCalendarView : Fragment(R.layout.fragment_history_calendar) {

    companion object {
        private const val ARG_MONTH = "arg_month"
        private const val ARG_YEAR  = "arg_year"

        fun forMonth(cal: Calendar): HistoryCalendarView {
            return HistoryCalendarView().apply {
                arguments = Bundle().apply {
                    putInt(ARG_MONTH, cal.get(Calendar.MONTH))
                    putInt(ARG_YEAR,  cal.get(Calendar.YEAR))
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val month = arguments?.getInt(ARG_MONTH) ?: Calendar.getInstance().get(Calendar.MONTH)
        val year  = arguments?.getInt(ARG_YEAR)  ?: Calendar.getInstance().get(Calendar.YEAR)

        val transactions = TransactionManager.getTransactions(requireContext())
        val dailySpend   = mutableMapOf<Int, Float>()

        transactions.forEach { txn ->
            val c = Calendar.getInstance()
            c.timeInMillis = txn.timestamp
            if (c.get(Calendar.MONTH) == month && c.get(Calendar.YEAR) == year) {
                val day = c.get(Calendar.DAY_OF_MONTH)
                dailySpend[day] = (dailySpend[day] ?: 0f) + txn.amount
            }
        }

        val cal = Calendar.getInstance()
        cal.set(year, month, 1)
        val daysInMonth   = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val startDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1 // 0=Sun

        val container = view.findViewById<LinearLayout>(R.id.calendar_container)
        container.removeAllViews()

        // Week row
        val weekRow = LinearLayout(requireContext()).also {
            it.orientation = LinearLayout.HORIZONTAL
            it.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        listOf("S","M","T","W","T","F","S").forEach { label ->
            weekRow.addView(makeCell(label, Color.TRANSPARENT, Color.GRAY, bold = true))
        }
        container.addView(weekRow)

        // Grid rows
        var row = LinearLayout(requireContext()).also {
            it.orientation = LinearLayout.HORIZONTAL
            it.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        container.addView(row)

        // Empty cells before day 1
        repeat(startDayOfWeek) { row.addView(makeCell("", Color.TRANSPARENT)) }

        val maxSpend = dailySpend.values.maxOrNull() ?: 1f
        val dailyLimit = TransactionManager.getDailyLimit(requireContext())

        for (day in 1..daysInMonth) {
            val spend = dailySpend[day] ?: 0f
            val bgColor = when {
                spend == 0f          -> Color.parseColor("#F5F5F5")
                dailyLimit > 0 && spend >= dailyLimit -> ContextCompat.getColor(requireContext(), R.color.danger_red)
                dailyLimit > 0 && spend >= dailyLimit * 0.7f -> ContextCompat.getColor(requireContext(), R.color.warning_yellow)
                else                 -> ContextCompat.getColor(requireContext(), R.color.success_green)
            }
            val opacity = if (spend > 0) (0.4f + 0.6f * (spend / maxSpend)).coerceIn(0.3f, 1f) else 1f
            val cell = makeCell(
                day.toString(),
                adjustAlpha(bgColor, opacity),
                if (spend > 0) Color.WHITE else Color.GRAY
            )
            cell.setOnClickListener {
                if (spend > 0) {
                    val fmt = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
                    val dateStr = fmt.format(Calendar.getInstance().also { c -> c.set(year, month, day) }.time)
                    android.widget.Toast.makeText(requireContext(), "$dateStr\n₹${spend.toInt()} spent", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            row.addView(cell)

            // Wrap to next row when week is complete
            val dayOfWeek = (startDayOfWeek + day - 1) % 7
            if (dayOfWeek == 6 && day != daysInMonth) {
                row = LinearLayout(requireContext()).also {
                    it.orientation = LinearLayout.HORIZONTAL
                    it.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                }
                container.addView(row)
            }
        }

        // Legend
        val tvLegend = view.findViewById<TextView>(R.id.tv_calendar_legend)
        val totalMonth = dailySpend.values.sum()
        tvLegend?.text = "Total this month: ₹${totalMonth.toInt()}  |  Days tracked: ${dailySpend.size}"
    }

    private fun makeCell(
        text: String,
        bgColor: Int,
        textColor: Int = Color.BLACK,
        bold: Boolean = false
    ): TextView {
        val size = 42
        val tv = TextView(requireContext())
        tv.text = text
        tv.gravity = Gravity.CENTER
        tv.setTextColor(textColor)
        tv.textSize = 11f
        if (bold) tv.setTypeface(tv.typeface, android.graphics.Typeface.BOLD)
        val params = LinearLayout.LayoutParams(0, size.dpToPx()).also { it.weight = 1f; it.setMargins(2, 2, 2, 2) }
        tv.layoutParams = params
        tv.setBackgroundColor(bgColor)

        // Rounded corners via background drawable
        val drawable = android.graphics.drawable.GradientDrawable()
        drawable.cornerRadius = 6f.dpToPxF()
        drawable.setColor(bgColor)
        tv.background = drawable

        return tv
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
    private fun Float.dpToPxF(): Float = this * resources.displayMetrics.density

    private fun adjustAlpha(color: Int, factor: Float): Int {
        val alpha = Math.round(Color.alpha(color) * factor)
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }
}
