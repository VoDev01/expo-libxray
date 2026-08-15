export default interface ExpoLibxrayOutboundsBuilderInterface {
  setOutbounds(outboundTag: string, protocol: string, address?: string, sni?: string): this;
  build(): string;
  buildWith<T extends ExpoLibxrayOutboundsBuilderInterface>(
    builder: new (config: Record<string, any>) => T
  ): T;
}
