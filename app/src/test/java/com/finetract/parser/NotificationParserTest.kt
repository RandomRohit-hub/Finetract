package com.finetract.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class NotificationParserTest {

    private val parser = NotificationParser()
    private val gpay = "com.google.android.apps.nbu.paisa.user"
    private val phonepe = "com.phonepe.app"
    private val paytm = "net.one97.paytm"
    private val hdfc = "com.hdfc.bank" // example package

    @Test
    fun testUpiDebit_patterns() {
        val msg1 = "Rs.499.00 debited from your account via UPI. UPI Ref: 123456789012."
        val p1 = parser.parse(gpay, "UPI Payment", msg1)
        assertNotNull(p1)
        assertEquals(499.0, p1!!.amount, 0.01)
        assertEquals(TransactionType.DEBIT, p1.type)

        val msg2 = "₹1,250 paid to Swiggy via Google Pay. UPI ID: swiggy@icici. Transaction ID: GPay123456."
        val p2 = parser.parse(gpay, "GPay", msg2)
        assertNotNull(p2)
        assertEquals(1250.0, p2!!.amount, 0.01)
        assertEquals(TransactionType.DEBIT, p2.type)
        assertEquals("Swiggy", p2.description)

        val msg3 = "Dear Customer, INR 349.00 has been debited from A/c XX4521 for UPI txn. Merchant: Zomato. Avl Bal: ₹12,450."
        val p3 = parser.parse(gpay, "Bank Alert", msg3)
        assertNotNull(p3)
        assertEquals(349.0, p3!!.amount, 0.01)
        assertEquals(TransactionType.DEBIT, p3.type)
        assertEquals("Zomato", p3.description)

        val msg4 = "PhonePe: ₹850 sent to Ramesh Kumar (ramesh@ybl) on 25-02-2026. Ref No: P2526XXXXXXX."
        val p4 = parser.parse(phonepe, "Payment", msg4)
        assertNotNull(p4)
        assertEquals(850.0, p4!!.amount, 0.01)
        assertEquals(TransactionType.DEBIT, p4.type)
        assertEquals("Ramesh Kumar", p4.description)

        val msg5 = "Paytm: Payment of Rs. 120 successful to Auto Rickshaw. Transaction ID: PTM2526XXXXXX."
        val p5 = parser.parse(paytm, "Paytm", msg5)
        assertNotNull(p5)
        assertEquals(120.0, p5!!.amount, 0.01)
        assertEquals(TransactionType.DEBIT, p5.type)
        assertEquals("Auto Rickshaw", p5.description)
    }

    @Test
    fun testBankDebit_patterns() {
        val msg1 = "HDFC Bank: Rs.2,500.00 debited from a/c XX7823 on 25-02-26. Info: POS/Amazon. Avl Bal: Rs.8,340.00."
        val p1 = parser.parse(hdfc, "HDFC Alert", msg1)
        assertNotNull(p1)
        assertEquals(2500.0, p1!!.amount, 0.01)
        assertEquals(TransactionType.DEBIT, p1.type)
        assertEquals("POS/Amazon", p1.description)

        val msg2 = "ICICI Bank: Your A/c XX1234 is debited with INR 670.00 on 25-FEB-2026. Info: ELECTRICITY BILL. Available balance INR 15,230.00."
        val p2 = parser.parse("com.icici.bank", "ICICI Alert", msg2)
        assertNotNull(p2)
        assertEquals(670.0, p2!!.amount, 0.01)
        assertEquals(TransactionType.DEBIT, p2.type)
        assertEquals("ELECTRICITY BILL", p2.description)

        val msg3 = "SBI: Dear Customer, your account XX9876 has been debited by Rs.3,200 on 25/02/2026 towards IRCTC booking."
        val p3 = parser.parse("com.sbi.bank", "SBI Alert", msg3)
        assertNotNull(p3)
        assertEquals(3200.0, p3!!.amount, 0.01)
        assertEquals(TransactionType.DEBIT, p3.type)
        assertEquals("IRCTC booking", p3.description)
    }

    @Test
    fun testBankCredit_patterns() {
        val msg1 = "HDFC Bank: Rs.25,000.00 credited to a/c XX7823 on 25-02-26. Info: SALARY. Avl Bal: Rs.33,340.00."
        val p1 = parser.parse(hdfc, "HDFC Alert", msg1)
        assertNotNull(p1)
        assertEquals(25000.0, p1!!.amount, 0.01)
        assertEquals(TransactionType.CREDIT, p1.type)
        assertEquals("SALARY", p1.description)

        val msg2 = "ICICI Bank: INR 500 refund credited to your A/c XX1234 from Swiggy on 25-FEB-2026. Avl Bal: INR 15,730."
        val p2 = parser.parse("com.icici.bank", "ICICI Alert", msg2)
        assertNotNull(p2)
        assertEquals(500.0, p2!!.amount, 0.01)
        assertEquals(TransactionType.CREDIT, p2.type)
        assertEquals("Swiggy", p2.description)

        val msg3 = "PhonePe: ₹200 received from Ankit Sharma (ankit@paytm). Ref: P2526YYYYYYY."
        val p3 = parser.parse(phonepe, "PhonePe", msg3)
        assertNotNull(p3)
        assertEquals(200.0, p3!!.amount, 0.01)
        assertEquals(TransactionType.CREDIT, p3.type)
        assertEquals("Ankit Sharma", p3.description)
    }

    @Test
    fun testEdgeCases() {
        val msg1 = "Your account has been debited. Amount: 2500. For: Apollo Pharmacy. Date: 25/02/2026."
        val p1 = parser.parse(hdfc, "Alert", msg1)
        assertNotNull(p1)
        assertEquals(2500.0, p1!!.amount, 0.01)
        assertEquals(TransactionType.DEBIT, p1.type)
        assertEquals("Apollo Pharmacy", p1.description)

        val msg2 = "Rs.75 debited via UPI on 25-02-2026. Ref No: 112233445566."
        val p2 = parser.parse(gpay, "UPI", msg2)
        assertNotNull(p2)
        assertEquals(75.0, p2!!.amount, 0.01)
        assertEquals(TransactionType.DEBIT, p2.type)

        val msg3 = "Rs.1,000 cashback credited. Previous debit of Rs.1,000 at Myntra reversed. A/c XX4321. Bal: Rs.18,200."
        val p3 = parser.parse(hdfc, "Cashback", msg3)
        assertNotNull(p3)
        assertEquals(1000.0, p3!!.amount, 0.01)
        assertEquals(TransactionType.CREDIT, p3.type)

        val msg4 = "ICICI Bank: INR 1,25,000.00 debited from A/c XX8899 on 25-02-2026. Info: HOME LOAN"
        val p4 = parser.parse("com.icici.bank", "Alert", msg4)
        assertNotNull(p4)
        assertEquals(125000.0, p4!!.amount, 0.01)
        assertEquals(TransactionType.DEBIT, p4.type)
        assertEquals("HOME LOAN", p4.description)
    }
}
