package com.tw1st.mahreader.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY lastReadTimestamp DESC")
    fun getAllBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE uriString = :uriString LIMIT 1")
    suspend fun getBookByUri(uriString: String): BookEntity?

    @Query("SELECT * FROM books WHERE id = :id LIMIT 1")
    suspend fun getBookById(id: Int): BookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(book: BookEntity): Long

    @Query("UPDATE books SET lastReadPage = :page, lastReadTimestamp = :timestamp WHERE id = :id")
    suspend fun updateProgress(id: Int, page: Int, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE books SET themeName = :theme WHERE id = :id")
    suspend fun updateTheme(id: Int, theme: String)

    @Query("UPDATE books SET isContinuousScroll = :isContinuous WHERE id = :id")
    suspend fun updateScrollMode(id: Int, isContinuous: Boolean)

    @Delete
    suspend fun deleteBook(book: BookEntity)
}
