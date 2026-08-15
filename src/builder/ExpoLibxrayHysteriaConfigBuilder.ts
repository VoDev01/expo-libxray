import ExpoLibxrayOutboundsBuilderInterface from './ExpoLibxrayOutboundsBuilderInterface';

type JsonValue = string | number | boolean | null | { [key: string]: JsonValue } | JsonValue[];

function cleanObject(obj: any): any {
  if (obj === null || typeof obj !== 'object') {
    return obj;
  }

  if (Array.isArray(obj)) {
    return obj.map(cleanObject);
  }

  return Object.fromEntries(
    Object.entries(obj)
      .map(([key, val]) => [key, cleanObject(val)])
      .filter(([_, val]) => val !== null && val !== undefined && val !== '')
  );
}

export default class ExpoLibxrayHysteriaConfigBuilder implements ExpoLibxrayOutboundsBuilderInterface {
  private initialConfig: Record<string, any> = {};

  constructor(initialConfig: Record<string, any>) {
    this.initialConfig = initialConfig;
  }

  setOutbounds(outboundTag: string, protocol: string, address?: string, sni?: string): this {
    const originalOutbound = Array.isArray(this.initialConfig.outbounds)
      ? this.initialConfig.outbounds[0]
      : {};

    if (originalOutbound.settings) {
      originalOutbound.settings.address = address ? address : originalOutbound.settings.address;
    }

    const realitySettingsCopy = originalOutbound.streamSettings?.realitySettings
      ? { ...originalOutbound.streamSettings.realitySettings }
      : null;

    realitySettingsCopy.serverName = sni ? sni : realitySettingsCopy.serverName;
    realitySettingsCopy.spiderX = '/';

    const outboundsConfig: JsonValue[] = cleanObject([
      {
        tag: outboundTag,
        protocol: originalOutbound.protocol || protocol,

        streamSettings: originalOutbound.streamSettings
          ? {
              ...originalOutbound.streamSettings,
              realitySettings: realitySettingsCopy,
            }
          : null,
        settings: originalOutbound.settings || null,
      },
      { tag: 'direct', protocol: 'freedom' },
      { tag: 'block', protocol: 'blackhole' },
      { tag: 'dns-out', protocol: 'dns' },
    ]);

    this.initialConfig.outbounds = outboundsConfig;
    return this;
  }

  build(): string {
    return JSON.stringify(this.initialConfig);
  }

  buildWith<T extends ExpoLibxrayOutboundsBuilderInterface>(
    builder: new (config: Record<string, any>) => T
  ): T {
    return new builder(this.initialConfig);
  }
}
