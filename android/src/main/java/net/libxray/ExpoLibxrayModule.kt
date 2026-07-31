package net.libxray

import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import kotlinx.coroutines.*

class ExpoLibxrayModule : Module() {
  override fun definition() = ModuleDefinition {
    Name("ExpoLibxray")

    AsyncFunction("setValueAsync") { value: String ->
      delay(2000L)
      "Hello " + value
    }
  }
}
