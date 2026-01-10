package com.finetract

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val etLimit = findViewById<EditText>(R.id.et_limit)
        val etThreshold = findViewById<EditText>(R.id.et_large_threshold)
        val btnSave = findViewById<Button>(R.id.btn_save_limit)

        // Pre-fill current values
        val currentLimit = TransactionManager.getDailyLimit(this)
        etLimit.setText(currentLimit.toString())

        val currentThreshold = TransactionManager.getLargePaymentThreshold(this)
        if (currentThreshold > 0) {
            etThreshold.setText(currentThreshold.toString())
        }

        btnSave.setOnClickListener {
            val limitStr = etLimit.text.toString()
            val thresholdStr = etThreshold.text.toString()

            if (limitStr.isNotEmpty()) {
                val limit = limitStr.toFloatOrNull()
                if (limit != null) {
                    TransactionManager.setDailyLimit(this, limit)
                }
            }

            if (thresholdStr.isNotEmpty()) {
                val threshold = thresholdStr.toFloatOrNull()
                if (threshold != null) {
                    TransactionManager.setLargePaymentThreshold(this, threshold)
                }
            } else {
                TransactionManager.setLargePaymentThreshold(this, 0f)
            }
            
            Toast.makeText(this, "Settings Saved!", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
