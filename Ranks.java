package br.nobrevilas;

/** Escada nobiliarquica: 0 Plebeu ... 10 Imperador. */
final class Ranks {
    private Ranks() {}

    static final String[] TITLES = {"Plebeu", "Cavaleiro", "Barão", "Visconde", "Conde", "Marquês", "Duque", "Arquiduque", "Príncipe", "Rei", "Imperador"};
    static final String[] TITLES_F = {"Plebeia", "Dama", "Baronesa", "Viscondessa", "Condessa", "Marquesa", "Duquesa", "Arquiduquesa", "Princesa", "Rainha", "Imperatriz"};
    static final int DUKE = 6, KING = 9, EMPEROR = 10;
    /** imposto diario base (esmeraldas/moedas) por posto: de 5 a 100 */
    static final int[] TAX = {0, 5, 8, 12, 18, 26, 36, 50, 68, 85, 100};
    /** numero de vilas para alcancar os postos 1..8 */
    static final int[] COUNT_FOR = {1, 2, 3, 5, 7, 10, 14, 19};
    static final String[] WALLS = {"-", "Paliçada de carvalho", "Paliçada de carvalho escuro", "Muralha de pedra bruta",
        "Muralha de tijolos de pedra", "Muralha dupla de tijolos", "Muralha de deepslate", "Muralha de deepslate polido",
        "Muralha de blackstone", "Muralha real de tijolos rubros", "Muralha imperial de quartzo"};

    private static int c(int t) { return Math.max(0, Math.min(10, t)); }
    static String title(int t) { return TITLES[c(t)]; }
    static String title(int t, boolean female) { return (female ? TITLES_F : TITLES)[c(t)]; }

    static int tierForCount(int n) {
        int t = 0;
        for (int i = 0; i < COUNT_FOR.length; i++) if (n >= COUNT_FOR[i]) t = i + 1;
        return t;
    }
}
