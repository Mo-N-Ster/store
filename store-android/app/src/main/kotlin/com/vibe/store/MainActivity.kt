package com.vibe.store
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.vibe.store.application.FoundationServiceImpl
import com.vibe.store.infrastructure.RoomDriverWiring
import com.vibe.store.presentation.FoundationApp

/** Composition only. This public launcher accepts no privileged command or data. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val service = FoundationServiceImpl(RoomDriverWiring())
        setContent { FoundationApp(service) }
    }
}
