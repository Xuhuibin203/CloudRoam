package com.cloudroam.app.hook

import android.content.SharedPreferences
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.util.concurrent.Executor
import java.util.function.Consumer

/**
 * 位置 API 伪装（FR-05 M1 范围：位置读取 + 最后已知位置 + 模拟标记）。
 *
 * 配置读取：LSPosed 远程偏好设置（App 侧 HookConfigWriter 写入，此处只读）。
 * 热路径优化：配置缓存于 volatile 字段，变更经 OnSharedPreferenceChangeListener 刷新，
 * 每次位置读取为 O(1)，无逐次 IPC。
 *
 * 失败安全（PRD 共享规则）：enabled=false 或 expireAt 心跳超时 → 全部放行真实数据。
 */
object LocationHooks {

    /** 与 App 侧 HookConfigWriter.GROUP 保持一致 */
    private const val GROUP = "cloudroam"

    @Volatile private var enabled = false
    @Volatile private var lat = 0.0
    @Volatile private var lng = 0.0
    @Volatile private var accuracy = 15f
    @Volatile private var expireAt = 0L

    // 显式持有监听器引用，防止 SharedPreferences 弱引用导致监听失效
    private val configListener =
        SharedPreferences.OnSharedPreferenceChangeListener { prefs, _ -> read(prefs) }

    fun install(xposed: XposedInterface) {
        val prefs = xposed.getRemotePreferences(GROUP)
        read(prefs)
        prefs.registerOnSharedPreferenceChangeListener(configListener)

        val lm = LocationManager::class.java

        // 1) 最后已知位置：拦截系统层缓存读取（含 getLastLocation 路径的对外出口）
        xposed.hook(lm.getMethod("getLastKnownLocation", String::class.java))
            .intercept { chain ->
                val result = chain.proceed() as? Location
                spoof(result)
                result
            }

        // 2) requestLocationUpdates 常用重载：包装回调，改写每次推送的位置
        hookListenerOverload(
            xposed, 3,
            String::class.java, Long::class.javaPrimitiveType!!, Float::class.javaPrimitiveType!!,
            LocationListener::class.java
        )
        hookListenerOverload(
            xposed, 3,
            String::class.java, Long::class.javaPrimitiveType!!, Float::class.javaPrimitiveType!!,
            LocationListener::class.java, Looper::class.java
        )
        hookListenerOverload(
            xposed, 2,
            Long::class.javaPrimitiveType!!, Float::class.javaPrimitiveType!!,
            LocationListener::class.java
        )
        hookListenerOverload(
            xposed, 2,
            Long::class.javaPrimitiveType!!, Float::class.javaPrimitiveType!!,
            LocationListener::class.java, Looper::class.java
        )

        // 3) getCurrentLocation（API 30+）：不同版本的签名在 Consumer/Executor 参数顺序上有差异，
        //    运行期探测 Consumer 参数位置，避免签名不匹配导致 hook 静默失效
        listOf(
            arrayOf(
                String::class.java, android.os.CancellationSignal::class.java,
                Consumer::class.java, Executor::class.java
            ),
            arrayOf(
                String::class.java, android.os.CancellationSignal::class.java,
                Executor::class.java, Consumer::class.java
            ),
        ).forEach { sig ->
            val m = try {
                lm.getMethod("getCurrentLocation", *sig)
            } catch (_: NoSuchMethodException) {
                return@forEach
            }
            xposed.hook(m).intercept { chain ->
                val args = chain.args
                val consumerIdx = args.indexOfFirst { it is Consumer<*> }
                if (consumerIdx < 0) return@intercept chain.proceed()
                @Suppress("UNCHECKED_CAST")
                val consumer = args[consumerIdx] as Consumer<Location>
                val wrapped = Consumer<Location> { loc ->
                    spoof(loc)
                    consumer.accept(loc)
                }
                val newArgs = args.toTypedArray()
                newArgs[consumerIdx] = wrapped
                chain.proceed(newArgs)
            }
        }

        // 4) 模拟标记恒为"否"（FR-05：目标应用无法据此识别伪装）
        try {
            xposed.hook(Location::class.java.getMethod("isFromMockProvider"))
                .intercept { false }
        } catch (_: NoSuchMethodException) {
        }
        try {
            xposed.hook(Location::class.java.getMethod("isMock")).intercept { false }
        } catch (_: NoSuchMethodException) {
        }

        // TODO(M2)：GNSS/NMEA、WiFi 扫描、基站信息伪造；FusedLocationProviderClient（GMS）覆盖
    }

    /** 原地改写位置；未开启或心跳超时则保持原样（放行真实数据） */
    private fun spoof(loc: Location?) {
        if (loc == null || !shouldSpoof()) return
        loc.latitude = lat + jitter()
        loc.longitude = lng + jitter()
        if (accuracy > 0f) loc.accuracy = accuracy
        // TODO(M2)：按 PRD FR-04 保持速度 / 航向 / 海拔与模式一致，细化噪声曲线
    }

    private fun shouldSpoof(): Boolean =
        enabled && System.currentTimeMillis() < expireAt

    /** 单点模式轻微噪声（±数米），避免"完美静止"被识别；M2 按模式细化 */
    private fun jitter(): Double = (Math.random() - 0.5) * 0.0001

    private fun read(p: SharedPreferences) {
        enabled = p.getBoolean("enabled", false)
        expireAt = p.getLong("expireAt", 0L)
        accuracy = p.getFloat("accuracy", 15f)
        lat = p.getString("lat", null)?.toDoubleOrNull() ?: lat
        lng = p.getString("lng", null)?.toDoubleOrNull() ?: lng
    }

    /** 包装 LocationListener：onLocationChanged 前改写坐标，其余事件原样转发 */
    private fun hookListenerOverload(
        xposed: XposedInterface,
        listenerIndex: Int,
        vararg types: Class<*>
    ) {
        val m = try {
            LocationManager::class.java.getMethod("requestLocationUpdates", *types)
        } catch (_: NoSuchMethodException) {
            return
        }
        xposed.hook(m).intercept { chain ->
            val raw = chain.getArg(listenerIndex) ?: return@intercept chain.proceed()
            val wrapped = Proxy.newProxyInstance(
                LocationListener::class.java.classLoader,
                arrayOf(LocationListener::class.java),
                listenerHandler(raw)
            )
            val args = chain.args.toTypedArray()
            args[listenerIndex] = wrapped
            chain.proceed(args)
        }
    }

    private fun listenerHandler(target: Any) = InvocationHandler { _, method, args ->
        if (method.name == "onLocationChanged") {
            spoof(args?.get(0) as? Location)
        }
        method.invoke(target, *(args ?: emptyArray()))
    }
}
