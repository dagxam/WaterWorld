package com.dagxam.waterworld;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Chest;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/** Deterministic villages. The main island and both secondary islands always receive a village when terrain allows it. */
public final class VillageDecorator {
    private final IslandLayout layout;
    private final TreasureDecorator treasures;
    private final int minRadius;
    private final Set<String> generated = new HashSet<>();

    public VillageDecorator(org.bukkit.configuration.file.FileConfiguration config, IslandLayout layout, TreasureDecorator treasures) {
        this.layout = layout;
        this.treasures = treasures;
        this.minRadius = Math.max(32, config.getInt("village.min-island-radius", 48));
    }

    public boolean shouldGenerate(World world, IslandLayout.Island island) {
        return island.radius() >= minRadius;
    }

    public void generate(World world, IslandLayout.Island island) {
        if (!shouldGenerate(world, island)) return;
        String key = world.getUID() + ":" + island.x() + ":" + island.z();
        if (generated.contains(key)) return;

        Random random = new Random(world.getSeed() ^ ((long) island.x() * 31L) ^ ((long) island.z() * 131L));
        List<int[]> candidates = findVillagePads(world, island);
        int needed = island.main() ? 5 : 3;
        if (candidates.size() < needed) return;

        List<int[]> used = new ArrayList<>(candidates.subList(0, needed));
        for (int[] p : used) buildHouse(world, p[0], p[1], random);
        buildPaths(world, island.x(), island.z(), used);
        spawnVillagers(world, used);
        placeTreasureMaps(world, island, used, random);
        generated.add(key);
    }

    private List<int[]> findVillagePads(World world, IslandLayout.Island island) {
        List<int[]> result = new ArrayList<>();
        int[][] offsets = {{0,0},{-20,-14},{20,-14},{-22,16},{22,16},{0,30},{-32,2},{32,2},{0,-30},{-10,28},{10,28}};
        for (int[] offset : offsets) {
            int x = island.x() + offset[0], z = island.z() + offset[1];
            if (inside(island, x, z, 10) && isFlat(world, x, z, 7)) result.add(new int[]{x, z});
        }
        return result;
    }

    private boolean inside(IslandLayout.Island island, int x, int z, int margin) {
        long dx = (long) x - island.x(), dz = (long) z - island.z();
        long r = Math.max(8, island.radius() - margin);
        return dx * dx + dz * dz <= r * r;
    }

    private boolean isFlat(World world, int x, int z, int size) {
        int centerY = world.getHighestBlockYAt(x, z);
        if (centerY <= 64) return false;
        for (int dx = -size; dx <= size; dx += 4) for (int dz = -size; dz <= size; dz += 4) {
            int y = world.getHighestBlockYAt(x + dx, z + dz);
            if (Math.abs(y - centerY) > 2 || world.getBlockAt(x + dx, y, z + dz).getType() != Material.GRASS_BLOCK) return false;
        }
        return true;
    }

    private void buildHouse(World world, int x, int z, Random random) {
        int ground = world.getHighestBlockYAt(x, z), w = 7, d = 7, wallH = 4;
        int minX = x - w / 2, minZ = z - d / 2;
        for (int bx = minX - 1; bx <= minX + w; bx++) for (int bz = minZ - 1; bz <= minZ + d; bz++) world.getBlockAt(bx, ground, bz).setType(Material.GRAVEL, false);
        for (int bx = minX; bx < minX + w; bx++) for (int bz = minZ; bz < minZ + d; bz++) {
            for (int y = ground + 1; y <= ground + wallH + 2; y++) world.getBlockAt(bx, y, bz).setType(Material.AIR, false);
            world.getBlockAt(bx, ground + 1, bz).setType(Material.COBBLESTONE, false);
        }
        for (int y = ground + 2; y <= ground + wallH; y++) {
            for (int bx = minX; bx < minX + w; bx++) for (int bz : new int[]{minZ, minZ + d - 1}) world.getBlockAt(bx, y, bz).setType((bx == minX || bx == minX + w - 1) ? Material.OAK_LOG : Material.OAK_PLANKS, false);
            for (int bz = minZ; bz < minZ + d; bz++) for (int bx : new int[]{minX, minX + w - 1}) world.getBlockAt(bx, y, bz).setType((bz == minZ || bz == minZ + d - 1) ? Material.OAK_LOG : Material.OAK_PLANKS, false);
        }
        world.getBlockAt(x, ground + 2, minZ).setType(Material.OAK_DOOR, false);
        world.getBlockAt(x, ground + 3, minZ).setType(Material.OAK_DOOR, false);
        world.getBlockAt(x - 2, ground + 3, minZ).setType(Material.GLASS_PANE, false);
        world.getBlockAt(x + 2, ground + 3, minZ + d - 1).setType(Material.GLASS_PANE, false);
        world.getBlockAt(x - 1, ground + 2, z).setType(Material.CHEST, false);
        world.getBlockAt(x + 1, ground + 2, z).setType(random.nextBoolean() ? Material.COMPOSTER : Material.FLETCHING_TABLE, false);
        for (int layer = 0; layer <= 3; layer++) {
            int y = ground + wallH + 1 + layer, fromX = minX + layer, toX = minX + w - 1 - layer;
            for (int bx = fromX; bx <= toX; bx++) { world.getBlockAt(bx, y, minZ).setType(Material.OAK_PLANKS, false); world.getBlockAt(bx, y, minZ + d - 1).setType(Material.OAK_PLANKS, false); }
        }
    }

    private void buildPaths(World world, int cx, int cz, List<int[]> houses) {
        int centerY = world.getHighestBlockYAt(cx, cz);
        for (int[] p : houses) {
            int dx = p[0] - cx, dz = p[1] - cz, steps = Math.max(Math.abs(dx), Math.abs(dz));
            for (int i = 0; i <= steps; i += 2) {
                int x = cx + dx * i / Math.max(1, steps), z = cz + dz * i / Math.max(1, steps);
                int y = world.getHighestBlockYAt(x, z);
                if (Math.abs(y - centerY) <= 3) world.getBlockAt(x, y, z).setType(Material.DIRT_PATH, false);
            }
        }
        world.getBlockAt(cx, centerY + 1, cz).setType(Material.BELL, false);
    }

    private void spawnVillagers(World world, List<int[]> houses) {
        for (int i = 0; i < houses.size(); i++) {
            int[] p = houses.get(i); int y = world.getHighestBlockYAt(p[0], p[1]) + 1;
            Villager v = (Villager) world.spawnEntity(new org.bukkit.Location(world, p[0] + .5D, y, p[1] + .5D), EntityType.VILLAGER);
            v.setProfession(switch (i % 4) { case 0 -> Villager.Profession.FARMER; case 1 -> Villager.Profession.LIBRARIAN; case 2 -> Villager.Profession.FLETCHER; default -> Villager.Profession.TOOLSMITH; });
        }
    }

    private void placeTreasureMaps(World world, IslandLayout.Island island, List<int[]> houses, Random random) {
        List<TreasureDecorator.Treasure> list = new ArrayList<>(treasures.forIsland(world, island));
        if (list.isEmpty()) return;
        Collections.shuffle(list, random);
        for (int i = 0; i < list.size(); i++) {
            int[] house = houses.get(i % houses.size());
            Chest chest = findChest(world, house[0], house[1]);
            if (chest == null) continue;
            chest.getBlockInventory().addItem(treasures.createTreasureMap(world, list.get(i)));
            chest.update(true, false);
        }
    }

    private Chest findChest(World world, int x, int z) {
        int ground = world.getHighestBlockYAt(x, z);
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) for (int dy = 1; dy <= 5; dy++) {
            if (world.getBlockAt(x + dx, ground + dy, z + dz).getState() instanceof Chest chest) return chest;
        }
        return null;
    }
}
