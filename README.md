#An Expo library to help the developers use Xray core.

Before using you should compile [Xray wrapper library](https://github.com/XTLS/libXray#usage) for used platforms (only Android supported currently)
and [Socks client](https://github.com/heiher/hev-socks5-tunnel) then put libXray into libs folder of android directory and Socks into jniLibs.

Use `convertShareLinksToXrayJson(vlessLink)` function and then build config manually using `LibxrayConfigBuilder(initialConf)` to the desired state.
