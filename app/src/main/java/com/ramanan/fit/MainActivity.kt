package com.ramanan.fit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.ramanan.fit.ui.App
import com.ramanan.fit.ui.RamananFitTheme
import com.ramanan.fit.ui.WorkoutViewModel

class MainActivity : ComponentActivity() {

    private val vm: WorkoutViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Leaving the foreground (home button, app switch, incoming-call screen, screen off)
        // auto-pauses the workout. Rotation does NOT pause: the ViewModel keeps running.
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                if (!isChangingConfigurations) vm.onBackgrounded()
            }
        })
        setContent {
            RamananFitTheme { App(vm) }
        }
    }
}
