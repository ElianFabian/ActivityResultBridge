package com.elianfabian.activityresultbridge

import android.Manifest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elianfabian.activity_result_bridge.ActivityResultBridge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(
	private val activityResultBridge: ActivityResultBridge = ActivityResultBridge,
) : ViewModel() {

	private val _uiState = MutableStateFlow(UiState())
	val uiState: StateFlow<UiState> = _uiState.asStateFlow()

	fun sendAction(action: UiAction) {
		viewModelScope.launch {
			try {
				handleAction(action)
			}
			catch (e: Exception) {
				_uiState.update { it.copy(errorMessage = e.localizedMessage ?: "Unknown error") }
			}
		}
	}

	private suspend fun handleAction(action: UiAction) {
		when (action) {
			UiAction.RequestCameraPermission -> {
				val isGranted = activityResultBridge.launch(
					contract = ActivityResultContracts.RequestPermission(),
					input = Manifest.permission.CAMERA
				)
				_uiState.update { it.copy(cameraPermissionGranted = isGranted) }
			}

			UiAction.PickFile -> {
				// Opens system file picker for any file type
				val uri = activityResultBridge.launch(
					contract = ActivityResultContracts.GetContent(),
					input = "*/*"
				)
				_uiState.update { it.copy(selectedFileUri = uri) }
			}

			UiAction.TakePhoto -> {
				// Launches camera app and returns a thumbnail Bitmap
				val bitmap = activityResultBridge.launch(
					contract = ActivityResultContracts.TakePicturePreview(),
					input = null
				)
				_uiState.update { it.copy(capturedPhoto = bitmap) }
			}

			UiAction.ClearError -> {
				_uiState.update { it.copy(errorMessage = null) }
			}
		}
	}
}
