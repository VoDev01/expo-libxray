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

    const remoteWorkerService = {
      $: {
        'android:name': 'androidx.work.multiprocess.RemoteWorkerService',
        'android:exported': 'false',
        'android:process': ':xray_vpn',
      },
    };

    mainApplication.service.push(xrayService as any);
    mainApplication.service.push(remoteWorkerService as any);

    if (!mainApplication.provider) {
      mainApplication.provider = [];
    }

    mainApplication.provider.push({
      $: {
        'android:name': 'androidx.startup.InitializationProvider',
        'android:authorities': '${applicationId}.androidx-startup',
        'android:exported': 'false',
        'tools:node': 'merge',
      },
      'meta-data': [
        {
          $: {
            'android:name': 'androidx.work.WorkManagerInitializer',
            'android:value': 'androidx.startup',
            'tools:node': 'remove',
          },
        },
      ],
    });

    return modConfig;
  });
};

export default withAndroidPlugin;
