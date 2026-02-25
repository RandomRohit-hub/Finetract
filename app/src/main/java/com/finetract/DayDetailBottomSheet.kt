package com.finetract

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.finetract.TransactionManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DayDetailBottomSheet : BottomSheetDialogFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.bottom_sheet_day_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val transactions = TransactionManager.getTodayTransactions(requireContext())
        val total = transactions.sumOf { it.amount.toDouble() }.toFloat()

        view.findViewById<TextView>(R.id.tv_detail_total).text = "₹${total.toInt()} spent"

        val rv = view.findViewById<RecyclerView>(R.id.rv_today_transactions)
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = TodayTxnAdapter(transactions)
    }

    inner class TodayTxnAdapter(private val items: List<TransactionManager.TransactionRecord>) : RecyclerView.Adapter<TodayTxnViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TodayTxnViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_transaction_today, parent, false)
            return TodayTxnViewHolder(v)
        }

        override fun onBindViewHolder(holder: TodayTxnViewHolder, position: Int) {
            val item = items[position]
            holder.tvMerchant.text = item.merchant
            holder.tvAmount.text = "₹${item.amount.toInt()}"
            holder.tvTime.text = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(item.timestamp))
        }

        override fun getItemCount() = items.size
    }

    class TodayTxnViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvMerchant: TextView = view.findViewById(R.id.tv_item_merchant)
        val tvTime: TextView = view.findViewById(R.id.tv_item_time)
        val tvAmount: TextView = view.findViewById(R.id.tv_item_amount)
    }
}
