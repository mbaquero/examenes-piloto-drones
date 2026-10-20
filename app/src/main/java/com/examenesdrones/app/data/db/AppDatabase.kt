package com.examenesdrones.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
    entities = [
        ImportedDocumentEntity::class, TopicEntity::class, TheoryBlockEntity::class, QuestionEntity::class,
        AnswerOptionEntity::class, PracticeSessionEntity::class, PracticeResponseEntity::class,
        ExamAttemptEntity::class, ExamResponseEntity::class, QuestionStatEntity::class,
        BookmarkEntity::class, ReadingProgressEntity::class,
    ],
    version = AppDatabase.VERSION,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun contentDao(): ContentDao
    abstract fun practiceDao(): PracticeDao
    abstract fun examDao(): ExamDao
    abstract fun statsDao(): StatsDao
    abstract fun userDao(): UserDao

    companion object {
        const val VERSION = 1
        const val NAME = "examenes.db"

        /**
         * Explicit migrations only: user history must never be dropped by a destructive fallback.
         * Add new migrations here when the schema version increases.
         */
        val MIGRATIONS: Array<Migration> = arrayOf()

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
