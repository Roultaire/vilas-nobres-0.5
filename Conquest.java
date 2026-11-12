package br.nobrevilas;

import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.core.BlockPos;

/** Dominacao, rebeliao, aliancas e traicao. */
final class Conquest {
    private Conquest() {}

    private static final int[] GUARDS = {0, 3, 4, 5, 6, 8, 9, 10, 12, 14, 16};

    static void register() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            String vid = lordVillage(entity);
            if (vid == null) return true;
            VillageManager m = NobreVilas.manager();
            VillageData v = m == null ? null : m.get(vid);
            if (v == null) return true;
            if (!(source.getEntity() instanceof ServerPlayer p)) return false;   // so jogadores ferem governantes
            String deny = attackDenied(m, p, v);
            if (deny != null) {
                p.sendSystemMessage(Component.literal(deny).withStyle(ChatFormatting.RED));
                return false;
            }
            startInvasion(m, p, v);
            return true;
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(source.getEntity() instanceof ServerPlayer killer)) return;
            VillageManager m = NobreVilas.manager();
            if (m == null) return;
            VillageData v = null;
            String vid = lordVillage(entity);
            if (vid != null) v = m.get(vid);
            else if (entity instanceof ServerPlayer victim) {            // matar o dono jogador dentro da vila dele
                VillageData here = m.villageAt(victim.getX(), victim.getZ());
                if (here != null && victim.getUUID().toString().equals(here.ownerUuid)
                    && attackDenied(m, killer, here) == null) v = here;
            }
            if (v != null) conquer(m, killer, v);
        });
    }

    private static String lordVillage(Entity e) {
        VillageManager m = NobreVilas.manager();
        return m == null ? null : m.villageIdOfLord(e.getUUID());
    }

    /** Regra: pode atacar governantes de ate 1 nivel acima do proprio titulo (ou qualquer nivel abaixo). */
    static String attackDenied(VillageManager m, ServerPlayer p, VillageData v) {
        UUID id = p.getUUID();
        if (id.toString().equals(v.ownerUuid)) return "Esta vila já é sua.";
        if (m.isAllied(id, v)) return "Você é aliado deste domínio. Use /vila romper para rompê-lo antes de atacar.";
        int mine = m.playerTier(id), theirs = m.tierOf(v);
        if (theirs > mine + 1)
            return "Seu título (" + Ranks.title(mine) + ") não permite atacar um " + Ranks.title(theirs) + ". Conquiste vilas para subir de posto.";
        return null;
    }

    static void startInvasion(VillageManager m, ServerPlayer p, VillageData v) {
        ServerLevel level = m.server().overworld();
        long now = level.getGameTime();
        if (now - v.lastInvasion < 3000) return;
        v.lastInvasion = now;
        int tier = m.tierOf(v);
        int count = (v.isCapital() && v.ownerUuid == null) ? 30 : GUARDS[Math.min(tier, 10)];
        UUID u = p.getUUID();
        long hi = u.getMostSignificantBits(), lo = u.getLeastSignificantBits();
        String ints = (int) (hi >> 32) + "," + (int) hi + "," + (int) (lo >> 32) + "," + (int) lo;
        String ang = "AngerTime:12000,AngryAt:[I;" + ints + "],angry_at:[I;" + ints + "],anger_end_time:" + (now + 12000) + "L";
        String tags = "\"nv_guard\",\"nv_v_" + v.id + "\"";
        int cx = v.manorBuilt ? v.rx : v.mx, cz = v.manorBuilt ? v.rz : v.mz;
        for (int i = 0; i < count; i++) {
            double a = 2 * Math.PI * i / count;
            int x = cx + (int) Math.round(Math.cos(a) * 7);
            int z = cz + (int) Math.round(Math.sin(a) * 7);
            boolean archer = i % 4 == 3;
            Soldiers.spawn(m, level, x, z, tier, archer ? "archer" : "guard",
                (archer ? "Arqueiro de " : "Defensor de ") + v.name, tags, ang);
        }
        p.sendSystemMessage(Component.literal("⚔ " + count + " soldados de " + v.name + " defendem o governante!")
            .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
    }

    static void conquer(VillageManager m, ServerPlayer p, VillageData v) {
        UUID id = p.getUUID();
        PlayerData pd = m.pd(id);
        pd.name = p.getScoreboardName();
        boolean first = m.owned(id).isEmpty();
        boolean wasCapitalNpc = v.isCapital() && v.ownerUuid == null;
        String oldKingdom = v.kingdom;
        String prevOwner = v.ownerName != null ? v.ownerName : v.lord;

        v.ownerUuid = id.toString();
        v.ownerName = pd.name;
        v.lordSpawned = true;
        v.garrisonSpawned = false;
        if (first && !pd.king && !pd.emperor) pd.homeKingdom = oldKingdom;
        if (pd.king || pd.emperor) {
            v.kingdom = pd.realm;
            v.kingRuler = Ranks.title(pd.emperor ? Ranks.EMPEROR : Ranks.KING) + " " + pd.name;
        }
        m.run("kill @e[tag=nv_v_" + v.id + "]");   // guardas e governante restantes

        boolean empire = false;
        if (wasCapitalNpc && pd.king && !pd.emperor) {
            pd.emperor = true;
            empire = true;
            String base = oldKingdom.startsWith("Reino de ") ? oldKingdom.substring(9) : oldKingdom;
            pd.realm = "Império de " + base;
            pd.enemies.remove(oldKingdom);
            for (VillageData o : m.all()) {
                if (oldKingdom.equals(o.kingdom) || (o.ownerUuid != null && o.ownerUuid.equals(id.toString()))) {
                    o.kingdom = pd.realm;
                    o.kingRuler = "Imperador " + pd.name;
                }
            }
        }
        m.refreshOwnerTitles(id);
        int spoils = 10 * m.tierOf(v);
        pd.pending += spoils;
        m.markDirty();

        var pl = m.server().getPlayerList();
        if (empire) {
            pl.broadcastSystemMessage(Component.literal("👑 " + pd.name + " tomou a capital " + v.name + " e proclamou o " + pd.realm + "!")
                .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), false);
        } else {
            pl.broadcastSystemMessage(Component.literal("⚔ " + pd.name + " dominou " + v.name + " e depôs " + prevOwner
                + ". Novo posto: " + Ranks.title(m.playerTier(id)) + ".").withStyle(ChatFormatting.GOLD), false);
        }
        p.sendSystemMessage(Component.literal("Espólios de guerra: " + spoils + " moedas (entregues junto dos impostos).")
            .withStyle(ChatFormatting.GREEN));
    }

    /** Duque ou Principe corta lacos com o rei e se proclama rei de um novo reino. */
    static String rebel(VillageManager m, ServerPlayer p, String rawName) {
        UUID id = p.getUUID();
        PlayerData pd = m.pd(id);
        int tier = m.playerTier(id);
        if (pd.king) return "Você já é " + Ranks.title(tier) + ".";
        if (tier < Ranks.DUKE) return "Só um Duque ou posto superior (10+ vilas) pode romper com o rei. Seu posto: " + Ranks.title(tier) + ".";
        String name = rawName.replaceAll("[^\\p{L}\\p{N} ]", "").trim();
        if (name.length() < 3 || name.length() > 24) return "Escolha um nome de 3 a 24 letras para o novo reino.";
        String old = pd.homeKingdom != null ? pd.homeKingdom : m.owned(id).get(0).kingdom;
        pd.king = true;
        pd.realm = "Reino de " + name;
        pd.homeKingdom = pd.realm;
        if (!pd.enemies.contains(old)) pd.enemies.add(old);
        pd.allyKingdoms.remove(old);
        for (VillageData v : m.owned(id)) { v.kingdom = pd.realm; v.kingRuler = "Rei " + pd.name; }
        m.refreshOwnerTitles(id);
        m.markDirty();
        m.server().getPlayerList().broadcastSystemMessage(Component.literal("🔥 " + pd.name + " rompeu com o " + old + " e fundou o "
            + pd.realm + "! Todas as vilas do reino antigo marcham contra ele. Tome a capital para se tornar Imperador.")
            .withStyle(ChatFormatting.RED, ChatFormatting.BOLD), false);
        return null;
    }

    /** Cheat: toma a vila sem combate, sem guerra e sem avisos. */
    static void adminTake(VillageManager m, ServerPlayer p, VillageData v) {
        UUID id = p.getUUID();
        PlayerData pd = m.pd(id);
        pd.name = p.getScoreboardName();
        boolean first = m.owned(id).isEmpty();
        v.ownerUuid = id.toString();
        v.ownerName = pd.name;
        v.lordSpawned = true;
        v.garrisonSpawned = false;
        if (first && !pd.king && !pd.emperor) pd.homeKingdom = v.kingdom;
        if (pd.king || pd.emperor) { v.kingdom = pd.realm; v.kingRuler = Ranks.title(pd.emperor ? Ranks.EMPEROR : Ranks.KING) + " " + pd.name; }
        if (pd.domain != null) { v.county = "Domínio de " + pd.domain; v.countRuler = Ranks.title(m.playerTier(id)) + " " + pd.name; }
        m.run("kill @e[tag=nv_v_" + v.id + "]");
        m.refreshOwnerTitles(id);
        m.markDirty();
    }

    static boolean takeEmeralds(ServerPlayer p, int n) {
        Inventory inv = p.getInventory();
        int have = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(Items.EMERALD)) have += s.getCount();
        }
        if (have < n) return false;
        int left = n;
        for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(Items.EMERALD)) { int t = Math.min(left, s.getCount()); s.shrink(t); left -= t; }
        }
        return true;
    }

    /** Alianca com o nobre da vila atual ou com o reino inteiro. Retorna mensagem de erro ou null. */
    static String ally(VillageManager m, ServerPlayer p, boolean kingdom) {
        VillageData v = m.villageAt(p.getX(), p.getZ());
        if (v == null) return "Entre em uma vila para propor uma aliança.";
        UUID id = p.getUUID();
        PlayerData pd = m.pd(id);
        if (id.toString().equals(v.ownerUuid)) return "Você não pode se aliar a si mesmo.";
        if (kingdom ? pd.allyKingdoms.contains(v.kingdom) : pd.allyVillages.contains(v.id)) return "Aliança já existente.";
        int cost = kingdom ? m.cfg().allyKingdomCost : m.cfg().allyVillageCost;
        if (!Economy.take(m, p, cost)) return "Tributo insuficiente: a aliança custa " + cost + " moedas.";
        if (kingdom) pd.allyKingdoms.add(v.kingdom); else pd.allyVillages.add(v.id);
        pd.enemies.remove(v.kingdom);   // aliar-se com um reino em guerra faz as pazes
        String msg = "Aliança firmada com " + (kingdom ? v.kingdom : v.name) + "! Trocas com aldeões ficam mais baratas lá.";
        p.sendSystemMessage(Component.literal(msg).withStyle(ChatFormatting.GREEN));
        // traicao: aliar-se a outro reino enquanto reside em um reino NPC
        String home = pd.homeKingdom;
        boolean homeIsNpc = home != null && !home.equals(pd.realm);
        if (homeIsNpc && !home.equals(v.kingdom) && !pd.enemies.contains(home)) {
            pd.enemies.add(home);
            m.server().getPlayerList().broadcastSystemMessage(Component.literal("🗡 " + pd.name + " traiu o " + home + " ao se aliar com "
                + v.kingdom + "! As vilas do reino traído declaram guerra.").withStyle(ChatFormatting.DARK_RED), false);
        }
        m.markDirty();
        return null;
    }

    static String breakAlliance(VillageManager m, ServerPlayer p) {
        VillageData v = m.villageAt(p.getX(), p.getZ());
        if (v == null) return "Entre na vila cuja aliança deseja romper.";
        PlayerData pd = m.pd(p.getUUID());
        boolean a = pd.allyVillages.remove(v.id);
        boolean b = pd.allyKingdoms.remove(v.kingdom);
        if (!a && !b) return "Você não tem aliança aqui.";
        m.markDirty();
        return null;
    }
}
