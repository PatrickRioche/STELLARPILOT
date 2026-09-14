# Perspective architecturale — Portabilité Windows 11 et iOS

**Projet :** STELLARPILOT  
**Date :** 30 août 2026  
**Statut :** Perspective archivée — aucune implémentation engagée

## 1. Objet

Ce document conserve les pistes d'évolution de StellarPilot vers :

- un serveur StellarPilot exécutable sous Windows 11 ;
- une application cliente StellarPilot disponible sur iOS ;
- une architecture multiplateforme conservant le Raspberry Pi / Linux et Android comme base actuelle.

Ces évolutions ne sont pas prioritaires à ce stade. La priorité reste la stabilisation de l'architecture actuelle :

```text
Android ↔ Raspberry Pi 5 / Linux ↔ INDI ↔ matériel réel
```

Le mode Démonstration reste entièrement local dans l'application mobile et indépendant du serveur.

## 2. Portabilité de StellarPilot Server vers Windows 11

### 2.1. Éléments déjà portables

La majeure partie du cœur serveur repose sur des technologies multiplateformes :

- Python ;
- FastAPI ;
- Uvicorn ;
- Pydantic ;
- NumPy ;
- Astropy ;
- Pillow ;
- SciPy ;
- API REST ;
- WebSocket ;
- JSON ;
- logique de session ;
- catalogue et calculs astronomiques.

Le serveur a déjà été testé sous Windows dans son environnement de développement Python.

### 2.2. Éléments actuellement liés à Linux

La couche matérielle reste fortement dépendante de Linux :

- INDI ;
- `indi_getprop` ;
- `indi_setprop` ;
- gpsd ;
- systemd ;
- scripts Bash ;
- périphériques de type `/dev/ttyACM0` ;
- intégration actuelle d'astrometry.net.

### 2.3. Première possibilité : Windows 11 + WSL2

Une solution transitoire pourrait consister à exécuter la partie Linux dans WSL2 :

```text
Windows 11
   |
   +-- WSL2 / Linux
          |
          +-- INDI
          +-- astrometry.net
          +-- Python / StellarPilot
```

Cette solution permettrait de conserver une grande partie du logiciel existant, mais la gestion USB et des périphériques astronomiques pourrait être moins simple qu'une intégration native.

### 2.4. Perspective recommandée : abstraction du backend matériel

À terme, StellarPilot pourrait séparer le cœur serveur de la couche d'accès au matériel :

```text
                 StellarPilot Core
                       |
          +------------+------------+
          |                         |
     LinuxBackend              WindowsBackend
          |                         |
        INDI                 ASCOM / Alpaca
```

Architecture cible possible :

```text
Raspberry Pi / Linux → INDI
Windows 11           → ASCOM / Alpaca
```

Le contrat REST/WebSocket resterait identique pour les applications clientes.

## 3. Portabilité de StellarPilot App vers iOS

### 3.1. Situation actuelle

L'application est actuellement native Android :

- Kotlin ;
- Jetpack Compose ;
- Android ViewModel ;
- ressources Android ;
- BitmapFactory ;
- APK.

Une application iOS native repose normalement sur :

- Swift ;
- SwiftUI ;
- cycle de vie iOS ;
- ressources iOS ;
- IPA.

L'application Android actuelle ne peut donc pas être simplement recompilée pour iOS.

### 3.2. Point favorable : contrat réseau déjà indépendant de la plateforme

L'application communique avec StellarPilot Server principalement via :

- REST ;
- WebSocket ;
- JSON ;
- JPEG / images.

Le serveur peut ainsi rester identique quel que soit le client :

```text
                  StellarPilot Server
                    REST / WebSocket
                          |
             +------------+------------+
             |                         |
        Android App                  iOS App
     Kotlin / Compose              SwiftUI
```

Les endpoints serveur resteraient partagés, notamment :

```text
/health
/status
/camera/capture
/camera/preview.jpg
/solve
/system/location
/mount/goto
/sky/objects
/catalog
```

### 3.3. Option recommandée : Kotlin Multiplatform

Une évolution progressive vers Kotlin Multiplatform permettrait de partager une partie importante de la logique :

```text
StellarPilot Shared
├── modèles
├── API REST
├── WebSocket
├── catalogue
├── filtres
├── logique GOTO
├── gestion session
└── logique Demo

Android
└── UI Compose

iOS
└── UI SwiftUI
```

Le mode Démonstration pourrait également utiliser une base de données locale commune ou équivalente sur les deux plateformes.

## 4. Architecture multiplateforme cible possible

```text
                   STELLARPILOT

           ┌──── Raspberry Pi / Linux
           │       └── INDI
Server ────┤
           │
           └──── Windows 11
                   └── ASCOM / Alpaca

                     ↕
              REST + WebSocket
                     ↕

           ┌──── Android
Clients ───┤
           └──── iOS

Mode Demo Android/iOS :
100 % local, aucun serveur
```

## 5. Ordre d'évolution conseillé

### Étape 1 — Stabiliser l'architecture actuelle

Conserver comme référence :

```text
Raspberry Pi 5 / Linux + INDI ↔ Android
```

Valider complètement :

- capture réelle ;
- affichage de l'image ;
- astrométrie réelle ;
- GOTO réel ;
- gestion temps / position ;
- reconnexion ;
- robustesse réseau.

### Étape 2 — Abstraire davantage la couche matériel serveur

Créer des interfaces internes indépendantes d'INDI afin de pouvoir brancher ultérieurement plusieurs backends.

### Étape 3 — Étudier Windows 11

Ajouter éventuellement un backend :

```text
ASCOM / Alpaca
```

sans modifier le contrat réseau StellarPilot.

### Étape 4 — Extraire la logique mobile partageable

Étudier Kotlin Multiplatform pour :

- modèles ;
- API ;
- WebSocket ;
- catalogue ;
- logique métier ;
- mode Démonstration.

### Étape 5 — Créer le client iOS

Créer une interface native SwiftUI utilisant la logique partagée et le même contrat serveur.

## 6. Décision actuelle

Aucune migration Windows ou iOS n'est engagée pour le moment.

La perspective est conservée pour une évolution future après stabilisation de StellarPilot sur :

```text
Raspberry Pi 5 / Linux + INDI + Android
```

Le principe directeur à conserver est :

> un cœur serveur et un contrat réseau indépendants des interfaces clientes et, à terme, indépendants du backend matériel.
