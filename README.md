# expo-libxray

An Expo native module that exposes the [Xray core](https://github.com/XTLS/Xray-core)
(Libxray **v26.9.9**) to React Native apps: convert share links ↔ Xray JSON, run the
tunnel through a VpnService, ping servers in batch, and receive VPN status events.

- Platforms: **Android** and **Apple** (iOS). The web build is a no-op stub.
- License: GPL-3.0.

## Native prerequisites (Android)

The native libraries are not bundled. Before building you must:

1. Compile the [libXray wrapper](https://github.com/XTLS/libXray#usage) and place
   `libXray.aar` in `android/src/main/libs/`.
2. Compile [hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel) and place
   the socks native libraries in `android/src/main/jniLibs/`.

The consuming app is responsible for registering the VPN service and permissions
(`BIND_VPN_SERVICE`, `FOREGROUND_SERVICE`, `POST_NOTIFICATIONS`) and for adding the
Android config plugins it needs (see `example/plugins/`).

## Installation

```bash
npx expo install expo-libxray
# or
yarn add expo-libxray
```

Because this is a native module, it requires a development build (it does not work in
Expo Go).

## API

```ts
import ExpoLibxray, {
  LibxrayConfigBuilder,
  TimeUnit,
  type RunXrayRequest,
  type PingBatchRequest,
} from 'expo-libxray';
```

| Method | Description |
| --- | --- |
| `convertShareLinksToXrayJson(links: string)` | Convert share links to Xray JSON (returns an `InvokeResponse` JSON string). |
| `convertXrayJsonToShareLinks(xrayJson: string)` | Convert Xray JSON back to share links. |
| `runXray(request: RunXrayRequest)` | Start the tunnel (`RunXrayResponse`). |
| `stopXray()` | Stop the tunnel. |
| `getXrayState()` | Current state (JSON string). |
| `pingBatch(request: PingBatchRequest)` | Ping up to **5** configs (`PingBatchResponse`). |
| `testXray(configJson: string)` | Validate a config. |
| `xrayVersion()` | Libxray/Xray version string. |
| `addListener('onVpnStatusChange', cb)` | Subscribe to VPN status events. |

### Types

- **`RunXrayRequest`** — `xrayJson`, `appsSplitTunneling` (package names bypassing the
  tunnel), and localized notification strings
  (`notificationErrorLocalized`, `vpnServiceErrorLocalized`, `vpnServiceNotificationTitle`,
  `vpnServiceNotificationContent`, `vpnServiceNotificationStatus`).
- **`PingBatchRequest`** — `configs: PingBatchItem[]` (`xrayJson`, optional `outboundTag`),
  `timeout`, `url`, `locationUrl`.
- **`PingBatchResponse`** — `results: PingBatchItemResponse[]` (`success`, `delay`, `error`,
  `locationJson`, `locationError`).
- **`VpnStatusEvent`** — `{ status: 'CONNECTED' | 'CONNECTING' | 'ERROR' | 'DISCONNECTED', error? }`.

### LibxrayConfigBuilder

Build an Xray config fluently from a converted JSON object:

```ts
setInbounds(inbounds)
setEnv(assetsDir)
setDns(hosts, servers, queryStrategy?)
setLogging(logLevel)
setOutbounds(outbounds, deleteFields?)
setRouting(rules, inboundTag, network)
build(): string
```

## Usage

```ts
import ExpoLibxray, { LibxrayConfigBuilder } from 'expo-libxray';

// 1. Convert a share link to Xray JSON.
const invoke = JSON.parse(await ExpoLibxray.convertShareLinksToXrayJson(vlessLink));
const initialConfig = JSON.parse(invoke.data);

// 2. Build the final config.
const xrayJson = new LibxrayConfigBuilder(initialConfig)
  .setEnv(assetsDir)
  .setDns({}, ['1.1.1.1'])
  .setLogging('warning')
  .build();

// 3. Listen for status changes.
const sub = ExpoLibxray.addListener('onVpnStatusChange', (event) => {
  console.log(event.status, event.error);
});

// 4. Start and later stop the tunnel.
await ExpoLibxray.runXray({
  xrayJson,
  appsSplitTunneling: undefined,
  notificationErrorLocalized: 'Permission required',
  vpnServiceErrorLocalized: 'VPN permission required',
  vpnServiceNotificationTitle: 'VPN',
  vpnServiceNotificationContent: 'Status:',
  vpnServiceNotificationStatus: { waiting: 'Waiting', connected: 'Connected', connecting: 'Connecting', error: 'Error' },
});

// ...
await ExpoLibxray.stopXray();
sub.remove();
```

## Development

```bash
yarn build        # build the JS/TS package
yarn clean
yarn lint         # eslint src/
yarn test         # jest (jest-expo)
yarn prepare
yarn open:ios     # open the iOS example
yarn open:android # open the Android example
```

The `example/` directory is a small Expo app used to exercise the module (it includes
the Android config plugins for the VPN service and ABI filters).

## License

Licensed under the GNU General Public License v3.0. See [`LICENSE`](LICENSE).
