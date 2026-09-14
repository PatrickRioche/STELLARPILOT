# Changelog

Ce fichier suit les changements des releases publiées.

## v0.6.1-poc — 2026-09-06

Livraison de consolidation après reprise du setup terrain validé avec OnStepX 10.28u.

### OnStep / monture

- ajout de `GET /mount/firmware` pour lire directement la propriété INDI `Firmware Info.Number` publiée par la monture ;
- affichage du firmware réel dans `Statut > Monture`, sans valeur codée en dur ;
- la version de référence observée sur le setup terrain est `10.28u` (`10.28U` dans l'affichage Android) ;
- conservation de la lecture de position réelle via `EQUATORIAL_EOD_COORD` ;
- aucun changement appliqué au calcul RA/DEC après validation terrain d'une position `idle`, `indi_state=Ok`, `virtual_position=false`.

### Cohérence App / Serveur

- correction du faux avertissement « versions différentes » ;
- l'égalité d'une livraison est désormais déterminée par le commit Git commun entre APK et serveur, et non par leurs horodatages de build ;
- comparaison compatible SHA court / SHA long ;
- les horodatages de build restent affichés à titre informatif.

### Version

- serveur : `0.6.1-poc` ;
- Android : `0.6.1`, `versionCode 7` ;
- branche de validation terrain conservée : `final/main-convergence`.

### Validation attendue

- CI serveur incluant le test de lecture du firmware OnStep ;
- CI Android simulationDebug + deviceDebug ;
- déploiement serveur exclusivement via le kit PC `tools/deploy-server.ps1` ;
- contrôle terrain de `/mount/firmware`, `/mount/status`, puis installation du nouvel APK.

## v0.6.0-poc — consolidation terrain 2026-09-06

Intégration des enseignements de la soirée d'observation du 5 au 6 septembre 2026 dans la branche de convergence finale.

### Astrométrie / OnStep

- suppression de l'orientation initiale imposée N/NE/E/... dans l'assistant final ;
- positionnement libre de la monture via OnStep, MLAstro Hub ou une autre console ;
- lecture AD/DEC via INDI comme hint facultatif, avec conversion RA heures → degrés ;
- la solution issue de l'image reste la référence ;
- stratégie robuste : hint + fenêtre étroite, blind solve étroit, puis blind solve élargi ;
- échelle de profil retenue : environ 1,218 arcsec/pixel, pour une moyenne mesurée de 1,217212 arcsec/pixel sur la session ;
- fenêtre étroite autour de 0,90–1,51 arcsec/pixel ;
- dernier fallback blind 0,50–2,50 arcsec/pixel ;
- tests serveur alignés sur cette stratégie.

### Interface tablette

- aperçu caméra plein largeur en conservant le ratio 3856 × 2180 de l'Uranus-C ;
- pinch-to-zoom jusqu'à 8× ;
- déplacement tactile de l'image zoomée ;
- réticule conservé au centre de l'écran ;
- retour au centrage lorsque le zoom revient à 1×.

### Documentation et exploitation

- ajout du compte rendu de la session du 5 au 6 septembre 2026 dans le wiki ;
- mise à jour de la séquence d'initialisation et du périmètre projet ;
- rappel du modèle de distribution : le Raspberry Pi ne contient pas de dépôt Git, les mises à jour passent par le kit produit depuis le poste de développement.

### Validation CI

- Android simulationDebug et deviceDebug validés après suppression de l'orientation initiale ;
- tests serveur et export OpenAPI validés après adaptation de la stratégie astrométrique.

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

`Connexion → Moteurs → Astrométrie → Étoile → Bahtinov → Prêt`.

Les darks sont volontairement différés. Le centrage doit devenir une fonction interne automatique associée aux GOTO plutôt qu'un écran utilisateur indépendant.

### Validation attendue

- compilation simulationDebug et deviceDebug via CI ;
- validation matérielle avec monture EQ et moteurs connectés ;
- validation de RA/DEC hors du pôle ;
- GOTO + tracking vers une étoile de focus ;
- constitution d'une série Bahtinov des deux côtés du foyer.

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
