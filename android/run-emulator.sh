#!/bin/bash

echo "🚀 Iniciando emulador Android..."

# Verificar se há emuladores disponíveis
AVDS=$(emulator -list-avds 2>/dev/null | head -1)

if [ -z "$AVDS" ]; then
    echo "❌ Nenhum emulador encontrado!"
    echo "Abra o Android Studio e crie um AVD primeiro."
    exit 1
fi

echo "📱 Emulador encontrado: $AVDS"
echo "Iniciando emulador em background..."

# Iniciar emulador em background
emulator -avd "$AVDS" > /dev/null 2>&1 &

echo "⏳ Aguardando emulador iniciar..."
echo "(Isso pode levar 30-60 segundos)"

# Aguardar até o emulador estar pronto
for i in {1..60}; do
    if adb devices | grep -q "device$"; then
        echo "✅ Emulador pronto!"
        adb devices
        echo ""
        echo "Agora você pode executar:"
        echo "  cd android && ./gradlew installDebug"
        exit 0
    fi
    sleep 2
    echo -n "."
done

echo ""
echo "⚠️  Emulador demorou muito para iniciar. Verifique manualmente com: adb devices"
