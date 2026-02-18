import java.util.regex.Pattern

fun main() {
    val AMOUNT_PATTERN = Pattern.compile("(?i)(?:₹|INR|Rs\\.?)\\s*([\\d,]+(?:\\.\\d{1,2})?)")
    
    val testCases = listOf(
        "Rs500 sent to ----",
        "Rs 500 sent to ----",
        "Rs. 500 sent to ----",
        "₹500 sent to ----",
        "INR 500 sent to ----"
    )
    
    testCases.forEach { text ->
        val matcher = AMOUNT_PATTERN.matcher(text)
        if (matcher.find()) {
            val amount = matcher.group(1)?.replace(",", "")
            println("✓ '$text' -> Amount: $amount")
        } else {
            println("✗ '$text' -> NO MATCH")
        }
    }
    
    // Test keyword matching
    val POSITIVE_KEYWORDS = listOf("paid", "sent", "transfer", "debited", "successful", "completed")
    val testText = "Rs500 sent to ----".lowercase()
    val hasKeyword = POSITIVE_KEYWORDS.any { testText.contains(it) }
    println("\nKeyword 'sent' found: $hasKeyword")
}
