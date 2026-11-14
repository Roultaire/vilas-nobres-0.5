package br.nobrevilas;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Muralha SUAVE: o topo e um envelope de inclinacao maxima 1:3 sobre o terreno suavizado (sem "estacas"),
 * as colunas descem ate o solo, os portoes sao em ARCO e ganham RAMPAS em ambos os lados para nunca ficarem
 * acima do nivel de quem entra/sai. O portao sul e o principal (5 de largura, torres de guarda).
 * Muralhas antigas sao removidas antes de reconstruir.
 */
final class WallBuilder {
    private WallBuilder() {}

    record Style(BlockState block, int height, int thick) {}

    static Style style(int tier, boolean capital) {
        Style s = switch (Math.max(1, Math.min(10, tier))) {
            case 1 -> new Style(Blocks.OAK_LOG.defaultBlockState(), 4, 1);
            case 2 -> new Style(Blocks.DARK_OAK_LOG.defaultBlockState(), 5, 1);
            case 3 -> new Style(Blocks.COBBLESTONE.defaultBlockState(), 5, 1);
            case 4 -> new Style(Blocks.STONE_BRICKS.defaultBlockState(), 6, 1);
            case 5 -> new Style(Blocks.STONE_BRICKS.defaultBlockState(), 7, 2);
            case 6 -> new Style(Blocks.DEEPSLATE_BRICKS.defaultBlockState(), 7, 2);
            case 7 -> new Style(Blocks.DEEPSLATE_TILES.defaultBlockState(), 8, 2);
            case 8 -> new Style(Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), 8, 2);
            case 9 -> new Style(Blocks.RED_NETHER_BRICKS.defaultBlockState(), 9, 3);
            default -> new Style(Blocks.QUARTZ_BRICKS.defaultBlockState(), 10, 3);
        };
        return capital ? new Style(s.block(), s.height() + 3, Math.max(3, s.thick())) : s;
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

    private static boolean isWall(BlockState s) {
        return s.is(Blocks.OAK_LOG) || s.is(Blocks.DARK_OAK_LOG) || s.is(Blocks.COBBLESTONE) || s.is(Blocks.STONE_BRICKS)
            || s.is(Blocks.DEEPSLATE_BRICKS) || s.is(Blocks.DEEPSLATE_TILES) || s.is(Blocks.POLISHED_BLACKSTONE_BRICKS)
            || s.is(Blocks.RED_NETHER_BRICKS) || s.is(Blocks.QUARTZ_BRICKS);
    }

    /** Referencia de nivel da agua ao lado da muralha (para restaurar rios/mar ao remover a muralha antiga). */
    private static int waterRef(ServerLevel l, int x, int z) {
        if (!l.hasChunkAt(new BlockPos(x, 64, z))) return Integer.MIN_VALUE;
        int s = surf(l, x, z);
        return l.getFluidState(new BlockPos(x, s, z)).isEmpty() ? Integer.MIN_VALUE : s;
    }

    private static void clearOld(ServerLevel l, int x, int z, int wRef) {
        if (!l.hasChunkAt(new BlockPos(x, 64, z))) return;
        int top = surf(l, x, z);
        BlockState air = Blocks.AIR.defaultBlockState();
        if (l.getBlockState(new BlockPos(x, top + 1, z)).is(Blocks.LANTERN)) set(l, x, top + 1, z, air);
        for (int y = top, k = 0; k < 80; y--, k++) {
            BlockState st = l.getBlockState(new BlockPos(x, y, z));
            if (st.isAir()) continue;
            if (isWall(st) || st.is(Blocks.LANTERN)) set(l, x, y, z, y <= wRef ? Blocks.WATER.defaultBlockState() : air);
            else break;
        }
    }

    /** Do topo ate o solo, preenchendo ar, agua e plantas; para ao encontrar terreno solido. */
    private static void column(ServerLevel l, int x, int z, int topY, BlockState b) {
        int own = surf(l, x, z);
        for (int y = topY, k = 0; k < 90; y--, k++) {
            if (y <= own && !soft(l, x, y, z)) break;
            set(l, x, y, z, b);
        }
    }

    private static void gateCol(ServerLevel l, int x, int z, int gy, int topY, int hOpen, BlockState b) {
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int y = gy + 1; y <= gy + hOpen; y++) set(l, x, y, z, air);
        for (int y = gy + hOpen + 1; y <= topY; y++) set(l, x, y, z, b);
        set(l, x, gy, z, Blocks.DIRT_PATH.defaultBlockState());
        for (int y = gy - 1, k = 0; k < 40; y--, k++) {
            if (!soft(l, x, y, z)) break;
            set(l, x, y, z, b);
        }
    }

    /** Caminho em rampa suave (1 bloco a cada 2) ate encontrar o terreno natural. */
    static void ramp(ServerLevel l, int gx, int gz, int dx, int dz, int lx, int lz, int startD, int gy, int halfW) {
        int t = gy;
        for (int d = startD; d <= startD + 16; d++) {
            int cx = gx + dx * d, cz = gz + dz * d;
            if (!l.hasChunkAt(new BlockPos(cx, 64, cz))) break;
            int nat = surf(l, cx, cz);
            if (nat - t >= 4) break;                      // nao cavar trincheira
            if (d % 2 == 0) { if (nat > t) t++; else if (nat < t) t--; }
            for (int w = -halfW; w <= halfW; w++) pave(l, cx + lx * w, cz + lz * w, t);
            if (nat == t && d >= startD + 2) break;
        }
    }

    private static void pave(ServerLevel l, int x, int z, int t) {
        if (!l.hasChunkAt(new BlockPos(x, 64, z))) return;
        int own = surf(l, x, z);
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int y = t + 1; y <= Math.max(t + 4, Math.min(own, t + 10)); y++)
            if (!l.getBlockState(new BlockPos(x, y, z)).isAir()) set(l, x, y, z, air);
        set(l, x, t, z, Blocks.DIRT_PATH.defaultBlockState());
        for (int y = t - 1, k = 0; k < 40; y--, k++) {
            if (!soft(l, x, y, z)) break;
            set(l, x, y, z, Blocks.DIRT.defaultBlockState());
        }
    }

    /** Remove a muralha procedural antiga (e restaura a agua) antes de montar a capital com o kit. */
    static void removeOld(ServerLevel l, VillageData v) {
        int h = Math.max(16, v.radius - 4);
        int side = 2 * h;
        for (int i = 0; i < side; i++) {
            band(l, v.x - h + i, v.z - h, 0, 1);
            band(l, v.x + h, v.z - h + i, -1, 0);
            band(l, v.x + h - i, v.z + h, 0, -1);
            band(l, v.x - h, v.z + h - i, 1, 0);
        }
    }

    private static void band(ServerLevel l, int px, int pz, int inX, int inZ) {
        if (!l.hasChunkAt(new BlockPos(px, 64, pz))) return;
        int wRef = Math.max(waterRef(l, px - inX * 4, pz - inZ * 4), waterRef(l, px + inX * 7, pz + inZ * 7));
        for (int o = -2; o <= 4; o++) clearOld(l, px + inX * o, pz + inZ * o, wRef);
    }

    /** @return false se ainda ha chunks nao carregados (tenta de novo depois). */
    static boolean build(ServerLevel l, VillageData v, int tier, boolean capital) {
        Style s = style(tier, capital);
        int h = Math.max(16, v.radius - 4);
        int side = 2 * h, n = side * 4;
        int[] px = new int[n], pz = new int[n], inX = new int[n], inZ = new int[n];
        for (int i = 0; i < side; i++) {
            px[i] = v.x - h + i;            pz[i] = v.z - h;            inX[i] = 0;  inZ[i] = 1;
            px[side + i] = v.x + h;         pz[side + i] = v.z - h + i; inX[side + i] = -1; inZ[side + i] = 0;
            px[2 * side + i] = v.x + h - i; pz[2 * side + i] = v.z + h; inX[2 * side + i] = 0;  inZ[2 * side + i] = -1;
            px[3 * side + i] = v.x - h;     pz[3 * side + i] = v.z + h - i; inX[3 * side + i] = 1;  inZ[3 * side + i] = 0;
        }
        for (int i = 0; i < n; i++) if (!l.hasChunkAt(new BlockPos(px[i], 64, pz[i]))) return false;

        // 1) remove a muralha antiga (e restaura a agua), para medir o terreno de verdade
        for (int i = 0; i < n; i++) {
            int wRef = Math.max(waterRef(l, px[i] - inX[i] * 4, pz[i] - inZ[i] * 4), waterRef(l, px[i] + inX[i] * 7, pz[i] + inZ[i] * 7));
            for (int o = -2; o <= 4; o++) clearOld(l, px[i] + inX[i] * o, pz[i] + inZ[i] * o, wRef);
        }

        // 2) perfil do terreno e topo suavizado
        int[] g = new int[n], gs = new int[n];
        for (int i = 0; i < n; i++) g[i] = surf(l, px[i], pz[i]);
        for (int i = 0; i < n; i++) {
            long sum = 0;
            for (int j = -5; j <= 5; j++) sum += g[Math.floorMod(i + j, n)];
            gs[i] = (int) Math.round(sum / 11.0);
        }
        // portoes: o trecho mais plano e seco de cada lado (evita trincheiras e degraus)
        boolean[] wet = new boolean[n];
        for (int i = 0; i < n; i++) wet[i] = !l.getFluidState(new BlockPos(px[i], g[i], pz[i])).isEmpty();
        int[] gates = new int[4];
        for (int q = 0; q < 4; q++) {
            int mid = q * side + h, best = mid, margin = Math.max(10, h / 4);
            double bestScore = Double.MAX_VALUE;
            for (int c = q * side + margin; c <= q * side + side - margin; c++) {
                double score = Math.abs(c - mid) * 0.05;
                for (int j = -8; j <= 8; j++) {
                    int idx = Math.floorMod(c + j, n);
                    score += Math.abs(g[idx] - g[c]) + (wet[idx] ? 40 : 0);
                }
                if (score < bestScore) { bestScore = score; best = c; }
            }
            gates[q] = best;
        }
        int mainGate = gates[2];
        int openMax = (capital || tier >= 7) ? 5 : 4;
        int[] gateY = new int[4];
        int[] need = new int[n];
        for (int i = 0; i < n; i++) need[i] = Math.max(gs[i] + s.height(), g[i] + 3);
        for (int k = 0; k < 4; k++) {
            gateY[k] = g[gates[k]];
            for (int j = -2; j <= 2; j++) {
                int idx = Math.floorMod(gates[k] + j, n);
                need[idx] = Math.max(need[idx], gateY[k] + openMax + 2);
            }
        }
        int[] top = new int[n];
        for (int i = 0; i < n; i++) {
            int best = Integer.MIN_VALUE;
            for (int j = 0; j < n; j++) {
                int d = Math.abs(i - j);
                d = Math.min(d, n - d);
                best = Math.max(best, need[j] - (d + 2) / 3);       // inclinacao maxima 1:3
            }
            top[i] = best;
        }

        // 3) colunas
        for (int k = 0; k < s.thick(); k++) {
            for (int i = 0; i < n; i++) {
                int x = px[i] + inX[i] * k, z = pz[i] + inZ[i] * k;
                int gateIdx = -1, lat = 0;
                for (int q = 0; q < 4; q++) {
                    int dd = i - gates[q];
                    if (dd > n / 2) dd -= n;
                    if (dd < -n / 2) dd += n;
                    int halfW = gates[q] == mainGate ? 2 : 1;
                    if (Math.abs(dd) <= halfW) { gateIdx = q; lat = dd; }
                }
                if (gateIdx >= 0) gateCol(l, x, z, gateY[gateIdx], top[i], openMax - Math.abs(lat), s.block());     // arco
                else column(l, x, z, top[i] + ((i & 1) == 0 ? 1 : 0), s.block());
            }
        }

        // 4) rampas e lanternas dos portoes
        for (int q = 0; q < 4; q++) {
            int gi = gates[q];
            int halfW = gi == mainGate ? 2 : 1;
            int lx = inX[gi] != 0 ? 0 : 1, lz = inX[gi] != 0 ? 1 : 0;
            ramp(l, px[gi], pz[gi], -inX[gi], -inZ[gi], lx, lz, 1, gateY[q], halfW);               // para fora
            ramp(l, px[gi], pz[gi], inX[gi], inZ[gi], lx, lz, s.thick(), gateY[q], halfW);        // para dentro
            MarkerBuilder.runCmd(l, "setblock " + px[gi] + " " + (gateY[q] + openMax) + " " + pz[gi] + " minecraft:lantern[hanging=true]");
        }

        // 5) torres
        BlockState lantern = Blocks.LANTERN.defaultBlockState();
        for (int c : new int[]{0, side, 2 * side, 3 * side}) tower(l, px[c], pz[c], top[c] + 3, s.block(), lantern);
        for (int sg : new int[]{-4, 4}) {
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
