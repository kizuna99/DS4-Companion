# DS4 Companion — Android Native DualShock 4 Controller Manager

A dedicated, native Android companion application designed specifically for connecting, customizing, testing, and diagnosing Sony DualShock 4 (PS4) controllers over **Bluetooth** and **USB OTG**.

Built with modern **Android 10–15** compatibility, **Kotlin**, **Jetpack Compose**, **Material You / Material 3**, and **Clean Architecture (MVVM)**.

---

## 🌟 Key Features

### 1. Controller Connection & Identity
- Automatic detection of DualShock 4 controllers via Android `InputDevice` subsystem, `UsbManager` (USB Host / OTG), and Bluetooth HID.
- Real-time connection status (`Connected`, `Connecting…`, `Disconnected`).
- Hardware inspection displaying Device Name, Transport type (Bluetooth vs USB OTG), Vendor ID (`0x054C`), Product ID (`0x05C4` V1 / `0x09CC` V2), Device Descriptor, and active input sources.
- Quick action controls: Scan, Disconnect, Refresh, and one-tap shortcut to Android Bluetooth Settings.
- Built-in **Device Simulation Mode** to test UI animations, button telemetry, and diagnostics without physical controller hardware.

### 2. RGB Light Bar Customization
- Real-time interactive DualShock 4 vector preview with luminous dynamic glow matching the selected light bar color.
- Preset colors:
  - 🟦 PlayStation Blue (`#0037DC`)
  - 🟥 Combat Red (`#FF1414`)
  - 🟩 Acid Green (`#00E650`)
  - 🟪 Neon Purple (`#AA1EE6`)
  - 🩵 Electric Cyan (`#00DCFF`)
  - 🟨 Amber Yellow (`#FFBE00`)
  - ⬜ Pure White (`#FFFFFF`)
  - 🎨 Custom RGB via fine-tuned sliders (0–255)
- Independent Brightness slider (5%–100%).
- Hardware command delivery via authentic DualShock 4 **USB HID Output Report 0x05** (32-byte protocol with motor/LED control bytes).
- Transparent status reporting: clear feedback on whether the command was accepted by hardware or if unrooted Bluetooth stack constraints apply.

### 3. Battery Level Detection
- DualShock 4 battery monitoring:
  - 🟢 **60–100%**: High
  - 🟡 **25–59%**: Medium
  - 🔴 **1–24%**: Low
  - ⚡ **Charging**: Charging state indicator
  - ⚪ **Battery unavailable**: Honest indicator when the operating system driver or transport does not expose battery metrics (no fabricated synthetic data).
- Supported over Android 12+ `InputDevice.getBatteryState()` and direct USB OTG hardware report inspection.

### 4. Interactive Input Tester
- Top-down visual controller representation with instantaneous reactive highlighting when any button is pressed:
  - **D-Pad**: Up, Down, Left, Right
  - **Action Buttons**: Cross (✕), Circle (◯), Square (□), Triangle (△) with PlayStation iconic styling
  - **Shoulder Buttons**: L1, R1, L2, R2
  - **Analog Triggers**: L2 and R2 analog pressure gauges (0%–100%)
  - **Analog Sticks**: L3 and R3 click detection with real-time 2D deflection radar
  - **Center & Special**: Share, Options, PS Home Button, and Touchpad click
- Live telemetry event stream logging keycodes, axes, values, and timestamps.
- One-tap "Reset Test" button.

### 5. Controller Diagnostics
- **Stick Drift & Centering Check**: Measures deadzone distance from true center (0,0) at rest to detect stick drift early.
- **Stuck Button Detector**: Alerts if any button remains depressed for an abnormal duration or fails to release.
- **Functional Inputs Checklist**: Records all buttons successfully verified during the active test session.
- **Troubleshooting Guide**: Explains Android Bluetooth polling latency, OTG adapters, and PS Button OS interception.

---

## 🎮 How to Pair Your DualShock 4

### Option A: Bluetooth Pairing
1. Turn off your DualShock 4 controller (light bar completely off).
2. Press and hold the **SHARE** button and the **PS Button** simultaneously for 3–5 seconds.
3. The light bar will begin flashing rapidly with a double-white strobe pattern (pairing mode).
4. On your Android device, open **Bluetooth Settings** (or tap the **Bluetooth** button in the app).
5. Select **Wireless Controller** under Available Devices to pair.
6. Open DS4 Companion; your controller will be recognized immediately.

### Option B: USB OTG (Plug-and-Play)
1. Connect a USB-A to Micro-USB cable to your DualShock 4 controller.
2. Plug the cable into your Android phone or tablet using a USB OTG adapter (Type-C or Micro-USB).
3. Tap **Allow** when prompted for USB device permission.
4. USB OTG grants zero-latency input and direct hardware access to send RGB Light Bar commands.

---

## ⚠️ Hardware & Platform Limitations

DualShock 4 implements proprietary Sony HID feature reports. Android natively handles button mapping through its Linux kernel HID driver (`hid-sony` or generic gamepad HID).

1. **Light Bar Control over Bluetooth**:
   Standard unrooted Android isolates raw Bluetooth HID output sockets to prevent unverified Bluetooth peripherals from injecting keystrokes into the OS. Therefore, over standard Bluetooth, user applications cannot directly send arbitrary raw HID output reports without root privileges. For hardware RGB programming, **USB OTG** is fully supported and sends direct HID Report 0x05 commands.
2. **PS Button Interception**:
   On most Android OEM skins, `KEYCODE_BUTTON_MODE` (the PS Button) is intercepted at the Window Manager level as the Android Home or Google Assistant trigger.
3. **Battery Reporting**:
   Android 12+ (API 31+) provides native `InputDevice.getBatteryState()`. If the underlying kernel driver for a specific Bluetooth stack does not report battery capacity, the app honestly displays **"Battery unavailable"** rather than faking percentage values.

---

## 🛠️ Build & Installation

### Requirements:
- Android Studio Hedgehog / Ladybug or newer
- Gradle 8.x+ with Kotlin 2.x
- Minimum SDK: Android 7.0 (API 24)
- Target SDK: Android 15 (API 35/36)

### Running Tests:
```bash
gradle :app:testDebugUnitTest
```

### Compiling Debug APK:
```bash
gradle :app:assembleDebug
```
The compiled APK will be located at `app/build/outputs/apk/debug/app-debug.apk`.
