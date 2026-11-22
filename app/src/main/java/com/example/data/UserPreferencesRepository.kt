package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "project_pi_prefs")

class UserPreferencesRepository(private val context: Context) {

  private object Keys {
    val JOINED_CHANNELS = stringSetPreferencesKey("joined_channels")
  }

  val joinedChannelIds: Flow<Set<String>> =
    context.dataStore.data.map { prefs -> prefs[Keys.JOINED_CHANNELS] ?: emptySet() }

  suspend fun toggleChannelJoined(channelId: String) {
    context.dataStore.edit { prefs ->
      val current = prefs[Keys.JOINED_CHANNELS] ?: emptySet()
      prefs[Keys.JOINED_CHANNELS] =
        if (channelId in current) current - channelId else current + channelId
    }
  }
}
