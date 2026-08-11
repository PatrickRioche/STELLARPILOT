# Spécification — darks au capuchon

## Statut

Fonction cible après validation du POC. Elle ne conditionne pas le premier Gate POC.

## But

Créer des images de calibration sans accessoire mécanique supplémentaire.

## Séquence cible

1. La mise au point Bahtinov est terminée.
2. StellarPilot demande à l'utilisateur de remettre le capuchon standard du télescope ou de la lunette.
3. StellarPilot acquiert une série de darks avec des paramètres compatibles avec la session d'observation.
4. Les darks sont enregistrés avec leurs métadonnées.
5. Un Master Dark peut être généré.
6. StellarPilot demande de retirer le capuchon avant le début des observations.

## Métadonnées à conserver

- identifiant/modèle de caméra ;
- durée d'exposition ;
- gain ;
- binning si applicable ;
- résolution/ROI si applicable ;
- température si disponible ;
- date de création ;
- nombre de darks utilisés pour le Master Dark.

## Réutilisation

À terme, StellarPilot pourra rechercher un Master Dark compatible avant de demander une nouvelle acquisition.
