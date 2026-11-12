package br.nobrevilas;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Mansao do governante (residencia oficial): salao, trono, quartos, torres, patio com jardim e bandeiras. Usa comandos fill/setblock. */
final class ManorBuilder {
    private ManorBuilder() {}

    static final int HX = 7, HZ = 5;

    static String wallFor(int tier) {
        return switch (Math.max(1, Math.min(10, tier))) {
            case 1 -> "minecraft:spruce_planks";
            case 2 -> "minecraft:dark_oak_planks";
            case 3 -> "minecraft:cobblestone";
            case 4, 5 -> "minecraft:stone_bricks";
            case 6 -> "minecraft:deepslate_bricks";
            case 7 -> "minecraft:deepslate_tiles";
            case 8 -> "minecraft:polished_blackstone_bricks";
            case 9 -> "minecraft:red_nether_bricks";
            default -> "minecraft:quartz_bricks";
        };
    }

    static void fill(ServerLevel l, int x1, int y1, int z1, int x2, int y2, int z2, String b) {
        MarkerBuilder.runCmd(l, "fill " + x1 + " " + y1 + " " + z1 + " " + x2 + " " + y2 + " " + z2 + " " + b);
    }

    static void set(ServerLevel l, int x, int y, int z, String b) {
        MarkerBuilder.runCmd(l, "setblock " + x + " " + y + " " + z + " " + b);
    }

    static boolean build(ServerLevel l, VillageData v, int tier) {
        BlockPos site = MarkerBuilder.findSite(l, v.x, v.z, Math.max(8, v.radius - 18), HX + 2, HZ + 5, 2);
        if (site == null) {
            NobreVilas.LOG.warn("Sem terreno livre para a mansao de {}; o governante ficara no farol.", v.name);
            return false;
        }
        int x0 = site.getX(), y0 = site.getY(), cz = site.getZ();
        int z0 = cz - 3;                                   // corpo da mansao; patio ao sul
        String wall = wallFor(tier);
        String floor = tier <= 1 ? "minecraft:oak_planks" : "minecraft:spruce_planks";

        // terraplanagem
        fill(l, x0 - HX - 2, y0 + 1, z0 - HZ - 2, x0 + HX + 2, y0 + 14, cz + 8, "minecraft:air");
        fill(l, x0 - HX - 2, y0 - 5, z0 - HZ - 2, x0 + HX + 2, y0 - 1, cz + 8, "minecraft:stone_bricks replace minecraft:air");
        fill(l, x0 - HX - 2, y0 - 5, z0 - HZ - 2, x0 + HX + 2, y0 - 1, cz + 8, "minecraft:stone_bricks replace minecraft:water");

        // corpo
        fill(l, x0 - HX, y0, z0 - HZ, x0 + HX, y0 + 6, z0 + HZ, wall + " hollow");
        fill(l, x0 - HX + 1, y0, z0 - HZ + 1, x0 + HX - 1, y0, z0 + HZ - 1, floor);
        fill(l, x0 - 1, y0 + 1, z0 + HZ, x0 + 1, y0 + 3, z0 + HZ, "minecraft:air");            // entrada
        for (int xo : new int[]{-5, -3, 3, 5}) {
            set(l, x0 + xo, y0 + 3, z0 + HZ, "minecraft:glass_pane");
            set(l, x0 + xo, y0 + 3, z0 - HZ, "minecraft:glass_pane");
        }
        for (int zo : new int[]{-2, 2}) {
            set(l, x0 - HX, y0 + 3, z0 + zo, "minecraft:glass_pane");
            set(l, x0 + HX, y0 + 3, z0 + zo, "minecraft:glass_pane");
        }

        // salao do trono e salas
        fill(l, x0 - 1, y0 + 1, z0 - HZ + 1, x0 + 1, y0 + 1, z0 + HZ - 1, "minecraft:red_carpet");
        set(l, x0, y0 + 1, z0 - HZ + 1, "minecraft:dark_oak_stairs[facing=south]");
        set(l, x0, y0 + 3, z0 - HZ + 1, "minecraft:red_wall_banner[facing=south]");
        fill(l, x0 + HX - 1, y0 + 1, z0 - HZ + 1, x0 + HX - 1, y0 + 3, z0 - 2, "minecraft:bookshelf");
        set(l, x0 + HX - 1, y0 + 1, z0 + 1, "minecraft:crafting_table");
        set(l, x0 + HX - 1, y0 + 1, z0 + 2, "minecraft:chest[facing=west]");
        set(l, x0 - HX + 2, y0 + 1, z0 - HZ + 2, "minecraft:red_bed[facing=west,part=foot]");
        set(l, x0 - HX + 1, y0 + 1, z0 - HZ + 2, "minecraft:red_bed[facing=west,part=head]");
        for (int xo : new int[]{-3, 0, 3}) set(l, x0 + xo, y0 + 5, z0, "minecraft:lantern[hanging=true]");

        // ameias e bandeiras de entrada
        fill(l, x0 - HX, y0 + 7, z0 - HZ, x0 + HX, y0 + 7, z0 - HZ, wall);
        fill(l, x0 - HX, y0 + 7, z0 + HZ, x0 + HX, y0 + 7, z0 + HZ, wall);
        fill(l, x0 - HX, y0 + 7, z0 - HZ, x0 - HX, y0 + 7, z0 + HZ, wall);
        fill(l, x0 + HX, y0 + 7, z0 - HZ, x0 + HX, y0 + 7, z0 + HZ, wall);
        set(l, x0 - 3, y0 + 3, z0 + HZ + 1, "minecraft:red_wall_banner[facing=south]");
        set(l, x0 + 3, y0 + 3, z0 + HZ + 1, "minecraft:red_wall_banner[facing=south]");

        // torres (condes para cima)
        if (tier >= 4) {
            int th = 8 + tier / 2;
            for (int sx : new int[]{-1, 1}) {
                for (int sz : new int[]{-1, 1}) {
                    int tx = x0 + sx * HX, tz = z0 + sz * HZ;
                    fill(l, tx - 1, y0, tz - 1, tx + 1, y0 + th, tz + 1, wall + " hollow");
                    set(l, tx, y0 + th + 1, tz, "minecraft:lantern");
                }
            }
        }

        // patio e jardim
        int zf = z0 + HZ + 1, zb = z0 + HZ + 5;
        fill(l, x0 - HX, y0, zf, x0 + HX, y0, zb, "minecraft:grass_block");
        fill(l, x0 - 1, y0, zf, x0 + 1, y0, zb, "minecraft:dirt_path");
        fill(l, x0 - HX, y0 + 1, zb, x0 - 2, y0 + 1, zb, "minecraft:oak_leaves[persistent=true]");
        fill(l, x0 + 2, y0 + 1, zb, x0 + HX, y0 + 1, zb, "minecraft:oak_leaves[persistent=true]");
        fill(l, x0 - HX, y0 + 1, zf, x0 - HX, y0 + 1, zb, "minecraft:oak_leaves[persistent=true]");
        fill(l, x0 + HX, y0 + 1, zf, x0 + HX, y0 + 1, zb, "minecraft:oak_leaves[persistent=true]");
        String[] flowers = {"minecraft:poppy", "minecraft:dandelion", "minecraft:cornflower", "minecraft:azure_bluet"};
        for (int i = 0; i < 8; i++) set(l, x0 + (i % 2 == 0 ? -1 : 1) * (3 + i / 2), y0 + 1, zf + 1 + (i % 3), flowers[i % 4]);
        for (int sx : new int[]{-1, 1}) {
            set(l, x0 + sx * 2, y0 + 1, zb, "minecraft:cobblestone_wall");
            set(l, x0 + sx * 2, y0 + 2, zb, "minecraft:lantern");
        }

        v.rx = x0; v.ry = y0 + 1; v.rz = z0;
        v.manorBuilt = true;
        return true;
    }
}
