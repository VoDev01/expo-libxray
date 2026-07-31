import ExpoModulesCore

public class ExpoLibxrayModule: Module {
  public func definition() -> ModuleDefinition {
    Name("ExpoLibxray")

    AsyncFunction("setValueAsync") { (value: String) in
    }
  }
}
