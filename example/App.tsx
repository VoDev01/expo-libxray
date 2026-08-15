import ExpoLibxray, {
  ExpoLibxrayXrayConfigBuilder,
  ExpoLibxrayHysteriaConfigBuilder,
  ExpoLibxrayConfigBuilder,
} from 'expo-libxray';
import { useState } from 'react';
import { Button, SafeAreaView, ScrollView, StyleSheet, Text, View } from 'react-native';
import { Paths } from 'expo-file-system';

export default function App() {
  const [text, setText] = useState<string>('Hello world!');
  return (
    <SafeAreaView style={styles.container}>
      <ScrollView style={styles.container}>
        <Text style={styles.header}>Module API Example</Text>
        <Group name="Async functions">
          <Text>{text}</Text>
          <Button
            title="Start"
            onPress={() => {
              startXray((text) => {
                setText(text);
              });
            }}
          />
          <Button
            title="Stop"
            onPress={() => {
              stopXray((text) => {
                setText(text);
              });
            }}
          />
        </Group>
      </ScrollView>
    </SafeAreaView>
  );
}

async function startXray(setText: (text: string) => void) {
  try {
    const appFilesDir = Paths.document;
    const resp = await ExpoLibxray.convertShareLinksToXrayJson(
      'vless://2aa237f9-b6ae-4fd2-b84c-34b1c70860e6@45.137.42.1:8443?security=reality&type=tcp&headerType=&flow=xtls-rprx-vision&path=&host=cloudflare.com&sni=cloudflare.com&fp=chrome&pbk=zSq9yPlKXHUx6CF5ucRP5lCRuGIh-4rEv-2RCdgbbkw&sid=595726f129092382#VLESS_tcp'
    );
    const responseObj = JSON.parse(resp);

    if (responseObj.success && responseObj.data) {
      const baseConfig = new ExpoLibxrayConfigBuilder(responseObj.data)
        .setLogging('debug')
        .setEnv(appFilesDir.uri.replace('file://', ''))
        .setInbound([
          {
            tag: 'SOCKS LOCAL',
            listen: '127.0.0.1',
            port: '10808',
            protocol: 'socks',
            settings: {
              auth: 'noauth',
              udp: true,
              ip: '127.0.0.1',
              userLevel: 0,
            },
            sniffing: {
              enabled: true,
              destOverride: ['http', 'tls', 'quic'],
            },
          },
        ])
        .setDns(
          { 'domain-!ru': ['8.8.8.8', '1.1.1.1'] },
          [
            '8.8.8.8',
            '1.1.1.1',
            {
              address: '8.8.8.8',
              port: 53,
              queryStrategy: 'UseIPv4',
            },
            {
              address: '1.1.1.1',
              port: 53,
              queryStrategy: 'UseIPv4',
            },
          ],
          'UseIPv4'
        )
        .setRouting(
          [
            {
              type: 'field',
              network: 'tcp,udp',
              inboundTag: ['SOCKS LOCAL'],
              outboundTag: 'VLESS TCP REALITY',
            },
            {
              type: 'field',
              inboundTag: ['SOCKS LOCAL'],
              outboundTag: 'dns-out',
              port: 53,
            },
            {
              type: 'field',
              domain: ['geosite:category-ads-all'],
              inboundTag: ['SOCKS LOCAL'],
              outboundTag: 'block',
            },
            {
              type: 'field',
              outboundTag: 'direct',
              inboundTag: ['SOCKS LOCAL'],
              protocol: ['bittorrent'],
            },
            {
              type: 'field',
              domain: ['geosite:ru-available-only-inside'],
              inboundTag: ['SOCKS LOCAL'],
              outboundTag: 'direct',
            },
          ],
          'AsIs'
        )
        .build();

      const config = new ExpoLibxrayXrayConfigBuilder(baseConfig)
        .setOutbounds('VLESS TCP REALITY', 'vless')
        .buildWith(ExpoLibxrayHysteriaConfigBuilder)
        .setOutbounds('HYSTERIA', 'hysteria')
        .build();

      const result = await ExpoLibxray.runXray(config);
      setText(result ? 'Success' : 'Fail');
    } else {
      setText(`Ошибка конвертации: ${responseObj.error}`);
    }
  } catch (error) {
    setText(`Ошибка при запуске Xray: ${(error as Error).message}`);
  }
}

async function stopXray(setText: (text: string) => void) {
  try {
    const resp = await ExpoLibxray.stopXray();
    setText(resp ? 'Stopping' : 'Fail');
  } catch (error) {
    setText(`Ошибка при остановке Xray: ${(error as Error).message}`);
  }
}

function Group(props: { name: string; children: React.ReactNode }) {
  return (
    <View style={styles.group}>
      <Text style={styles.groupHeader}>{props.name}</Text>
      <View style={styles.groupContainer}>{props.children}</View>
    </View>
  );
}

const styles = StyleSheet.create({
  header: { fontSize: 30, margin: 20 },
  groupHeader: { fontSize: 20, marginBottom: 20 },
  group: { margin: 20, backgroundColor: '#fff', borderRadius: 10, padding: 20 },
  groupContainer: { flexDirection: 'column', rowGap: 10 },
  container: { flex: 1, backgroundColor: '#eee' },
  view: { flex: 1, height: 200 },
});
