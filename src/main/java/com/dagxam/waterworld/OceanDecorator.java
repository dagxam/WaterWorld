package com.dagxam.waterworld;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Barrel;
import org.bukkit.block.Chest;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Random;

/** Deterministic living ocean generator using a real seabed scan, not water-surface height. */
public final class OceanDecorator {
    private final IslandLayout layout;
    private final int seaLevel, attempts, plantChance, kelpChance, coralChance, meadowChance, ventChance;
    private final int structureChance, shipwreckChance, ruinChance;

    public OceanDecorator(FileConfiguration config, IslandLayout layout) {
        this.layout = layout;
        seaLevel = config.getInt("sea-level", 62);
        attempts = Math.max(0, config.getInt("ocean-life.attempts-per-chunk", 32));
        plantChance = clamp(config.getInt("ocean-life.seagrass-chance-percent", 58), 0, 100);
        kelpChance = clamp(config.getInt("ocean-life.kelp-chance-percent", 24), 0, 100);
        coralChance = clamp(config.getInt("ocean-life.coral-chance-percent", 18), 0, 100);
        meadowChance = clamp(config.getInt("ocean-life.meadow-chance-percent", 18), 0, 100);
        ventChance = clamp(config.getInt("ocean-life.magma-vent-chance-percent", 2), 0, 100);
        structureChance = clamp(config.getInt("ocean-structures.chance-percent", 3), 0, 100);
        shipwreckChance = clamp(config.getInt("ocean-structures.shipwreck-percent", 45), 0, 100);
        ruinChance = clamp(config.getInt("ocean-structures.ruin-percent", 55), 0, 100);
    }

    public void decorate(World world, int chunkX, int chunkZ) {
        if (attempts == 0 || !isOceanChunk(world, chunkX, chunkZ)) return;
        Random random = random(world, chunkX, chunkZ, 0x51A7E5EEDL);
        for (int i = 0; i < attempts; i++) {
            int x = chunkX * 16 + random.nextInt(16), z = chunkZ * 16 + random.nextInt(16);
            if (insideIsland(world, x, z)) continue;
            int floorY = findSeabed(world, x, z);
            if (floorY < world.getMinHeight() + 2 || floorY >= seaLevel - 1) continue;
            decorateFloor(world, x, floorY, z, random);
            decorateLife(world, x, floorY, z, random);
        }
        Random structures = random(world, chunkX, chunkZ, 0x0CE45EEDL);
        if (structures.nextInt(100) < structureChance) {
            int x = chunkX * 16 + 3 + structures.nextInt(10), z = chunkZ * 16 + 3 + structures.nextInt(10), floor = findSeabed(world, x, z);
            if (floor > world.getMinHeight() + 8 && floor < seaLevel - 8) {
                int roll = structures.nextInt(100);
                if (roll < shipwreckChance) shipwreck(world, x, floor, z, structures);
                else if (roll < shipwreckChance + ruinChance) underwaterRuin(world, x, floor, z, structures);
            }
        }
    }

    private void decorateLife(World world, int x, int floorY, int z, Random random) {
        int roll = random.nextInt(100);
        if (floorY >= seaLevel - 24 && roll < coralChance) coralGarden(world, x, floorY, z, random);
        else if (roll < coralChance + kelpChance) kelpCluster(world, x, floorY + 1, z, random);
        else if (roll < coralChance + kelpChance + meadowChance) seagrassMeadow(world, x, floorY + 1, z, random);
        else if (roll < coralChance + kelpChance + meadowChance + plantChance) seagrass(world, x, floorY + 1, z, random);
    }

    private int findSeabed(World world, int x, int z) {
        int y = Math.min(seaLevel - 1, world.getMaxHeight() - 2);
        while (y > world.getMinHeight() + 1 && world.getBlockAt(x, y, z).getType() == Material.WATER) y--;
        return world.getBlockAt(x, y, z).getType() == Material.WATER ? -1 : y;
    }

    private boolean isOceanChunk(World world, int chunkX, int chunkZ) {
        int x = chunkX * 16 + 8, z = chunkZ * 16 + 8; long dx = x, dz = z, radius = 900L;
        return dx * dx + dz * dz <= radius * radius && !insideIsland(world, x, z);
    }

    private boolean insideIsland(World world, int x, int z) {
        for (IslandLayout.Island island : layout.get(world.getSeed())) {
            long dx = (long) x - island.x(), dz = (long) z - island.z(), r = island.radius() + 10L;
            if (dx * dx + dz * dz <= r * r) return true;
        }
        return false;
    }

    private void decorateFloor(World world, int x, int y, int z, Random random) {
        Material current = world.getBlockAt(x, y, z).getType();
        if (current != Material.SAND && current != Material.GRAVEL && current != Material.CLAY && current != Material.STONE) return;
        int roll = random.nextInt(100);
        if (roll < 14) world.getBlockAt(x, y, z).setType(Material.GRAVEL, false);
        else if (roll < 28) world.getBlockAt(x, y, z).setType(Material.CLAY, false);
        else if (roll < 34) world.getBlockAt(x, y, z).setType(Material.SAND, false);
        else if (roll < 34 + ventChance && y < seaLevel - 12) world.getBlockAt(x, y, z).setType(Material.MAGMA_BLOCK, false);
    }

    private void seagrassMeadow(World world, int x, int y, int z, Random random) {
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            if (dx * dx + dz * dz > 9 || random.nextInt(100) >= 82) continue;
            int px = x + dx, pz = z + dz, floor = findSeabed(world, px, pz);
            if (floor >= seaLevel - 1 || floor < world.getMinHeight() + 2) continue;
            seagrass(world, px, floor + 1, pz, random);
        }
    }

    private void seagrass(World world, int x, int y, int z, Random random) {
        if (world.getBlockAt(x, y, z).getType() != Material.WATER) return;
        if (random.nextInt(100) < 40 && y + 1 < seaLevel && world.getBlockAt(x, y + 1, z).getType() == Material.WATER) world.getBlockAt(x, y, z).setType(Material.TALL_SEAGRASS, false);
        else world.getBlockAt(x, y, z).setType(Material.SEAGRASS, false);
    }

    private void kelpCluster(World world, int x, int y, int z, Random random) {
        int count = 3 + random.nextInt(6);
        for (int i = 0; i < count; i++) {
            int px = x + random.nextInt(9) - 4, pz = z + random.nextInt(9) - 4, floor = findSeabed(world, px, pz);
            if (floor < world.getMinHeight() + 2 || floor >= seaLevel - 2) continue;
            kelp(world, px, floor + 1, pz, random);
        }
    }

    private void kelp(World world, int x, int y, int z, Random random) {
        int height = 4 + random.nextInt(10);
        for (int i = 0; i < height && y + i < seaLevel - 1; i++) {
            if (world.getBlockAt(x, y + i, z).getType() != Material.WATER) break;
            world.getBlockAt(x, y + i, z).setType(i == height - 1 ? Material.KELP : Material.KELP_PLANT, false);
        }
    }

    private void coralGarden(World world, int x, int y, int z, Random random) {
        Material[] corals = {Material.TUBE_CORAL_BLOCK, Material.BRAIN_CORAL_BLOCK, Material.BUBBLE_CORAL_BLOCK, Material.FIRE_CORAL_BLOCK, Material.HORN_CORAL_BLOCK};
        int radius = 3 + random.nextInt(3);
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            if (dx * dx + dz * dz > radius * radius || random.nextInt(100) >= 76) continue;
            int px = x + dx, pz = z + dz, floor = findSeabed(world, px, pz);
            if (floor < seaLevel - 26 || floor >= seaLevel - 1 || world.getBlockAt(px, floor + 1, pz).getType() != Material.WATER) continue;
            world.getBlockAt(px, floor, pz).setType(corals[random.nextInt(corals.length)], false);
            if (random.nextInt(100) < 48 && world.getBlockAt(px, floor + 1, pz).getType() == Material.WATER) world.getBlockAt(px, floor + 1, pz).setType(Material.SEA_PICKLE, false);
            if (random.nextInt(100) < 28 && world.getBlockAt(px, floor + 1, pz).getType() == Material.WATER) seagrass(world, px, floor + 1, pz, random);
        }
    }

    private void shipwreck(World world, int x, int floor, int z, Random random) {
        int length = 7 + random.nextInt(4), y = floor + 1; boolean alongX = random.nextBoolean();
        for (int i = -length / 2; i <= length / 2; i++) for (int w = -2; w <= 2; w++) {
            int px = alongX ? x + i : x + w, pz = alongX ? z + w : z + i;
            if (Math.abs(w) == 2 || (Math.abs(i) == length / 2 && Math.abs(w) > 0)) world.getBlockAt(px, y, pz).setType(Material.DARK_OAK_PLANKS, false);
            else if (random.nextInt(100) < 24) world.getBlockAt(px, y, pz).setType(Material.WATER, false);
        }
        for (int i = -length / 2 + 1; i < length / 2; i++) {
            int px = alongX ? x + i : x, pz = alongX ? z : z + i;
            if (random.nextInt(100) < 65) world.getBlockAt(px, y + 1, pz).setType(Material.OAK_FENCE, false);
        }
        world.getBlockAt(x, y, z).setType(Material.BARREL, false);
        if (world.getBlockAt(x, y, z).getState() instanceof Barrel barrel) fillLoot(barrel.getInventory(), random, true);
    }

    private void underwaterRuin(World world, int x, int floor, int z, Random random) {
        int y = floor + 1, radius = 4 + random.nextInt(3); Material wall = random.nextBoolean() ? Material.PRISMARINE_BRICKS : Material.MOSSY_STONE_BRICKS;
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            int d = dx * dx + dz * dz;
            if (d > radius * radius || d < (radius - 2) * (radius - 2) || random.nextInt(100) < 18) continue;
            int h = 1 + random.nextInt(4);
            for (int py = 0; py < h; py++) world.getBlockAt(x + dx, y + py, z + dz).setType(wall, false);
        }
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) if (Math.abs(dx) == 2 || Math.abs(dz) == 2) world.getBlockAt(x + dx, y + 1, z + dz).setType(Material.PRISMARINE, false);
        world.getBlockAt(x, y, z).setType(Material.CHEST, false);
        if (world.getBlockAt(x, y, z).getState() instanceof Chest chest) fillLoot(chest.getInventory(), random, false);
    }

    private void fillLoot(Inventory inventory, Random random, boolean ship) {
        inventory.addItem(new ItemStack(Material.EMERALD, 1 + random.nextInt(4)));
        inventory.addItem(new ItemStack(Material.GOLD_INGOT, 2 + random.nextInt(5)));
        if (random.nextInt(100) < 45) inventory.addItem(new ItemStack(Material.DIAMOND, 1 + random.nextInt(3)));
        if (ship && random.nextInt(100) < 35) inventory.addItem(new ItemStack(Material.HEART_OF_THE_SEA));
        if (!ship && random.nextInt(100) < 25) inventory.addItem(new ItemStack(Material.NAUTILUS_SHELL, 1 + random.nextInt(3)));
    }

    private Random random(World world, int chunkX, int chunkZ, long salt) { return new Random(world.getSeed() ^ salt ^ ((long) chunkX * 341873128712L) ^ ((long) chunkZ * 132897987541L)); }
    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
}
