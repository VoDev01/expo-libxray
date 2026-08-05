import ExpoLibxray from 'expo-libxray';
import { useState } from 'react';
import { Button, SafeAreaView, ScrollView, StyleSheet, Text, View } from 'react-native';

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
            onPress={async () => {
              const resp = await ExpoLibxray.convertShareLinksToXrayJson(
                'vless://bdbec06a-de1c-4fb0-8748-e865b33b4ac3@104.28.156.67:443?security=reality&type=tcp&headerType=&flow=xtls-rprx-vision&path=&host=microsoft.com&sni=microsoft.com&fp=chrome&pbk=c4GdV4kQeE1L8Un7i20At-6_7ba5X99FgDWhgOoiKi4&sid=595726f129092382#VLESS_tcp'
              );
              const responseObj = JSON.parse(resp);

              if (responseObj.success && responseObj.data) {
                const config = responseObj.data;

                const result = await ExpoLibxray.runXrayFromJson(JSON.stringify(config));
                setText(result);
              } else {
                setText(`Ошибка конвертации: ${responseObj.error}`);
              }
            }}
          />
          <Button
            title="Stop"
            onPress={async () => {
              await ExpoLibxray.stopXray();
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
