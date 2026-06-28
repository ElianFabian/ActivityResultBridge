package com.elianfabian.activity_result_bridge

import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContract
import com.elianfabian.activity_provider.ActivityProvider
import com.elianfabian.activity_result_bridge.util.ActivityResultLauncherHolder
import com.elianfabian.activity_result_bridge.util.ActivityResultLauncherState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

internal class ActivityResultBridgeImpl : ActivityResultBridge {

	private val _scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

	private val _launchersStateByKey = ConcurrentHashMap<String, ActivityResultLauncherState<Any?, Any?>>()


	init {
		_scope.launch {
			ActivityProvider.activity.collect { activity ->
				if (activity == null) {
					return@collect
				}
				_launchersStateByKey.forEach { (key, state) ->
					state.realLauncher.unregister()

					state.realLauncher.launcher = activity.activityResultRegistry.register(
						key = key,
						contract = state.contract,
						callback = state.callback,
					)
				}
			}
		}
	}


	override suspend fun <I, O> launch(contract: ActivityResultContract<I, O>, input: I): O {
		val activity = ActivityProvider.getActivity()

		return suspendCancellableCoroutine { continuation ->
			val key = UUID.randomUUID().toString()

			@Suppress("UNCHECKED_CAST")
			val launcher: ActivityResultLauncherState<I, O> = _launchersStateByKey.getOrPut(key) {
				val callback = ActivityResultCallback<O> { result ->
					_launchersStateByKey.remove(key)?.realLauncher?.unregister()

					if (continuation.isActive) {
						continuation.resume(result)
					}
				}

				val launcher = activity.activityResultRegistry.register(
					key = key,
					contract = contract,
					callback = callback,
				)

				val realLauncher = ActivityResultLauncherHolder<I>().apply {
					this.launcher = launcher
				}

				ActivityResultLauncherState(
					contract = contract,
					callback = callback,
					realLauncher = realLauncher,
				) as ActivityResultLauncherState<Any?, Any?>
			} as ActivityResultLauncherState<I, O>

			continuation.invokeOnCancellation {
				_launchersStateByKey.remove(key)?.realLauncher?.unregister()
			}

			launcher.realLauncher.launch(input)
		}
	}
}
