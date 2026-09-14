# StellarPilot

StellarPilot est un projet libre visant à fournir une couche de pilotage intelligente et indépendante des constructeurs pour l’astronomie amateur, au-dessus d’INDI.

Le POC associe actuellement :

- un serveur Python/FastAPI destiné au Raspberry Pi 5 / Linux ARM64 ;
- une API REST et un canal WebSocket ;
- une abstraction INDI et un plate solver encore simulés dans le squelette serveur du dépôt ;
- une application Android native Kotlin + Jetpack Compose ;
- une CI GitHub Actions produisant les APK de simulation et de test sur appareil réel.

## Architecture POC

```text
[StellarPilot Android]
        |
        | REST + WebSocket / Wi-Fi LAN
        v
[StellarPilot Server / Raspberry Pi 5]
        |
        +--> INDI --> monture / caméra
        |
        +--> plate solving local
```

L’application Android ne dialogue pas directement avec INDI : le serveur StellarPilot constitue la frontière matérielle et le contrat réseau.

## État validé au 17 août 2026

Le premier chemin Android -> Raspberry Pi a été validé sur matériel réel :

- Raspberry Pi 5 joignable sur le réseau StellarPilot ;
- `GET /status` retourne HTTP 200 ;
- `/ws` accepte la connexion WebSocket ;
- l’APK `deviceDebug` installé sur une tablette Android se connecte au serveur ;
- les workflows `Server POC CI` et `Android APK Debug` construisent/testent les composants du POC.

Cette validation ne constitue pas encore le Gate final de faisabilité : la chaîne INDI réelle, capture réelle et plate solving réel reste à valider.

## Démarrer le serveur depuis le dépôt

```bash
cd server
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
PYTHONPATH=. pytest -q
./run.sh
```

Le serveur écoute sur le port `8000`. FastAPI expose sa documentation sur :

```text
http://<ip-du-pi>:8000/docs
```

Test minimal :

```bash
curl http://127.0.0.1:8000/status
```

## Application Android

Le module Android définit deux variantes de développement :

- `simulationDebug` : backend de simulation via l’émulateur Android ;
- `deviceDebug` : backend Raspberry Pi sur le LAN du POC.

Build local :

```bash
cd android
gradle --no-daemon :app:assembleSimulationDebug
gradle --no-daemon :app:assembleDeviceDebug
```

GitHub Actions publie également les artifacts :

- `StellarPilot-simulation-debug` ;
- `StellarPilot-device-debug`.

## Documentation POC

- `docs/01-architecture/poc-android-server.md` : architecture Android / serveur ;
- `docs/07-testing/android-pi5-e2e.md` : procédure de test tablette -> Pi 5 ;
- `docs/00-project/devops-poc.md` : exploitation du serveur et service systemd ;
- `docs/05-releases/v0.1.0-poc.md` : notes du premier jalon POC.

## Releases

La source de vérité de version est `VERSION`.

Les tags `v*` déclenchent le workflow de release qui teste le serveur, construit les deux APK, exporte le contrat OpenAPI, empaquette le serveur et crée une GitHub Release.

## Licence

GPL-3.0-or-later.
