# Diretrizes do Agente — FlightJoy (`agents.md`)

Este documento define o padrão obrigatório de desenvolvimento, arquitetura, interface, empacotamento na pasta `Zips/` e entrega do link direto de download para todas as alterações no projeto **FlightJoy**.

---

## 1. Regras Obrigatórias a Cada Nova Alteração

1. **Pasta `Zips/` no Próprio Repositório (Sem criar GitHub Releases)**:
   - **Nunca** crie GitHub Releases ou tags a cada build.
   - O repositório possui a pasta **`Zips/`** contendo **um único arquivo `.zip` unificado**: `Zips/FlightJoy.zip` (com `Android/FlightJoy-Android-debug.apk` e `Windows/FlightJoy.Windows.exe` dentro).
   - **Otimização de Tempo no GitHub Actions**:
     - Se a demanda exigir mexer nos **dois programas** (ex.: mudança de protocolo ou pedido que afete PC e celular), o workflow compila **ambos** e gera o `Zips/FlightJoy.zip` atualizado.
     - Se a demanda exigir mexer **apenas no Android**, o workflow compila **apenas o APK** (ganhando tempo) e atualiza o `Zips/FlightJoy.zip` (mantendo o `.exe` já existente na pasta `Zips/` junto com o novo `.apk`).
     - Se a demanda exigir mexer **apenas no Windows**, o workflow compila **apenas o `.exe`** (ganhando tempo) e atualiza o `Zips/FlightJoy.zip` (mantendo o `.apk` já existente na pasta `Zips/` junto com o novo `.exe`).
   - O próprio workflow faz commit e push automático do `Zips/FlightJoy.zip` atualizado para a branch (`[skip ci]`), e o agente deve fazer `git pull` após o término da Action para manter o workspace sincronizado.

2. **REGRA OBRIGATÓRIA: Sempre Fornecer o Link Pronto para Download**:
   - Ao concluir qualquer alteração e aguardar o GitHub Actions finalizar com sucesso, **sempre forneça o link direto pronto para download (1 clique)** do arquivo na pasta `Zips/`:
     - 👉 `https://github.com/samuelffer/flightjoy/raw/arena/b556b42d-flightjoy/Zips/FlightJoy.zip`
     - E também o link do artefato da execução no GitHub Actions (`https://github.com/samuelffer/flightjoy/actions/runs/<RUN_ID>/artifacts/<ARTIFACT_ID>`).

3. **Diretrizes de Produto e Interface**:
   - **Tema Visual**: Paleta monocromática minimalista em **Preto, Branco e Cinza** (`#0A0A0A`, `#161616`, `#262626`, `#9E9E9E`, `#FFFFFF`), sem poluição visual.
   - **Sem Sistema de PIN**: Conexão direta sem PIN, com **Busca Rápida (botão de lupa 🔍)** no celular via UDP Broadcast que localiza automaticamente o PC na mesma rede e exibe um Toast com o nome do desktop encontrado.
   - **Configuração Exclusiva no Celular**: Todo o mapeamento de eixos vJoy, mapeamento e edição de nomes dos botões (`1..32`), sensibilidade, zona morta e calibração são feitos **exclusivamente no aplicativo Android** (acessíveis pelo menu de 3 listras `☰` que abre uma janela na tela fechável com `✕`).
   - **Sliders e Controles no Celular**:
     - Sem slider de Trim.
     - **Esquerda (Vertical)**: Slider grosso de **Potência (Throttle)** com leve suavização para ajuste fluido.
     - **Direita Inferior (Horizontal)**: Slider grosso de **Rudder (Pedais)**.
     - **Trava Anti-Toque Acidental**: Os sliders **só se movem se o toque iniciar exatamente em cima do quadradinho (handle)** e for arrastado.
     - **Centro**: Indicador minimalista do giroscópio.
     - **Botões**: Organizados por uso e editáveis (nome, botão vJoy `1..32` e modo Push/Toggle).
   - **Comportamento no Cliente Windows (PC)**:
     - Interface limpa em preto, branco e cinza exibindo apenas status de conexão, nome do PC, IP, driver vJoy e monitor compacto.
     - **Efeito Sonoro**: Som satisfatório de **1 toque** ao conectar e **2 toques** ao desconectar.
     - **Minimizar no X**: Ao clicar no `X` da janela, o programa **não fecha**; ele permanece minimizado na barra de tarefas (com botão dedicado caso o usuário queira encerrar de vez).
