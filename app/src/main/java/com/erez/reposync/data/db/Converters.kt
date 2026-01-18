package com.erez.reposync.data.db

import androidx.room.TypeConverter

object Converters {
    @TypeConverter
    fun fromIgnorePatterns(patterns: List<String>?): String? {
        return patterns?.joinToString("\n")
    }

    @TypeConverter
    fun toIgnorePatterns(value: String?): List<String> {
        if (value.isNullOrBlank()) return emptyList()
        return value.split("\n")
    }
}
