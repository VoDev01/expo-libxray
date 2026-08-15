export default interface ExpoLibxrayBuilderInterface {
  setInbound(inbounds: any[]): this;
  setEnv(assetsDir: string): this;
  setDns(hosts: Record<string, string | string[]>, servers: any[], queryStrategy?: string): this;
  setLogging(logLevel: string): this;
  setRouting(routingRules: Record<string, any>[], inboundTag: string, network: string): this;
  build(): Record<string, any>;
}
