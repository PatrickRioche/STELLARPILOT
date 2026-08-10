# Releases

Les releases utilisent Semantic Versioning : `vMAJOR.MINOR.PATCH`.

Les sprints possèdent leur propre identifiant et ne modifient pas cette règle.

## Principe essentiel

Pour une release `vX.Y.Z`, les artefacts doivent partager **exactement** ce numéro :

- `StellarPilot-vX.Y.Z-linux-arm64.tar.gz`
- `StellarPilot-vX.Y.Z-android.apk`
- `StellarPilot-vX.Y.Z-source.zip`
- `StellarPilot-vX.Y.Z-SHA256SUMS.txt`

Il n'existe pas de version Android distincte de la version ARM64 pour le produit.
Les numéros techniques Android internes éventuels sont des métadonnées de build,
pas des versions produit visibles.
