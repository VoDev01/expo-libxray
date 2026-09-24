// Define your exported module types here.

export enum TimeUnit {
  NANOSECONDS = 'NANOSECONDS',
  MICROSECONDS = 'MICROSECONDS',
  MILLISECONDS = 'MILLISECONDS',
  SECONDS = 'SECONDS',
  MINUTES = 'MINUTES',
  HOURS = 'HOURS',
  DAYS = 'DAYS',
}

/**
 * Represents a request to batch ping configurations.
 */
export interface PingBatchRequest {
  /**
   * Array of ping batch items.
   */
  configs: PingBatchItem[];
  /**
   * Timeout for the ping request.
   */
  timeout: number | undefined;
  /**
   * URL for the ping request.
   */
  url: string | undefined;
  locationUrl: string | undefined;
}

/**
 * Represents an item in the ping batch request.
 */
export interface PingBatchItem {
  /**
   * Xray JSON configuration.
   */
  xrayJson: string;
  /**
   * Optional outbound tag for the ping request.
   */
  outboundTag: string | undefined;
}

/**
 * Represents a request to run Xray.
 */
export interface RunXrayRequest {
  /**
   * Xray JSON configuration.
   */
  xrayJson: string;
  /**
   * Apps package names that need to bypass VPN tunnel
   */
  appsSplitTunneling: string[] | undefined;
  /**
   * Localized error message for notification permission.
   */
  notificationErrorLocalized: string | undefined;
  /**
   * Localized error message for VPN service permission.
   */
  vpnServiceErrorLocalized: string | undefined;
  /**
   * Notification title localized
   */
  vpnServiceNotificationTitle: string | undefined;
  /**
   * Notification text content localized
   */
  vpnServiceNotificationContent: string | undefined;
  /**
   * Notification localized text statuses of notification
   */
  vpnServiceNotificationStatus:
    Record<'waiting' | 'connected' | 'error' | 'connecting', string> | undefined;
}

/**
 * Represents the response for running Xray.
 */
export interface RunXrayResponse {
  /**
   * Indicates whether the operation was successful.
   */
  success: boolean;
  /**
   * Optional error message if the operation failed.
   */
  error: string | undefined;
}

/**
 * Represents the response for a batch ping request.
 */
export interface PingBatchResponse {
  /**
   * Array of ping batch item responses.
   */
  results: PingBatchItemResponse[] | undefined;
}

/**
 * Represents the response for an individual item in a batch ping request.
 */
export interface PingBatchItemResponse {
  /**
   * Indicates whether the operation was successful.
   */
  success: boolean;
  /**
   * Delay in milliseconds.
   */
  delay: bigint | undefined;
  /**
   * Optional error message if the operation failed.
   */
  error: string | undefined;
  locationJson: string | undefined;
  locationError: string | undefined;
}
