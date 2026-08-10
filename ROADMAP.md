# Roadmap StellarPilot

## Phase POC — Vérification de faisabilité globale

Avant toute architecture de production, StellarPilot doit prouver que sa chaîne technique essentielle fonctionne sur du matériel réel.

1. **POC-S0.1 — Raspberry Pi + INDI + caméra**
   - détecter la caméra via INDI ;
   - déclencher une capture ;
   - récupérer une image exploitable.
2. **POC-S0.2 — INDI + monture AZ/EQ**
   - détecter la monture ;
   - lire son état et ses coordonnées ;
   - envoyer une commande simple et contrôlée.
3. **POC-S0.3 — Astrométrie locale ARM64**
   - plate solving d'une image réelle ;
   - récupérer au minimum RA, DEC, orientation et échelle ;
   - mesurer le temps de résolution.
4. **POC-S0.4 — Serveur StellarPilot minimal**
   - exposer une API locale ;
   - orchestrer capture + solve ;
   - renvoyer le résultat au client.
5. **POC-S0.5 — Client Android minimal**
   - se connecter au Raspberry Pi ;
   - transmettre heure et localisation ;
   - lancer Capture & Solve ;
   - afficher le résultat astrométrique.

## Gate POC — Go / No-Go

Le projet est considéré techniquement faisable si la chaîne suivante fonctionne de bout en bout :

```text
Android
  -> connexion locale au Raspberry Pi
  -> transmission heure + localisation
  -> serveur StellarPilot
  -> INDI
  -> monture + caméra
  -> capture réelle
  -> plate solving local ARM64
  -> RA/DEC + orientation + échelle
  -> résultat renvoyé et affiché sur Android
```

Le masque de Bahtinov, les darks, le recentrage automatique et le live stacking appartiennent à la séquence fonctionnelle cible, mais ne conditionnent pas le premier Gate POC.

## Phase 0 — Spécification après validation du POC

Après Gate POC :
- formaliser l'architecture MVP ;
- figer les contrats d'interface ;
- spécifier l'initialisation AZ/EQ ;
- spécifier l'assistance Bahtinov ;
- spécifier la calibration par darks ;
- définir packaging ARM64 et Android ;
- préparer les tests de compatibilité.

## Phase 1 — MVP

La numérotation des sprints d'implémentation sera définie après validation des spécifications issues de la Phase 0.

## Vision ultérieure

- GoTo et centrage automatique ;
- acquisition et suivi ;
- calibration avancée ;
- empilement temps réel ;
- catalogue et identification d'objets ;
- assistance automatique Bahtinov ;
- focuser motorisé et autofocus en phase ultérieure ;
- expérience de télescope intelligent ouverte et multi-constructeurs.
