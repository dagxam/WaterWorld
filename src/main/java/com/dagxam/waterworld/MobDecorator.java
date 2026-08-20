package com.dagxam.waterworld;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

/**
 * Балансирует живность на главном острове и добавляет редких животных/морских мобов.
 * Основной естественный спавн Minecraft не отключается.
 */
public final class MobDecorator implements Listener {
    private final int seaLevel;
    private final int centerX;
    private final int centerZ;
    private final int islandRadius;
    private final int oceanRadius;
    private final Random random = new Random();

    private final Map<EntityType, Integer> landCaps = new EnumMap<>(EntityType.class);
    private final Map<EntityType, Integer> oceanCaps = new EnumMap<>(EntityType.class);

    public MobDecorator(int seaLevel, int centerX, int centerZ, int islandRadius, int oceanRadius) {
        this.seaLevel = seaLevel;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.islandRadius = islandRadius;
        this.oceanRadius = oceanRadius;

        // Свиней специально мало, чтобы они больше не заполняли весь остров.
        landCaps.put(EntityType.PIG, 3);
        landCaps.put(EntityType.SHEEP, 8);
        landCaps.put(EntityType.COW, 7);
        landCaps.put(EntityType.CHICKEN, 7);
        landCaps.put(EntityType.HORSE, 4);
        landCaps.put(EntityType.DONKEY, 2);
        landCaps.put(EntityType.RABBIT, 5);
        landCaps.put(EntityType.WOLF, 3);
        landCaps.put(EntityType.CAT, 3);

        oceanCaps.put(EntityType.TROPICAL_FISH, 24);
        oceanCaps.put(EntityType.PUFFERFISH, 8);
        oceanCaps.put(EntityType.COD, 10);
        oceanCaps.put(EntityType.SALMON, 10);
        oceanCaps.put(EntityType.SQUID, 8);
        oceanCaps.put(EntityType.GLOW_SQUID, 4);
        oceanCaps.put(EntityType.DOLPHIN, 4);
    }

    public void populate(Chunk chunk) {
        World world = chunk.getWorld();
        int x = chunk.getX() * 16 + 8;
        int z = chunk.getZ() * 16 + 8;

        if (isInsideIsland(x, z)) {
            if (random.nextInt(100) < 38) spawnLandMob(world, chunk);
            return;
        }

        double distance = distance(x, z);
        if (distance >= islandRadius + 18 && distance <= oceanRadius && random.nextInt(100) < 22) {
            spawnOceanMob(world, chunk);
        }
    }

    private void spawnLandMob(World world, Chunk chunk) {
        EntityType type = weightedLandType();
        int cap = landCaps.getOrDefault(type, 0);
        if (countNearby(world, centerX, centerZ, islandRadius + 25, type) >= cap) return;

        for (int attempt = 0; attempt < 8; attempt++) {
            int x = chunk.getX() * 16 + 1 + random.nextInt(14);
            int z = chunk.getZ() * 16 + 1 + random.nextInt(14);
            if (!isInsideIsland(x, z)) continue;

            int y = world.getHighestBlockYAt(x, z);
            if (world.getBlockAt(x, y, z).getType() != Material.GRASS_BLOCK) continue;
            if (!world.getBlockAt(x, y + 1, z).isEmpty()) continue;
            if (!world.getBlockAt(x, y + 2, z).isEmpty()) continue;

            world.spawnEntity(new Location(world, x + .5, y + 1, z + .5), type);
            return;
        }
    }

    private EntityType weightedLandType() {
        int roll = random.nextInt(100);
        if (roll < 32) return EntityType.SHEEP;
        if (roll < 55) return EntityType.COW;
        if (roll < 68) return EntityType.CHICKEN;
        if (roll < 75) return EntityType.PIG;
        if (roll < 83) return EntityType.HORSE;
        if (roll < 87) return EntityType.RABBIT;
        if (roll < 91) return EntityType.DONKEY;
        if (roll < 96) return EntityType.WOLF;
        return EntityType.CAT;
    }

    private void spawnOceanMob(World world, Chunk chunk) {
        EntityType type = weightedOceanType();
        int cap = oceanCaps.getOrDefault(type, 0);
        if (countNearby(world, centerX, centerZ, oceanRadius + 20, type) >= cap) return;

        for (int attempt = 0; attempt < 10; attempt++) {
            int x = chunk.getX() * 16 + 1 + random.nextInt(14);
            int z = chunk.getZ() * 16 + 1 + random.nextInt(14);
            double distance = distance(x, z);
            if (distance < islandRadius + 18 || distance > oceanRadius) continue;

            int y = Math.max(5, seaLevel - 5 - random.nextInt(22));
            Material at = world.getBlockAt(x, y, z).getType();
            Material above = world.getBlockAt(x, y + 1, z).getType();
            if (at != Material.WATER || above != Material.WATER) continue;

            world.spawnEntity(new Location(world, x + .5, y, z + .5), type);
            return;
        }
    }

    private EntityType weightedOceanType() {
        int roll = random.nextInt(100);
        if (roll < 38) return EntityType.TROPICAL_FISH;
        if (roll < 53) return EntityType.COD;
        if (roll < 65) return EntityType.SALMON;
        if (roll < 76) return EntityType.PUFFERFISH;
        if (roll < 89) return EntityType.SQUID;
        if (roll < 95) return EntityType.GLOW_SQUID;
        return EntityType.DOLPHIN;
    }

    private int countNearby(World world, int x, int z, int radius, EntityType type) {
        int count = 0;
        double r = radius;
        for (Entity entity : world.getNearbyEntities(new Location(world, x, seaLevel, z), r, 96, r)) {
            if (entity.getType() == type && entity instanceof LivingEntity) count++;
        }
        return count;
    }

    private boolean isInsideIsland(int x, int z) {
        return distance(x, z) <= islandRadius - 5;
    }

    private double distance(int x, int z) {
        double dx = x - centerX;
        double dz = z - centerZ;
        return Math.sqrt(dx * dx + dz * dz);
    }

    @EventHandler
    public void onNaturalCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) return;
        Location location = event.getLocation();
        if (!isInsideIsland(location.getBlockX(), location.getBlockZ())) return;

        EntityType type = event.getEntityType();
        Integer cap = landCaps.get(type);
        if (cap == null) return;

        if (countNearby(location.getWorld(), centerX, centerZ, islandRadius + 25, type) >= cap) {
            event.setCancelled(true);
        }
    }
}
