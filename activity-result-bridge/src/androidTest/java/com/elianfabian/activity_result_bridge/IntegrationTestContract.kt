package com.elianfabian.activity_result_bridge

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract

internal class IntegrationTestContract : ActivityResultContract<String, String>() {

	override fun createIntent(context: Context, input: String): Intent {
		return Intent(context, IntegrationTargetActivity::class.java).putExtra("EXTRA_INPUT", input)
	}

	override fun parseResult(resultCode: Int, intent: Intent?): String {
		return if (resultCode == Activity.RESULT_OK) {
			intent?.getStringExtra("EXTRA_OUTPUT").orEmpty()
		}
		else "CANCELLED"
	}
}
