# StellarPilot field candidate 0.6.9-r5

Baseline terrain: `12c713718a2dae6de67ee072f0b59af8f81d8bff` (`0.6.9-r4`, build terrain `0917-193814`).

Candidate goals after the 2026-09-21 field test:
- bound astrometric recentering and use live mount readback;
- block unsafe corrections and polar-edge corrections;
- preflight OnStep time before the high-altitude calibration GOTO;
- allow safe stacking tests when centering is usable but not formally validated;
- keep automatic recentering disabled in uncentered test stacking mode.

Do not promote to `main` until a new field validation is successful.
