# FlightJoy ✈️📱🕹️

**FlightJoy** é um sistema modular de baixa latência que transforma um smartphone **Android** em um joystick/manche físico virtual para simuladores de voo no **Windows** (Microsoft Flight Simulator, X-Plane, DCS World, FlightGear, IL-2, etc.), utilizando a rede Wi-Fi local (UDP) e o driver **vJoy 2.1.9.1**.

---

## 📦 O que você precisa ter instalado no PC (Dependências Windows)

Para rodar o executável de testes (`FlightJoy.Windows.exe`) no seu computador Windows 10/11 (64-bit), você precisa apenas de **2 itens**:

### 1. Driver vJoy 2.1.9.1 *(Você já possui instalado)*
- O cliente Windows **não embute o instalador do vJoy** e carrega automaticamente a biblioteca oficial `vJoyInterface.dll` diretamente da sua instalação do vJoy (`C:\Program Files\vJoy\x64\vJoyInterface.dll` ou via Registro do Windows).
- **Como verificar se o Dispositivo vJoy #1 está pronto**:
  1. Abra o programa **Configure vJoy** (`vJoyConf.exe`) no menu Iniciar.
  2. Na aba **Device 1**, marque a caixa **Enable vJoy** (canto inferior) e habilite os eixos:
     - `X`, `Y`, `Z`, `Rx`, `Ry`, `Rz`, `Slider`, `Dial/Slider2`
     - `Number of Buttons`: pelo menos `8` (ou `32`)
  3. Clique em **Apply**.

### 2. Microsoft .NET 8.0 Desktop Runtime (x64)
- O cliente Windows foi desenvolvido em **C# / .NET 8.0 (WPF)** para máxima performance de rede assíncrona e baixa latência.
- *(Nota: o `.NET Framework 4.8` nativo do Windows é diferente do `.NET 8.0` moderno).*
- Caso ainda não tenha o **.NET 8.0 Desktop Runtime (x64)** instalado no PC, instale-o em segundos pelo terminal (PowerShell / CMD) com o comando oficial da Microsoft:
  ```powershell
  winget install Microsoft.DotNet.DesktopRuntime.8
  ```
  Ou baixe o instalador oficial diretamente do site da Microsoft:
  - 🔗 [Download .NET 8.0 Desktop Runtime (Windows x64)](https://dotnet.microsoft.com/pt-br/download/dotnet/8.0) *(procure por **.NET Desktop Runtime 8.0.x -> x64**)*.

### 3. Rede Wi-Fi Local e Firewall do Windows
- O celular e o PC devem estar conectados na **mesma rede local** (PC via cabo ou Wi-Fi no mesmo roteador que o Wi-Fi do celular).
- Na primeira vez que abrir o `FlightJoy.Windows.exe`, o **Firewall do Windows** perguntará se deseja permitir o acesso à rede: marque **Redes Privadas** e clique em **Permitir acesso** (porta padrão **UDP `49152`**).

---

## 🚀 Como Baixar e Testar (GitHub Actions)

A cada alteração enviada para a branch de testes, o workflow do **GitHub Actions** (`FlightJoy CI — Build APK & Windows EXE`) executa os testes automatizados e gera o pacote `.zip`:

1. Acesse a aba **Actions** do repositório no GitHub.
2. Clique na execução mais recente da branch.
3. Na seção **Artifacts** (rodapé da página da execução), baixe o arquivo **`FlightJoy-Package-...`**.
   - Se **ambos** os projetos foram criados/alterados, o `.zip` conterá:
     - `Android/FlightJoy-Android-debug.apk`
     - `Windows/FlightJoy.Windows.exe` (+ arquivos de configuração)
   - Se **apenas o Android** foi modificado em um pedido seu, o `.zip` conterá apenas o APK atualizado.
   - Se **apenas o Windows** foi modificado em um pedido seu, o `.zip` conterá apenas o `.exe` atualizado.

### Passo a Passo do Teste Prático:
1. **No PC (Windows)**:
   - Extraia a pasta `Windows` do `.zip` e execute **`FlightJoy.Windows.exe`**.
   - Observe no topo da janela o **IP Local do PC** (ex.: `192.168.1.15`) e o **PIN de Pareamento** de 6 dígitos (ex.: `482910`).
   - Verifique na barra de status se aparece **`vJoy Ativo — Device #1`**.
2. **No Celular (Android)**:
   - Instale o **`FlightJoy-Android-debug.apk`** (permita instalar apps de fontes desconhecidas para teste).
   - Abra o app **FlightJoy** (ele abre automaticamente em modo Paisagem / Manche).
   - Digite o **IP do PC**, a **Porta** (`49152`) e o **PIN de Pareamento** exibido no PC.
   - Toque em **Conectar**.
   - Segure o celular na posição em que deseja voar confortavelmente e toque em **Calibrar Centro**!
3. **Validar no Windows**:
   - Na aba **1. Monitor de Controles & Calibração** do programa no PC, incline o celular, mova os sliders (**THR** e **AUX/TRIM**) e pressione os botões **B1 a B8** para ver as barras reagindo em tempo real.
   - Você também pode pressionar `Win + R`, digitar `joy.cpl` e dar Enter para ver o **vJoy Device** se movendo nativamente no Windows!

---

## 🎯 Escopo: MVP (Versão Atual) vs. Versões Futuras (Roadmap)

Conforme boas práticas de engenharia, separamos claramente o que faz parte deste **MVP funcional** e o que ficará para **versões futuras**:

### ✅ Incluído no MVP (Implementado Agora)
| Categoria | Funcionalidade Implementada |
| :--- | :--- |
| **Sensores & Controles (Android)** | Fusão de giroscópio/acelerômetro (`TYPE_GAME_ROTATION_VECTOR` com fallback `TYPE_ROTATION_VECTOR`), 3 eixos de atitude (**Pitch, Roll, Yaw**), 2 Sliders (**Throttle 0-100%** e **Aux/Trim -100% a +100%**) e **8 botões multi-touch** (`B1..B8`). |
| **Calibração & Curvas** | Calibração de orientação 3D por matriz de rotação relativa no celular (**Calibrar Centro / Tara**) + Calibração de offset no PC, **Sensibilidade** (`0.1x a 3.0x`), **Zona Morta (Deadzone)**, **Curva Exponencial (Expo)**, **Filtro Passa-Baixa (Suavização)** e **Inversão por eixo**. |
| **Mapeamento Configurável** | Mapeamento livre de qualquer eixo do celular para qualquer eixo do vJoy (`X, Y, Z, RX, RY, RZ, SL0, SL1`) e botões (`B1..B8` -> `vJoy 1..32`), persistido em `flightjoy.config.json`. |
| **Baixa Latência & Rede** | Transmissão contínua via **UDP** configurável (`30 Hz`, `60 Hz`, `100 Hz`), controle de sequência (`seq`) contra pacotes fora de ordem e medição de **RTT (ms)** via `PING`/`PONG`. |
| **Pareamento Seguro** | Handshake desafio-resposta (`PAIR_REQ` / `PAIR_ACK`) com **PIN de 6 dígitos** e autenticação criptográfica **HMAC-SHA256** em todos os pacotes (o PIN nunca trafega em texto aberto na rede). |
| **Detecção de Desconexão** | **Watchdog Timer** configurável (padrão `1000 ms`) no Windows e no Android com **Fail-Safe** automático que centraliza/neutraliza os controles em caso de queda de Wi-Fi. |
| **Qualidade & CI/CD** | Arquitetura modular em múltiplos pacotes, logs em arquivo e na interface, testes unitários automatizados (**xUnit** no Windows e **JUnit** no Android) e pipeline **GitHub Actions**. |

### 🔮 Planejado para Versões Futuras (Pós-MVP)
| Fase Futura | Funcionalidade Planejada |
| :--- | :--- |
| **v0.2 — Descoberta Automática** | Descoberta automática do PC via mDNS / UDP Broadcast e pareamento por **QR Code** na tela do PC para não precisar digitar IP/PIN manualmente. |
| **v0.3 — Perfis por Aeronave** | Perfis salvos independentes (ex.: *Aviação Geral Cessna*, *Caça Militar*, *Helicóptero*, *Airliner Comercial*) com troca rápida e exportação/importação. |
| **v0.4 — Force Feedback / Haptics** | Recepção de eventos de Force Feedback do vJoy ou telemetria SimConnect para vibrar o celular no estol (stall shaker), toque de pista e disparo de armas. |
| **v0.5 — Suporte USB (ADB)** | Modo de conexão cabeada via USB tethering / ADB forward com latência sub-milissegundo além do Wi-Fi. |
| **v1.0 — Instalador Unificado** | Setup executável (`Inno Setup` / `WiX`) que verifica e instala automaticamente `.NET Desktop Runtime`, configura regras de Firewall e valida o driver `vJoy`. |

---

## 🗂️ Documentação Técnica Adicional

- [`agents.md`](agents.md) — Regras de engenharia, manutenção modular e padrão de geração do `.zip` no GitHub Actions.
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — Detalhamento da arquitetura modular do cliente Windows e do app Android.
- [`docs/PROTOCOL.md`](docs/PROTOCOL.md) — Especificação técnica completa do protocolo UDP FlightJoy v1 e segurança HMAC-SHA256.
