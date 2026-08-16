export default interface LibxrayBuilderInterface {
  setInbounds(inbounds: Record<string, any>[]): this;
  setEnv(assetsDir: string): this;
  setDns(hosts: Record<string, string | string[]>, servers: any[], queryStrategy?: string): this;
  setLogging(logLevel: string): this;
  setOutbounds(outboundsConfig: Record<string, any>[], deleteFields?: string[]): this;
  setRouting(routingRules: Record<string, any>[], inboundTag: string, network: string): this;
  build(): string;
}
