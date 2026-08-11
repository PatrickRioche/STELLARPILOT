# Spécifications actuelles

## POC prioritaire

Le POC doit valider :

`Android -> Raspberry Pi ARM64 -> INDI -> monture + caméra -> capture -> astrométrie -> résultat Android`.

## Séquence cible après faisabilité

`serveur Pi -> connexion Android -> heure/localisation -> AZ/EQ -> capture initiale -> plate solving -> étoile brillante -> Bahtinov manuel -> darks au capuchon -> observations`.

## Documents

- `initialization-sequence.md` : séquence complète cible ;
- `hardware-abstraction.md` : monture/caméra et report du focuser ;
- `astrometry.md` : exigences du plate solving ;
- `manual-focus-bahtinov.md` : mise au point manuelle ;
- `darks.md` : acquisition des darks ;
- `android.md` : client Android POC ;
- `arm64.md` : rôle du Raspberry Pi ;
- `api-contract.md` : contrat réseau à préciser ;
- `smart-telescope.md` : trajectoire fonctionnelle globale.
