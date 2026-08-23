import { NativeModule, requireNativeModule } from 'expo';
import {
  PingBatchRequest,
  PingBatchResponse,
  RunXrayRequest,
  RunXrayResponse,
  TestXrayResponse,
} from './ExpoLibxray.types';

/**
 * ExpoLibxrayModule is a native module for interacting with Xray.
 */
declare class ExpoLibxrayModule extends NativeModule<{}> {
  /**
   * Converts share links to Xray JSON format.
   * @param links - The share links to convert.
   * @returns A promise that resolves to a string representing the Xray JSON.
   */
  convertShareLinksToXrayJson(links: string): Promise<string>;

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
   * @returns A promise that resolves to a TestXrayResponse object.
   */
  testXray(configJson: string): Promise<TestXrayResponse>;

  /**
   * Gets the version of Xray.
   * @returns A promise that resolves to a string representing the Xray version.
   */
  xrayVersion(): Promise<string>;
}

export default requireNativeModule<ExpoLibxrayModule>('ExpoLibxray');
