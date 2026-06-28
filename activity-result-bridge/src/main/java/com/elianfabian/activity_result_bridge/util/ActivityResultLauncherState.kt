package com.elianfabian.activity_result_bridge.util

import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContract

internal class ActivityResultLauncherState<I, O>(
	val contract: ActivityResultContract<I, O>,
	val callback: ActivityResultCallback<O>,
	val realLauncher: ActivityResultLauncherHolder<I>,
)
