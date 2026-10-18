<div align="center">

<img src="windows/FlightJoy.Windows/icon.png" width="110" alt="FlightJoy Logo" />

# FlightJoy

**Use o celular Android como manche e controles de voo no PC via Wi-Fi.**

[![Download ZIP](https://img.shields.io/badge/Download-FlightJoy.zip-white?style=for-the-badge&logo=github&logoColor=black)](./Zips/FlightJoy.zip)

![Windows](https://img.shields.io/badge/Windows_10%2F11-0078D6?style=flat-square&logo=windows&logoColor=white)
![Android](https://img.shields.io/badge/Android_8.0+-3DDC84?style=flat-square&logo=android&logoColor=black)
![.NET 8](https://img.shields.io/badge/.NET_8.0-512BD4?style=flat-square&logo=dotnet&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=flat-square&logo=kotlin&logoColor=white)
![vJoy](https://img.shields.io/badge/Driver-vJoy_2.1.9.1-222222?style=flat-square)

</div>

---

## 📦 Download

Baixe o pacote pronto (contendo o `.apk` de Android e o `.exe` de Windows) direto na pasta [`Zips/FlightJoy.zip`](./Zips/FlightJoy.zip).

---

## ⚙️ Requisitos no PC

Para o programa abrir e enviar os comandos ao simulador no Windows, você precisa de apenas dois itens instalados:

| Requisito | Descrição | Link |
| :--- | :--- | :--- |
| <img src="https://cdn.simpleicons.org/dotnet/512BD4" width="20" align="center" /> **.NET 8 Desktop Runtime** | Necessário para abrir o `FlightJoy.Windows.exe` (versão **x64**). | [Baixar da Microsoft](https://dotnet.microsoft.com/en-us/download/dotnet/8.0) |
| 🎮 **vJoy 2.1.9.1** | Driver de joystick virtual no Windows (com **Device 1** ativo). | [vJoy 2.1.9.1](https://sourceforge.net/projects/vjoystick/) |

---

## 🕹️ Como Usar

1. Abra o **`FlightJoy.Windows.exe`** no PC.
2. Abra o app **FlightJoy** no celular (na mesma rede Wi-Fi).
3. Toque no menu **`☰`** $\rightarrow$ clique na lupa **`🔍`** para achar o PC automaticamente $\rightarrow$ **Conectar**.
4. **Dicas rápidas no celular**:
   - **Sliders (Throttle e Rudder)**: Só mexem arrastando direto pelo **quadradinho**, evitando esbarrões.
   - **Modo Reverso (`REV`)**: Ative o switch retangular ao lado do Throttle para puxar o reverso de cima para baixo (`0%` a `100%`).
   - **Travar Giroscópio**: Toque no meio da cruz central para travar/destravar o manche em `0%`.
   - **Editar Botões**: Segure o dedo sobre qualquer um dos 8 botões para mudar o nome, modo (*Push/Switch*) ou botão vJoy (`1..32`).
