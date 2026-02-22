package com.finetract

import android.content.Context
import android.content.SharedPreferences

class PreferenceManager(context: Context) {
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "finetrack_prefs"
        private const val KEY_TERMS_ACCEPTED = "terms_accepted"
    }

    fun isTermsAccepted(): Boolean {
        return sharedPreferences.getBoolean(KEY_TERMS_ACCEPTED, false)
    }

    fun setTermsAccepted(accepted: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_TERMS_ACCEPTED, accepted).apply()
    }
}
