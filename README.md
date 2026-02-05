# Pedidos Express - Android App

Aplicativo Android nativo desenvolvido em Kotlin para o sistema Pedidos Express, permitindo que restaurantes gerenciem pedidos, cardápio e impressão de recibos.

## 📱 Sobre o Projeto

Este é o aplicativo Android do Pedidos Express, uma plataforma completa de gestão de pedidos para restaurantes. O app permite:

- 📋 Visualização e gerenciamento de pedidos em tempo real
- 🍔 Gerenciamento de cardápio e itens
- 🖨️ Impressão de recibos via impressoras térmicas ESC/POS
- 📊 Dashboard com estatísticas e métricas
- ⚙️ Configurações da loja
- 💬 Suporte integrado

## 🛠️ Tecnologias

- **Linguagem**: Kotlin
- **SDK Mínimo**: Android 7.0 (API 24)
- **SDK Alvo**: Android 14 (API 34)
- **Arquitetura**: MVVM com Fragments
- **Bibliotecas Principais**:
  - AndroidX (Core, AppCompat, Material Design)
  - OkHttp para requisições HTTP
  - Gson para serialização JSON
  - Coroutines para operações assíncronas
  - MPAndroidChart para gráficos
  - Lottie para animações
  - ESCPOS-ThermalPrinter para impressão térmica

## 📦 Estrutura do Projeto

```
app-kotlin/
├── android/
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── java/com/pedidosexpress/
│   │   │   │   ├── ApiService.kt          # Serviço de API
│   │   │   │   ├── AuthService.kt         # Autenticação
│   │   │   │   ├── MainActivity.kt        # Activity principal
│   │   │   │   ├── DashboardFragment.kt  # Dashboard
│   │   │   │   ├── OrdersFragment.kt      # Lista de pedidos
│   │   │   │   ├── MenuFragment.kt        # Cardápio
│   │   │   │   ├── SettingsFragment.kt    # Configurações
│   │   │   │   └── PrinterHelper.kt       # Helper de impressão
│   │   │   └── res/                       # Recursos (layouts, drawables, etc)
│   │   └── build.gradle
│   └── build.gradle
└── android-backup/                        # Backup da versão anterior
```

## 🚀 Como Executar

### Pré-requisitos

- Android Studio Hedgehog ou superior
- JDK 8 ou superior
- Android SDK com API 24+
- Dispositivo Android ou emulador

### Instalação

1. Clone o repositório:
```bash
git clone https://github.com/ryanfigueredo/pedidos-express-android.git
cd pedidos-express-android
```

2. Abra o projeto no Android Studio:
   - File → Open → Selecione a pasta `android`

3. Configure as variáveis de ambiente:
   - Crie um arquivo `local.properties` na pasta `android/` se não existir
   - Configure `sdk.dir` apontando para seu Android SDK

4. Sincronize o Gradle:
   - O Android Studio fará isso automaticamente, ou clique em "Sync Now"

5. Execute o app:
   - Conecte um dispositivo Android ou inicie um emulador
   - Clique em "Run" ou pressione `Shift+F10`

### Build de Release

```bash
cd android
./gradlew assembleRelease
```

O APK será gerado em `app/build/outputs/apk/release/app-release.apk`

## 🔧 Configuração

### API Endpoint

Configure a URL da API no arquivo `ApiService.kt`:

```kotlin
private const val BASE_URL = "https://sua-api.com/api"
```

### Autenticação

O app utiliza autenticação via token JWT. As credenciais são armazenadas localmente usando SharedPreferences.

## 📱 Funcionalidades

### Dashboard
- Visualização de pedidos pendentes
- Estatísticas de vendas
- Status da loja (aberta/fechada)

### Pedidos
- Lista de pedidos em tempo real
- Filtros por status
- Detalhes do pedido
- Marcação de impresso/enviado

### Cardápio
- Visualização de itens
- Edição de preços e disponibilidade
- Categorias

### Impressão
- Suporte para impressoras térmicas ESC/POS
- Impressão de recibos de pedidos
- Configuração de impressora

## 🔐 Segurança

- Credenciais armazenadas de forma segura
- Comunicação HTTPS com a API
- Validação de tokens JWT

## 📄 Licença

Este projeto é privado e proprietário.

## 👥 Contribuição

Este é um projeto privado. Para questões ou sugestões, entre em contato com a equipe de desenvolvimento.

## 📞 Suporte

Para suporte técnico, abra uma issue no repositório ou entre em contato através do app na seção "Suporte".

---

**Versão**: 1.0.1  
**Última atualização**: 2025
