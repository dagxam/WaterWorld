package com.dagxam.waterworld;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkPopulateEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/** Главный класс WaterWorld. */
public final class WaterWorldPlugin extends JavaPlugin implements Listener {
    private WaterGenerator generator;
    private NaturalIslandDecorator decorator;
    private MountainDecorator mountain;
    private CaveDecorator cave;
    private VillageDecorator village;
    private MobDecorator mobs;
    private IslandLayout islandLayout;

    @Override public void onEnable() {
        saveDefaultConfig();
        islandLayout = new IslandLayout(getConfig());
        generator = new WaterGenerator(getConfig());
        if (getConfig().getBoolean("island.enabled", true)) {
            int sea = getConfig().getInt("sea-level",63), cx=getConfig().getInt("island.center-x",0), cz=getConfig().getInt("island.center-z",0), radius=getConfig().getInt("island.radius",100);
            decorator = new NaturalIslandDecorator(sea,cx,cz,radius);
            if(getConfig().getBoolean("island.mountain.enabled",true)){
                int mx=cx+getConfig().getInt("island.mountain.offset-x",0), mz=cz+getConfig().getInt("island.mountain.offset-z",-22), mr=getConfig().getInt("island.mountain.radius",38), peak=getConfig().getInt("island.mountain.peak-height",92);
                mountain=new MountainDecorator(sea,mx,mz,mr,peak,getConfig().getInt("island.mountain.snow-line",120),getConfig().getBoolean("island.mountain.secondary-peaks",false));
                if(getConfig().getBoolean("island.mountain.cave.enabled",true)) cave=new CaveDecorator(mx,mz,mr,sea,peak);
            }
            if(getConfig().getBoolean("village.enabled",true)) village=new VillageDecorator(cx,cz,radius,getConfig().getInt("village.offset-x",0),getConfig().getInt("village.offset-z",55));
            if(getConfig().getBoolean("animals.enabled",true)) mobs=new MobDecorator(this,sea,cx,cz,radius,getConfig().getInt("animals.ocean-radius",280));
        }
        getServer().getPluginManager().registerEvents(this,this);
        if(mobs!=null){getServer().getPluginManager().registerEvents(mobs,this);mobs.start();}
        for(World world:getServer().getWorlds()) scheduleIslandSpawn(world);
        getLogger().info("WaterWorld запущен: дополнительные острова, растительность и животные включены.");
    }
    @Override public ChunkGenerator getDefaultWorldGenerator(String worldName,String id){if(generator==null)generator=new WaterGenerator(getConfig());return generator;}
    @EventHandler public void onWorldLoad(WorldLoadEvent e){if(mobs!=null)mobs.initializeWorld(e.getWorld());scheduleIslandSpawn(e.getWorld());}
    private void scheduleIslandSpawn(World w){if(getConfig().getBoolean("island.enabled",true))getServer().getScheduler().runTask(this,()->setIslandSpawn(w));}
    private void setIslandSpawn(World w){int cx=getConfig().getInt("island.center-x",0),cz=getConfig().getInt("island.center-z",0),r=getConfig().getInt("island.radius",100);w.getChunkAt(cx>>4,cz>>4).load();Location safe=findSafeSpawn(w,cx,cz,Math.min(16,Math.max(4,r/5)));if(safe!=null)w.setSpawnLocation(safe.getBlockX(),safe.getBlockY(),safe.getBlockZ());}
    private Location findSafeSpawn(World w,int cx,int cz,int r){Location best=null;double bd=Double.MAX_VALUE;for(int dx=-r;dx<=r;dx++)for(int dz=-r;dz<=r;dz++){int x=cx+dx,z=cz+dz,y=w.getHighestBlockYAt(x,z);if(w.getBlockAt(x,y,z).getType()!=Material.GRASS_BLOCK||!w.getBlockAt(x,y+1,z).isEmpty()||!w.getBlockAt(x,y+2,z).isEmpty())continue;double d=(double)dx*dx+(double)dz*dz;if(d<bd){bd=d;best=new Location(w,x+.5D,y+1,z+.5D);}}return best;}
    @EventHandler public void onChunkPopulate(ChunkPopulateEvent e){World w=e.getWorld();Chunk c=e.getChunk();int x=c.getX()*16+8,z=c.getZ()*16+8;
        if(mountain!=null)mountain.generate(w,c.getX(),c.getZ()); if(cave!=null)cave.generate(w,c.getX(),c.getZ());
        if(decorator!=null){List<IslandLayout.Island> islands=islandLayout.get(w.getSeed());for(IslandLayout.Island island:islands)if(near(c.getX(),c.getZ(),island.x(),island.z(),island.radius()+16)){new NaturalIslandDecorator(getConfig().getInt("sea-level",63),island.x(),island.z(),island.radius()).decorate(w,c.getX(),c.getZ());break;}}
        if(village!=null&&isVillageTriggerChunk(c.getX(),c.getZ()))village.generate(w); if(mobs!=null)getServer().getScheduler().runTask(this,()->mobs.populate(c));}
    private boolean near(int chunkX,int chunkZ,int cx,int cz,int r){double x=chunkX*16+8-cx,z=chunkZ*16+8-cz;return x*x+z*z<=(double)r*r;}
    private boolean isVillageTriggerChunk(int chunkX,int chunkZ){int cx=getConfig().getInt("island.center-x",0)+getConfig().getInt("village.offset-x",0),cz=getConfig().getInt("island.center-z",0)+getConfig().getInt("village.offset-z",55);return chunkX==(cx>>4)&&chunkZ==(cz>>4);}
}
