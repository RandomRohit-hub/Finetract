package com.finetract

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class InsightsFragment : Fragment(R.layout.fragment_insights) {

    private val calendar = Calendar.getInstance()
    private var historyArgs: List<TransactionManager.DailyRecord> = emptyList()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Load History
        historyArgs = TransactionManager.getHistory(requireContext())
        
        // Setup Recycler
        val rv = view.findViewById<RecyclerView>(R.id.rv_calendar)
        rv.layoutManager = GridLayoutManager(context, 7)
        rv.adapter = CalendarAdapter()

        // Setup Month Nav
        view.findViewById<View>(R.id.btn_prev_month).setOnClickListener {
            calendar.add(Calendar.MONTH, -1)
            updateUI()
        }
        view.findViewById<View>(R.id.btn_next_month).setOnClickListener {
            calendar.add(Calendar.MONTH, 1)
            updateUI()
        }
        
        view.findViewById<View>(R.id.btn_open_passbook).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, PassbookFragment())
                .addToBackStack(null)
                .commit()
        }

        updateUI()
    }
    
    private fun updateUI() {
        updateHeader()
        updateCalendar()
        updateAnalytics()
    }

    private fun updateHeader() {
        val fmt = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        view?.findViewById<TextView>(R.id.tv_month_year)?.text = fmt.format(calendar.time)
    }

    private fun updateAnalytics() {
        // Last 30 Days Logic
        // Filter history for last 30 days
        // Since history is just a list, we can take the last 30 entries if sorted, 
        // but it's unsorted in storage? TransactionManager appends, so it should be chronological.
        // Let's assume chronological.
        
        val recent = historyArgs.takeLast(30)
        if (recent.isEmpty()) {
             view?.findViewById<TextView>(R.id.tv_stat_on_track)?.text = "-/-"
             view?.findViewById<TextView>(R.id.tv_stat_avg)?.text = "₹0"
             return
        }

        var onTrackCount = 0
        var totalSpend = 0f
        
        for (rec in recent) {
            if (rec.spend <= rec.limit) onTrackCount++
            totalSpend += rec.spend
        }
        
        val avg = totalSpend / recent.size
        
        view?.findViewById<TextView>(R.id.tv_stat_on_track)?.text = "$onTrackCount/${recent.size}"
        view?.findViewById<TextView>(R.id.tv_stat_avg)?.text = "₹${avg.toInt()}"
    }

    private fun updateCalendar() {
        // Generate grid items
        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val tempCal = calendar.clone() as Calendar
        tempCal.set(Calendar.DAY_OF_MONTH, 1)
        
        // Day of week: 1=Sun, 2=Mon...
        val startDayOfWeek = tempCal.get(Calendar.DAY_OF_WEEK) 
        val emptyCells = startDayOfWeek - 1
        
        val items = mutableListOf<CalendarItem>()
        
        // Add Empty
        for (i in 0 until emptyCells) {
            items.add(CalendarItem(0, Status.NONE))
        }
        
        // Add Days
        val monthStr = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(calendar.time)
        val todayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val todayStr = todayFmt.format(java.util.Date())
        
        // Live Data for Today
        val context = requireContext()
        val todaySpend = TransactionManager.getTodaySpend(context)
        val todayLimit = TransactionManager.getDailyLimit(context)
        
        for (day in 1..daysInMonth) {
            val dayStr = String.format("%02d", day)
            val fullDate = "$monthStr-$dayStr"
            val isToday = (fullDate == todayStr)
            
            // Check history
            var record = historyArgs.find { it.date == fullDate }
            var status = Status.NONE
            
            // If it's today, we might not have a history record yet, so build a temp one for display
            if (isToday) {
                record = TransactionManager.DailyRecord(fullDate, todaySpend, todayLimit)
            }
            
            if (record != null) {
                status = if (record.spend > record.limit) Status.RED
                         else if (record.spend > record.limit * 0.9f) Status.YELLOW
                         else Status.GREEN
            } else {
                 // Future or No Data
                 status = Status.NONE
            }
            
            items.add(CalendarItem(day, status, record, isToday, fullDate))
        }
        
        (view?.findViewById<RecyclerView>(R.id.rv_calendar)?.adapter as? CalendarAdapter)?.submitList(items)
    }

    // --- Inner Classes ---
    
    enum class Status { NONE, GREEN, YELLOW, RED }
    
    data class CalendarItem(
        val day: Int, 
        val status: Status, 
        val record: TransactionManager.DailyRecord? = null,
        val isToday: Boolean = false,
        val fullDate: String = ""
    )
    
    inner class CalendarAdapter : RecyclerView.Adapter<CalendarViewHolder>() {
        private var items = listOf<CalendarItem>()
        
        fun submitList(newItems: List<CalendarItem>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CalendarViewHolder {
            val v = layoutInflater.inflate(R.layout.item_calendar_day, parent, false)
            return CalendarViewHolder(v)
        }

        override fun onBindViewHolder(holder: CalendarViewHolder, position: Int) {
            val item = items[position]
            if (item.day == 0) {
                holder.itemView.visibility = View.INVISIBLE // Hide empty cells completely
                holder.itemView.setOnClickListener(null)
            } else {
                holder.itemView.visibility = View.VISIBLE
                holder.tvDay.visibility = View.VISIBLE
                holder.tvDay.text = item.day.toString()

                // Today Highlight
                if (item.isToday) {
                    holder.todayBg.visibility = View.VISIBLE
                    holder.tvDay.setTextColor(requireContext().getColor(R.color.primary))
                } else {
                    holder.todayBg.visibility = View.INVISIBLE
                    holder.tvDay.setTextColor(requireContext().getColor(R.color.text_primary))
                }

                // Status Dot
                if (item.status != Status.NONE) {
                    holder.dot.visibility = View.VISIBLE
                    val drawableRes = when(item.status) {
                        Status.GREEN -> R.drawable.shape_circle_green
                        Status.RED -> R.drawable.shape_circle_red
                        Status.YELLOW -> R.drawable.shape_circle_yellow
                        else -> 0 
                    }
                    holder.dot.setBackgroundResource(drawableRes)
                } else {
                    // No Data / Future
                    holder.dot.visibility = View.INVISIBLE
                }
                
                // Click Listener (Always allow click if it's a valid day)
                holder.itemView.setOnClickListener {
                    showDetailDialog(item)
                }
            }
        }

        override fun getItemCount() = items.size
    }

    class CalendarViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val dot: View = view.findViewById(R.id.view_status_dot)
        val tvDay: TextView = view.findViewById(R.id.tv_day_number)
        val todayBg: View = view.findViewById(R.id.view_today_bg)
    }
    
    private fun showDetailDialog(item: CalendarItem) {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_day_detail, null)
        
        val dateText = if (item.fullDate.isNotEmpty()) item.fullDate else "Day ${item.day}"
        view.findViewById<TextView>(R.id.tv_detail_date).text = dateText // e.g. 2026-01-15
        
        if (item.record != null) {
            view.findViewById<TextView>(R.id.tv_detail_spend).text = "Spent: ₹${item.record.spend.toInt()}"
            view.findViewById<TextView>(R.id.tv_detail_limit).text = "Limit: ₹${item.record.limit.toInt()}"
            
            val statusText = if (item.record.spend > item.record.limit) "Over Limit" else "Under Limit"
            val statusColor = if (item.record.spend > item.record.limit) requireContext().getColor(R.color.danger_red) else requireContext().getColor(R.color.success_green)
            
            val tvStatus = view.findViewById<TextView>(R.id.tv_detail_status)
            tvStatus.text = statusText
            tvStatus.setTextColor(statusColor)
            tvStatus.visibility = View.VISIBLE
        } else {
             view.findViewById<TextView>(R.id.tv_detail_spend).text = "No recorded data"
             view.findViewById<TextView>(R.id.tv_detail_limit).text = ""
             view.findViewById<TextView>(R.id.tv_detail_status).visibility = View.GONE
        }

        dialog.setContentView(view)
        dialog.show()
    }
}
