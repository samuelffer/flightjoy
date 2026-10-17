# Diretrizes do Agente — FlightJoy (`agents.md`)

Este documento define o padrão obrigatório de desenvolvimento, arquitetura, testes e empacotamento (CI/CD via GitHub Actions) para todas as alterações no projeto **FlightJoy**.

---

## 1. Regras de Ouro a Cada Nova Alteração

Sempre que o usuário solicitar uma nova funcionalidade, correção ou ajuste, siga rigorosamente este fluxo:

1. **Não invente APIs**:
   - **Windows / vJoy**: Utilize exclusivamente a API nativa oficial exportada pela `vJoyInterface.dll` (SDK vJoy 2.1.9.1: `vJoyEnabled`, `GetvJoyVersion`, `DriverMatch`, `GetVJDStatus`, `AcquireVJD`, `RelinquishVJD`, `ResetVJD`, `ResetButtons`, `SetAxis`, `SetBtn`, `GetVJDAxisExist`, `GetVJDAxisMin`, `GetVJDAxisMax`, `HID_USAGE_X..SL1`).
   - **Android**: Utilize exclusivamente as APIs oficiais do Android SDK (`SensorManager`, `Sensor.TYPE_GAME_ROTATION_VECTOR` / `Sensor.TYPE_ROTATION_VECTOR`, `SensorManager.getRotationMatrixFromVector`, `DatagramSocket`, `SharedPreferences`).
2. **Arquitetura Modular (Proibido Código Monolítico)**:
   - Nunca concentre toda a lógica em um único arquivo.
   - Mantenha a separação de responsabilidades entre **Protocolo**, **Rede/Segurança**, **Processamento de Controle/Calibração**, **Integração vJoy / Sensores**, **Configuração**, **Logs** e **Interface (UI)**.
3. **Não embutir o instalador do driver vJoy**:
   - O usuário já possui o driver **vJoy 2.1.9.1** instalado no Windows. O cliente Windows localiza automaticamente a `vJoyInterface.dll` instalada pelo próprio vJoy (via Registro do Windows `HKLM\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\{8E31F76F-74C3-47F1-9550-E041EEDC5FBB}_is1` ou `C:\Program Files\vJoy\x64\vJoyInterface.dll`, com fallback para o diretório local do `.exe`).
4. **Testes Automatizados**:
   - Qualquer alteração no protocolo, criptografia de pareamento, curvas de sensibilidade, calibração ou watchdog deve manter e atualizar os testes unitários em `windows/FlightJoy.Tests` (xUnit) e `android/app/src/test` (JUnit).
5. **Regra do Pacote `.zip` no GitHub Actions**:
   - O workflow `.github/workflows/build-release.yml` compila e gera um artefato `.zip` pronto para teste:
     - **Alteração em ambos (`android/` e `windows/`) ou build inicial/completo**: O `.zip` final (`FlightJoy-Bundle.zip`) **deve conter ambos** (`Android/FlightJoy-debug.apk` e `Windows/FlightJoy.Windows.exe` + arquivos necessários).
     - **Alteração apenas no Android (`android/`)**: O workflow compila apenas o Android e o `.zip` final contém **apenas o APK modificado**, sem recompilar nem incluir desnecessariamente o cliente Windows.
     - **Alteração apenas no Windows (`windows/`)**: O workflow compila apenas o Windows e o `.zip` final contém **apenas o `.exe` + arquivos necessários modificados**, sem recompilar nem incluir desnecessariamente o APK Android.
   - Sempre faça commit e push na branch de testes ativa para disparar o GitHub Actions e valide que a execução (`gh run list` / `gh run view`) concluiu com sucesso.
6. **Documentação de Dependências (`README.md`)**:
   - Sempre que uma nova biblioteca ou requisito de sistema no PC ou Android for adicionado, atualize imediatamente a seção de dependências do `README.md`.

---

## 2. Estrutura Modular do Repositório

```text
flightjoy/
├── agents.md                        # Este arquivo de regras contínuas do projeto
├── README.md                        # Guia de instalação, dependências no PC, MVP vs Futuro e uso
├── docs/
│   ├── ARCHITECTURE.md              # Visão arquitetural e decisões técnicas
│   └── PROTOCOL.md                  # Especificação do protocolo UDP FlightJoy v1
├── .github/workflows/
│   └── build-release.yml            # Pipeline condicional (Android / Windows / Ambos -> ZIP)
├── windows/
│   ├── FlightJoy.sln                # Solution .NET 8
│   ├── FlightJoy.Core/              # Núcleo de domínio (Protocol, Network, Control, Config, Logging, VJoy)
│   ├── FlightJoy.Windows/           # Interface WPF minimalista (.NET 8 Windows x64)
│   └── FlightJoy.Tests/             # Testes unitários xUnit
└── android/
    ├── app/src/main/                # App Android Kotlin (sensor, protocol, network, control, config, ui)
    └── app/src/test/                # Testes unitários JUnit
```

---

## 3. Compatibilidade de Protocolo (Android <-> Windows)

- **Porta padrão**: UDP `49152` (configurável).
- **Segurança**: Handshake `PAIR_REQ` / `PAIR_ACK` autenticado com PIN de 6 dígitos via `HMAC-SHA256`, derivando chave de sessão exclusiva (`sessionKey`) sem trafegar o PIN em texto aberto.
- **Representação de Eixos na Rede**: Valores inteiros escalados em `[-10000, +10000]` (equivalentes a `[-1.0000, +1.0000]`), prevenindo erros de formatação decimal regional (`pt-BR` vírgula vs ponto) no cálculo do `HMAC-SHA256`.
- **Fail-safe de Desconexão**: Watchdog configurável (padrão `1000 ms`) que neutraliza eixos e botões do vJoy caso a transmissão seja interrompida.
