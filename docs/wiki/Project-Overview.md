# Présentation du projet StellarPilot

## Objectif

StellarPilot vise à construire une couche de pilotage astronomique intelligente qui reste indépendante des constructeurs.

L’application mobile ne dialogue pas directement avec chaque caméra ou monture. Le Raspberry Pi joue le rôle de passerelle matérielle et expose un contrat réseau stable à l’application.

```text
[ Tablette Android ]
        |
        | REST + WebSocket / Wi-Fi
        v
[ StellarPilot Server ]
[ Raspberry Pi / Astroberry ]
        |
        +----> INDI ----> monture / caméra
        +----> astrometry.net
        +----> gpsd ----> GPS USB
```

## Principes d’architecture

- **Vendor neutral** : le changement de constructeur ne doit pas remettre en cause l’architecture.
- **INDI first** : INDI est la couche matérielle de référence.
- **Local first** : les fonctions critiques restent locales au Raspberry Pi.
- **API boundary** : Android communique avec StellarPilot Server, jamais directement avec INDI.

## Application Android

L’application Android est native : Kotlin, Jetpack Compose, REST et WebSocket.

Deux variantes existent :

- `device` → `http://10.42.0.1:8000/` ;
- `simulation` → futur backend `http://10.42.0.1:8008/`.

## Serveur

Le serveur repose sur Python 3, FastAPI, Uvicorn, INDI, astrometry.net et gpsd.

Routes principales : `/health`, `/status`, `/devices`, `/system/location`, `/system/mount-type`, `/camera/capture`, `/camera/preview.jpg`, `/mount/goto`, `/solve`, `/ws`.

## Astrométrie

Le POC utilise `solve-field` et `wcsinfo` d’astrometry.net avec des index locaux. Le serveur retourne notamment RA, DEC, orientation et échelle angulaire.

## Caméra

Le jalon v0.5.0-poc a été validé avec une Player One Uranus-C : 3856 × 2180, RAW16, FITS, Bayer RGGB. Le FITS brut reste la source pour l’astrométrie ; un JPEG couleur séparé est produit pour Android.

## GPS

En mode `device`, StellarPilot interroge gpsd sur `127.0.0.1:2947`. Si le GPS fournit un fix, sa position est utilisée. Sinon, StellarPilot peut utiliser la position de session saisie manuellement.

## État v0.5.0-poc

Validé : communication Android ↔ Pi, reconnexion, INDI réel, capture réelle, FITS RAW16, aperçu couleur RGGB, astrometry.net intégré, GPS via gpsd, build et installation Android.

À poursuivre : plate solving de nuit, remontée détaillée des étoiles détectées, balance des blancs évoluée, backend de simulation, guidage et autofocus.
