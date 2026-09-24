import { Paths } from 'expo-file-system';
import ExpoLibxray, {
  LibxrayConfigBuilder,
  PingBatchItem,
  PingBatchRequest,
  RunXrayRequest,
  TimeUnit,
} from 'expo-libxray';
import { useEffect, useState } from 'react';
import { Button, SafeAreaView, ScrollView, StyleSheet, Text, View } from 'react-native';

const xrayLink =
  'vless://863690dc-5888-484d-8e16-efe678fd90e2@185.155.8.159:7858?mode=auto&path=%2Fphx&security=tls&encryption=none&extra=%7B%22scMaxEachPostBytes%22%3A%224000000%22%2C%22scMinPostsIntervalMs%22%3A%225%22%2C%22downloadSettings%22%3A%7B%22address%22%3A%22nw.amirport.sbs%22%2C%22port%22%3A7858%2C%22network%22%3A%22xhttp%22%2C%22security%22%3A%22tls%22%2C%22tlsSettings%22%3A%7B%22serverName%22%3A%22nw.amirport.sbs%22%7D%2C%22xhttpSettings%22%3A%7B%22mode%22%3A%22auto%22%2C%22path%22%3A%22%2Fphx%22%2C%22extra%22%3A%7B%22scMaxEachPostBytes%22%3A%224000000%22%2C%22scMinPostsIntervalMs%22%3A%225%22%7D%7D%7D%7D&type=xhttp&sni=nw.amirport.sbs&fp=firefox#%F0%9F%8C%90%20Anycast-IP%20%7C%20%F0%9F%87%B3%F0%9F%87%B1%20%F0%9F%87%B7%F0%9F%87%B4%20%7C%20%5BBL%5D';

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
          setVpnState(event.status);
        } else if (event.status === 'DISCONNECTED'){
          setVpnState(event.status);
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
          <Text>Status: {vpnState}</Text>
          <Button
            title="Start Xray Core"
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
            title="Convert link to json"
            onPress={() => {
              convertShareLinksToXrayJson((text) => {
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
        appsSplitTunneling: undefined,
        vpnServiceErrorLocalized: undefined,
        notificationErrorLocalized: undefined,
        vpnServiceNotificationTitle: "VPN service notification",
        vpnServiceNotificationContent: 'Status text:',
        vpnServiceNotificationStatus: { connected: "Connected successfully", error: "Internal service error", waiting: "Waiting...", 'connecting': "Connecting" }
      });
      setText(result.success ? 'Connected with VLESS' : 'Fail');
    } else {
      setText(`Converting error: ${responseObj.error}`);
    }
  } catch (error) {
    setText(`Ошибка при запуске Xray: ${(error as Error).message}`);
  }
}

async function convertShareLinksToXrayJson(setText: (text: string) => void) {
  try {
    const resp = JSON.parse(await ExpoLibxray.convertShareLinksToXrayJson(xrayLink));
    setText(resp.success ? 'Success' : resp.error);
  } catch (error) {
    setText(`Error converting links to Xray json: ${(error as Error).message}`);
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

      const result = JSON.parse(await ExpoLibxray.testXray(config));
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
        locationUrl: 'https://google.com'
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
