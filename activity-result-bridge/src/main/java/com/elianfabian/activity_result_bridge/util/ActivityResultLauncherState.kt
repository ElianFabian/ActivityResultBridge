package com.elianfabian.activity_result_bridge.util

import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContract

internal class ActivityResultLauncherState<I, O>(
	val contract: ActivityResultContract<I, O>,
	val callback: ActivityResultCallback<O>,
	val onFailure: (Exception) -> Unit,
) {
	val realLauncher = ActivityResultLauncherHolder<I>()

	fun register(activity: ComponentActivity, key: String, isPending: () -> Boolean) {
		realLauncher.unregister()
		val registry = activity.activityResultRegistry
		val launcher = try {
			registry.register(key, contract, callback)
		} catch (exception: Exception) {
			// AndroidX installs the callback before parsing a pending result. If parsing
			// fails, obtain a cleanup handle without invoking the request callback again.
			try {
				registry.register(key, contract, ActivityResultCallback<O> { }).unregister()
			} catch (cleanupException: Exception) {
				exception.addSuppressed(cleanupException)
			}
			throw exception
		}
		if (isPending()) {
			realLauncher.attach(activity, launcher)
		} else {
			// A pending result may have completed the request inside register().
			launcher.unregister()
		}
	}
}
