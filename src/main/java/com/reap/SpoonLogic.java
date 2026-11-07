package com.reap;

/** Geometria compartilhada entre servidor (acertos) e cliente (visual). */
public final class SpoonLogic {
    public static final double ORBIT_SPEED = 0.30;   // rad/tick
    public static final double HANDLE_R = 0.45;      // distância do cabo ao jogador
    public static final double LENGTH = 1.95;        // comprimento da colher em blocos
    public static final double CENTER_Y = 0.85;      // altura da órbita (cintura)

    private SpoonLogic() {}

    public static double angle(long time, float tickDelta, int i, int n) {
        return (time + tickDelta) * ORBIT_SPEED + i * (Math.PI * 2.0 / n);
    }

    public static double bob(long time, float tickDelta, int i) {
        return Math.sin((time + tickDelta) * 0.12 + i * 1.7) * 0.12;
    }
}
