# 🛒 Einkaufsliste

Simple, moderne Einkaufslisten-App für Android (Kotlin, Jetpack Compose, Material 3) mit Spracheingabe und Gemini-Anbindung – und einem **Vorrat** mit Mindesthaltbarkeitsdaten (früher die eigene App „MHD-Tracker“).

Unten gibt es zwei Reiter: **Einkaufsliste** und **Vorrat**.

## Einkaufsliste

- **Sprechen statt tippen** – Mikrofon antippen und z. B. „Zwei Liter Milch, Brot und sechs Eier“ sagen. Mengen, Einheiten und mehrere Produkte werden erkannt.
- **Listen einfügen** – Kopierten Text (z. B. aus WhatsApp) ins Eingabefeld einfügen oder ⋮ → *Aus Zwischenablage einfügen*: Jede Zeile wird ein eigener Eintrag.
- **Plus/Minus** – Hinter jedem Produkt die Anzahl mit − und + einstellen (bei g/ml in 50er/100er-, bei kg/l in 1er-Schritten).
- **Automatische Kategorien** – Produkte landen sortiert nach Supermarkt-Bereich (Obst & Gemüse, Milch & Käse, Getränke, …).
- **Aus Gemini übernehmen** – Gemini z. B. nach Zutaten für ein Rezept fragen, Antwort über *Teilen → Einkaufsliste* schicken oder Text markieren → *Zur Einkaufsliste*.
- **Gemini AppFunctions** – Die App stellt Gemini die Aktionen `addItems`, `getShoppingList`, `checkOffItem`, `removeItem` und `clearCheckedItems` bereit (Android 16+).
- **Schnellzugriff** – App-Symbol lange drücken → *Per Sprache hinzufügen*.
- Abhaken per Tippen, Bearbeiten per langem Tippen, Löschen per Wischen (mit Rückgängig), Liste teilen, Hell/Dunkel-Modus.
- Link für Automationen (z. B. Samsung Modi & Routinen): `einkaufsliste://add?text=Milch,Brot`

## Vorrat (MHD)

- **Scannen** – Barcode scannen, danach öffnet sich direkt die Kamera fürs Mindesthaltbarkeitsdatum. Beides läuft offline auf dem Handy (Google ML Kit). Ein Datum gilt erst, wenn es zweimal gleich gelesen wurde. Erkannt werden u. a. `12.03.2027`, `12.03.27`, `2027-03-12`, `12 03 27` und `03/2027` (→ Monatsende).
- **Produktname** von Open Food Facts; selbst vergebene Namen merkt sich die App pro Barcode.
- **Erinnerung** 1, 2 oder 3 Tage vor Ablauf zur eingestellten Uhrzeit (Standard 9:00). Eine Benachrichtigung listet alle fälligen Produkte und hat den Knopf *Auf die Einkaufsliste*.
- **Preise** von EDEKA, REWE, LIDL und ALDI aus Open Prices (von Nutzern gemeldet), Region Mülheim an der Ruhr oder ganz Deutschland.
- **Auf die Einkaufsliste** – Einkaufswagen-Symbol am Produkt. Ist das Produkt schon auf der Liste, ist das Symbol grün.
- Haken = verbraucht (mit Rückgängig), Suche ab 6 Produkten, Schnellwahl fürs Datum (+3 Tage … +3 Monate).
- Schnellzugriff: App-Symbol lange drücken → *MHD scannen*.

### Umzug aus der alten MHD-App

In der alten App *Einstellungen → Exportieren* tippen und die Sicherung über *Teilen* an **Zur Einkaufsliste** schicken. Die App erkennt die Sicherung und übernimmt alle Produkte in den Vorrat. Alternativ den Text kopieren und im Vorrat unter ⚙️ → *Sicherung → Aus Zwischenablage* importieren. Danach kann die alte App gelöscht werden.

Tipp für Samsung: In der App-Info unter *Akku* auf „Nicht eingeschränkt“ stellen, sonst kann Android Erinnerungen verzögern.

## Installieren

Jeder Push auf `main` baut über GitHub Actions ein signiertes APK und veröffentlicht es unter **Releases** (Pushes auf andere Branches werden nur gebaut und getestet). Auf dem Handy die neueste `Einkaufsliste-v1.0.X.apk` herunterladen und installieren. Neue Versionen installieren sich als Update über die alte.

## Gemini direkt

Damit Gemini die App ohne Umweg ansprechen kann („Setz Milch auf meine Einkaufsliste“), nutzt die App Android **AppFunctions**. Google gibt Gemini dafür derzeit nur für Apps im *Connected Apps Platform*-Entwicklerprogramm frei. Lokal testen lässt sich die Schnittstelle per ADB:

```bash
adb shell cmd app_function list-app-functions --package de.zeroxion.einkaufsliste
adb shell "cmd app_function execute-app-function --package de.zeroxion.einkaufsliste \
  --function 'de.zeroxion.einkaufsliste.appfunctions.BaseShoppingListAppFunctionService#addItems' \
  --parameters '{\"products\": [\"2 Liter Milch\", \"Brot\"]}'"
```

## Technik

Kotlin · Jetpack Compose · Material 3 · AndroidX AppFunctions · CameraX · ML Kit (Barcode, Texterkennung) · AGP 9.5 · compileSdk 37 · minSdk 26 · nur arm64

- `pantry/logic` – reine Logik ohne Android (Datumserkennung, Erinnerungsregeln, Preise), getestet mit `./gradlew testDebugUnitTest`
- `pantry/data` – Vorrat (`vorrat.json`), Einstellungen, Open Food Facts / Open Prices, Preis-Cache
- `pantry/reminders` – täglicher Wecker (`AlarmManager.setAndAllowWhileIdle`), Neustart-Empfänger, Benachrichtigung
- `pantry/ui` – Vorrat-Reiter, Editor, Scanner, Einstellungen
