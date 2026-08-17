# Changelog

Ce fichier suit les changements des releases publiées.

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
