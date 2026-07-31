import { NativeModule, requireNativeModule } from 'expo';

declare class ExpoLibxrayModule extends NativeModule<{}> {
  setValueAsync(value: string): Promise<void>;
}

export default requireNativeModule<ExpoLibxrayModule>('ExpoLibxray');
