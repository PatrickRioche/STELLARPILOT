# Spécification — mise au point manuelle Bahtinov

## Statut

Fonction cible après validation du POC. Elle ne conditionne pas le Gate POC initial.

## But

Permettre une mise au point fiable sans focuser motorisé.

## Séquence cible

1. StellarPilot propose une étoile brillante adaptée.
2. L'utilisateur sélectionne l'étoile.
3. La monture pointe la cible.
4. StellarPilot affiche l'image de la caméra avec rafraîchissement adapté.
5. L'application demande d'installer le masque de Bahtinov.
6. L'utilisateur règle manuellement la mise au point.
7. L'utilisateur valide la mise au point.
8. L'application demande de retirer le masque.

## MVP

Le premier niveau d'assistance peut se limiter à :
- affichage agrandi de l'étoile ;
- réglages d'exposition/gain simples ;
- rafraîchissement régulier ;
- confirmation utilisateur.

## Évolution

Une version ultérieure pourra détecter les aigrettes du masque et estimer automatiquement l'erreur et le sens de correction, toujours sans exiger de focuser motorisé.
