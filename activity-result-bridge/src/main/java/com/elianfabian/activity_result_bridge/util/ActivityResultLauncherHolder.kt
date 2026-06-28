package com.elianfabian.activity_result_bridge.util

import androidx.activity.result.ActivityResultLauncher

internal class ActivityResultLauncherHolder<I> {

	var launcher: ActivityResultLauncher<I>? = null

	fun launch(input: I) {
		launcher?.launch(input) ?: error("No active Activity available to launch the contract")
	}

	fun unregister() {
		launcher?.unregister()
		launcher = null
	}
}
