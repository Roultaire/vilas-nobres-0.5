package br.nobrevilas;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Muralha quadrada CONTINUA: o topo e suavizado (nunca degrau > 1), cada coluna desce ate o solo
 * (cobre fossos, vales e rios), 4 portoes em arco e torres. O portao principal (sul) tem torres de guarda.
 */
final class WallBuilder {
    private WallBuilder() {}

    record Style(BlockState block, int height, int thick) {}

    static Style style(int tier, boolean capital) {
        Style s = switch (Math.max(1, Math.min(6, tier))) {
            case 1 -> new Style(Blocks.OAK_LOG.defaultBlockState(), 4, 1);
            case 2 -> new Style(Blocks.COBBLESTONE.defaultBlockState(), 5, 1);
            case 3 -> new Style(Blocks.STONE_BRICKS.defaultBlockState(), 6, 1);
            case 4 -> new Style(Blocks.DEEPSLATE_BRICKS.defaultBlockState(), 7, 2);
            case 5 -> new Style(Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), 8, 2);
            default -> new Style(Blocks.QUARTZ_BRICKS.defaultBlockState(), 9, 2);
        };
        return capital ? new Style(s.block(), s.height() + 3, 3) : s;
    }

    private static int surf(ServerLevel l, int x, int z) {
        return l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
    }

    private static void set(ServerLevel l, int x, int y, int z, BlockState s) {
        l.setBlock(new BlockPos(x, y, z), s, 2);
    }

    private static boolean soft(ServerLevel l, int x, int y, int z) {
        BlockState st = l.getBlockState(new BlockPos(x, y, z));
        return st.isAir() || !st.getFluidState().isEmpty();
    }

    /** Do topo ate o solo, preenchendo ar, agua e plantas; para ao encontrar terreno solido. */
    private static void column(ServerLevel l, int x, int z, int topY, BlockState b) {
        int own = surf(l, x, z);
        for (int y = topY, k = 0; k < 90; y--, k++) {
            if (y <= own && !soft(l, x, y, z)) break;
            set(l, x, y, z, b);
        }
    }

    private static void gate(ServerLevel l, int x, int z, int gy, int topY, int openH, BlockState b) {
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int y = gy + 1; y <= gy + openH; y++) set(l, x, y, z, air);
        for (int y = gy + openH + 1; y <= topY; y++) set(l, x, y, z, b);          // arco / verga
        set(l, x, gy, z, Blocks.DIRT_PATH.defaultBlockState());
        for (int y = gy - 1, k = 0; k < 40; y--, k++) {
            if (!soft(l, x, y, z)) break;
            set(l, x, y, z, b);
        }
    }

    /** @return false se ainda ha chunks nao carregados (tenta de novo depois). */
    static boolean build(ServerLevel l, VillageData v, int tier, boolean capital) {
        Style s = style(tier, capital);
        int h = Math.max(16, v.radius - 4);
        int side = 2 * h, n = side * 4;
        int[] px = new int[n], pz = new int[n], inX = new int[n], inZ = new int[n];
        for (int i = 0; i < side; i++) {
            px[i] = v.x - h + i;        pz[i] = v.z - h;        inX[i] = 0;  inZ[i] = 1;
            px[side + i] = v.x + h;     pz[side + i] = v.z - h + i;     inX[side + i] = -1; inZ[side + i] = 0;
            px[2 * side + i] = v.x + h - i; pz[2 * side + i] = v.z + h; inX[2 * side + i] = 0;  inZ[2 * side + i] = -1;
            px[3 * side + i] = v.x - h; pz[3 * side + i] = v.z + h - i; inX[3 * side + i] = 1;  inZ[3 * side + i] = 0;
        }
        for (int i = 0; i < n; i++) if (!l.hasChunkAt(new BlockPos(px[i], 64, pz[i]))) return false;

        int[] ground = new int[n];
        for (int i = 0; i < n; i++) ground[i] = surf(l, px[i], pz[i]);

        int[] gates = {h, side + h, 2 * side + h, 3 * side + h};
        int mainGate = gates[2];                                       // lado sul
        for (int g : gates) {                                          // piso do portao nivelado
            int gy = ground[g];
            for (int j = -1; j <= 1; j++) ground[Math.floorMod(g + j, n)] = gy;
        }

        int[] top = new int[n];
        for (int i = 0; i < n; i++) {
            int m = Integer.MIN_VALUE;
            for (int j = -2; j <= 2; j++) m = Math.max(m, ground[Math.floorMod(i + j, n)]);
            top[i] = m + s.height();
        }
        for (int pass = 0; pass < 30; pass++) {                        // continuidade: degrau maximo de 1 bloco
            for (int i = 0; i < n; i++)
                top[i] = Math.max(top[i], Math.max(top[Math.floorMod(i - 1, n)], top[Math.floorMod(i + 1, n)]) - 1);
        }

        int openH = (capital || tier >= 2) ? 4 : 3;
        for (int k = 0; k < s.thick(); k++) {
            for (int i = 0; i < n; i++) {
                int x = px[i] + inX[i] * k, z = pz[i] + inZ[i] * k;
                boolean isGate = false;
                int gy = ground[i];
                for (int g : gates) {
                    int d = Math.abs(i - g);
                    if (Math.min(d, n - d) <= 1) { isGate = true; gy = ground[g]; }
                }
                if (isGate) gate(l, x, z, gy, top[i], openH, s.block());
                else column(l, x, z, top[i] + ((i & 1) == 0 ? 1 : 0), s.block());
            }
        }

        BlockState lantern = Blocks.LANTERN.defaultBlockState();
        // torres de canto
        for (int c : new int[]{0, side, 2 * side, 3 * side}) tower(l, px[c], pz[c], top[c] + 3, s.block(), lantern);
        // torres de guarda do portao principal
        for (int sg : new int[]{-3, 3}) {
            int idx = Math.floorMod(mainGate + sg, n);
            tower(l, px[idx] + inX[idx], pz[idx] + inZ[idx], top[idx] + 3, s.block(), lantern);
        }
        return true;
    }

    private static void tower(ServerLevel l, int cx, int cz, int topY, BlockState b, BlockState lantern) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) column(l, cx + dx, cz + dz, topY, b);
        set(l, cx, topY + 1, cz, lantern);
    }
}
