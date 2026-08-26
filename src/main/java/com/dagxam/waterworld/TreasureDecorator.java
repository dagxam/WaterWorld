package com.dagxam.waterworld;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.EntityType;
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

/** Спрятанные карты сокровищ на островах и соответствующие бочки-сокровища. */
public final class TreasureDecorator {
    private final JavaPlugin plugin;
    private final NamespacedKey treasureBarrelKey;
    private final NamespacedKey treasureMapKey;
    private final IslandLayout layout;
    private final int seaLevel;

    public TreasureDecorator(JavaPlugin plugin, int seaLevel, IslandLayout layout) {
        this.plugin = plugin;
        this.seaLevel = seaLevel;
        this.layout = layout;
        this.treasureBarrelKey = new NamespacedKey(plugin, "treasure-barrel-v1");
        this.treasureMapKey = new NamespacedKey(plugin, "treasure-map-v1");
    }

    /** Детерминированно создаёт несколько цепочек карта -> бочка на всех зелёных островах. */
    public void decorate(World world, int chunkX, int chunkZ) {
        List<IslandLayout.Island> islands = layout.get(world.getSeed());
        Random random = new Random(world.getSeed()
                ^ ((long) chunkX * 341873128712L)
                ^ ((long) chunkZ * 132897987541L)
                ^ 0x5452454153555245L);

        for (IslandLayout.Island island : islands) {
            if (!isIslandChunk(chunkX, chunkZ, island)) continue;

            // Не размещаем больше одной новой цепочки в каждом подходящем чанке.
            if (random.nextInt(100) >= 6) continue;
            createTreasureChain(world, island, random);
        }
    }

    private boolean isIslandChunk(int chunkX, int chunkZ, IslandLayout.Island island) {
        int minX = chunkX * 16;
        int minZ = chunkZ * 16;
        int maxX = minX + 15;
        int maxZ = minZ + 15;
        int r = island.radius() + 4;
        int cx = island.x();
        int cz = island.z();
        return cx >= minX - r && cx <= maxX + r && cz >= minZ - r && cz <= maxZ + r;
    }

    private void createTreasureChain(World world, IslandLayout.Island island, Random random) {
        LocationPoint mapSpot = findSurfaceSpot(world, island, random);
        if (mapSpot == null) return;

        int maxOffset = Math.max(8, Math.min(45, island.radius() - 5));
        LocationPoint barrelSpot = null;
        for (int attempt = 0; attempt < 24; attempt++) {
            int bx = island.x() + random.nextInt(maxOffset * 2 + 1) - maxOffset;
            int bz = island.z() + random.nextInt(maxOffset * 2 + 1) - maxOffset;
            if ((long) (bx - island.x()) * (bx - island.x()) + (long) (bz - island.z()) * (bz - island.z()) > (long) Math.max(6, island.radius() - 5) * Math.max(6, island.radius() - 5)) continue;
            int by = world.getHighestBlockYAt(bx, bz);
            if (by <= seaLevel || world.getBlockAt(bx, by, bz).getType() != Material.GRASS_BLOCK) continue;
            barrelSpot = new LocationPoint(bx, by, bz);
            break;
        }
        if (barrelSpot == null) return;

        placeHiddenMapChest(world, mapSpot, barrelSpot, island, random);
        placeBuriedBarrel(world, barrelSpot, random);
    }

    private LocationPoint findSurfaceSpot(World world, IslandLayout.Island island, Random random) {
        int maxOffset = Math.max(6, Math.min(35, island.radius() - 6));
        for (int attempt = 0; attempt < 30; attempt++) {
            int x = island.x() + random.nextInt(maxOffset * 2 + 1) - maxOffset;
            int z = island.z() + random.nextInt(maxOffset * 2 + 1) - maxOffset;
            if ((long) (x - island.x()) * (x - island.x()) + (long) (z - island.z()) * (z - island.z()) > (long) Math.max(5, island.radius() - 6) * Math.max(5, island.radius() - 6)) continue;
            int y = world.getHighestBlockYAt(x, z);
            if (y > seaLevel && world.getBlockAt(x, y, z).getType() == Material.GRASS_BLOCK
                    && world.getBlockAt(x, y + 1, z).isEmpty()) return new LocationPoint(x, y, z);
        }
        return null;
    }

    private void placeHiddenMapChest(World world, LocationPoint spot, LocationPoint treasure, IslandLayout.Island island, Random random) {
        Block hidden = world.getBlockAt(spot.x, spot.y + 1, spot.z);
        if (!hidden.isEmpty()) return;
        hidden.setType(Material.CHEST);
        if (!(hidden.getState() instanceof Chest chest)) return;

        ItemStack map = createTreasureMap(world, island, treasure.x, treasure.z, random);
        chest.getInventory().addItem(map);
        if (random.nextBoolean()) chest.getInventory().addItem(new ItemStack(Material.COMPASS));
        if (random.nextBoolean()) chest.getInventory().addItem(new ItemStack(Material.TORCH, 2));
        chest.update();
    }

    private ItemStack createTreasureMap(World world, IslandLayout.Island island, int treasureX, int treasureZ, Random random) {
        MapView view = plugin.getServer().createMap(world);
        view.setScale(MapView.Scale.NORMAL);
        view.setTrackingPosition(false);
        view.setUnlimitedTracking(false);
        view.setCenterX(island.x());
        view.setCenterZ(island.z());
        view.getRenderers().clear();
        view.addRenderer(new TreasureMapRenderer(treasureX, treasureZ, island.x(), island.z()));

        ItemStack item = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) item.getItemMeta();
        if (meta == null) return item;
        meta.setMapView(view);
        meta.setDisplayName("§6§lКарта спрятанного сокровища");
        meta.getPersistentDataContainer().set(treasureMapKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private void placeBuriedBarrel(World world, LocationPoint surface, Random random) {
        int depth = 2 + random.nextInt(4);
        int x = surface.x;
        int y = Math.max(world.getMinHeight() + 2, surface.y - depth);
        int z = surface.z;

        for (int i = surface.y; i > y; i--) {
            if (world.getBlockAt(x, i, z).getType() == Material.GRASS_BLOCK || world.getBlockAt(x, i, z).getType() == Material.DIRT) {
                world.getBlockAt(x, i, z).setType(Material.DIRT);
            }
        }

        Block block = world.getBlockAt(x, y, z);
        block.setType(Material.BARREL);
        if (!(block.getState() instanceof org.bukkit.block.Barrel barrel)) return;
        barrel.getPersistentDataContainer().set(treasureBarrelKey, PersistentDataType.BYTE, (byte) 1);
        fillTreasure(barrel, random);
        barrel.update();

        // Ставим несколько блоков грунта сверху, чтобы бочка действительно была спрятана.
        for (int i = y + 1; i <= surface.y; i++) {
            world.getBlockAt(x, i, z).setType(Material.DIRT);
        }
        if (surface.y > 0) world.getBlockAt(x, surface.y, z).setType(Material.GRASS_BLOCK);
    }

    private void fillTreasure(org.bukkit.block.Barrel barrel, Random random) {
        addChance(barrel, Material.DIAMOND, 0.45, 1 + random.nextInt(2));
        addChance(barrel, Material.EMERALD, 0.80, 2 + random.nextInt(5));
        addChance(barrel, Material.GOLD_INGOT, 1.00, 4 + random.nextInt(8));
        addChance(barrel, Material.IRON_INGOT, 1.00, 5 + random.nextInt(12));
        addChance(barrel, Material.GOLDEN_APPLE, 0.12, 1);
        addChance(barrel, Material.HEART_OF_THE_SEA, 0.08, 1);
        addChance(barrel, Material.ENDER_PEARL, 0.25, 1 + random.nextInt(2));
        addChance(barrel, Material.OBSIDIAN, 0.30, 2 + random.nextInt(5));
        addChance(barrel, Material.EXPERIENCE_BOTTLE, 0.35, 3 + random.nextInt(6));
    }

    private void addChance(org.bukkit.block.Barrel barrel, Material material, double chance, int amount) {
        if (Math.random() <= chance) barrel.getInventory().addItem(new ItemStack(material, amount));
    }

    private static final class LocationPoint {
        private final int x, y, z;
        private LocationPoint(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
    }

    private static final class TreasureMapRenderer extends MapRenderer {
        private final int treasureX, treasureZ, centerX, centerZ;
        private boolean rendered;

        private TreasureMapRenderer(int treasureX, int treasureZ, int centerX, int centerZ) {
            super(false);
            this.treasureX = treasureX;
            this.treasureZ = treasureZ;
            this.centerX = centerX;
            this.centerZ = centerZ;
        }

        @Override
        public void render(MapView map, MapCanvas canvas, org.bukkit.entity.Player player) {
            if (rendered) return;
            rendered = true;
            int px = Math.max(0, Math.min(127, 64 + Math.round((treasureX - centerX) * 1.2f)));
            int pz = Math.max(0, Math.min(127, 64 + Math.round((treasureZ - centerZ) * 1.2f)));
            canvas.setPixel(px, pz, (byte) 10);
            if (px > 0) canvas.setPixel(px - 1, pz, (byte) 10);
            if (px < 127) canvas.setPixel(px + 1, pz, (byte) 10);
            if (pz > 0) canvas.setPixel(px, pz - 1, (byte) 10);
            if (pz < 127) canvas.setPixel(px, pz + 1, (byte) 10);
        }
    }
}
