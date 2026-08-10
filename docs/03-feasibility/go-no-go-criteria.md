# Gate POC — critères Go / No-Go

## Go

Le POC est considéré réussi si, sur matériel réel :

- [ ] le serveur StellarPilot démarre sur Raspberry Pi ARM64 ;
- [ ] une caméra est détectée via INDI ;
- [ ] une monture est détectée via INDI ;
- [ ] une image astronomique réelle est capturée ;
- [ ] le plate solving s'exécute localement ;
- [ ] une solution RA/DEC/orientation/échelle est obtenue ;
- [ ] Android se connecte au serveur local ;
- [ ] Android transmet heure et localisation ;
- [ ] Android peut lancer Capture & Solve ;
- [ ] le résultat remonte et s'affiche sur Android ;
- [ ] les temps de traitement et limites sont documentés ;
- [ ] aucun blocage technique critique sans contournement réaliste n'est identifié.

## Go avec réserves

Acceptable si la chaîne fonctionne mais exige encore :
- optimisation ARM64 ;
- amélioration de robustesse ;
- adaptation de certains pilotes ;
- simplification temporaire de l'interface.

## No-Go

À prononcer si une dépendance critique rend impossible ou irréaliste la chaîne de bout en bout dans les contraintes du projet.
