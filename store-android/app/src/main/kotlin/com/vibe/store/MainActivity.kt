package com.vibe.store
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
            if (access) SecurityApp((application as StoreApplication).identity) { access = false }
            else FoundationApp(service) { access = true }
        }
    }
}
