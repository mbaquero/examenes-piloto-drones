package com.examenesdrones.app.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.examenesdrones.app.R
import com.examenesdrones.app.ui.exam.ExamResultScreen
import com.examenesdrones.app.ui.exam.ExamReviewScreen
import com.examenesdrones.app.ui.exam.ExamSessionScreen
import com.examenesdrones.app.ui.exam.ExamSetupScreen
import com.examenesdrones.app.ui.home.HomeScreen
import com.examenesdrones.app.ui.practice.PracticeSessionScreen
import com.examenesdrones.app.ui.practice.PracticeSetupScreen
import com.examenesdrones.app.ui.practice.PracticeSummaryScreen
import com.examenesdrones.app.ui.progress.ProgressScreen
import com.examenesdrones.app.ui.reader.ReaderScreen
import com.examenesdrones.app.ui.settings.SettingsScreen
import com.examenesdrones.app.ui.topics.TopicsScreen

private data class Tab(val route: String, val label: Int, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, R.string.tab_home, Icons.Outlined.Home),
    Tab(Routes.TOPICS, R.string.tab_topics, Icons.AutoMirrored.Outlined.MenuBook),
    Tab(Routes.PRACTICE_SETUP_WITH, R.string.tab_practice, Icons.Outlined.Quiz),
    Tab(Routes.EXAM_SETUP, R.string.tab_exam, Icons.Outlined.TaskAlt),
    Tab(Routes.PROGRESS, R.string.tab_progress, Icons.Outlined.Insights),
)

fun NavHostController.navigateTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun AppNavigation() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val currentRoute = entry?.destination?.route
    val showBar = tabs.any { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                val target = if (tab.route == Routes.PRACTICE_SETUP_WITH) Routes.practiceSetup(emptyList()) else tab.route
                                nav.navigateTab(target)
                            },
                            icon = { Icon(tab.icon, null) },
                            label = { Text(stringResource(tab.label), maxLines = 1) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            nav, startDestination = Routes.HOME,
            modifier = Modifier.padding(bottom = if (showBar) padding.calculateBottomPadding() else androidx.compose.ui.unit.Dp(0f)),
            enterTransition = { fadeIn() }, exitTransition = { fadeOut() },
        ) {
            composable(Routes.HOME) { HomeScreen(nav) }
            composable(Routes.TOPICS) { TopicsScreen(nav) }
            composable(
                Routes.PRACTICE_SETUP_WITH,
                arguments = listOf(navArgument("topics") { type = NavType.StringType; defaultValue = "" }),
            ) { e -> PracticeSetupScreen(nav, e.arguments?.getString("topics").orEmpty()) }
            composable(Routes.EXAM_SETUP) { ExamSetupScreen(nav) }
            composable(Routes.PROGRESS) { ProgressScreen(nav) }
            composable(Routes.SETTINGS) { SettingsScreen(nav) }
            composable(
                Routes.READER,
                arguments = listOf(
                    navArgument("topicKey") { type = NavType.StringType },
                    navArgument("block") { type = NavType.IntType; defaultValue = -1 },
                ),
            ) { e -> ReaderScreen(nav, e.arguments!!.getString("topicKey")!!, e.arguments!!.getInt("block")) }
            composable(Routes.PRACTICE_SESSION, arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                PracticeSessionScreen(nav, e.arguments!!.getLong("id"))
            }
            composable(Routes.PRACTICE_SUMMARY, arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                PracticeSummaryScreen(nav, e.arguments!!.getLong("id"))
            }
            composable(Routes.EXAM_SESSION, arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                ExamSessionScreen(nav, e.arguments!!.getLong("id"))
            }
            composable(Routes.EXAM_RESULT, arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                ExamResultScreen(nav, e.arguments!!.getLong("id"))
            }
            composable(
                Routes.EXAM_REVIEW,
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType },
                    navArgument("filter") { type = NavType.StringType; defaultValue = "ALL" },
                ),
            ) { e -> ExamReviewScreen(nav, e.arguments!!.getLong("id"), e.arguments!!.getString("filter") ?: "ALL") }
        }
    }
}
