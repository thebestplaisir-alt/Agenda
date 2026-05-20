package com.inchios.agenda

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromStringList(value: List<String>?): String? {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toStringList(value: String?): List<String>? {
        val listType = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, listType)
    }

    @TypeConverter
    fun fromStringMap(value: Map<String, Int>?): String? {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toStringMap(value: String?): Map<String, Int>? {
        val mapType = object : TypeToken<Map<String, Int>>() {}.type
        return gson.fromJson(value, mapType)
    }

    @TypeConverter
    fun fromDate(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun toDate(value: String?): LocalDate? = value?.let { LocalDate.parse(it) }

    @TypeConverter
    fun fromTime(value: LocalTime?): String? = value?.toString()

    @TypeConverter
    fun toTime(value: String?): LocalTime? = value?.let { LocalTime.parse(it) }
}
