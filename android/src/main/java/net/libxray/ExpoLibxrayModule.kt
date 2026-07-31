package net.libxray

import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

class ExpoLibxrayModule : Module() {
  override fun definition() = ModuleDefinition {
    Name("ExpoLibxray")

    AsyncFunction("setValueAsync") { value: String ->
    }
  }
}
