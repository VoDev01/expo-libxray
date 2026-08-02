import ExpoLibxray from 'expo-libxray';
import { useState } from 'react';
import { Button, SafeAreaView, ScrollView, Text, View } from 'react-native';

export default function App() {
  const [text, setText] = useState<string>('Hello world!');
  return (
    <SafeAreaView style={styles.container}>
      <ScrollView style={styles.container}>
        <Text style={styles.header}>Module API Example</Text>
        <Group name="Async functions">
          <Text>{text}</Text>
          <Button
            title="Set value"
            onPress={async () => {
              const hasPermission = await ExpoLibxray.requestVpnPermission();

              if (!hasPermission) {
                setText('Подтвердите подключение в системном окне!');
                return;
              }

              const resp = await ExpoLibxray.convertShareLinksToXrayJson(
                'ss://Y2hhY2hhMjAtaWV0Zi1wb2x5MTMwNTptYnJUU2dWTTh6aFZRbTJDdDBtbGFR@10.0.2.2:1080#%F0%9F%9A%80%20Marz%20%28android-test%29%20%5BShadowsocks%20-%20tcp%5D'
              );

              const responseObj = JSON.parse(resp);

              if (responseObj.success && responseObj.data) {
                const config = responseObj.data;

                config.dns = {
                  servers: ['8.8.8.8', '1.1.1.1'],
                };

                config.routing = {
                  domainStrategy: 'IPIfNonMatch',
                  rules: [
                    {
                      type: 'field',
                      inboundTag: ['socks-in'],
                      outboundTag: 'direct',
                    },
                    {
                      type: 'field',
                      network: 'udp',
                      port: 53,
                      outboundTag: 'direct',
                    },
                  ],
                };

                const result = await ExpoLibxray.runXrayFromJson(JSON.stringify(config));
                setText(result);
              } else {
                setText(`Ошибка конвертации: ${responseObj.error}`);
              }
            }}
          />
        </Group>
      </ScrollView>
    </SafeAreaView>
  );
}

function Group(props: { name: string; children: React.ReactNode }) {
  return (
    <View style={styles.group}>
      <Text style={styles.groupHeader}>{props.name}</Text>
      {props.children}
    </View>
  );
}

const styles = {
  header: { fontSize: 30, margin: 20 },
  groupHeader: { fontSize: 20, marginBottom: 20 },
  group: { margin: 20, backgroundColor: '#fff', borderRadius: 10, padding: 20 },
  container: { flex: 1, backgroundColor: '#eee' },
  view: { flex: 1, height: 200 },
};
