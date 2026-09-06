# Compte rendu final — soirée d'observation du 5 au 6 septembre 2026

## Objet

Cette session avait pour but de valider en conditions réelles plusieurs éléments du POC StellarPilot :

- acquisition avec la caméra Player One Uranus-C ;
- comportement de la monture OnStep via INDI ;
- première astrométrie ;
- pertinence des coordonnées de monture AD/DEC comme hint ;
- robustesse du solveur ;
- qualité des previews ;
- comportement de la chaîne sur tablette ;
- préparation de la mise au point manuelle.

Les données ont été archivées sous :

`/home/astroberry/stellarpilot-server/data/astrometry/assistant-3/2026/2026-09-05/`

## Jeu de données

Le dossier contient 46 acquisitions de 4 s :

- 13 acquisitions réalisées plus tôt le 5 septembre ;
- 33 acquisitions correspondant à la session nocturne retenue pour l'analyse.

La séquence nocturne analysée va de 19:58:33 UTC à 21:42:33 UTC, soit environ 21:58 à 23:42 heure locale en France métropolitaine le 5 septembre 2026.

Chaque archive comprend au minimum :

- `initial.fits` ;
- `initial.jpg` ;
- `metadata.json` ;
- `solve.json` lorsqu'une résolution a été enregistrée pendant la session.

## Résultat du retest astrométrique

Les 33 FITS nocturnes ont été retestés sans modifier les originaux.

Résultats :

- captures nocturnes testées : **33** ;
- résolues : **25** ;
- non résolues : **8** ;
- taux réel de résolution : **75,8 %** ;
- échelle moyenne : **1,217212 arcsec/pixel** ;
- dispersion : **0,002095 arcsec/pixel** ;
- durée médiane des solutions réussies : **3,302 s**.

La valeur instrumentale d'environ **1,218 arcsec/pixel** est donc confirmée expérimentalement sur le ciel.

## Lecture des données INDI / OnStep

Les `solve.json` enregistrés en fin de session montrent que StellarPilot peut récupérer une position de monture via INDI, notamment à partir de :

`EQUATORIAL_EOD_COORD`

Exemple observé :

- source : `indi_mount_readback` ;
- monture : `LX200 OnStep` ;
- RA lue sur la monture ;
- DEC lue sur la monture ;
- utilisation de cette position comme hint astrométrique.

Cette observation valide le principe suivant : **la position de départ de la monture n'a pas besoin d'être imposée par StellarPilot**.

La monture peut être commandée avec :

- OnStep directement ;
- un mobile en Wi-Fi ;
- une autre console ;
- un autre client compatible.

StellarPilot lit ensuite l'état et les coordonnées disponibles via INDI. Le plate solving détermine la position réelle du champ observé.

## Chronologie de la session

### 21:58–22:07 — début de session très satisfaisant

Les huit premières captures nocturnes montrent des champs stellaires propres et exploitables.

Le retest résout les huit images.

Les temps de résolution sont généralement de l'ordre de 3 s et le nombre de sources détectées est élevé.

Conclusion :

- caméra fonctionnelle ;
- chaîne d'acquisition fonctionnelle ;
- ciel exploitable ;
- échelle instrumentale stable ;
- solveur très rapide lorsque l'image est correcte.

### 22:15–22:19 — phase dégradée

Plusieurs captures montrent :

- des étoiles fortement défocalisées ;
- des disques / donuts ;
- des étoiles allongées ou déplacées ;
- une qualité variant rapidement entre deux acquisitions.

Cette zone concentre une grande partie des timeouts du retest.

Causes probables :

- mise au point insuffisante ou modifiée ;
- capture effectuée pendant un mouvement ;
- stabilisation mécanique non terminée ;
- combinaison de plusieurs de ces facteurs.

Le système retrouve toutefois des images solvables au milieu de cette séquence, ce qui exclut une panne générale.

### 22:18–22:21 — reprise

Plusieurs images redeviennent exploitables et sont résolues.

Deux captures demandent davantage de temps au solveur, mais aboutissent.

### 23:17–23:29 — séquence stable

Les captures sont majoritairement classées `weak-signal`, mais les champs sont visuellement corrects et le retest les résout presque toutes.

Conclusion importante :

`weak-signal` ne doit pas être assimilé à `image inutilisable`.

Une image classée `weak-signal` peut rester parfaitement adaptée à l'astrométrie.

### 23:37–23:42 — deux anomalies puis retour à la normale

#### 23:37:03

Capture pratiquement uniforme :

- contraste : 0 ;
- seulement deux sources détectées au retest ;
- image non exploitable pour le plate solving.

#### 23:37:53

Capture fortement structurée avec un grand disque lumineux.

Elle ne correspond pas à un champ stellaire normal adapté au solveur. L'objet peut correspondre à une source étendue très lumineuse ou à une situation fortement défocalisée ; l'image seule ne permet pas d'en certifier la nature.

#### 23:39:27 et 23:42:33

Retour à deux champs stellaires exploitables et solutions réussies.

Le système était donc toujours opérationnel en fin de session.

## Bilan technique

### Validé

- acquisition FITS réelle avec l'Uranus-C ;
- previews JPEG exploitables ;
- fonctionnement de la caméra sur toute la session ;
- lecture de la monture OnStep via INDI ;
- récupération de coordonnées équatoriales utilisables comme hint ;
- plate solving local avec astrometry.net ;
- échelle instrumentale validée autour de 1,218 arcsec/pixel ;
- solutions typiques en environ 3 s ;
- capacité à résoudre des champs pourtant classés `weak-signal`.

### À corriger

- ne pas imposer une orientation initiale de la monture dans l'assistant ;
- ne pas demander Nord / Nord-Est / Est / etc. pour préparer la première astrométrie ;
- considérer la position INDI AD/DEC comme un hint facultatif ;
- conserver une résolution blind sans hint en repli ;
- éviter toute capture astrométrique pendant un slew actif ;
- améliorer la détection des images vides, défocalisées ou fortement filées ;
- agrandir l'aperçu sur tablette en mode portrait ;
- ajouter le zoom tactile à deux doigts et le déplacement de l'image zoomée.

## Décision de conception après la session

L'étape « Position initiale » doit disparaître du workflow utilisateur.

Nouveau principe :

1. connexion à StellarPilot ;
2. lecture de la monture via INDI ;
3. l'utilisateur positionne librement la monture avec l'outil de son choix ;
4. StellarPilot lit AD/DEC si disponibles ;
5. capture ;
6. plate solving ;
7. le champ réellement résolu devient la référence.

Il n'est donc plus nécessaire de proposer dans StellarPilot des boutons de direction du type N, NE, E, SE, S, SO, O, NO pour la première astrométrie.

## Stratégie astrométrique retenue

Pour le setup testé :

- échelle nominale : ~1,218 arcsec/pixel ;
- première fenêtre rapide recommandée : 0,90–1,50 arcsec/pixel ;
- hint AD/DEC INDI utilisé lorsqu'il est disponible ;
- fallback blind obligatoire si le hint ne permet pas de résoudre ;
- élargissement de la plage uniquement après échec de la stratégie rapide.

Le solveur ne doit jamais dépendre exclusivement de la qualité de la position annoncée par la monture.

## Interface tablette à mettre à jour

L'aperçu principal de la caméra doit être conçu pour une tablette utilisée verticalement :

- utilisation de toute la largeur disponible ;
- suppression de la hauteur fixe de 240 dp pour l'aperçu principal ;
- conservation du ratio du capteur ;
- pinch-to-zoom ;
- pan tactile lorsque l'image est zoomée ;
- réticule fixe au centre de l'écran ;
- retour automatique à une position centrée lorsque le zoom revient à 1×.

## Priorités de développement

### P0

- supprimer l'étape d'orientation initiale de l'assistant ;
- conserver et fiabiliser le hint AD/DEC INDI ;
- ajouter un fallback blind rapide autour de 1,218 arcsec/pixel ;
- bloquer ou différer une capture destinée au solve si la monture est encore en mouvement.

### P1

- aperçu caméra plein largeur en portrait ;
- pinch-to-zoom et pan ;
- classification des captures manifestement non exploitables ;
- journalisation systématique d'un résultat de solve pour chaque tentative.

### P2

- mise au point Bahtinov guidée ;
- darks ;
- amélioration des écrans de diagnostic.

## Conclusion

La soirée du 5 au 6 septembre 2026 valide l'essentiel de la chaîne instrumentale et astrométrique.

Le principal enseignement n'est pas une insuffisance du matériel mais une simplification nécessaire du workflow et une meilleure exploitation des informations déjà disponibles via INDI.

La direction retenue est donc : **moins d'orientation manuelle imposée par StellarPilot, davantage d'utilisation du retour réel OnStep/INDI et du plate solving, avec une interface tablette plus adaptée à l'observation sur le terrain.**