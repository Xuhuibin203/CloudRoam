package com.cloudroam.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * 用户协议与用途声明（FR-08）。
 * 必须明确同意后才能使用；不同意则退出应用。
 */
@Composable
fun AgreementScreen(onAgree: () -> Unit) {
    val context = LocalContext.current
    var checked by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Text("用户协议与用途声明", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))

        Card(Modifier.weight(1f)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "CloudRoam 是一款面向个人隐私保护的位置伪装工具。使用前请确认以下条款：",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text("1. 合法用途：仅限保护个人位置隐私等合法场景。", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "2. 禁止行为：严禁用于网约车 / 外卖等平台作弊、考勤造假、游戏作弊及其他一切违法违规场景；由此产生的后果由使用者自行承担。",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text("3. 环境要求：完整伪装需要 root 与 LSPosed 框架环境；无该环境时可使用能力受限的兼容模式。", style = MaterialTheme.typography.bodyMedium)
                Text("4. 数据底线：CloudRoam 不采集、不上传任何位置与使用数据，选点、历史与协议确认全部仅保存在本机。", style = MaterialTheme.typography.bodyMedium)
                Text("5. 已知限制：出口 IP 不在伪装范围内；个别应用使用自有定位缓存，可能需要重新进入页面后刷新。", style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = checked, onCheckedChange = { checked = it })
            Text("我已阅读并理解上述条款", style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { (context as? android.app.Activity)?.finishAffinity() }) {
                Text("不同意")
            }
            Button(onClick = onAgree, enabled = checked) {
                Text("同意并继续")
            }
        }
    }
}
