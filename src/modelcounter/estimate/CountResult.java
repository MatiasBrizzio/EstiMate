package modelcounter.estimate;

import java.math.BigInteger;

/**
 * Outcome of {@link ModelCounter#count}: a count, a proof of unsatisfiability,
 * or a failure. A failure carries no count, so it can never be read as zero.
 */
public final class CountResult {

    /** How a count ended. {@link #label()} is the name used in the JSON output. */
    public enum Status {
        OK("ok"),
        UNSAT_SIMPLIFIED("unsat_simplified"),
        UNSAT_SOLVER("unsat_solver"),
        TRANSLATION_TIMEOUT("timeout"),
        COUNT_TIMEOUT("timeout"),
        SOLVER_TIMEOUT("timeout"),
        ERROR("error");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private final Status status;
    private final BigInteger value;

    CountResult(Status status, BigInteger value) {
        this.status = status;
        this.value = value;
    }

    public Status status() {
        return status;
    }

    /** @return {@code true} iff this result holds neither a count nor a proof of unsatisfiability */
    public boolean isFailure() {
        return status != Status.OK && status != Status.UNSAT_SIMPLIFIED && status != Status.UNSAT_SOLVER;
    }

    /**
     * @return the number of models, zero for an unsatisfiable formula
     * @throws IllegalStateException if this result is a failure
     */
    public BigInteger count() {
        if (isFailure())
            throw new IllegalStateException("no count available: " + status);
        return value;
    }

    @Override
    public String toString() {
        return status + (value == null ? "" : "(" + value + ")");
    }
}
