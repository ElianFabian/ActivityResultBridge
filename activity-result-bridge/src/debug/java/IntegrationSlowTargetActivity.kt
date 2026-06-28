package com.elianfabian.activity_result_bridge

import android.os.Bundle
import androidx.activity.ComponentActivity

public class IntegrationSlowTargetActivity : ComponentActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		// We do NOT finish immediately to let the test framework cancel the caller coroutine
	}
}
