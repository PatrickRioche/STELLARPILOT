# Spécification — darks au capuchon

## Statut

Fonction active dans le POC StellarPilot.

## But

Créer des images de calibration sans accessoire mécanique supplémentaire et réutiliser automatiquement les Master Darks compatibles.

## Séquence cible

1. La mise au point Bahtinov est terminée.
2. StellarPilot demande à l'utilisateur de remettre le capuchon standard du télescope ou de la lunette.
3. StellarPilot acquiert une série de darks avec des paramètres compatibles avec la session d'observation.
4. Les darks sont enregistrés avec leurs métadonnées.
5. Un Master Dark est généré avec sa carte de pixels chauds.
6. StellarPilot demande de retirer le capuchon avant le début des observations.

## Métadonnées à conserver

- identifiant/modèle de caméra ;
- durée d'exposition ;
- gain ;
- offset ;
- binning si applicable ;
- résolution/ROI si applicable ;
- profondeur en bits ;
- matrice Bayer si applicable ;
- température si disponible ;
- date de création ;
- nombre de darks utilisés pour le Master Dark.

## Réutilisation et association avec les LIGHTs

Avant calibration d'un LIGHT, StellarPilot parcourt la bibliothèque de Master Darks et vérifie :

- même caméra ;
- même durée d'exposition ;
- même gain et même offset ;
- même binning ;
- mêmes dimensions/ROI ;
- même profondeur en bits ;
- même matrice Bayer ;
- température du capteur dans une tolérance de ±5 °C.

La température enregistrée dans le FITS du LIGHT est prioritaire sur la température instantanée remontée par INDI, car elle correspond au moment exact de l'exposition.

Si plusieurs Master Darks sont compatibles, StellarPilot choisit celui dont la température est la plus proche, puis le plus récent en cas d'égalité. Si le Master déjà utilisé par la session devient incompatible, StellarPilot doit essayer automatiquement un autre Master compatible avant d'interrompre le stack.

Si aucun Master n'est compatible, le stacking passe en pause de calibration. StellarPilot affiche la ou les valeurs incompatibles (LIGHT vs DARK), l'écart mesuré et la tolérance appliquée, puis demande de refaire les darks avec les paramètres actuels avant de reprendre le stacking.
