package br.nobrevilas;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** /vila [info | localizar | lista | rastrear | rank | status | aliar [reino] | romper | rebelar <nome>] */
final class VilaCommand {
    private VilaCommand() {}

    static void register(CommandDispatcher<CommandSourceStack> d, CommandBuildContext ctx, Commands.CommandSelection env) {
        d.register(Commands.literal("vila")
            .executes(c -> info(c.getSource()))
            .then(Commands.literal("info").executes(c -> info(c.getSource())))
            .then(Commands.literal("localizar").executes(c -> locate(c.getSource())))
            .then(Commands.literal("lista").executes(c -> list(c.getSource())))
            .then(Commands.literal("rastrear").executes(c -> track(c.getSource())))
            .then(Commands.literal("rank").executes(c -> rank(c.getSource())))
            .then(Commands.literal("status").executes(c -> status(c.getSource())))
            .then(Commands.literal("livro").executes(c -> { Book.give(NobreVilas.manager(), c.getSource().getPlayerOrException()); return 1; }))
            .then(Commands.literal("aliar")
                .executes(c -> ally(c.getSource(), false))
                .then(Commands.literal("reino").executes(c -> ally(c.getSource(), true))))
            .then(Commands.literal("romper").executes(c -> breakAlly(c.getSource())))
            .then(Commands.literal("rebelar")
                .then(Commands.argument("nome", StringArgumentType.greedyString())
                    .executes(c -> rebel(c.getSource(), StringArgumentType.getString(c, "nome")))))
            .then(Commands.literal("ui")
                .then(Commands.literal("guia")
                    .executes(c -> Ui.guide(c.getSource(), "menu"))
                    .then(Commands.argument("pagina", StringArgumentType.word())
                        .executes(c -> Ui.guide(c.getSource(), StringArgumentType.getString(c, "pagina")))))
                .then(Commands.literal("falar")
                    .then(Commands.argument("alvo", StringArgumentType.word())
                        .executes(c -> Ui.talk(c.getSource(), StringArgumentType.getString(c, "alvo"), "menu"))
                        .then(Commands.argument("topico", StringArgumentType.word())
                            .executes(c -> Ui.talk(c.getSource(), StringArgumentType.getString(c, "alvo"), StringArgumentType.getString(c, "topico")))))))
            .then(Commands.literal("admin").requires(Admin::allowed)
                .then(Commands.literal("subir").executes(c -> Admin.adjust(c.getSource(), 1))
                    .then(Commands.argument("n", IntegerArgumentType.integer(1, 10))
                        .executes(c -> Admin.adjust(c.getSource(), IntegerArgumentType.getInteger(c, "n")))))
                .then(Commands.literal("descer").executes(c -> Admin.adjust(c.getSource(), -1))
                    .then(Commands.argument("n", IntegerArgumentType.integer(1, 10))
                        .executes(c -> Admin.adjust(c.getSource(), -IntegerArgumentType.getInteger(c, "n")))))
                .then(Commands.literal("definir")
                    .then(Commands.argument("nivel", IntegerArgumentType.integer(0, 10))
                        .executes(c -> Admin.setTier(c.getSource(), IntegerArgumentType.getInteger(c, "nivel")))))
                .then(Commands.literal("normal").executes(c -> Admin.normal(c.getSource())))
                .then(Commands.literal("assumir").executes(c -> Admin.take(c.getSource())))
                .then(Commands.literal("dominio")
                    .then(Commands.argument("nome", StringArgumentType.greedyString())
                        .executes(c -> Admin.domain(c.getSource(), StringArgumentType.getString(c, "nome")))))
                .then(Commands.literal("reino")
                    .then(Commands.argument("nome", StringArgumentType.greedyString())
                        .executes(c -> Admin.kingdom(c.getSource(), StringArgumentType.getString(c, "nome"), false))))
                .then(Commands.literal("imperio")
                    .then(Commands.argument("nome", StringArgumentType.greedyString())
                        .executes(c -> Admin.kingdom(c.getSource(), StringArgumentType.getString(c, "nome"), true))))
                .then(Commands.literal("anexar")
                    .then(Commands.argument("raio", IntegerArgumentType.integer(16, 5000))
                        .executes(c -> Admin.annex(c.getSource(), IntegerArgumentType.getInteger(c, "raio")))))
                .then(Commands.literal("reconstruir").executes(c -> Admin.rebuild(c.getSource()))).then(Commands.literal("castelo").executes(c -> Admin.castle(c.getSource())))));
    }

    private static void ok(CommandSourceStack src, Component c) { src.sendSuccess(() -> c, false); }
    private static void ok(CommandSourceStack src, String s, ChatFormatting f) { ok(src, Component.literal(s).withStyle(f)); }

    private static int info(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        VillageManager m = NobreVilas.manager();
        VillageData v = m.villageAt(p.getX(), p.getZ());
        if (v == null) v = m.nearest(p.getX(), p.getZ());
        if (v == null) { src.sendFailure(Component.literal("Nenhuma vila registrada ainda. Explore o mundo!")); return 0; }
        for (Component line : m.infoLines(v)) ok(src, line);
        return 1;
    }

    private static int locate(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        VillageData v = NobreVilas.manager().nearest(p.getX(), p.getZ());
        if (v == null) { src.sendFailure(Component.literal("Nenhuma vila registrada ainda.")); return 0; }
        double dx = v.x - p.getX(), dz = v.z - p.getZ();
        ok(src, VillageManager.arrow(dx, dz, p.getYRot()) + " " + v.name + " (" + v.typeLabel() + ") — " + v.x + ", " + v.z
            + " — a " + (int) Math.sqrt(dx * dx + dz * dz) + " blocos", ChatFormatting.YELLOW);
        return 1;
    }

    private static int list(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        List<VillageData> vs = NobreVilas.manager().visitedBy(p.getUUID());
        if (vs.isEmpty()) { src.sendFailure(Component.literal("Você ainda não visitou nenhuma vila.")); return 0; }
        ok(src, "Vilas visitadas (" + vs.size() + "):", ChatFormatting.GOLD);
        for (VillageData v : vs) ok(src, " • " + v.name + " — " + v.typeLabel() + ", " + v.county + " [" + v.x + ", " + v.z + "]", ChatFormatting.WHITE);
        return vs.size();
    }

    private static int track(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        boolean on = NobreVilas.manager().toggleTracking(p.getUUID());
        ok(src, on ? "Rastreador de vilas ativado." : "Rastreador de vilas desativado.", ChatFormatting.GREEN);
        return 1;
    }

    private static int rank(CommandSourceStack src) throws CommandSyntaxException {
        VillageManager m = NobreVilas.manager();
        List<VillageData> r = m.ranking();
        if (r.isEmpty()) { src.sendFailure(Component.literal("Nenhuma vila registrada ainda.")); return 0; }
        ok(src, "══ Ranking de vilas (poder e produtividade) ══", ChatFormatting.GOLD);
        for (int i = 0; i < Math.min(10, r.size()); i++) {
            VillageData v = r.get(i);
            String dono = v.ownerUuid != null ? v.ownerName + " (jogador)" : v.lord;
            ok(src, "#" + (i + 1) + " " + v.name + " — " + v.typeLabel() + " · " + dono + " · poder " + m.power(v)
                + " · " + m.productivity(v) + "/dia", i == 0 ? ChatFormatting.YELLOW : ChatFormatting.WHITE);
        }
        return r.size();
    }

    private static int status(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        VillageManager m = NobreVilas.manager();
        UUID id = p.getUUID();
        PlayerData pd = m.pd(id);
        int tier = m.playerTier(id);
        List<VillageData> mine = m.owned(id);
        ok(src, "══ " + Ranks.title(tier) + " " + p.getScoreboardName() + " ══", ChatFormatting.GOLD);
        ok(src, "Vilas: " + mine.size() + " · Imposto diário: " + m.taxFor(id) + " esmeraldas"
            + (pd.pending > 0 ? " · a receber: " + pd.pending : ""), ChatFormatting.WHITE);
        ok(src, "Reino: " + (pd.realm != null ? pd.realm : pd.homeKingdom), ChatFormatting.YELLOW);
        for (VillageData v : mine) ok(src, " • " + v.name + " (poder " + m.power(v) + ")", ChatFormatting.GRAY);
        ok(src, "Aliados: " + (pd.allyVillages.size() + pd.allyKingdoms.size()) + " · Em guerra com: "
            + (pd.enemies.isEmpty() ? "ninguém" : String.join(", ", pd.enemies)), ChatFormatting.AQUA);
        int next = tier < Ranks.COUNT_FOR.length ? Ranks.COUNT_FOR[tier] : -1;
        if (next > 0) ok(src, "Próximo título com " + next + " vilas.", ChatFormatting.GRAY);
        if (tier >= Ranks.DUKE && !pd.king) ok(src, "Você pode romper com o rei: /vila rebelar <nome do reino>", ChatFormatting.RED);
        return 1;
    }

    private static int ally(CommandSourceStack src, boolean kingdom) throws CommandSyntaxException {
        String err = Conquest.ally(NobreVilas.manager(), src.getPlayerOrException(), kingdom);
        if (err != null) { src.sendFailure(Component.literal(err)); return 0; }
        return 1;
    }

    private static int breakAlly(CommandSourceStack src) throws CommandSyntaxException {
        String err = Conquest.breakAlliance(NobreVilas.manager(), src.getPlayerOrException());
        if (err != null) { src.sendFailure(Component.literal(err)); return 0; }
        ok(src, "Aliança rompida.", ChatFormatting.YELLOW);
        return 1;
    }

    private static int rebel(CommandSourceStack src, String name) throws CommandSyntaxException {
        String err = Conquest.rebel(NobreVilas.manager(), src.getPlayerOrException(), name);
        if (err != null) { src.sendFailure(Component.literal(err)); return 0; }
        return 1;
    }
}
