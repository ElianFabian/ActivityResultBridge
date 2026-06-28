package com.elianfabian.activityresultbridge

import android.graphics.Bitmap
import android.net.Uri

data class UiState(
	val cameraPermissionGranted: Boolean? = null,
	val selectedFileUri: Uri? = null,
	val capturedPhoto: Bitmap? = null,
	val errorMessage: String? = null,
)

sealed interface UiAction {
	object RequestCameraPermission : UiAction
	object PickFile : UiAction
	object TakePhoto : UiAction
	object ClearError : UiAction
}
