package com.finetract.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class PrivacyRepository @Inject constructor(@ApplicationContext private val context: Context) {

    private val IS_PRIVACY_MODE = booleanPreferencesKey("is_privacy_mode")

    val isPrivacyMode: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[IS_PRIVACY_MODE] ?: false
        }

    suspend fun setPrivacyMode(isPrivate: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_PRIVACY_MODE] = isPrivate
        }
    }

    suspend fun togglePrivacyMode() {
        context.dataStore.edit { preferences ->
            val current = preferences[IS_PRIVACY_MODE] ?: false
            preferences[IS_PRIVACY_MODE] = !current
        }
    }
}
