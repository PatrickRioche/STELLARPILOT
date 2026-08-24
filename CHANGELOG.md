# Changelog

Ce fichier suit les changements des releases publiées.

## v0.5.0-poc — 2026-08-24

Jalon POC validant la capture caméra réelle, l'aperçu couleur et l'intégration de l'astrométrie dans l'application Android.

### Serveur

- capture réelle via INDI avec caméra Player One Uranus-C ;
- FITS RAW16 3856 × 2180 avec matrice Bayer RGGB ;
- intégration réelle d'astrometry.net et solve-field ;
- génération d'un aperçu JPEG couleur via /camera/preview.jpg ;
- conservation du FITS brut comme source pour l'astrométrie.

### Android

- connexion serveur renforcée ;
- device sur http://10.42.0.1:8000/ ;
- simulation prévue sur http://10.42.0.1:8008/ ;
- exposition réglable de 1 ms à 10 s ;
- affichage des résultats astrométriques ;
- préparation du support des étoiles détectées.

### Build et validation

- sorties Gradle déplacées hors de Google Drive ;
- build deviceDebug validé ;
- installation ADB validée ;
- capture réelle validée ;
- aperçu couleur RGGB validé.

### Limites connues

- plate solving réel de nuit encore à valider ;
- timeout attendu lors des essais de jour sans étoiles ;
- positions individuelles des étoiles non encore retournées par le serveur ;
- balance des blancs de l'aperçu encore perfectible.

Voir docs/05-releases/v0.5.0-poc.md.

## v0.1.0-poc — 2026-08-17

Premier jalon Android ↔ Raspberry Pi du POC StellarPilot.

### Serveur

- serveur FastAPI exposant notamment `/status`, `/devices`, `/system/location`, `/system/mount-type`, `/camera/capture`, `/mount/goto`, `/solve` et `/ws` ;
- CI Python avec `pytest` ;
- export du contrat OpenAPI ;
- documentation d’installation sur Raspberry Pi 5 ;
- documentation d’exploitation comme service `systemd`.

### Android

- client natif Kotlin + Jetpack Compose ;
- séparation UI / ViewModel / client API / modèles ;
- REST `/status` et WebSocket `/ws` ;
- variantes `simulationDebug` et `deviceDebug` ;
- sécurité réseau distincte entre debug et release ;
- CI Android produisant les deux APK debug ;
- Java/Kotlin alignés sur JVM 17 ;
- configuration Gradle adaptée à la mémoire des runners CI.

### Validation réelle

Le 17 août 2026, un APK `deviceDebug` installé sur une tablette Android a communiqué avec `stellarpilot-server` exécuté sur Raspberry Pi 5 :

- `GET /status` -> HTTP 200 ;
- WebSocket `/ws` -> connexion acceptée ;
- fonctionnement sur le réseau Wi-Fi/hotspot du Raspberry Pi.

### Limites du jalon

Cette release ne valide pas encore la chaîne complète INDI réelle, capture réelle et plate solving réel.
