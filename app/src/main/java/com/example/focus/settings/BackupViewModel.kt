package com.example.focus.settings

import android.app.Application
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.focus.data.backup.BackupRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    /**
     * The pending import URI, if any.
     * Set when the user selects a file to import and cleared when the import is completed or canceled.
     */
    val pendingImport = _pendingImport.asStateFlow()

    private val _dataReplaced = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** A flow that emits when the application data has been replaced (imported or cleared). */
    val dataReplaced = _dataReplaced.asSharedFlow()

    /** Clear all application data */
    fun clearAll() = viewModelScope.launch(Dispatchers.IO) {
        repository.clearAll()
        _dataReplaced.emit(Unit)
    }

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

        // show a toast
        viewModelScope.launch(Dispatchers.Main) {
            Toast.makeText(app, "Exported to $uri", Toast.LENGTH_LONG).show()
        }
    }

    /** Requests an import from the given URI. This will set the pending import so the user can confirm the import */
    fun requestImport(uri: Uri) { _pendingImport.value = uri }
    /** Clears the pending import, called when the user cancels the import */
    fun clearPendingImport() { _pendingImport.value = null }

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
        _dataReplaced.emit(Unit)
    }
}
