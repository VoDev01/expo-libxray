import { merge, unset } from 'lodash';

import LibxrayBuilderInterface from './LibxrayBuilderInterface';

type JsonValue = string | number | boolean | null | { [key: string]: JsonValue } | JsonValue[];

function cleanObject(obj: any): any {
  if (obj === null || typeof obj !== 'object') {
    if (obj === '') return null;
    return obj;
  }

  if (Array.isArray(obj)) {
    return obj.map(cleanObject).filter((val) => {
      if (val === null || val === undefined || val === '') return false;
      if (typeof val === 'object' && Object.keys(val).length === 0) return false;
      return true;
    });
  }

  const cleanedEntries = Object.entries(obj)
    .map(([key, val]) => [key, cleanObject(val)])
    .filter(([_, val]) => {
      if (val === null || val === undefined || val === '') return false;

      if (Array.isArray(val) && val.length === 0) return false;

      if (typeof val === 'object' && Object.keys(val).length === 0) return false;

      return true;
    });

  return Object.fromEntries(cleanedEntries);
}

export default class LibxrayConfigBuilder implements LibxrayBuilderInterface {
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

  setInbounds(inbounds: Record<string, any>[]): this {
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
  setOutbounds(outboundsConfig: Record<string, any>[], deleteFields?: string[]): this {
    const originalOutbounds = Array.isArray(this.initialConfig.outbounds)
      ? this.initialConfig.outbounds
      : [];

    const finalOutbounds: any[] = [];

    outboundsConfig.forEach((configItem, index) => {
      const originalItem = originalOutbounds[index] || {};

      const mergedItem = merge({}, originalItem, configItem);

      if (deleteFields) {
        deleteFields.forEach((field) => {
          unset(mergedItem, field);
        });
      }

      const cleanedItem = cleanObject(mergedItem);
      if (cleanedItem && Object.keys(cleanedItem).length > 0) {
        finalOutbounds.push(cleanedItem);
      }
    });

    if (originalOutbounds.length > outboundsConfig.length) {
      for (let i = outboundsConfig.length; i < originalOutbounds.length; i++) {
        finalOutbounds.push(originalOutbounds[i]);
      }
    }

    // Записываем строго плоский массив объектов [ { ... } ]
    this.initialConfig.outbounds = finalOutbounds;
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
  build(): string {
    return JSON.stringify(this.initialConfig);
  }
}
