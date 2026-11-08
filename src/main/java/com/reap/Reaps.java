package com.reap;

/**
 * As Reaps (colheres) da loja. O índice 0 é a Reap inicial (grátis, branca); 1..10 são vendidas nos blocos
 * de loja {@code reap:shop_1} .. {@code reap:shop_10}.
 * <p>
 * Cada Reap tem uma cor própria, um preço em wins e um multiplicador de velocidade de destruição
 * (aplicado em {@link ReapData#bitePower()}). A última (índice 10) custa 3200 wins.
 * Os custos e nomes precisam bater com SHOP em tools/gen_assets.py.
 */
public final class Reaps {
    /** rainbow = a cor da colher muda sozinha com o tempo (usado na Reap final). */
    public record Tier(String name, String namePt, int rgb, int cost, float speed, boolean rainbow) {}

    public static final Tier[] TIERS = {
            new Tier("Starter",  "Inicial",  0xFFFFFF,    0,  1.0f, false),
            new Tier("Green",    "Verde",    0x2EE05A,    3,  1.3f, false),
            new Tier("Peach",    "Pêssego",  0xFFCBA4,    8,  1.7f, false),
            new Tier("Purple",   "Roxa",     0xB43CFF,   18,  2.2f, false),
            new Tier("Silver",   "Prata",    0xC9D1DB,   40,  2.8f, false),
            new Tier("Pink",     "Rosa",     0xFFA6E8,   90,  3.5f, false),
            new Tier("Yellow",   "Amarela",  0xFFE04A,  200,  4.4f, false),
            new Tier("Orange",   "Laranja",  0xFF8A1C,  420,  5.5f, false),
            new Tier("Cyan",     "Ciano",    0x2CD6EC,  850,  7.0f, false),
            new Tier("Red",      "Vermelha", 0xFF2D4A, 1700,  9.0f, false),
            new Tier("Rainbow",  "Arco-íris",0xFF2BD6, 3200, 12.0f, true),
    };

    /** Índice da última Reap (a mais cara). */
    public static final int MAX = TIERS.length - 1;

    private Reaps() {}

    public static int clamp(int i) {
        return Math.max(0, Math.min(MAX, i));
    }

    public static Tier get(int i) {
        return TIERS[clamp(i)];
    }

    public static float speed(int i) {
        return get(i).speed();
    }

    public static String speedLabel(float m) {
        return m == Math.floor(m) ? "x" + (int) m : "x" + m;
    }
}
