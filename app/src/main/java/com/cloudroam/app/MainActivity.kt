package com.cloudroam.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.cloudroam.app.data.Settings
import com.cloudroam.app.ui.AgreementScreen
import com.cloudroam.app.ui.CloudRoamTheme
import com.cloudroam.app.ui.HomeScreen
import com.cloudroam.app.ui.SelfCheckScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CloudRoamTheme {
                RootNav()
            }
        }
    }
}

/** 顶层导航：协议确认（FR-08）→ 环境自检（FR-01）→ 首页（FR-02/FR-07） */
@Composable
private fun RootNav() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val agreed by Settings.agreed.collectAsState()

    when (agreed) {
        null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        false -> AgreementScreen(
            onAgree = { scope.launch { Settings.setAgreed(context, true) } }
        )
        else -> MainFlow()
    }
}

@Composable
private fun MainFlow() {
    var passed by remember { mutableStateOf(false) }
    if (passed) {
        HomeScreen()
    } else {
        SelfCheckScreen(onPassed = { passed = true })
    }
}
