package com.tw1st.mahreader.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ReadingTheme(val displayName: String) {
    AMOLED_DARK("AMOLED Black"),
    EYE_STRAIN_AMBER("Eye Strain Amber"),
    SOFT_CHARCOAL("Soft Charcoal"),
    ORIGINAL_LIGHT("Original Light")
}

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val uriString: String,
    val title: String,
    val totalPages: Int = 0,
    val lastReadPage: Int = 0,
    val lastReadTimestamp: Long = System.currentTimeMillis(),
    val themeName: String = ReadingTheme.AMOLED_DARK.name,
    val isContinuousScroll: Boolean = true
) {
    fun progressPercentage(): Int {
        if (totalPages <= 0) return 0
        val percent = ((lastReadPage + 1).toFloat() / totalPages * 100).toInt()
        return percent.coerceIn(0, 100)
    }
}
