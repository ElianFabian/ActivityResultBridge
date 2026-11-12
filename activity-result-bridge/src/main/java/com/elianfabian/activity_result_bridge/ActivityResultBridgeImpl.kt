package com.elianfabian.activity_result_bridge

import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContract
import com.elianfabian.activity_provider.ActivityProvider
import com.elianfabian.activity_result_bridge.util.ActivityResultLauncherState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class ActivityResultBridgeImpl(
	private val activityFlow: StateFlow<ComponentActivity?> = ActivityProvider.activity,
	scope: CoroutineScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob()),
) : ActivityResultBridge {

	// Accessed only on Main, including callbacks and cancellation cleanup.
	private val _launchersStateByKey = mutableMapOf<String, ActivityResultLauncherState<*, *>>()

	init {
		scope.launch(Dispatchers.Main.immediate) {
			activityFlow.collect { activity ->
				// register() can deliver a pending result and modify the map immediately.
				_launchersStateByKey.toList().forEach { (key, state) ->
					if (_launchersStateByKey[key] !== state) return@forEach
					if (activity == null) {
						state.realLauncher.unregister()
					} else {
						try {
							state.register(activity, key) { _launchersStateByKey[key] === state }
						} catch (exception: Exception) {
							state.onFailure(exception)
						}
					}
				}
			}
		}
	}

	override suspend fun <I, O> launch(contract: ActivityResultContract<I, O>, input: I): O =
		withContext(Dispatchers.Main.immediate) {
			val activity = activityFlow.filterNotNull().first()
			val key = UUID.randomUUID().toString()
			try {
				suspendCancellableCoroutine { continuation ->
					if (!continuation.isActive) return@suspendCancellableCoroutine
					val state = ActivityResultLauncherState(
						contract = contract,
						callback = ActivityResultCallback<O> { result ->
							clear(key)
							if (continuation.isActive) continuation.resume(result)
						},
						onFailure = { exception ->
							clear(key)
							if (continuation.isActive) continuation.resumeWithException(exception)
						},
					)
					_launchersStateByKey[key] = state
					state.register(activity, key) { _launchersStateByKey[key] === state && continuation.isActive }
					if (continuation.isActive && _launchersStateByKey[key] === state) {
						state.realLauncher.launch(input)
					}
				}
			} finally {
				// Also runs on Main if launch throws or the caller cancels from another thread.
				clear(key)
			}
		}

	private fun clear(key: String) {
		_launchersStateByKey.remove(key)?.realLauncher?.unregister()
	}
}
