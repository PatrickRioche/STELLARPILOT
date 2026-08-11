# Spécification — astrométrie / plate solving

## Priorité POC

L'astrométrie est l'une des fonctions critiques de faisabilité de StellarPilot.

## Entrée minimale

- image réellement capturée par la caméra via INDI.

Le contexte suivant peut être fourni au système mais le POC doit distinguer clairement ce qui est nécessaire au solveur de ce qui sert à l'initialisation astronomique globale :
- heure/date ;
- localisation ;
- type de monture AZ/EQ ;
- coordonnées approximatives si disponibles.

## Sortie minimale

- succès/échec ;
- RA ;
- DEC ;
- orientation ;
- échelle angulaire ;
- temps de résolution.

## Critères de faisabilité

Le POC doit déterminer :
- si le solve peut fonctionner localement sur ARM64 ;
- quelles dépendances et quels index sont requis ;
- le temps de résolution sur le Raspberry Pi cible ;
- la robustesse sur plusieurs images réelles ;
- le comportement en cas d'échec.

## Après le POC

La solution pourra être utilisée pour :
- établir le repère initial ;
- vérifier le pointage ;
- synchroniser la monture si la stratégie retenue le permet ;
- calculer une erreur de centrage ;
- boucler vers un recentrage automatique.
