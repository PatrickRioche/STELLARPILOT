# ADR-0003 — Valider un POC avant l'architecture de production

- Statut : Accepted
- Date : 2026-08-10
- Phase : POC

## Contexte

StellarPilot combine plusieurs dépendances critiques : Android, Raspberry Pi ARM64, INDI, matériels hétérogènes et astrométrie locale.

## Décision

Avant tout développement de production, réaliser un POC minimal de bout en bout.

Le Gate POC doit valider :
Android -> Raspberry Pi -> INDI -> monture/caméra -> capture -> plate solving -> retour Android.

## Conséquences

Les choix techniques du POC restent réversibles et ne deviennent pas automatiquement l'architecture finale.
