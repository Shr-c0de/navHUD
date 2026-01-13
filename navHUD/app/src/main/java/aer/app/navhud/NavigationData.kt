package aer.app.navhud

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NavInfo(
    val instruction: String = "No Data",
    val details: String = "Waiting for Maps...",
    val subText: String = "",
    val icon: Bitmap? = null,
    val isNavigating: Boolean = false // Flag to check if Maps is active
)

object NavigationRepository {
    private val _navInfo = MutableStateFlow(NavInfo())
    val navInfo = _navInfo.asStateFlow()

    fun update(instruction: String?, details: String?, subText: String?, icon: Bitmap?) {
        _navInfo.value = NavInfo(
            instruction ?: "No Instruction",
            details ?: "",
            subText ?: "",
            icon,
            isNavigating = true // Set to true whenever we get a valid update
        )
    }
    
    fun setNavigationStopped() {
        // Reset to default values but keep isNavigating false
        _navInfo.value = NavInfo(isNavigating = false)
    }
}
