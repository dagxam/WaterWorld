package com.dagxam.waterworld;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Небольшая декоративная деревня, создаваемая только если на равнине достаточно места. */
public final class VillageDecorator {
    private final int centerX, centerZ, radius;
    private final int offsetX, offsetZ;
    private boolean generated;

    public VillageDecorator(int centerX, int centerZ, int radius, int offsetX, int offsetZ) {
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.radius = radius;
        this.offsetX = offsetX;
        this.offsetZ = offsetZ;
    }

    public void generate(World world) {
        if (generated) return;
        int cx = centerX + offsetX;
        int cz = centerZ + offsetZ;
        if (!isInsideIsland(cx, cz, radius)) return;

        List<int[]> houses = List.of(
                new int[]{-20, -10}, new int[]{20, -10},
                new int[]{-18, 18}, new int[]{18, 18},
                new int[]{0, 4}
        );

        List<int[]> good = new ArrayList<>();
        for (int[] p : houses) {
            int x = cx + p[0], z = cz + p[1];
            if (isFlat(world, x, z, 8)) good.add(new int[]{x, z});
        }

        if (good.size() < 3) return;

        // Загружаем чанки заранее, чтобы дома не были перезаписаны последующей генерацией.
        for (int[] p : good) {
            int chunkX = p[0] >> 4;
            int chunkZ = p[1] >> 4;
            world.getChunkAt(chunkX, chunkZ).load();
        }

        Random random = new Random(world.getSeed() ^ 0xBADC0FFEE0DDF00DL);
        for (int[] p : good) buildHouse(world, p[0], p[1], random);

        buildPathsAndBell(world, cx, cz, good);
        spawnVillagers(world, good, random);
        generated = true;
    }

    private boolean isInsideIsland(int x, int z, int margin) {
        double dx = x - centerX, dz = z - centerZ;
        return dx * dx + dz * dz <= (double) (radius - margin) * (radius - margin);
    }

    private boolean isFlat(World world, int x, int z, int size) {
        int centerY = world.getHighestBlockYAt(x, z);
        if (centerY <= 63) return false;
        for (int dx = -size; dx <= size; dx += 4) {
            for (int dz = -size; dz <= size; dz += 4) {
                int y = world.getHighestBlockYAt(x + dx, z + dz);
                if (Math.abs(y - centerY) > 2) return false;
                if (world.getBlockAt(x + dx, y, z + dz).getType() != Material.GRASS_BLOCK) return false;
            }
        }
        return true;
    }

    private void buildHouse(World world, int x, int z, Random random) {
        int ground = world.getHighestBlockYAt(x, z);
        int w = 7, d = 7, wallH = 4;
        int minX = x - w / 2, minZ = z - d / 2;

        for (int bx = minX - 1; bx <= minX + w; bx++) {
            for (int bz = minZ - 1; bz <= minZ + d; bz++) {
                world.getBlockAt(bx, ground, bz).setType(Material.GRAVEL, false);
            }
        }

        for (int bx = minX; bx < minX + w; bx++) {
            for (int bz = minZ; bz < minZ + d; bz++) {
                for (int y = ground + 1; y <= ground + wallH + 2; y++) {
                    world.getBlockAt(bx, y, bz).setType(Material.AIR, false);
                }
                world.getBlockAt(bx, ground + 1, bz).setType(Material.COBBLESTONE, false);
            }
        }

        for (int y = ground + 2; y <= ground + wallH; y++) {
            for (int bx = minX; bx < minX + w; bx++) {
                for (int bz : new int[]{minZ, minZ + d - 1}) {
                    world.getBlockAt(bx, y, bz).setType((bx == minX || bx == minX + w - 1) ? Material.OAK_LOG : Material.OAK_PLANKS, false);
                }
            }
            for (int bz = minZ; bz < minZ + d; bz++) {
                for (int bx : new int[]{minX, minX + w - 1}) {
                    world.getBlockAt(bx, y, bz).setType((bz == minZ || bz == minZ + d - 1) ? Material.OAK_LOG : Material.OAK_PLANKS, false);
                }
            }
        }

        // Окна.
        world.getBlockAt(x - 2, ground + 3, minZ).setType(Material.GLASS_PANE, false);
        world.getBlockAt(x + 2, ground + 3, minZ + d - 1).setType(Material.GLASS_PANE, false);
        world.getBlockAt(minX, ground + 3, z).setType(Material.GLASS_PANE, false);
        world.getBlockAt(minX + w - 1, ground + 3, z).setType(Material.GLASS_PANE, false);

        // Дверь и интерьер.
        world.getBlockAt(x, ground + 2, minZ).setType(Material.OAK_DOOR, false);
        world.getBlockAt(x, ground + 3, minZ).setType(Material.OAK_DOOR, false);
        world.getBlockAt(x - 1, ground + 2, z).setType(Material.CHEST, false);
        world.getBlockAt(x + 1, ground + 2, z).setType(random.nextBoolean() ? Material.COMPOSTER : Material.FLETCHING_TABLE, false);

        // Двускатная крыша.
        for (int layer = 0; layer <= 3; layer++) {
            int y = ground + wallH + 1 + layer;
            int fromX = minX + layer;
            int toX = minX + w - 1 - layer;
            for (int bx = fromX; bx <= toX; bx++) {
                world.getBlockAt(bx, y, minZ).setType(Material.OAK_PLANKS, false);
                world.getBlockAt(bx, y, minZ + d - 1).setType(Material.OAK_PLANKS, false);
            }
        }

        // Кровать.
        world.getBlockAt(x, ground + 2, z + 1).setType(random.nextBoolean() ? Material.RED_BED : Material.WHITE_BED, false);
    }

    private void buildPathsAndBell(World world, int cx, int cz, List<int[]> houses) {
        int y = world.getHighestBlockYAt(cx, cz);
        if (y <= 63) return;
        for (int[] p : houses) {
            int steps = Math.max(Math.abs(p[0]), Math.abs(p[1]));
            for (int i = 0; i <= steps; i += 2) {
                int x = cx + (p[0] * i / Math.max(1, steps));
                int z = cz + (p[1] * i / Math.max(1, steps));
                int py = world.getHighestBlockYAt(x, z);
                if (Math.abs(py - y) <= 3) world.getBlockAt(x, py, z).setType(Material.DIRT_PATH, false);
            }
        }
        world.getBlockAt(cx, y + 1, cz).setType(Material.BELL, false);
    }

    private void spawnVillagers(World world, List<int[]> houses, Random random) {
        for (int i = 0; i < Math.min(5, houses.size()); i++) {
            int[] p = houses.get(i);
            int x = p[0], z = p[1];
            int y = world.getHighestBlockYAt(x, z) + 1;
            Villager villager = (Villager) world.spawnEntity(
                    new org.bukkit.Location(world, x + .5D, y, z + .5D), EntityType.VILLAGER);
            switch (i % 4) {
                case 0: villager.setProfession(Villager.Profession.FARMER); break;
                case 1: villager.setProfession(Villager.Profession.LIBRARIAN); break;
                case 2: villager.setProfession(Villager.Profession.FLETCHER); break;
                default: villager.setProfession(Villager.Profession.TOOLSMITH); break;
            }
        }
    }
}
