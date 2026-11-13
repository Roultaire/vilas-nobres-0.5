package br.nobrevilas;

import net.minecraft.server.level.ServerPlayer;

/** Livro-guia entregue ao jogador na primeira vez que entra no mundo (e por /vila livro). */
final class Book {
    private Book() {}

    private static final String[] PAGES = {
        "Guia das Nobres Vilas\n\nCada vila tem nome, tipo (Portuária ou Colonial), uma família nobre e um governante que mora na mansão.\n\nAo chegar, um aviso mostra o nome. Use /vila info para ver tudo.",
        "Os 10 Postos\n\n1 Cavaleiro\n2 Barão\n3 Visconde\n4 Conde\n5 Marquês\n6 Duque\n7 Arquiduque\n8 Príncipe\n9 Rei\n10 Imperador",
        "Reinos e Famílias\n\nCondado, Marquesado, Ducado, Principado e Reino formam a hierarquia. Cada reino tem uma Casa Real; a rainha é a consorte do rei, e os príncipes são da mesma casa.",
        "Farol, Muralha e Castelo\n\nUm farol com feixe de luz marca cada vila. A muralha evolui com o posto de quem governa, de madeira a quartzo. A capital do reino tem um castelo e a muralha mais forte.",
        "Conquista\n\nAtaque o governante na mansão. Soldados equipados defendem a vila. Se ele cair, a vila é sua.\n\nVocê só ataca nobres até um posto acima do seu. Mais vilas = posto maior.",
        "Seu Posto\n\nVilas: 1 Cavaleiro, 2 Barão, 3 Visconde, 5 Conde, 7 Marquês, 10 Duque, 14 Arquiduque, 19 Príncipe.\n\nDuque ou mais: /vila rebelar <nome> vira Rei. Tomar a capital do reino antigo faz de você Imperador.",
        "Alianças e Impostos\n\n/vila aliar e /vila aliar reino custam moedas e baixam os preços dos aldeões. Aliar-se a outro reino é traição e causa guerra.\n\nTodo dia você recebe impostos de 5 a 100 moedas, conforme o posto e as vilas.",
        "Interface (tecla N)\n\nAperte N no ar para abrir o guia do addon. Aperte N olhando para um NPC para conversar: governantes (aliança, rebelião, desafio), aldeões (rumores, capital, vizinhas) e soldados.\n\nVocê pode mudar a tecla em Controles.",
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
