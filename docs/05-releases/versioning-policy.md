# Politique de version

## Sprint

`S0.1`, `S0.2`, etc. = unité de travail.

## Release

`v0.x.y` = version publiée du produit.

Les versions portant un suffixe comme `-poc` sont des **pré-releases techniques** destinées à figer un jalon de faisabilité avant le Gate G0.

## Règles

1. Un sprint n'implique pas automatiquement une release.
2. Une release peut agréger plusieurs sprints.
3. Une release multi-plateforme porte un seul numéro.
4. Le fichier `VERSION` est la source de vérité du dépôt.
5. Les documents permanents évitent de recopier inutilement un numéro courant.
6. `0.0.0-dev` désigne un dépôt sans jalon publiable.
7. Avant Gate G0, une pré-release suffixée `-poc` peut être publiée lorsqu'un sous-ensemble cohérent et testable du POC a été validé.
8. Une pré-release POC ne signifie pas que le Gate G0 est franchi.
9. Le passage à une release sans suffixe POC sera décidé après validation du Gate G0.
