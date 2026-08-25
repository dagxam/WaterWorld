package com.dagxam.waterworld;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Barrel;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;

/** Deterministic buried treasures: a hidden barrel with loot and maps pointing to it. */
public final class TreasureDecorator {
    public record Treasure(int x, int z, IslandLayout.Island island) {}

    private final IslandLayout layout;
    private final int seaLevel, minPerIsland, maxPerIsland;
    private final Map<UUID, List<Treasure>> cache = new HashMap<>();
    private final Set<String> generated = new HashSet<>();

    public TreasureDecorator(FileConfiguration config, IslandLayout layout) {
        this.layout = layout;
        this.seaLevel = config.getInt("sea-level", 63);
        this.minPerIsland = Math.max(0, config.getInt("treasures.min-per-island", 1));
        this.maxPerIsland = Math.max(minPerIsland, config.getInt("treasures.max-per-island", 3));
    }

    public List<Treasure> treasures(World world) {
        return cache.computeIfAbsent(world.getUID(), ignored -> create(world));
    }

    private List<Treasure> create(World world) {
        Random random = new Random(world.getSeed() ^ 0xC0FFEE1234ABL);
        List<Treasure> result = new ArrayList<>();
        for (IslandLayout.Island island : layout.get(world.getSeed())) {
            int amount = minPerIsland + (maxPerIsland == minPerIsland ? 0 : random.nextInt(maxPerIsland - minPerIsland + 1));
            for (int i = 0; i < amount; i++) {
                for (int attempt = 0; attempt < 80; attempt++) {
                    double angle = random.nextDouble() * Math.PI * 2.0D;
                    double distance = island.radius() * (0.28D + random.nextDouble() * 0.48D);
                    int x = island.x() + (int) Math.round(Math.cos(angle) * distance);
                    int z = island.z() + (int) Math.round(Math.sin(angle) * distance);
                    boolean near = result.stream().noneMatch(t -> {
                        long dx = (long) t.x - x, dz = (long) t.z - z;
                        return dx * dx + dz * dz < 32L * 32L;
                    });
                    if (near) { result.add(new Treasure(x, z, island)); break; }
                }
            }
        }
        return List.copyOf(result);
    }

    public boolean populateChunk(World world, int chunkX, int chunkZ) {
        boolean changed = false;
        for (Treasure treasure : treasures(world)) {
            if ((treasure.x() >> 4) != chunkX || (treasure.z() >> 4) != chunkZ) continue;
            String key = world.getUID() + ":" + treasure.x() + ":" + treasure.z();
            if (!generated.add(key)) continue;
            int surface = world.getHighestBlockYAt(treasure.x(), treasure.z());
            if (surface <= seaLevel + 1) continue;
            int y = Math.max(world.getMinHeight() + 2, surface - 2);
            world.getBlockAt(treasure.x(), y, treasure.z()).setType(Material.BARREL, false);
            world.getBlockAt(treasure.x(), y + 1, treasure.z()).setType(Material.DIRT, false);
            fill((Barrel) world.getBlockAt(treasure.x(), y, treasure.z()).getState(), world.getSeed(), treasure);
            changed = true;
        }
        return changed;
    }

    private void fill(Barrel barrel, long seed, Treasure treasure) {
        Inventory inv = barrel.getInventory();
        Random r = new Random(seed ^ ((long) treasure.x() * 73428767L) ^ ((long) treasure.z() * 912931L));
        inv.addItem(new ItemStack(Material.EMERALD, 3 + r.nextInt(6)));
        inv.addItem(new ItemStack(Material.GOLD_INGOT, 2 + r.nextInt(5)));
        inv.addItem(new ItemStack(Material.DIAMOND, 1 + r.nextInt(3)));
        if (r.nextBoolean()) inv.addItem(new ItemStack(Material.ENDER_PEARL, 1 + r.nextInt(2)));
        if (r.nextInt(100) < 35) inv.addItem(new ItemStack(Material.ENCHANTED_GOLDEN_APPLE));
        if (r.nextInt(100) < 55) inv.addItem(new ItemStack(Material.HEART_OF_THE_SEA));
        barrel.update(true, false);
    }

    public ItemStack createTreasureMap(World world, Treasure treasure) {
        MapView view = world.getServer().createMap(world);
        view.setCenterX(treasure.x());
        view.setCenterZ(treasure.z());
        view.setScale(MapView.Scale.CLOSEST);
        view.setTrackingPosition(true);
        view.setUnlimitedTracking(false);
        view.setLocked(true);
        ItemStack map = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) map.getItemMeta();
        meta.setMapView(view);
        meta.setDisplayName("§6Карта к затерянному кладу");
        meta.setLore(List.of("§7Остров: §f" + treasure.island().type(), "§7Ищите в районе отметки", "§8Координаты: " + treasure.x() + ", " + treasure.z()));
        map.setItemMeta(meta);
        return map;
    }

    public List<Treasure> forIsland(World world, IslandLayout.Island island) {
        return treasures(world).stream().filter(t -> t.island().equals(island)).toList();
    }
}
