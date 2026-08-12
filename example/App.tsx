import ExpoLibxray, { ExpoLibxrayXrayConfigBuilder } from 'expo-libxray';
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
      'vless://bdbec06a-de1c-4fb0-8748-e865b33b4ac3@195.20.119.44:4433?security=reality&type=tcp&headerType=&flow=xtls-rprx-vision&path=&host=microsoft.com&sni=microsoft.com&fp=chrome&pbk=c4GdV4kQeE1L8Un7i20At-6_7ba5X99FgDWhgOoiKi4&sid=#VLESS_tcp'
    );
    const responseObj = JSON.parse(resp);

    if (responseObj.success && responseObj.data) {
      const config = new ExpoLibxrayXrayConfigBuilder(responseObj.data)
        .setLogging('debug')
        .setEnv(appFilesDir.uri.replace('file://', ''))
        .setInbound(() => {
          return [
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
            },
          ];
        }, 'SOCKS LOCAL')
        .setOutbounds('VLESS TCP REALITY', 'vless', '0.0.0.0')
        .setDns({ 'domain-!ru': '8.8.8.8' }, ['8.8.8.8', '1.1.1.1'])
        .setRouting(
          [
            {
              network: 'tcp, udp',
              inboundTag: ['SOCKS LOCAL'],
              outboundTag: 'VLESS TCP REALITY',
              domain: [],
            },
            {
              domain: ['geosite:ru-available-only-inside'],
              outboundTag: 'direct',
              network: '',
              inboundTag: [],
            },
            {
              domain: ['geosite:category-ads-all'],
              outboundTag: 'block',
              network: '',
              inboundTag: [],
            },
          ],
          'IpIfNonMatch'
        )
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
