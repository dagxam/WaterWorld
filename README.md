# WaterWorld 3.0

Optimized generator for the main Minecraft server world.

## What changed in 3.0

- Terrain geometry is generated in one `ChunkGenerator` pass.
- Removed the expensive manual high-altitude `AIR` fill loop.
- `IslandLayout` is cached and never recreated for every populated chunk.
- Distance checks use squared distances in hot paths.
- Mountain generation moved from a post-population decorator into the generator.
- Cave generation moved into the generator; old duplicate cave decorator removed.
- Removed duplicate `IslandDecorator` and obsolete `MountainDecorator`.
- Vegetation now uses every corresponding value from `config.yml`.
- Added several deterministic island types: forest, rocky and tropical.
- Added a wider natural shoreline and shallow-water transition.
- Main mountain is stretched instead of being a perfect circle.
- Spawn preload was reduced to a configurable chunk radius.
- Spawn search uses a small deterministic set of candidates instead of scanning thousands of blocks.
- Time cycle defaults to one update per second instead of every tick.
- `MobDecorator` is connected and uses event-driven caps without periodic full-world entity scans.
- Build target is Java 21 for broader server compatibility.

## Generation pipeline

`WaterGenerator -> ocean -> island shape -> mountain -> caves -> surface`

`ChunkPopulateEvent -> vegetation -> village -> optional light mob population`

Only objects that require a populated world remain in `ChunkPopulateEvent`.
