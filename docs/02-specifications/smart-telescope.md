# Spécification — trajectoire "smart telescope"

## POC de faisabilité

```text
Android
  -> Raspberry Pi
  -> INDI
  -> monture + caméra
  -> capture
  -> plate solving
  -> RA / DEC / orientation
  -> retour Android
```

## Séquence cible après validation du POC

1. démarrer StellarPilot Server ;
2. connecter l'application Android ;
3. récupérer heure et localisation ;
4. identifier AZ/EQ ;
5. faire une capture d'initialisation ;
6. résoudre le champ ;
7. choisir une étoile brillante ;
8. mise au point manuelle au masque de Bahtinov ;
9. darks au capuchon ;
10. passer en mode observation ;
11. GoTo ;
12. solve et recentrage ;
13. acquisition ;
14. calibration ;
15. empilement temps réel ;
16. identification et présentation des objets.

Le support d'un focuser motorisé et de l'autofocus est une évolution ultérieure et ne fait pas partie du coeur initial.
