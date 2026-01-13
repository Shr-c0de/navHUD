package com.example.navhud

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.navhud.ui.theme.NavHUDTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Keep screen on for HUD
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            NavHUDTheme {
                val navInfo by NavigationRepository.navInfo.collectAsState()
                val lifecycleOwner = LocalLifecycleOwner.current
                
                var isNotificationEnabled by remember { mutableStateOf(false) }

                // Check permissions whenever user returns to the app
                LaunchedEffect(lifecycleOwner) {
                    lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                        isNotificationEnabled = isNotificationServiceEnabled(this@MainActivity)
                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    NavigationDashboard(
                        navInfo = navInfo,
                        showNotificationButton = !isNotificationEnabled,
                        onOpenNotificationSettings = {
                            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    private fun isNotificationServiceEnabled(context: Context): Boolean {
        val pkgName = context.packageName
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        if (flat != null) {
            val names = flat.split(":")
            for (name in names) {
                val cn = ComponentName.unflattenFromString(name)
                if (cn != null && pkgName == cn.packageName) return true
            }
        }
        return false
    }
}

@Composable
fun NavigationDashboard(
    navInfo: NavInfo,
    showNotificationButton: Boolean,
    onOpenNotificationSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Maps Navigation HUD",
            style = MaterialTheme.typography.headlineMedium
        )
        
        Spacer(modifier = Modifier.height(32.dp))

        navInfo.icon?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Turn Icon",
                modifier = Modifier.size(150.dp)
            )
        } ?: Spacer(modifier = Modifier.height(150.dp))

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = navInfo.instruction,
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = navInfo.details,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.weight(1f))

        if (showNotificationButton) {
            Button(
                onClick = onOpenNotificationSettings,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Text("Grant Notification Access")
            }
        }
    }
}
