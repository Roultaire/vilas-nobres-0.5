# Nobre Vilas (Fabric, Minecraft Java 26.3)

## Vilas
Nome, tipo (Portuária se houver água por perto / Colonial), nobres locais e suserania Condado → Ducado → Reino.
Título "Bem-vindo a X", ficha no chat, farol com feixe colorido e placa flutuante visíveis de longe.
A 1ª vila descoberta de cada região de reino vira a **Capital**; as primeiras de cada ducado/condado viram **sedes**.

## Muralhas (evoluem com o título de quem governa)
Barão: paliçada de madeira · Conde: pedra · Duque: tijolos de pedra · Príncipe: deepslate ·
Rei: blackstone · Imperador: quartzo · Capital do reino: fortaleza (3 de espessura, mais alta). 4 portões, torres, porto aberto sobre a água.

## Dominação
O governante NPC (um aldeão) reside no Paço (plataforma do farol). Ao bater nele, os guardas se enfurecem
(Capital = 30 guardas). Matar o governante = vila é sua. Matar o dono jogador dentro da vila dele também conquista.
Posto por nº de vilas: 1 Barão · 3 Conde · 6 Duque · 10 Príncipe. Você só ataca governantes até **1 nível acima** do seu posto.
**Rebelião:** Duque/Príncipe usa `/vila rebelar <nome>` → vira Rei de um reino novo; todas as vilas do reino antigo
mandam exércitos contra você. Tome a Capital do reino antigo → **Imperador** (e o reino antigo vira seu império).

## Alianças
`/vila aliar` (15 esmeraldas) com o nobre da vila onde está; `/vila aliar reino` (60) com o reino todo.
Aliados dão desconto de troca com aldeões (efeito Herói da Vila nas vilas aliadas). Aliar-se a outro reino
enquanto reside em um reino NPC = **traição**: o seu reino declara guerra. `/vila romper` desfaz.

## Impostos
Por dia de jogo, 5 a 100 esmeraldas conforme o posto (5/10/20/40/70/100), +10% por vila extra. Entregues no inventário.

## Ranking
`/vila rank` – top 10 por poder e produtividade. `/vila status` – seu posto, vilas, imposto, aliados e inimigos.

## Compatibilidade com aldeões guerreiros
Se o mod **Guard Villagers** (`guardvillagers`) estiver instalado, os guardas da invasão são `guardvillagers:guard`;
senão, golens de ferro. Ajuste em `<mundo>/nobrevilas/config.json` (também custos e intervalos).

Build: `gradle build` com Java 25 (ou o workflow do GitHub Actions incluso). Só precisa estar no servidor/mundo local.

## v1.1
- Farol com piramide (feixe visivel), mansao do governante, muralha continua com 4 portoes (o sul tem torres de guarda).
- Governantes circulam pela vila de dia e voltam a mansao a noite.
- Boas-vindas compactas (titulo curto + barra de acao). Livro-guia entregue no primeiro spawn (/vila livro).
- Moeda configuravel (config.json: currencyItem) – aceita itens de outros mods.
- Cheats: /vila admin subir|descer|definir|normal|assumir|dominio|reino|imperio|anexar|reconstruir (criativo/espectador).
- Vilas antigas sao reconstruidas sozinhas quando voce chega perto (ou /vila admin reconstruir).

## v1.2
- 10 postos: Plebeu, Cavaleiro, Barao, Visconde, Conde, Marques, Duque, Arquiduque, Principe, Rei, Imperador (imposto de 5 a 100).
- Territorios: Condado, Marquesado, Ducado, Principado e Reino. Cada reino tem Casa Real propria (sobrenomes unicos por reino),
  rainha consorte, principes da mesma casa; condes/marqueses de casas proprias.
- Muralha suave (inclinacao maxima 1:3), portoes em arco com rampas nos dois lados; portao sul com torres de guarda.
- Soldados equipados (ferro -> diamante -> netherite) + capitao; guarnicao permanente por vila.
- Castelo na capital do reino e do imperador (`/vila admin castelo` forca em qualquer vila).

## v1.3 – Interface de dialogos (tecla N)
- N no ar: guia do addon (dialogo nativo do Minecraft). N olhando para um NPC: conversa com perguntas e respostas.
- Governante: sobre a vila, alianca (nobre ou reino, com aviso de traicao), romper alianca, desafiar (mostra se voce pode atacar), romper com o reino (campo de texto para o nome), status.
- Aldeao: quem governa, como esta a vila, guerra, onde fica a capital, vilas vizinhas, impostos. Soldado: comando, ameacas, muralha.
- Precisa do mod tambem no CLIENTE (para a tecla). Os dialogos usam o comando vanilla /dialog (MC 1.21.6+).

## v1.4 – Tela de conversa propria
- NPCs: tela com nome, profissao, historico da conversa, fala atual e opcoes de resposta (pacote servidor->cliente).
- Se o jogador nao tiver o mod no cliente, cai no dialogo nativo do Minecraft.
- O NPC para e olha para o jogador durante a conversa (45 s sem interacao).
- Portoes das muralhas ficam no trecho mais plano e seco de cada lado; rampa curta, sem trincheira.

## v1.5 – Kit de estruturas da capital (.nbt do jogador)
- `KitData.java` embute os .nbt (muralha, portao, torre estendida +7, casa do ferreiro). `StructureKit` carrega e coloca com rotacao.
- `CapitalBuilder`: 4 torres + segmentos de muralha emendados + UM portao; cada peca na sua altura (degrau maximo 1), fundacao, local escolhido pelo terreno mais plano/seco, rampas curtas no portao.
- Comandos de teste (criativo/espectador): `/vila admin colar <muralha|portao|torre|ferreiro> [0|90|180|270]` e `/vila admin capital`.
- `autoCapitalKit` no config.json (padrao false) faz toda capital usar o kit sozinha.
