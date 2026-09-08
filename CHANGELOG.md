# Changelog

Ce fichier suit les changements des releases publiées.

## v0.7.0-beta — 2026-09-08

Première beta intégrée de StellarPilot après validation de la chaîne matérielle principale Android -> Raspberry Pi -> INDI -> OnStep/caméra.

### Monture / OnStep

- GOTO réel validé avec la monture `LX200 OnStep` ;
- conversion des coordonnées catalogue J2000 vers `EQUATORIAL_EOD_COORD` lorsque la monture publie des coordonnées époque-du-jour ;
- progression RA/DEC observée pendant les GOTO avec passage `Busy -> Ok` ;
- mouvement physique rapide et cohérent confirmé lors des essais du 8 septembre ;
- lecture des propriétés INDI critiques par noms exacts afin d’éviter les timeouts liés aux wildcards ;
- validation plus robuste de la monture connectée en conservant un cache puis en revalidant `CONNECTION.CONNECT` précisément.

### Temps / session

- synchronisation `TIME_UTC` explicite une fois par session depuis une source GPS/Android fiable ;
- vérification du readback OnStep après écriture ;
- reconnaissance du fait que `TIME_UTC` est publié par le driver comme un point de consigne statique et non comme une horloge qui s’incrémente seconde par seconde ;
- confiance de session conservée jusqu’à 12 h si le point de consigne et l’offset restent inchangés ;
- les GOTO ne réécrivent plus l’horloge OnStep avant chaque mouvement.

### Android / préparation

- maintien du workflow `Connexion -> Moteurs -> Astrométrie -> Étoile -> Bahtinov -> Prêt` ;
- une version produit commune est utilisée pour les variantes `device` et `simulation` ;
- `BACKEND_MODE` distingue le backend sans modifier le numéro de version ;
- passage de la version produit à `0.7.0-beta` avec `versionCode = 70`.

### Caméra / astrométrie

- conservation des acquis v0.5/v0.6 : capture FITS réelle Player One Uranus-C, aperçu JPEG couleur et plate solving local via astrometry.net ;
- référentiel optique empirique conservé : environ 1,2183 arcsec/pixel et champ d’environ 1,305° × 0,738°.

### DevOps / release

- GitHub reste l’unique source de vérité ;
- le Raspberry Pi reste une cible d’exécution/déploiement sans Git ;
- correction du workflow de release pour utiliser automatiquement les notes correspondant au tag ;
- validation automatique de la cohérence `VERSION` <-> tag ;
- artefacts de release versionnés ;
- génération d’un fichier SHA-256 pour les APK, l’archive serveur et OpenAPI.

### Validation du 8 septembre 2026

- connexion série OnStep via INDI confirmée ;
- synchronisation horaire de session validée ;
- coordonnées Vega et Arcturus correctement transformées vers l’époque du jour ;
- GOTO Arcturus -> Vega observé côté INDI jusqu’à la cible ;
- mouvement mécanique de la monture ensuite confirmé comme rapide et cohérent sur les essais suivants ;
- les nuages ont empêché de terminer la validation optique sous ciel réel.

### Limites connues

- validation Bahtinov complète à refaire sous ciel dégagé ;
- validation finale du centrage/plate solving après GOTO à poursuivre sous ciel réel ;
- `0.7.0-beta` reste une pré-release et ne constitue pas encore le franchissement du Gate G0.

## v0.6.0-poc — 2026-09-03

Jalon de préparation des essais réels de motorisation équatoriale et de calibration Bahtinov.

### Motorisation / OnStep

- nouvel écran de diagnostic de la monture dans l'assistant de préparation ;
- lecture réelle de `/mount/status` avec RA, DEC, état INDI, tracking et cible ;
- petits GOTO de test RA/DEC à partir de la position réellement publiée par OnStep ;
- pointage de l'étoile de mise au point via `/mount/goto` ;
- activation du suivi sidéral lors du GOTO ;
- avertissement spécifique lorsque DEC reste exactement à ±90° afin d'éviter de réutiliser aveuglément un hint astrométrique figé.

### Première astrométrie

- pose de départ portée à 4 s dans le nouveau workflow de test ;
- affichage du référentiel optique mesuré le 2 septembre 2026 ;
- échelle empirique de référence : environ 1,2183 arcsec/pixel ;
- champ associé : environ 1,305° × 0,738° ;
- documentation wiki du calcul de l'échantillonnage et de la focale effective.

### Bahtinov

- nouvel écran de collecte des références de mise au point ;
- choix d'une étoile brillante puis GOTO et tracking ;
- poses Bahtinov rapides sélectionnables ;
- étiquettes manuelles : très mauvais, mauvais, moyen, bon, optimum, mauvais de l'autre côté et ignorer ;
- chaque étiquette déclenche une nouvelle capture FITS via le pipeline existant ;
- journal JSONL Android reliant l'étiquette, la cible, l'exposition et le chemin du FITS archivé sur le Raspberry Pi ;
- préparation du futur calcul automatique d'erreur à partir des trois aigrettes.

### Workflow

Le flux V0.6 de test devient :

`Connexion -> Moteurs -> Astrométrie -> Étoile -> Bahtinov -> Prêt`.

Les darks sont volontairement différés. Le centrage doit devenir une fonction interne automatique associée aux GOTO plutôt qu'un écran utilisateur indépendant.

## v0.5.0-poc — 2026-08-24

Jalon POC validant la capture caméra réelle, l'aperçu couleur et l'intégration de l'astrométrie dans l'application Android.

### Serveur

- capture réelle via INDI avec caméra Player One Uranus-C ;
- FITS RAW16 3856 × 2180 avec matrice Bayer RGGB ;
- intégration réelle d'astrometry.net et solve-field ;
- génération d'un aperçu JPEG couleur via `/camera/preview.jpg` ;
- conservation du FITS brut comme source pour l'astrométrie.

### Android

- connexion serveur renforcée ;
- device sur `http://10.42.0.1:8000/` ;
- simulation prévue sur `http://10.42.0.1:8008/` ;
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

Voir `docs/05-releases/v0.5.0-poc.md`.

## v0.1.0-poc — 2026-08-17

Premier jalon Android <-> Raspberry Pi du POC StellarPilot.

### Serveur

- serveur FastAPI exposant notamment `/status`, `/devices`, `/system/location`, `/system/mount-type`, `/camera/capture`, `/mount/goto`, `/solve` et `/ws` ;
- CI Python avec `pytest` ;
- export du contrat OpenAPI ;
- documentation d'installation sur Raspberry Pi 5 ;
- documentation d'exploitation comme service `systemd`.

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

Cette release ne validait pas encore la chaîne complète INDI réelle, capture réelle et plate solving réel.
