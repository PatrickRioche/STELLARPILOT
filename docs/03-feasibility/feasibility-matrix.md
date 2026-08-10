# Matrice de faisabilité POC

| ID | Sujet | Question | Preuve attendue | État |
|---|---|---|---|---|
| F-001 | ARM64 | StellarPilot peut-il exécuter son serveur minimal sur le Raspberry Pi cible ? | POC reproductible | À faire |
| F-002 | INDI caméra | Peut-on détecter une caméra et déclencher une capture réelle ? | image capturée | À faire |
| F-003 | INDI monture | Peut-on détecter une monture AZ/EQ, lire son état et envoyer une commande simple ? | POC matériel | À faire |
| F-004 | Astrométrie | Le plate solving local est-il viable sur ARM64 ? | solve + benchmark | À faire |
| F-005 | Résultat | Peut-on extraire RA, DEC, orientation et échelle de façon reproductible ? | jeux d'essais | À faire |
| F-006 | API locale | Le Raspberry Pi peut-il exposer Capture & Solve proprement ? | appel API | À faire |
| F-007 | Android | Android peut-il se connecter, transmettre contexte et recevoir le résultat ? | APK POC | À faire |
| F-008 | Bout en bout | Android -> Pi -> INDI -> capture -> solve -> Android fonctionne-t-il ? | démonstration réelle | À faire |
| F-009 | AZ/EQ | Le même coeur peut-il couvrir les montures AZ et EQ avec des différences d'initialisation maîtrisées ? | analyse + essais | À faire |
| F-010 | Astroberry | Le POC peut-il s'intégrer à l'environnement Raspberry Pi/Astroberry sans dépendance bloquante ? | analyse + essai | À faire |

## Fonctions volontairement différées

- focuser motorisé ;
- autofocus ;
- Bahtinov automatique ;
- dark library avancée ;
- live stacking ;
- interface finale.
