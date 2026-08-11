# Spécification — Android

## Rôle dans le POC

L'application Android est un client minimal permettant de valider le lien utilisateur -> Raspberry Pi -> INDI -> astrométrie.

## Fonctions POC

- se connecter au serveur StellarPilot sur le réseau local ;
- transmettre date/heure/fuseau ;
- transmettre la localisation ;
- afficher les périphériques détectés ;
- déclencher une opération `Capture & Solve` ;
- afficher le statut ;
- afficher la solution astrométrique minimale.

## Non requis pour le POC

- design définitif ;
- catalogue complet ;
- live stacking ;
- workflow Bahtinov complet ;
- bibliothèque de darks ;
- écran d'observation final.

## Principe

La logique astronomique centrale reste sur le Raspberry Pi. Android sert de client de contrôle et d'affichage.
