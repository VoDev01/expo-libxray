import { NativeModule, requireNativeModule } from 'expo';
import {
  PingBatchRequest,
  PingBatchResponse,
  RunXrayRequest,
  RunXrayResponse,
} from './ExpoLibxray.types';

export type VpnStatusEvent = {
  status: 'CONNECTED' | 'CONNECTING' | 'ERROR' | 'DISCONNECTED';
  error?: string;
};

/**
 * ExpoLibxrayModule is a native module for interacting with Xray.
 */
declare class ExpoLibxrayModule extends NativeModule<{}> {
  /**
   * Converts share links to Xray JSON format.
   * @param links - The share links to convert.
   * @returns A promise that resolves to json string, which contains InvokeResponse object with success, data, error fields.
   */
  convertShareLinksToXrayJson(links: string): Promise<string>;

  /**
   * Converts Xray JSON to share link.
   * @param xrayJson - The Xray JSON to convert.
   * @returns A promise that resolves to json string, which contains InvokeResponse object with success, data, error fields.
   */
  convertXrayJsonToShareLinks(xrayJson: string): Promise<string>;

  /**
   * Runs Xray with the provided request.
   * @param request - The request for running Xray.
   * @returns A promise that resolves to a RunXrayResponse object.
   */
  runXray(request: RunXrayRequest): Promise<RunXrayResponse>;

  /**
   * Stops Xray.
   * @returns A promise that resolves to a boolean indicating success.
   */
  stopXray(): Promise<boolean>;

  /**
   * Stops Xray .
   * @returns A promise that resolves to a boolean indicating success.
   */
  stopXray(): Promise<boolean>;

  /**
   * Gets the current state of Xray.
   * @returns A promise that resolves to a boolean indicating whether Xray is running.
   */
  getXrayState(): Promise<boolean>;

  /**
   * Pings multiple configurations in a batch.
   * @param request - The request for batch pinging.
   * @returns A promise that resolves to a PingBatchResponse object.
   * @throws Error if the request contains more than 5 configurations.
   */
  pingBatch(request: PingBatchRequest): Promise<PingBatchResponse>;

  /**
   * Tests Xray configuration.
   * @param configJson - The configuration JSON for testing.
   * @returns A promise that resolves to generic json string, which contains a generic InvokeResponse object with success, data, error fields.
   */
  testXray(configJson: string): Promise<string>;

  /**
   * Gets the version of Xray.
   * @returns A promise that resolves to a string representing the Xray version.
   */
  xrayVersion(): Promise<string>;

  addListener(
    eventName: 'onVpnStatusChange',
    listener: (event: VpnStatusEvent) => void
  ): { remove: () => void };
}

export default requireNativeModule<ExpoLibxrayModule>('ExpoLibxray');
