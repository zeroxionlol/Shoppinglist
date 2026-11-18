# 🛒 Einkaufsliste

Simple, moderne Einkaufslisten-App für Android (Kotlin, Jetpack Compose, Material 3) mit Spracheingabe und Gemini-Anbindung.

## Funktionen

- **Sprechen statt tippen** – Mikrofon antippen und z. B. „Zwei Liter Milch, Brot und sechs Eier“ sagen. Mengen, Einheiten und mehrere Produkte werden erkannt.
- **Automatische Kategorien** – Produkte landen sortiert nach Supermarkt-Bereich (Obst & Gemüse, Milch & Käse, Getränke, …).
- **Aus Gemini übernehmen** – Gemini z. B. nach Zutaten für ein Rezept fragen, Antwort über *Teilen → Einkaufsliste* schicken oder Text markieren → *Zur Einkaufsliste*.
- **Gemini AppFunctions** – Die App stellt Gemini die Aktionen `addItems`, `getShoppingList`, `checkOffItem`, `removeItem` und `clearCheckedItems` bereit (Android 16+).
- **Schnellzugriff** – App-Symbol lange drücken → *Per Sprache hinzufügen*.
- Abhaken per Tippen, Bearbeiten per langem Tippen, Löschen per Wischen (mit Rückgängig), Liste teilen, Hell/Dunkel-Modus.
- Link für Automationen (z. B. Samsung Modi & Routinen): `einkaufsliste://add?text=Milch,Brot`

## Installieren

Jeder Push auf `main` baut über GitHub Actions ein signiertes APK und veröffentlicht es unter **Releases**. Auf dem Handy die neueste `Einkaufsliste-v1.0.X.apk` herunterladen und installieren. Neue Versionen installieren sich als Update über die alte.

## Gemini direkt

Damit Gemini die App ohne Umweg ansprechen kann („Setz Milch auf meine Einkaufsliste“), nutzt die App Android **AppFunctions**. Google gibt Gemini dafür derzeit nur für Apps im *Connected Apps Platform*-Entwicklerprogramm frei. Lokal testen lässt sich die Schnittstelle per ADB:

```bash
adb shell cmd app_function list-app-functions --package de.zeroxion.einkaufsliste
adb shell "cmd app_function execute-app-function --package de.zeroxion.einkaufsliste \
  --function 'de.zeroxion.einkaufsliste.appfunctions.BaseShoppingListAppFunctionService#addItems' \
  --parameters '{\"products\": [\"2 Liter Milch\", \"Brot\"]}'"
```

## Technik

Kotlin · Jetpack Compose · Material 3 · AndroidX AppFunctions · AGP 9.5 · compileSdk 37 · minSdk 26
