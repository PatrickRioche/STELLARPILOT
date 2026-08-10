# Spécification — abstraction matérielle

Objectif : définir des capacités communes plutôt qu'une liste de marques.

## Monture
Capacités à étudier : type AZ/EQ, slew, stop, sync, park, tracking, coordonnées,
limites et état.

## Caméra
Capacités à étudier : exposition, gain, binning, ROI, température, format image,
flux de capture et annulation.

## Focuser
Capacités à étudier : position absolue/relative, déplacement, limites,
température éventuelle et arrêt.

## Critère clé
Une fonctionnalité StellarPilot ne doit pas dépendre d'une API constructeur
lorsqu'une capacité INDI équivalente est disponible et suffisamment fiable.
