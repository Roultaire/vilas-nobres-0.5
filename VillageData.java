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
    public int seatTier;              // posto do governante NPC (1 Cavaleiro ... 9 Rei)
    public String ownerUuid, ownerName;
    public int wallKey;
    public boolean lordSpawned;
    public String lordUuid;
    public long lastInvasion = -100000;
    public int buildVersion;
    public boolean manorBuilt;
    public int rx, ry, rz;            // residencia (salao da mansao/castelo)
    // v3
    public int schema;
    public String marchName, marchRuler, princeName, princeRuler;
    public String countConsort, marchConsort, dukeConsort, princeConsort, kingConsort;
    public boolean castleBuilt, forceCastle, garrisonSpawned;
    public boolean kitBuilt, forceKit;   // perimetro da capital feito com o kit de estruturas

    public boolean isNamed() { return type != null && name != null; }
    public boolean isPort() { return "PORT".equals(type); }
    public boolean isCapital() { return seatTier == Ranks.KING; }
    public String typeLabel() { return isPort() ? "Vila Portuária" : "Vila Colonial"; }
}
