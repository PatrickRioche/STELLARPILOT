# StellarPilot

StellarPilot est un projet libre visant à fournir une couche de pilotage intelligente et indépendante des constructeurs pour l’astronomie amateur, au-dessus d’INDI.

La beta actuelle associe :

- un serveur Python/FastAPI pour Raspberry Pi 5 / Linux ARM64 ;
- une API REST et un canal WebSocket ;
- une intégration INDI réelle pour la monture et la caméra ;
- une capture FITS réelle avec aperçu JPEG ;
- une astrométrie locale réelle via astrometry.net ;
- une application Android native Kotlin + Jetpack Compose ;
- une CI GitHub Actions pour les tests serveur, les APK et les releases versionnées.

## Architecture

```text
[StellarPilot Android]
        |
        | REST + WebSocket / Wi-Fi LAN
        v
[StellarPilot Server / Raspberry Pi 5]
        |
        +--> INDI --> monture OnStep / caméra
        |
        +--> astrometry.net / solve-field
```

L’application Android ne dialogue pas directement avec INDI : le serveur StellarPilot constitue la frontière matérielle et le contrat réseau.

## État validé au 8 septembre 2026

La chaîne matérielle principale a été validée sur équipement réel :

- tablette Android connectée au serveur Raspberry Pi ;
- caméra Player One Uranus-C utilisée en capture FITS réelle ;
- aperçu JPEG couleur à partir des FITS ;
- résolution astrométrique locale avec astrometry.net ;
- monture `LX200 OnStep` connectée via INDI ;
- synchronisation `TIME_UTC` explicite une fois par session avec vérification du readback ;
- GOTO sans réécriture de l’horloge OnStep avant chaque mouvement ;
- conversion des cibles catalogue J2000 vers le référentiel de la monture lorsqu’elle publie `EQUATORIAL_EOD_COORD` ;
- GOTO OnStep observé en `Busy -> Ok` avec progression réelle RA/DEC ;
- mouvement physique rapide et cohérent de la monture confirmé lors des essais du 8 septembre.

Les requêtes INDI sensibles utilisent désormais des propriétés exactes sur les chemins critiques afin d’éviter les blocages connus de `indi_getprop` avec les wildcards.

La version produit courante est définie par le fichier `VERSION`. L’identifiant OpenAPI peut rester temporairement compatible avec le jalon API précédent pendant la beta ; il ne remplace pas la version produit.

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

- `simulationDebug` : backend de simulation ;
- `deviceDebug` : backend Raspberry Pi sur le LAN du banc de test.

Les deux variantes portent la même version produit. Le mode de backend est exposé séparément par `BACKEND_MODE`.

Build local :

```bash
cd android
gradle --no-daemon :app:assembleSimulationDebug
gradle --no-daemon :app:assembleDeviceDebug
```

## Déploiement Raspberry Pi

GitHub est la source de vérité. Le Raspberry Pi est une cible d’exécution et de déploiement uniquement : aucun workflow Git n’est utilisé sur le Pi.

Le déploiement serveur se fait depuis le PC avec `tools/deploy-server.ps1`. Les données scientifiques et le `.venv` du Pi sont préservés lors des mises à jour normales.

## Releases

La source de vérité de version est `VERSION`.

Un tag `v*` déclenche le workflow de release qui :

- vérifie que le tag correspond exactement à `VERSION` ;
- exécute les tests serveur ;
- construit les APK simulation et device ;
- exporte OpenAPI ;
- empaquette le serveur ;
- génère les SHA-256 ;
- publie les notes `docs/05-releases/<tag>.md` et les artefacts versionnés.

Les versions `-beta` restent des pré-releases techniques tant que la validation complète sous ciel réel et le Gate G0 ne sont pas terminés.

## Documentation

- `docs/00-project/devops-poc.md` : exploitation et règles de déploiement ;
- `docs/01-architecture/poc-android-server.md` : architecture Android / serveur ;
- `docs/05-releases/` : politique de version et notes de release ;
- `docs/07-testing/android-pi5-e2e.md` : tests tablette -> Pi 5.

## Licence

GPL-3.0-or-later.
