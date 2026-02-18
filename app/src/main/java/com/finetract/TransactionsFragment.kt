package com.finetract

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class TransactionsFragment : Fragment(R.layout.fragment_transactions) {

    private lateinit var adapter: TxnAdapter
    private var allTxns: List<TransactionManager.TransactionRecord> = emptyList()
    private lateinit var chartBar: BarChart
    private lateinit var chartPie: PieChart
    private lateinit var tvTotalSpent: TextView
    private lateinit var tvDailyBudget: TextView
    private lateinit var progressBudget: ProgressBar
    private lateinit var tvBudgetStatus: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rv = view.findViewById<RecyclerView>(R.id.rv_transactions)
        rv.layoutManager = LinearLayoutManager(context)
        adapter = TxnAdapter()
        rv.adapter = adapter

        // Initialize chart and budget views
        chartBar = view.findViewById(R.id.chart_bar)
        chartPie = view.findViewById(R.id.chart_pie)
        tvTotalSpent = view.findViewById(R.id.tv_total_spent)
        tvDailyBudget = view.findViewById(R.id.tv_daily_budget)
        progressBudget = view.findViewById(R.id.progress_budget)
        tvBudgetStatus = view.findViewById(R.id.tv_budget_status)

        setupBarChart()
        setupPieChart()

        // Load all
        allTxns = TransactionManager.getTransactions(requireContext()).sortedByDescending { it.timestamp }
        
        // Default: Current Month
        filterByMonth(Calendar.getInstance())

        view.findViewById<View>(R.id.btn_filter_transactions).setOnClickListener {
            showFilterDialog()
        }
    }

    private fun setupBarChart() {
        chartBar.apply {
            description.isEnabled = false
            setTouchEnabled(true)
            setDrawGridBackground(false)
            legend.isEnabled = false
            setScaleEnabled(false)
            setDrawBarShadow(false)
            
            // X-Axis styling
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                textColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
                granularity = 1f
            }
            
            // Y-Axis styling
            axisLeft.apply {
                setDrawGridLines(true)
                gridColor = Color.parseColor("#E0E0E0")
                textColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
                axisMinimum = 0f
            }
            axisRight.isEnabled = false
            
            // Padding
            setExtraOffsets(0f, 10f, 0f, 10f)
        }
    }

    private fun setupPieChart() {
        chartPie.apply {
            description.isEnabled = false
            setUsePercentValues(false)
            setDrawEntryLabels(false)
            setTouchEnabled(true)
            setDrawHoleEnabled(true)
            holeRadius = 45f
            setHoleColor(Color.WHITE)
            setTransparentCircleRadius(50f)
            
            legend.apply {
                isEnabled = true
                textSize = 11f
                textColor = ContextCompat.getColor(requireContext(), R.color.text_primary)
                horizontalAlignment = com.github.mikephil.charting.components.Legend.LegendHorizontalAlignment.CENTER
            }
            
            setExtraOffsets(5f, 10f, 5f, 10f)
        }
    }

    private fun showFilterDialog() {
        val months = arrayOf(
            "Current Month",
            "January", "February", "March", "April", "May", "June", 
            "July", "August", "September", "October", "November", "December"
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Filter Transactions")
            .setItems(months) { _, which ->
                if (which == 0) {
                     filterByMonth(Calendar.getInstance())
                     view?.findViewById<TextView>(R.id.tv_filter_label)?.text = "Current Month"
                } else {
                     val cal = Calendar.getInstance()
                     cal.set(Calendar.MONTH, which - 1) // Jan is index 1 in our array
                     filterByMonth(cal)
                     view?.findViewById<TextView>(R.id.tv_filter_label)?.text = months[which]
                }
            }
            .show()
    }

    private fun filterByMonth(cal: Calendar) {
        val targetMonth = cal.get(Calendar.MONTH)
        val targetYear = cal.get(Calendar.YEAR)
        
        val filtered = allTxns.filter {
            val c = Calendar.getInstance()
            c.timeInMillis = it.timestamp
            c.get(Calendar.MONTH) == targetMonth && c.get(Calendar.YEAR) == targetYear
        }
        
        adapter.submitList(filtered)
        
        val emptyView = view?.findViewById<View>(R.id.tv_empty_txns)
        emptyView?.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        
        // Update charts
        updateBarChart(filtered, targetYear, targetMonth)
        updatePieChart(filtered)
        updateBudgetSummary(filtered)
    }

    private fun updateBarChart(transactions: List<TransactionManager.TransactionRecord>, year: Int, month: Int) {
        val cal = Calendar.getInstance()
        cal.set(year, month, 1)
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        
        // Aggregate spending by day
        val dailySpending = mutableMapOf<Int, Float>()
        transactions.forEach { txn ->
            val c = Calendar.getInstance()
            c.timeInMillis = txn.timestamp
            val day = c.get(Calendar.DAY_OF_MONTH)
            dailySpending[day] = (dailySpending[day] ?: 0f) + txn.amount
        }
        
        // Create bar chart entries
        val entries = mutableListOf<BarEntry>()
        for (day in 1..daysInMonth) {
            val amount = dailySpending[day] ?: 0f
            entries.add(BarEntry(day.toFloat(), amount))
        }
        
        if (entries.isEmpty() || entries.all { it.y == 0f }) {
            chartBar.clear()
            chartBar.invalidate()
            return
        }
        
        val dataSet = BarDataSet(entries, "Daily Spending").apply {
            color = ContextCompat.getColor(requireContext(), R.color.primary)
            valueTextColor = ContextCompat.getColor(requireContext(), R.color.text_primary)
            valueTextSize = 9f
            setDrawValues(false)
        }
        
        chartBar.data = BarData(dataSet)
        chartBar.xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return value.toInt().toString()
            }
        }
        chartBar.animateY(800)
        chartBar.invalidate()
    }

    private fun updatePieChart(transactions: List<TransactionManager.TransactionRecord>) {
        // Aggregate spending by category
        val categorySpending = mutableMapOf<String, Float>()
        transactions.forEach { txn ->
            categorySpending[txn.category] = (categorySpending[txn.category] ?: 0f) + txn.amount
        }
        
        if (categorySpending.isEmpty()) {
            chartPie.clear()
            chartPie.invalidate()
            return
        }
        
        // Create pie chart entries
        val entries = categorySpending.map { (category, amount) ->
            PieEntry(amount, category)
        }
        
        val dataSet = PieDataSet(entries, "").apply {
            sliceSpace = 2f
            selectionShift = 5f
            
            // Use colorful palette
            val colors = mutableListOf<Int>()
            colors.addAll(ColorTemplate.MATERIAL_COLORS.toList())
            colors.addAll(ColorTemplate.VORDIPLOM_COLORS.toList())
            setColors(colors)
            
            valueTextSize = 11f
            valueTextColor = Color.WHITE
        }
        
        val pieData = PieData(dataSet).apply {
            setValueFormatter(object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return "₹${value.toInt()}"
                }
            })
        }
        
        chartPie.data = pieData
        chartPie.animateY(1000)
        chartPie.invalidate()
    }

    private fun updateBudgetSummary(transactions: List<TransactionManager.TransactionRecord>) {
        val totalSpent = transactions.sumOf { it.amount.toDouble() }.toFloat()
        val dailyLimit = TransactionManager.getDailyLimit(requireContext())
        
        tvTotalSpent.text = "₹${totalSpent.toInt()}"
        tvDailyBudget.text = "₹${dailyLimit.toInt()}"
        
        val averageDaily = if (transactions.isEmpty()) 0f else {
            val minDate = transactions.minByOrNull { it.timestamp }?.timestamp ?: return
            val maxDate = transactions.maxByOrNull { it.timestamp }?.timestamp ?: return
            val daysDiff = ((maxDate - minDate) / (1000 * 60 * 60 * 24)).toInt() + 1
            totalSpent / daysDiff.coerceAtLeast(1)
        }
        
        val progress = ((averageDaily / dailyLimit) * 100).toInt().coerceIn(0, 100)
        progressBudget.progress = progress
        
        if (averageDaily <= dailyLimit) {
            tvBudgetStatus.text = "Within budget - ₹${(dailyLimit - averageDaily).toInt()} avg. remaining"
            tvBudgetStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.success_green))
        } else {
            tvBudgetStatus.text = "Over budget by ₹${(averageDaily - dailyLimit).toInt()} avg. per day"
            tvBudgetStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.danger_red))
        }
    }

    // --- Adapter ---
    inner class TxnAdapter : RecyclerView.Adapter<TxnViewHolder>() {
        private var items = listOf<TransactionManager.TransactionRecord>()
        
        fun submitList(newItems: List<TransactionManager.TransactionRecord>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TxnViewHolder {
            val v = layoutInflater.inflate(R.layout.item_transaction, parent, false)
            return TxnViewHolder(v)
        }

        override fun onBindViewHolder(holder: TxnViewHolder, position: Int) {
            val item = items[position]
            
            // Header Logic
            val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            val currentDate = dateFmt.format(Date(item.timestamp))
            
            // Show header if it's the first item OR if previous item has different date
            var showHeader = false
            if (position == 0) {
                showHeader = true
            } else {
                val prevDate = dateFmt.format(Date(items[position - 1].timestamp))
                if (prevDate != currentDate) showHeader = true
            }
            
            if (showHeader) {
                holder.tvHeader.visibility = View.VISIBLE
                holder.tvHeader.text = currentDate
            } else {
                holder.tvHeader.visibility = View.GONE
            }

            // Normal Data
            holder.tvMerchant.text = item.merchant
            holder.tvCategory.text = item.category
            holder.tvAmount.text = "- ₹${item.amount.toInt()}"
            
            val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
            holder.tvTime.text = timeFmt.format(Date(item.timestamp))
        }

        override fun getItemCount() = items.size
    }

    class TxnViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvHeader: TextView = view.findViewById(R.id.tv_txn_header)
        val tvMerchant: TextView = view.findViewById(R.id.tv_merchant)
        val tvCategory: TextView = view.findViewById(R.id.tv_category)
        val tvTime: TextView = view.findViewById(R.id.tv_time)
        val tvAmount: TextView = view.findViewById(R.id.tv_amount)
    }
}
