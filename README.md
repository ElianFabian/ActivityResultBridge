# Activity Result Bridge

[![JitPack](https://jitpack.io/v/ElianFabian/ActivityResultBridge.svg)](https://jitpack.io/project/ElianFabian/ActivityResultBridge)

A lightweight Kotlin library that decouples Android's Activity Result API from the UI layer (Activity/Fragment) by leveraging Kotlin Coroutines. It abstracts the traditional lifecycle-bound registration and callback mechanics into a clean, modern suspending function.

## Features

- **🚀 Coroutine Powered**: Launch activity results and suspend until the result is delivered.
- **🏗️ Architecture Friendly**: Keep your ViewModels clean from Activity references.
- **🔄 Lifecycle Safe**: Automatically handles registration tokens and ensures safety against configuration changes (like screen rotation).
- **🧪 Test Ready**: Includes a `FakeActivityResultBridge` for isolated unit testing.
- **📦 Seamless Integration**: Uses the standard `ActivityResultContract` from Android Jetpack.

## Installation

Add it in your root `build.gradle` at the end of repositories:

Add the JitPack repository to your `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { setUrl("https://jitpack.io") }
    }
}
```

Add the dependency to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.ElianFabian:ActivityResultBridge:$version")
}
```

## Quick Start

### 1. Launch from a ViewModel

Inject the `ActivityResultBridge` into your ViewModel and use the `launch` suspending function.

```kotlin
class MainViewModel(
    private val activityResultBridge: ActivityResultBridge = ActivityResultBridge.getInstance(),
) : ViewModel() {

    fun requestCameraPermission() {
        viewModelScope.launch {
            val isGranted: Boolean = activityResultBridge.launch(
                contract = ActivityResultContracts.RequestPermission(),
                input = Manifest.permission.CAMERA,
            )
            // Handle result...
        }
    }

    fun pickFile() {
        viewModelScope.launch {
            val uri: Uri? = activityResultBridge.launch(
                contract = ActivityResultContracts.GetContent(),
                input = "*/*",
            )
            // Handle result...
        }
    }
}
```

## Unit Testing

The library provides `FakeActivityResultBridge` to simulate Android's Activity Result system in-memory.

```kotlin
class MainViewModelTest {

    private lateinit var fakeBridge: FakeActivityResultBridge
    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        fakeBridge = ActivityResultBridge.newMockedActivityResultBridge()
        viewModel = MainViewModel(fakeBridge)
    }

    @Test
    fun `when permission is granted then update state`() = runTest {
        // Enqueue the fake result
        fakeBridge.sendMockedResult(true)

        viewModel.requestCameraPermission()

        // Verify your state...
    }
}
```

## How it works

The bridge uses a singleton `ActivityResultBridgeImpl` that tracks the current `ComponentActivity` via an internal `ActivityProvider`. When you call `launch`, it registers a temporary callback in the activity's `ActivityResultRegistry` and suspends the coroutine. Once the result is received, the coroutine resumes and the launcher is automatically unregistered.

## License

```text
Copyright 2024 Elian Fabian

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
