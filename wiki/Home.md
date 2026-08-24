# StellarPilot Wiki

Bienvenue dans la documentation d’installation, d’exploitation et de développement de **StellarPilot**.

StellarPilot est un projet libre d’astronomie amateur visant à fournir une couche de pilotage intelligente, locale et indépendante des constructeurs au-dessus d’INDI.

Le POC associe actuellement :

- un serveur Python / FastAPI sur Raspberry Pi 5 / Astroberry ;
- INDI pour l’accès aux montures et caméras ;
- astrometry.net pour le plate solving local ;
- gpsd pour la position GPS ;
- une API REST et WebSocket ;
- une application Android native Kotlin + Jetpack Compose sur tablette.

## Parcours recommandé

1. [Présentation du projet](Project-Overview.md)
2. [Méthode de développement](Development-Methodology.md)
3. [Architecture](Architecture.md)
4. [Installation serveur sur Raspberry Pi / Astroberry](Server-Installation-Astroberry.md)
5. [Configuration du GPS](GPS-Setup.md)
6. [Installation sur tablette Android](Android-Tablet-Installation.md)
7. [Exploitation et diagnostic](Operations-and-Troubleshooting.md)
8. [Licence et contributions](License-and-Contributing.md)

## Version de référence

Cette première version du Wiki est alignée sur **StellarPilot v0.5.0-poc**.

Ce jalon valide notamment la connexion Android ↔ Raspberry Pi, la récupération après perte de connexion, la capture caméra réelle via INDI, les FITS RAW16, l’aperçu couleur RGGB et l’intégration de `solve-field` d’astrometry.net.

## Réseau de référence

Sur le hotspot StellarPilot :

- serveur réel : `http://10.42.0.1:8000/` ;
- serveur de simulation futur : `http://10.42.0.1:8008/`.

Sur le LAN utilisé pendant le développement, le Raspberry Pi est également accessible à `192.168.1.46`.

## Philosophie

StellarPilot applique : **Specification first**, **Feasibility first**, **Vendor neutral**, **INDI first**, **Local first**, **Evidence based**, **Rollback safe** et **One release version**.

## Licence

StellarPilot est distribué sous licence **GNU GPL v3 ou ultérieure (GPL-3.0-or-later)**.
