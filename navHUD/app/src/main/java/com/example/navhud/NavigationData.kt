package com.example.navhud

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NavInfo(
    val instruction: String = "No Data",
    val details: String = "Waiting for Maps...",
    val icon: Bitmap? = null
)

object NavigationRepository {
    private val _navInfo = MutableStateFlow(NavInfo())
    val navInfo = _navInfo.asStateFlow()

    fun update(instruction: String?, details: String?, icon: Bitmap?) {
        _navInfo.value = NavInfo(
            instruction ?: "No Instruction",
            details ?: "",
            icon
        )
    }
}
