import { ConfigPlugin, withAndroidManifest, AndroidConfig } from '@expo/config-plugins';

const withAndroidPlugin: ConfigPlugin = (config) => {
  return withAndroidManifest(config, async (modConfig) => {
    const androidManifest = modConfig.modResults;

    const mainApplication = AndroidConfig.Manifest.getMainApplicationOrThrow(
      androidManifest
    ) as any;
    if (!mainApplication) {
      return modConfig;
    }

    if (!mainApplication.service) {
      mainApplication.service = [];
    }

    const xrayService = {
      $: {
        'android:name': 'net.libxray.service.XrayVpnService',
        'android:permission': 'android.permission.BIND_VPN_SERVICE',
        'android:foregroundServiceType': 'systemExempted',
        'android:exported': 'false',
        'android:label': '@string/app_name',
        'android:process': ':xray_vpn',
      },
      'intent-filter': [
        {
          action: [
            {
              $: {
                'android:name': 'android.net.VpnService',
              },
            },
          ],
        },
      ],
    };

    mainApplication.service.push(xrayService as any);

    return modConfig;
  });
};

export default withAndroidPlugin;
