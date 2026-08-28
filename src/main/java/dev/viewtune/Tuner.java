package dev.viewtune;

/**
 * Reine Rechenlogik von ViewTune — bewusst ohne Bukkit-Objekte, damit der Prober
 * sie auf beiden Paper-Versionen mit echten Zusicherungen pruefen kann, ohne dass
 * ein Spieler joinen muss.
 */
public final class Tuner {

    private Tuner() {
    }

    /** Kleinster Wert, den Minecraft als Sicht-/Simulationsdistanz akzeptiert. */
    public static final int HARD_MIN = 2;

    /**
     * Sichtdistanz fuer einen Spieler.
     *
     * @param clientView was der Client gemeldet hat; &lt;= 0 heisst "noch unbekannt"
     * @param min        Untergrenze aus der Konfiguration
     * @param ceiling    bereits verrechnete Obergrenze (Welt, Rang, Auslastung, TPS)
     */
    public static int viewFor(int clientView, int min, int ceiling) {
        int lo = Math.max(HARD_MIN, min);
        int hi = Math.max(lo, ceiling);
        if (clientView <= 0) {
            return hi;          // Ohne Client-Angabe nicht bestrafen.
        }
        return Math.min(hi, Math.max(lo, clientView));
    }

    /**
     * Simulationsdistanz fuer einen Spieler. Sie ist der teure Wert (Mobs, Redstone,
     * Pflanzenwachstum) und darf optional nie ueber der Sichtdistanz liegen.
     */
    public static int simFor(int appliedView, int min, int ceiling, boolean neverAboveView) {
        int lo = Math.max(HARD_MIN, min);
        int hi = Math.max(lo, ceiling);
        if (neverAboveView) {
            hi = Math.max(lo, Math.min(hi, appliedView));
        }
        return hi;
    }

    /**
     * Ein Schritt des TPS-Reglers. Bewusst nur +-1 Chunk pro Aufruf: ein Sprung von
     * 16 auf 4 wuerde selbst eine Lastspitze ausloesen (alle Chunks neu senden).
     */
    public static int adaptiveStep(int cap, double tps, double lowTps, double recoverTps,
                                   int floor, int max) {
        int lo = Math.max(HARD_MIN, floor);
        int hi = Math.max(lo, max);
        int c = Math.min(hi, Math.max(lo, cap));
        if (tps < lowTps) {
            return Math.max(lo, c - 1);
        }
        if (tps > recoverTps) {
            return Math.min(hi, c + 1);
        }
        return c;
    }

    /**
     * Obergrenze aus der Spielerzahl. Die Schwellen kommen aufsteigend nach
     * {@code players} sortiert herein; die hoechste erreichte Schwelle gewinnt.
     */
    public static int scalingCap(int online, int[][] thresholds, int fallback) {
        int cap = fallback;
        if (thresholds == null) {
            return cap;
        }
        for (int[] t : thresholds) {
            if (online >= t[0]) {
                cap = t[1];
            }
        }
        return cap;
    }

    /** Wie viele Chunks pro Spieler gespart werden, verglichen mit einem festen Serverwert. */
    public static long chunksSaved(int serverView, int appliedView) {
        long a = (2L * serverView + 1) * (2L * serverView + 1);
        long b = (2L * appliedView + 1) * (2L * appliedView + 1);
        return Math.max(0L, a - b);
    }
}
