# StellarPilot — POC Gate

## Question principale

Peut-on construire un système ouvert qui, depuis Android, commande un Raspberry Pi utilisant INDI pour une monture et une caméra, capture une image, la résout localement par astrométrie et renvoie la position réelle du champ au client ?

## Démonstration finale attendue

```text
[Android]
   |
   | connexion locale + heure + localisation
   v
[StellarPilot POC Server / Raspberry Pi ARM64]
   |
   v
[INDI]
   |-------------------|
   v                   v
[Monture AZ/EQ]      [Caméra]
                         |
                         v
                    [Image réelle]
                         |
                         v
                   [Plate solving]
                         |
                         v
              [RA / DEC / orientation]
                         |
                         v
                     [Android]
```

## Ce que ce Gate ne valide pas

Il ne valide pas encore :
- l'expérience utilisateur finale ;
- l'alignement complet AZ/EQ ;
- Bahtinov ;
- darks ;
- recentrage ;
- stacking ;
- focuser/autofocus.

Ces fonctions sont étudiées après démonstration de la faisabilité fondamentale.
