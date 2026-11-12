package com.elianfabian.activity_result_bridge

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.time.Duration.Companion.seconds
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ActivityResultBridgeImplTest {
    private val activities = MutableStateFlow<ComponentActivity?>(null)
    private val controllers = mutableListOf<ActivityController<ComponentActivity>>()

    @After fun tearDown() {
        controllers.filter { !it.get().isDestroyed }.forEach { it.pause().stop().destroy() }
        Dispatchers.resetMain()
    }

    private fun activity(savedState: Bundle? = null): ActivityController<ComponentActivity> =
        Robolectric.buildActivity(ComponentActivity::class.java).also { controllers.add(it) }
            .create(savedState).start().resume()

    // Inspect AndroidX registrations to detect retained callbacks without relying on GC timing.
    private fun callbacks(activity: ComponentActivity): Map<*, *> {
        val field = ActivityResultRegistry::class.java.getDeclaredField("keyToCallback")
        field.isAccessible = true
        return field.get(activity.activityResultRegistry) as Map<*, *>
    }

    private fun pendingCount(bridge: ActivityResultBridgeImpl): Int {
        val field = bridge.javaClass.getDeclaredField("_launchersStateByKey")
        field.isAccessible = true
        return (field.get(bridge) as Map<*, *>).size
    }

    private open class Contract : ActivityResultContract<String, String>() {
        override fun createIntent(context: Context, input: String) = Intent("test.result").putExtra("input", input)
        override fun parseResult(resultCode: Int, intent: Intent?) =
            if (resultCode == Activity.RESULT_OK) intent?.getStringExtra("result") ?: "empty" else "cancelled"
    }

    @Test fun `result completes request and removes callback`() = runTest(timeout = 5.seconds) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = activity()
        activities.value = owner.get()
        val bridge = ActivityResultBridgeImpl(activities, backgroundScope)
        val result = async { bridge.launch(Contract(), "input") }
        runCurrent()
        val code = shadowOf(owner.get()).nextStartedActivityForResult.requestCode
        owner.get().activityResultRegistry.dispatchResult(code, Activity.RESULT_OK, Intent().putExtra("result", "ok"))
        assertEquals("ok", result.await())
        assertTrue(callbacks(owner.get()).isEmpty())
        assertEquals(0, pendingCount(bridge))
    }

    @Test fun `launch exception removes request and callback`() = runTest(timeout = 5.seconds) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = activity()
        activities.value = owner.get()
        val bridge = ActivityResultBridgeImpl(activities, backgroundScope)
        val failure = IllegalStateException("cannot launch")
        val contract = object : Contract() {
            override fun createIntent(context: Context, input: String): Intent = throw failure
        }
        val error = runCatching { bridge.launch(contract, "input") }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertEquals(failure.message, error?.message)
        assertTrue(callbacks(owner.get()).isEmpty())
        assertEquals(0, pendingCount(bridge))
    }

    @Test fun `cancellation from background removes request and callback`() = runTest(timeout = 5.seconds) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = activity()
        activities.value = owner.get()
        val bridge = ActivityResultBridgeImpl(activities, backgroundScope)
        val result = async { bridge.launch(Contract(), "input") }
        runCurrent()
        assertEquals(1, callbacks(owner.get()).size)
        withContext(Dispatchers.Default) { result.cancel() }
        result.join()
        assertTrue(callbacks(owner.get()).isEmpty())
        assertEquals(0, pendingCount(bridge))
    }

    @Test fun `destroying owner detaches callback even if flow skips null`() = runTest(timeout = 5.seconds) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = activity()
        activities.value = owner.get()
        val bridge = ActivityResultBridgeImpl(activities, backgroundScope)
        val result = async { bridge.launch(Contract(), "input") }
        runCurrent()
        owner.pause().stop().destroy()
        assertTrue(callbacks(owner.get()).isEmpty())
        assertFalse(result.isCompleted)
        result.cancelAndJoin()
        assertEquals(0, pendingCount(bridge))
    }

    @Test fun `null activity detaches registration and preserves pending request`() = runTest(timeout = 5.seconds) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = activity()
        activities.value = owner.get()
        val bridge = ActivityResultBridgeImpl(activities, backgroundScope)
        val result = async { bridge.launch(Contract(), "input") }
        runCurrent()
        activities.value = null
        runCurrent()
        assertTrue(callbacks(owner.get()).isEmpty())
        assertEquals(1, pendingCount(bridge))
        result.cancelAndJoin()
    }

    @Test fun `recreation preserves request code and completes on replacement`() = runTest(timeout = 5.seconds) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = activity()
        activities.value = owner.get()
        val bridge = ActivityResultBridgeImpl(activities, backgroundScope)
        val result = async { bridge.launch(Contract(), "input") }
        runCurrent()
        val code = shadowOf(owner.get()).nextStartedActivityForResult.requestCode
        val savedState = Bundle()
        owner.pause().saveInstanceState(savedState).stop().destroy()
        activities.value = null
        val replacement = activity(savedState)
        activities.value = replacement.get()
        replacement.get().activityResultRegistry.dispatchResult(code, Activity.RESULT_OK, Intent().putExtra("result", "restored"))
        assertEquals("restored", result.await())
        assertTrue(callbacks(owner.get()).isEmpty())
        assertTrue(callbacks(replacement.get()).isEmpty())
    }

    @Test fun `result delivered during registration does not leave orphan callback`() = runTest(timeout = 5.seconds) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = activity()
        activities.value = owner.get()
        val bridge = ActivityResultBridgeImpl(activities, backgroundScope)
        val result = async { bridge.launch(Contract(), "input") }
        runCurrent()
        val code = shadowOf(owner.get()).nextStartedActivityForResult.requestCode
        val savedState = Bundle()
        owner.pause().saveInstanceState(savedState).stop().destroy()
        activities.value = null
        val replacement = activity(savedState)
        replacement.get().activityResultRegistry.dispatchResult(code, Activity.RESULT_OK, Intent().putExtra("result", "pending"))
        activities.value = replacement.get()
        assertEquals("pending", result.await())
        assertTrue(callbacks(replacement.get()).isEmpty())
        assertEquals(0, pendingCount(bridge))
    }

    @Test fun `background caller executes Android contract on main`() = runTest(timeout = 5.seconds) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = activity()
        activities.value = owner.get()
        val bridge = ActivityResultBridgeImpl(activities, backgroundScope)
        var usedMain = false
        val failure = IllegalStateException("thread probe")
        val contract = object : Contract() {
            override fun createIntent(context: Context, input: String): Intent {
                usedMain = Looper.myLooper() == Looper.getMainLooper()
                throw failure
            }
        }
        // A queued Main dispatcher lets the test thread execute Android work from a background caller.
        Dispatchers.setMain(kotlinx.coroutines.test.StandardTestDispatcher(testScheduler))
        val error = withContext(Dispatchers.Default) { runCatching { bridge.launch(contract, "input") }.exceptionOrNull() }
        assertTrue(error is IllegalStateException)
        assertEquals(failure.message, error?.message)
        assertTrue(usedMain)
        assertEquals(0, pendingCount(bridge))
    }

    @Test fun `concurrent requests remain independent and system cancellation is returned`() = runTest(timeout = 5.seconds) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = activity()
        activities.value = owner.get()
        val bridge = ActivityResultBridgeImpl(activities, backgroundScope)
        val first = async { bridge.launch(Contract(), "first") }
        val second = async { bridge.launch(Contract(), "second") }
        runCurrent()
        val firstIntent = shadowOf(owner.get()).nextStartedActivityForResult
        val secondIntent = shadowOf(owner.get()).nextStartedActivityForResult
        val firstCode = if (firstIntent.intent.getStringExtra("input") == "first") firstIntent.requestCode else secondIntent.requestCode
        val secondCode = if (firstIntent.intent.getStringExtra("input") == "second") firstIntent.requestCode else secondIntent.requestCode
        assertNotEquals(firstCode, secondCode)
        owner.get().activityResultRegistry.dispatchResult(secondCode, Activity.RESULT_CANCELED, null)
        assertEquals("cancelled", second.await())
        assertFalse(first.isCompleted)
        owner.get().activityResultRegistry.dispatchResult(firstCode, Activity.RESULT_OK, Intent().putExtra("result", "first"))
        assertEquals("first", first.await())
        assertTrue(callbacks(owner.get()).isEmpty())
    }

    @Test fun `synchronous contract result is returned and registration is removed`() = runTest(timeout = 5.seconds) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = activity()
        activities.value = owner.get()
        val bridge = ActivityResultBridgeImpl(activities, backgroundScope)
        val contract = object : Contract() {
            override fun getSynchronousResult(context: Context, input: String) = SynchronousResult("cached")
        }
        val result = async { bridge.launch(contract, "input") }
        runCurrent()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("cached", result.await())
        assertTrue(callbacks(owner.get()).isEmpty())
        assertEquals(0, pendingCount(bridge))
    }

    @Test fun `replaying several pending results safely removes requests during collection`() = runTest(timeout = 5.seconds) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = activity()
        activities.value = owner.get()
        val bridge = ActivityResultBridgeImpl(activities, backgroundScope)
        val requests = listOf("first", "second").associateWith { input -> async { bridge.launch(Contract(), input) } }
        runCurrent()
        val launches = List(2) { shadowOf(owner.get()).nextStartedActivityForResult }
        val savedState = Bundle()
        owner.pause().saveInstanceState(savedState).stop().destroy()
        val replacement = activity(savedState)
        launches.forEach { launch ->
            replacement.get().activityResultRegistry.dispatchResult(launch.requestCode, Activity.RESULT_OK,
                Intent().putExtra("result", launch.intent.getStringExtra("input")))
        }
        activities.value = replacement.get()
        requests.forEach { (input, result) -> assertEquals(input, result.await()) }
        assertTrue(callbacks(replacement.get()).isEmpty())
        assertEquals(0, pendingCount(bridge))
    }

    @Test fun `failed replay cleans registration and collector handles later requests`() = runTest(timeout = 5.seconds) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = activity()
        activities.value = owner.get()
        val bridge = ActivityResultBridgeImpl(activities, backgroundScope)
        val badContract = object : Contract() {
            override fun parseResult(resultCode: Int, intent: Intent?): String = error("bad result")
        }
        val failed = async { runCatching { bridge.launch(badContract, "input") } }
        runCurrent()
        val code = shadowOf(owner.get()).nextStartedActivityForResult.requestCode
        val savedState = Bundle()
        owner.pause().saveInstanceState(savedState).stop().destroy()
        val replacement = activity(savedState)
        replacement.get().activityResultRegistry.dispatchResult(code, Activity.RESULT_OK, Intent())
        activities.value = replacement.get()
        assertEquals("bad result", failed.await().exceptionOrNull()?.message)
        assertTrue(callbacks(replacement.get()).isEmpty())
        assertEquals(0, pendingCount(bridge))

        val next = async { bridge.launch(Contract(), "next") }
        runCurrent()
        val nextCode = shadowOf(replacement.get()).nextStartedActivityForResult.requestCode
        replacement.get().activityResultRegistry.dispatchResult(nextCode, Activity.RESULT_OK, Intent().putExtra("result", "ok"))
        assertEquals("ok", next.await())
        assertTrue(callbacks(replacement.get()).isEmpty())
    }
}
