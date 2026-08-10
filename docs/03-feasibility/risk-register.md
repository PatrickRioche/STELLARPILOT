# Registre des risques POC

| ID | Risque | Impact | Probabilité initiale | Vérification / mitigation |
|---|---|---:|---:|---|
| R-001 | Variations entre pilotes INDI | Élevé | Moyen | essais sur plusieurs matériels/pilotes |
| R-002 | Plate solving trop lent sur ARM64 | Élevé | Moyen | benchmark réel |
| R-003 | Plate solving peu robuste sans estimation initiale | Élevé | Moyen | jeux d'images variés |
| R-004 | Communication Android/Pi instable | Moyen | Moyen | reconnexion et tests LAN |
| R-005 | Sémantique AZ/EQ trop différente | Élevé | Moyen | séparer coeur commun et initialisation spécifique |
| R-006 | Astroberry impose des contraintes inattendues | Moyen | À mesurer | POC d'intégration |
| R-007 | API constructeur nécessaire pour certains périphériques | Moyen | Moyen | privilégier INDI, documenter exceptions |
| R-008 | Temps de capture + solve incompatible avec l'UX visée | Élevé | À mesurer | instrumenter tous les temps |
