package br.nobrevilas;

import java.util.Random;

/** Nomes e familias deterministicos (semente do mundo + coordenadas). Cada reino tem uma Casa Real propria. */
public final class NameGenerator {
    private NameGenerator() {}

    private static final String[] PRE = {"Eld","Bel","Thor","Mar","Val","Ash","Kor","Wyn","Dra","Lor","Fen","Gal","Hav",
        "Ist","Nor","Sol","Tar","Umb","Vor","Zan","Ald","Bram","Cor","Dun","Esk","Fal","Gren","Hol","Ith","Jor","Kel",
        "Lun","Mor","Nev","Oth","Pel","Quin","Rav","Sel","Tor"};
    private static final String[] SUF_GEN = {"eria","thryn","ford","wick","dor","ia","ora","vale","mont","gard","stone","hollow","heim","wyn"};
    private static final String[] SUF_PORT = {"haven","mouth","port","mere","bay","wharf","cove","tide","eria"};
    private static final String[] GIVEN_M = {"Aldric","Bertrand","Cedric","Doran","Edmund","Falk","Gareth","Hadrian","Ivor",
        "Joren","Kael","Lucan","Merrick","Osric","Percival","Roderic","Stefan","Tobias","Ulric","Viktor","Alaric","Branwen","Corvin","Dunstan"};
    private static final String[] GIVEN_F = {"Adela","Brenna","Cecília","Dagny","Elara","Freya","Gwendolyn","Helena","Isolde",
        "Joana","Katarina","Liora","Maren","Nadia","Odette","Philippa","Rowena","Sibyl","Tamsin","Ysolde","Alina","Beatriz","Clarisse","Dorotea"};
    private static final String[] HOUSE_PRE = {"Val","Kerr","Ash","Drak","Hol","Thorn","Storm","Bright","Iron","Grey","Falcon","Red",
        "High","Dun","Oak","Black","Ward","Rook","Wolf","Hawk","Stone","Frost","Gold","Silver","Raven","Lion","Bran","Cald","Mor",
        "Ever","Fair","Grim","Hart","Lang","Mer","North","Stan","Tyr","West","Wynd"};
    private static final String[] HOUSE_SUF = {"morn","ford","wick","vale","moor","well","mantle","water","fell","marsh","castle",
        "shield","wood","crest","born","hall","ridge","gate"};

    public record Realm(String name, String ruler, String consort, String house) {}

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

    private static String surname(Random r) { return pick(r, HOUSE_PRE) + pick(r, HOUSE_SUF); }

    private static String distinct(Random r, String... excl) {
        for (int i = 0; i < 40; i++) {
            String s = surname(r);
            boolean bad = false;
            for (String e : excl) if (s.equals(e)) bad = true;
            if (!bad) return s;
        }
        return surname(r);
    }

    /** tier: 1 condado(1024) 4 marquesado(2048) 2 ducado(4096) 5 principado(8192) 3 reino(16384). */
    private static int sizeOf(int tier) {
        return switch (tier) { case 1 -> 1024; case 4 -> 2048; case 2 -> 4096; case 5 -> 8192; default -> 16384; };
    }

    /**
     * Casa (sobrenome) do governante. Reino: casa real propria. Principe: sempre da casa real.
     * Duque: 50% ramo da casa real. Marques/Conde: casas proprias, diferentes das superiores (alguns ramos do duque).
     */
    static String houseOf(long seed, int tier, int x, int z) {
        int size = sizeOf(tier);
        int cx = Math.floorDiv(x, size), cz = Math.floorDiv(z, size);
        Random r = new Random(mix(seed, 5000 + tier, cx, cz));
        switch (tier) {
            case 3: return surname(r);
            case 5: return houseOf(seed, 3, x, z);
            case 2: {
                String royal = houseOf(seed, 3, x, z);
                return r.nextInt(100) < 50 ? royal : distinct(r, royal);
            }
            case 4: {
                String royal = houseOf(seed, 3, x, z), duke = houseOf(seed, 2, x, z);
                return r.nextInt(100) < 20 ? duke : distinct(r, royal, duke);
            }
            default: {
                String royal = houseOf(seed, 3, x, z), duke = houseOf(seed, 2, x, z), march = houseOf(seed, 4, x, z);
                return r.nextInt(100) < 25 ? duke : distinct(r, royal, duke, march);
            }
        }
    }

    public static Realm realm(long seed, int tier, int x, int z) {
        int size = sizeOf(tier);
        int cx = Math.floorDiv(x, size), cz = Math.floorDiv(z, size);
        Random r = new Random(mix(seed, tier, cx, cz));
        String base = placeName(r, false);                       // mantem os nomes antigos dos territorios
        Random g = new Random(mix(seed, 6000 + tier, cx, cz));
        boolean female = g.nextBoolean();
        String house = houseOf(seed, tier, x, z);
        int rank = switch (tier) { case 1 -> 4; case 4 -> 5; case 2 -> 6; case 5 -> 8; default -> 9; };
        String prefix = switch (tier) {
            case 1 -> "Condado de "; case 4 -> "Marquesado de "; case 2 -> "Ducado de ";
            case 5 -> "Principado de "; default -> "Reino de ";
        };
        String given = pick(g, female ? GIVEN_F : GIVEN_M);
        String spouse = pick(g, female ? GIVEN_M : GIVEN_F);
        String ruler = Ranks.title(rank, female) + " " + given + " " + house;
        String consort = female ? "Consorte " + spouse + " " + house : Ranks.title(rank, true) + " consorte " + spouse + " " + house;
        return new Realm(prefix + base, ruler, consort, house);
    }

    /** {senhor local, oficial, capitao}. rank 1..3 (Cavaleiro, Barao, Visconde). */
    public static String[] localNobles(Random r, boolean port, int rank) {
        boolean f1 = r.nextBoolean(), f2 = r.nextBoolean(), f3 = r.nextBoolean();
        String house = surname(r);
        String lord = Ranks.title(rank, f1) + " " + pick(r, f1 ? GIVEN_F : GIVEN_M) + " " + house;
        String off = port ? (f2 ? "Mestra do Porto" : "Mestre do Porto") : "Intendente Colonial";
        String offHouse = r.nextInt(100) < 40 ? house : surname(r);
        String cap = f3 ? "Capitã da Guarda" : "Capitão da Guarda";
        return new String[] {
            lord,
            off + " " + pick(r, f2 ? GIVEN_F : GIVEN_M) + " " + offHouse,
            cap + " " + pick(r, f3 ? GIVEN_F : GIVEN_M) + " " + surname(r)
        };
    }
}
