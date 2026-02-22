package com.finetract

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TermsPrivacyScreen(
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            // Header
            Text(
                text = "Welcome to Finetrack",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Text(
                text = "Security & Privacy Policy",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp)
            ) {
                PolicySection(
                    title = "Data Isolation & Protection",
                    content = "• Financial data from notifications is stored only in the app's private internal storage.\n" +
                            "• No external servers.\n" +
                            "• No third-party sharing.\n" +
                            "• Data remains on your device."
                )

                Spacer(modifier = Modifier.height(24.dp))

                PolicySection(
                    title = "Permission Transparency",
                    content = "• Finetrack requires Notification Access to automatically track your expenses from SMS and app alerts.\n" +
                            "• This permission is used only for automated financial tracking.\n" +
                            "• No unrelated notifications (messages from friends, social media, etc.) are collected.\n" +
                            "• You can revoke this permission anytime from your device settings."
                )
            }

            // Buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = onAccept,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("Accept & Continue", fontSize = 16.sp)
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(
                    onClick = onDecline,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Decline", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun PolicySection(title: String, content: String) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = content,
            style = MaterialTheme.typography.bodyLarge,
            lineHeight = 24.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
