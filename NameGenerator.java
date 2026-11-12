package br.nobrevilas;

import java.util.Random;

/** Gera nomes e nobres de forma deterministica a partir da semente do mundo + coordenadas. */
public final class NameGenerator {
    private NameGenerator() {}

    private static final String[] PRE = {"Eld","Bel","Thor","Mar","Val","Ash","Kor","Wyn","Dra","Lor","Fen","Gal","Hav",
        "Ist","Nor","Sol","Tar","Umb","Vor","Zan","Ald","Bram","Cor","Dun","Esk","Fal","Gren","Hol","Ith","Jor","Kel",
        "Lun","Mor","Nev","Oth","Pel","Quin","Rav","Sel","Tor"};
    private static final String[] SUF_GEN = {"eria","thryn","ford","wick","dor","ia","ora","vale","mont","gard","stone","hollow","heim","wyn"};
    private static final String[] SUF_PORT = {"haven","mouth","port","mere","bay","wharf","cove","tide","eria"};
    private static final String[] GIVEN_M = {"Aldric","Bertrand","Cedric","Doran","Edmund","Falk","Gareth","Hadrian","Ivor",
        "Joren","Kael","Lucan","Merrick","Osric","Percival","Roderic","Stefan","Tobias","Ulric","Viktor"};
    private static final String[] GIVEN_F = {"Adela","Brenna","Cecília","Dagny","Elara","Freya","Gwendolyn","Helena","Isolde",
        "Joana","Katarina","Liora","Maren","Nadia","Odette","Philippa","Rowena","Sibyl","Tamsin","Ysolde"};
    private static final String[] FAMILY = {"Valmorn","Kerrin","Ashgrove","Drakmoor","Hollowell","Thornwick","Stormvale",
        "Brightwater","Ironfell","Greymantle","Falconer","Redmarsh","Highcastle","Dunmere","Oakenshield","Blackwood"};

    public record Realm(String name, String ruler) {}

    public static long mix(long a, long b, long c, long d) {
        long h = a * 0x9E3779B97F4A7C15L;
        h ^= h >>> 29; h += b * 0xBF58476D1CE4E5B9L;
        h ^= h >>> 31; h += c * 0x94D049BB133111EBL;
        h ^= h >>> 27; h += d * 0x9E3779B97F4A7C15L;
        h ^= h >>> 33;
        return h;
    }

    private static <T> T pick(Random r, T[] arr) { return arr[r.nextInt(arr.length)]; }

    public static String placeName(Random r, boolean port) {
        String[] suf = (port && r.nextInt(10) < 7) ? SUF_PORT : SUF_GEN;
        return pick(r, PRE) + pick(r, suf);
    }

    private static String person(Random r, boolean female) {
        return pick(r, female ? GIVEN_F : GIVEN_M) + " " + pick(r, FAMILY);
    }

    /** tier 1 = condado (1024 blocos), 2 = ducado (4096), 3 = reino (16384). Celulas aninhadas. */
    public static Realm realm(long worldSeed, int tier, int x, int z) {
        int size = switch (tier) { case 1 -> 1024; case 2 -> 4096; default -> 16384; };
        int cx = Math.floorDiv(x, size), cz = Math.floorDiv(z, size);
        Random r = new Random(mix(worldSeed, tier, cx, cz));
        String base = placeName(r, false);
        boolean f = r.nextBoolean();
        String prefix, title;
        switch (tier) {
            case 1 -> { prefix = "Condado de "; title = f ? "Condessa" : "Conde"; }
            case 2 -> { prefix = "Ducado de ";  title = f ? "Duquesa" : "Duque"; }
            default -> { prefix = "Reino de ";  title = f ? "Rainha" : "Rei"; }
        }
        return new Realm(prefix + base, title + " " + person(r, f));
    }

    /** Retorna {senhor local, oficial, capitao}. */
    public static String[] localNobles(Random r, boolean port) {
        boolean f1 = r.nextBoolean(), f2 = r.nextBoolean(), f3 = r.nextBoolean();
        String lordTitle = port ? (f1 ? "Baronesa" : "Barão") : (f1 ? "Governadora" : "Governador");
        String offTitle = port ? (f2 ? "Mestra do Porto" : "Mestre do Porto") : (f2 ? "Intendente Colonial" : "Intendente Colonial");
        String capTitle = f3 ? "Capitã da Guarda" : "Capitão da Guarda";
        return new String[] { lordTitle + " " + person(r, f1), offTitle + " " + person(r, f2), capTitle + " " + person(r, f3) };
    }
}
