# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /usr/local/Cellar/android-sdk/24.3.3/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# react-native-reanimated
-keep class com.swmansion.reanimated.** { *; }
-keep class com.facebook.react.turbomodule.** { *; }

# Keep custom native modules
-keep class com.tamborilburguer.admin.** { *; }

# Keep ESC/POS Printer library
-keep class com.dantsu.escposprinter.** { *; }
-keepclassmembers class com.dantsu.escposprinter.** { *; }

# Keep React Native modules
-keep class com.facebook.react.bridge.** { *; }
-keep class com.facebook.react.modules.** { *; }

# Keep Bluetooth classes
-keep class android.bluetooth.** { *; }

# Add any project specific keep options here:
