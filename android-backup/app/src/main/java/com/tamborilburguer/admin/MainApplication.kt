package com.tamborilburguer.admin

import android.app.Application
import android.content.res.Configuration

import com.facebook.react.PackageList
import com.facebook.react.ReactApplication
import com.facebook.react.ReactNativeApplicationEntryPoint.loadReactNative
import com.facebook.react.ReactNativeHost
import com.facebook.react.ReactPackage
import com.facebook.react.ReactHost
import com.facebook.react.common.ReleaseLevel
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint
import com.facebook.react.defaults.DefaultReactNativeHost

import expo.modules.ApplicationLifecycleDispatcher
import expo.modules.ReactNativeHostWrapper

import com.tamborilburguer.admin.BluetoothPairPackage
import com.tamborilburguer.admin.EscPosPrinterPackage

class MainApplication : Application(), ReactApplication {

  override val reactNativeHost: ReactNativeHost = ReactNativeHostWrapper(
      this,
      object : DefaultReactNativeHost(this) {
        override fun getPackages(): List<ReactPackage> {
          val packages = PackageList(this).packages.toMutableList()
          try {
            android.util.Log.d("MainApplication", "Adding custom packages...")
            // Módulo customizado para pareamento Bluetooth programático
            try {
              packages.add(BluetoothPairPackage())
              android.util.Log.d("MainApplication", "BluetoothPairPackage added")
            } catch (e: Exception) {
              android.util.Log.e("MainApplication", "Error adding BluetoothPairPackage: ${e.message}", e)
              e.printStackTrace()
            }
            // Módulo para impressão ESC/POS usando biblioteca que funciona
            // ⚠️ CRÍTICO: Este módulo deve SEMPRE estar habilitado - funcionalidade essencial
            try {
              packages.add(EscPosPrinterPackage())
              android.util.Log.d("MainApplication", "EscPosPrinterPackage added")
            } catch (e: Exception) {
              android.util.Log.e("MainApplication", "Error adding EscPosPrinterPackage: ${e.message}", e)
              e.printStackTrace()
            }
            android.util.Log.d("MainApplication", "All custom packages processed")
          } catch (e: Exception) {
            android.util.Log.e("MainApplication", "Fatal error adding custom packages: ${e.message}", e)
            e.printStackTrace()
          }
          return packages
        }

          override fun getJSMainModuleName(): String {
            // Em debug, usa Metro bundler. Em release, ainda precisa retornar um valor válido
            return if (BuildConfig.DEBUG) ".expo/.virtual-metro-entry" else "index"
          }

          override fun getUseDeveloperSupport(): Boolean = BuildConfig.DEBUG
          
          override fun getBundleAssetName(): String {
            // Sempre retorna o nome do bundle. Em debug, Metro vai sobrescrever. Em release, usa este.
            return "index.android.bundle"
          }
          
          // Garantir que Hermes está desabilitado
          @Deprecated("Hermes está desabilitado, usando JSC")
          override val isHermesEnabled: Boolean
            get() = false

          override val isNewArchEnabled: Boolean = BuildConfig.IS_NEW_ARCHITECTURE_ENABLED
      }
  )

  override val reactHost: ReactHost
    get() = ReactNativeHostWrapper.createReactHost(applicationContext, reactNativeHost)

  override fun onCreate() {
    android.util.Log.d("MainApplication", "onCreate called")
    super.onCreate()
    try {
      android.util.Log.d("MainApplication", "Setting release level...")
      DefaultNewArchitectureEntryPoint.releaseLevel = try {
        ReleaseLevel.valueOf(BuildConfig.REACT_NATIVE_RELEASE_LEVEL.uppercase())
      } catch (e: IllegalArgumentException) {
        android.util.Log.w("MainApplication", "Invalid release level, using STABLE")
        ReleaseLevel.STABLE
      }
      android.util.Log.d("MainApplication", "Loading React Native...")
      loadReactNative(this)
      android.util.Log.d("MainApplication", "React Native loaded, dispatching application create")
      ApplicationLifecycleDispatcher.onApplicationCreate(this)
      android.util.Log.d("MainApplication", "onCreate completed successfully")
    } catch (e: Exception) {
      android.util.Log.e("MainApplication", "FATAL ERROR in onCreate: ${e.message}", e)
      e.printStackTrace()
      throw e
    }
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    ApplicationLifecycleDispatcher.onConfigurationChanged(this, newConfig)
  }
}
