package br.nobrevilas;

/** POJO simples, salvo em JSON via Gson. */
public class VillageData {
    public String id;                 // "x_z" do centro
    public int x, y, z, radius;
    public String type;               // PORT | COLONIAL | null (pendente)
    public String name;
    public String county, duchy, kingdom;
    public String countRuler, dukeRuler, kingRuler;
    public String lord, officer, captain;
    public boolean markerBuilt;
    public int mx, my, mz;
    // v2
    public int seatTier;              // 1 vila comum, 2 sede do condado, 3 sede do ducado, 5 capital do reino
    public String ownerUuid, ownerName;   // dono jogador (null = governante NPC)
    public int wallKey;               // tier*10 + (capital?1:0) da muralha ja construida
    public boolean lordSpawned;
    public String lordUuid;           // UUID da entidade do governante NPC
    public long lastInvasion = -100000;
    public int buildVersion;          // 2 = farol com piramide + mansao + muralha continua
    public boolean manorBuilt;
    public int rx, ry, rz;            // residencia (salao da mansao)

    public boolean isNamed() { return type != null && name != null; }
    public boolean isPort() { return "PORT".equals(type); }
    public boolean isCapital() { return seatTier == 5; }
    public String typeLabel() { return isPort() ? "Vila Portuária" : "Vila Colonial"; }
}
