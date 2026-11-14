package br.nobrevilas;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Tela de conversa em estilo medieval rustico (pedra, ouro, estandartes), desenhada so com retangulos e texto:
 * moldura de tijolos com musgo, placa com nome/cargo, retrato em pixel art, "Profissao", caixa de fala com historico
 * e botoes de pedra com icones. Os botoes reais continuam sendo widgets do Minecraft (clique, teclado, narracao);
 * a arte e pintada por cima deles.
 */
public class NpcScreen extends Screen {
    private enum Kind { GUIDE, ROYAL, NOBLE, GUARD, TRADER, VILLAGER }

    // ───────────── paleta ─────────────
    private static final int MORTAR = 0xFF2B2720, DARK = 0xFF17130E, GOLD = 0xFFD4A21F, GOLD_D = 0xFF8C6A12, GOLD_L = 0xFFF2CF5A,
        RED = 0xFF9E2222, RED_D = 0xFF661414, TEXT = 0xFFF0E6D2, DIM = 0xFF8F8470, MOSS = 0xFF4B6B2F, SHADOW = 0x66000000;
    private static final int[] STONES = {0xFF6E675B, 0xFF625B50, 0xFF776F62, 0xFF5A544A};
    private static final int[] PAL = new int[128];
    static {
        PAL['G'] = 0xFFE6B422; PAL['g'] = 0xFF9A7410; PAL['R'] = 0xFFB02828; PAL['r'] = 0xFF6B1414;
        PAL['S'] = 0xFFB9B3A6; PAL['s'] = 0xFF7A7468; PAL['K'] = 0xFF1A140C; PAL['P'] = 0xFFEBDDB4; PAL['p'] = 0xFFBFA974;
        PAL['B'] = 0xFF7A4A21; PAL['N'] = 0xFFC38A62; PAL['n'] = 0xFF8F5D3C; PAL['H'] = 0xFF3F2616; PAL['W'] = 0xFFF4F4F4;
        PAL['E'] = 0xFF2FA33A; PAL['A'] = 0xFF4CAF50; PAL['U'] = 0xFF2E5FA3; PAL['u'] = 0xFF1E3F73; PAL['M'] = 0xFF6A2C91;
    }

    // ───────────── icones (pixel art) ─────────────
    private static final String[] CROWN = {"G..G..G", "GG.G.GG", "GGGGGGG", "GRGGGRG", "ggggggg"};
    private static final String[] BUBBLE = {".PPPPPP.", "PPPPPPPP", "PpPpPpPP", "PPPPPPPP", ".PPPPPP.", "..PP....", "..P....."};
    private static final String[] SCROLL = {"BBBBBBB", ".PPPPP.", ".PpppP.", ".PPPPP.", ".PpppP.", ".PPPPP.", "BBBBBBB"};
    private static final String[] HAND = {"RRRNNUUU", "RRNNNNUU", "RRNnnNUU", ".NNNNNN.", "..NnnN.."};
    private static final String[] ARROW = {"...A...", "..AA...", ".AAAAAA", "AAAAAAA", ".AAAAAA", "..AA...", "...A..."};
    private static final String[] CASTLE = {"S.S.S.S", "SSSSSSS", "SsSsSsS", "SSSKSSS", "SSKKKSS", "SSKKKSS"};
    private static final String[] TROPHY = {"GGGGGGG", "GGGGGGG", ".GGGGG.", "..GGG..", "...g...", "..ggg.."};
    private static final String[] BOOK = {"BBBBBBBBBBB", "BPPPPBPPPPB", "BPpppBpppPB", "BPPPPBPPPPB", "BPpppBpppPB", "BPPPPBPPPPB", "BBBBBBBBBBB"};

    private static final List<String> HIST = new ArrayList<>();
    private static String lastKey = "";
    private static long lastTime;

    private final String name, role, title, body;
    private final List<String[]> opts = new ArrayList<>();
    private final List<String> hist;
    private final Kind kind;

    // layout
    private List<String> histLines = new ArrayList<>(), bodyLines = new ArrayList<>();
    private final List<int[]> optRects = new ArrayList<>();        // x, y, w, h
    private int px, py, pw, ph, panelTop, titleY, bodyY, boxH, tbX, tbW, lh, closeIdx;
    private int portraitW;
    private EditBox nameBox;

    public NpcScreen(NpcTalkPayload p) {
        super(Component.literal("Conversa"));
        String[] h = p.head().split("\u0001", -1);
        name = h[0];
        role = h.length > 1 ? h[1] : "";
        title = h.length > 3 ? h[3] : "";
        body = p.body();
        for (String e : p.options().split("\u0002", -1)) {
            if (e.isEmpty()) continue;
            String[] f = e.split("\u0001", -1);
            opts.add(new String[]{f[0], f.length > 1 ? f[1] : ""});
        }
        kind = detectKind();
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

    private Kind detectKind() {
        String n = name.toLowerCase(), r = role.toLowerCase();
        if (n.startsWith("guia")) return Kind.GUIDE;
        if (n.startsWith("soldado") || r.startsWith("guarda")) return Kind.GUARD;
        if (n.startsWith("mercador") || r.startsWith("comerciante")) return Kind.TRADER;
        if (n.startsWith("aldeão") || n.startsWith("aldeao") || r.startsWith("habitante")) return Kind.VILLAGER;
        for (String t : new String[]{"rei", "rainha", "imperador", "imperatriz", "príncipe", "princesa", "duque", "duquesa", "arquiduque", "arquiduquesa"})
            if (r.startsWith(t + " ")) return Kind.ROYAL;
        return Kind.NOBLE;
    }

    private String profession() {
        return switch (kind) {
            case ROYAL -> "Realeza";
            case NOBLE -> "Nobreza";
            case GUARD -> "Guarda";
            case TRADER -> "Comércio";
            default -> "Aldeão";
        };
    }

    // ───────────── utilitarios ─────────────
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

    private String fit(String s, int maxW) {
        if (this.font.width(s) <= maxW) return s;
        String t = s;
        while (t.length() > 1 && this.font.width(t + "…") > maxW) t = t.substring(0, t.length() - 1);
        return t + "…";
    }

    private static int hash(int a, int b, int c) {
        int h = a * 73856093 ^ b * 19349663 ^ c * 83492791;
        h ^= h >>> 13;
        return h & 0x7fffffff;
    }

    private boolean showTitle() { return !title.isEmpty() && !title.equals(name); }

    // ───────────── layout ─────────────
    @Override
    protected void init() {
        optRects.clear();
        lh = this.font.lineHeight + 2;
        pw = Math.min(450, this.width - 20);
        px = (this.width - pw) / 2;
        int t = 8;
        int inner = pw - 2 * t - 16;
        portraitW = kind == Kind.GUIDE ? 0 : 76;
        tbW = inner - (portraitW > 0 ? portraitW + 8 : 0);
        int iconPad = kind == Kind.GUIDE ? 34 : 0;
        int textW = tbW - 16 - iconPad;

        histLines = new ArrayList<>();
        for (String s : hist) histLines.addAll(wrap("› " + s, textW));
        while (histLines.size() > 4) histLines.remove(0);
        bodyLines = wrap(body, textW);

        int textBoxH = 12 + (histLines.isEmpty() ? 0 : histLines.size() * lh + 6) + (showTitle() ? lh + 2 : 0) + bodyLines.size() * lh + 10;
        boxH = kind == Kind.GUIDE ? Math.max(textBoxH, 44) : Math.max(textBoxH, 118);

        boolean needBox = false;
        for (String[] o : opts) if (o[1].contains("$(nome)")) needBox = true;

        int nBtn = opts.size() + 1;
        int total = 12 + t + 36 + 8 + boxH + 10 + nBtn * 25 + 6 + t + (needBox ? 26 : 0);
        py = Math.max(2, (this.height - total) / 2);
        panelTop = py + 12;
        titleY = panelTop + t;
        bodyY = titleY + 36 + 8;
        tbX = px + t + 8 + (portraitW > 0 ? portraitW + 8 : 0);
        int by = bodyY + boxH + 10;
        int bx = px + t + 8;
        for (String[] o : opts) {
            final String cmd = o[1];
            this.addRenderableWidget(Button.builder(Component.literal(o[0]), b -> click(cmd)).bounds(bx, by, inner, 22).build());
            optRects.add(new int[]{bx, by, inner, 22});
            by += 25;
        }
        closeIdx = optRects.size();
        this.addRenderableWidget(Button.builder(Component.literal("Encerrar conversa"), b -> this.onClose()).bounds(bx, by, inner, 22).build());
        optRects.add(new int[]{bx, by, inner, 22});
        by += 25;
        ph = (by + 6 + t) - panelTop;
        if (needBox) {
            nameBox = new EditBox(this.font, px, panelTop + ph + 6, pw, 20, Component.literal("Nome do reino"));
            nameBox.setMaxLength(24);
            nameBox.setHint(Component.literal("Nome do novo reino"));
            this.addRenderableWidget(nameBox);
        }
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

    // ───────────── desenho ─────────────
    private void art(GuiGraphicsExtractor g, String[] rows, int x, int y, int s) {
        for (int r = 0; r < rows.length; r++) {
            String row = rows[r];
            for (int c = 0; c < row.length(); c++) {
                char ch = row.charAt(c);
                if (ch == '.' || ch >= 128 || PAL[ch] == 0) continue;
                g.fill(x + c * s, y + r * s, x + (c + 1) * s, y + (r + 1) * s, PAL[ch]);
            }
        }
    }

    private void stone(GuiGraphicsExtractor g, int x, int y, int w, int h, int bh, int bw, int seed) {
        g.fill(x, y, x + w, y + h, MORTAR);
        int row = 0;
        for (int yy = y; yy < y + h; yy += bh, row++) {
            int off = (row % 2 == 0) ? 0 : bw / 2;
            for (int xx = x - off; xx < x + w; xx += bw) {
                int x1 = Math.max(xx + 1, x), x2 = Math.min(xx + bw, x + w), y1 = yy + 1, y2 = Math.min(yy + bh, y + h);
                if (x2 <= x1 || y2 <= y1) continue;
                g.fill(x1, y1, x2, y2, STONES[hash(xx, yy, seed) & 3]);
            }
        }
    }

    private void goldBox(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, DARK);
        g.outline(x, y, w, h, GOLD_D);
        g.outline(x + 1, y + 1, w - 2, h - 2, GOLD);
    }

    private void banner(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean castle) {
        int hb = h - w / 2;
        g.fill(x, y, x + w, y + hb, RED);
        g.fill(x, y, x + 2, y + hb, RED_D);
        g.fill(x + w - 2, y, x + w, y + hb, RED_D);
        for (int i = 0; i < w / 2; i++) g.fill(x + i, y + hb + i, x + w - i, y + hb + i + 1, i < 2 ? RED_D : RED);
        g.fill(x - 1, y, x + w + 1, y + 3, GOLD);
        g.fill(x - 1, y + 3, x + w + 1, y + 4, GOLD_D);
        art(g, castle ? CASTLE : CROWN, x + (w - 14) / 2, y + 9, 2);
    }

    private String[] portrait() {
        String[] top = switch (kind) {
            case ROYAL -> new String[]{"G.GG.GG.GG.G", "GGRGGGGGGRGG"};
            case NOBLE -> new String[]{"............", "GGGGGGGGGGGG"};
            case GUARD -> new String[]{"SSSSSSSSSSSS", "SsSsSsSsSsSs"};
            case TRADER -> new String[]{"..UUUUUUUU..", "UUUUUUUUUUUU"};
            default -> new String[]{"............", "NNNNNNNNNNNN"};
        };
        String[] face = {"NNNNNNNNNNNN", "NHHHHHHHHHHN", "NWEENNNNWEEN", "NNNNNnnNNNNN", "NNNNnnnnNNNN", "NNNNnnnnNNNN",
            "NNNNNnnNNNNN", "NNNNNNNNNNNN", "NNNNNNNNNNNN", "NNNNNNNNNNNN", "NNNNNNNNNNNN", "NNNNNNNNNNNN"};
        if (kind == Kind.ROYAL) { face[8] = "NNWWWWWWWWNN"; face[9] = "NWWWWWWWWWWN"; face[10] = "NWWWWWWWWWWN"; face[11] = "NNWWWWWWWWNN"; }
        String[] bottom = switch (kind) {
            case ROYAL -> new String[]{"RGGGGGGGGGGR", "RRRRRRRRRRRR"};
            case NOBLE -> new String[]{"MMMMMMMMMMMM", "MMMMMMMMMMMM"};
            case GUARD -> new String[]{"SSSSSSSSSSSS", "ssssssssssss"};
            case TRADER -> new String[]{"UUUUUUUUUUUU", "uuuuuuuuuuuu"};
            default -> new String[]{"BBBBBBBBBBBB", "BBBBBBBBBBBB"};
        };
        String[] all = new String[16];
        System.arraycopy(top, 0, all, 0, 2);
        System.arraycopy(face, 0, all, 2, 12);
        System.arraycopy(bottom, 0, all, 14, 2);
        return all;
    }

    private String[] iconFor(String label, boolean isClose) {
        String l = label.toLowerCase();
        if (isClose || l.contains("voltar") || l.contains("adeus") || l.contains("encerrar") || l.contains("‹")) return ARROW;
        if (l.contains("alian") || l.contains("aliar") || l.contains("pact")) return HAND;
        if (l.contains("missão") || l.contains("missao") || l.contains("status") || l.contains("posto") || l.contains("comando") || l.contains("imposto")) return SCROLL;
        if (l.contains("reino") || l.contains("coroa") || l.contains("proclam") || l.contains("rebel") || l.contains("rebeli") || l.contains("romper")) return CROWN;
        if (l.contains("desafi") || l.contains("conquist")) return TROPHY;
        if (l.contains("muralha") || l.contains("capital") || l.contains("vila") || l.contains("vizinh") || l.contains("castelo") || l.contains("territ") || l.contains("o que é")) return CASTLE;
        return BUBBLE;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        int t = 8;
        int cx = px + pw / 2;

        // moldura de pedra com musgo e rebites
        stone(g, px, panelTop, pw, ph, 8, 16, 1);
        g.fill(px + t, panelTop + t, px + pw - t, panelTop + ph - t, DARK);
        g.outline(px + t, panelTop + t, pw - 2 * t, ph - 2 * t, GOLD_D);
        for (int i = 0; i < 9; i++) {
            int mx = px + (hash(i, 7, 3) % (pw - 10)), my = panelTop + (i % 2 == 0 ? 1 : ph - 4);
            g.fill(mx, my, mx + 3, my + 2, MOSS);
        }
        for (int[] c : new int[][]{{px + 1, panelTop + 1}, {px + pw - 7, panelTop + 1}, {px + 1, panelTop + ph - 7}, {px + pw - 7, panelTop + ph - 7}}) {
            g.fill(c[0], c[1], c[0] + 6, c[1] + 6, GOLD_D);
            g.fill(c[0] + 1, c[1] + 1, c[0] + 5, c[1] + 5, GOLD);
            g.fill(c[0] + 2, c[1] + 2, c[0] + 4, c[1] + 4, GOLD_L);
        }

        // estandartes e placa do nome
        boolean castle = kind == Kind.GUIDE;
        banner(g, px + t + 3, panelTop + 3, 22, 54, castle);
        banner(g, px + pw - t - 25, panelTop + 3, 22, 54, castle);
        int plateX = px + t + 32, plateW = pw - 2 * (t + 32);
        goldBox(g, plateX, titleY, plateW, 36);
        String n = fit(name, plateW - 16);
        g.text(this.font, n, cx - this.font.width(n) / 2 + 1, titleY + 6, SHADOW | 0xFF000000, false);
        g.text(this.font, n, cx - this.font.width(n) / 2, titleY + 5, GOLD_L, false);
        String r = fit("— " + role + " —", plateW - 16);
        g.text(this.font, r, cx - this.font.width(r) / 2, titleY + 21, TEXT, false);

        // ornamento superior
        if (kind == Kind.GUIDE) art(g, BOOK, cx - 17, py - 1, 3);
        else art(g, CROWN, cx - 11, py, 3);

        // retrato + profissao
        if (portraitW > 0) {
            int ptx = px + t + 8;
            goldBox(g, ptx, bodyY, portraitW, 88);
            art(g, portrait(), ptx + 8, bodyY + 4, 5);
            goldBox(g, ptx, bodyY + 92, portraitW, 26);
            g.text(this.font, "Profissão:", ptx + 6, bodyY + 96, DIM, false);
            g.text(this.font, fit(profession(), portraitW - 12), ptx + 6, bodyY + 107, GOLD_L, false);
        }

        // caixa de fala
        goldBox(g, tbX, bodyY, tbW, boxH);
        int tx = tbX + 8;
        int y = bodyY + 8;
        if (kind == Kind.GUIDE) { art(g, BOOK, tbX + 8, bodyY + 8, 2); tx += 34; }
        for (String l : histLines) { g.text(this.font, l, tx, y, DIM, false); y += lh; }
        if (!histLines.isEmpty()) { g.fill(tx, y + 1, tbX + tbW - 8, y + 2, GOLD_D); y += 6; }
        if (showTitle()) { g.text(this.font, title, tx, y, GOLD_L, false); y += lh + 2; }
        for (String l : bodyLines) { g.text(this.font, l, tx, y, TEXT, false); y += lh; }

        // botoes de pedra
        for (int i = 0; i < optRects.size(); i++) {
            int[] b = optRects.get(i);
            boolean close = i == closeIdx;
            String label = close ? "Encerrar conversa" : opts.get(i)[0];
            boolean hover = mouseX >= b[0] && mouseX < b[0] + b[2] && mouseY >= b[1] && mouseY < b[1] + b[3];
            stone(g, b[0], b[1], b[2], b[3], 11, 22, 5 + i);
            g.fill(b[0] + 2, b[1] + 2, b[0] + b[2] - 2, b[1] + b[3] - 2, hover ? 0x55FFE9A0 : 0x33000000);
            g.outline(b[0], b[1], b[2], b[3], hover ? GOLD_L : MORTAR);
            if (hover) g.outline(b[0] + 1, b[1] + 1, b[2] - 2, b[3] - 2, GOLD);
            art(g, iconFor(label, close), b[0] + 8, b[1] + 5, 2);
            String lab = fit(label, b[2] - 52);
            g.text(this.font, lab, b[0] + (b[2] - this.font.width(lab)) / 2 + 1, b[1] + 7, 0xFF000000, false);
            g.text(this.font, lab, b[0] + (b[2] - this.font.width(lab)) / 2, b[1] + 6, TEXT, false);
        }

        // ornamento inferior
        int by = panelTop + ph - 6;
        g.fill(cx - 22, by - 1, cx + 22, by, GOLD_D);
        for (int i = 0; i < 4; i++) g.fill(cx - 3 + i, by - 4 + i, cx + 3 - i, by - 3 + i, GOLD);
    }
}
