package com.example.focus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen() {
    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Focus", style = MaterialTheme.typography.headlineLarge)
        Text("Stuff will go here eventually trust", style = MaterialTheme.typography.titleMedium)
        SummaryRow("Phone usage today", "todo")
        SummaryRow("Distracting apps", "todo")
        SummaryRow("Focus time today", "todo")
        SummaryRow("Daily target", "todo ")
        Text("Statistics", style = MaterialTheme.typography.titleLarge)
        Text("todo")
    }
}

@Composable
private fun SummaryRow(title: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title)
        Text(value, color = MaterialTheme.colorScheme.primary)
    }
}
