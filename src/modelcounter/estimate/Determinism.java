package modelcounter.estimate;

import sun.misc.Unsafe;

import java.lang.reflect.Field;

/**
 * Makes counts repeatable between runs, for {@code --det}.
 *
 * <p>Owl stores formula children in {@code Set.of} collections, whose iteration
 * order the JDK randomises at each JVM start, and hashes formulas with class
 * identity hashes. Both change the automaton on some formulas. See
 * {@code docs/non-determinism.md}.</p>
 *
 * <p>This writes private JDK fields and is experimental: it may stop working
 * on another Java version, in which case {@link #pin()} fails rather than
 * letting a run continue without repeatable results.</p>
 */
public final class Determinism {

    /** The JVM flags the launch scripts add for {@code --det}; they fix identity hashes at 1. */
    private static final int PINNED_IDENTITY_HASH = 1;
    private static final long SALT = 12345678L;

    private Determinism() {
    }

    /**
     * Fixes the order of immutable collections. Call before creating formulas.
     *
     * @throws IllegalStateException if this JVM cannot be made repeatable
     */
    public static void pin() {
        if (new Object().hashCode() != PINNED_IDENTITY_HASH) {
            throw new IllegalStateException("--det needs the JVM flags -XX:+UnlockExperimentalVMOptions "
                    + "-XX:hashCode=2, which modelcount.sh adds");
        }
        try {
            Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            Unsafe unsafe = (Unsafe) unsafeField.get(null);
            Class<?> collections = Class.forName("java.util.ImmutableCollections");
            Field reverse = collections.getDeclaredField("REVERSE");
            Field salt = collections.getDeclaredField("SALT32L");
            unsafe.putBoolean(unsafe.staticFieldBase(reverse), unsafe.staticFieldOffset(reverse), true);
            unsafe.putLong(unsafe.staticFieldBase(salt), unsafe.staticFieldOffset(salt), SALT);
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new IllegalStateException("--det is not supported on Java "
                    + System.getProperty("java.version") + ": " + e, e);
        }
    }
}
