package com.elianfabian.activity_result_bridge

import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector

fun UiDevice.findAllowButton() = findObject(
	UiSelector().textMatches("(?i)Allow|While using the app|Precise")
)
