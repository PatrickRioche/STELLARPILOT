# Déploiement du serveur StellarPilot depuis Windows

## Règle d'architecture

Le Raspberry Pi est une cible de déploiement et d'exécution. Il ne contient pas de clone Git du projet et aucune commande `git` n'est nécessaire sur le Pi.

Le dépôt de référence reste sur le PC Windows. Les mises à jour courantes du serveur passent par :

```powershell
.\tools\deploy-server.ps1
```

## Ce que fait le script

1. lit la branche et le commit locaux sur le PC ;
2. produit une archive propre avec `git archive HEAD server` ;
3. n'embarque donc pas le `.venv` Windows, les caches Python, les fichiers temporaires ou les données non suivies ;
4. copie l'archive vers `/tmp` sur le Raspberry Pi ;
5. extrait dans un répertoire de staging temporaire ;
6. synchronise les sources vers `/home/astroberry/stellarpilot-server` ;
7. préserve toujours :
   - `/home/astroberry/stellarpilot-server/.venv/` ;
   - `/home/astroberry/stellarpilot-server/data/` ;
8. met à jour les dépendances dans le venv Linux ;
9. installe/met à jour le service systemd ;
10. redémarre `stellarpilot-server` ;
11. vérifie `/health` et les routes V0.6 indispensables.

## Utilisation standard

Depuis PowerShell, à la racine du dépôt :

```powershell
cd "C:\Users\PatrickR\Google Drive\00-PERSO-CG\00-DEVOP-CG\ASTRO\STELLARPILOT"

git fetch origin
git switch feat/mount-bahtinov-v060
git pull --ff-only

.\tools\deploy-server.ps1
```

Pour une autre adresse :

```powershell
.\tools\deploy-server.ps1 -PiHost "astroberry@192.168.1.46"
```

## Pré-requis sur le Pi

Le Pi doit déjà disposer de :

- Python 3 et `venv` ;
- `rsync` ;
- INDI (`indi_getprop`) ;
- astrometry.net (`solve-field`) ;
- accès `sudo` pour la mise à jour du service systemd.

Le script s'arrête explicitement si INDI ou astrometry.net sont absents.

## À ne plus faire pour une mise à jour courante

Ne pas :

- installer Git sur le Pi ;
- cloner le dépôt sur le Pi ;
- créer une archive avec `tar -czf ... server` depuis Windows, car cela peut embarquer `server/.venv` ;
- copier le `.venv` Windows vers Linux ;
- supprimer manuellement `stellarpilot-server/data` lors d'une mise à jour.

Le script historique `scripts/install/install-server-astroberry.sh` reste utile comme outil de bootstrap/installation initiale depuis une arborescence source complète, mais n'est plus la procédure normale de mise à jour du serveur.
