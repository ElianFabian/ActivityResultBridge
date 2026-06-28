package com.elianfabian.activity_result_bridge

import androidx.activity.result.contract.ActivityResultContract

/**
 * A bridge that decouples Android's Activity Result API from the UI layer (Activity/Fragment)
 * by leveraging Kotlin Coroutines. It abstracts the traditional lifecycle-bound registration
 * and callback mechanics into a clean, modern suspending function.
 */
public interface ActivityResultBridge {

	/**
	 * Launches an asynchronous activity result contract and suspends execution until the result is delivered.
	 *
	 * This method handles the registration token under the hood and ensures safety against
	 * common Android runtime anomalies like runtime configuration changes (screen rotation).
	 *
	 * @param I The input type required by the contract.
	 * @param O The expected output type returned by the contract.
	 * @param contract The [ActivityResultContract] defining the intent creation and result parsing logic.
	 * @param input The input parameters passed to the target activity.
	 * @return The parsed result [O] from the executed activity contract.
	 */
	public suspend fun <I, O> launch(contract: ActivityResultContract<I, O>, input: I): O


	public companion object {

		private val _instance by lazy { ActivityResultBridgeImpl() }

		/**
		 * Provides the thread-safe, lazily initialized production instance of the bridge.
		 */
		public fun getInstance(): ActivityResultBridge = _instance

		/**
		 * Factory method that creates a new isolated instance of [FakeActivityResultBridge].
		 * * @return A standalone [FakeActivityResultBridge] instance dedicated for unit testing.
		 */
		public fun newMockedActivityResultBridge(): FakeActivityResultBridge = FakeActivityResultBridge()
	}
}
