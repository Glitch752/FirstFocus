package com.example.focus

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.focus.apps.AppSelectionScreen
import com.example.focus.settings.DebugSettingsScreen
import com.example.focus.settings.FocusReminderEditorScreen
import com.example.focus.settings.FocusRemindersScreen
import com.example.focus.settings.GrayscaleSettingsScreen
import com.example.focus.settings.SettingsScreen
import com.example.focus.usage.UsageViewModel
import kotlinx.coroutines.flow.Flow

private data class AppDestination(val route: String, val label: String, val icon: @Composable () -> Unit)

private val destinations = listOf(
    AppDestination("home", "Home") { Icon(Icons.Default.Home, null) },
    AppDestination("focus", "Focus") { Icon(Icons.Default.SelfImprovement, null) },
    AppDestination("settings", "Settings") { Icon(Icons.Default.Settings, null) }
)

/**
 * The main app composable
 * @param modifier Modifier to apply to the root of the app
 * @param openFocusOnStart Whether to open the focus screen on app start
 * @param openFocusRequests A flow of requests to open the focus screen.
 *      This is used to handle notifications without recreating the full app
 */
@Composable
fun FocusApp(
    modifier: Modifier = Modifier,
    openFocusOnStart: Boolean = false,
    openFocusRequests: Flow<Unit> = kotlinx.coroutines.flow.emptyFlow()
) {
    val navController = rememberNavController()
    val usageViewModel: UsageViewModel = viewModel()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // NavHost only reads startDestination once on creation, so we navigate manually with requests after that
    LaunchedEffect(navController, openFocusRequests) {
        openFocusRequests.collect {
            navController.navigate("focus") {
                launchSingleTop = true
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            }
        }
    }

    Scaffold(
        // Remove all padding from the scaffold
        modifier = modifier,
        bottomBar = {
            NavigationBar(Modifier.height(64.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())) {
                destinations.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = {
                            if (destination.route == currentRoute) return@NavigationBarItem
                            // if we're on a subpage of the destination, pop back to it
                            if (!navController.popBackStack(destination.route, inclusive = false)) {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = destination.icon,
                        label = { Text(destination.label) }
                    )
                }
            }
        }
    ) { paddingValues ->
        val destinationDirection = { initialRoute: String?, targetRoute: String? ->
            // transition in the direction of the new destination relative to the current page if
            // switching between main destinations. otherwise, slide in from the right for subpages.
            val initialIndex = destinations.indexOfFirst { it.route == initialRoute }
            val targetIndex = destinations.indexOfFirst { it.route == targetRoute }

            when {
                targetIndex == -1 -> AnimatedContentTransitionScope.SlideDirection.Left
                initialIndex == -1 -> AnimatedContentTransitionScope.SlideDirection.Right
                initialIndex < targetIndex -> AnimatedContentTransitionScope.SlideDirection.Left
                initialIndex > targetIndex -> AnimatedContentTransitionScope.SlideDirection.Right
                else -> AnimatedContentTransitionScope.SlideDirection.Left
            }
        }

        NavHost(
            navController,
            if (openFocusOnStart) "focus" else "home",
            Modifier.padding(paddingValues),
            enterTransition = {
                slideIntoContainer(destinationDirection(initialState.destination.route, targetState.destination.route), tween(250))
            },
            exitTransition = {
                slideOutOfContainer(destinationDirection(initialState.destination.route, targetState.destination.route), tween(250))
            },
            popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(250)) },
            popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(250)) }
        ) {
            composable("home") { HomeScreen(viewModel = usageViewModel) }
            composable("focus") { FocusSessionScreen() }
            composable("settings") { SettingsScreen(navController) }
            composable("settings/apps") { AppSelectionScreen(navController) }
            composable("settings/reminders") { FocusRemindersScreen(navController) }
            composable("settings/grayscale") { GrayscaleSettingsScreen(navController) }
            composable(
                "settings/reminders/edit/{reminderId}",
                arguments = listOf(navArgument("reminderId") { type = NavType.LongType })
            ) { entry ->
                val reminderId = entry.arguments?.getLong("reminderId") ?: 0L
                val remindersViewModel: com.example.focus.settings.FocusRemindersViewModel = viewModel()
                val reminders by remindersViewModel.reminders.collectAsStateWithLifecycle()
                FocusReminderEditorScreen(navController, reminders.firstOrNull { it.id == reminderId }, remindersViewModel)
            }
            if (BuildConfig.DEBUG) {
                composable("settings/debug") { DebugSettingsScreen(navController, usageViewModel) }
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String) {
    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        androidx.compose.material3.Text(title)
    }
}
