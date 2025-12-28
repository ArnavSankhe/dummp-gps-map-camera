package com.example.gpsmapcamera.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("overlay_prefs")

class OverlayRepository(private val context: Context) {
    private val detailsKey = stringPreferencesKey("details")
    private val mapUriKey = stringPreferencesKey("map_uri")

    val configFlow: Flow<OverlayConfig> = context.dataStore.data.map { prefs ->
        OverlayConfig(
            details = prefs[detailsKey] ?: "",
            mapUri = prefs[mapUriKey] ?: ""
        )
    }

    suspend fun save(config: OverlayConfig) {
        context.dataStore.edit { prefs ->
            prefs[detailsKey] = config.details
            prefs[mapUriKey] = config.mapUri
        }
    }
}
