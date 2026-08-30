package com.example.focus.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.focus.data.backup.BackupRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class BackupViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val repository = BackupRepository(application)

    /**
     * If there's an import pending (importing or unconfirmed by the user),
     * this is the imported file's URI. Otherwise, null.
     */
    private val _pendingImport = MutableStateFlow<Uri?>(null)
    /** Public read-only view of the pending import */
    val pendingImport = _pendingImport.asStateFlow()

    /** Exports the database and settings to the given URI */
    fun export(uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        // Create a temporary file to export to since we want to write to the output in one go and can't
        // directly stream
        val file = File.createTempFile("focus-export", ".zip", app.cacheDir)
        try {
            repository.export(file)

            app.contentResolver.openOutputStream(uri)?.use { output ->
                file.inputStream().use { input -> input.copyTo(output) }
            }
        } finally {
            file.delete()
        }
    }

    /** Requests an import from the given URI. This will set the pending import so the user can confirm the import */
    fun requestImport(uri: Uri) { _pendingImport.value = uri }

    /** Actually import the database and settings from the given URI, called after the user confirms the import */
    fun import(uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        // Copy the file to a temporary location since we may not be able to read it again
        val file = File.createTempFile("focus-import", ".zip", app.cacheDir)
        app.contentResolver.openInputStream(uri)!!.use { input -> file.outputStream().use(input::copyTo) }

        try {
            repository.import(file)
        } finally {
            file.delete()
        }

        _pendingImport.value = null
    }
}
