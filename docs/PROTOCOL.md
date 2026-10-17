# Especificação do Protocolo — FlightJoy UDP v1

## 1. Transporte e Codificação

- **Transporte**: UDP sobre IPv4 (porta padrão `49152`).
- **Codificação**: JSON UTF-8 compacto em linha única.
- **Representação de Eixos**: Inteiros escalados em `[-10000, +10000]` (onde `-10000 = -1.0` e `+10000 = +1.0`), evitando divergências de separador decimal entre diferentes configurações regionais (`pt-BR` vs `en-US`) durante o cálculo do `HMAC-SHA256`.

---

## 2. Pareamento Seguro e Derivação de Chave de Sessão (`HMAC-SHA256`)

O pareamento utiliza um código **PIN de 6 dígitos** exibido no cliente Windows. O PIN nunca é transmitido na rede.

1. **`PAIR_REQ` (Android -> Windows)**:
   - Campos: `v`, `type="PAIR_REQ"`, `deviceId`, `deviceName`, `clientNonce`, `ts`, `mac`.
   - String canônica assinada com `key = PIN`:
     `PAIR_REQ|1|{deviceId}|{deviceName}|{clientNonce}|{ts}`
2. **`PAIR_ACK` (Windows -> Android)**:
   - Campos: `v`, `type="PAIR_ACK"`, `deviceId`, `clientNonce`, `sessionId`, `ts`, `mac`.
   - String canônica assinada com `key = PIN`:
     `PAIR_ACK|1|{deviceId}|{clientNonce}|{sessionId}|{ts}`
3. **Derivação da Chave de Sessão (`sessionKey`)**:
   - Ambos os lados calculam de forma independente:
     `sessionKey = HMAC_SHA256_HEX(key = PIN, data = "SESSION|1|{deviceId}|{clientNonce}|{sessionId}")`

---

## 3. Transmissão Contínua de Controle (`STATE`)

- **Direção**: Android -> Windows (30 / 60 / 100 Hz).
- **Campos**:
  - `v`: `1`
  - `type`: `"STATE"`
  - `sessionId`: Identificador da sessão ativa
  - `seq`: Número de sequência monotônico crescente (pacotes fora de ordem ou duplicados com `seq <= lastSeq` são descartados imediatamente)
  - `ts`: Timestamp em milissegundos
  - `p`, `r`, `y`, `t`, `a`: Eixos escalados `[-10000, +10000]` (Pitch, Roll, Yaw, Throttle, Aux)
  - `b`: Máscara de bits dos botões (`bit 0 = B1`, ..., `bit 7 = B8`)
  - `mac`: `HMAC_SHA256_HEX(key = sessionKey, data = "STATE|1|{sessionId}|{seq}|{ts}|{p}|{r}|{y}|{t}|{a}|{b}")`

---

## 4. Heartbeat e Desconexão (`PING` / `PONG` / `DISCONNECT`)

- **`PING`**: `PING|1|{sessionId}|{seq}|{ts}`
- **`PONG`**: `PONG|1|{sessionId}|{seq}|{clientTs}` (ecoa `clientTs` para cálculo exato de RTT no Android sem depender de relógios sincronizados)
- **`DISCONNECT`**: `DISCONNECT|1|{sessionId}|{ts}`
