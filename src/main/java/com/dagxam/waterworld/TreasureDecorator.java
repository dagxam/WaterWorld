package com.dagxam.waterworld;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Генерирует на островах небольшую сеть ПОДЗЕМНЫХ сундуков.
 *
 * На каждом острове создаётся от 3 до 7 тайников. Часть сундуков содержит карту,
 * ведущую к другому закопанному сундуку, остальные содержат ценные предметы.
 */
public final class TreasureDecorator {
    private static final long TREASURE_SALT = 0x5452454153555245L;

    private final JavaPlugin plugin;
    private final NamespacedKey treasureChestKey;
    private final NamespacedKey treasureMapKey;
    private final IslandLayout layout;
    private final int seaLevel;

    public TreasureDecorator(JavaPlugin plugin, int seaLevel, IslandLayout layout) {
        this.plugin = plugin;
        this.seaLevel = seaLevel;
        this.layout = layout;
        this.treasureChestKey = new NamespacedKey(plugin, "buried-island-treasure-v2");
        this.treasureMapKey = new NamespacedKey(plugin, "treasure-map-v2");
    }

    /**
     * Вызывается для чанка. Раскладка сундуков заранее вычисляется от seed мира,
     * поэтому каждый сундук создаётся только в своём чанке и не дублируется.
     */
    public void decorate(World world, int chunkX, int chunkZ) {
        for (IslandLayout.Island island : layout.get(world.getSeed())) {
            if (!isNearIslandChunk(chunkX, chunkZ, island)) continue;

            List<TreasureSite> sites = createSites(world, island);
            if (sites.isEmpty()) continue;

            for (TreasureSite site : sites) {
                if (Math.floorDiv(site.x, 16) != chunkX || Math.floorDiv(site.z, 16) != chunkZ) continue;
                placeTreasureChest(world, site, island, sites);
            }
        }
    }

    private boolean isNearIslandChunk(int chunkX, int chunkZ, IslandLayout.Island island) {
        int minX = chunkX * 16 - 8;
        int minZ = chunkZ * 16 - 8;
        int maxX = chunkX * 16 + 23;
        int maxZ = chunkZ * 16 + 23;
        int radius = island.radius() + 4;
        return island.x() >= minX - radius && island.x() <= maxX + radius
                && island.z() >= minZ - radius && island.z() <= maxZ + radius;
    }

    /** От 3 до 7 хорошо разнесённых точек на суше острова. */
    private List<TreasureSite> createSites(World world, IslandLayout.Island island) {
        long salt = TREASURE_SALT
                ^ ((long) island.x() * 341873128712L)
                ^ ((long) island.z() * 132897987541L)
                ^ ((long) island.radius() * 42317861L);
        Random random = new Random(world.getSeed() ^ salt);

        int count = 3 + random.nextInt(5); // 3..7
        int mapCount = count >= 5 ? 2 : 1;
        List<TreasureSite> sites = new ArrayList<>();

        int usableRadius = Math.max(10, island.radius() - 7);
        int minDistance = Math.max(8, Math.min(16, island.radius() / 3));

        for (int index = 0; index < count; index++) {
            TreasureSite site = null;
            for (int attempt = 0; attempt < 80; attempt++) {
                int x = island.x() + random.nextInt(usableRadius * 2 + 1) - usableRadius;
                int z = island.z() + random.nextInt(usableRadius * 2 + 1) - usableRadius;
                long dx = x - island.x();
                long dz = z - island.z();
                if (dx * dx + dz * dz > (long) usableRadius * usableRadius) continue;

                boolean tooClose = false;
                for (TreasureSite other : sites) {
                    long ox = x - other.x;
                    long oz = z - other.z;
                    if (ox * ox + oz * oz < (long) minDistance * minDistance) {
                        tooClose = true;
                        break;
                    }
                }
                if (tooClose) continue;

                int surfaceY = world.getHighestBlockYAt(x, z);
                if (surfaceY <= seaLevel + 1) continue;
                Material surface = world.getBlockAt(x, surfaceY, z).getType();
                if (surface != Material.GRASS_BLOCK && surface != Material.DIRT
                        && surface != Material.COARSE_DIRT && surface != Material.PODZOL) continue;

                int depth = 2 + random.nextInt(4); // 2..5 блоков под поверхностью
                int y = surfaceY - depth;
                if (y <= world.getMinHeight() + 2 || y >= surfaceY - 1) continue;

                boolean mapChest = index < mapCount;
                site = new TreasureSite(x, y, z, surfaceY, mapChest, index);
                break;
            }
            if (site != null) sites.add(site);
        }

        // На маленьком острове может не хватить точек: гарантируем минимум 3 попытками ближе к центру.
        int fallback = 0;
        while (sites.size() < 3 && fallback < 40) {
            int x = island.x() + random.nextInt(Math.max(7, usableRadius)) - Math.max(3, usableRadius / 2);
            int z = island.z() + random.nextInt(Math.max(7, usableRadius)) - Math.max(3, usableRadius / 2);
            int surfaceY = world.getHighestBlockYAt(x, z);
            if (surfaceY > seaLevel + 1) {
                Material surface = world.getBlockAt(x, surfaceY, z).getType();
                if (surface == Material.GRASS_BLOCK || surface == Material.DIRT || surface == Material.COARSE_DIRT) {
                    sites.add(new TreasureSite(x, surfaceY - 3, z, surfaceY, sites.size() < mapCount, sites.size()));
                }
            }
            fallback++;
        }

        return sites;
    }

    private void placeTreasureChest(World world, TreasureSite site, IslandLayout.Island island, List<TreasureSite> sites) {
        Block block = world.getBlockAt(site.x, site.y, site.z);

        // Не перезаписываем уже созданный тайник при повторной загрузке чанка.
        if (block.getType() == Material.CHEST && block.getState() instanceof Chest existing
                && existing.getPersistentDataContainer().has(treasureChestKey, PersistentDataType.BYTE)) {
            return;
        }

        // Если точка оказалась в воде, пещере или слишком близко к поверхности — пропускаем её.
        for (int y = site.y; y <= site.surfaceY; y++) {
            Material type = world.getBlockAt(site.x, y, site.z).getType();
            if (type == Material.WATER || type == Material.LAVA || type == Material.AIR) return;
        }

        block.setType(Material.CHEST, false);
        if (!(block.getState() instanceof Chest chest)) return;

        chest.getPersistentDataContainer().set(treasureChestKey, PersistentDataType.BYTE, (byte) 1);
        chest.getInventory().clear();

        Random random = new Random(world.getSeed()
                ^ ((long) site.x * 341873128712L)
                ^ ((long) site.z * 132897987541L)
                ^ ((long) site.y * 42317861L)
                ^ TREASURE_SALT);

        if (site.mapChest) {
            TreasureSite target = chooseTarget(site, sites, random);
            if (target != null) {
                chest.getInventory().addItem(createTreasureMap(world, site, target));
            }
            addChance(chest, random, Material.COMPASS, 0.70, 1);
            addChance(chest, random, Material.PAPER, 0.50, 2 + random.nextInt(4));
            addChance(chest, random, Material.GOLD_INGOT, 0.55, 2 + random.nextInt(5));
            addChance(chest, random, Material.EMERALD, 0.30, 1 + random.nextInt(3));
        } else {
            fillValuableTreasure(chest, random);
        }

        chest.update(true, false);
    }

    /** Карта всегда ведёт к другому сундуку, а не к самой себе. */
    private TreasureSite chooseTarget(TreasureSite source, List<TreasureSite> sites, Random random) {
        List<TreasureSite> targets = new ArrayList<>();
        for (TreasureSite site : sites) {
            if (site.index != source.index) targets.add(site);
        }
        if (targets.isEmpty()) return null;
        return targets.get(random.nextInt(targets.size()));
    }

    private ItemStack createTreasureMap(World world, TreasureSite source, TreasureSite target) {
        MapView view = plugin.getServer().createMap(world);
        view.setScale(MapView.Scale.NORMAL);
        view.setTrackingPosition(false);
        view.setUnlimitedTracking(false);
        view.setCenterX(source.x);
        view.setCenterZ(source.z);
        view.getRenderers().clear();
        view.addRenderer(new TreasureMapRenderer(source.x, source.z, target.x, target.z));

        ItemStack item = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) item.getItemMeta();
        if (meta == null) return item;
        meta.setMapView(view);
        meta.setDisplayName("§6§lКарта к спрятанному сундуку");
        meta.getPersistentDataContainer().set(treasureMapKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private void fillValuableTreasure(Chest chest, Random random) {
        // Обычные ценности.
        addChance(chest, random, Material.DIAMOND, 0.65, 1 + random.nextInt(4));
        addChance(chest, random, Material.EMERALD, 0.85, 3 + random.nextInt(7));
        addChance(chest, random, Material.GOLD_INGOT, 0.95, 4 + random.nextInt(10));
        addChance(chest, random, Material.IRON_INGOT, 0.85, 6 + random.nextInt(12));
        addChance(chest, random, Material.GOLDEN_APPLE, 0.20, 1);
        addChance(chest, random, Material.ENDER_PEARL, 0.30, 1 + random.nextInt(2));
        addChance(chest, random, Material.EXPERIENCE_BOTTLE, 0.35, 3 + random.nextInt(6));

        // Редкая экипировка.
        addChance(chest, random, Material.DIAMOND_SWORD, 0.16, 1);
        addChance(chest, random, Material.DIAMOND_PICKAXE, 0.14, 1);
        addChance(chest, random, Material.DIAMOND_HELMET, 0.10, 1);
        addChance(chest, random, Material.DIAMOND_CHESTPLATE, 0.08, 1);
        addChance(chest, random, Material.DIAMOND_LEGGINGS, 0.08, 1);
        addChance(chest, random, Material.DIAMOND_BOOTS, 0.10, 1);

        // Очень редкий главный приз — незерит.
        addChance(chest, random, Material.NETHERITE_SWORD, 0.035, 1);
        addChance(chest, random, Material.NETHERITE_PICKAXE, 0.025, 1);
        addChance(chest, random, Material.NETHERITE_HELMET, 0.018, 1);
        addChance(chest, random, Material.NETHERITE_CHESTPLATE, 0.012, 1);
        addChance(chest, random, Material.NETHERITE_LEGGINGS, 0.012, 1);
        addChance(chest, random, Material.NETHERITE_BOOTS, 0.018, 1);
    }

    private void addChance(Chest chest, Random random, Material material, double chance, int amount) {
        if (random.nextDouble() <= chance) {
            chest.getInventory().addItem(new ItemStack(material, amount));
        }
    }

    private static final class TreasureSite {
        private final int x;
        private final int y;
        private final int z;
        private final int surfaceY;
        private final boolean mapChest;
        private final int index;

        private TreasureSite(int x, int y, int z, int surfaceY, boolean mapChest, int index) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.surfaceY = surfaceY;
            this.mapChest = mapChest;
            this.index = index;
        }
    }

    private static final class TreasureMapRenderer extends MapRenderer {
        private final int sourceX;
        private final int sourceZ;
        private final int targetX;
        private final int targetZ;
        private boolean rendered;

        private TreasureMapRenderer(int sourceX, int sourceZ, int targetX, int targetZ) {
            super(false);
            this.sourceX = sourceX;
            this.sourceZ = sourceZ;
            this.targetX = targetX;
            this.targetZ = targetZ;
        }

        @Override
        public void render(MapView map, MapCanvas canvas, org.bukkit.entity.Player player) {
            if (rendered) return;
            rendered = true;

            int px = Math.max(0, Math.min(127, 64 + Math.round((targetX - sourceX) * 1.2f)));
            int pz = Math.max(0, Math.min(127, 64 + Math.round((targetZ - sourceZ) * 1.2f)));
            byte marker = 10;
            canvas.setPixel(px, pz, marker);
            if (px > 0) canvas.setPixel(px - 1, pz, marker);
            if (px < 127) canvas.setPixel(px + 1, pz, marker);
            if (pz > 0) canvas.setPixel(px, pz - 1, marker);
            if (pz < 127) canvas.setPixel(px, pz + 1, marker);
        }
    }
}
