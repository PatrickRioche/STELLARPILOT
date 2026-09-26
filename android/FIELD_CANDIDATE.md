# StellarPilot Android field candidate 0.6.9-r6

Baseline terrain: `1ed009cfaa76ffd9f81e3ded2c7dce881231abba` (`0.6.9-r5`, DEVICE), version utilisée pendant la nuit d'observation du 25 au 26 septembre 2026.

Cette candidate reste strictement dérivée de r5 et n'intègre pas les essais 0.7.x.

Corrections / ajouts r6 :

- réparation de la compatibilité Master Dark en privilégiant les métadonnées réellement écrites dans les FITS (gain, offset, exposition) ;
- conservation de la tolérance thermique des darks pour la caméra non refroidie ;
- nouvelle bibliothèque de Master Flats, utilisable avec des flats réalisés en journée ;
- sélection automatique du Master Flat compatible le plus récent, sans contrainte de température ni d'exposition LIGHT ;
- application Dark + Flat avant registration/stacking ;
- garde-fou de galerie : une image résolue très loin de la cible déclarée ne peut plus être enregistrée silencieusement sous le mauvais nom ;
- tests r6 sur compatibilité dark et flat.

Le diagnostic du GOTO/coordonnées OnStep reste volontairement instrumenté mais aucune correction mathématique spéculative RA/DEC/JNow/LST n'est introduite dans cette candidate tant que la cause exacte n'est pas isolée.

Expected Android version: `0.6.9-r6` / versionCode 20.

Do not promote to `main` before field validation.
