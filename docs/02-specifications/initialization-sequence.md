# Spécification — séquence initiale StellarPilot

## Objectif

Définir la séquence utilisateur cible de préparation du setup avant observation.

## 1. Démarrage du serveur

Le Raspberry Pi démarre StellarPilot Server et initialise :
- le service local ;
- la communication avec INDI ;
- la détection de la monture ;
- la détection de la caméra ;
- l'API utilisée par Android.

Le Raspberry Pi est une cible de distribution : il ne contient pas de dépôt Git. Les mises à jour sont installées à partir du kit de distribution produit sur le poste de développement.

## 2. Connexion Android

L'utilisateur ouvre StellarPilot sur Android et se connecte au serveur local du Raspberry Pi.

L'application affiche au minimum :
- serveur connecté ;
- caméra détectée ;
- monture détectée ;
- type AZ/EQ connu ou demandé à l'utilisateur.

## 3. Contexte local

L'application transmet ou vérifie avec le serveur :
- date ;
- heure locale ;
- fuseau horaire ;
- latitude ;
- longitude ;
- altitude si disponible et utile.

## 4. Positionnement libre de la monture

StellarPilot n'impose plus de direction initiale telle que nord céleste, zénith, N, NE, E, etc.

L'utilisateur peut positionner la monture avec :
- OnStep ;
- MLAstro Hub ou une application mobile équivalente ;
- une autre console de commande compatible.

StellarPilot lit ensuite la position équatoriale courante publiée par INDI. Pour une monture télescope INDI, RA est lue en heures et convertie en degrés pour astrometry.net ; DEC est lue en degrés.

Ces coordonnées ne sont qu'un **hint facultatif** destiné à accélérer la résolution. Elles ne constituent jamais la vérité de référence sur le champ photographié.

## 5. Capture et astrométrie

StellarPilot :
1. vérifie la position AD/DEC disponible via INDI ;
2. capture une image ;
3. tente une résolution locale avec une fenêtre d'échelle resserrée autour du profil optique ;
4. si le hint de monture échoue, recommence sans position imposée ;
5. élargit la fenêtre d'échelle si nécessaire ;
6. récupère la position réelle du centre du champ à partir du WCS ;
7. synchronise OnStep avec la solution lorsque le workflow le demande ;
8. archive la capture et le résultat de chaque tentative pour diagnostic.

Pour le setup Uranus-C testé le 5 septembre 2026, l'échelle moyenne mesurée est proche de **1,2172 arcsec/pixel**. La valeur de profil retenue est **1,218 arcsec/pixel**.

Résultat utilisateur minimal :
- RA résolue ;
- DEC résolue ;
- échelle angulaire ;
- statut de résolution ;
- statut de synchronisation OnStep.

L'orientation WCS peut rester enregistrée dans les données techniques, mais elle n'est pas demandée à l'utilisateur et n'est pas nécessaire à la validation de cette étape.

Cette étape établit précisément où regarde le télescope. Elle ne doit pas être confondue avec une procédure complète d'alignement polaire d'une monture EQ.

## 6. Affichage de l'image

Sur tablette en orientation portrait, l'aperçu utilise toute la largeur disponible tout en respectant le rapport du capteur. L'utilisateur peut :
- zoomer avec deux doigts jusqu'à 8× ;
- déplacer l'image lorsqu'elle est zoomée ;
- conserver le réticule au centre de l'écran indépendamment du déplacement de l'image.

## 7. Mise au point

StellarPilot propose une étoile brillante.

Après pointage :
- centrage de l'étoile ;
- installation du masque de Bahtinov ;
- affichage de l'étoile ;
- réglage manuel ;
- validation ;
- retrait du masque.

## 8. Darks

StellarPilot demande de remettre le capuchon standard, acquiert les darks nécessaires, puis demande de retirer le capuchon.

## 9. Observation

Le système passe à l'état `READY_FOR_OBSERVATION`.

La chaîne d'observation peut ensuite comprendre :
- choix d'une cible ;
- GoTo ;
- capture ;
- plate solving ;
- recentrage ;
- acquisition ;
- calibration ;
- empilement ;
- affichage progressif.
