# Licence et contributions

## Licence

StellarPilot est distribué sous **GNU General Public License v3.0 or later (GPL-3.0-or-later)**. Le texte complet dans `LICENSE` reste la référence juridique.

La GPL permet notamment d’utiliser, étudier, modifier et redistribuer le logiciel, sous réserve de respecter ses obligations lors de la redistribution. Les bibliothèques, pilotes et dépendances externes conservent leurs propres licences.

## Contributions

Le fichier `CONTRIBUTING.md` est la référence.

Principes : spécifier avant d’implémenter, prouver les hypothèses critiques, consigner les décisions structurantes, effectuer un PRECHECK, éviter les modifications partielles, maintenir l’abstraction constructeur et partager le même numéro produit entre composants d’une release.

Types de commits : `docs:`, `feas:`, `arch:`, `test:`, `feat:`, `fix:`, `build:`, `ci:`, `release:`.

Avant intégration :

```bash
git status
git diff --check
```

Toute procédure d’installation reproductible doit évoluer avec le code correspondant. Le Wiki versionné se trouve dans `wiki/`, les scripts dans `scripts/install/` et `scripts/wiki/`.
