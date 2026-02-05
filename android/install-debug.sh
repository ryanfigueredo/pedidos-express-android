#!/bin/bash

echo "📦 Instalando app no emulador/dispositivo..."

cd "$(dirname "$0")"

# Verificar se há dispositivo conectado
if ! adb devices | grep -q "device$"; then
    echo "❌ Nenhum dispositivo/emulador conectado!"
    echo ""
    echo "Opções:"
    echo "1. Abra o Android Studio e inicie um emulador"
    echo "2. Ou execute: ./run-emulator.sh"
    echo "3. Ou conecte um dispositivo Android via USB"
    exit 1
fi

echo "✅ Dispositivo encontrado!"
adb devices

echo ""
echo "🔨 Compilando e instalando app..."
./gradlew installDebug

if [ $? -eq 0 ]; then
    echo ""
    echo "✅ App instalado com sucesso!"
    echo ""
    echo "Para abrir o app, execute:"
    echo "  adb shell am start -n com.tamborilburguer.admin/.MainActivity"
    echo ""
    echo "Ou abra manualmente no dispositivo/emulador"
else
    echo ""
    echo "❌ Erro ao instalar app"
fi
