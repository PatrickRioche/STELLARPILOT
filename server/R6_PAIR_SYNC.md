# 0.6.9-r6 synchronized delivery marker

This file intentionally lives under `server/` so the synchronized r6 pairing commit triggers both the Android and server CI workflows.

Pairing policy:
- identical Git SHA: same delivery;
- same normalized functional version (for example `0.6.9-r6-device-*` and `0.6.9-r6`): compatible delivery;
- different functional versions: incompatible.
