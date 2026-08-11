# StellarPilot — Architecture du POC

## Décision validée

- Raspberry Pi sous Linux 64 bits.
- Serveur StellarPilot en Python 3.
- API FastAPI.
- Communication Android ↔ serveur en HTTP/REST + WebSocket.
- Échanges structurés en JSON.
- Images scientifiques en FITS ; JPEG possible pour aperçu.
- Matériel astronomique piloté côté serveur via INDI.
- Plate solving via une abstraction compatible ASTAP ou astrometry.net.
- Client Android natif Kotlin + Jetpack Compose.
- Réseau local Wi‑Fi/LAN ; IP locale pour le POC, mDNS ensuite.
- Android ne communique jamais directement avec INDI.

## Chaîne fonctionnelle POC

Android → localisation/heure/type de monture → API StellarPilot → INDI → caméra → FITS → plate solving → RA/DEC/orientation → Android.

## Principe d'architecture

L'API StellarPilot constitue la frontière stable du produit. INDI, le solveur et les périphériques sont encapsulés derrière des services serveur afin de pouvoir évoluer sans modifier le protocole Android.
