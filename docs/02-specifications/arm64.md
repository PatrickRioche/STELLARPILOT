# Spécification — Raspberry Pi / Linux ARM64

## Rôle dans le POC

Le Raspberry Pi héberge le coeur technique du POC :
- serveur StellarPilot minimal ;
- communication INDI ;
- acquisition caméra ;
- accès monture ;
- plate solving local ;
- API locale pour Android.

## Critères de faisabilité

- démarrage reproductible ;
- accès aux périphériques INDI ;
- capture d'une image réelle ;
- exécution locale du plate solver ;
- temps et consommation de ressources mesurés ;
- communication locale stable avec Android.

## Après le POC

Le packaging, le service système, l'installation et l'intégration précise à Astroberry seront traités dans les sprints de spécification suivants.
