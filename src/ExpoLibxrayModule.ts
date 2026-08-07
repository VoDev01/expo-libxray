import { NativeModule, requireNativeModule } from 'expo';

declare class ExpoLibxrayModule extends NativeModule<{}> {
  convertShareLinksToXrayJson(links: string): Promise<string>;
  runXray(json: string): Promise<string>;
  stopXray(): Promise<boolean>;
  getXrayState(): Promise<boolean>;
  pingXrayConfig(json: string): Promise<string>;
}

export default requireNativeModule<ExpoLibxrayModule>('ExpoLibxray');
