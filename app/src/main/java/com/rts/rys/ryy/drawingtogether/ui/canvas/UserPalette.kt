package com.rts.rys.ryy.drawingtogether.ui.canvas

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// 사용자 정의 색 팔레트 — DefaultColorPalette 의 12 슬롯을 사용자가 ColorPickerSheet 로 갱신
// 가능. SharedPreferences 에 "/" 구분 ARGB 문자열로 저장. 항상 정확히 PALETTE_SIZE 개 유지 —
// 손상되거나 사이즈 다르면 기본값으로 fallback.
class UserPaletteRepo private constructor(private val prefs: SharedPreferences) {

    private val _palette = MutableStateFlow(load())
    val palette: StateFlow<List<Int>> = _palette.asStateFlow()

    // 최근 사용 색 — 최신이 앞. 중복 제거, 최대 RECENT_MAX 개. 색을 쓸 때마다 addRecent 호출.
    private val _recent = MutableStateFlow(loadRecent())
    val recent: StateFlow<List<Int>> = _recent.asStateFlow()

    fun addRecent(argb: Int) {
        val opaque = 0xFF000000.toInt() or (argb and 0x00FFFFFF)
        val updated = (listOf(opaque) + _recent.value.filter { it != opaque }).take(RECENT_MAX)
        if (updated == _recent.value) return
        _recent.value = updated
        prefs.edit().putString(KEY_RECENT, updated.joinToString(SEP) { it.toString() }).apply()
    }

    fun updateSlot(index: Int, argb: Int) {
        if (index !in 0 until PALETTE_SIZE) return
        val updated = _palette.value.toMutableList().apply { set(index, argb) }
        _palette.value = updated
        prefs.edit().putString(KEY, updated.joinToString(SEP) { it.toString() }).apply()
    }

    fun resetToDefault() {
        _palette.value = DefaultColorPalette
        prefs.edit().remove(KEY).apply()
    }

    private fun load(): List<Int> {
        val raw = prefs.getString(KEY, null) ?: return DefaultColorPalette
        val parsed = raw.split(SEP).mapNotNull { it.toIntOrNull() }
        return if (parsed.size == PALETTE_SIZE) parsed else DefaultColorPalette
    }

    private fun loadRecent(): List<Int> {
        val raw = prefs.getString(KEY_RECENT, null) ?: return emptyList()
        return raw.split(SEP).mapNotNull { it.toIntOrNull() }.take(RECENT_MAX)
    }

    companion object {
        const val PALETTE_SIZE: Int = 7  // DefaultColorPalette 크기
        const val RECENT_MAX: Int = 8
        private const val KEY = "argb_list"
        private const val KEY_RECENT = "recent_list"
        private const val SEP = "/"
        private const val PREFS = "user_palette"

        @Volatile private var instance: UserPaletteRepo? = null

        fun get(context: Context): UserPaletteRepo = instance ?: synchronized(this) {
            instance ?: run {
                val prefs = context.applicationContext
                    .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                UserPaletteRepo(prefs).also { instance = it }
            }
        }
    }
}
