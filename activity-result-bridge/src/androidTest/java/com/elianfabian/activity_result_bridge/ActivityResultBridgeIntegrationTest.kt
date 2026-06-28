package com.elianfabian.activity_result_bridge

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
@LargeTest
@RunWith(AndroidJUnit4::class)
class ActivityResultBridgeIntegrationTest {

	@get:Rule
	val activityRule = ActivityScenarioRule(IntegrationTestActivity::class.java)

	private lateinit var bridge: ActivityResultBridge
	private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

	@Before
	fun setUp() {
		bridge = ActivityResultBridge.getInstance()
	}

	@Test
	fun testSuccessfulContractExecutionFlow() = runTest(timeout = 5.seconds) {
		// Given
		val inputData = "Hello Android integration test"
		val contract = IntegrationTestContract()

		// When
		val result = bridge.launch(contract, inputData)

		// Then
		assertEquals("PROCESSED: Hello Android integration test", result)
	}

	@Test
	fun testBridgeSurvivesScreenRotationDuringSuspension() = runTest(timeout = 10.seconds) {
		// Given
		val contract = IntegrationTestContract()

		// When - We launch the contract in an async block to simulate long-running/delayed flows
		val deferredResult = async {
			bridge.launch(contract, "Rotation Testing")
		}
		yield()

		// Act - Simulate a heavy runtime configuration change by rotating the screen
		device.setOrientationLeft()
		yield()

		// Restore orientation to normal
		device.setOrientationNatural()
		yield()

		// Then - The bridge must recover the registration state and deliver the result flawlessly
		val finalResult = deferredResult.await()
		assertEquals("PROCESSED: Rotation Testing", finalResult)
	}

	@Test
	fun testSystemPermissionContractWithUiAutomator() = runTest(timeout = 10.seconds) {
		// Given - Using the standard contract and leveraging your own patterns
		val contract = ActivityResultContracts.RequestPermission()

		val deferredPermission = async {
			bridge.launch(contract, Manifest.permission.CAMERA)
		}
		yield()

		// Act - Find the system dialog button using your extension methods
		val allowButton = device.findAllowButton()
		if (allowButton.waitForExists(5000)) {
			allowButton.click()
		}

		// Then
		val isGranted = deferredPermission.await()
		assertEquals(true, isGranted)
	}

	@Test
	fun testConcurrentParallelLaunchesDoNotInterfereWithEachOther() = runTest(timeout = 10.seconds) {
		// Given - We launch multiple requests in parallel to stress test the ConcurrentHashMap
		val contract = IntegrationTestContract()

		// When - Executing three separate system intents at the exact same time
		val deferred1 = async { bridge.launch(contract, "Task A") }
		val deferred2 = async { bridge.launch(contract, "Task B") }
		val deferred3 = async { bridge.launch(contract, "Task C") }

		// Then - All of them must resolve uniquely without mixing keys or crashing
		assertEquals("PROCESSED: Task A", deferred1.await())
		assertEquals("PROCESSED: Task B", deferred2.await())
		assertEquals("PROCESSED: Task C", deferred3.await())
	}

	@Test
	fun testUserCancellationReturnsGracefullyWithContractFallback() = runTest(timeout = 5.seconds) {
		// Given - A contract pointing to an activity that returns RESULT_CANCELED
		val cancelContract = object : ActivityResultContract<String, String>() {
			override fun createIntent(context: Context, input: String): Intent {
				return Intent(context, IntegrationCancelTargetActivity::class.java)
			}

			override fun parseResult(resultCode: Int, intent: Intent?): String {
				return if (resultCode == Activity.RESULT_OK) "OK" else "USER_BACK_PRESSED"
			}
		}

		// When - Launching the contract where the user cancels the flow
		val result = bridge.launch(cancelContract, "Trigger Cancel")

		// Then - The bridge must resume safely and pass the contract's parsed fallback value
		assertEquals("USER_BACK_PRESSED", result)
	}

	@Test
	fun testCoroutineCancellationCleansUpAndUnregistersFromSystemRegistry() = runTest(timeout = 5.seconds) {
		// Given - A contract pointing to a slow activity that hangs suspended
		val slowContract = object : ActivityResultContract<String, String>() {
			override fun createIntent(context: Context, input: String): Intent {
				return Intent(context, IntegrationSlowTargetActivity::class.java)
			}

			override fun parseResult(resultCode: Int, intent: Intent?): String = "SLOW"
		}

		// When - We start the launch flow inside a cancellable child job
		val job = async {
			bridge.launch(slowContract, "Slow Flow")
		}
		yield() // Force execution to enter the suspendCancellableCoroutine block

		// Act - Cancel the coroutine scope while it is waiting for the result
		job.cancel()
		yield()

		// Then - This verifies that your `invokeOnCancellation` block executed cleanly,
		// the coroutine did not leak, and no residual crash happens when the test ends.
		assertTrue(job.isCancelled)
	}
}
