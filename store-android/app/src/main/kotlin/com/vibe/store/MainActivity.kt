package com.vibe.store
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.vibe.store.api.SelectedImage
import com.vibe.store.infrastructure.media.AndroidProductMedia
import com.vibe.store.application.FoundationServiceImpl
import com.vibe.store.infrastructure.RoomDriverWiring
import com.vibe.store.presentation.FoundationApp
import com.vibe.store.presentation.SecurityApp
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable

/** Composition only. This public launcher accepts no privileged command or data. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val service = FoundationServiceImpl(RoomDriverWiring())
        setContent {
            var access by rememberSaveable { mutableStateOf(false) }
            var selectionResult by remember { mutableStateOf<((SelectedImage?) -> Unit)?>(null) }
            val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                val callback = selectionResult; selectionResult = null
                callback?.invoke(uri?.let(AndroidProductMedia::selection))
            }
            val store = application as StoreApplication
            if (access) SecurityApp(store.identity, store.catalog, { callback ->
                selectionResult = callback; picker.launch(arrayOf("image/jpeg", "image/png", "image/webp"))
            }) { access = false }
            else FoundationApp(service) { access = true }
        }
    }
}
