#!/bin/bash
# Roda o app Android: compila, instala e (opcional) abre no device/emulador
# Uso: ./run-android.sh   (a partir da pasta app-kotlin)

set -e
cd "$(dirname "$0")/android"

echo "🔨 Compilando e instalando..."
./gradlew installDebug

if [ $? -eq 0 ]; then
  echo "✅ Instalado."
  if adb devices | grep -q "device$"; then
    echo "📱 Abrindo o app..."
    adb shell am start -n com.pedidosexpress/.MainActivity
  fi
fi
