export type ConfigRoutingRule = {
  domain: string[];
  inboundTag: string[];
  outboundTag: string;
  network: string;
};

export default interface ExpoLibxrayBuilderInterface {
  setInbound(inbound: () => any, inboundTag: string): this;
  setEnv(assetsDir: string): this;
  setDns(hosts: Record<string, string>, servers: string[]): this;
  setLogging(logLevel: string): this;
  setRouting(routingRules: ConfigRoutingRule[], inboundTag: string, network: string): this;
  setOutbounds(outboundTag: string, protocol: string, sendThrough: string): this;
  build(): string;
}
