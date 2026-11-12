package br.nobrevilas;

import java.util.ArrayList;
import java.util.List;

public class PlayerData {
    public String name = "";
    public boolean king, emperor;
    public String realm;          // nome do reino/imperio proprio (se rei)
    public String homeKingdom;    // reino onde "reside"
    public int pending;
    public boolean gotBook;
    public int tierOverride = -1;   // cheat: -1 = progressao normal
    public String domain;           // esmeraldas a entregar
    public long lastPayDay = -1;
    public List<String> allyVillages = new ArrayList<>();
    public List<String> allyKingdoms = new ArrayList<>();
    public List<String> enemies = new ArrayList<>();   // reinos em guerra com o jogador
}
