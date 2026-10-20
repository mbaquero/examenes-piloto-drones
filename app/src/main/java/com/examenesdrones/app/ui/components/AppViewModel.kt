package com.examenesdrones.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.examenesdrones.app.AppContainer
import com.examenesdrones.app.ExamApp

@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as ExamApp).container

/** Creates a ViewModel scoped to the current navigation entry, with access to the app container. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(key: String? = null, noinline create: (AppContainer) -> VM): VM {
    val container = appContainer()
    return viewModel(key = key, factory = viewModelFactory { initializer { create(container) } })
}
