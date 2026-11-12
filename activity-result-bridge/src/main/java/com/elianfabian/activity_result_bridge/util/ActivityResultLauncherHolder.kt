package com.elianfabian.activity_result_bridge.util

import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner

internal class ActivityResultLauncherHolder<I> {
	private var launcher: ActivityResultLauncher<I>? = null
	private var lifecycle: Lifecycle? = null
	private val observer = object : DefaultLifecycleObserver {
		override fun onDestroy(owner: LifecycleOwner) {
			unregister()
		}
	}

	fun attach(activity: ComponentActivity, launcher: ActivityResultLauncher<I>) {
		if (activity.lifecycle.currentState == Lifecycle.State.DESTROYED) {
			launcher.unregister()
			return
		}
		this.launcher = launcher
		lifecycle = activity.lifecycle
		activity.lifecycle.addObserver(observer)
	}

	fun launch(input: I) {
		launcher?.launch(input) ?: error("No active Activity available to launch the contract")
	}

	fun unregister() {
		val previousLauncher = launcher
		val previousLifecycle = lifecycle
		launcher = null
		lifecycle = null
		previousLifecycle?.removeObserver(observer)
		previousLauncher?.unregister()
	}
}
