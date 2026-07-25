package com.elimtiyaz.data.local

import androidx.room.TypeConverter
import kotlinx.datetime.LocalDate
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Room type converters for the few non-primitive fields used by entities.
 *
 * - [LocalDate] is stored as its ISO-8601 date string.
 * - `List<String>` is stored as a JSON array string.
 * - `Map<String,String>` is stored as a JSON object string (used by the sync
 *   queue's payload column when an update carries a partial diff).
 */
class Converters {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val listSerializer = ListSerializer(String.serializer())
    private val mapSerializer = MapSerializer(String.serializer(), String.serializer())

    /** Encode a [LocalDate] as ISO `yyyy-MM-dd`. */
    @TypeConverter
    fun fromLocalDate(date: LocalDate?): String? = date?.toString()

    /** Decode a [LocalDate] from ISO `yyyy-MM-dd`. */
    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? = value?.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) }

    /** Encode a string list as a JSON array. */
    @TypeConverter
    fun fromStringList(value: List<String>?): String =
        if (value == null) "[]" else json.encodeToString(listSerializer, value)

    /** Decode a string list from a JSON array. */
    @TypeConverter
    fun toStringList(value: String?): List<String> =
        if (value.isNullOrBlank()) emptyList() else json.decodeFromString(listSerializer, value)

    /** Encode a string map as a JSON object. */
    @TypeConverter
    fun fromStringMap(value: Map<String, String>?): String =
        if (value == null) "{}" else json.encodeToString(mapSerializer, value)

    /** Decode a string map from a JSON object. */
    @TypeConverter
    fun toStringMap(value: String?): Map<String, String> =
        if (value.isNullOrBlank()) emptyMap() else json.decodeFromString(mapSerializer, value)
}
