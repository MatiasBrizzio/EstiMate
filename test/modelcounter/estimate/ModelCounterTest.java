package modelcounter.estimate;

import main.Settings;
import modelcounter.estimate.CountResult.Status;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import owl.ltl.parser.LtlParser;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ModelCounterTest {

    private static final List<String> AB = List.of("a", "b");
    private static final String UNSAT = "G (a -> X b) & !G (a -> F b)";
    /** Large enough that translating and counting cannot finish within a zero timeout. */
    private static final List<String> WIDE =
            IntStream.range(0, 18).mapToObj(i -> "v" + i).collect(Collectors.toList());
    /**
     * Results of ModelCounter in AuRUS (commit 6e7b2a6) for 320 random formulas
     * over a,b,c with k = 10, depth 4, Random seed 20261110, one per line:
     * formula, status, count. aalta and BLACK gave identical results there.
     */
    private static final Path AURUS_COUNTS = Path.of("test/modelcounter/estimate/aurus-counts.tsv");

    private String savedSolver;
    private String savedBin;
    private int savedSatTimeout;
    private int savedParsingTimeout;
    private int savedCountTimeout;

    @BeforeEach
    void save() {
        savedSolver = Settings.SAT_SOLVER;
        savedBin = Settings.SAT_BIN;
        savedSatTimeout = Settings.SAT_TIMEOUT;
        savedParsingTimeout = Settings.PARSING_TIMEOUT;
        savedCountTimeout = Settings.MC_TIMEOUT;
    }

    @AfterEach
    void restore() {
        Settings.SAT_SOLVER = savedSolver;
        Settings.SAT_BIN = savedBin;
        Settings.SAT_TIMEOUT = savedSatTimeout;
        Settings.PARSING_TIMEOUT = savedParsingTimeout;
        Settings.MC_TIMEOUT = savedCountTimeout;
    }

    private static boolean blackInstalled() throws InterruptedException {
        try {
            return new ProcessBuilder("black", "--sat-backends").start().waitFor() == 0;
        } catch (IOException e) {
            return false;
        }
    }

    private static CountResult count(String formula, List<String> vars, int k) {
        CountResult result = ModelCounter.count(LtlParser.parse(formula, vars), k);
        System.out.printf("%nformula: %s vars=%s k=%d => %s%n", formula, vars, k, result);
        return result;
    }

    private static void assertCount(long expected, String formula) {
        CountResult result = count(formula, AB, 10);
        assertEquals(Status.OK, result.status(), formula);
        assertEquals(BigInteger.valueOf(expected), result.count(), formula);
    }

    private static void assertFailure(Status expected, CountResult result) {
        assertEquals(expected, result.status());
        assertTrue(result.isFailure());
        assertThrows(IllegalStateException.class, result::count);
    }

    @Test
    void knownCounts() {
        assertCount(1024, "G a");
        assertCount(1023, "G a & F b");
        assertCount(1, "!(G a & F b) & G a");
        assertCount(1048576, "G F a");
        assertCount(78732, "G (a -> X b)");
    }

    @Test
    void unsatisfiableBySolver() {
        CountResult result = count(UNSAT, AB, 10);
        assertEquals(Status.UNSAT_SOLVER, result.status());
        assertEquals(BigInteger.ZERO, result.count());
    }

    @Test
    void unsatisfiableBySimplifier() {
        CountResult result = count("a & !a", AB, 10);
        assertEquals(Status.UNSAT_SIMPLIFIED, result.status());
        assertEquals(BigInteger.ZERO, result.count());
    }

    @Test
    void weakUntilAndStrongReleaseNestedUnderTemporalOperatorsAreRewritten() throws Exception {
        // Unsatisfiable: a M b needs a eventually. aalta rejects a raw M, and
        // BLACK 26.05.0 answers SAT to one, so the rewriter must reach it.
        String nested = "F ((a M b) & G !a)";
        Settings.SAT_SOLVER = "aalta";
        assertEquals(Status.UNSAT_SOLVER, count(nested, AB, 10).status());
        assumeTrue(blackInstalled(), "black is not usable on the PATH");
        Settings.SAT_SOLVER = "black";
        assertEquals(Status.UNSAT_SOLVER, count(nested, AB, 10).status());
    }

    private void assertAgreesWithAurus() throws IOException {
        List<String> lines = Files.readAllLines(AURUS_COUNTS);
        assertTrue(lines.size() >= 300);
        List<String> differences = new ArrayList<>();
        for (String line : lines) {
            String[] row = line.split("\t");
            CountResult result = ModelCounter.count(LtlParser.parse(row[0], List.of("a", "b", "c")), 10);
            String count = result.isFailure() ? "-" : result.count().toString();
            if (!result.status().name().equals(row[1]) || !count.equals(row[2])) {
                differences.add(row[0] + " => aurus=" + row[1] + "/" + row[2] + " estimate=" + result.status() + "/" + count);
            }
        }
        System.out.printf("%nAGREEMENT solver=%s formulas=%d differences=%d%n", Settings.SAT_SOLVER, lines.size(), differences.size());
        differences.forEach(d -> System.out.println("DIFF " + d));
        assertEquals(List.of(), differences);
    }

    @Test
    void agreesWithAurusUsingAalta() throws IOException {
        Settings.SAT_SOLVER = "aalta";
        assertAgreesWithAurus();
    }

    @Test
    void agreesWithAurusUsingBlack() throws Exception {
        assumeTrue(blackInstalled(), "black is not usable on the PATH");
        Settings.SAT_SOLVER = "black";
        assertAgreesWithAurus();
    }

    @Test
    void translationTimeoutIsAFailure() {
        Settings.PARSING_TIMEOUT = 0;
        assertFailure(Status.TRANSLATION_TIMEOUT, count("G v0", WIDE, 10));
    }

    @Test
    void countTimeoutIsAFailure() {
        Settings.MC_TIMEOUT = 0;
        assertFailure(Status.COUNT_TIMEOUT, count("G v0", WIDE, 10));
    }

    @Test
    void solverTimeoutIsAFailure() {
        Settings.SAT_TIMEOUT = 0;
        assertFailure(Status.SOLVER_TIMEOUT, count(UNSAT, AB, 10));
    }

    @Test
    void missingSolverBinaryIsAFailure() {
        for (String solver : List.of("aalta", "black")) {
            Settings.SAT_SOLVER = solver;
            Settings.SAT_BIN = "/nonexistent/solver";
            assertFailure(Status.ERROR, count(UNSAT, AB, 10));
        }
    }

    private static String script(Path dir, String body) throws IOException {
        Path file = Files.createTempFile(dir, "solver", ".sh");
        Files.writeString(file, "#!/bin/sh\n" + body + "\n");
        assertTrue(file.toFile().setExecutable(true));
        return file.toString();
    }

    @Test
    void crashingSolverIsAFailure(@TempDir Path dir) throws Exception {
        for (String solver : List.of("aalta", "black")) {
            Settings.SAT_SOLVER = solver;
            Settings.SAT_BIN = script(dir, "exit 3");
            assertFailure(Status.ERROR, count(UNSAT, AB, 10));
        }
    }

    @Test
    void anythingButAConclusiveAnswerFromBlackIsAFailure(@TempDir Path dir) throws Exception {
        Settings.SAT_SOLVER = "black";
        for (String body : List.of("echo garbage", "exit 0", "echo 'UNKNOWN (stopped at k = 1)'")) {
            Settings.SAT_BIN = script(dir, body);
            assertTrue(count(UNSAT, AB, 10).isFailure(), body);
        }
    }

    @Test
    void anUnusedPropositionMultipliesTheCountByTwoToTheK() {
        BigInteger one = count("G a", List.of("a"), 10).count();
        BigInteger two = count("G a", AB, 10).count();
        BigInteger three = count("G a", List.of("a", "b", "c"), 10).count();
        assertEquals(one.shiftLeft(10), two);
        assertEquals(two.shiftLeft(10), three);
    }

    @Test
    void sixtyConcurrentRunsWithBlackInOneDirectoryAllAnswerUnsat() throws Exception {
        assumeTrue(blackInstalled(), "black is not usable on the PATH");
        Settings.SAT_SOLVER = "black";
        int runs = 60;
        ExecutorService pool = Executors.newFixedThreadPool(runs);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<CountResult>> futures = new ArrayList<>();
            for (int i = 0; i < runs; i++) {
                Callable<CountResult> task = () -> {
                    start.await();
                    return ModelCounter.count(LtlParser.parse(UNSAT, AB), 10);
                };
                futures.add(pool.submit(task));
            }
            start.countDown();
            for (Future<CountResult> future : futures) {
                assertEquals(Status.UNSAT_SOLVER, future.get().status());
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
