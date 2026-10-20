package com.examenesdrones.app

import android.content.Context
import com.examenesdrones.app.data.BundledContentLoader
import com.examenesdrones.app.data.ContentRepository
import com.examenesdrones.app.data.ExportManager
import com.examenesdrones.app.data.SettingsRepository
import com.examenesdrones.app.data.StudyRepository
import com.examenesdrones.app.data.UserRepository
import com.examenesdrones.app.data.db.AppDatabase
import kotlinx.serialization.json.Json

/** Manual dependency container shared by all screens. */
class AppContainer(context: Context) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    val database: AppDatabase = AppDatabase.build(context)
    val settings = SettingsRepository(context)
    val content = ContentRepository(context, database, json)
    val study = StudyRepository(database, content, json)
    val user = UserRepository(database)
    val export = ExportManager(context, study)
    val bundledContent = BundledContentLoader(context, content)
}
