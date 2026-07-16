package com.reminds.characters

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reminds.characters.overlay.MascotOverlayService
import com.reminds.characters.ui.HomeScreen
import com.reminds.characters.ui.TaskViewModel
import com.reminds.characters.ui.theme.RemindsTheme

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* 拒否時は通知なしで動く */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // キャラ常駐がONなら復帰させる（権限を許可して戻ってきた直後にも効く）
        MascotOverlayService.startIfEnabled(this)

        setContent {
            RemindsTheme {
                val viewModel: TaskViewModel = viewModel(factory = TaskViewModel.Factory)
                HomeScreen(viewModel)
            }
        }
    }
}
