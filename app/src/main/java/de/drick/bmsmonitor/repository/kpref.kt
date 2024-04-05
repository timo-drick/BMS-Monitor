package de.drick.bmsmonitor.repository

import android.content.SharedPreferences

interface PrefType<T> {
    val prefs: SharedPreferences
    val key: String
    val defaultValue: T
    fun load(): T
    fun save(value: T)
    fun clear() {
        prefs.edit().clear().apply()
    }
}

fun SharedPreferences.string(key: String, initValue: String? = null): PrefType<String?> =
    KStringNAPref(this, key, initValue)

private class KStringNAPref(
    override val prefs: SharedPreferences,
    override val key: String,
    override val defaultValue: String? = null
) : PrefType<String?> {
    override fun load() = prefs.getString(key, null)
    override fun save(value: String?) = prefs.edit().putString(key, value).apply()
}

fun SharedPreferences.long(key: String, defaultValue: Long): PrefType<Long> =
    KLongPref(this, key, defaultValue)


private class KLongPref(
    override val prefs: SharedPreferences,
    override val key: String,
    override val defaultValue: Long
) : PrefType<Long> {
    override fun load() = prefs.getLong(key, defaultValue)
    override fun save(value: Long) = prefs.edit().putLong(key, value).apply()
}


fun SharedPreferences.boolean(key: String, defaultValue: Boolean): PrefType<Boolean> =
    KBooleanPref(this, key, defaultValue)

private class KBooleanPref(
    override val prefs: SharedPreferences,
    override val key: String,
    override val defaultValue: Boolean
) : PrefType<Boolean> {
    override fun load() = prefs.getBoolean(key, defaultValue)
    override fun save(value: Boolean) = prefs.edit().putBoolean(key, value).apply()
}

inline fun <reified T: Enum<T>> SharedPreferences.enumAsString(key: String, defaultValue: T): KEnumPref<T> =
    KEnumPref(this, key, defaultValue, enumValues<T>())

class KEnumPref<T: Enum<T>>(
    override val prefs: SharedPreferences,
    override val key: String,
    override val defaultValue: T,
    enumValues: Array<T>
) : PrefType<Enum<T>> {
    private val map = enumValues.associateBy { it.name }
    override fun load(): Enum<T> = map[prefs.getString(key, null)] ?: defaultValue
    override fun save(value: Enum<T>) {
        prefs.edit().putString(key, value.name).apply()
    }
}

fun <T: PrefEnumId> SharedPreferences.enumWithId(key: String, defaultValue: T, possibleValues: Array<T>): KEnumIdPref<T> =
    KEnumIdPref(this, key, defaultValue, possibleValues)

interface PrefEnumId {
    val id: String
}

class KEnumIdPref<T: PrefEnumId>(
    override val prefs: SharedPreferences,
    override val key: String,
    override val defaultValue: T,
    enumValues: Array<T>
) : PrefType<T> {
    private val map = enumValues.associateBy { it.id }
    override fun load(): T = map[prefs.getString(key, null)] ?: defaultValue
    override fun save(value: T) {
        prefs.edit().putString(key, value.id).apply()
    }
}


interface StringSetPref {
    val key: String
    fun load(): Set<String>
    fun add(element: String)
    fun addAll(element: List<String>)
    fun remove(element: String)
    fun clear()
}

fun SharedPreferences.stringSet(key: String, defaultValue: Set<String>): StringSetPref =
    KStringSetPref(this, key, defaultValue)

private class KStringSetPref(
    private val prefs: SharedPreferences,
    override val key: String,
    val defaultValue: Set<String>
) : StringSetPref {
    override fun load(): Set<String> = prefs.getStringSet(key, defaultValue) ?: defaultValue
    override fun add(element: String) {
        val existingEntry = load()
        val newSet = existingEntry + element
        prefs.edit().putStringSet(key, newSet).apply()
    }
    override fun addAll(element: List<String>) {
        val existingEntry = load()
        val newSet = existingEntry + element
        prefs.edit().putStringSet(key, newSet).apply()
    }

    override fun remove(element: String) {
        val existingEntry = load()
        val newSet = existingEntry - element
        prefs.edit().putStringSet(key, newSet).apply()
    }

    override fun clear() {
        prefs.edit().remove(key).apply()
    }
}
