import { ConfigPlugin, withAndroidManifest } from '@expo/config-plugins';

const withAndroidPlugin: ConfigPlugin = (config) => {
  return withAndroidManifest(config, async (modConfig) => {
    const androidManifest = modConfig.modResults;

    const mainApplication = androidManifest?.manifest?.application?.[0];
    if (!mainApplication) {
      return modConfig;
    }

    if (!mainApplication.service) {
      mainApplication.service = [];
    }

    const xrayService = {
      $: {
        'android:name': 'net.libxray.XrayVpnService',
        'android:permission': 'android.permission.BIND_VPN_SERVICE',
        'android:foregroundServiceType': 'specialUse',
        'android:exported': 'false',
        'android:label': '@string/app_name',
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
      'meta-data': [
        {
          $: {
            'android:name': 'android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE',
            'android:value': 'VPN',
          },
        },
      ],
    };

    const exists = mainApplication.service.some(
      (s: any) => s.$ && s.$['android:name'] === '.XrayVpnService'
    );

    if (!exists) {
      mainApplication.service.push(xrayService as any);
    }

    return modConfig;
  });
};

export default withAndroidPlugin;
