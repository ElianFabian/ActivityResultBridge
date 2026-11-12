plugins {
	alias(libs.plugins.android.library)
}

android {
	namespace = "com.elianfabian.activity_result_bridge"
	compileSdk {
		version = release(36)
	}

	defaultConfig {
		minSdk = 21
		testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

		testInstrumentationRunnerArguments["clearPackageData"] = "true"

		consumerProguardFiles("consumer-rules.keep")
	}
	buildTypes {
		debug {
			//isMinifyEnabled = true
			proguardFiles(
				getDefaultProguardFile("proguard-android-optimize.txt"),
				"src/main/keepRules/rules.keep"
			)
		}
	}
	testOptions {
		execution = "ANDROIDX_TEST_ORCHESTRATOR"
		unitTests.isIncludeAndroidResources = true
	}
	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_11
		targetCompatibility = JavaVersion.VERSION_11
	}
	kotlin {
		explicitApi()
	}
}

dependencies {
	implementation(libs.activityProvider)
	implementation(libs.androidx.activity.ktx)
	implementation(libs.kotlinxCoroutinesAndroid)

	testImplementation(libs.junit)
	testImplementation(libs.robolectric)
	testImplementation(libs.kotlinxCoroutinesTest)
	androidTestUtil(libs.androidx.orchestrator)
	androidTestImplementation(libs.kotlinxCoroutinesTest)
	androidTestImplementation(libs.androidx.uiautomator)
	androidTestImplementation(libs.androidx.espresso.core)
	androidTestImplementation(libs.androidx.junit)
}
