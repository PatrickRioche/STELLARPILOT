# ADR-0002 — Reporter focuser et autofocus

- Statut : Accepted
- Date : 2026-08-10
- Phase : POC

## Contexte

La faisabilité fondamentale de StellarPilot dépend d'abord de la chaîne Android -> Raspberry Pi -> INDI -> monture/caméra -> capture -> plate solving -> retour Android.

Un focuser motorisé ajouterait une dépendance matérielle qui n'est pas nécessaire pour prouver cette chaîne.

## Décision

Le POC et le premier périmètre fonctionnel n'exigent pas de focuser motorisé.

La mise au point est réalisée manuellement avec un masque de Bahtinov sur une étoile brillante.

Les darks sont réalisés en remettant le capuchon standard du télescope ou de la lunette.

## Conséquences

- POC matériel plus simple ;
- meilleure compatibilité avec des configurations existantes ;
- autofocus reporté à une phase ultérieure ;
- possibilité future d'ajouter un focuser INDI sans en faire une dépendance du coeur.
