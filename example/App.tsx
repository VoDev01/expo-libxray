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
              const resp = await ExpoLibxray.convertShareLinksToXrayJson(
                'vless://bdbec06a-de1c-4fb0-8748-e865b33b4ac3@195.20.119.44:10808?security=none&type=tcp&headerType=&path=&host=#%F0%9F%9A%80%20Marz%20%28android-test%29%20%5BVLESS%20-%20tcp%5D'
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
