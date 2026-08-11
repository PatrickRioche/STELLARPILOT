# Composants envisagés

## Composants nécessaires au POC

- `poc-server` : serveur minimal sur Raspberry Pi ARM64 ;
- `indi-adapter` : connexion aux périphériques INDI ;
- `camera-control` : acquisition d'une image ;
- `mount-control` : lecture d'état/coordonnées et commande minimale ;
- `solve-service` : plate solving local ;
- `api-server` : communication locale avec Android ;
- `android-poc` : client Android minimal.

## Composants envisagés après le POC

- `orchestrator` : séquences complètes d'initialisation et d'observation ;
- `calibration-service` : darks et calibration ;
- `bahtinov-assist` : assistance à la mise au point manuelle ;
- `centering-service` : boucle solve / correction / re-solve ;
- `stacking-service` : empilement temps réel ;
- `catalog-service` : identification d'objets célestes.

Un service de focuser/autofocus n'est pas requis dans le premier périmètre.
