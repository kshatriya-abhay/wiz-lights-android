# WiZ Lights for Android

Control Philips WiZ bulbs over your local network. No account, no cloud, no internet.

- Scans the Wi-Fi (or Ethernet) network for WiZ bulbs, saves them by MAC and lets you name them
- Per-bulb power, brightness, white temperature (2200–6500 K), colour (hue × saturation plane), 12 scenes and saved presets
- All on / all off
- Resizable home-screen widget: 2×1 shows All on / All off; wider adds Rescan; more room adds light toggles
- 23 stock Omarchy themes, styled like [Flux](https://github.com/bjarneo/flux)
- Optional "only on chosen Wi-Fi networks" restriction

It is the Android counterpart of the [WiZ Lights plugin](https://github.com/kshatriya-abhay/omarchy-wiz-lights-plugin)
for the Omarchy shell, and uses the same `lights.json` / `presets.json` formats.

## Local network only

Android requires the `INTERNET` permission for any socket, including UDP on the LAN. The app enforces
"local only" in code:

- every socket is bound to the Wi-Fi or Ethernet network (never cellular, and not through a VPN),
- every packet goes to, and every reply must come from, an address on that network's subnet or `255.255.255.255` (`net/AddressGuard.kt`),
- IP addresses are parsed as literals; no hostname is ever resolved,
- no HTTP library, analytics or crash reporting; a unit test fails the build if one appears on the classpath,
- cleartext HTTP is denied, backups are off.

The `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED` and `FOREGROUND_SERVICE` permissions in the APK come from
WorkManager, which Glance (the widget library) uses.

## Wi-Fi restriction

Off by default, and then the app needs no location permission. When you turn on **Settings → Network →
Only on chosen Wi-Fi networks**, Android requires location permission to read the Wi-Fi name, and
"Allow all the time" for the widget to check it in the background. Scanning and control are blocked on
other Wi-Fi networks. Ethernet is always allowed.

## Build

Requires JDK 17–21 and the Android SDK (platform 37.2).

```sh
./gradlew testDebugUnitTest      # JVM unit tests, including a fake bulb on loopback
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Release builds are signed when `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`
are all set; `WIZ_VERSION` and `WIZ_VERSION_CODE` set the version. `.github/workflows/release.yml`
builds, tests and attaches the signed APK to a `v*` tag's GitHub release.

## Troubleshooting discovery

- Phone and bulbs must be on the same network; bulbs use 2.4 GHz.
- Turn off "AP isolation" / "client isolation" on the router.
- Turn on "Allow local communication" in the WiZ app.

## On-device checklist

The emulator can't reach LAN broadcast, so these are checked on a real phone:

1. Scan finds the same bulbs as `python3 wizctl.py discover` on the laptop.
2. Power, brightness, temperature, colour and each scene on an RGB bulb (and a white bulb if available).
3. A brightness/temperature/colour/scene command turns an off bulb on (`"state": true` is sent with it), and brightness 10 % is accepted. If a bulb rejects the combined form, set `WizClient.combinedState = false`.
4. Rename and forget survive an app restart; a changed bulb IP is picked up by a scan.
5. Wi-Fi off, mobile data on: the app shows "Connect to Wi-Fi" and sends nothing.
6. With a VPN on, control still works.
7. Wi-Fi restriction: an allowed SSID works; another SSID blocks app and widget; declining background location shows the widget's "Open app" state.
8. Widget at 2×1, 3×1, 5×1, 2×2, 4×2, 4×4; taps work with the app swiped away and after a reboot.
9. Each theme: Lights screen, an expanded light, Settings, widget.

## License

GPL-3.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE) for third-party code and credits.
WiZ is a trademark of Signify. This app is not affiliated with or endorsed by Signify.
