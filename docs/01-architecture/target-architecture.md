# Architecture cible — hypothèse à valider par le POC

```text
                  StellarPilot Android
                         |
                   LAN / Wi-Fi
                         |
                StellarPilot Server
                 Raspberry Pi ARM64
                         |
                +--------+--------+
                |                 |
             Camera            Mount
             control           control
                |                 |
                +--------+--------+
                         |
                       INDI
                         |
                 +-------+-------+
                 |               |
              Camera         Mount AZ/EQ
                         
        Image captured on Raspberry Pi
                         |
                   Plate solver
                         |
            RA / DEC / orientation
                         |
                Return to Android
```

Cette architecture n'est pas gelée. Le POC doit d'abord démontrer que cette chaîne est viable sur ARM64 avec du matériel réel.

Le support d'un focuser motorisé est explicitement reporté à une phase ultérieure.
