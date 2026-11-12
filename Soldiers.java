package br.nobrevilas;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

/** Soldados equipados conforme o posto da vila: ferro, ferro+elmo de diamante, diamante, netherite; capitao um degrau acima. */
final class Soldiers {
    private Soldiers() {}

    private static String it(String id) { return "{id:\"minecraft:" + id + "\",count:1}"; }

    static void spawn(VillageManager m, ServerLevel l, int x, int z, int tier, String kind, String name, String tags, String extra) {
        if (!l.hasChunkAt(new BlockPos(x, 64, z))) return;
        int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        boolean vanilla = m.guardEntity().equals(m.cfg().fallbackGuardEntity);
        boolean captain = kind.equals("captain"), archer = kind.equals("archer");
        String color = captain ? "gold" : archer ? "gray" : "white";

        StringBuilder nbt = new StringBuilder();
        nbt.append("Tags:[").append(tags).append("],PersistenceRequired:1b,CustomNameVisible:1b,CustomName:{text:\"")
            .append(name.replace("\"", "").replace("\\", "")).append("\",color:\"").append(color).append("\"}");
        if (!vanilla) {
            int t = captain ? tier + 3 : tier;
            String mat = t >= 9 ? "netherite" : t >= 7 ? "diamond" : "iron";
            String head = (t >= 4 && t < 7) ? "diamond" : mat;            // elite: elmo de diamante
            String weapon = archer ? "crossbow" : t >= 9 ? "netherite_sword" : t >= 7 ? "diamond_sword" : "iron_sword";
            nbt.append(",equipment:{head:").append(it(head + "_helmet"))
                .append(",chest:").append(it(mat + "_chestplate"))
                .append(",legs:").append(it(mat + "_leggings"))
                .append(",feet:").append(it(mat + "_boots"))
                .append(",mainhand:").append(it(weapon));
            if (!archer) nbt.append(",offhand:").append(it("shield"));
            nbt.append("},drop_chances:{head:0.0f,chest:0.0f,legs:0.0f,feet:0.0f,mainhand:0.0f,offhand:0.0f}");
            int hp = captain ? 80 : 40 + tier * 2;
            nbt.append(",Health:").append(hp).append(".0f,attributes:[{id:\"minecraft:max_health\",base:").append(hp)
                .append(".0d},{id:\"minecraft:armor\",base:").append(Math.min(20, 6 + tier)).append(".0d}]");
        }
        if (!extra.isEmpty()) nbt.append(",").append(extra);
        m.run("summon " + m.guardEntity() + " " + x + ".5 " + y + " " + z + ".5 {" + nbt + "}");
    }
}
