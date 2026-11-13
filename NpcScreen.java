package br.nobrevilas;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Tela de conversa com NPC: cabecalho (nome + profissao), historico, fala atual, opcoes de resposta. */
public class NpcScreen extends Screen {
    private static final List<String> HIST = new ArrayList<>();
    private static String lastKey = "";
    private static long lastTime;

    private final String name, role, title, body;
    private final int nameColor, titleColor;
    private final List<String[]> opts = new ArrayList<>();
    private final List<String> hist;

    private List<String> histLines = new ArrayList<>(), bodyLines = new ArrayList<>();
    private int px, py, panelW, panelH, lh;
    private EditBox nameBox;

    public NpcScreen(NpcTalkPayload p) {
        super(Component.literal("Conversa"));
        String[] h = p.head().split("\u0001", -1);
        name = h[0];
        role = h.length > 1 ? h[1] : "";
        nameColor = argb(h.length > 2 ? h[2] : "white");
        title = h.length > 3 ? h[3] : "";
        titleColor = argb(h.length > 4 ? h[4] : "yellow");
        body = p.body();
        for (String e : p.options().split("\u0002", -1)) {
            if (e.isEmpty()) continue;
            String[] f = e.split("\u0001", -1);
            opts.add(new String[]{f[0], f.length > 1 ? f[1] : ""});
        }
        long now = System.currentTimeMillis();
        if (!name.equals(lastKey) || now - lastTime > 120_000) HIST.clear();
        lastKey = name;
        lastTime = now;
        hist = new ArrayList<>(HIST);
        String entry = (title.isEmpty() || title.equals(name) ? "" : title + " — ") + body.replace("\n", " ");
        if (entry.length() > 140) entry = entry.substring(0, 137) + "...";
        HIST.add(entry);
        while (HIST.size() > 3) HIST.remove(0);
    }

    private static int argb(String c) {
        return switch (c) {
            case "gold" -> 0xFFFFAA00;
            case "yellow" -> 0xFFFFFF55;
            case "red" -> 0xFFFF5555;
            case "aqua" -> 0xFF55FFFF;
            case "green" -> 0xFF55FF55;
            case "gray" -> 0xFFAAAAAA;
            default -> 0xFFFFFFFF;
        };
    }

    private List<String> wrap(String text, int maxW) {
        List<String> out = new ArrayList<>();
        for (String para : text.split("\n", -1)) {
            StringBuilder line = new StringBuilder();
            for (String word : para.split(" ", -1)) {
                String t = line.length() == 0 ? word : line + " " + word;
                if (this.font.width(t) > maxW && line.length() > 0) {
                    out.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(t);
                }
            }
            out.add(line.toString());
        }
        return out;
    }

    private boolean showTitle() { return !title.isEmpty() && !title.equals(name); }

    @Override
    protected void init() {
        panelW = Math.min(380, this.width - 30);
        px = (this.width - panelW) / 2;
        int textW = panelW - 20;
        lh = this.font.lineHeight + 2;

        histLines = new ArrayList<>();
        for (String s : hist) histLines.addAll(wrap("› " + s, textW));
        while (histLines.size() > 6) histLines.remove(0);
        bodyLines = wrap(body, textW);

        int contentH = 30 + 10 + (histLines.isEmpty() ? 0 : histLines.size() * lh + 6) + (showTitle() ? lh + 2 : 0) + bodyLines.size() * lh + 10;
        boolean needBox = false;
        for (String[] o : opts) if (o[1].contains("$(nome)")) needBox = true;
        int total = contentH + 8 + opts.size() * 24 + (needBox ? 24 : 0) + 26;
        py = Math.max(6, (this.height - total) / 2);
        panelH = contentH;

        int by = py + contentH + 8;
        if (needBox) {
            nameBox = new EditBox(this.font, px, by, panelW, 20, Component.literal("Nome do reino"));
            nameBox.setMaxLength(24);
            nameBox.setHint(Component.literal("Nome do novo reino"));
            this.addRenderableWidget(nameBox);
            by += 24;
        }
        for (String[] o : opts) {
            final String cmd = o[1];
            this.addRenderableWidget(Button.builder(Component.literal(o[0]), b -> click(cmd)).bounds(px, by, panelW, 20).build());
            by += 24;
        }
        this.addRenderableWidget(Button.builder(Component.literal("Encerrar conversa"), b -> this.onClose()).bounds(px, by + 2, panelW, 20).build());
    }

    private void click(String cmd) {
        String c = cmd;
        if (c.contains("$(nome)")) {
            String v = nameBox == null ? "" : nameBox.getValue().trim();
            if (v.length() < 3) return;
            c = c.replace("$(nome)", v);
        }
        if (this.minecraft != null && this.minecraft.player != null) this.minecraft.player.connection.sendCommand(c);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        g.fill(px, py, px + panelW, py + panelH, 0xE6140E08);
        g.outline(px, py, panelW, panelH, 0xFFB8860B);
        g.fill(px + 1, py + 1, px + panelW - 1, py + 30, 0xFF2A1A0A);
        g.text(this.font, name, px + 10, py + 6, nameColor, true);
        g.text(this.font, role, px + 10, py + 18, 0xFFAAAAAA, false);
        int y = py + 40;
        for (String l : histLines) { g.text(this.font, l, px + 10, y, 0xFF8A8A8A, false); y += lh; }
        if (!histLines.isEmpty()) { g.fill(px + 8, y + 1, px + panelW - 8, y + 2, 0xFF5A4630); y += 6; }
        if (showTitle()) { g.text(this.font, title, px + 10, y, titleColor, true); y += lh + 2; }
        for (String l : bodyLines) { g.text(this.font, l, px + 10, y, 0xFFFFFFFF, false); y += lh; }
    }
}
