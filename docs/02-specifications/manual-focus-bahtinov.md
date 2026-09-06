# Spécification — mise au point manuelle Bahtinov

## Statut

V0.6 : collecte de références réelles et validation du workflow avec une monture équatoriale motorisée.

L'objectif immédiat n'est pas encore de motoriser le focuser. STELLARPILOT guide l'utilisateur, maintient l'étoile suivie et constitue un référentiel permettant de développer ensuite l'estimation automatique de la qualité de mise au point.

## But

Permettre une mise au point fiable avec un masque de Bahtinov et un focuser manuel.

Le logiciel doit progressivement être capable de :

- choisir une étoile brillante adaptée ;
- pointer cette étoile ;
- maintenir le tracking ;
- afficher les captures en continu ou à cadence régulière ;
- mesurer la géométrie des aigrettes ;
- indiquer la qualité du focus ;
- indiquer de quel côté du foyer se trouve le réglage.

## Séquence cible

1. La première astrométrie est réussie.
2. STELLARPILOT propose plusieurs étoiles brillantes visibles.
3. L'utilisateur sélectionne l'étoile.
4. La monture effectue un GOTO réel.
5. Le tracking sidéral est activé.
6. Le centrage de l'étoile sera à terme réalisé automatiquement par plate solving.
7. L'application demande d'installer le masque de Bahtinov.
8. Les captures sont répétées pendant que l'utilisateur agit sur le focuser.
9. STELLARPILOT affiche l'évaluation du focus.
10. Lorsque la qualité optimale est atteinte, l'utilisateur retire le masque et poursuit la préparation.

## Calibration V0.6

Pour construire le premier référentiel réel, l'utilisateur doit pouvoir étiqueter volontairement plusieurs états de mise au point :

```text
Très mauvais
Mauvais
Moyen
Bon
Optimum
Mauvais de l'autre côté
```

Une commande supplémentaire permet de marquer une capture comme :

```text
Mauvaise capture / ignorer
```

La série recommandée autour du foyer est :

```text
Très mauvais côté A
→ Mauvais
→ Moyen
→ Bon
→ Optimum
→ Bon
→ Moyen
→ Mauvais côté B
→ Très mauvais côté B
```

La V0.6 expose les six étiquettes demandées. Le côté du foyer sera ensuite représenté explicitement dans le modèle interne lorsque l'analyse automatique sera stabilisée.

## Données à conserver

Chaque référence doit permettre de retrouver le FITS scientifique et son contexte :

```text
timestamp_utc
capture_path
exposure_s
camera
label
label_fr
target_id
target_name
target_ra_hours
target_dec_deg
mount_ra_hours
mount_dec_deg
tracking_mode
```

Le FITS reste archivé sur le Raspberry Pi par le pipeline de capture existant. La V0.6 conserve en parallèle un journal JSONL Android reliant l'étiquette choisie au chemin du FITS.

## Exposition

Le Bahtinov n'utilise pas nécessairement la pose astrométrique de 4 s. L'étoile choisie est brillante et les aigrettes doivent rester nettes sans saturation.

Les valeurs de test proposées dans la V0.6 sont :

```text
0,10 s
0,25 s
0,50 s
1,00 s
2,00 s
```

La version automatique devra adapter la pose à la luminosité de l'étoile et au niveau de saturation.

## Mesures automatiques futures

Pour une étoile sans masque, les indicateurs classiques restent utiles :

- HFR ;
- FWHM ;
- ellipticité ;
- SNR ;
- saturation.

Avec le masque de Bahtinov, l'indicateur principal devra cependant provenir de la géométrie des trois aigrettes.

Le principe est de détecter les trois axes de diffraction et de mesurer le décalage de l'aigrette centrale par rapport à l'intersection idéale des deux autres.

La sortie cible pourra être de la forme :

```text
Qualité : 96 / 100
État : OPTIMUM
Décalage Bahtinov : 0,6 px
Sens : centré
Confiance : 94 %
```

ou, hors foyer :

```text
Qualité : 58 / 100
État : MOYEN
Décalage Bahtinov : 8,4 px
Sens : côté A
```

Le signe du décalage doit permettre de distinguer les deux côtés du foyer.

## Critères de validation du référentiel

Le référentiel doit contenir :

- plusieurs captures par niveau ;
- des captures des deux côtés du foyer ;
- au moins une séquence passant progressivement par l'optimum ;
- une étoile maintenue en tracking ;
- les paramètres de caméra et d'exposition ;
- des captures explicitement ignorées en cas de vibration, nuage ou défaut de suivi.

## Évolution après la V0.6

Une fois le référentiel réel acquis :

1. détecter automatiquement l'étoile principale ;
2. détecter les trois aigrettes ;
3. mesurer l'erreur signée ;
4. comparer la mesure aux labels humains ;
5. établir les seuils `très mauvais / mauvais / moyen / bon / optimum` ;
6. afficher une indication temps réel ;
7. à terme, arrêter automatiquement la boucle lorsque plusieurs captures consécutives confirment l'optimum.
