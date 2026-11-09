import main.Settings;
import owl.ltl.LabelledFormula;
import owl.ltl.parser.LtlParser;

import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;

/**
 * Writes the result of counting a seeded stream of random formulas, one per
 * line: formula, status, count. Compiles against EstiMate or AuRUS, so that
 * fuzz-compare.sh can compare the same formulas across counters and solvers.
 *
 * <p>Arguments: count seed solver timeoutSeconds outputFile [det].</p>
 */
public final class FuzzCounts {

    private static final List<String> VARIABLES = List.of("a", "b", "c");
    private static final int BOUND = 10;
    private static final int DEPTH = 4;

    private FuzzCounts() {
    }

    private static String randomFormula(Random random, int depth) {
        String[] atoms = {"a", "b", "c", "!a", "!b", "!c"};
        if (depth == 0 || random.nextInt(4) == 0) {
            return atoms[random.nextInt(atoms.length)];
        }
        String[] unary = {"!", "X ", "F ", "G "};
        String[] binary = {"&", "|", "->", "<->", "U", "W", "R", "M"};
        if (random.nextBoolean()) {
            return unary[random.nextInt(unary.length)] + "(" + randomFormula(random, depth - 1) + ")";
        }
        return "(" + randomFormula(random, depth - 1) + ") " + binary[random.nextInt(binary.length)]
                + " (" + randomFormula(random, depth - 1) + ")";
    }

    private static Class<?> counterClass() throws ClassNotFoundException {
        try {
            return Class.forName("modelcounter.estimate.ModelCounter");
        } catch (ClassNotFoundException e) {
            return Class.forName("modelcounter.ModelCounter");
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 5 && args[5].equals("det")) {
            Class.forName("modelcounter.estimate.Determinism").getMethod("pin").invoke(null);
        }
        int formulas = Integer.parseInt(args[0]);
        Random random = new Random(Long.parseLong(args[1]));
        Settings.SAT_SOLVER = args[2];
        int timeout = Integer.parseInt(args[3]);
        Settings.SAT_TIMEOUT = timeout;
        Settings.PARSING_TIMEOUT = timeout;
        Settings.MC_TIMEOUT = timeout;

        Method count = counterClass().getMethod("count", LabelledFormula.class, int.class);
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(Path.of(args[4])))) {
            for (int i = 0; i < formulas; i++) {
                String formula = randomFormula(random, DEPTH);
                Object result = count.invoke(null, LtlParser.parse(formula, VARIABLES), BOUND);
                boolean failure = (boolean) result.getClass().getMethod("isFailure").invoke(result);
                Object status = result.getClass().getMethod("status").invoke(result);
                Object models = failure ? "-" : result.getClass().getMethod("count").invoke(result);
                out.println(formula + "\t" + status + "\t" + models);
            }
        }
        // Timed-out counter threads would otherwise keep the JVM alive.
        System.exit(0);
    }
}
