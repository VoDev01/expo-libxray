import { Paths } from 'expo-file-system';
import ExpoLibxray, {
  LibxrayConfigBuilder,
  PingBatchItem,
  PingBatchRequest,
  RunXrayRequest,
  TimeUnit,
} from 'expo-libxray';
import { EventSubscription, EventEmitter } from 'expo-modules-core';
import { useEffect, useState } from 'react';
import { ActivityIndicator, Button, SafeAreaView, ScrollView, StyleSheet, Text, View } from 'react-native';

const xrayLink =
  'vless://1ceed667-7895-4750-99aa-fe2d7dd91c8d@85.192.60.109:8443?encryption=none&extra=%7B%22mode%22%3A%22stream-up%22%2C%22xPaddingBytes%22%3A%22100-1000%22%7D&fp=chrome&host=dl.google.com&mode=stream-up&path=%2Fchrome%2Fupdate&pbk=Y_h7Eekek0kE78qYrlrhbotdEgsf2NgNer3TALAyXzM&security=reality&sid=ed11541a6dbfa616&sni=youtu.be&spx=%2Fe00230f58dd174f&type=xhttp&x_padding_bytes=100-1000#VLESS%20REALITY%20XHTTP-w8vou5sxlt';

export default function App() {
  const [text, setText] = useState<string>('Hello world!');

  const [vpnState, setVpnState] = useState<'DISCONNECTED' | 'CONNECTING' | 'CONNECTED' | 'ERROR'>(
    'DISCONNECTED'
  );
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    const subscription = ExpoLibxray.addListener(
      'onVpnStatusChange',
      (event: { status: string; error?: string }) => {
        console.log('Received VPN status:', event.status);

        if (event.status === 'CONNECTED') {
          setVpnState('CONNECTED');
        } else if (event.status === 'ERROR') {
          setVpnState('ERROR');
          setErrorMessage(event.error || 'Uknown error');
        }
      }
    );

    return () => {
      subscription.remove();
    };
  }, []);

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView style={styles.container}>
        <Text style={styles.header}>Module API Example</Text>
        <Group name="Test VPN connection protocols">
          <Text>{text}</Text>
          <Text>Статус: {vpnState}</Text>
          {vpnState === 'CONNECTING' && <ActivityIndicator />}
          {vpnState === 'ERROR' && <Text>Ошибка: {errorMessage}</Text>}
          <Button
            title="Start VLESS + Reality"
            onPress={() => {
              startXrayVless((text) => {
                setText(text);
              });
            }}
            disabled={vpnState === 'CONNECTING' || vpnState === 'CONNECTED'}
          />
          <Button
            title="Test Xray Config"
            onPress={() => {
              testXrayConfig((text) => {
                setText(text);
              });
            }}
          />
          <Button
            title="Ping Batch"
            onPress={() => {
              pingBatch((text) => {
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

function buildConfig(initConfig: string, appFilesDir: string) {
  return new LibxrayConfigBuilder(initConfig)
    .setLogging('debug')
    .setEnv(appFilesDir)
    .setInbounds([
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
    .setOutbounds(
      [
        {
          tag: 'VLESS TCP REALITY',
          sendThrough: '0.0.0.0',
          streamSettings: {
            xhttpSettings: {
              path: '/chrome/update',
              mode: 'stream-up',
              extra: {
                xPaddingBytes: '100-1000',
              },
            },
          },
        },
      ],
      ['streamSettings.realitySettings.password', 'streamSettings.realitySettings.port']
    )
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
}

async function startXrayVless(setText: (text: string) => void) {
  try {
    const appFilesDir = Paths.document;
    const resp = await ExpoLibxray.convertShareLinksToXrayJson(xrayLink);
    const responseObj = JSON.parse(resp);

    if (responseObj.success && responseObj.data) {
      let xrayConfigData = responseObj.data;

      if (typeof xrayConfigData === 'string' && xrayConfigData.trim().startsWith('[')) {
        const parsedArray = JSON.parse(xrayConfigData);
        if (Array.isArray(parsedArray) && parsedArray.length > 0) {
          xrayConfigData = JSON.stringify(parsedArray[0]);
        }
      }

      const config = buildConfig(xrayConfigData, appFilesDir.uri.replace('file://', ''));

      let finalConfigJson = config;
      if (config.trim().startsWith('[')) {
        const arr = JSON.parse(config);
        finalConfigJson = JSON.stringify(arr[0]);
      }

      const result = await ExpoLibxray.runXray({
        xrayJson: finalConfigJson,
        geoIpUrl: undefined,
        geoSiteUrl: undefined,
        downloadEvery: 30n.toString(),
        timeUnit: TimeUnit.SECONDS,
        maxGeoAgeMillis: '30000',
        appsSplitTunneling: undefined,
        vpnServiceErrorLocalized: undefined,
        notificationErrorLocalized: undefined,
        vpnServiceNotificationTitle: "VPN service notification",
        vpnServiceNotificationContent: 'Status text:',
        vpnServiceNotificationStatus: { connected: "Connected successfully", error: "Internal service error", waiting: "Waiting..." }
      });
      setText(result.success ? 'Connected with VLESS' : 'Fail');
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

async function testXrayConfig(setText: (text: string) => void) {
  try {
    const appFilesDir = Paths.document;
    const resp = await ExpoLibxray.convertShareLinksToXrayJson(xrayLink);
    const responseObj = JSON.parse(resp);

    if (responseObj.success && responseObj.data) {
      const config = buildConfig(responseObj.data, appFilesDir.uri.replace('file://', ''));

      const result = await ExpoLibxray.testXray(config);
      setText(!result.success ? (result.error ?? 'Uknown error') : 'Valid configuration');
    } else {
      setText(`Ошибка конвертации: ${responseObj.error}`);
    }
  } catch (error) {
    setText(`Ошибка при запуске Xray: ${(error as Error).message}`);
  }
}

async function pingBatch(setText: (text: string) => void) {
  try {
    const appFilesDir = Paths.document;
    const resp = await ExpoLibxray.convertShareLinksToXrayJson(xrayLink);
    const responseObj = JSON.parse(resp);

    if (responseObj.success && responseObj.data) {
      const config = buildConfig(responseObj.data, appFilesDir.uri.replace('file://', ''));

      const batchRequest: PingBatchRequest = {
        configs: [
          {
            xrayJson: config,
            outboundTag: undefined,
          },
        ],
        timeout: 5,
        url: 'https://microsoft.com',
      };

      const result = await ExpoLibxray.pingBatch(batchRequest);
      setText(result.results ? JSON.stringify(result.results[0]) : 'Empty response');
    } else {
      setText(`Ошибка конвертации: ${responseObj.error}`);
    }
  } catch (error) {
    setText(`Ошибка при запуске Xray: ${(error as Error).message}`);
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
