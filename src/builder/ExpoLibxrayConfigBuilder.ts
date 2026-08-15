import ExpoLibxrayBuilderInterface from './ExpoLibxrayBuilderInterface';

type JsonValue = string | number | boolean | null | { [key: string]: JsonValue } | JsonValue[];

export default class ExpoLibxrayConfigBuilder implements ExpoLibxrayBuilderInterface {
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

  setInbound(inbounds: any[]): this {
    this.initialConfig.inbounds = inbounds;
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
  setDns(hosts: Record<string, string | string[]>, servers: any[], queryStrategy?: string): this {
    const dnsConfig: Record<string, JsonValue> = {
      hosts,
      servers,
    };
    dnsConfig.queryStrategy = queryStrategy ? queryStrategy : null;
    this.initialConfig.dns = dnsConfig;
    return this;
  }
  setLogging(logLevel: string): this {
    const loggingConfig: Record<string, JsonValue> = {
      loglevel: logLevel,
      maskAddress: 'half',
    };
    this.initialConfig.log = loggingConfig;
    return this;
  }
  setRouting(routingRules: Record<string, any>[], domainStrategy: string): this {
    const routingConfig: Record<string, JsonValue> = {
      domainStrategy,
      rules: routingRules,
    };
    this.initialConfig.routing = routingConfig;
    return this;
  }
  build(): Record<string, any> {
    return this.initialConfig;
  }
}
