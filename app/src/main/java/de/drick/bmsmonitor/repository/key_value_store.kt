package de.drick.bmsmonitor.repository

import android.content.Context
import android.content.SharedPreferences
import de.drick.log
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

//inline fun <reified T>SharedPreferences.keyValueStorage() = KeyValuePrefStorage(serializer<T>(), this)
inline fun <reified T> Context.keyValuePrefStorage(sharedPreferencesName: String) =
    KeyValuePrefStorage(serializer<T>(), getSharedPreferences(sharedPreferencesName, Context.MODE_PRIVATE))

class KeyValuePrefStorage<T>(
    private val serializer: KSerializer<T>,
    val prefs: SharedPreferences,
) {
    val json = Json

    fun save(key: String, value: T) {
        val jsonStr = json.encodeToString(serializer, value)
        prefs.edit().putString(key, jsonStr).apply()
    }
    fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }
    fun load(key: String): T? {
        return try {
            val jsonStr: String? = prefs.getString(key, null)
            if (jsonStr != null)
                json.decodeFromString(serializer, jsonStr)
            else
                null
        } catch (err: Throwable) {
            log(err)
            null
        }
    }

    fun loadAll(): List<T> = prefs.all.keys.mapNotNull { load(it) }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
