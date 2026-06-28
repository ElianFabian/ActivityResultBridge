package com.elianfabian.activity_result_bridge

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

public class IntegrationTargetActivity : ComponentActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		val input = intent.getStringExtra("EXTRA_INPUT").orEmpty()
		val resultIntent = Intent().putExtra("EXTRA_OUTPUT", "PROCESSED: $input")
		setResult(RESULT_OK, resultIntent)
		finish() // Auto-finish to return to the bridge instantly
	}
}
