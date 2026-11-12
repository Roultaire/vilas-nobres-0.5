package br.nobrevilas;

import net.minecraft.server.level.ServerPlayer;

/** Moeda unica do mundo, definida em config.json (currencyItem). Funciona com itens de qualquer mod via comandos vanilla. */
final class Economy {
    private Economy() {}

    private static int exec(VillageManager m, String cmd) {
        try {
            var src = m.server().createCommandSourceStack().withSuppressedOutput();
            return m.server().getCommands().getDispatcher().execute(cmd, src);
        } catch (Exception e) {
            return -1;
        }
    }

    static int balance(VillageManager m, ServerPlayer p) {
        return Math.max(0, exec(m, "clear " + p.getScoreboardName() + " " + m.cfg().currencyItem + " 0"));
    }

    static boolean take(VillageManager m, ServerPlayer p, int n) {
        if (balance(m, p) < n) return false;
        return exec(m, "clear " + p.getScoreboardName() + " " + m.cfg().currencyItem + " " + n) >= 0;
    }

    static void give(VillageManager m, ServerPlayer p, int n) {
        if (exec(m, "give " + p.getScoreboardName() + " " + m.cfg().currencyItem + " " + n) < 0)
            exec(m, "give " + p.getScoreboardName() + " minecraft:emerald " + n);   // moeda invalida: usa esmeralda
    }
}
