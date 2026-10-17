# FlightJoy — Smartphone Android como Joystick Virtual para Simuladores de Voo no Windows

O **FlightJoy** transforma um celular Android em um manche/joystick virtual de baixa latência para simuladores de voo no PC (**Microsoft Flight Simulator**, **X-Plane**, **DCS World**, **FlightGear**, **IL-2 Sturmovik**), utilizando os sensores de orientação do aparelho (giroscópio + acelerômetro), sliders grossos anti-toque acidental para **Potência (Throttle)** e **Pedais (Rudder)**, 8 botões editáveis e alimentação direta do driver **vJoy (v2.1.9.1)** via rede Wi-Fi local (UDP).

---

## Download Direto (Pasta `Zips/`)

O pacote unificado contendo o **APK Android** e o **Cliente Windows (`.exe`)** fica salvo diretamente na pasta [`Zips/FlightJoy.zip`](./Zips/FlightJoy.zip) do repositório.

---

## Principais Recursos

- **Visual Monocromático (Preto, Branco e Cinza)**: Interface limpa, sem poluição visual tanto no PC quanto no Android.
- **Conexão Rápida Sem PIN + Busca Automática na Rede (`🔍`)**:
  - Clique no botão de menu (**`☰`**) no celular e toque na **lupa (`🔍`)** ao lado do IP para localizar automaticamente o seu PC na rede Wi-Fi (exibindo um *Toast* com o nome do seu Desktop).
- **Sliders Grossos com Trava Anti-Toque Acidental**:
  - **Esquerda (Vertical)**: Slider grosso de **Potência (Throttle)** com suavização progressiva.
  - **Direita Inferior (Horizontal)**: Slider grosso de **Rudder (Pedais)** com botão rápido para centralizar em `0%`.
  - **Trava no "Quadradinho"**: Os sliders **só se movem quando você clica e arrasta diretamente em cima do quadradinho (handle)**, evitando toques acidentais na barra.
- **Botões Organizados e Editáveis no Celular**:
  - Segure qualquer um dos 8 botões na tela principal para editar o **Nome**, o **Botão vJoy correspondente no PC (`1 a 32`)** e o modo (**Segurar** ou **Interruptor Liga/Desliga**).
- **Mapeamento Exclusivo pelo Celular**:
  - Todo o mapeamento de eixos (`X, Y, Z, RX, RY, RZ, SL0, SL1`), botões (`1..32`), sensibilidade e zona morta é configurado diretamente pelo celular.
- **Cliente PC Minimalista com Avisos Sonoros e Minimização**:
  - Efeito sonoro discreto: **1 toque suave** ao conectar com sucesso e **2 toques** ao desconectar.
  - Ao clicar no **`X`** da janela do Windows, o programa **não fecha** — ele permanece aberto e minimizado na barra de tarefas (para encerrar totalmente, use o botão *"Encerrar"*).

---

## Dependências Obrigatórias no PC (Windows 10/11 x64)

1. **Driver vJoy 2.1.9.1 instalado e ativo**:
   - Caso já tenha o **vJoy 2.1.9.1** instalado, nenhuma instalação adicional do driver é necessária.
   - Certifique-se no **Configure vJoy** de que o **Device 1** está habilitado com os eixos **X, Y, Z, RX, RY, RZ, Slider (`SL0`)** e pelo menos **8 a 32 botões**.
2. **.NET 8 Desktop Runtime (Windows x64)**:
   - Caso o Windows solicite ao abrir o `FlightJoy.Windows.exe`, instale o pacote oficial da Microsoft:
   - **Microsoft .NET 8.0 Desktop Runtime (x64)**: `https://dotnet.microsoft.com/en-us/download/dotnet/8.0` (seção *".NET Desktop Runtime 8.0.x"* -> instalador **x64**).
3. **Rede Local (Wi-Fi / LAN)**:
   - O PC e o smartphone Android devem estar conectados à mesma rede local. Libere a porta **UDP `49152`** no Firewall do Windows caso solicitado.
