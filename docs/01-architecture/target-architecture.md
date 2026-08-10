# Architecture cible — hypothèse à valider

```text
                 StellarPilot Android
                         |
                 HTTPS / WebSocket
                         |
                StellarPilot Server
                  (Linux ARM64)
                         |
        +----------------+----------------+
        |                |                |
     Orchestrator     Imaging         Astronomy
        |                |                |
   Mount control      Camera I/O      Plate solving
   Focus control      Capture         Catalog / sky
        |                |                |
        +----------------+----------------+
                         |
                       INDI
                         |
        +-----------+----+------------+
        |           |                 |
     AZ / EQ      Camera            Focuser
```

Ce schéma est une hypothèse de départ, pas une architecture gelée.
Chaque frontière doit être testée pendant la Phase 0.
