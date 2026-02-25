package com.finetract

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import java.util.*

class AddTransactionBottomSheet(private val onTransactionAdded: () -> Unit) : BottomSheetDialogFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.bottom_sheet_add_transaction, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val etAmount = view.findViewById<TextInputEditText>(R.id.et_amount)
        val etDescription = view.findViewById<TextInputEditText>(R.id.et_description)
        val chipGroup = view.findViewById<ChipGroup>(R.id.chip_group_category)
        val btnSave = view.findViewById<Button>(R.id.btn_save_transaction)

        btnSave.setOnClickListener {
            val amountStr = etAmount.text.toString()
            val amount = amountStr.toFloatOrNull() ?: 0f
            
            if (amount > 0) {
                val selectedChipId = chipGroup.checkedChipId
                val category = if (selectedChipId != View.NO_ID) {
                    view.findViewById<Chip>(selectedChipId).text.toString()
                } else {
                    "Other"
                }
                
                val description = etDescription.text.toString().ifEmpty { category }
                
                TransactionManager.addTransaction(
                    context = requireContext(),
                    amount = amount,
                    uniqueId = UUID.randomUUID().toString(),
                    timestamp = System.currentTimeMillis(),
                    merchant = description,
                    rawContent = "Manual Entry: $category"
                )
                
                onTransactionAdded()
                dismiss()
            }
        }
        
        // Auto-focus amount field
        etAmount.requestFocus()
    }
}
