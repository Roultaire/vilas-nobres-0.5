package br.nobrevilas;

import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/** Carrega os .nbt do kit (embutidos em KitData) e os coloca no mundo pelo canto minimo, com rotacao correta. */
final class StructureKit {
    private StructureKit() {}

    private static final Map<String, String> DATA = Map.of(
        "muralha", KitData.WALL, "portao", KitData.GATE, "torre", KitData.TOWER, "ferreiro", KitData.SMITH);
    /** camadas de fundacao incluidas na propria estrutura (o "_gN" do nome do arquivo) */
    private static final Map<String, Integer> FOUNDATION = Map.of("muralha", 0, "portao", 0, "torre", 0, "ferreiro", 1);
    private static final Map<String, StructureTemplate> CACHE = new HashMap<>();

    static List<String> names() { return List.of("muralha", "portao", "torre", "ferreiro"); }
    static boolean has(String name) { return DATA.containsKey(name); }
    static int foundation(String name) { return FOUNDATION.getOrDefault(name, 0); }

    static StructureTemplate get(ServerLevel l, String name) {
        StructureTemplate cached = CACHE.get(name);
        if (cached != null) return cached;
        String b64 = DATA.get(name);
        if (b64 == null) return null;
        try {
            byte[] raw = Base64.getDecoder().decode(b64);
            CompoundTag tag = NbtIo.readCompressed(new ByteArrayInputStream(raw), NbtAccounter.unlimitedHeap());
            StructureTemplate t = new StructureTemplate();
            t.load(l.registryAccess().lookupOrThrow(Registries.BLOCK), tag);
            CACHE.put(name, t);
            return t;
        } catch (Exception e) {
            NobreVilas.LOG.error("Falha ao carregar a estrutura " + name, e);
            return null;
        }
    }

    /** Tamanho (largura x, profundidade z) ja considerando a rotacao. */
    static int[] size(ServerLevel l, String name, Rotation rot) {
        StructureTemplate t = get(l, name);
        if (t == null) return new int[]{0, 0, 0};
        boolean swap = rot == Rotation.CLOCKWISE_90 || rot == Rotation.COUNTERCLOCKWISE_90;
        return new int[]{swap ? t.getSize().getZ() : t.getSize().getX(), t.getSize().getY(), swap ? t.getSize().getX() : t.getSize().getZ()};
    }

    /** Coloca a estrutura de modo que o canto minimo (x,z) da caixa ja rotacionada fique em (minX, minZ); y = camada 0. */
    static boolean place(ServerLevel l, String name, Rotation rot, int minX, int y, int minZ) {
        StructureTemplate t = get(l, name);
        if (t == null) return false;
        int sx = t.getSize().getX(), sz = t.getSize().getZ();
        int mx, mz;
        switch (rot) {
            case CLOCKWISE_90 -> { mx = -(sz - 1); mz = 0; }
            case CLOCKWISE_180 -> { mx = -(sx - 1); mz = -(sz - 1); }
            case COUNTERCLOCKWISE_90 -> { mx = 0; mz = -(sx - 1); }
            default -> { mx = 0; mz = 0; }
        }
        BlockPos origin = new BlockPos(minX - mx, y, minZ - mz);
        StructurePlaceSettings st = new StructurePlaceSettings().setRotation(rot).setMirror(Mirror.NONE).setIgnoreEntities(true);
        return t.placeInWorld(l, origin, origin, st, l.getRandom(), 2);
    }
}
