import { NativeModule, requireNativeModule } from 'expo';

declare class ExpoLibxrayModule extends NativeModule<{}> {
  convertShareLinksToXrayJson(links: string): Promise<string>;
  runXray(configJson: string): Promise<boolean>;
  stopXray(): Promise<boolean>;
  getXrayState(): Promise<boolean>;
  pingXrayConfig(configJson: string): Promise<string>;
}

export default requireNativeModule<ExpoLibxrayModule>('ExpoLibxray');
