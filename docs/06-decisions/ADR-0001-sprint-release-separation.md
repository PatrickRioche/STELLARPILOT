# ADR-0001 — Séparer sprints et versions produit

- Statut : Accepted
- Date : 2026-08-10
- Sprint : S0.1

## Contexte
Le projet doit avancer par sprints tout en produisant ultérieurement des artefacts
ARM64 et Android. Mélanger numéro de sprint et numéro de release crée des ambiguïtés.

## Décision
Utiliser :
- `S<phase>.<numéro>` pour les sprints ;
- `vMAJOR.MINOR.PATCH` pour les releases ;
- un numéro de release unique commun à ARM64 et Android.

## Conséquence
Il n'existe qu'une version produit à suivre pour chaque publication.
