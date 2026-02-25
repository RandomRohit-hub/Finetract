package com.finetract

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.finetract.domain.BudgetAlertState
import com.finetract.domain.BudgetAlertUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext context: Context,
    budgetAlertUseCase: BudgetAlertUseCase
) : ViewModel() {

    // Note: TransactionManager still holds the limit in SharedPreferences in current implementation
    // Ideally this would move to SettingsRepository, but for now we fetch it once or periodically
    private val dailyLimit = TransactionManager.getDailyLimit(context).toDouble()

    val budgetAlert: StateFlow<BudgetAlertState> = budgetAlertUseCase
        .observe(todayStartMillis(), dailyLimit)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = BudgetAlertState.INITIAL
        )

    private fun todayStartMillis(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
