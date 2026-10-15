package com.cloudroam.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.cloudroam.app.BuildConfig
import com.cloudroam.app.R
import com.cloudroam.app.data.Settings
import com.cloudroam.app.engine.EngineController
import kotlinx.coroutines.launch

/**
 * 首页（FR-02 选点 + FR-07 开关生命周期）。
 * 全屏地图 + 中心大头针 + 底部位置卡（状态 / 名称 / 坐标 / 主开关）。
 */
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val engineStatus by EngineController.status.collectAsState()
    val engineMode by EngineController.mode.collectAsState()
    var placeName by remember { mutableStateOf("") }
    var camLat by remember { mutableStateOf(31.2304) }
    var camLng by remember { mutableStateOf(121.4737) }
    val keyMissing = BuildConfig.AMAP_KEY == "PLACEHOLDER"

    // 运行期权限：精确定位（地图选点）+ 通知（常驻通知）
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }
    LaunchedEffect(Unit) {
        val need = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS,
        ).filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (need.isNotEmpty()) permLauncher.launch(need.toTypedArray())
    }

    Box(Modifier.fillMaxSize()) {
        AMapPicker(
            onCenter = { lat, lng ->
                camLat = lat
                camLng = lng
            },
            modifier = Modifier.fillMaxSize()
        )

        // 中心大头针（跟随地图中心的虚拟选点）
        Icon(
            painter = painterResource(R.drawable.ic_launcher),
            contentDescription = "中心选点",
            tint = Color.Unspecified,
            modifier = Modifier
                .align(Alignment.Center)
                .size(40.dp)
                .offset(y = (-20).dp)
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            if (keyMissing) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "未配置高德 Key：底图无法加载，但拖动网格仍可取坐标。请在 local.properties 中添加 AMAP_KEY=你的Key 后重新安装。",
                        modifier = Modifier.padding(10.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    val running = engineStatus is EngineController.Status.Running
                    val error = engineStatus as? EngineController.Status.Error
                    val modeText = if (engineMode == EngineController.Mode.FULL) "完整模式" else "兼容模式"
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (running) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            if (running) "伪装运行中 · $modeText · 所有APP只见虚拟位置"
                            else "真实位置 · 伪装未开启",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    error?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            it.reason,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "坐标：%.5f, %.5f".format(camLat, camLng),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = placeName,
                        onValueChange = { placeName = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("位置名称（收藏用）") },
                        placeholder = { Text("自定义位置") }
                    )
                    Spacer(Modifier.height(10.dp))
                    if (running && engineMode == EngineController.Mode.COMPAT) {
                        Text(
                            "兼容模式：不伪装 WiFi / 基站，测试时建议关闭手机 WiFi；若开启失败，请在 开发者选项 → 选择模拟位置信息应用 中选择 CloudRoam。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {
                                val place = Settings.Place(
                                    lat = camLat,
                                    lng = camLng,
                                    name = placeName.ifBlank { "自定义位置" }
                                )
                                scope.launch {
                                    Settings.setPlace(context, place) // 记入位置库（FR-03）
                                }
                                EngineController.start(context, place)
                            },
                            enabled = engineStatus is EngineController.Status.Off
                        ) {
                            Text("开启伪装")
                        }
                        OutlinedButton(
                            onClick = { EngineController.stop(context) },
                            enabled = running
                        ) {
                            Text("关闭")
                        }
                    }
                }
            }
        }
    }
}

/**
 * 高德地图选点容器（FR-02）。
 * 拖动地图使中心大头针对准目标；相机停止时回调中心坐标。
 */
@Composable
private fun AMapPicker(onCenter: (Double, Double) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val mapView = remember {
        // 高德 SDK 隐私合规：须在创建地图前确认（本应用首页前已展示用户协议，FR-08）
        com.amap.api.maps.MapsInitializer.updatePrivacyShow(context, true, true)
        com.amap.api.maps.MapsInitializer.updatePrivacyAgree(context, true)
        com.amap.api.maps.TextureMapView(context)
    }
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current

    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> mapView.onResume()
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = {
            mapView.onCreate(null)
            val map = mapView.map
            map.uiSettings.isRotateGesturesEnabled = false
            map.moveCamera(
                com.amap.api.maps.CameraUpdateFactory.newLatLngZoom(
                    com.amap.api.maps.model.LatLng(31.2304, 121.4737), 13f
                )
            )
            map.setOnCameraChangeListener(object : com.amap.api.maps.AMap.OnCameraChangeListener {
                override fun onCameraChange(position: com.amap.api.maps.model.CameraPosition?) {}
                override fun onCameraChangeFinish(position: com.amap.api.maps.model.CameraPosition?) {
                    position?.target?.let { onCenter(it.latitude, it.longitude) }
                }
            })
            mapView
        }
    )
}
