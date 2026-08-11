# Politique de version

## Sprint
`S0.1`, `S0.2`, etc. = unité de travail.

## Release
`v0.x.y` = version publiée du produit.

## Règles
1. Un sprint n'implique pas automatiquement une release.
2. Une release peut agréger plusieurs sprints.
3. Une release multi-plateforme porte un seul numéro.
4. Le fichier `VERSION` est la source de vérité du dépôt.
5. Les documents permanents évitent de recopier inutilement un numéro courant.
6. Tant que Gate G0 n'est pas franchi, `0.0.0-dev` signifie que le dépôt est
   en phase de définition et ne constitue pas une release binaire.
7. La première version publiable sera décidée au Gate G0 ; elle n'est pas
   prédéterminée dans ce squelette.
