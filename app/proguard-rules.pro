# Keep the AppFunction service and its data classes, Gemini calls them by name.
-keep class de.zeroxion.einkaufsliste.appfunctions.** { *; }

# ML Kit (Barcode + Texterkennung, Modelle in der App).
# R8 (full mode) entfernt sonst Konstruktoren und benennt Felder um, die ML Kit per Reflection
# nutzt: Komponenten-Suche (ComponentRegistrar), eingebettete Protobuf-Nachrichten, JNI.
# Folge war ein Absturz beim Öffnen des Scanners. Deshalb die Bibliotheken unverändert lassen.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_** { *; }
-keep class com.google.android.libraries.barhopper.** { *; }
-keep class com.google.android.libraries.vision.** { *; }
-keep class com.google.android.odml.** { *; }
-keep class com.google.firebase.components.** { *; }
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); *; }
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.internal.mlkit_**
