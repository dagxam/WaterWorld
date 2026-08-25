package com.dagxam.waterworld;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Barrel;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/** Deterministic buried treasure system with different rarity tiers and real search maps. */
public final class TreasureDecorator {
    public enum Tier { COMMON, RARE, LEGENDARY }
    public record Treasure(int x, int z, IslandLayout.Island island, Tier tier) {}

    private final IslandLayout layout;
    private final int seaLevel, minPerIsland, maxPerIsland;
    private final Map<UUID, List<Treasure>> cache = new HashMap<>();
    private final Set<String> generated = new HashSet<>();
    private final NamespacedKey xKey = new NamespacedKey("waterworld", "treasure_x");
    private final NamespacedKey zKey = new NamespacedKey("waterworld", "treasure_z");
    private final NamespacedKey tierKey = new NamespacedKey("waterworld", "treasure_tier");

    public TreasureDecorator(FileConfiguration config, IslandLayout layout) {
        this.layout = layout;
        seaLevel = config.getInt("sea-level", 63);
        minPerIsland = Math.max(0, config.getInt("treasures.min-per-island", 1));
        maxPerIsland = Math.max(minPerIsland, config.getInt("treasures.max-per-island", 3));
    }

    public List<Treasure> treasures(World world) { return cache.computeIfAbsent(world.getUID(), ignored -> create(world)); }

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
                    if (near) { result.add(new Treasure(x, z, island, rollTier(random))); break; }
                }
            }
        }
        return List.copyOf(result);
    }

    private Tier rollTier(Random random) {
        int roll = random.nextInt(100);
        return roll < 4 ? Tier.LEGENDARY : roll < 28 ? Tier.RARE : Tier.COMMON;
    }

    public boolean populateChunk(World world, int chunkX, int chunkZ) {
        boolean changed = false;
        for (Treasure treasure : treasures(world)) {
            if ((treasure.x() >> 4) != chunkX || (treasure.z() >> 4) != chunkZ) continue;
            String key = world.getUID() + ":" + treasure.x() + ":" + treasure.z();
            if (!generated.add(key)) continue;
            int surface = world.getHighestBlockYAt(treasure.x(), treasure.z());
            if (surface <= seaLevel + 1) continue;
            int y = Math.max(world.getMinHeight() + 2, surface - (2 + Math.floorMod(treasure.x() ^ treasure.z(), 5)));
            world.getBlockAt(treasure.x(), y, treasure.z()).setType(Material.BARREL, false);
            for (int cover = y + 1; cover <= surface; cover++) world.getBlockAt(treasure.x(), cover, treasure.z()).setType(Material.DIRT, false);
            fill((Barrel) world.getBlockAt(treasure.x(), y, treasure.z()).getState(), world.getSeed(), treasure);
            changed = true;
        }
        return changed;
    }

    private void fill(Barrel barrel, long seed, Treasure treasure) {
        Inventory inv = barrel.getInventory();
        Random r = new Random(seed ^ ((long) treasure.x() * 73428767L) ^ ((long) treasure.z() * 912931L));
        inv.addItem(new ItemStack(Material.EMERALD, 2 + r.nextInt(6)));
        inv.addItem(new ItemStack(Material.GOLD_INGOT, 2 + r.nextInt(5)));
        switch (treasure.tier()) {
            case COMMON -> {
                inv.addItem(new ItemStack(Material.IRON_INGOT, 4 + r.nextInt(8)));
                if (r.nextBoolean()) inv.addItem(new ItemStack(Material.EMERALD, 3 + r.nextInt(5)));
            }
            case RARE -> {
                inv.addItem(new ItemStack(Material.DIAMOND, 2 + r.nextInt(4)));
                inv.addItem(new ItemStack(Material.ENDER_PEARL, 1 + r.nextInt(3)));
                if (r.nextBoolean()) inv.addItem(new ItemStack(Material.HEART_OF_THE_SEA));
            }
            case LEGENDARY -> {
                inv.addItem(new ItemStack(Material.DIAMOND, 5 + r.nextInt(5)));
                inv.addItem(new ItemStack(Material.NETHERITE_SCRAP, 1 + r.nextInt(2)));
                inv.addItem(new ItemStack(Material.ENCHANTED_GOLDEN_APPLE));
                inv.addItem(new ItemStack(Material.HEART_OF_THE_SEA));
            }
        }
        barrel.update(true, false);
    }

    public ItemStack createTreasureMap(World world, Treasure treasure) {
        MapView view = Bukkit.createMap(world);
        view.setCenterX(treasure.x());
        view.setCenterZ(treasure.z());
        view.setScale(treasure.tier() == Tier.LEGENDARY ? MapView.Scale.NORMAL : MapView.Scale.CLOSE);
        view.setTrackingPosition(true);
        view.setUnlimitedTracking(false);
        view.setLocked(false);
        view.getCursors().clear();
        ItemStack map = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) map.getItemMeta();
        meta.setMapView(view);
        meta.setDisplayName(title(treasure.tier()));
        meta.setLore(lore(treasure));
        meta.getPersistentDataContainer().set(xKey, PersistentDataType.INTEGER, treasure.x());
        meta.getPersistentDataContainer().set(zKey, PersistentDataType.INTEGER, treasure.z());
        meta.getPersistentDataContainer().set(tierKey, PersistentDataType.STRING, treasure.tier().name());
        map.setItemMeta(meta);
        return map;
    }

    private String title(Tier tier) {
        return switch (tier) {
            case COMMON -> "§aКарта забытого клада";
            case RARE -> "§9Карта древнего сокровища";
            case LEGENDARY -> "§6§lЛегендарная карта сокровищ";
        };
    }

    private List<String> lore(Treasure treasure) {
        String type = switch (treasure.island().type()) {
            case MAIN -> "Главный остров";
            case FOREST -> "Лесной остров";
            case ROCKY -> "Каменистый остров";
            case TROPICAL -> "Тропический остров";
        };
        String clue = switch (treasure.tier()) {
            case COMMON -> "Ищите под землёй возле отмеченной области";
            case RARE -> "Сокровище глубоко скрыто в земле";
            case LEGENDARY -> "Легендарный клад ждёт того, кто найдёт путь";
        };
        return List.of("§7Остров: §f" + type, "§7" + clue, "§8Точные координаты скрыты");
    }

    public List<Treasure> forIsland(World world, IslandLayout.Island island) {
        return treasures(world).stream().filter(t -> t.island().equals(island)).toList();
    }
}
