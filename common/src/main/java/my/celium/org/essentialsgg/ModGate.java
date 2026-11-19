package my.celium.org.essentialsgg;

/**
 * Whether the mod runs at all. It stays off when Mycel is missing; every mixin asks here first,
 * so none of them loads a class that needs Mycel. Must never touch a Mycel class.
 */
public final class ModGate {
    private static volatile boolean on;

    private ModGate() {
    }

    /** Called by the loader entrypoint once it knows Mycel is installed. */
    public static void open() {
        on = true;
    }

    /** @return whether Mycel is installed and the mod is running */
    public static boolean on() {
        return on;
    }
}
