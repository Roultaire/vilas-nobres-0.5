package br.nobrevilas;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.LevelResource;

public final class VillageManager {
    static final int BUILD_VERSION = 4;
    private static final int[] GARRISON = {0, 3, 4, 5, 6, 8, 9, 10, 12, 14, 16};
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    static class SaveFile {
        List<VillageData> villages = new ArrayList<>();
        Map<String, List<String>> visited = new HashMap<>();
        Map<String, PlayerData> players = new HashMap<>();
        Map<String, String> claims = new HashMap<>();
    }

    private final MinecraftServer server;
    private Config cfg = new Config();
    private final Map<String, VillageData> villages = new LinkedHashMap<>();
    private final Map<UUID, Set<String>> visited = new HashMap<>();
    private final Map<UUID, PlayerData> players = new HashMap<>();
    private final Map<String, String> claims = new HashMap<>();
    private final Map<UUID, String> current = new HashMap<>();
    private final Set<UUID> tracking = new HashSet<>();
    private final Random rnd = new Random();
    private final Map<UUID, Long> frozenUntil = new HashMap<>();
    private final Map<UUID, UUID> frozenWith = new HashMap<>();
    private final java.util.ArrayDeque<Runnable> jobs = new java.util.ArrayDeque<>();
    private int ticks;
    private boolean dirty;

    public VillageManager(MinecraftServer server) { this.server = server; }

    public MinecraftServer server() { return server; }
    public Config cfg() { return cfg; }
    public void markDirty() { dirty = true; }
    public VillageData get(String id) { return villages.get(id); }
    public Collection<VillageData> all() { return villages.values(); }
    public PlayerData pd(UUID id) { return players.computeIfAbsent(id, k -> new PlayerData()); }
    public PlayerData pdOrNull(UUID id) { return players.get(id); }
    public Map<UUID, PlayerData> allPlayers() { return players; }

    // ───────────── persistência ─────────────
    private Path dir() {
        Path dir = server.getWorldPath(LevelResource.ROOT).resolve("nobrevilas");
        try { Files.createDirectories(dir); } catch (IOException ignored) {}
        return dir;
    }

    public void load() {
        Path cp = dir().resolve("config.json");
        try {
            if (Files.exists(cp)) {
                try (var r = Files.newBufferedReader(cp)) {
                    Config c = GSON.fromJson(r, Config.class);
                    if (c != null) cfg = c;
                }
            } else {
                Files.writeString(cp, GSON.toJson(cfg));
            }
        } catch (Exception e) { NobreVilas.LOG.error("Falha ao ler config.json", e); }

        Path p = dir().resolve("villages.json");
        if (!Files.exists(p)) return;
        try (var r = Files.newBufferedReader(p)) {
            SaveFile s = GSON.fromJson(r, SaveFile.class);
            if (s == null) return;
            for (VillageData v : s.villages) { migrate(v); villages.put(v.id, v); }
            if (s.visited != null) s.visited.forEach((k, list) -> visited.put(UUID.fromString(k), new HashSet<>(list)));
            if (s.players != null) s.players.forEach((k, pdv) -> players.put(UUID.fromString(k), pdv));
            if (s.claims != null) claims.putAll(s.claims);
            NobreVilas.LOG.info("{} vilas carregadas.", villages.size());
        } catch (Exception e) {
            NobreVilas.LOG.error("Falha ao ler villages.json", e);
        }
    }

    /** Converte os postos antigos (1 baraoo, 2 conde, 3 duque, 5 rei) para a nova escada de 10 postos. */
    private void migrate(VillageData v) {
        if (v.schema >= 3) return;
        v.seatTier = switch (v.seatTier) {
            case 5 -> 9;
            case 3 -> 6;
            case 2 -> 4;
            default -> v.radius < 45 ? 1 : v.radius < 58 ? 2 : 3;
        };
        v.schema = 3;
    }

    /** O NPC para (sem IA) e olha para o jogador enquanto durar a conversa (45 s sem interacao). */
    public void freeze(Entity e, ServerPlayer p) {
        UUID id = e.getUUID();
        boolean already = frozenUntil.containsKey(id);
        frozenUntil.put(id, server.overworld().getGameTime() + 900);
        frozenWith.put(id, p.getUUID());
        if (!already) run("data merge entity " + id + " {NoAI:1b}");
        run("execute as " + id + " at @s run tp @s ~ ~ ~ facing entity " + p.getScoreboardName() + " eyes");
    }

    private void processTalking() {
        if (frozenUntil.isEmpty()) return;
        ServerLevel level = server.overworld();
        long now = level.getGameTime();
        for (var it = frozenUntil.entrySet().iterator(); it.hasNext();) {
            var en = it.next();
            UUID id = en.getKey();
            UUID who = frozenWith.get(id);
            ServerPlayer pl = who == null ? null : server.getPlayerList().getPlayer(who);
            Entity ent = level.getEntity(id);
            if (now >= en.getValue() || pl == null || ent == null || ent.distanceToSqr(pl) > 400.0) {
                run("data merge entity " + id + " {NoAI:0b}");
                frozenWith.remove(id);
                it.remove();
            }
        }
    }

    public void shutdown() {
        for (UUID id : frozenUntil.keySet()) run("data merge entity " + id + " {NoAI:0b}");
        frozenUntil.clear();
        frozenWith.clear();
        save();
    }

    public void save() {
        SaveFile s = new SaveFile();
        s.villages.addAll(villages.values());
        visited.forEach((k, set) -> s.visited.put(k.toString(), new ArrayList<>(set)));
        players.forEach((k, v) -> s.players.put(k.toString(), v));
        s.claims.putAll(claims);
        try (var w = Files.newBufferedWriter(dir().resolve("villages.json"))) {
            GSON.toJson(s, w);
            dirty = false;
        } catch (IOException e) {
            NobreVilas.LOG.error("Falha ao salvar villages.json", e);
        }
    }

    public void run(String cmd) {
        try {
            var src = server.createCommandSourceStack().withLevel(server.overworld()).withSuppressedOutput();
            server.getCommands().performPrefixedCommand(src, cmd);
        } catch (Exception e) {
            NobreVilas.LOG.warn("Comando falhou: {}", cmd, e);
        }
    }

    public String guardEntity() {
        return FabricLoader.getInstance().isModLoaded(cfg.guardModId) ? cfg.guardEntity : cfg.fallbackGuardEntity;
    }

    // ───────────── descoberta ─────────────
    public void onChunkLoad(ServerLevel level, LevelChunk chunk) {
        if (level.dimension() != Level.OVERWORLD) return;
        var starts = chunk.getAllStarts();
        if (starts.isEmpty()) return;
        var reg = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        for (var e : starts.entrySet()) {
            var start = e.getValue();
            if (start == null || !start.isValid()) continue;
            var key = reg.getKey(e.getKey());
            if (key == null || !key.getPath().contains("village")) continue;
            BoundingBox box = start.getBoundingBox();
            BlockPos c = box.getCenter();
            String id = c.getX() + "_" + c.getZ();
            if (villages.containsKey(id)) continue;
            VillageData v = new VillageData();
            v.id = id;
            v.x = c.getX(); v.y = c.getY(); v.z = c.getZ();
            v.radius = Math.max(box.getXSpan(), box.getZSpan()) / 2 + 12;
            villages.put(id, v);
            dirty = true;
        }
    }

    private static boolean ready(ServerLevel l, int cx, int cz, int r) {
        for (int dx = -r; dx <= r; dx += 16)
            for (int dz = -r; dz <= r; dz += 16)
                if (!l.hasChunkAt(new BlockPos(cx + dx, 64, cz + dz))) return false;
        return true;
    }

    private static boolean isPort(ServerLevel l, int cx, int cz) {
        int water = 0;
        for (int r = 12; r <= 48; r += 12) {
            for (int i = 0; i < 16; i++) {
                double a = i * Math.PI / 8;
                int x = cx + (int) Math.round(Math.cos(a) * r);
                int z = cz + (int) Math.round(Math.sin(a) * r);
                if (!l.hasChunkAt(new BlockPos(x, 64, z))) continue;
                int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                if (l.getFluidState(new BlockPos(x, y, z)).is(FluidTags.WATER)) water++;
            }
        }
        return water >= 8;
    }

    // ───────────── hierarquia, sede e familias ─────────────
    private static String cell(String p, int size, VillageData v) {
        return p + ":" + Math.floorDiv(v.x, size) + ":" + Math.floorDiv(v.z, size);
    }

    private void claimAll(VillageData v, String... keys) { for (String k : keys) claims.putIfAbsent(k, v.id); }

    /** A 1a vila de cada territorio vira sua sede: Reino 9, Principado 8, Ducado 6, Marquesado 5, Condado 4; as demais 1..3 por tamanho. */
    private int decideSeat(VillageData v) {
        String k = cell("K", 16384, v), p = cell("P", 8192, v), d = cell("D", 4096, v), m = cell("M", 2048, v), c = cell("C", 1024, v);
        int t;
        if (!claims.containsKey(k)) { t = 9; claimAll(v, k, p, d, m, c); }
        else if (!claims.containsKey(p)) { t = 8; claimAll(v, p, d, m, c); }
        else if (!claims.containsKey(d)) { t = 6; claimAll(v, d, m, c); }
        else if (!claims.containsKey(m)) { t = 5; claimAll(v, m, c); }
        else if (!claims.containsKey(c)) { t = 4; claimAll(v, c); }
        else t = v.radius < 45 ? 1 : v.radius < 58 ? 2 : 3;
        v.seatTier = t;
        return t;
    }

    private void applyHierarchy(VillageData v, long seed) {
        var county = NameGenerator.realm(seed, 1, v.x, v.z);
        var march = NameGenerator.realm(seed, 4, v.x, v.z);
        var duchy = NameGenerator.realm(seed, 2, v.x, v.z);
        var prince = NameGenerator.realm(seed, 5, v.x, v.z);
        var kingdom = NameGenerator.realm(seed, 3, v.x, v.z);
        v.county = county.name();    v.countRuler = county.ruler();    v.countConsort = county.consort();
        v.marchName = march.name();  v.marchRuler = march.ruler();     v.marchConsort = march.consort();
        v.duchy = duchy.name();      v.dukeRuler = duchy.ruler();      v.dukeConsort = duchy.consort();
        v.princeName = prince.name(); v.princeRuler = prince.ruler();  v.princeConsort = prince.consort();
        v.kingConsort = kingdom.consort();
        if (v.ownerUuid == null) { v.kingdom = kingdom.name(); v.kingRuler = kingdom.ruler(); }
    }

    private void applyNobles(VillageData v, long seed, boolean port) {
        int t = v.seatTier;
        String[] n = NameGenerator.localNobles(new Random(NameGenerator.mix(seed, 78, v.x, v.z)), port, Math.max(1, Math.min(3, t)));
        v.officer = n[1];
        v.captain = n[2];
        v.lord = switch (t) {
            case 9 -> v.kingRuler;
            case 8 -> v.princeRuler;
            case 6 -> v.dukeRuler;
            case 5 -> v.marchRuler;
            case 4 -> v.countRuler;
            default -> n[0];
        };
    }

    private void finalizeVillage(ServerLevel level, VillageData v) {
        boolean port = isPort(level, v.x, v.z);
        long seed = level.getSeed();
        Random r = new Random(NameGenerator.mix(seed, 77, v.x, v.z));

        String name;
        int guard = 0;
        do { name = NameGenerator.placeName(r, port); } while (nameTaken(name) && ++guard < 20);

        v.name = name;
        applyHierarchy(v, seed);
        decideSeat(v);
        applyNobles(v, seed, port);
        v.schema = 3;
        v.type = port ? "PORT" : "COLONIAL";   // marca como pronta

        BlockPos m = MarkerBuilder.build(level, v);
        v.markerBuilt = true;
        v.mx = m.getX(); v.my = m.getY(); v.mz = m.getZ();
        ManorBuilder.build(level, v, tierOf(v));
        v.buildVersion = BUILD_VERSION;
        spawnLord(level, v);
        dirty = true;
        NobreVilas.LOG.info("Vila fundada: {} ({}, posto {}) em {} {}", v.name, v.type, v.seatTier, v.x, v.z);
    }

    private boolean nameTaken(String n) {
        for (VillageData v : villages.values()) if (n.equals(v.name)) return true;
        return false;
    }

    /** O governante NPC reside na mansao/castelo. Matar este aldeao = dominar a vila. */
    private void spawnLord(ServerLevel level, VillageData v) {
        int tier = tierOf(v);
        int hp = 40 + 20 * tier;
        String color = tier >= 8 ? "red" : tier >= 5 ? "gold" : "yellow";
        int sx = v.manorBuilt ? v.rx : v.mx + 2, sy = v.manorBuilt ? v.ry : v.my + 1, sz = v.manorBuilt ? v.rz : v.mz;
        UUID lu = UUID.randomUUID();
        long hi = lu.getMostSignificantBits(), lo = lu.getLeastSignificantBits();
        String uuidNbt = "UUID:[I;" + (int) (hi >> 32) + "," + (int) hi + "," + (int) (lo >> 32) + "," + (int) lo + "],";
        v.lordUuid = lu.toString();
        run("summon minecraft:villager " + sx + ".5 " + sy + " " + sz + ".5 {" + uuidNbt + "Tags:[\"nv_lord\",\"nv_v_" + v.id + "\"],"
            + "PersistenceRequired:1b,CustomNameVisible:1b,CustomName:{text:\"" + v.lord.replace("\"", "") + "\",color:\"" + color + "\"},"
            + "VillagerData:{profession:\"minecraft:nitwit\",type:\"minecraft:plains\",level:1},"
            + "Health:" + hp + ".0f,attributes:[{id:\"minecraft:max_health\",base:" + hp + ".0d}]}");
        v.lordSpawned = true;
    }

    /** Guarnicao permanente: capitao + guardas e arqueiros equipados, no portao sul e na residencia. */
    private void spawnGarrison(ServerLevel level, VillageData v) {
        int tier = tierOf(v);
        int count = GARRISON[Math.min(tier, 10)] + ((v.isCapital() && v.ownerUuid == null) ? 16 : 0);
        int rx = v.manorBuilt ? v.rx : v.mx, rz = v.manorBuilt ? v.rz : v.mz;
        int h = Math.max(16, v.radius - 4);
        String tags = "\"nv_garrison\",\"nv_v_" + v.id + "\"";
        String cap = v.ownerUuid != null ? "Capitão de " + v.name : v.captain;
        Soldiers.spawn(this, level, rx + 2, rz + 4, tier, "captain", cap, tags, "");
        for (int i = 0; i < count; i++) {
            boolean atGate = i % 2 == 0;
            int x = atGate ? v.x + ((i / 2) % 5 - 2) * 2 : rx + ((i / 2) % 7 - 3) * 2;
            int z = atGate ? v.z + h - 4 : rz + 5 + (i / 14);
            boolean archer = i % 3 == 2;
            Soldiers.spawn(this, level, x, z, tier, archer ? "archer" : "guard", (archer ? "Arqueiro de " : "Guarda de ") + v.name, tags, "");
        }
        v.garrisonSpawned = true;
        dirty = true;
    }

    public String villageIdOfLord(UUID id) {
        String s = id.toString();
        for (VillageData v : villages.values()) if (s.equals(v.lordUuid)) return v.id;
        return null;
    }

    // ───────────── postos / poder ─────────────
    public List<VillageData> owned(UUID id) {
        List<VillageData> out = new ArrayList<>();
        String s = id.toString();
        for (VillageData v : villages.values()) if (s.equals(v.ownerUuid)) out.add(v);
        return out;
    }

    public int playerTier(UUID id) {
        PlayerData p = players.get(id);
        if (p != null && p.tierOverride >= 0) return p.tierOverride;
        if (p != null && p.emperor) return Ranks.EMPEROR;
        if (p != null && p.king) return Ranks.KING;
        return Ranks.tierForCount(owned(id).size());
    }

    public int tierOf(VillageData v) {
        if (v.ownerUuid != null) return Math.max(1, playerTier(UUID.fromString(v.ownerUuid)));
        return Math.max(1, v.seatTier);
    }

    public int taxFor(UUID id) {
        int n = owned(id).size();
        if (n == 0) return 0;
        return (int) Math.round(Ranks.TAX[playerTier(id)] * (1 + 0.1 * (n - 1)));
    }

    public int productivity(VillageData v) { return 10 + v.radius / 6 + tierOf(v) * 6; }
    public int power(VillageData v) { return v.radius * 3 + tierOf(v) * 40 + productivity(v) * 2 + (v.isPort() ? 20 : 0); }

    public List<VillageData> ranking() {
        List<VillageData> l = new ArrayList<>();
        for (VillageData v : villages.values()) if (v.isNamed()) l.add(v);
        l.sort((a, b) -> Integer.compare(power(b), power(a)));
        return l;
    }

    public void refreshOwnerTitles(UUID id) {
        for (VillageData v : owned(id)) v.lord = Ranks.title(tierOf(v)) + " " + v.ownerName;
    }

    public boolean isAllied(UUID id, VillageData v) {
        PlayerData p = players.get(id);
        if (p == null) return false;
        return p.allyVillages.contains(v.id) || (v.ownerUuid == null && p.allyKingdoms.contains(v.kingdom));
    }

    // ───────────── consultas ─────────────
    public VillageData villageAt(double px, double pz) {
        VillageData best = null; double bd = Double.MAX_VALUE;
        for (VillageData v : villages.values()) {
            if (!v.isNamed()) continue;
            double dx = px - v.x, dz = pz - v.z, d = dx * dx + dz * dz;
            if (d <= (double) v.radius * v.radius && d < bd) { best = v; bd = d; }
        }
        return best;
    }

    public VillageData nearest(double px, double pz) {
        VillageData best = null; double bd = Double.MAX_VALUE;
        for (VillageData v : villages.values()) {
            if (!v.isNamed()) continue;
            double dx = px - v.x, dz = pz - v.z, d = dx * dx + dz * dz;
            if (d < bd) { best = v; bd = d; }
        }
        return best;
    }

    public List<VillageData> visitedBy(UUID id) {
        List<VillageData> out = new ArrayList<>();
        for (String vid : visited.getOrDefault(id, Set.of())) {
            VillageData v = villages.get(vid);
            if (v != null && v.isNamed()) out.add(v);
        }
        return out;
    }

    public boolean toggleTracking(UUID id) {
        if (!tracking.remove(id)) { tracking.add(id); return true; }
        return false;
    }

    public static String arrow(double dx, double dz, float yaw) {
        double target = Math.toDegrees(Math.atan2(-dx, dz));
        double rel = ((target - yaw) % 360 + 540) % 360 - 180;
        int idx = (int) Math.round(rel / 45.0);
        idx = ((idx % 8) + 8) % 8;
        return new String[]{"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"}[idx];
    }

    // ───────────── texto ─────────────
    private static Component kv(String k, String val) {
        return Component.literal(k + ": ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal(val).withStyle(ChatFormatting.WHITE));
    }

    public String subtitleFor(VillageData v) {
        String rel = switch (v.seatTier) {
            case 9 -> "Capital do " + v.kingdom;
            case 8 -> "Sede do " + v.princeName;
            case 6 -> "Sede do " + v.duchy;
            case 5 -> "Sede do " + v.marchName;
            case 4 -> "Sede do " + v.county;
            default -> "Vassala do " + v.county;
        };
        if (v.ownerUuid != null) rel = "Domínio de " + v.ownerName + " · " + v.kingdom;
        return v.typeLabel() + " · " + rel;
    }

    public List<Component> infoLines(VillageData v) {
        int tier = tierOf(v);
        ChatFormatting col = v.isPort() ? ChatFormatting.AQUA : ChatFormatting.GOLD;
        List<Component> l = new ArrayList<>();
        l.add(Component.literal("══════ " + v.name + " ══════").withStyle(col, ChatFormatting.BOLD));
        l.add(Component.literal(subtitleFor(v)).withStyle(ChatFormatting.YELLOW));
        if (v.ownerUuid != null) l.add(kv("Senhor", Ranks.title(tier) + " " + v.ownerName + " (jogador)"));
        else l.add(kv("Governo local", v.lord));
        l.add(kv(v.isPort() ? "Porto" : "Administração", v.officer));
        l.add(kv("Guarda", v.captain));
        l.add(kv("Condado", v.countRuler + " · " + v.county));
        if (v.marchName != null) l.add(kv("Marquesado", v.marchRuler + " · " + v.marchName));
        l.add(kv("Ducado", v.dukeRuler + " · " + v.duchy));
        if (v.princeName != null) l.add(kv("Principado", v.princeRuler + " · " + v.princeName));
        l.add(kv("Coroa", v.kingRuler + " · " + v.kingdom));
        if (v.kingConsort != null && v.ownerUuid == null) l.add(kv("Corte", v.kingConsort));
        l.add(kv("Muralha", (v.isCapital() ? "Fortaleza do Reino" : Ranks.WALLS[Math.min(tier, 10)]) + " (posto " + tier + ")"));
        l.add(kv("Poder", power(v) + " · produção " + productivity(v) + "/dia"));
        l.add(kv("Localização", v.x + ", " + v.z));
        return l;
    }

    // ───────────── tick ─────────────
    public void tick() {
        ticks++;
        for (int n = 0; n < 2 && !jobs.isEmpty(); n++) {
            try { jobs.poll().run(); } catch (Exception ex) { NobreVilas.LOG.error("Erro em tarefa de construcao", ex); }
        }
        if (ticks % 40 == 0) processPending();
        if (ticks % 100 == 0) processLords();
        if (ticks % 20 == 0) processPlayers();
        if (ticks % 10 == 0) { processTracking(); processTalking(); }
        if (ticks % 200 == 0) processTaxes();
        if (ticks % Math.max(600, cfg.raidIntervalTicks) == 0) processWars();
        if (dirty && ticks % 6000 == 0) save();
    }

    private boolean needsCastle(VillageData v) {
        return v.forceCastle || (v.isCapital() && tierOf(v) >= Ranks.KING);
    }

    private void processPending() {
        ServerLevel level = server.overworld();
        boolean heavy = false;
        for (VillageData v : new ArrayList<>(villages.values())) {
            if (!v.isNamed()) {
                if (!ready(level, v.x, v.z, 48)) continue;
                try { finalizeVillage(level, v); }
                catch (Exception ex) { NobreVilas.LOG.error("Erro ao fundar vila " + v.id, ex); v.type = null; }
                continue;
            }
            try {
                int full = ((v.radius + 15) / 16) * 16;
                boolean loaded = ready(level, v.x, v.z, full);
                if (!heavy && v.markerBuilt && v.buildVersion < BUILD_VERSION && loaded) {
                    upgradeVillage(level, v);
                    heavy = true;
                    continue;
                }
                if (v.buildVersion < BUILD_VERSION) continue;      // espera a migracao antes de construir
                if (!v.lordSpawned && v.ownerUuid == null && v.markerBuilt && loaded) spawnLord(level, v);
                if (!v.garrisonSpawned && v.markerBuilt && loaded) spawnGarrison(level, v);
                if (!heavy && !v.castleBuilt && needsCastle(v) && loaded) {
                    boolean ok = CastleBuilder.build(level, v, tierOf(v));
                    v.castleBuilt = true;
                    v.forceCastle = false;
                    dirty = true;
                    heavy = true;
                    if (ok && v.ownerUuid == null) {
                        run("kill @e[tag=nv_lord,tag=nv_v_" + v.id + "]");
                        spawnLord(level, v);
                    }
                    continue;
                }
                boolean kitWanted = v.isCapital() && (v.kitBuilt || v.forceKit || cfg.autoCapitalKit);
                if (!heavy && kitWanted && !v.kitBuilt && jobs.isEmpty()) {
                    var plan = CapitalBuilder.plan(level, v);
                    if (plan != null) {
                        jobs.addAll(plan);
                        jobs.add(() -> {
                            v.kitBuilt = true;
                            v.forceKit = false;
                            v.wallKey = tierOf(v) * 10 + 1;
                            dirty = true;
                            NobreVilas.LOG.info("Capital {} concluida.", v.name);
                        });
                        heavy = true;
                    }
                    continue;
                }
                if (kitWanted) continue;                       // a capital usa o kit, nao a muralha procedural
                int key = tierOf(v) * 10 + (v.isCapital() ? 1 : 0);
                if (!heavy && v.wallKey != key && loaded) {
                    if (WallBuilder.build(level, v, tierOf(v), v.isCapital())) {
                        v.wallKey = key;
                        dirty = true;
                        heavy = true;
                    }
                }
            } catch (Exception ex) {
                NobreVilas.LOG.error("Erro na vila " + v.name, ex);
                v.wallKey = tierOf(v) * 10 + (v.isCapital() ? 1 : 0);
                v.buildVersion = BUILD_VERSION;
            }
        }
    }

    /** Vilas de versoes antigas: novas familias e titulos, governante renomeado, muralha suave refeita, guarnicao nova. */
    private void upgradeVillage(ServerLevel level, VillageData v) {
        long seed = level.getSeed();
        applyHierarchy(v, seed);
        if (v.ownerUuid == null) applyNobles(v, seed, v.isPort());
        else refreshOwnerTitles(UUID.fromString(v.ownerUuid));
        if (v.buildVersion < 2) MarkerBuilder.build(level, v);
        if (!v.manorBuilt) ManorBuilder.build(level, v, tierOf(v));
        if (v.ownerUuid == null) {
            run("kill @e[tag=nv_lord,tag=nv_v_" + v.id + "]");
            spawnLord(level, v);
        }
        run("kill @e[tag=nv_garrison,tag=nv_v_" + v.id + "]");
        v.garrisonSpawned = false;
        v.buildVersion = BUILD_VERSION;
        v.wallKey = 0;
        dirty = true;
        NobreVilas.LOG.info("Vila atualizada: {}", v.name);
    }

    /** Rotina dos governantes: passeiam de dia, voltam a residencia a noite e nao se afastam da vila. */
    private void processLords() {
        ServerLevel level = server.overworld();
        boolean night = level.getGameTime() % 24000 >= 13000;
        for (VillageData v : villages.values()) {
            if (!v.isNamed() || v.ownerUuid != null || v.lordUuid == null) continue;
            int hx = v.manorBuilt ? v.rx : v.mx + 2, hy = v.manorBuilt ? v.ry : v.my + 1, hz = v.manorBuilt ? v.rz : v.mz;
            if (frozenUntil.containsKey(UUID.fromString(v.lordUuid))) continue;      // em conversa
            Entity e = level.getEntity(UUID.fromString(v.lordUuid));
            if (e == null) {
                if (v.lordSpawned && v.buildVersion >= BUILD_VERSION && ready(level, v.x, v.z, ((v.radius + 15) / 16) * 16)) spawnLord(level, v);
                continue;
            }
            if (!(e instanceof Mob mob)) continue;
            double dx = e.getX() - v.x, dz = e.getZ() - v.z;
            boolean tooFar = dx * dx + dz * dz > (double) v.radius * v.radius;
            var nav = mob.getNavigation();
            if (tooFar || ((night || rnd.nextInt(4) == 0) && nav.isDone())) {
                nav.moveTo(hx + 0.5, hy, hz + 0.5, 0.6);
            } else if (nav.isDone() && rnd.nextInt(3) == 0) {
                double a = rnd.nextDouble() * 2 * Math.PI, d = rnd.nextDouble() * v.radius * 0.55;
                int x = v.x + (int) Math.round(Math.cos(a) * d), z = v.z + (int) Math.round(Math.sin(a) * d);
                if (level.hasChunkAt(new BlockPos(x, 64, z))) {
                    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                    nav.moveTo(x + 0.5, y, z + 0.5, 0.6);
                }
            }
        }
    }

    private void processPlayers() {
        ServerLevel level = server.overworld();
        for (ServerPlayer p : level.players()) {
            PlayerData pd = pd(p.getUUID());
            pd.name = p.getScoreboardName();
            if (!pd.gotBook) { pd.gotBook = true; dirty = true; Book.give(this, p); }
            if (pd.homeKingdom == null)
                pd.homeKingdom = NameGenerator.realm(level.getSeed(), 3, (int) p.getX(), (int) p.getZ()).name();

            VillageData here = villageAt(p.getX(), p.getZ());
            if (here != null) {
                int amp = pd.allyVillages.contains(here.id) ? 2 : (here.ownerUuid == null && pd.allyKingdoms.contains(here.kingdom)) ? 1 : -1;
                if (amp >= 0) run("effect give " + p.getScoreboardName() + " minecraft:hero_of_the_village 30 " + amp + " true");
            }

            String hereId = here == null ? null : here.id;
            String prev = current.get(p.getUUID());
            if (Objects.equals(prev, hereId)) continue;
            if (here == null) {
                current.remove(p.getUUID());
                VillageData old = villages.get(prev);
                if (old != null)
                    p.connection.send(new ClientboundSetActionBarTextPacket(
                        Component.literal("Deixando " + old.name + "…").withStyle(ChatFormatting.GRAY)));
            } else {
                current.put(p.getUUID(), hereId);
                welcome(level, p, here);
            }
        }
    }

    private void welcome(ServerLevel level, ServerPlayer p, VillageData v) {
        ChatFormatting col = v.isPort() ? ChatFormatting.AQUA : ChatFormatting.GOLD;
        Component title = Component.literal(v.name).withStyle(col, ChatFormatting.BOLD);
        Component sub = Component.literal("Bem-vindo à " + v.typeLabel()).withStyle(ChatFormatting.YELLOW);
        Component bar = Component.literal(subtitleFor(v)).withStyle(ChatFormatting.WHITE);
        p.connection.send(new ClientboundSetTitlesAnimationPacket(8, 50, 15));
        p.connection.send(new ClientboundSetSubtitleTextPacket(sub));
        p.connection.send(new ClientboundSetTitleTextPacket(title));
        p.connection.send(new ClientboundSetActionBarTextPacket(bar));
        p.sendSystemMessage(Component.literal("Você chegou a " + v.name + ". Use /vila info para ver os nobres e detalhes.")
            .withStyle(ChatFormatting.GRAY));
        level.playSound(null, p.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.0F, 1.0F);
        if (visited.computeIfAbsent(p.getUUID(), k -> new HashSet<>()).add(v.id)) dirty = true;
    }

    private void processTracking() {
        if (tracking.isEmpty()) return;
        for (ServerPlayer p : server.overworld().players()) {
            if (!tracking.contains(p.getUUID())) continue;
            VillageData v = nearest(p.getX(), p.getZ());
            Component msg;
            if (v == null) {
                msg = Component.literal("Nenhuma vila conhecida por perto").withStyle(ChatFormatting.GRAY);
            } else {
                double dx = v.x - p.getX(), dz = v.z - p.getZ();
                msg = Component.literal(arrow(dx, dz, p.getYRot()) + " " + v.name + " · " + (int) Math.sqrt(dx * dx + dz * dz) + "m")
                    .withStyle(v.isPort() ? ChatFormatting.AQUA : ChatFormatting.GOLD);
            }
            p.connection.send(new ClientboundSetActionBarTextPacket(msg));
        }
    }

    // ───────────── impostos ─────────────
    private void processTaxes() {
        long day = server.overworld().getGameTime() / Math.max(1200, cfg.payDayTicks);
        for (var e : players.entrySet()) {
            PlayerData pd = e.getValue();
            int tax = taxFor(e.getKey());
            if (pd.lastPayDay < 0) { pd.lastPayDay = day; dirty = true; continue; }
            long days = Math.min(day - pd.lastPayDay, 5);
            if (days > 0) {
                pd.lastPayDay = day;
                if (tax > 0) pd.pending += (int) (tax * days);
                dirty = true;
            }
            if (pd.pending > 0) {
                ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
                if (p != null) {
                    int n = pd.pending;
                    pd.pending = 0;
                    Economy.give(this, p, n);
                    p.sendSystemMessage(Component.literal("💰 Seus impostos chegaram: " + n + " moedas.").withStyle(ChatFormatting.GREEN));
                    dirty = true;
                }
            }
        }
    }

    // ───────────── guerra: o reino inimigo ataca ─────────────
    private void processWars() {
        ServerLevel level = server.overworld();
        for (ServerPlayer p : level.players()) {
            PlayerData pd = players.get(p.getUUID());
            if (pd == null || pd.enemies.isEmpty()) continue;
            List<VillageData> mine = new ArrayList<>();
            for (VillageData v : owned(p.getUUID())) {
                double dx = v.x - p.getX(), dz = v.z - p.getZ();
                if (dx * dx + dz * dz < 160.0 * 160.0) mine.add(v);
            }
            if (mine.isEmpty()) continue;
            int n = 0;
            for (VillageData v : villages.values()) if (v.ownerUuid == null && pd.enemies.contains(v.kingdom)) n++;
            if (n == 0) continue;
            VillageData target = mine.get(rnd.nextInt(mine.size()));
            int wave = Math.min(4 + n / 2, 16);
            for (int i = 0; i < wave; i++) {
                double a = 2 * Math.PI * i / wave;
                int x = target.x + (int) Math.round(Math.cos(a) * (target.radius + 6));
                int z = target.z + (int) Math.round(Math.sin(a) * (target.radius + 6));
                if (!level.hasChunkAt(new BlockPos(x, 64, z))) continue;
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                run("summon minecraft:vindicator " + x + ".5 " + y + " " + z + ".5 {Tags:[\"nv_raider\"],Glowing:1b,"
                    + "CustomName:{text:\"Soldado inimigo\",color:\"red\"}}");
            }
            p.sendSystemMessage(Component.literal("⚔ Exército de " + String.join(", ", pd.enemies) + " (" + n + " vilas) marcha contra "
                + target.name + "!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        }
    }
}
