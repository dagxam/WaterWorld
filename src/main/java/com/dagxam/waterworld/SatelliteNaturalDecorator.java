package com.dagxam.waterworld;

import org.bukkit.Material;
import org.bukkit.World;
import java.util.Random;

/** Дополнительное естественное оформление малых островов. */
public final class SatelliteNaturalDecorator {
    private final int seaLevel, centerX, centerZ, count, distance, minRadius, maxRadius;

    public SatelliteNaturalDecorator(int seaLevel, int centerX, int centerZ, int count, int distance, int minRadius, int maxRadius) {
        this.seaLevel = seaLevel;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.count = Math.min(5, Math.max(0, count));
        this.distance = Math.max(70, distance);
        this.minRadius = Math.max(7, minRadius);
        this.maxRadius = Math.max(minRadius, maxRadius);
    }

    public void decorate(World world, int chunkX, int chunkZ) {
        int minX = chunkX * 16, minZ = chunkZ * 16, maxX = minX + 15, maxZ = minZ + 15;
        for (int i = 0; i < count; i++) {
            double angle = Math.toRadians(-90D + i * 72D);
            int cx = centerX + (int)Math.round(Math.cos(angle) * distance);
            int cz = centerZ + (int)Math.round(Math.sin(angle) * distance);
            Random r = new Random(world.getSeed() ^ ((long)i * 341873128712L));
            int radius = minRadius + r.nextInt(maxRadius - minRadius + 1);
            if (!intersects(cx, cz, radius, minX, minZ, maxX, maxZ)) continue;
            decorateVegetation(world, cx, cz, radius, chunkX, chunkZ, i);
            generateOres(world, cx, cz, radius, chunkX, chunkZ, i);
        }
    }

    private boolean intersects(int cx,int cz,int radius,int minX,int minZ,int maxX,int maxZ){
        int x=Math.max(minX,Math.min(maxX,cx)),z=Math.max(minZ,Math.min(maxZ,cz));
        double dx=x-cx,dz=z-cz; return dx*dx+dz*dz<=((double)radius+4)*((double)radius+4);
    }

    private void decorateVegetation(World w,int cx,int cz,int radius,int chunkX,int chunkZ,int index){
        Random r=new Random(w.getSeed() ^ ((long)chunkX*341873128712L) ^ ((long)chunkZ*132897987541L) ^ ((long)index*98765431L));
        for(int i=0;i<Math.max(14,radius*2);i++){
            int x=cx-radius+r.nextInt(radius*2+1),z=cz-radius+r.nextInt(radius*2+1);
            double dx=x-cx,dz=z-cz;if(dx*dx+dz*dz>(radius-3)*(radius-3))continue;
            int y=w.getHighestBlockYAt(x,z);if(y<=seaLevel||w.getBlockAt(x,y,z).getType()!=Material.GRASS_BLOCK||!w.getBlockAt(x,y+1,z).isEmpty())continue;
            int roll=r.nextInt(100);
            if(roll<25) placeTree(w,x,y+1,z,r); else if(roll<65) placeFlower(w,x,y+1,z,r); else w.getBlockAt(x,y+1,z).setType(Material.GRASS,false);
        }
    }

    private void placeFlower(World w,int x,int y,int z,Random r){
        Material[] f={Material.DANDELION,Material.POPPY,Material.AZURE_BLUET,Material.OXEYE_DAISY,Material.CORNFLOWER,Material.ALLIUM,Material.LILY_OF_THE_VALLEY};
        w.getBlockAt(x,y,z).setType(f[r.nextInt(f.length)],false);
    }

    private void placeTree(World w,int x,int y,int z,Random r){
        Material log,leaves; int t=r.nextInt(100);
        if(t<55){log=Material.OAK_LOG;leaves=Material.OAK_LEAVES;}
        else if(t<82){log=Material.BIRCH_LOG;leaves=Material.BIRCH_LEAVES;}
        else{log=Material.SPRUCE_LOG;leaves=Material.SPRUCE_LEAVES;}
        int h=4+r.nextInt(4);
        for(int i=0;i<h+3;i++)if(!w.getBlockAt(x,y+i,z).isEmpty())return;
        for(int i=0;i<h;i++)w.getBlockAt(x,y+i,z).setType(log,false);
        int top=y+h-1;
        for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)for(int dy=-1;dy<=2;dy++){
            int d=Math.abs(dx)+Math.abs(dz);if(d>=4&&dy!=0)continue;if(dy==2&&d>1)continue;
            if(w.getBlockAt(x+dx,top+dy,z+dz).isEmpty())w.getBlockAt(x+dx,top+dy,z+dz).setType(leaves,false);
        }
    }

    private void generateOres(World w,int cx,int cz,int radius,int chunkX,int chunkZ,int index){
        Random r=new Random(w.getSeed() ^ ((long)chunkX*341873128712L) ^ ((long)chunkZ*132897987541L) ^ ((long)index*0x6A09E667L));
        for(int i=0;i<Math.max(16,radius*2);i++){
            int x=cx-radius+r.nextInt(radius*2+1),z=cz-radius+r.nextInt(radius*2+1);
            double dx=x-cx,dz=z-cz;if(dx*dx+dz*dz>radius*radius)continue;
            int top=w.getHighestBlockYAt(x,z);if(top<=seaLevel+2)continue;
            int y=1+r.nextInt(Math.max(1,top-2)); Material ore=chooseOre(r,y); int size=2+r.nextInt(5);
            for(int v=0;v<size*3;v++){
                int bx=x+r.nextInt(5)-2,by=y+r.nextInt(5)-2,bz=z+r.nextInt(5)-2;if(by<=0||by>=top)continue;
                Material current=w.getBlockAt(bx,by,bz).getType();if(current!=Material.STONE&&current!=Material.DEEPSLATE)continue;
                w.getBlockAt(bx,by,bz).setType(current==Material.DEEPSLATE?deepslate(ore):ore,false);
            }
        }
    }

    private Material chooseOre(Random r,int y){
        int n=r.nextInt(1000);
        if(y<=16&&n<35)return Material.DIAMOND_ORE;
        if(y<=32&&n<110)return Material.REDSTONE_ORE;
        if(y<=40&&n<160)return Material.GOLD_ORE;
        if(n<360)return Material.IRON_ORE;
        if(n<600)return Material.COAL_ORE;
        if(n<760)return Material.COPPER_ORE;
        if(n<830)return Material.LAPIS_ORE;
        return Material.IRON_ORE;
    }

    private Material deepslate(Material m){
        switch(m){
            case COAL_ORE:return Material.DEEPSLATE_COAL_ORE;
            case IRON_ORE:return Material.DEEPSLATE_IRON_ORE;
            case COPPER_ORE:return Material.DEEPSLATE_COPPER_ORE;
            case GOLD_ORE:return Material.DEEPSLATE_GOLD_ORE;
            case REDSTONE_ORE:return Material.DEEPSLATE_REDSTONE_ORE;
            case LAPIS_ORE:return Material.DEEPSLATE_LAPIS_ORE;
            case DIAMOND_ORE:return Material.DEEPSLATE_DIAMOND_ORE;
            default:return m;
        }
    }
}
