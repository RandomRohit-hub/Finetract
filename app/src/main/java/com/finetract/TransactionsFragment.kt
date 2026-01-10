package com.finetract

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class TransactionsFragment : Fragment(R.layout.fragment_transactions) {

    private lateinit var adapter: TxnAdapter
    private var allTxns: List<TransactionManager.TransactionRecord> = emptyList()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rv = view.findViewById<RecyclerView>(R.id.rv_transactions)
        rv.layoutManager = LinearLayoutManager(context)
        adapter = TxnAdapter()
        rv.adapter = adapter

        // Load all
        allTxns = TransactionManager.getTransactions(requireContext()).sortedByDescending { it.timestamp }
        
        // Default: Current Month
        filterByMonth(Calendar.getInstance())

        view.findViewById<View>(R.id.btn_filter_transactions).setOnClickListener {
            showFilterDialog()
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
