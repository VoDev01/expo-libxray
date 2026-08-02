import { NativeModule, requireNativeModule } from 'expo';

declare class ExpoLibxrayModule extends NativeModule<{}> {
  convertShareLinksToXrayJson(links: string): Promise<string>;
  runXrayFromJson(json: string): Promise<string>;
  stopXray(): Promise<void>;
  getXrayState(): Promise<boolean>;
  pingXrayConfig(json: string): Promise<string>;
  requestVpnPermission(): Promise<boolean>;
}

export default requireNativeModule<ExpoLibxrayModule>('ExpoLibxray');
