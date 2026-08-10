# Interfaces à valider

## POC

### Android <-> Raspberry Pi
- découverte ou adresse du serveur ;
- connexion locale ;
- transmission heure/date/fuseau ;
- transmission localisation ;
- commande Capture & Solve ;
- retour de statut ;
- retour de la solution astrométrique.

### StellarPilot <-> INDI
- découverte caméra ;
- capture ;
- récupération de l'image ;
- découverte monture ;
- lecture état/coordonnées ;
- commande simple de monture.

### StellarPilot <-> plate solver
- image en entrée ;
- succès/échec ;
- RA ;
- DEC ;
- orientation ;
- échelle angulaire ;
- temps de résolution.

## Après le POC

Les interfaces de calibration, Bahtinov, recentrage, stacking et catalogue seront définies après validation de la faisabilité globale.
