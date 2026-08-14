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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.focus.apps.AppSelectionScreen
import com.example.focus.settings.SettingsScreen

private data class AppDestination(val route: String, val label: String, val icon: @Composable () -> Unit)

private val destinations = listOf(
    AppDestination("home", "Home") { Icon(Icons.Default.Home, null) },
    AppDestination("focus", "Focus") { Icon(Icons.Default.SelfImprovement, null) },
    AppDestination("settings", "Settings") { Icon(Icons.Default.Settings, null) }
)

@Composable
fun FocusApp(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

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
                            navController.navigate(destination.route)
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
            "home",
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
            composable("home") { HomeScreen() }
            composable("focus") { PlaceholderScreen("Focus session") }
            composable("settings") { SettingsScreen(navController) }
            composable("settings/apps") { AppSelectionScreen(navController) }
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String) {
    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        androidx.compose.material3.Text(title)
    }
}
