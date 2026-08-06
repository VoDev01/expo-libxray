import { ConfigPlugin, withAppBuildGradle } from '@expo/config-plugins';

const withAndroidAbiFilters: ConfigPlugin = (config) => {
  return withAppBuildGradle(config, (modConfig) => {
    if (modConfig.modResults.language === 'groovy') {
      const ndkBlock = `
        ndk {
            abiFilters 'arm64-v8a', 'x86_64'
        }
      `;
      if (!modConfig.modResults.contents.includes('abiFilters')) {
        modConfig.modResults.contents = modConfig.modResults.contents.replace(
          /defaultConfig\s*\{/,
          `defaultConfig {${ndkBlock}`
        );
      }
    }
    return modConfig;
  });
};

export default withAndroidAbiFilters;
