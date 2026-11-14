package br.nobrevilas;

/** Salvo em <mundo>/nobrevilas/config.json */
public class Config {
    public String guardModId = "guardvillagers";          // mod de aldeoes guerreiros (opcional)
    public String guardEntity = "guardvillagers:guard";   // entidade usada se o mod estiver instalado
    public String fallbackGuardEntity = "minecraft:iron_golem";
    public String currencyItem = "minecraft:emerald";   // qualquer item (inclusive de outros mods): moeda do mundo
    public String beaconBase = "minecraft:iron_block";   // base da piramide do farol
    public boolean cheatsEnabled = true;                   // /vila admin (somente criativo/espectador)
    public int allyVillageCost = 15;                      // esmeraldas
    public int allyKingdomCost = 60;
    public boolean autoCapitalKit = false;                 // true: toda capital recebe sozinha a muralha do kit
    public int payDayTicks = 24000;
    public int raidIntervalTicks = 6000;
}
