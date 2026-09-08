# Politique de version

## Sprint

`S0.1`, `S0.2`, etc. = unité de travail interne.

## Release

`vMAJOR.MINOR.PATCH[-qualificatif]` = version publiée du produit.

Qualificatifs utilisés :

- `-poc` : pré-release technique de faisabilité avant validation de la chaîne intégrée ;
- `-beta` : pré-release intégrée destinée aux essais terrain après validation des fonctions matérielles principales ;
- `-rc` : release candidate à utiliser seulement lorsque les critères du Gate G0 sont presque entièrement satisfaits.

## Règles

1. Un sprint n'implique pas automatiquement une release.
2. Une release peut agréger plusieurs sprints.
3. Une release multi-plateforme porte un seul numéro de version produit.
4. Le fichier `VERSION` est la source de vérité du dépôt.
5. Le tag GitHub doit être exactement `v` suivi du contenu de `VERSION`.
6. Les variantes Android `device` et `simulation` partagent la même version produit ; le backend est une information séparée.
7. Les documents permanents évitent de recopier inutilement un numéro courant.
8. `0.0.0-dev` désigne un dépôt sans jalon publiable.
9. Une pré-release `-poc`, `-beta` ou `-rc` ne signifie pas que le Gate G0 est franchi.
10. Le passage à une release sans suffixe sera décidé après validation du Gate G0.

## Passage POC -> beta

Le passage en beta est justifié lorsque la chaîne matérielle principale fonctionne sur équipement réel et qu'un jalon intégré peut être testé de manière répétable. La beta peut conserver des limites terrain connues, à condition qu'elles soient documentées dans les notes de release.
