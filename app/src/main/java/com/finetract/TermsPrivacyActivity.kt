package com.finetract

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme

class TermsPrivacyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val preferenceManager = PreferenceManager(this)

        setContent {
            // Apply a basic Material3 theme
            MaterialTheme(
                colorScheme = lightColorScheme()
            ) {
                TermsPrivacyScreen(
                    onAccept = {
                        preferenceManager.setTermsAccepted(true)
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    },
                    onDecline = {
                        finishAffinity() // Close the app
                    }
                )
            }
        }
    }
}
