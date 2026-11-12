package br.nobrevilas;

final class Ranks {
    private Ranks() {}
    static final String[] TITLES = {"Plebeu", "Barão", "Conde", "Duque", "Príncipe", "Rei", "Imperador"};
    static final int[] TAX = {0, 5, 10, 20, 40, 70, 100};   // esmeraldas/dia (base)
    static final String[] WALLS = {"-", "Paliçada de madeira", "Muralha de pedra", "Muralha de tijolos de pedra",
        "Muralha de deepslate", "Muralha de blackstone", "Muralha imperial de quartzo"};

    static String title(int t) { return TITLES[Math.max(0, Math.min(6, t))]; }
    static int tierForCount(int n) { return n >= 10 ? 4 : n >= 6 ? 3 : n >= 3 ? 2 : n >= 1 ? 1 : 0; }
}
