package com.dagxam.waterworld;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Event-driven mob balancing. No periodic world-wide entity scan. */
public final class MobDecorator implements Listener {
    private final JavaPlugin plugin;
    private final IslandLayout layout;
    private final int seaLevel, oceanRadius;
    private final Map<EntityType, Integer> landCaps = new EnumMap<>(EntityType.class);
    private final Map<EntityType, Integer> oceanCaps = new EnumMap<>(EntityType.class);
    private final Map<UUID, EnumMap<EntityType, Integer>> counters = new HashMap<>();

    public MobDecorator(JavaPlugin plugin, FileConfiguration config, IslandLayout layout) {
        this.plugin = plugin; this.layout = layout;
        seaLevel = config.getInt("sea-level", 63);
        oceanRadius = Math.max(128, config.getInt("animals.ocean-radius", 600));
        landCaps.put(EntityType.PIG, 4); landCaps.put(EntityType.SHEEP, 10); landCaps.put(EntityType.COW, 8); landCaps.put(EntityType.CHICKEN, 8);
        landCaps.put(EntityType.HORSE, 4); landCaps.put(EntityType.DONKEY, 2); landCaps.put(EntityType.RABBIT, 6); landCaps.put(EntityType.WOLF, 3);
        oceanCaps.put(EntityType.TROPICAL_FISH, 28); oceanCaps.put(EntityType.PUFFERFISH, 8); oceanCaps.put(EntityType.COD, 12); oceanCaps.put(EntityType.SALMON, 12);
        oceanCaps.put(EntityType.SQUID, 8); oceanCaps.put(EntityType.GLOW_SQUID, 4); oceanCaps.put(EntityType.DOLPHIN, 4);
    }

    public void initializeWorld(World world) { counters.computeIfAbsent(world.getUID(), ignored -> new EnumMap<>(EntityType.class)); }
    public void stop() { counters.clear(); }

    public void populate(Chunk chunk) {
        World world = chunk.getWorld(); ThreadLocalRandom random = ThreadLocalRandom.current();
        int x = chunk.getX() * 16 + 8, z = chunk.getZ() * 16 + 8;
        if (insideAnyIsland(world, x, z) && random.nextInt(100) < 6) spawnLandMob(world, chunk, random);
        else if (insideOcean(world, x, z) && random.nextInt(100) < 5) spawnOceanMob(world, chunk, random);
    }

    @EventHandler public void onCreatureSpawn(CreatureSpawnEvent event) {
        EntityType type = event.getEntityType(); World world = event.getLocation().getWorld();
        Map<EntityType, Integer> caps = insideAnyIsland(world, event.getLocation().getBlockX(), event.getLocation().getBlockZ()) ? landCaps :
                insideOcean(world, event.getLocation().getBlockX(), event.getLocation().getBlockZ()) ? oceanCaps : null;
        if (caps == null || !caps.containsKey(type)) return;
        int count = getCount(world, type);
        if (count >= caps.get(type)) { event.setCancelled(true); return; }
        change(world, type, 1);
    }

    @EventHandler public void onEntityDeath(EntityDeathEvent event) {
        EntityType type = event.getEntityType(); if (landCaps.containsKey(type) || oceanCaps.containsKey(type)) change(event.getEntity().getWorld(), type, -1);
    }

    @EventHandler public void onChunkUnload(ChunkUnloadEvent event) {
        // Counters are caps, not exact persistence tracking. Do not scan unloaded chunks or worlds.
        if (!event.getChunk().isLoaded()) return;
    }

    private void spawnLandMob(World world, Chunk chunk, ThreadLocalRandom random) {
        EntityType type = weightedLand(random); if (getCount(world, type) >= landCaps.get(type)) return;
        for (int attempt = 0; attempt < 3; attempt++) {
            int x = chunk.getX() * 16 + random.nextInt(16), z = chunk.getZ() * 16 + random.nextInt(16);
            if (!insideAnyIsland(world, x, z)) continue;
            int y = world.getHighestBlockYAt(x, z);
            if (!world.getBlockAt(x, y, z).getType().isSolid() || !world.getBlockAt(x, y + 1, z).isEmpty() || !world.getBlockAt(x, y + 2, z).isEmpty()) continue;
            world.spawnEntity(new Location(world, x + .5D, y + 1, z + .5D), type); return;
        }
    }

    private void spawnOceanMob(World world, Chunk chunk, ThreadLocalRandom random) {
        EntityType type = weightedOcean(random); if (getCount(world, type) >= oceanCaps.get(type)) return;
        for (int attempt = 0; attempt < 4; attempt++) {
            int x = chunk.getX() * 16 + random.nextInt(16), z = chunk.getZ() * 16 + random.nextInt(16);
            if (!insideOcean(world, x, z)) continue;
            int y = Math.max(world.getMinHeight() + 4, seaLevel - 4 - random.nextInt(20));
            if (world.getBlockAt(x, y, z).getType() != Material.WATER || world.getBlockAt(x, y + 1, z).getType() != Material.WATER) continue;
            world.spawnEntity(new Location(world, x + .5D, y, z + .5D), type); return;
        }
    }

    private EntityType weightedLand(ThreadLocalRandom r) { int n=r.nextInt(100); if(n<36)return EntityType.SHEEP; if(n<58)return EntityType.COW; if(n<72)return EntityType.CHICKEN; if(n<80)return EntityType.PIG; if(n<90)return EntityType.HORSE; if(n<95)return EntityType.RABBIT; return EntityType.WOLF; }
    private EntityType weightedOcean(ThreadLocalRandom r) { int n=r.nextInt(100); if(n<40)return EntityType.TROPICAL_FISH; if(n<58)return EntityType.COD; if(n<72)return EntityType.SALMON; if(n<84)return EntityType.PUFFERFISH; if(n<95)return EntityType.SQUID; return EntityType.DOLPHIN; }

    private boolean insideAnyIsland(World world, int x, int z) {
        for (IslandLayout.Island island : layout.get(world.getSeed())) { long dx=(long)x-island.x(), dz=(long)z-island.z(), r=Math.max(1,island.radius()-5); if(dx*dx+dz*dz<=r*r)return true; }
        return false;
    }
    private boolean insideOcean(World world, int x, int z) {
        long dx=x, dz=z; if(dx*dx+dz*dz>(long)oceanRadius*oceanRadius)return false;
        return !insideAnyIsland(world, x, z);
    }
    private int getCount(World world, EntityType type) { return counters.computeIfAbsent(world.getUID(), ignored -> new EnumMap<>(EntityType.class)).getOrDefault(type, 0); }
    private void change(World world, EntityType type, int delta) { EnumMap<EntityType,Integer> map=counters.computeIfAbsent(world.getUID(), ignored -> new EnumMap<>(EntityType.class)); map.put(type, Math.max(0,map.getOrDefault(type,0)+delta)); }
}
