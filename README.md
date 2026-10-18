<div align="center">

<img src="windows/FlightJoy.Windows/icon.png" width="110" alt="FlightJoy Logo" />

# FlightJoy

**Use your Android phone as a wireless flight yoke, sliders, and button panel for PC flight simulators.**

[![Download ZIP](https://img.shields.io/badge/Download-FlightJoy.zip-white?style=for-the-badge&logo=github&logoColor=black)](./Zips/FlightJoy.zip)

![Windows](https://img.shields.io/badge/Windows_10%2F11-0078D6?style=flat-square&logo=windows&logoColor=white)
![Android](https://img.shields.io/badge/Android_8.0+-3DDC84?style=flat-square&logo=android&logoColor=black)
![.NET 8](https://img.shields.io/badge/.NET_8.0-512BD4?style=flat-square&logo=dotnet&logoColor=white)
![vJoy](https://img.shields.io/badge/Driver-vJoy_2.1.9.1-222222?style=flat-square)

</div>

---

## ✈️ Compatible Simulators

Works with any PC flight simulator that supports Windows / vJoy joystick input:

<div align="center">
  <img src="https://upload.wikimedia.org/wikipedia/commons/9/93/Microsoft_Flight_Simulator_2024_logo.webp" height="48" alt="Microsoft Flight Simulator 2024" title="Microsoft Flight Simulator" />
  &nbsp;&nbsp;&nbsp;&nbsp;
  <img src="https://www.x-plane.com/wp-content/uploads/2026/03/Logo_XPlane_Main.svg" height="42" alt="X-Plane" title="X-Plane" />
  &nbsp;&nbsp;&nbsp;&nbsp;
  <img src="https://upload.wikimedia.org/wikipedia/commons/a/aa/DCS-World-logo.jpg" height="48" alt="DCS World" title="DCS World" />
  &nbsp;&nbsp;&nbsp;&nbsp;
  <img src="https://upload.wikimedia.org/wikipedia/commons/d/d8/IL2-Sturmovik-BoS-logo.jpg" height="48" alt="IL-2 Sturmovik" title="IL-2 Sturmovik" />
</div>

---

## 📦 Download

Download the unified package (containing **`Android/FlightJoy.apk`** and **`Windows/FlightJoy.exe`**) directly from [`Zips/FlightJoy.zip`](./Zips/FlightJoy.zip).

---

## ⚙️ PC Requirements

| Requirement | Description | Link |
| :--- | :--- | :--- |
| <img src="https://cdn.simpleicons.org/dotnet/512BD4" width="20" align="center" /> **.NET 8 Desktop Runtime** | Required to run `FlightJoy.exe` (**x64** version). | [Download from Microsoft](https://dotnet.microsoft.com/en-us/download/dotnet/8.0) |
| 🎮 **vJoy 2.1.9.1** | Virtual joystick driver for Windows (with **Device 1** enabled). | [Download vJoy 2.1.9.1](https://sourceforge.net/projects/vjoystick/) |

---

## 🕹️ How to Use

1. Launch **`FlightJoy.exe`** on your PC.
2. Open the **FlightJoy** app on your Android phone (connected to the same Wi-Fi network).
3. Tap **`☰`** $\rightarrow$ **Connection** $\rightarrow$ tap **`🔍`** to automatically find your PC (or tap **Connect** directly inside the **`☰`** menu for 1-tap Quick Connect).
4. **Controls Overview**:
   - **Sliders**: Only move when you press and drag directly on the **square handle**.
   - **Reverse Mode (`REV`)**: Toggle the `REV` switch next to the left slider to pull reverse thrust from top (`0%`) to bottom (`100%`).
   - **Optional Extra Brake Slider**: Can be enabled in **`☰` $\rightarrow$ Settings** (`Enable Extra Vertical Brake Slider`).
   - **Lock Gyroscope**: Tap the center of the `+` cross to lock/unlock the gyroscope at `0%`.
   - **Edit Controls**: Tap **`☰`** $\rightarrow$ **Edit Controls**, then tap any button to rename it, change its vJoy button (`1..32`), or choose between **One Click**, **Hold (While Pressed)**, and **Stay Pressed (On/Off)**.
