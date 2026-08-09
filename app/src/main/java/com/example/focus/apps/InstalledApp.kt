package com.example.focus.apps

import android.graphics.drawable.Drawable

/** A simple data class representing an installed app */
data class InstalledApp(
    val packageName: String,
    val label: String,
    val icon: Drawable
)