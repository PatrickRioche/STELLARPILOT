# Spécification — séquence initiale StellarPilot

## Objectif

Définir la séquence utilisateur cible qui sera implémentée seulement après validation du POC de faisabilité.

## 1. Démarrage du serveur

Le Raspberry Pi démarre StellarPilot Server et initialise :
- le service local ;
- la communication avec INDI ;
- la détection de la monture ;
- la détection de la caméra ;
- l'API utilisée par Android.

## 2. Connexion Android

L'utilisateur ouvre StellarPilot sur Android et se connecte au serveur local du Raspberry Pi.

L'application affiche au minimum :
- serveur connecté ;
- caméra détectée ;
- monture détectée ;
- type AZ/EQ connu ou demandé à l'utilisateur.

## 3. Contexte local

L'application transmet au serveur :
- date ;
- heure locale ;
- fuseau horaire ;
- latitude ;
- longitude ;
- altitude si disponible et utile.

## 4. Orientation initiale

### Monture EQ

L'utilisateur oriente approximativement le tube vers la région du nord céleste / nord astronomique selon la procédure qui sera validée par les essais.

### Monture AZ

L'utilisateur oriente approximativement le tube vers le zénith selon la procédure qui sera validée par les essais.

## 5. Capture et astrométrie

StellarPilot :
1. capture une image ;
2. réalise un plate solving local ;
3. récupère la position réelle du centre du champ ;
4. récupère l'orientation et l'échelle ;
5. associe la solution au contexte heure/localisation/type de monture.

Résultat minimal :
- RA ;
- DEC ;
- orientation ;
- échelle angulaire ;
- statut de résolution.

Cette étape établit précisément où regarde le télescope. Elle ne doit pas être confondue avec une procédure complète d'alignement polaire d'une monture EQ.

## 6. Mise au point

StellarPilot propose une étoile brillante.

Après pointage :
- installation du masque de Bahtinov ;
- affichage de l'étoile ;
- réglage manuel ;
- validation ;
- retrait du masque.

## 7. Darks

StellarPilot demande de remettre le capuchon standard, acquiert les darks nécessaires, puis demande de retirer le capuchon.

## 8. Observation

Le système passe à l'état `READY_FOR_OBSERVATION`.

La chaîne d'observation pourra ensuite comprendre :
- choix d'une cible ;
- GoTo ;
- capture ;
- plate solving ;
- recentrage ;
- acquisition ;
- calibration ;
- empilement ;
- affichage progressif.
