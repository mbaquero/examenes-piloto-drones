package com.examenesdrones.app.data

import androidx.room.withTransaction
import com.examenesdrones.app.data.db.AppDatabase
import com.examenesdrones.app.data.db.BookmarkEntity
import com.examenesdrones.app.data.db.ReadingProgressEntity

/** Reading positions and bookmarks, keyed by stable topic keys so they survive re-imports. */
class UserRepository(private val db: AppDatabase, private val clock: () -> Long = System::currentTimeMillis) {
    private val dao = db.userDao()

    val readingProgress = dao.observeReading()
    val lastRead = dao.observeLastRead()
    val bookmarks = dao.observeBookmarks()
    fun bookmarks(topicKey: String) = dao.observeBookmarks(topicKey)

    suspend fun reading(topicKey: String) = dao.reading(topicKey)

    // Read-modify-write updates run in transactions so a scroll save never overwrites "completed".
    suspend fun saveReading(topicKey: String, first: Int, offset: Int, lastVisible: Int, total: Int) = db.withTransaction {
        val old = dao.reading(topicKey)
        val maxSeen = maxOf(old?.maxSeenIndex ?: 0, lastVisible)
        dao.upsertReading(
            ReadingProgressEntity(
                topicKey = topicKey, firstVisibleIndex = first, scrollOffset = offset, maxSeenIndex = maxSeen,
                totalBlocks = total, completed = old?.completed ?: false, updatedAt = clock(),
            )
        )
    }

    suspend fun setCompleted(topicKey: String, completed: Boolean, total: Int) = db.withTransaction {
        val old = dao.reading(topicKey)
        dao.upsertReading(
            (old ?: ReadingProgressEntity(topicKey, 0, 0, 0, total, false, clock()))
                .copy(completed = completed, totalBlocks = total, updatedAt = clock(),
                    maxSeenIndex = if (completed) maxOf(total - 1, old?.maxSeenIndex ?: 0) else old?.maxSeenIndex ?: 0)
        )
    }

    suspend fun toggleBookmark(topicKey: String, position: Int, excerpt: String, add: Boolean) {
        if (add) dao.addBookmark(BookmarkEntity(topicKey = topicKey, blockPosition = position, excerpt = excerpt.take(160), createdAt = clock()))
        else dao.removeBookmark(topicKey, position)
    }
}
