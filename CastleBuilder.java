package br.nobrevilas;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Castelo da capital (rei) e do imperador: grande salao do trono, torre de menagem de 27 blocos, 4 torres de canto,
 * portao em arco, salao com mesas, tapetes, bandeiras, lustres e patio com fonte e jardim. Tudo via fill/setblock.
 */
final class CastleBuilder {
    private CastleBuilder() {}

    private static void fill(ServerLevel l, int x1, int y1, int z1, int x2, int y2, int z2, String b) { ManorBuilder.fill(l, x1, y1, z1, x2, y2, z2, b); }
    private static void set(ServerLevel l, int x, int y, int z, String b) { ManorBuilder.set(l, x, y, z, b); }

    private static void parapet(ServerLevel l, int x1, int z1, int x2, int z2, int y, String wall) {
        fill(l, x1, y, z1, x2, y, z1, wall);
        fill(l, x1, y, z2, x2, y, z2, wall);
        fill(l, x1, y, z1, x1, y, z2, wall);
        fill(l, x2, y, z1, x2, y, z2, wall);
    }

    static boolean build(ServerLevel l, VillageData v, int tier) {
        int maxR = Math.max(8, v.radius - 24);
        BlockPos site = MarkerBuilder.findSite(l, v.x, v.z, maxR, 16, 19, 4);
        if (site == null) site = MarkerBuilder.findSite(l, v.x, v.z, maxR, 16, 19, 8);
        if (site == null) {
            NobreVilas.LOG.warn("Sem terreno livre para o castelo de {}.", v.name);
            return false;
        }
        int x0 = site.getX(), y0 = site.getY(), z0 = site.getZ() - 2;
        boolean emp = tier >= Ranks.EMPEROR;
        String wall = ManorBuilder.wallFor(tier);
        String floor = "minecraft:dark_oak_planks";
        String carpet = emp ? "minecraft:purple_carpet" : "minecraft:red_carpet";
        String wb = emp ? "minecraft:purple_wall_banner" : "minecraft:red_wall_banner";
        String sb = emp ? "minecraft:purple_banner" : "minecraft:red_banner";
        String air = "minecraft:air";
        int xa = x0 - 16, xb = x0 + 16, za = z0 - 17, zb = z0 + 20;

        // terraplanagem e fundacao
        for (int ys = y0 + 1; ys <= y0 + 37; ys += 12) fill(l, xa, ys, za, xb, Math.min(ys + 11, y0 + 37), zb, air);
        fill(l, xa, y0 - 5, za, xb, y0 - 1, zb, "minecraft:stone_bricks replace minecraft:air");
        fill(l, xa, y0 - 5, za, xb, y0 - 1, zb, "minecraft:stone_bricks replace minecraft:water");

        // grande salao
        fill(l, x0 - 12, y0, z0 - 8, x0 + 12, y0 + 12, z0 + 8, wall + " hollow");
        fill(l, x0 - 11, y0, z0 - 7, x0 + 11, y0, z0 + 7, floor);
        // torre de menagem (atras) com 3 andares e escada
        fill(l, x0 - 4, y0, z0 - 17, x0 + 4, y0 + 26, z0 - 8, wall + " hollow");
        for (int fy : new int[]{7, 14, 21}) fill(l, x0 - 3, y0 + fy, z0 - 16, x0 + 3, y0 + fy, z0 - 9, floor);
        fill(l, x0 + 3, y0 + 1, z0 - 9, x0 + 3, y0 + 25, z0 - 9, "minecraft:ladder[facing=west]");
        fill(l, x0 - 1, y0 + 1, z0 - 8, x0 + 1, y0 + 4, z0 - 8, air);                       // passagem salao -> torre
        parapet(l, x0 - 4, z0 - 17, x0 + 4, z0 - 8, y0 + 27, wall);
        fill(l, x0, y0 + 27, z0 - 12, x0, y0 + 31, z0 - 12, "minecraft:spruce_fence");
        set(l, x0, y0 + 32, z0 - 12, sb + "[rotation=0]");

        // portao em arco
        fill(l, x0 - 2, y0 + 1, z0 + 8, x0 + 2, y0 + 4, z0 + 8, air);
        fill(l, x0 - 1, y0 + 5, z0 + 8, x0 + 1, y0 + 5, z0 + 8, air);
        set(l, x0, y0 + 6, z0 + 8, air);
        parapet(l, x0 - 12, z0 - 8, x0 + 12, z0 + 8, y0 + 13, wall);

        // torres de canto
        for (int sx : new int[]{-1, 1}) {
            for (int sz : new int[]{-1, 1}) {
                int tx = x0 + sx * 12, tz = z0 + sz * 8;
                fill(l, tx - 2, y0, tz - 2, tx + 2, y0 + 20, tz + 2, wall + " hollow");
                parapet(l, tx - 2, tz - 2, tx + 2, tz + 2, y0 + 21, wall);
                set(l, tx, y0 + 22, tz, "minecraft:lantern");
                set(l, tx, y0 + 10, tz + sz * 2, "minecraft:glass_pane");
                set(l, tx, y0 + 16, tz + sz * 2, "minecraft:glass_pane");
            }
        }

        // janelas do salao
        for (int xo : new int[]{-9, -5, 5, 9}) {
            fill(l, x0 + xo, y0 + 4, z0 + 8, x0 + xo, y0 + 9, z0 + 8, "minecraft:glass_pane");
            fill(l, x0 + xo, y0 + 4, z0 - 8, x0 + xo, y0 + 9, z0 - 8, "minecraft:glass_pane");
        }
        for (int zo : new int[]{-4, 0, 4}) {
            fill(l, x0 - 12, y0 + 4, z0 + zo, x0 - 12, y0 + 9, z0 + zo, "minecraft:glass_pane");
            fill(l, x0 + 12, y0 + 4, z0 + zo, x0 + 12, y0 + 9, z0 + zo, "minecraft:glass_pane");
        }

        // salao do trono
        fill(l, x0 - 2, y0 + 1, z0 - 7, x0 + 2, y0 + 1, z0 + 7, carpet);
        fill(l, x0 - 4, y0 + 1, z0 - 7, x0 + 4, y0 + 1, z0 - 5, "minecraft:quartz_block");
        fill(l, x0 - 2, y0 + 2, z0 - 7, x0 + 2, y0 + 2, z0 - 7, "minecraft:gold_block");
        fill(l, x0, y0 + 3, z0 - 7, x0, y0 + 4, z0 - 7, "minecraft:gold_block");
        set(l, x0, y0 + 2, z0 - 6, "minecraft:quartz_stairs[facing=south]");
        set(l, x0 - 4, y0 + 4, z0 - 7, wb + "[facing=south]");
        set(l, x0 + 4, y0 + 4, z0 - 7, wb + "[facing=south]");
        for (int zo : new int[]{-6, -2, 2, 6}) {
            set(l, x0 - 11, y0 + 5, z0 + zo, wb + "[facing=east]");
            set(l, x0 + 11, y0 + 5, z0 + zo, wb + "[facing=west]");
        }
        // mesas e bancos
        for (int sg : new int[]{-1, 1}) {
            fill(l, x0 + sg * 8, y0 + 1, z0 - 3, x0 + sg * 6, y0 + 1, z0 + 5, "minecraft:dark_oak_slab");
            fill(l, x0 + sg * 9, y0 + 1, z0 - 3, x0 + sg * 9, y0 + 1, z0 + 5, "minecraft:spruce_stairs[facing=" + (sg < 0 ? "east" : "west") + "]");
            fill(l, x0 + sg * 5, y0 + 1, z0 - 3, x0 + sg * 5, y0 + 1, z0 + 5, "minecraft:spruce_stairs[facing=" + (sg < 0 ? "west" : "east") + "]");
        }
        // lustres
        for (int xo : new int[]{-7, 0, 7}) for (int zo : new int[]{-5, 0, 5}) set(l, x0 + xo, y0 + 11, z0 + zo, "minecraft:lantern[hanging=true]");

        // patio, caminho, fonte, cercas vivas, mastros
        fill(l, xa, y0, z0 + 9, xb, y0, zb, "minecraft:grass_block");
        fill(l, x0 - 2, y0, z0 + 9, x0 + 2, y0, zb, "minecraft:dirt_path");
        fill(l, x0 - 2, y0, z0 + 12, x0 + 2, y0 + 1, z0 + 16, "minecraft:stone_bricks hollow");
        fill(l, x0 - 1, y0 + 1, z0 + 13, x0 + 1, y0 + 1, z0 + 15, "minecraft:water");
        fill(l, xa, y0 + 1, zb, x0 - 3, y0 + 1, zb, "minecraft:oak_leaves[persistent=true]");
        fill(l, x0 + 3, y0 + 1, zb, xb, y0 + 1, zb, "minecraft:oak_leaves[persistent=true]");
        fill(l, xa, y0 + 1, z0 + 9, xa, y0 + 1, zb, "minecraft:oak_leaves[persistent=true]");
        fill(l, xb, y0 + 1, z0 + 9, xb, y0 + 1, zb, "minecraft:oak_leaves[persistent=true]");
        String[] fl = {"minecraft:poppy", "minecraft:dandelion", "minecraft:cornflower", "minecraft:azure_bluet"};
        for (int i = 0; i < 12; i++) set(l, x0 + (i % 2 == 0 ? -1 : 1) * (5 + i / 2), y0 + 1, z0 + 10 + i % 8, fl[i % 4]);
        for (int sg : new int[]{-1, 1}) {
            fill(l, x0 + sg * 4, y0 + 1, z0 + 10, x0 + sg * 4, y0 + 6, z0 + 10, "minecraft:spruce_fence");
            set(l, x0 + sg * 4, y0 + 7, z0 + 10, sb + "[rotation=0]");
            set(l, x0 + sg * 8, y0 + 1, zb, "minecraft:cobblestone_wall");
            set(l, x0 + sg * 8, y0 + 2, zb, "minecraft:lantern");
        }

        v.rx = x0; v.ry = y0 + 1; v.rz = z0;
        v.manorBuilt = true;
        v.castleBuilt = true;
        return true;
    }
}
