package com.dagxam.waterworld;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
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

/** Живая фауна островов и отдельная фауна океана, включая опасных подводных мобов. */
public final class MobDecorator implements Listener {
    private final JavaPlugin plugin;
    private final int seaLevel;
    private final int centerX;
    private final int centerZ;
    private final int islandRadius;
    private final int oceanRadius;
    private final Map<EntityType, Integer> landCaps = new EnumMap<>(EntityType.class);
    private final Map<EntityType, Integer> oceanCaps = new EnumMap<>(EntityType.class);
    private final Map<UUID, EnumMap<EntityType, Integer>> counters = new HashMap<>();

    public MobDecorator(JavaPlugin plugin, int seaLevel, int centerX, int centerZ,
                        int islandRadius, int oceanRadius) {
        this.plugin = plugin;
        this.seaLevel = seaLevel;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.islandRadius = islandRadius;
        this.oceanRadius = oceanRadius;

        landCaps.put(EntityType.PIG, 3);
        landCaps.put(EntityType.SHEEP, 8);
        landCaps.put(EntityType.COW, 7);
        landCaps.put(EntityType.CHICKEN, 7);
        landCaps.put(EntityType.HORSE, 4);
        landCaps.put(EntityType.DONKEY, 2);
        landCaps.put(EntityType.RABBIT, 5);
        landCaps.put(EntityType.WOLF, 3);
        landCaps.put(EntityType.CAT, 3);

        // Мирные и обычные морские существа.
        oceanCaps.put(EntityType.TROPICAL_FISH, 48);
        oceanCaps.put(EntityType.PUFFERFISH, 12);
        oceanCaps.put(EntityType.COD, 24);
        oceanCaps.put(EntityType.SALMON, 24);
        oceanCaps.put(EntityType.SQUID, 16);
        oceanCaps.put(EntityType.GLOW_SQUID, 8);
        oceanCaps.put(EntityType.DOLPHIN, 8);

        // Опасные обитатели глубин. Лимиты специально небольшие, чтобы океан был опасным,
        // но не превращался в бесконечную толпу мобов.
        oceanCaps.put(EntityType.DROWNED, 18);
        oceanCaps.put(EntityType.GUARDIAN, 8);
    }

    public void start() {
        for (World world : plugin.getServer().getWorlds()) reconcile(world);
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (World world : plugin.getServer().getWorlds()) reconcile(world);
        }, 12000L, 12000L);
    }

    public void initializeWorld(World world) { reconcile(world); }

    public void populate(Chunk chunk) {
        World world = chunk.getWorld();
        int x = chunk.getX() * 16 + 8;
        int z = chunk.getZ() * 16 + 8;
        ThreadLocalRandom random = ThreadLocalRandom.current();

        if (isInsideIsland(x, z)) {
            if (random.nextInt(100) < 12) spawnLandMob(world, chunk, random);
            return;
        }

        if (isInsideOcean(x, z) && random.nextInt(100) < 24) {
            // В большинстве океанских чанков появляются косяки и обычные морские существа.
            // Реже появляются утопленники и стражи глубин.
            if (random.nextInt(100) < 16) spawnHostileOceanMob(world, chunk, random);
            else spawnOceanMob(world, chunk, random);
        }
    }

    private void spawnLandMob(World world, Chunk chunk, ThreadLocalRandom random) {
        EntityType type = weightedLandType(random);
        if (getCount(world, type) >= landCaps.getOrDefault(type, 0)) return;
        for (int attempt = 0; attempt < 4; attempt++) {
            int x = chunk.getX() * 16 + 2 + random.nextInt(12);
            int z = chunk.getZ() * 16 + 2 + random.nextInt(12);
            if (!isInsideIsland(x, z)) continue;
            int y = world.getHighestBlockYAt(x, z);
            if (!world.getBlockAt(x, y, z).getType().isSolid()) continue;
            if (!world.getBlockAt(x, y + 1, z).isEmpty() || !world.getBlockAt(x, y + 2, z).isEmpty()) continue;
            world.spawnEntity(new Location(world, x + .5D, y + 1, z + .5D), type);
            return;
        }
    }

    private void spawnOceanMob(World world, Chunk chunk, ThreadLocalRandom random) {
        EntityType type = weightedOceanType(random);
        if (getCount(world, type) >= oceanCaps.getOrDefault(type, 0)) return;
        int count = type == EntityType.TROPICAL_FISH || type == EntityType.COD || type == EntityType.SALMON
                ? 2 + random.nextInt(4) : 1;
        for (int i = 0; i < count; i++) {
            if (!spawnOceanEntity(world, chunk, random, type)) break;
        }
    }

    private void spawnHostileOceanMob(World world, Chunk chunk, ThreadLocalRandom random) {
        EntityType type = random.nextInt(100) < 78 ? EntityType.DROWNED : EntityType.GUARDIAN;
        if (getCount(world, type) >= oceanCaps.getOrDefault(type, 0)) return;
        spawnOceanEntity(world, chunk, random, type);
    }

    private boolean spawnOceanEntity(World world, Chunk chunk, ThreadLocalRandom random, EntityType type) {
        if (getCount(world, type) >= oceanCaps.getOrDefault(type, 0)) return false;
        for (int attempt = 0; attempt < 8; attempt++) {
            int x = chunk.getX() * 16 + 2 + random.nextInt(12);
            int z = chunk.getZ() * 16 + 2 + random.nextInt(12);
            if (!isInsideOcean(x, z)) continue;
            int y = Math.max(world.getMinHeight() + 6, seaLevel - 5 - random.nextInt(30));
            if (world.getBlockAt(x, y, z).getType() != Material.WATER) continue;
            if (world.getBlockAt(x, y + 1, z).getType() != Material.WATER) continue;
            world.spawnEntity(new Location(world, x + .5D, y + .2D, z + .5D), type);
            return true;
        }
        return false;
    }

    private EntityType weightedLandType(ThreadLocalRandom random) {
        int roll = random.nextInt(100);
        if (roll < 34) return EntityType.SHEEP;
        if (roll < 56) return EntityType.COW;
        if (roll < 70) return EntityType.CHICKEN;
        if (roll < 76) return EntityType.PIG;
        if (roll < 84) return EntityType.HORSE;
        if (roll < 88) return EntityType.RABBIT;
        if (roll < 92) return EntityType.DONKEY;
        if (roll < 97) return EntityType.WOLF;
        return EntityType.CAT;
    }

    private EntityType weightedOceanType(ThreadLocalRandom random) {
        int roll = random.nextInt(100);
        if (roll < 36) return EntityType.TROPICAL_FISH;
        if (roll < 54) return EntityType.COD;
        if (roll < 70) return EntityType.SALMON;
        if (roll < 80) return EntityType.PUFFERFISH;
        if (roll < 91) return EntityType.SQUID;
        if (roll < 96) return EntityType.GLOW_SQUID;
        return EntityType.DOLPHIN;
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        Location location = event.getLocation();
        EntityType type = event.getEntityType();
        Map<EntityType, Integer> caps = isInsideIsland(location.getBlockX(), location.getBlockZ()) ? landCaps
                : isInsideOcean(location.getBlockX(), location.getBlockZ()) ? oceanCaps : null;
        if (caps == null || !caps.containsKey(type)) return;
        World world = location.getWorld();
        if (getCount(world, type) >= caps.get(type)) {
            event.setCancelled(true);
            return;
        }
        changeCount(world, type, 1);
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();
        EntityType type = entity.getType();
        if (!landCaps.containsKey(type) && !oceanCaps.containsKey(type)) return;
        changeCount(entity.getWorld(), type, -1);
    }

    private void reconcile(World world) {
        EnumMap<EntityType, Integer> map = new EnumMap<>(EntityType.class);
        for (Entity entity : world.getEntities()) {
            EntityType type = entity.getType();
            int x = entity.getLocation().getBlockX();
            int z = entity.getLocation().getBlockZ();
            if (landCaps.containsKey(type) && isInsideIsland(x, z)) {
                map.merge(type, 1, Integer::sum);
            } else if (oceanCaps.containsKey(type) && isInsideOcean(x, z)) {
                map.merge(type, 1, Integer::sum);
            }
        }
        counters.put(world.getUID(), map);
    }

    private int getCount(World world, EntityType type) {
        return counters.computeIfAbsent(world.getUID(), ignored -> new EnumMap<>(EntityType.class))
                .getOrDefault(type, 0);
    }

    private void changeCount(World world, EntityType type, int delta) {
        EnumMap<EntityType, Integer> map = counters.computeIfAbsent(world.getUID(), ignored -> new EnumMap<>(EntityType.class));
        map.put(type, Math.max(0, map.getOrDefault(type, 0) + delta));
    }

    private boolean isInsideIsland(int x, int z) { return distance(x, z) <= islandRadius - 5; }
    private boolean isInsideOcean(int x, int z) {
        double d = distance(x, z);
        return d >= islandRadius + 18 && d <= oceanRadius;
    }
    private double distance(int x, int z) {
        double dx = x - centerX, dz = z - centerZ;
        return Math.sqrt(dx * dx + dz * dz);
    }
}
