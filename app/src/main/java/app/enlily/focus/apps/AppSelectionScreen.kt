package app.enlily.focus.apps

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController

// TODO: it would be cool to have a "recommendations" section here with apps
// like social media, games, or whatever else

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSelectionScreen(navController: NavHostController, viewModel: AppSelectionViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Filter apps based on search query
    val apps = state.apps.filter {
        state.searchQuery.isBlank() || it.label.contains(state.searchQuery, true) || it.packageName.contains(state.searchQuery, true)
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        TopAppBar(
            title = { Text("Distracting apps") },
            windowInsets = WindowInsets(0.dp),
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            }
        )
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = viewModel::setSearchQuery,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Search apps") }
        )
        if (state.isLoading) {
            Text("${state.selectedPackages.size} selected", Modifier.padding(vertical = 4.dp, horizontal = 4.dp))
            Column(
                Modifier.fillMaxWidth().padding(top = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Text("Loading apps...", Modifier.padding(top = 12.dp))
            }
        } else {
            Text("${state.selectedPackages.size} / ${state.apps.size} selected", Modifier.padding(vertical = 4.dp, horizontal = 4.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(apps, key = { it.packageName }) { app ->
                    Row(
                        Modifier.fillMaxWidth().clickable { viewModel.toggleApp(app.packageName) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.ui.viewinterop.AndroidView(
                            factory = { context -> android.widget.ImageView(context) },
                            update = { it.setImageDrawable(app.icon) },
                            modifier = Modifier.size(40.dp)
                        )
                        Text(app.label, Modifier.weight(1f).padding(start = 12.dp))
                        Checkbox(
                            checked = app.packageName in state.selectedPackages,
                            onCheckedChange = { viewModel.toggleApp(app.packageName) }
                        )
                    }
                }
            }
        }
    }
}