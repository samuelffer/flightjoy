# Arquitetura do Sistema — FlightJoy

## 1. Visão Geral

O **FlightJoy** é dividido em dois subsistemas desacoplados que se comunicam exclusivamente pelo protocolo **FlightJoy UDP v1** na rede Wi-Fi local:

1. **FlightJoy Android (`android/`)**:
   - Captura em tempo real o vetor de rotação fundido (`Sensor.TYPE_GAME_ROTATION_VECTOR` / `Sensor.TYPE_ROTATION_VECTOR`), sliders de potência/compensador e botões multi-touch.
   - Realiza a calibração geométrica 3D por multiplicação de matriz de rotação relativa ($R_{\text{rel}} = R_{\text{ref}}^T \cdot R_{\text{cur}}$), garantindo que o usuário possa segurar o celular em qualquer inclinação confortável e definir aquele ponto como `(0, 0, 0)` sem *gimbal lock*.
   - Assina cada pacote de controle com `HMAC-SHA256` usando a chave de sessão efêmera negociada no pareamento e transmite via `DatagramSocket` a até 100 Hz.

2. **FlightJoy Windows (`windows/`)**:
   - **`FlightJoy.Core`**: Biblioteca de domínio independente de UI contendo:
     - `Protocol/`: Serializador JSON UTF-8 e modelos de pacotes com eixos inteiros escalados (`[-10000, +10000]`).
     - `Network/`: `PairingAuthenticator` (`HMAC-SHA256`), `ConnectionWatchdog` (detecção de desconexão) e `UdpControlServer`.
     - `Control/`: `AxisCurveProcessor` (zona morta, curva exponencial, ganho, inversão e filtro passa-baixa), `CalibrationOffset` e `ControlPipeline`.
     - `VJoy/`: `VJoyNativeMethods` (P/Invoke `vJoyInterface.dll` 2.1.9.1 com resolvedor automático pelo Registro do Windows), `VJoyFeederService` e `MockVJoyFeeder`.
     - `Config/` & `Logging/`: Persistência em `flightjoy.config.json` e logs estruturados em arquivo (`logs/flightjoy-YYYYMMDD.log`) e memória.
   - **`FlightJoy.Windows`**: Interface gráfica WPF minimalista com tema escuro de cockpit e três abas (Monitor em Tempo Real, Mapeamento & Curvas, Logs).
   - **`FlightJoy.Tests`**: Suíte de testes unitários e de integração em xUnit.

---

## 2. Integração Oficial com vJoy 2.1.9.1

Não são utilizadas APIs fictícias. A integração utiliza diretamente os pontos de entrada nativos documentados no arquivo de cabeçalho `vjoyinterface.h` e `public.h` do SDK oficial vJoy 2.1.9.1:

- **Resolução da DLL**:
  O instalador oficial do vJoy 2.1.9.1 grava sua localização na chave de registro:
  `HKEY_LOCAL_MACHINE\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\{8E31F76F-74C3-47F1-9550-E041EEDC5FBB}_is1`
  valores `DllX64Location` / `InstallLocation` (padrão `C:\Program Files\vJoy\x64\vJoyInterface.dll`).
  `VJoyNativeMethods` registra `NativeLibrary.SetDllImportResolver` para carregar automaticamente essa DLL instalada pelo próprio driver do usuário.
- **Funções Nativas Utilizadas**:
  - `vJoyEnabled()`, `GetvJoyVersion()`, `DriverMatch(out ushort dllVer, out ushort drvVer)`
  - `GetVJDStatus(uint rID)` (`VJD_STAT_OWN`, `VJD_STAT_FREE`, `VJD_STAT_BUSY`, `VJD_STAT_MISS`, `VJD_STAT_UNKN`)
  - `AcquireVJD(uint rID)`, `RelinquishVJD(uint rID)`, `ResetVJD(uint rID)`, `ResetButtons(uint rID)`
  - `GetVJDAxisExist(uint rID, uint Axis)`, `GetVJDAxisMin(uint rID, uint Axis, out int Min)`, `GetVJDAxisMax(uint rID, uint Axis, out int Max)`
  - `SetAxis(int Value, uint rID, uint Axis)` (`HID_USAGE_X = 0x30` até `HID_USAGE_SL1 = 0x37`)
  - `SetBtn(bool Value, uint rID, byte nBtn)` (`1..32`)
