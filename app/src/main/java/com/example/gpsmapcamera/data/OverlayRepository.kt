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
    private val titleKey = stringPreferencesKey("title")
    private val addressKey = stringPreferencesKey("address")
    private val latLongKey = stringPreferencesKey("lat_long")
    private val dateTimeKey = stringPreferencesKey("date_time")
    private val mapUriKey = stringPreferencesKey("map_uri")

    val configFlow: Flow<OverlayConfig> = context.dataStore.data.map { prefs ->
        OverlayConfig(
            title = prefs[titleKey] ?: "",
            address = prefs[addressKey] ?: "",
            latLong = prefs[latLongKey] ?: "",
            dateTime = prefs[dateTimeKey] ?: "",
            mapUri = prefs[mapUriKey] ?: ""
        )
    }

    suspend fun save(config: OverlayConfig) {
        context.dataStore.edit { prefs ->
            prefs[titleKey] = config.title
            prefs[addressKey] = config.address
            prefs[latLongKey] = config.latLong
            prefs[dateTimeKey] = config.dateTime
            prefs[mapUriKey] = config.mapUri
        }
    }
}
