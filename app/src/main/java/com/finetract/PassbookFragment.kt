package com.finetract

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class PassbookFragment : Fragment(R.layout.fragment_passbook) {

    private lateinit var adapter: PassbookAdapter
    private var allRecords: List<TransactionManager.DailyRecord> = emptyList()
    private var currentFilter: String = "Today"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setup Recycler
        val rv = view.findViewById<RecyclerView>(R.id.rv_passbook)
        rv.layoutManager = LinearLayoutManager(context)
        adapter = PassbookAdapter()
        rv.adapter = adapter

        // Load Data
        allRecords = TransactionManager.getHistory(requireContext()).reversed() // Most recent first
        
        // Listeners
        view.findViewById<View>(R.id.btn_back).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // Filter Click - Open Menu
        view.findViewById<View>(R.id.btn_filter).setOnClickListener {
            showFilterMenu(it)
        }
        
        // Remove card listener as we now use the icon
        view.findViewById<View>(R.id.card_filter).visibility = View.GONE 
        
        view.findViewById<View>(R.id.btn_export_csv).setOnClickListener {
            exportCsv()
        }

        // Default to Today
        applyFilter("Today")
    }

    private fun showFilterMenu(anchor: View) {
        val popup = android.widget.PopupMenu(requireContext(), anchor)
        popup.menu.add("Select Month")
        popup.menu.add("Last Month")
        popup.menu.add("Last 2 Months")
        popup.menu.add("Last 3 Months")
        popup.menu.add("Last 6 Months")
        
        popup.setOnMenuItemClickListener { item ->
            if (item.title == "Select Month") {
                showMonthSelector()
            } else {
                applyFilter(item.title.toString())
            }
            true
        }
        popup.show()
    }
    
    private fun showMonthSelector() {
        val months = arrayOf(
            "January", "February", "March", "April", "May", "June", 
            "July", "August", "September", "October", "November", "December"
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Select Month")
            .setItems(months) { _, which ->
                applyFilter(months[which])
            }
            .show()
    }

    private fun applyFilter(filter: String) {
        currentFilter = filter
        // We removed the card text update since we hid the card, 
        // but let's make sure the user knows what they see. 
        // Maybe toast or just reliance on the list content?
        // Requirement says "Default to Today". 
        
        val filtered = when {
            filter == "Today" -> filterToday()
            filter.startsWith("Last") -> filterByPreset(filter)
            else -> filterByMonthName(filter)
        }
        
        adapter.submitList(filtered)
        
        val emptyView = view?.findViewById<TextView>(R.id.tv_empty_state)
        if (filtered.isEmpty()) {
            emptyView?.text = "No records available for this period."
            emptyView?.visibility = View.VISIBLE
        } else {
            emptyView?.visibility = View.GONE
        }
    }
    
    private fun filterToday(): List<TransactionManager.DailyRecord> {
        // "Today" means current day. 
        // Check if today is in history first.
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val hist = allRecords.find { it.date == todayStr }
        
        if (hist != null) return listOf(hist)
        
        // If not in saved history (because it archives at midnight), read live data
        // But the requirement says "Show the record for the CURRENT DAY".
        // Passbook usually shows history. If "Today" isn't archived yet, we should construct it live.
        val context = requireContext()
        val spend = TransactionManager.getTodaySpend(context)
        val limit = TransactionManager.getDailyLimit(context)
        // Only show if there's non-zero spend or just always show today's status?
        // Let's show it as a record.
        return listOf(TransactionManager.DailyRecord(todayStr, spend, limit))
    }

    private fun filterByPreset(filter: String): List<TransactionManager.DailyRecord> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        val today = cal.time
        
        // Calculate start date
        when(filter) {
            "Last Month" -> cal.add(Calendar.MONTH, -1)
            "Last 2 Months" -> cal.add(Calendar.MONTH, -2)
            "Last 3 Months" -> cal.add(Calendar.MONTH, -3)
            "Last 6 Months" -> cal.add(Calendar.MONTH, -6)
        }
        
        val cutoff = cal.time
        return allRecords.filter { checkDateRange(it.date, cutoff, today) }
    }

    private fun filterByMonthName(monthName: String): List<TransactionManager.DailyRecord> {
        val cal = Calendar.getInstance()
        val currentYear = cal.get(Calendar.YEAR)
        
        val monthIdx = when(monthName) {
            "January" -> 0; "February" -> 1; "March" -> 2; "April" -> 3; "May" -> 4; "June" -> 5
            "July" -> 6; "August" -> 7; "September" -> 8; "October" -> 9; "November" -> 10; "December" -> 11
            else -> return emptyList()
        }
        
        return allRecords.filter {
             val parts = it.date.split("-")
             if (parts.size >= 2) {
                 val recYear = parts[0].toIntOrNull() ?: 0
                 val recMonth = (parts[1].toIntOrNull() ?: 1) - 1 
                 recYear == currentYear && recMonth == monthIdx
             } else false
        }
    }
    
    private fun checkDateRange(dateStr: String, start: Date, end: Date): Boolean {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val d = sdf.parse(dateStr) ?: return false
        // Inclusive check
        return !d.before(start) && !d.after(end)
    }
    
    private fun exportCsv() {
        if (adapter.items.isEmpty()) return
        
        val sb = StringBuilder()
        sb.append("Date,Spend,Limit,Status\n")
        
        for (item in adapter.items) {
            val status = if (item.spend > item.limit) "OVER LIMIT" else "UNDER LIMIT"
            sb.append("${item.date},${item.spend},${item.limit},$status\n")
        }
        
        val filename = "Finetrack_History.csv"
        val file = File(requireContext().cacheDir, filename)
        file.writeText(sb.toString())
        
        val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", file)
        
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "text/csv"
        intent.putExtra(Intent.EXTRA_STREAM, uri)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        
        startActivity(Intent.createChooser(intent, "Export History"))
    }


    // --- Adapter ---
    
    inner class PassbookAdapter : RecyclerView.Adapter<PassbookViewHolder>() {
        var items = listOf<TransactionManager.DailyRecord>()
        
        fun submitList(newItems: List<TransactionManager.DailyRecord>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PassbookViewHolder {
            val v = layoutInflater.inflate(R.layout.item_passbook_record, parent, false)
            return PassbookViewHolder(v)
        }

        override fun onBindViewHolder(holder: PassbookViewHolder, position: Int) {
            val item = items[position]
            
            // Format nice date: 2026-01-09 -> 09 Jan 2026
            try {
                val inFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val outFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                val d = inFmt.parse(item.date)
                holder.tvDate.text = if (d != null) outFmt.format(d) else item.date
            } catch (e: Exception) { 
                holder.tvDate.text = item.date 
            }

            holder.tvSpend.text = "₹${item.spend.toInt()}"
            holder.tvLimit.text = "/ ${item.limit.toInt()}"
            
            val isOver = item.spend > item.limit
            holder.tvStatus.text = if (isOver) "OVER LIMIT" else "UNDER LIMIT"
            holder.tvStatus.setTextColor(requireContext().getColor(if (isOver) R.color.danger_red else R.color.success_green))
        }

        override fun getItemCount() = items.size
    }

    class PassbookViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvDate: TextView = view.findViewById(R.id.tv_record_date)
        val tvStatus: TextView = view.findViewById(R.id.tv_record_status)
        val tvSpend: TextView = view.findViewById(R.id.tv_record_spend)
        val tvLimit: TextView = view.findViewById(R.id.tv_record_limit)
    }
}
