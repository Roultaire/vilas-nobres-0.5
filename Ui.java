package br.nobrevilas;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.*;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Interface de dialogos (tecla N): usa os DIALOGOS NATIVOS do Minecraft (/dialog show), sem tela customizada.
 * N no ar = Guia do addon. N olhando para um NPC = conversa com perguntas e respostas (alianca, rebeliao etc.).
 */
final class Ui {
    private Ui() {}

    // ───────────── construcao de dialogos ─────────────
    record Btn(String label, String cmd, String tip, boolean dynamic) {}

    static Btn b(String label, String cmd) { return new Btn(label, cmd, null, false); }
    static Btn bt(String label, String cmd, String tip) { return new Btn(label, cmd, tip, false); }

    static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private static String btnSnbt(Btn b) {
        return "{label:\"" + esc(b.label()) + "\",width:280," + (b.tip() != null ? "tooltip:\"" + esc(b.tip()) + "\"," : "")
            + "action:{type:\"" + (b.dynamic() ? "minecraft:dynamic/run_command\",template:\"" : "minecraft:run_command\",command:\"")
            + esc(b.cmd()) + "\"}}";
    }

    static void show(VillageManager m, ServerPlayer p, String title, String color, String body, String inputs, List<Btn> actions) {
        List<String> acts = new ArrayList<>();
        for (Btn b : actions) acts.add(btnSnbt(b));
        String snbt = "{type:\"minecraft:multi_action\",title:{text:\"" + esc(title) + "\",color:\"" + color + "\",bold:true},"
            + "body:[{type:\"minecraft:plain_message\",contents:\"" + esc(body) + "\",width:320}],"
            + (inputs == null ? "" : "inputs:[" + inputs + "],")
            + "can_close_with_escape:true,pause:false,after_action:\"close\",columns:1,"
            + "exit_action:{label:\"Fechar\",width:280},"
            + "actions:[" + String.join(",", acts) + "]}";
        try {
            var src = m.server().createCommandSourceStack().withSuppressedOutput();
            m.server().getCommands().getDispatcher().execute("dialog show " + p.getScoreboardName() + " " + snbt, src);
        } catch (Exception e) {
            NobreVilas.LOG.warn("Falha ao abrir dialogo ({}). Usando o chat.", e.toString());
            p.sendSystemMessage(Component.literal("═ " + title + " ═").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            for (String line : body.split("\n")) p.sendSystemMessage(Component.literal(line).withStyle(ChatFormatting.WHITE));
            for (Btn x : actions) p.sendSystemMessage(Component.literal(" › " + x.label() + "  →  /" + x.cmd()).withStyle(ChatFormatting.GRAY));
        }
    }

    static void show(VillageManager m, ServerPlayer p, String title, String color, String body, List<Btn> actions) {
        show(m, p, title, color, body, null, actions);
    }

    // ───────────── guia do addon (N no ar) ─────────────
    static int guide(CommandSourceStack src, String page) throws CommandSyntaxException {
        guide(NobreVilas.manager(), src.getPlayerOrException(), page);
        return 1;
    }

    static void guide(VillageManager m, ServerPlayer p, String page) {
        Btn back = b("‹ Voltar ao guia", "vila ui guia menu");
        switch (page) {
            case "visao" -> show(m, p, "O que é o Nobre Vilas", "gold",
                "O mundo ganha uma sociedade feudal. Cada vila tem nome, tipo (Portuária ou Colonial), uma família nobre, um governante que mora na mansão, soldados e muralhas.\n\nVocê começa como plebeu e pode conquistar vilas, subir de posto, firmar alianças, romper com reis e até virar Imperador.\n\nAperte N olhando para um NPC para conversar; N no ar abre este guia.", List.of(back));
            case "postos" -> show(m, p, "Postos e Títulos", "gold",
                "Plebeu → Cavaleiro → Barão → Visconde → Conde → Marquês → Duque → Arquiduque → Príncipe → Rei → Imperador.\n\nSeu posto sobe com o número de vilas:\n1 Cavaleiro · 2 Barão · 3 Visconde · 5 Conde · 7 Marquês · 10 Duque · 14 Arquiduque · 19 Príncipe.\n\nRei só rompendo com o reino (Duque ou mais). Imperador tomando a capital do reino antigo.", List.of(back));
            case "familias" -> show(m, p, "Territórios e Famílias", "gold",
                "Vila → Condado → Marquesado → Ducado → Principado → Reino.\n\nCada reino tem uma Casa Real. O rei tem uma rainha consorte da mesma casa; os príncipes são da casa real; duques podem ser ramos dela; condes e marqueses têm casas próprias.\n\nA primeira vila descoberta de cada território vira a sede dele; a do reino é a Capital.", List.of(back));
            case "muralhas" -> show(m, p, "Muralhas, Farol e Castelos", "gold",
                "Um farol com feixe de luz marca cada vila (ciano = portuária, laranja = colonial).\n\nA muralha evolui com o posto de quem governa: madeira, pedra, tijolos, deepslate, blackstone e quartzo. A capital do reino tem a muralha mais forte e um castelo.\n\nSoldados equipados guardam a vila: o equipamento melhora com o posto.", List.of(back));
            case "conquista" -> show(m, p, "Conquista", "red",
                "O governante mora na mansão (ou no castelo). Ataque-o: os soldados da vila o defendem. Se ele cair, a vila é sua.\n\nRegra: você só ataca governantes de até 1 posto acima do seu.\n\nPara saber se pode atacar alguém, aperte N olhando para ele e escolha 'Desafiar o governante'.", List.of(back));
            case "rebeliao" -> show(m, p, "Rebelião e Império", "red",
                "Com posto de Duque ou mais, fale com um governante (N) e escolha 'Romper com o reino'. Dê um nome ao seu novo reino e vire Rei.\n\nTodas as vilas do reino antigo marcham contra você. Para acabar com a guerra, tome a Capital do reino antigo: você vira Imperador e o reino antigo passa ao seu império.", List.of(back));
            case "aliancas" -> show(m, p, "Alianças e Traição", "aqua",
                "Fale com um governante (N) e proponha uma aliança, com o nobre ou com o reino inteiro. Custa moedas.\n\nAliados dão desconto nas trocas com os aldeões da vila aliada.\n\nAliar-se a outro reino enquanto você 'mora' em um reino NPC é traição: o seu reino declara guerra.", List.of(back));
            case "impostos" -> show(m, p, "Impostos e Moedas", "green",
                "Todo dia de jogo você recebe impostos das suas vilas, de 5 a 100 moedas conforme o posto, +10% por vila extra.\n\nA moeda padrão é a esmeralda, e pode ser trocada no config.json por um item de outro mod.\n\nConquistas também rendem espólios.", List.of(back));
            case "comandos" -> show(m, p, "Comandos úteis", "gray",
                "/vila info · detalhes da vila\n/vila localizar · vila mais próxima\n/vila lista · vilas visitadas\n/vila rastrear · bússola de vilas\n/vila rank · ranking\n/vila status · seu posto\n/vila livro · livro-guia\n\nA maioria das ações também está nos diálogos (tecla N).", List.of(back));
            case "status" -> status(m, p, "vila ui guia menu");
            default -> show(m, p, "Guia do Nobre Vilas", "gold",
                "Escolha um tema. Dica: aperte N olhando para um NPC para conversar com ele.",
                List.of(b("O que é o Nobre Vilas", "vila ui guia visao"), b("Postos e Títulos", "vila ui guia postos"),
                    b("Territórios e Famílias", "vila ui guia familias"), b("Muralhas, Farol e Castelos", "vila ui guia muralhas"),
                    b("Conquista", "vila ui guia conquista"), b("Rebelião e Império", "vila ui guia rebeliao"),
                    b("Alianças e Traição", "vila ui guia aliancas"), b("Impostos e Moedas", "vila ui guia impostos"),
                    b("Comandos úteis", "vila ui guia comandos"), b("Meu status", "vila ui guia status")));
        }
    }

    private static void status(VillageManager m, ServerPlayer p, String backCmd) {
        UUID id = p.getUUID();
        PlayerData pd = m.pd(id);
        int tier = m.playerTier(id);
        List<VillageData> mine = m.owned(id);
        StringBuilder sb = new StringBuilder();
        sb.append(Ranks.title(tier)).append(" ").append(p.getScoreboardName()).append("\n");
        sb.append("Vilas: ").append(mine.size()).append(" · Imposto diário: ").append(m.taxFor(id)).append(" moedas\n");
        sb.append("Moedas no inventário: ").append(Economy.balance(m, p)).append("\n");
        sb.append("Reino: ").append(pd.realm != null ? pd.realm : pd.homeKingdom).append("\n");
        for (VillageData v : mine) sb.append(" • ").append(v.name).append(" (poder ").append(m.power(v)).append(")\n");
        sb.append("Aliados: ").append(pd.allyVillages.size() + pd.allyKingdoms.size()).append("\nEm guerra com: ")
            .append(pd.enemies.isEmpty() ? "ninguém" : String.join(", ", pd.enemies)).append("\n");
        if (tier < Ranks.COUNT_FOR.length) sb.append("Próximo posto com ").append(Ranks.COUNT_FOR[tier]).append(" vilas.");
        show(m, p, "Seu status", "gold", sb.toString(), List.of(b("‹ Voltar", backCmd)));
    }

    // ───────────── conversa com NPCs (N olhando para um NPC) ─────────────
    static int talk(CommandSourceStack src, String alvo, String topic) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        VillageManager m = NobreVilas.manager();
        Entity e = null;
        try { e = m.server().overworld().getEntity(UUID.fromString(alvo)); } catch (IllegalArgumentException ignored) {}
        if (e == null || e.distanceToSqr(p) > 144.0) { guide(m, p, "menu"); return 1; }

        String type = e.getType().toString();
        String lordVid = m.villageIdOfLord(e.getUUID());
        VillageData v = lordVid != null ? m.get(lordVid) : m.villageAt(e.getX(), e.getZ());
        String u = e.getUUID().toString();

        if (lordVid != null && v != null) lord(m, p, v, u, topic);
        else if (type.contains("wandering_trader")) trader(m, p);
        else if (v != null && (type.contains("guard") || type.contains("iron_golem"))) guard(m, p, v, u, topic);
        else if (v != null && type.contains("villager")) commoner(m, p, v, u, topic);
        else guide(m, p, "menu");
        return 1;
    }

    private static String talkCmd(String u, String topic) { return "vila ui falar " + u + " " + topic; }

    private static String trader(VillageManager m, ServerPlayer p) {
        show(m, p, "Mercador viajante", "yellow",
            "Viajo de vila em vila. Dizem que as estradas ficarão cheias de caravanas em breve... por enquanto, só eu e minhas lhamas.\n\nSe quer saber como o reino funciona, aperte N no ar para abrir o guia.",
            List.of(b("Abrir o guia", "vila ui guia menu")));
        return null;
    }

    // ----- governante -----
    private static void lord(VillageManager m, ServerPlayer p, VillageData v, String u, String topic) {
        UUID id = p.getUUID();
        PlayerData pd = m.pd(id);
        int mine = m.playerTier(id), theirs = m.tierOf(v);
        Btn back = b("‹ Voltar", talkCmd(u, "menu"));
        switch (topic) {
            case "sobre" -> {
                List<VillageData> rank = m.ranking();
                int pos = rank.indexOf(v) + 1;
                show(m, p, v.name, "gold",
                    v.typeLabel() + " · " + m.subtitleFor(v) + "\n\nGoverno: " + v.lord + "\nAdministração: " + v.officer + "\nGuarda: " + v.captain
                        + "\n\nMuralha: " + (v.isCapital() ? "Fortaleza do Reino" : Ranks.WALLS[Math.min(theirs, 10)])
                        + "\nPoder: " + m.power(v) + " · Produção: " + m.productivity(v) + "/dia\nRanking: #" + pos + " de " + rank.size(),
                    List.of(back));
            }
            case "reino" -> show(m, p, "O reino", "gold",
                "Condado: " + v.countRuler + " (" + v.county + ")\n"
                    + (v.marchName != null ? "Marquesado: " + v.marchRuler + " (" + v.marchName + ")\n" : "")
                    + "Ducado: " + v.dukeRuler + " (" + v.duchy + ")\n"
                    + (v.princeName != null ? "Principado: " + v.princeRuler + " (" + v.princeName + ")\n" : "")
                    + "Coroa: " + v.kingRuler + " (" + v.kingdom + ")"
                    + (v.kingConsort != null && v.ownerUuid == null ? "\nCorte: " + v.kingConsort : ""),
                List.of(back));
            case "aliar", "aliarreino" -> {
                boolean kingdom = topic.equals("aliarreino");
                int cost = kingdom ? m.cfg().allyKingdomCost : m.cfg().allyVillageCost;
                boolean already = kingdom ? pd.allyKingdoms.contains(v.kingdom) : pd.allyVillages.contains(v.id);
                String home = pd.homeKingdom;
                boolean treason = home != null && !home.equals(v.kingdom) && !home.equals(pd.realm);
                String body = "Aliança com " + (kingdom ? "o " + v.kingdom : v.lord) + ".\n\nCusto: " + cost + " moedas (você tem " + Economy.balance(m, p) + ").\n"
                    + "Benefício: preços menores nas trocas com os aldeões " + (kingdom ? "de todo o reino." : "de " + v.name + ".")
                    + (treason ? "\n\n⚠ Seu reino de origem é " + home + ". Aliar-se a " + v.kingdom + " é TRAIÇÃO: as vilas do seu reino declararão guerra a você." : "")
                    + (already ? "\n\nVocê já tem esta aliança." : "");
                List<Btn> acts = new ArrayList<>();
                if (!already) acts.add(bt("Confirmar aliança (" + cost + " moedas)", kingdom ? "vila aliar reino" : "vila aliar", "Cobra o valor do seu inventário"));
                acts.add(back);
                show(m, p, "Propor aliança", "aqua", body, acts);
            }
            case "romper" -> show(m, p, "Romper aliança", "red",
                "Romper a aliança com " + v.name + " e com o " + v.kingdom + " (se houver). Os descontos acabam.",
                List.of(b("Romper agora", "vila romper"), back));
            case "desafio" -> {
                String denied = Conquest.attackDenied(m, p, v);
                show(m, p, "Desafiar o governante", "red",
                    "Para conquistar " + v.name + ", mate " + v.lord + ". Os soldados da vila o defenderão assim que o primeiro golpe for dado.\n\n"
                        + (denied == null ? "✔ Você PODE atacá-lo agora (seu posto: " + Ranks.title(mine) + ", dele: " + Ranks.title(theirs) + ")." : "✘ " + denied)
                        + "\n\nConquistar rende espólios e aumenta o seu posto.",
                    List.of(back));
            }
            case "rebelar" -> {
                if (pd.king || mine < Ranks.DUKE) {
                    show(m, p, "Romper com o reino", "red",
                        pd.king ? "Você já é " + Ranks.title(mine) + " e tem o seu próprio reino."
                            : "Só um Duque ou posto superior (10+ vilas) pode romper com o rei.\nSeu posto: " + Ranks.title(mine) + " (" + m.owned(id).size() + " vilas).",
                        List.of(back));
                } else {
                    show(m, p, "Romper com o reino", "red",
                        "Você deixará o " + (pd.homeKingdom != null ? pd.homeKingdom : "reino") + " e fundará o seu. Todas as vilas do reino antigo marcharão contra você. Tome a capital para virar Imperador.\n\nEscolha o nome do novo reino:",
                        "{type:\"minecraft:text\",key:\"nome\",label:\"Nome do reino\",width:300,max_length:24,initial:\"\"}",
                        List.of(new Btn("Proclamar o reino", "vila rebelar $(nome)", null, true), back));
                }
            }
            case "status" -> status(m, p, talkCmd(u, "menu"));
            default -> {
                String greet;
                if (m.isAllied(id, v)) greet = "Meu aliado! As portas de " + v.name + " estão sempre abertas para você.";
                else if (mine == 0) greet = "Um plebeu diante de " + Ranks.title(theirs) + "? Diga o que quer, e seja breve.";
                else if (mine >= theirs) greet = "Vossa Senhoria honra a minha casa. Em que posso servir?";
                else greet = "Um " + Ranks.title(mine) + " em minhas terras. Fale, mas com respeito.";
                List<Btn> acts = new ArrayList<>();
                acts.add(b("Falar sobre a vila", talkCmd(u, "sobre")));
                acts.add(b("Propor aliança", talkCmd(u, "aliar")));
                acts.add(b("Aliança com o reino inteiro", talkCmd(u, "aliarreino")));
                if (m.isAllied(id, v)) acts.add(b("Romper aliança", talkCmd(u, "romper")));
                acts.add(b("Perguntar sobre o reino", talkCmd(u, "reino")));
                acts.add(b("Desafiar o governante", talkCmd(u, "desafio")));
                if (mine >= Ranks.DUKE && !pd.king) acts.add(b("Romper com o reino", talkCmd(u, "rebelar")));
                acts.add(b("Meu status", talkCmd(u, "status")));
                show(m, p, v.lord, theirs >= 8 ? "red" : "gold", greet + "\n\n" + v.typeLabel() + " de " + v.name + "\nSeu posto: " + Ranks.title(mine), acts);
            }
        }
    }

    // ----- aldeao comum -----
    private static void commoner(VillageManager m, ServerPlayer p, VillageData v, String u, String topic) {
        UUID id = p.getUUID();
        PlayerData pd = m.pd(id);
        Btn back = b("‹ Voltar", talkCmd(u, "menu"));
        switch (topic) {
            case "governa" -> show(m, p, "Quem governa?", "yellow",
                v.ownerUuid != null ? "Esta vila pertence a " + Ranks.title(m.tierOf(v)) + " " + v.ownerName + "."
                    : v.lord + " governa " + v.name + ".\nÉ vassalo de " + v.countRuler + ", do " + v.county + ".\nO reino é regido por " + v.kingRuler + ".",
                List.of(back));
            case "vila" -> {
                List<VillageData> rank = m.ranking();
                show(m, p, "Como está a vila?", "yellow",
                    "Somos uma " + v.typeLabel().toLowerCase() + ". Nossa muralha é " + (v.isCapital() ? "a fortaleza do reino" : Ranks.WALLS[Math.min(m.tierOf(v), 10)].toLowerCase())
                        + ".\nO poder de " + v.name + " é " + m.power(v) + ", o " + (rank.indexOf(v) + 1) + "º entre as " + rank.size() + " vilas que conheço.\nProduzimos cerca de " + m.productivity(v) + " por dia.",
                    List.of(back));
            }
            case "guerra" -> {
                String t;
                if (pd.enemies.contains(v.kingdom)) t = "Ouvi dizer que o " + v.kingdom + " está em guerra com VOCÊ. Cuidado por aqui, viajante.";
                else if (!pd.enemies.isEmpty()) t = "Dizem que " + String.join(", ", pd.enemies) + " marcha contra um nobre rebelde. Por aqui, a paz continua.";
                else t = "Por enquanto há paz. Que continue assim.";
                show(m, p, "Há guerra?", "yellow", t, List.of(back));
            }
            case "capital" -> {
                VillageData cap = null;
                for (VillageData o : m.all()) if (o.isNamed() && o.isCapital() && Objects.equals(o.kingdom, v.kingdom)) cap = o;
                if (cap == null) for (VillageData o : m.all()) if (o.isNamed() && o.isCapital() && Objects.equals(o.kingRuler, v.kingRuler)) cap = o;
                String t;
                if (cap == null) t = "A capital? Nunca estive lá. Ninguém por aqui sabe dizer onde fica.";
                else {
                    double dx = cap.x - p.getX(), dz = cap.z - p.getZ();
                    t = "A capital é " + cap.name + ", em " + cap.x + ", " + cap.z + ". " + VillageManager.arrow(dx, dz, p.getYRot())
                        + " a cerca de " + (int) Math.sqrt(dx * dx + dz * dz) + " blocos. É a mais bem guardada do reino.";
                }
                show(m, p, "Onde fica a capital?", "yellow", t, List.of(back));
            }
            case "vizinhas" -> {
                List<VillageData> l = new ArrayList<>();
                for (VillageData o : m.all()) if (o.isNamed() && o != v) l.add(o);
                l.sort(Comparator.comparingDouble(o -> (o.x - v.x) * (double) (o.x - v.x) + (o.z - v.z) * (double) (o.z - v.z)));
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < Math.min(3, l.size()); i++) {
                    VillageData o = l.get(i);
                    double dx = o.x - p.getX(), dz = o.z - p.getZ();
                    sb.append(VillageManager.arrow(dx, dz, p.getYRot())).append(" ").append(o.name).append(" (").append(o.typeLabel()).append(") — ")
                        .append((int) Math.sqrt(dx * dx + dz * dz)).append(" blocos\n");
                }
                show(m, p, "Vilas vizinhas", "yellow", sb.length() == 0 ? "Não conheço outras vilas por aqui." : sb.toString(), List.of(back));
            }
            case "impostos" -> show(m, p, "Impostos", "yellow",
                v.ownerUuid != null && v.ownerUuid.equals(id.toString())
                    ? "Os impostos de " + v.name + " vão para o senhor, ou seja, você. Seu total diário: " + m.taxFor(id) + " moedas."
                    : "Pagamos nossos impostos ao senhor da vila. Ouvi dizer que quem governa muitas vilas recebe até 100 moedas por dia.",
                List.of(back));
            default -> show(m, p, "Aldeão de " + v.name, "yellow",
                (m.isAllied(id, v) ? "Que bom ver um aliado de nosso senhor!" : "Bom dia, viajante.") + " O que deseja saber?",
                List.of(b("Quem governa a vila?", talkCmd(u, "governa")), b("Como está a vila?", talkCmd(u, "vila")),
                    b("Há guerra?", talkCmd(u, "guerra")), b("Onde fica a capital?", talkCmd(u, "capital")),
                    b("Vilas vizinhas", talkCmd(u, "vizinhas")), b("Sobre os impostos", talkCmd(u, "impostos"))));
        }
    }

    // ----- soldado -----
    private static void guard(VillageManager m, ServerPlayer p, VillageData v, String u, String topic) {
        PlayerData pd = m.pd(p.getUUID());
        Btn back = b("‹ Voltar", talkCmd(u, "menu"));
        switch (topic) {
            case "comando" -> show(m, p, "Quem comanda a guarda?", "gray",
                v.ownerUuid != null ? "Respondemos a " + v.ownerName + ", senhor de " + v.name + "." : v.captain + " comanda a guarda de " + v.name + ", sob as ordens de " + v.lord + ".",
                List.of(back));
            case "ameacas" -> show(m, p, "Há ameaças?", "gray",
                pd.enemies.contains(v.kingdom) ? "O " + v.kingdom + " está em guerra com você, viajante. Não dê motivos para sacarmos as armas."
                    : pd.enemies.isEmpty() ? "Tudo calmo. Patrulhamos o portão e a mansão dia e noite." : "Soldados de " + String.join(", ", pd.enemies) + " estão em marcha, mas não contra nós.",
                List.of(back));
            case "muralha" -> show(m, p, "Sobre a muralha", "gray",
                "A muralha de " + v.name + " é " + (v.isCapital() ? "uma fortaleza" : Ranks.WALLS[Math.min(m.tierOf(v), 10)].toLowerCase())
                    + ". Quanto mais alto o título de quem governa, mais forte ela fica.",
                List.of(back));
            default -> show(m, p, "Soldado de " + v.name, "gray",
                "Alto lá! Estamos de guarda. Diga rápido o que quer.",
                List.of(b("Quem comanda a guarda?", talkCmd(u, "comando")), b("Há ameaças?", talkCmd(u, "ameacas")), b("Sobre a muralha", talkCmd(u, "muralha"))));
        }
    }
}
