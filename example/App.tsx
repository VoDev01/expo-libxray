import ExpoLibxray, { LibxrayConfigBuilder } from 'expo-libxray';
import { useState } from 'react';
import { Button, SafeAreaView, ScrollView, StyleSheet, Text, View } from 'react-native';
import { Paths } from 'expo-file-system';

export default function App() {
  const [text, setText] = useState<string>('Hello world!');
  return (
    <SafeAreaView style={styles.container}>
      <ScrollView style={styles.container}>
        <Text style={styles.header}>Module API Example</Text>
        <Group name="Test VPN connection protocols">
          <Text>{text}</Text>
          <Button
            title="Start VLESS + Reality"
            onPress={() => {
              startXrayVless((text) => {
                setText(text);
              });
            }}
          />
          <Button
            title="Start Hysteria v2"
            onPress={() => {
              startXrayHysteria((text) => {
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

async function startXrayVless(setText: (text: string) => void) {
  try {
    const appFilesDir = Paths.document;
    const resp = await ExpoLibxray.convertShareLinksToXrayJson(
      'vless://1ceed667-7895-4750-99aa-fe2d7dd91c8d@85.192.60.109:8443?encryption=none&extra=%7B%22mode%22%3A%22stream-up%22%2C%22xPaddingBytes%22%3A%22100-1000%22%7D&fp=chrome&host=dl.google.com&mode=stream-up&path=%2Fchrome%2Fupdate&pbk=Y_h7Eekek0kE78qYrlrhbotdEgsf2NgNer3TALAyXzM&security=reality&sid=ed11541a6dbfa616&sni=youtu.be&spx=%2Fe00230f58dd174f&type=xhttp&x_padding_bytes=100-1000#VLESS%20REALITY%20XHTTP-w8vou5sxlt'
    );
    const responseObj = JSON.parse(resp);

    if (responseObj.success && responseObj.data) {
      const config = new LibxrayConfigBuilder(responseObj.data)
        .setLogging('debug')
        .setEnv(appFilesDir.uri.replace('file://', ''))
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
              sendThrough: '0.0.0.0',//responseObj.data.outbounds[0].settings.address,
              streamSettings: {
                xhttpSettings: {
                  path: '/chrome/update',
                  mode: 'stream-up',
                  extra: {
                    xPaddingBytes: '100-1000',
                  },
                },
              }
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

        console.log(config);

      const result = await ExpoLibxray.runXray(config);
      setText(result ? 'Connected with VLESS' : 'Fail');
    } else {
      setText(`Ошибка конвертации: ${responseObj.error}`);
    }
  } catch (error) {
    setText(`Ошибка при запуске Xray: ${(error as Error).message}`);
  }
}

async function startXrayHysteria(setText: (text: string) => void) {
  try {
    const appFilesDir = Paths.document;
    const resp = await ExpoLibxray.convertShareLinksToXrayJson(
      'hysteria2://de48b194280796967454bc2d58ae4b60@45.137.42.1:5678?sni=microsoft.com#Netherlands%20AEZA%20%5Bhysteria2%20-%20%3Cmissing%3E%5D'
    );
    const responseObj = JSON.parse(resp);

    if (responseObj.success && responseObj.data) {
      const config = new LibxrayConfigBuilder(responseObj.data)
        .setLogging('debug')
        .setEnv(appFilesDir.uri.replace('file://', ''))
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
              tag: 'HYSTERIA',
              settings: {
                address: '45.137.42.1',
              },
            },
          ],
          ['password', 'limitFallbackUpload', 'limitFallbackDownload']
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

      const result = await ExpoLibxray.runXray(config);
      setText(result ? 'Connected with Hysteria v2' : 'Fail');
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
