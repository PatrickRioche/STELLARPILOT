# StellarPilot 0.6 — protocole de test terrain du 3 septembre 2026

## Objectif

Valider sur le matériel réel la chaîne minimale de préparation avant de poursuivre l'automatisation :

1. connexion tablette ↔ Raspberry Pi ↔ INDI ;
2. réponse réelle des axes RA et DEC ;
3. capture 4 s, plate solving et synchronisation OnStep ;
4. GOTO vers une étoile de mise au point ;
5. retour explicite en tracking sidéral ;
6. constitution du référentiel Bahtinov étiqueté.

Cette séance ne cherche pas encore à valider les darks ni le centrage automatique complet.

## Préconditions

- Raspberry Pi 5 démarré et serveur StellarPilot actif ;
- monture LX200 OnStep connectée à INDI ;
- caméra PlayerOne Uranus-C connectée ;
- tablette connectée au réseau StellarPilot ;
- heure Android/GPS cohérente ;
- OnStep sans `TIME_UTC Alert` ;
- aucun câble susceptible d'être tendu pendant un déplacement.

## 1. Connexion

Ouvrir **Préparation de l'observation → Assistant StellarPilot 0.6**.

La progression ne doit être autorisée que si la monture et la caméra sont disponibles.

À relever en cas d'anomalie :

- URL serveur ;
- état monture ;
- état caméra ;
- message de connexion Android.

## 2. Validation des moteurs EQ

L'écran moteur réalise de petits GOTO relatifs :

- RA+ : +0,03 h, soit environ +0,45° ;
- RA− : −0,03 h, soit environ −0,45° ;
- DEC+ : +0,30° ;
- DEC− : −0,30°.

Pour chaque commande, StellarPilot mémorise les coordonnées avant le mouvement, attend le retour OnStep puis compare la position finale.

Un axe est considéré **validé** lorsqu'un déplacement mesurable est constaté dans le bon sens. La progression vers l'astrométrie reste bloquée tant que RA et DEC n'ont pas chacun produit au moins un PASS.

### Arrêt immédiat si

- un axe part en mouvement continu ;
- le déplacement est manifestement beaucoup plus important que demandé ;
- un câble se tend ;
- la monture approche d'un obstacle ;
- DEC reste exactement à +90° ou −90° après les essais DEC ;
- l'application affiche FAIL de manière répétée alors que le moteur se déplace physiquement.

Dans ce dernier cas, conserver les logs : le problème peut venir de la remontée des coordonnées INDI et non du moteur lui-même.

## 3. Première astrométrie + SYNC

Référence initiale du setup Uranus-C :

- exposition : 4 s ;
- échelle de référence : environ 1,2183 arcsec/pixel.

Séquence attendue :

```text
capture 4 s
→ analyse qualité
→ astrometry.net
→ solution RA/DEC
→ conversion J2000/JNow selon la propriété INDI disponible
→ ON_COORD_SET.SYNC
→ coordonnées envoyées à OnStep
→ SYNC confirmé
```

L'étape n'est validée que si :

- `solveStatus = solved` ;
- `mountSyncStatus = synced`.

Un solve réussi sans SYNC ne doit pas permettre la suite.

## 4. Étoile de mise au point

Choisir une étoile proposée par StellarPilot.

Lancer **Pointer … et suivre**.

Résultat attendu :

- GOTO accepté ;
- déplacement de la monture ;
- fin du slew ;
- `/mount/status` retourne `tracking` ;
- tracking mode = `sidereal`.

Le passage au Bahtinov est volontairement bloqué tant que l'état `tracking` n'est pas confirmé.

## 5. Référentiel Bahtinov

Installer le masque puis tester plusieurs positions du focuser.

Expositions disponibles pour la séance :

- 100 ms ;
- 250 ms ;
- 500 ms ;
- 1 s ;
- 2 s.

Étiquettes disponibles :

- Très mauvais ;
- Mauvais ;
- Moyen ;
- Bon ;
- Optimum ;
- Mauvais autre côté ;
- Mauvaise capture / ignorer.

Pour obtenir un premier référentiel exploitable, viser si possible au moins :

- 2 captures Très mauvais ;
- 2 captures Mauvais ;
- 2 captures Moyen ;
- 3 captures Bon ;
- 3 captures Optimum ;
- 2 captures Mauvais autre côté.

Ne pas hésiter à marquer `ignorer` les captures perturbées par vibration, nuage, déplacement du masque ou mouvement involontaire.

Chaque entrée JSONL contient notamment :

- horodatage UTC ;
- chemin du FITS archivé sur le Pi ;
- exposition ;
- caméra ;
- étiquette ;
- étoile cible ;
- RA/DEC cible ;
- RA/DEC remontée par la monture ;
- tracking ;
- classification qualité ;
- score qualité ;
- nombre d'étoiles ;
- saturation ;
- facteur d'exposition recommandé.

## 6. Collecte après la séance

Depuis PowerShell à la racine du dépôt :

```powershell
.\tools\collect-field-test.ps1
```

Pour récupérer aussi les archives du Raspberry Pi :

```powershell
.\tools\collect-field-test.ps1 -PiHost "astroberry@10.42.0.1"
```

Le script crée un dossier horodaté dans :

```text
artifacts/field-tests/
```

avec :

- logs Android filtrés ;
- état ADB ;
- journal Bahtinov exporté ;
- archives astrométriques du Pi si `-PiHost` est fourni.

## Critères de réussite de la soirée

La chaîne V0.6 est considérée prête pour l'étape suivante si :

- RA : PASS ;
- DEC : PASS ;
- astrométrie : solved ;
- SYNC OnStep : synced ;
- GOTO étoile : terminé ;
- tracking sidéral : confirmé ;
- plusieurs références Bahtinov couvrent les deux côtés du foyer et l'optimum.

En cas d'échec, ne modifier qu'un sous-système à la fois lors de l'analyse : motorisation, coordonnées/référentiel, solver, tracking ou optique.
