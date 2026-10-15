package com.cloudroam.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val Context.dataStore by preferencesDataStore(name = "cloudroam")

/**
 * 本地设置与位置库（FR-03 / FR-08）。
 * 全部仅存本地，无云端上传（共享规则：隐私底线）。
 */
object Settings {

    data class Place(val lat: Double, val lng: Double, val name: String)

    private val KEY_AGREED = booleanPreferencesKey("agreement_v1")
    private val KEY_LAT = doublePreferencesKey("place_lat")
    private val KEY_LNG = doublePreferencesKey("place_lng")
    private val KEY_NAME = stringPreferencesKey("place_name")

    private lateinit var scope: CoroutineScope

    private val _agreed = MutableStateFlow<Boolean?>(null)
    val agreed: StateFlow<Boolean?> = _agreed.asStateFlow()

    private val _place = MutableStateFlow<Place?>(null)
    val place: StateFlow<Place?> = _place.asStateFlow()

    fun init(context: Context) {
        if (::scope.isInitialized) return
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val ds = context.applicationContext.dataStore
        scope.launch {
            ds.data.catch { emit(emptyPreferences()) }.collect { p ->
                _agreed.value = p[KEY_AGREED] == true
                val lat = p[KEY_LAT]
                val lng = p[KEY_LNG]
                _place.value = if (lat != null && lng != null) {
                    Place(lat, lng, p[KEY_NAME] ?: "自定义位置")
                } else null
            }
        }
    }

    /** FR-08：首次启动同意用户协议 */
    suspend fun setAgreed(context: Context, agreed: Boolean) {
        context.applicationContext.dataStore.edit { it[KEY_AGREED] = agreed }
    }

    /** FR-02/FR-03：保存当前选定的虚拟位置 */
    suspend fun setPlace(context: Context, place: Place) {
        context.applicationContext.dataStore.edit { p ->
            p[KEY_LAT] = place.lat
            p[KEY_LNG] = place.lng
            p[KEY_NAME] = place.name
        }
    }

    /** 快速读取（供磁贴等服务入口同步调用） */
    suspend fun readPlace(context: Context): Place? {
        val p = context.applicationContext.dataStore.data
            .catch { emit(emptyPreferences()) }.first()
        val lat = p[KEY_LAT] ?: return null
        val lng = p[KEY_LNG] ?: return null
        return Place(lat, lng, p[KEY_NAME] ?: "自定义位置")
    }
}
