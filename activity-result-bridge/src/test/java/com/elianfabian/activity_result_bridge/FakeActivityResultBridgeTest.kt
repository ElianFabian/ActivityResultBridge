package com.elianfabian.activity_result_bridge

import androidx.activity.result.contract.ActivityResultContract
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FakeActivityResultBridgeTest {

	private lateinit var fakeBridge: FakeActivityResultBridge

	// Pure JVM stub for the contract since the framework implementation is not needed
	private val dummyContract = object : ActivityResultContract<String, Int>() {
		override fun createIntent(context: android.content.Context, input: String): android.content.Intent = error("Stub")
		override fun parseResult(resultCode: Int, intent: android.content.Intent?): Int = error("Stub")
	}

	@Before
	fun setUp() {
		fakeBridge = ActivityResultBridge.newMockedActivityResultBridge()
	}

	@Test
	fun `launch returns immediately when result is pre-mocked`() = runTest {
		// Given
		val expectedResult = 42
		fakeBridge.sendMockedResult(expectedResult)

		// When
		val actualResult = fakeBridge.launch(dummyContract, "any_input")

		// Then
		assertEquals(expectedResult, actualResult)
	}

	@Test
	fun `launch suspends and resumes correctly when result is sent asynchronously`() = runTest {
		// Given
		var asynchronousResult: Int? = null

		// When - Launching in a child coroutine to trigger suspension
		val job = launch {
			asynchronousResult = fakeBridge.launch(dummyContract, "async_input")
		}
		runCurrent() // Yield execution to reach the suspension point

		// Then - Verification that it is waiting and hasn't returned yet
		assertNull(asynchronousResult)

		// When - Simulating the asynchronous OS response
		fakeBridge.sendMockedResult(100)
		runCurrent() // Progress the coroutine event loop

		// Then
		assertEquals(100, asynchronousResult)
		job.join()
	}

	@Test
	fun `mocked result is consumed atomically and does not leak to subsequent launches`() = runTest {
		// Given
		fakeBridge.sendMockedResult(77)

		// When - First execution consumes the pre-mocked value
		val firstResult = fakeBridge.launch(dummyContract, "first_launch")
		assertEquals(77, firstResult)

		// When - Second execution should suspend because NoValue was restored
		var secondResult: Int? = null
		val job = launch {
			secondResult = fakeBridge.launch(dummyContract, "second_launch")
		}
		runCurrent()

		// Then - Asserts it did not leak or reuse the previous '77'
		assertNull(secondResult)

		// Clean up the suspended job safely
		job.cancel()
	}

	@Test
	fun `cancellation handles gracefully and prevents dead continuation resumes`() = runTest {
		// When - Launching a suspendable action
		val job = launch {
			fakeBridge.launch(dummyContract, "cancellable_input")
		}
		runCurrent()

		// Act - Cancelling the caller scope
		job.cancel()
		runCurrent()

		// Then - Sending a result post-cancellation should be safe and not crash the execution
		fakeBridge.sendMockedResult(999)
		runCurrent()
	}

	@Test(expected = ClassCastException::class)
	fun `launch throws ClassCastException when synchronous mocked result type mismatches contract output`() = runTest {
		// Given - The dummyContract expects an Int, but we mock a String
		val wrongMockedResult = "Not An Integer"
		fakeBridge.sendMockedResult(wrongMockedResult)

		// When - This will throw a ClassCastException at the call site
		val actualResult: Int = fakeBridge.launch(dummyContract, "any_input")
	}

	@Test(expected = ClassCastException::class)
	fun `launch throws ClassCastException when asynchronous mocked result type mismatches contract output`() = runTest {
		// When - Launching the coroutine that expects an Int
		val job = launch {
			val actualResult: Int = fakeBridge.launch(dummyContract, "async_input")
		}
		runCurrent()

		// Act - Sending a String instead of an Int asynchronously
		fakeBridge.sendMockedResult("This is a String")
		runCurrent()

		// Then - The job will fail and throw the ClassCastException up to the test scope
		job.join()
	}
}
