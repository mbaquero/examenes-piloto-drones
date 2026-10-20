package com.examenesdrones.app

import android.app.LocaleManager
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.examenesdrones.app.data.UserSettings
import com.examenesdrones.app.ui.navigation.AppNavigation
import com.examenesdrones.app.ui.navigation.ContentGate
import com.examenesdrones.app.ui.theme.ExamTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as ExamApp).container
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = UserSettings())
            LaunchedEffect(settings.language) { applyLanguage(settings.language) }
            ExamTheme(settings.theme) { ContentGate { AppNavigation() } }
        }
    }

    private fun applyLanguage(tag: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val manager = getSystemService(LocaleManager::class.java) ?: return
            val wanted = if (tag.isBlank()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
            if (manager.applicationLocales != wanted) manager.applicationLocales = wanted
        }
    }
}
