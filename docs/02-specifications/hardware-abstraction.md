# Spécification — abstraction matérielle du POC

## Monture

Le POC doit supporter le concept de monture sans dépendre d'une marque particulière.

Capacités minimales à tester :
- détection ;
- type AZ ou EQ si disponible, sinon sélection utilisateur ;
- état ;
- coordonnées ;
- mouvement ou commande simple ;
- arrêt contrôlé.

## Caméra

Capacités minimales à tester :
- détection ;
- exposition ;
- gain si disponible ;
- déclenchement ;
- récupération d'une image exploitable par le plate solver.

## Focuser

Le focuser n'est **pas requis** dans le POC ni dans le premier périmètre fonctionnel.

La mise au point initiale est manuelle avec un masque de Bahtinov sur une étoile brillante.

Le support des focusers INDI et de l'autofocus est reporté à une évolution ultérieure.
