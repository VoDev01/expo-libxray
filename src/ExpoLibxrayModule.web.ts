import { registerWebModule, NativeModule } from 'expo';

// ExpoLibxrayModule is not available on the web platform.
class ExpoLibxrayModule extends NativeModule<{}> {}

export default registerWebModule(ExpoLibxrayModule, 'ExpoLibxrayModule');
