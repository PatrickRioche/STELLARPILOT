# Contribuer à StellarPilot

## Principes

1. Une fonctionnalité doit être spécifiée avant implémentation.
2. Une hypothèse critique doit être vérifiée par une preuve ou un POC.
3. Les décisions structurantes sont consignées dans un ADR.
4. Toute modification automatisée du dépôt doit commencer par un PRECHECK.
5. Les changements incompatibles doivent échouer sans modification partielle.
6. Les composants spécifiques à un constructeur doivent rester derrière une
   abstraction commune autant que possible.
7. Les builds ARM64 et Android d'une même release portent exactement le même
   numéro de version produit.

## Commits suggérés

- `docs:` spécification/documentation
- `feas:` faisabilité ou POC
- `arch:` architecture / ADR
- `test:` vérification
- `feat:` fonctionnalité
- `fix:` correction
- `build:` build / packaging
- `ci:` intégration continue
- `release:` préparation d'une release
