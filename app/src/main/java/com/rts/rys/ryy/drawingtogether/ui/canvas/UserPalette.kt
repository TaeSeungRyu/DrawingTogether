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

    companion object {
        const val PALETTE_SIZE: Int = 13  // DefaultColorPalette 크기
        private const val KEY = "argb_list"
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
