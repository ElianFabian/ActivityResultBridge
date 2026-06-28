package com.elianfabian.activity_result_bridge

import android.os.Bundle
import androidx.activity.ComponentActivity

public class IntegrationCancelTargetActivity : ComponentActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setResult(RESULT_CANCELED)
		finish()
	}
}
