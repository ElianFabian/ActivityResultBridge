package com.elianfabian.activity_result_bridge

import androidx.activity.result.contract.ActivityResultContract
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * A test double (Fake) implementation of [ActivityResultBridge] designed for isolated Unit Testing.
 *
 * It simulates Android's Activity Result system entirely in-memory without spinning up
 * heavy instrumented environments, allowing rapid verification of ViewModels or UseCases
 * under both synchronous and asynchronous testing paradigms.
 */
public class FakeActivityResultBridge : ActivityResultBridge {

	private var _continuation: CancellableContinuation<Any?>? = null
	private var _mockedResult: Any? = NoValue


	/**
	 * Enqueues or immediately dispatches a mocked result to the suspended [launch] coroutine.
	 *
	 * - **Asynchronous Flow:** If [launch] has already been called and is currently suspended,
	 * this method immediately resumes the coroutine with the provided [result].
	 * - **Synchronous Flow:** If [launch] hasn't been reached yet, it caches the [result]
	 * to be consumed instantly when [launch] gets triggered.
	 *
	 * @param O The expected output type. It must match the output type [O] of the targeted contract.
	 * @param result The fake value that the bridge will return to the active execution flow.
	 */
	public fun <O> sendMockedResult(result: O) {
		val pendingContinuation = _continuation
		if (pendingContinuation != null) {
			_continuation = null
			if (pendingContinuation.isActive) {
				pendingContinuation.resume(result)
			}
		}
		else {
			_mockedResult = result
		}
	}

	/**
	 * Simulates the execution of an [ActivityResultContract] in a test environment.
	 *
	 * If a mocked result was pre-configured via [sendMockedResult], this method consumes it
	 * and resumes synchronously. Otherwise, it captures the current coroutine context and suspends
	 * execution until [sendMockedResult] is invoked externally.
	 */
	override suspend fun <I, O> launch(contract: ActivityResultContract<I, O>, input: I): O {

		@Suppress("UNCHECKED_CAST")
		return suspendCancellableCoroutine { continuation ->
			if (_mockedResult != NoValue) {
				val currentMockedResult = _mockedResult
				_mockedResult = NoValue
				continuation.resume(currentMockedResult as O)
			}
			else {
				_continuation = continuation as CancellableContinuation<Any?>?
				continuation.invokeOnCancellation {
					_continuation = null
				}
			}
		}
	}
}

private object NoValue
