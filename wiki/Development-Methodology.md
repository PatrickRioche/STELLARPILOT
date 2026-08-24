# Méthode de développement StellarPilot

## Principes

### Specification first
Une fonction est décrite avant d’être implémentée : besoin, entrées/sorties, interfaces, erreurs et critères de validation.

### Feasibility first
Une hypothèse critique doit être démontrée par un test ou un POC avant d’être considérée comme acquise.

### Vendor neutral
Le code spécifique à un constructeur doit rester derrière une abstraction commune.

### INDI first
INDI est la couche matérielle de référence du POC.

### Local first
Les fonctions critiques doivent fonctionner sans service cloud obligatoire.

### Evidence based
Un Go/No-Go doit reposer sur une preuve reproductible : commande, log, build, capture, mesure ou test.

### Rollback safe
Les scripts doivent vérifier leurs préconditions, sauvegarder les fichiers remplacés et éviter les écritures partielles.

### One release version
Une release StellarPilot possède un numéro produit commun. Le fichier `VERSION` est la source de vérité.

## Cycle recommandé

```text
besoin → spécification → faisabilité → branche → implémentation
      → tests → documentation → commit → main → tag → release
```

## Branches et intégration

`main` représente l’état intégré. Une évolution significative est réalisée sur une branche dédiée.

Avant fusion :

```bash
git status
git diff --check
```

## Commits

Préfixes : `docs:`, `feas:`, `arch:`, `test:`, `feat:`, `fix:`, `build:`, `ci:`, `release:`.

## Tests

Serveur :

```bash
python -m py_compile server/app/main.py server/app/indi/service.py server/app/solving/service.py
cd server
PYTHONPATH=. pytest -q
```

Android :

```powershell
cd android
.\gradlew.bat assembleDeviceDebug --no-daemon
```

Une fonction matérielle doit être validée sur matériel réel lorsque cela est nécessaire à la preuve de faisabilité.

## Releases POC

Avant Gate G0, les versions suffixées `-poc` sont des pré-releases techniques. Une release comprend idéalement code intégré, `VERSION`, changelog, note de release, tests, build Android, tag Git, GitHub Release et artifacts.

## Documentation as code

Le Wiki est versionné sous `wiki/` puis publiable dans GitHub Wiki. Ainsi les procédures suivent les mêmes règles de review, historique et rollback que le code.
