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
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Event-driven land and marine population with bounded per-world caps. */
public final class MobDecorator implements Listener {
    private final IslandLayout layout;
    private final int seaLevel, oceanRadius;
    private final Map<EntityType, Integer> landCaps = new EnumMap<>(EntityType.class);
    private final Map<EntityType, Integer> oceanCaps = new EnumMap<>(EntityType.class);
    private final Map<UUID, EnumMap<EntityType, Integer>> counters = new HashMap<>();

    public MobDecorator(JavaPlugin plugin, FileConfiguration config, IslandLayout layout) {
        this.layout = layout;
        seaLevel = config.getInt("sea-level", 62);
        oceanRadius = Math.max(128, config.getInt("animals.ocean-radius", 900));
        landCaps.put(EntityType.PIG, 4); landCaps.put(EntityType.SHEEP, 10); landCaps.put(EntityType.COW, 8); landCaps.put(EntityType.CHICKEN, 8);
        landCaps.put(EntityType.HORSE, 4); landCaps.put(EntityType.DONKEY, 2); landCaps.put(EntityType.RABBIT, 6); landCaps.put(EntityType.WOLF, 3);

        oceanCaps.put(EntityType.TROPICAL_FISH, 52); oceanCaps.put(EntityType.PUFFERFISH, 14); oceanCaps.put(EntityType.COD, 28); oceanCaps.put(EntityType.SALMON, 24);
        oceanCaps.put(EntityType.SQUID, 14); oceanCaps.put(EntityType.GLOW_SQUID, 12); oceanCaps.put(EntityType.DOLPHIN, 8); oceanCaps.put(EntityType.TURTLE, 8);
        oceanCaps.put(EntityType.DROWNED, 14);
    }

    public void initializeWorld(World world) { counters.computeIfAbsent(world.getUID(), ignored -> new EnumMap<>(EntityType.class)); }
    public void stop() { counters.clear(); }

    public void populate(Chunk chunk) {
        World world = chunk.getWorld();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int x = chunk.getX() * 16 + 8, z = chunk.getZ() * 16 + 8;
        if (insideAnyIsland(world, x, z) && random.nextInt(100) < 6) spawnLandMob(world, chunk, random);
        else if (insideOcean(world, x, z) && random.nextInt(100) < 18) spawnOceanMob(world, chunk, random);
    }

    @EventHandler public void onCreatureSpawn(CreatureSpawnEvent event) {
        EntityType type = event.getEntityType();
        World world = event.getLocation().getWorld();
        Map<EntityType, Integer> caps = insideAnyIsland(world, event.getLocation().getBlockX(), event.getLocation().getBlockZ()) ? landCaps :
                insideOcean(world, event.getLocation().getBlockX(), event.getLocation().getBlockZ()) ? oceanCaps : null;
        if (caps == null || !caps.containsKey(type)) return;
        int count = getCount(world, type);
        if (count >= caps.get(type)) { event.setCancelled(true); return; }
        change(world, type, 1);
    }

    @EventHandler public void onEntityDeath(EntityDeathEvent event) {
        EntityType type = event.getEntityType();
        if (landCaps.containsKey(type) || oceanCaps.containsKey(type)) change(event.getEntity().getWorld(), type, -1);
    }

    private void spawnLandMob(World world, Chunk chunk, ThreadLocalRandom random) {
        EntityType type = weightedLand(random);
        if (getCount(world, type) >= landCaps.get(type)) return;
        for (int attempt = 0; attempt < 3; attempt++) {
            int x = chunk.getX() * 16 + random.nextInt(16), z = chunk.getZ() * 16 + random.nextInt(16);
            if (!insideAnyIsland(world, x, z)) continue;
            int y = world.getHighestBlockYAt(x, z);
            if (!world.getBlockAt(x, y, z).getType().isSolid() || !world.getBlockAt(x, y + 1, z).isEmpty() || !world.getBlockAt(x, y + 2, z).isEmpty()) continue;
            world.spawnEntity(new Location(world, x + .5D, y + 1, z + .5D), type);
            return;
        }
    }

    private void spawnOceanMob(World world, Chunk chunk, ThreadLocalRandom random) {
        EntityType type = weightedOcean(random);
        if (getCount(world, type) >= oceanCaps.get(type)) return;
        for (int attempt = 0; attempt < 6; attempt++) {
            int x = chunk.getX() * 16 + random.nextInt(16), z = chunk.getZ() * 16 + random.nextInt(16);
            if (!insideOcean(world, x, z)) continue;
            int floor = world.getHighestBlockYAt(x, z);
            int minY = Math.max(world.getMinHeight() + 3, floor + 2);
            int maxY = Math.max(minY, seaLevel - 2);
            int y = minY + random.nextInt(Math.max(1, maxY - minY + 1));
            if (type == EntityType.TURTLE) y = seaLevel - 2;
            if (type == EntityType.GLOW_SQUID) y = Math.max(minY, seaLevel - 12 - random.nextInt(10));
            if (type == EntityType.DROWNED) y = Math.max(minY, seaLevel - 8 - random.nextInt(10));
            if (world.getBlockAt(x, y, z).getType() != Material.WATER || world.getBlockAt(x, Math.min(y + 1, world.getMaxHeight() - 1), z).getType() != Material.WATER) continue;
            world.spawnEntity(new Location(world, x + .5D, y, z + .5D), type);
            return;
        }
    }

    private EntityType weightedLand(ThreadLocalRandom r) { int n=r.nextInt(100); if(n<36)return EntityType.SHEEP; if(n<58)return EntityType.COW; if(n<72)return EntityType.CHICKEN; if(n<80)return EntityType.PIG; if(n<90)return EntityType.HORSE; if(n<95)return EntityType.RABBIT; return EntityType.WOLF; }
    private EntityType weightedOcean(ThreadLocalRandom r) {
        int n = r.nextInt(100);
        if (n < 32) return EntityType.TROPICAL_FISH;
        if (n < 50) return EntityType.COD;
        if (n < 64) return EntityType.SALMON;
        if (n < 74) return EntityType.PUFFERFISH;
        if (n < 84) return EntityType.SQUID;
        if (n < 91) return EntityType.GLOW_SQUID;
        if (n < 96) return EntityType.DOLPHIN;
        if (n < 99) return EntityType.TURTLE;
        return EntityType.DROWNED;
    }

    private boolean insideAnyIsland(World world, int x, int z) {
        for (IslandLayout.Island island : layout.get(world.getSeed())) {
            long dx=(long)x-island.x(), dz=(long)z-island.z(), r=Math.max(1,island.radius()-5);
            if(dx*dx+dz*dz<=r*r)return true;
        }
        return false;
    }

    private boolean insideOcean(World world, int x, int z) {
        long dx=x, dz=z;
        if(dx*dx+dz*dz>(long)oceanRadius*oceanRadius)return false;
        return !insideAnyIsland(world, x, z);
    }

    private int getCount(World world, EntityType type) {
        return counters.computeIfAbsent(world.getUID(), ignored -> new EnumMap<>(EntityType.class)).getOrDefault(type, 0);
    }

    private void change(World world, EntityType type, int delta) {
        EnumMap<EntityType,Integer> map=counters.computeIfAbsent(world.getUID(), ignored -> new EnumMap<>(EntityType.class));
        map.put(type, Math.max(0,map.getOrDefault(type,0)+delta));
    }
}
