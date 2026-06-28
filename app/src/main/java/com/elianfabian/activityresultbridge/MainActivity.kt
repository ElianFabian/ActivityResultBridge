package com.elianfabian.activityresultbridge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.elianfabian.activityresultbridge.ui.theme.ActivityResultBridgeTheme

class MainActivity : ComponentActivity() {

	val viewModel: MainViewModel by viewModels()


	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		setContent {
			ActivityResultBridgeTheme {
				val uiState by viewModel.uiState.collectAsState()

				Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
					MainScreen(
						state = uiState,
						onAction = { action ->
							viewModel.sendAction(action)
						},
						modifier = Modifier.padding(innerPadding)
					)
				}
			}
		}
	}
}

@Composable
fun MainScreen(
	state: UiState,
	onAction: (action: UiAction) -> Unit, // Using Void? wrapper or Unit representation
	modifier: Modifier = Modifier,
) {
	Column(
		modifier = modifier
			.fillMaxSize()
			.padding(24.dp)
			.verticalScroll(rememberScrollState()),
		verticalArrangement = Arrangement.spacedBy(20.dp),
		horizontalAlignment = Alignment.CenterHorizontally
	) {
		Text(
			text = "Activity Result Bridge Demo",
			style = MaterialTheme.typography.headlineMedium
		)

		HorizontalDivider()

		// --- CAMERA PERMISSION SECTION ---
		Column(horizontalAlignment = Alignment.CenterHorizontally) {
			Button(onClick = { onAction(UiAction.RequestCameraPermission) }) {
				Text("Request Camera Permission")
			}
			Text(
				text = "Status: ${
					when (state.cameraPermissionGranted) {
						true -> "Granted"
						false -> "Denied"
						null -> "Unknown"
					}
				}",
				style = MaterialTheme.typography.bodyMedium
			)
		}

		// --- FILE PICKER SECTION ---
		Column(horizontalAlignment = Alignment.CenterHorizontally) {
			Button(onClick = { onAction(UiAction.PickFile) }) {
				Text("Pick Any File")
			}
			Text(
				text = "Selected URI: ${state.selectedFileUri?.toString() ?: "None"}",
				style = MaterialTheme.typography.bodySmall,
				maxLines = 2
			)
		}

		// --- TAKE PHOTO SECTION ---
		Column(horizontalAlignment = Alignment.CenterHorizontally) {
			Button(onClick = { onAction(UiAction.TakePhoto) }) {
				Text("Take Photo Thumbnail")
			}
			Spacer(modifier = Modifier.height(8.dp))
			if (state.capturedPhoto != null) {
				Image(
					bitmap = state.capturedPhoto.asImageBitmap(),
					contentDescription = "Captured photo preview",
					modifier = Modifier.size(120.dp)
				)
			}
			else {
				Text("No photo taken yet", style = MaterialTheme.typography.bodyMedium)
			}
		}

		// --- ERROR HANDLING ---
		if (state.errorMessage != null) {
			AlertDialog(
				onDismissRequest = { onAction(UiAction.ClearError) },
				confirmButton = {
					TextButton(onClick = { onAction(UiAction.ClearError) }) {
						Text("OK")
					}
				},
				title = { Text("Error Occurred") },
				text = { Text(state.errorMessage) }
			)
		}
	}
}
