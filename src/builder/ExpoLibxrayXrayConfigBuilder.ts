import ExpoLibxrayInboundBuilderInterface from './ExpoLibxrayBuilderInterface';
import { ConfigRoutingRule } from './ExpoLibxrayBuilderInterface';

type JsonValue = string | number | boolean | null | { [key: string]: JsonValue } | JsonValue[];

export default class ExpoLibxrayXrayConfigBuilder implements ExpoLibxrayInboundBuilderInterface {
  private initialConfig: Record<string, any> = {};

  constructor(initialConfig: any) {
    this.initialConfig = {
      dns: initialConfig.dns,
      env: initialConfig.env,
      inbounds: initialConfig.inbounds,
      outbounds: initialConfig.outbounds,
      routing: initialConfig.routing,
      log: initialConfig.log,
    };
  }

  setInbound(inbound: () => any, inboundTag: string): this {
    this.initialConfig.inboundTag = inboundTag;
    this.initialConfig.inbounds = inbound();
    return this;
  }
  setEnv(assetsDir: string): this {
    const envConfig: Record<string, JsonValue> = {
      'v2ray.location.asset': assetsDir,
      'xray.location.asset': assetsDir,
    };
    this.initialConfig.env = envConfig;
    return this;
  }
  setDns(hosts: Record<string, string>, servers: string[]): this {
    const dnsConfig: Record<string, JsonValue> = {
      hosts,
      servers,
    };
    this.initialConfig.dns = dnsConfig;
    return this;
  }
  setLogging(logLevel: string): this {
    const loggingConfig: Record<string, JsonValue> = {
      loglevel: logLevel,
      maskAddress: 'half',
    };
    this.initialConfig.logging = loggingConfig;
    return this;
  }
  setRouting(routingRules: ConfigRoutingRule[], domainStrategy: string): this {
    const routingConfig: Record<string, JsonValue> = {
      domainStrategy,
      rules: routingRules,
    };
    this.initialConfig.routing = routingConfig;
    return this;
  }
  setOutbounds(outboundTag: string, protocol: string, sendThrough: string): this {
    const originalOutbound = Array.isArray(this.initialConfig.outbounds)
      ? this.initialConfig.outbounds[0]
      : {};

    const realitySettingsCopy = originalOutbound.streamSettings?.realitySettings
      ? { ...originalOutbound.streamSettings.realitySettings }
      : null;

    this.sanitizeRealitySettings(realitySettingsCopy);

    const outboundsConfig: JsonValue[] = [
      {
        sendThrough,
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
    ];

    this.initialConfig.outbounds = outboundsConfig;
    return this;
  }

  private sanitizeRealitySettings(realitySettings: Record<string, any> | null | undefined): void {
    if (!realitySettings || typeof realitySettings !== 'object') {
      return;
    }

    const keysToRemove = [
      'target',
      'dest',
      'type',
      'xver',
      'mldsa65Seed',
      'privateKey',
      'mldsa65Verify',
      'spiderX',
      'minClientVer',
      'maxClientVer',
      'maxTimeDiff',
      'masterKeyLog',
      'serverNames',
      'shortIds',
    ];

    for (const key of keysToRemove) {
      if (key in realitySettings) {
        delete realitySettings[key];
      }
    }
  }
  build(): string {
    return JSON.stringify(this.initialConfig);
  }
}
