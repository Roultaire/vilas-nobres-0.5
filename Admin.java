package br.nobrevilas;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** /vila admin ... — cheats. Liberados apenas para quem esta em criativo/espectador (e se cheatsEnabled=true no config). */
final class Admin {
    private Admin() {}

    static boolean allowed(CommandSourceStack src) {
        VillageManager m = NobreVilas.manager();
        if (m == null || !m.cfg().cheatsEnabled) return false;
        ServerPlayer p = src.getPlayer();
        return p != null && (p.isCreative() || p.isSpectator());
    }

    private static int ok(CommandSourceStack src, String msg) {
        src.sendSuccess(() -> Component.literal(msg).withStyle(ChatFormatting.LIGHT_PURPLE), false);
        return 1;
    }

    private static int fail(CommandSourceStack src, String msg) {
        src.sendFailure(Component.literal(msg));
        return 0;
    }

    private static String clean(String s) { return s.replaceAll("[^\\p{L}\\p{N} ]", "").trim(); }

    static int setTier(CommandSourceStack src, int t) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        VillageManager m = NobreVilas.manager();
        PlayerData pd = m.pd(p.getUUID());
        pd.tierOverride = Math.max(0, Math.min(Ranks.EMPEROR, t));
        pd.name = p.getScoreboardName();
        m.refreshOwnerTitles(p.getUUID());
        m.markDirty();
        return ok(src, "Posto definido: " + Ranks.title(pd.tierOverride) + " (muralhas das suas vilas serão atualizadas).");
    }

    static int adjust(CommandSourceStack src, int delta) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        return setTier(src, NobreVilas.manager().playerTier(p.getUUID()) + delta);
    }

    static int normal(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        VillageManager m = NobreVilas.manager();
        m.pd(p.getUUID()).tierOverride = -1;
        m.refreshOwnerTitles(p.getUUID());
        m.markDirty();
        return ok(src, "Progressão normal restaurada: " + Ranks.title(m.playerTier(p.getUUID())) + ".");
    }

    static int take(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        VillageManager m = NobreVilas.manager();
        VillageData v = m.villageAt(p.getX(), p.getZ());
        if (v == null) return fail(src, "Entre em uma vila.");
        Conquest.adminTake(m, p, v);
        return ok(src, "Você assumiu " + v.name + ".");
    }

    static int kingdom(CommandSourceStack src, String raw, boolean empire) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        VillageManager m = NobreVilas.manager();
        String name = clean(raw);
        if (name.length() < 3) return fail(src, "Nome muito curto.");
        UUID id = p.getUUID();
        PlayerData pd = m.pd(id);
        pd.name = p.getScoreboardName();
        pd.king = true;
        pd.emperor = empire;
        pd.realm = (empire ? "Império de " : "Reino de ") + name;
        pd.homeKingdom = pd.realm;
        for (VillageData v : m.owned(id)) { v.kingdom = pd.realm; v.kingRuler = (empire ? "Imperador " : "Rei ") + pd.name; }
        m.refreshOwnerTitles(id);
        m.markDirty();
        return ok(src, (empire ? "Império" : "Reino") + " criado: " + pd.realm + ". Anexe vilas com /vila admin anexar <raio>.");
    }

    static int domain(CommandSourceStack src, String raw) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        VillageManager m = NobreVilas.manager();
        String name = clean(raw);
        if (name.length() < 3) return fail(src, "Nome muito curto.");
        UUID id = p.getUUID();
        PlayerData pd = m.pd(id);
        pd.name = p.getScoreboardName();
        pd.domain = name;
        for (VillageData v : m.owned(id)) { v.county = "Domínio de " + name; v.countRuler = Ranks.title(m.playerTier(id)) + " " + pd.name; }
        m.markDirty();
        return ok(src, "Domínio nobre criado: Domínio de " + name + ".");
    }

    static int annex(CommandSourceStack src, int radius) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        VillageManager m = NobreVilas.manager();
        int n = 0;
        for (VillageData v : new java.util.ArrayList<>(m.all())) {
            if (!v.isNamed() || p.getUUID().toString().equals(v.ownerUuid)) continue;
            double dx = v.x - p.getX(), dz = v.z - p.getZ();
            if (dx * dx + dz * dz <= (double) radius * radius) { Conquest.adminTake(m, p, v); n++; }
        }
        return ok(src, n + " vila(s) anexada(s) ao seu domínio.");
    }

    static int castle(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        VillageManager m = NobreVilas.manager();
        VillageData v = m.villageAt(p.getX(), p.getZ());
        if (v == null) return fail(src, "Entre em uma vila.");
        v.castleBuilt = false;
        v.forceCastle = true;
        m.markDirty();
        return ok(src, "Castelo agendado em " + v.name + " (fique perto para os chunks carregarem).");
    }

    static int rebuild(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        VillageManager m = NobreVilas.manager();
        VillageData v = m.villageAt(p.getX(), p.getZ());
        if (v == null) v = m.nearest(p.getX(), p.getZ());
        if (v == null) return fail(src, "Nenhuma vila registrada.");
        v.buildVersion = 0;
        v.wallKey = 0;
        m.markDirty();
        return ok(src, "Reconstrução de " + v.name + " agendada (fique perto dela para os chunks carregarem).");
    }
}
