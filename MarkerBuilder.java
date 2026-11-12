package br.nobrevilas;

import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/** Farol da Vila: plataforma, 4 lanternas, beacon COM piramide (3x3 de blocos de metal) + vidro colorido + placa com o nome. */
final class MarkerBuilder {
    private MarkerBuilder() {}

    static int surf(ServerLevel l, int x, int z) {
        return l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
    }

    static boolean natural(BlockState s) {
        return s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(BlockTags.BASE_STONE_OVERWORLD)
            || s.is(BlockTags.TERRACOTTA) || s.is(Blocks.DIRT_PATH) || s.is(Blocks.GRAVEL)
            || s.is(Blocks.SNOW) || s.is(Blocks.SNOW_BLOCK);
    }

    /** Area (2hx+1)x(2hz+1) livre (so terreno natural) e quase plana. */
    static boolean siteOk(ServerLevel l, int cx, int cz, int hx, int hz, int tol) {
        if (!l.hasChunkAt(new BlockPos(cx, 64, cz))) return false;
        int y0 = surf(l, cx, cz);
        for (int dx = -hx; dx <= hx; dx++) {
            for (int dz = -hz; dz <= hz; dz++) {
                int x = cx + dx, z = cz + dz;
                if (!l.hasChunkAt(new BlockPos(x, 64, z))) return false;
                int y = surf(l, x, z);
                if (Math.abs(y - y0) > tol) return false;
                if (!natural(l.getBlockState(new BlockPos(x, y, z)))) return false;
            }
        }
        return true;
    }

    static BlockPos findSite(ServerLevel l, int vx, int vz, int maxR, int hx, int hz, int tol) {
        if (siteOk(l, vx, vz, hx, hz, tol)) return new BlockPos(vx, surf(l, vx, vz), vz);
        for (int r = 4; r <= maxR; r += 4) {
            int steps = Math.max(8, r / 2);
            for (int i = 0; i < steps; i++) {
                double a = 2 * Math.PI * i / steps;
                int x = vx + (int) Math.round(Math.cos(a) * r);
                int z = vz + (int) Math.round(Math.sin(a) * r);
                if (siteOk(l, x, z, hx, hz, tol)) return new BlockPos(x, surf(l, x, z), z);
            }
        }
        return null;
    }

    private static void set(ServerLevel l, int x, int y, int z, BlockState s) {
        l.setBlock(new BlockPos(x, y, z), s, 3);
    }

    static void runCmd(ServerLevel l, String cmd) {
        try {
            var server = l.getServer();
            var src = server.createCommandSourceStack().withLevel(l).withSuppressedOutput();
            server.getCommands().performPrefixedCommand(src, cmd);
        } catch (Exception e) {
            NobreVilas.LOG.warn("Comando falhou: {}", cmd);
        }
    }

    /** Se o farol ja existe (reconstrucao) reaproveita o mesmo local. Retorna o centro (y = piso). */
    static BlockPos build(ServerLevel l, VillageData v) {
        BlockPos site = v.markerBuilt ? new BlockPos(v.mx, v.my, v.mz)
            : findSite(l, v.x, v.z, Math.max(8, v.radius - 14), 3, 3, 1);
        int cx = site != null ? site.getX() : v.x;
        int cz = site != null ? site.getZ() : v.z;
        int y0 = site != null ? site.getY() : surf(l, cx, cz);
        boolean port = v.isPort();

        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState bricks = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState inner = (port ? Blocks.PRISMARINE_BRICKS : Blocks.POLISHED_ANDESITE).defaultBlockState();

        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                int x = cx + dx, z = cz + dz;
                int top = surf(l, x, z);
                for (int y = top + 1; y < y0; y++) set(l, x, y, z, bricks);
                int m = Math.max(Math.abs(dx), Math.abs(dz));
                set(l, x, y0, z, m == 3 ? bricks : inner);
                for (int y = y0 + 1; y <= y0 + 6; y++) {
                    if (!l.getBlockState(new BlockPos(x, y, z)).isAir()) set(l, x, y, z, air);
                }
            }
        }

        // piramide nivel 1 (3x3) logo abaixo do beacon: sem ela NAO ha feixe
        String base = NobreVilas.manager() != null ? NobreVilas.manager().cfg().beaconBase : "minecraft:iron_block";
        runCmd(l, "fill " + (cx - 1) + " " + (y0 - 1) + " " + (cz - 1) + " " + (cx + 1) + " " + (y0 - 1) + " " + (cz + 1) + " " + base);
        set(l, cx, y0, cz, Blocks.BEACON.defaultBlockState());

        // vidro colorido por cima (define a cor do feixe)
        String gp = cx + " " + (y0 + 1) + " " + cz;
        runCmd(l, "setblock " + gp + " minecraft:glass");
        runCmd(l, "setblock " + gp + (port ? " minecraft:cyan_stained_glass" : " minecraft:orange_stained_glass"));

        // pilares com lanternas
        BlockState lantern = (port ? Blocks.SOUL_LANTERN : Blocks.LANTERN).defaultBlockState();
        for (int sx : new int[]{-3, 3}) {
            for (int sz : new int[]{-3, 3}) {
                set(l, cx + sx, y0 + 1, cz + sz, bricks);
                set(l, cx + sx, y0 + 2, cz + sz, bricks);
                set(l, cx + sx, y0 + 3, cz + sz, lantern);
            }
        }

        // feixe livre ate o ceu: remove folhas, troncos e neve acima
        int topAll = l.getHeight(Heightmap.Types.WORLD_SURFACE, cx, cz);
        for (int y = y0 + 2; y <= topAll; y++) {
            BlockState s = l.getBlockState(new BlockPos(cx, y, cz));
            if (s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS) || s.is(Blocks.SNOW)) set(l, cx, y, cz, air);
        }

        spawnLabel(l, v, cx, y0 + 7, cz);
        return new BlockPos(cx, y0, cz);
    }

    private static String clean(String s) { return s.replace("\"", "").replace("\\", ""); }

    private static void spawnLabel(ServerLevel l, VillageData v, int x, int y, int z) {
        runCmd(l, "kill @e[type=minecraft:text_display,tag=nv_" + v.id + "]");   // evita placas duplicadas ao reconstruir
        String color = v.isPort() ? "aqua" : "gold";
        String pos = String.format(Locale.ROOT, "%.1f %d %.1f", x + 0.5, y, z + 0.5);
        String text = "[{text:\"" + clean(v.name) + "\",color:\"" + color + "\",bold:true},"
            + "{text:\"\\n" + v.typeLabel() + "\",color:\"yellow\",bold:false},"
            + "{text:\"\\n" + clean(v.county) + "\",color:\"gray\",bold:false}]";
        runCmd(l, "summon minecraft:text_display " + pos
            + " {Tags:[\"nobrevilas\",\"nv_" + v.id + "\"],billboard:\"center\",view_range:8.0f,shadow:1b,alignment:\"center\","
            + "text:" + text + ","
            + "transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[3f,3f,3f]}}");
    }
}
