package br.nobrevilas;

import net.minecraft.server.level.ServerPlayer;

/** Livro-guia entregue ao jogador na primeira vez que entra no mundo (e por /vila livro). */
final class Book {
    private Book() {}

    private static final String[] PAGES = {
        "Guia das Nobres Vilas\n\nCada vila tem nome, tipo (Portuária ou Colonial) e um governante que mora na mansão.\n\nAo chegar, um aviso mostra o nome. Use /vila info para ver os nobres e detalhes.",
        "Hierarquia\n\nBarão ou Governador > Conde > Duque > Príncipe > Rei > Imperador.\n\nToda vila pertence a um Condado, a um Ducado e a um Reino. A primeira vila descoberta de cada reino é a Capital, a mais forte.",
        "Farol e Muralha\n\nUm farol com feixe de luz marca cada vila: ciano é portuária, laranja é colonial.\n\nA muralha evolui com o título de quem governa: madeira, pedra, tijolos, deepslate, blackstone e quartzo.",
        "Conquista\n\nAtaque o governante na mansão da vila. Os guardas vão defendê-lo. Se ele cair, a vila é sua.\n\nVocê só ataca nobres até um nível acima do seu título. Plebeus começam atacando barões.",
        "Seu Título\n\n1 vila: Barão\n3 vilas: Conde\n6 vilas: Duque\n10 vilas: Príncipe\n\nDuque ou Príncipe pode usar /vila rebelar <nome> e virar Rei. Tomar a Capital do reino antigo faz de você Imperador.",
        "Alianças e Impostos\n\n/vila aliar e /vila aliar reino custam moedas e baixam os preços dos aldeões lá. Aliar-se a outro reino é traição e causa guerra.\n\nTodo dia você recebe impostos de 5 a 100 moedas, conforme o título e o número de vilas.",
        "Comandos\n\n/vila info\n/vila localizar\n/vila lista\n/vila rastrear\n/vila rank\n/vila status\n/vila aliar\n/vila romper\n/vila rebelar\n/vila livro"
    };

    static void give(VillageManager m, ServerPlayer p) {
        StringBuilder pages = new StringBuilder();
        for (String pg : PAGES) {
            if (pages.length() > 0) pages.append(",");
            pages.append("\"").append(pg.replace("\\", "").replace("\"", "'").replace("\n", "\\n")).append("\"");
        }
        m.run("give " + p.getScoreboardName() + " minecraft:written_book[minecraft:written_book_content={title:\"Guia das Nobres Vilas\","
            + "author:\"Cronista Real\",pages:[" + pages + "]}]");
    }
}
