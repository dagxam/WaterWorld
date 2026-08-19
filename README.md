# WaterWorld 2.0

Improved version of the original WaterWorld generator.

## Main changes

- Noise generators are cached per world seed instead of recreated for every chunk.
- Ocean terrain, caves and island generation are separated into readable methods.
- Ocean terrain is configurable in `config.yml`.
- Added one central plains island around X=0, Z=0.
- Island is about 34x34 blocks at its widest point.
- Island rises above sea level, has a sandy shoreline, dirt and grass.
- Island biome is `PLAINS`.
- Vanilla decorations remain enabled so plains vegetation and trees can generate.
- Mob generation remains enabled so normal passive mobs can spawn on the island.
- Cave generation is kept away from the upper ocean floor.
- Added basic bounds/clamping to prevent invalid terrain heights.

## Important

The repository integration currently returns HTTP 403 for write operations, so these changes were packaged as source files instead of being pushed directly to GitHub.
