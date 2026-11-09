package main;

import helpers.FormulaUtils;
import helpers.TlsfUtils;
import modelcounter.estimate.AutomatonDump;
import modelcounter.estimate.CountResult;
import modelcounter.estimate.Determinism;
import modelcounter.estimate.ModelCounter;
import modelcounter.exact.PreciseLTLModelCounter;
import modelcounter.re.CountREModels;
import owl.ltl.Formula;
import owl.ltl.LabelledFormula;
import owl.ltl.parser.LtlParser;
import owl.ltl.tlsf.Tlsf;

import java.io.*;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    private static final int EXIT_OK = 0;
    private static final int EXIT_FAILURE = 1;
    private static final int EXIT_USAGE = 2;
    private static final String PREFIXES_COMMAND = "prefixes";
    private static final String EVALUATION_COMMAND = "evaluate";
    static String solver = "";

    public static void main(String[] rawArgs) {
        String[] args = Arrays.stream(rawArgs).filter(arg -> !arg.equals("--det")).toArray(String[]::new);
        if (args.length != rawArgs.length) {
            try {
                Determinism.pin();
            } catch (IllegalStateException e) {
                System.exit(rejectDeterminism(args, System.out, e.getMessage()));
            }
        }
        try {
            if (args.length > 0 && args[0].equals(PREFIXES_COMMAND)) {
                helpers.CountMain.run(Arrays.copyOfRange(args, 1, args.length));
            } else if (args.length > 0 && args[0].equals(EVALUATION_COMMAND)) {
                PreciseModelCountingEvaluation.run(Arrays.copyOfRange(args, 1, args.length));
            } else if (usesClassicInterface(args)) {
                modelCount(args);
            } else {
                // Solvers and the counter log to standard output; keep it for the JSON.
                PrintStream json = System.out;
                System.setOut(System.err);
                int exit = runCount(args, json);
                json.flush();
                // Timed-out and idle counter threads would otherwise keep the JVM alive.
                System.exit(exit);
            }
        } catch (IOException | InterruptedException | RuntimeException e) {
            e.printStackTrace();
            // Counter threads would otherwise keep the JVM alive after a failure.
            System.exit(1);
        }
    }

    /** The original interface: -formula=, the other counters, or a list of formulas in a file. */
    static boolean usesClassicInterface(String[] args) {
        for (String arg : args) {
            if (arg.startsWith("-formula=") || arg.startsWith("-exact") || arg.startsWith("-re")
                    || arg.startsWith("-auto") || arg.startsWith("-to=") || arg.startsWith("-out=")) {
                return true;
            }
            if (!arg.startsWith("-") && !arg.endsWith(".tlsf") && new File(arg).isFile()) {
                return true;
            }
        }
        return false;
    }

    private static void modelCount(String[] args) throws IOException, InterruptedException {
        String outname = null;
        Formula form;
        String formula = "";
        boolean automaton_counting = true;
        boolean re_counting = false;
        int bound = Settings.MC_BOUND;
        String filepath = null;
        List<String> vars = new LinkedList<>();
        for (String arg : args) {
            if (arg.startsWith("-k=")) {
                String val = arg.replace("-k=", "");
                bound = Integer.parseInt(val);
            } else if (arg.startsWith("-to=")) {
                String val = arg.replace("-to=", "");
                Settings.MC_TIMEOUT = Integer.parseInt(val);
            } else if (arg.startsWith("-vars=")) {
                Collections.addAll(vars, arg.replace("-vars=", "").split(","));
            } else if (arg.startsWith("-out=")) {
                outname = arg.replace("-out=", "");
            } else if (arg.startsWith("-auto")) {
                automaton_counting = true;
            } else if (arg.startsWith("-re")) {
                re_counting = true;
            } else if (arg.startsWith("-formula=")) {
                formula = arg.replace("-formula=", "");
            } else if (arg.equals("-exact")) {
                automaton_counting = false;
            } else if (arg.startsWith("-exact=")) {
                solver = arg.replace("-exact=", "");
                automaton_counting = false;
            } else if (arg.startsWith("-sat-bin=")) {
                Settings.SAT_BIN = arg.replace("-sat-bin=", "");
            } else if (arg.startsWith("-sat=")) {
                String sat = arg.replace("-sat=", "");
                if (!sat.equalsIgnoreCase("aalta") && !sat.equalsIgnoreCase("black")) {
                    System.err.println("ERROR: unknown value for -sat: '" + sat + "' (expected 'aalta' or 'black').");
                    System.exit(1);
                }
                Settings.SAT_SOLVER = sat;
            } else {
                filepath = arg;
            }

        }

        List<Formula> formulas = new LinkedList<>();
        String directoryName = "result";

        String filename = "result/default.out";

        if (filepath == null && formula.isEmpty()) {
            printHelp(System.out);
            return;
        }
        if (!formula.isEmpty()) {
            form = LtlParser.parse(formula, vars).formula();
            List<String> declaredVars = new ArrayList<>(vars);
            vars.clear();
            vars.addAll(new HashSet<>(FormulaUtils.extractAtoms(formula)));
            BigInteger result;
            if (automaton_counting || re_counting)
                try {
                    if (!re_counting)
                        result = declaredVars.isEmpty()
                                ? countExhaustiveAutomataBasedPrefixes(LtlParser.parse(formula).formula(), LtlParser.parse(formula).variables(), bound)
                                : countExhaustiveAutomataBasedPrefixes(form, declaredVars, bound);
                    else
                        result = countExhaustivePrefixesRltl(form, vars, bound);
                    if (result == null)
                        System.out.println("timeout");
                    else System.out.println(result);
                } catch (Exception e) {
                    System.out.println(e);
                }
            else {
                result = countModelsExact(form, vars.size(), bound);
                if ((result == null)) {
                    System.out.println("timeout");
                } else {
                    System.out.println(result);
                }
            }
            System.out.println();
            System.exit(0);
        } else {
            if (filepath.endsWith(".tlsf")) {
                Tlsf tlsf = TlsfUtils.toBasicTLSF(new File(filepath));
                formulas.add(tlsf.toFormula().formula());
                vars = tlsf.variables();
            } else if (filepath.endsWith(".list")) {
                BufferedReader reader;
                reader = new BufferedReader(new FileReader(filepath));

                String line = reader.readLine();
                int numOfVars = 0;
                while (line != null) {

                    if (!line.startsWith("--") && line.endsWith(".tlsf")) {
                        Tlsf tlsf = TlsfUtils.toBasicTLSF(new File(line));
                        formulas.add(tlsf.toFormula().formula());
                        numOfVars = Math.max(numOfVars, tlsf.variables().size());
                    }
                    line = reader.readLine();
                }
                reader.close();
                for (int i = 0; i < numOfVars; i++) {
                    vars.add("v" + i);
                }
            } else {
                BufferedReader reader;
                reader = new BufferedReader(new FileReader(filepath));

                String line = reader.readLine();
                while (line != null) {
                    if (!line.startsWith("--"))
                        formulas.add(LtlParser.syntax(line, vars));
                    line = reader.readLine();
                }
                reader.close();
            }
        }

        if (outname == null) {
            new File(directoryName).mkdirs();
        }
        if (outname != null) {
            if (outname.contains(".")) {
                directoryName = outname.substring(0, outname.lastIndexOf('.'));

            }
            File outfolder = new File(directoryName);
            if (!outfolder.exists() && !outfolder.mkdirs()) {
                System.err.println("Failed to create directory: " + directoryName);
            } else {
                if (outname.contains("/")) {
                    filename = directoryName + outname.substring(outname.lastIndexOf('/'));
                } else {
                    filename = directoryName + File.separator + outname;
                }
            }
        }

        if (automaton_counting || re_counting)
            runPrefixesMC(formulas, vars, bound, filename, re_counting);
        else
            runPreciseMC(formulas, vars, bound, filename);

        System.exit(0);
    }


    static void runPreciseMC(List<Formula> formulas, List<String> vars, int bound, String outname) throws IOException, InterruptedException {
        long initialTOTALTime = System.currentTimeMillis();
        int num_of_formulas = formulas.size();
        BigInteger[] solutions = new BigInteger[num_of_formulas];
        int index = 0;
        System.out.println("Counting...");

        for (Formula f : formulas) {
            long initialTime = System.currentTimeMillis();
            System.out.println(index + " Formula: " + LabelledFormula.of(f, vars));
            BigInteger result = countModelsExact(f, vars.size(), bound);
            if (result != null) {
                System.out.println("Result" + result);
                long finalTime = System.currentTimeMillis();
                long totalTime = finalTime - initialTime;
                int min = (int) (totalTime) / 60000;
                int sec = (int) (totalTime - min * 60000) / 1000;
                String time = String.format("Time: %s m  %s s", min, sec);
                System.out.println(time);
                if (outname != null) {
                    String filename = outname.replace(".out", index + ".out");
                    writeFile(filename, result, time);
                }
                solutions[index] = result;
            } else {
                System.out.println("MC Timeout reached.");
            }
            index++;
        }

        System.out.println("Global ranking...");
        SortedMap<BigInteger, List<Integer>> global_ranking = new TreeMap<>();
        for (int i = 0; i < num_of_formulas; i++) {
            BigInteger key = solutions[i];
            if (key != null) {
                List<Integer> value;
                if (global_ranking.containsKey(key))
                    value = global_ranking.get(key);
                else
                    value = new LinkedList<>();
                value.add(i);
                global_ranking.put(key, value);
            }
        }

        StringBuilder global = new StringBuilder();
        StringBuilder flatten_ranking_str = new StringBuilder();
        int[] formula_ranking = new int[num_of_formulas];
        int pos = 0;
        for (BigInteger key : global_ranking.keySet()) {
            global.append(global_ranking.get(key)).append("\n");
            for (Integer f_pos : global_ranking.get(key)) {
                formula_ranking[f_pos] = pos;
                flatten_ranking_str.append(f_pos).append("\n");
            }
            pos++;
        }

        global.append("\nRanking Levels: ").append(pos).append("\n");
        StringBuilder formula_ranking_str = new StringBuilder();
        for (int i = 0; i < num_of_formulas; i++) {
            formula_ranking_str.append(formula_ranking[i]).append("\n");
        }

        System.out.println(global);


        long finalTOTALTime = System.currentTimeMillis();
        long totalTime = finalTOTALTime - initialTOTALTime;
        int min = (int) (totalTime) / 60000;
        int sec = (int) (totalTime - min * 60000) / 1000;
        String time = String.format("Time: %s m  %s s", min, sec);
        System.out.println(time);

        if (outname != null) {
            writeRanking(outname.replace(".out", "-global.out"), global.toString(), time);
            writeRanking(outname.replace(".out", "-ranking-by-formula.out"), formula_ranking_str.toString(), "");
            writeRanking(outname.replace(".out", "-ranking.out"), flatten_ranking_str.toString(), "");
            String rank_as_number = outname.replace(".out", "rankingNumber.out");
            writeRanking(rank_as_number, global_ranking);
        }
    }

    static void runPrefixesMC(List<Formula> formulas, List<String> vars, int bound, String outname, boolean re_counting) throws IOException {
        long initialTOTALTime = System.currentTimeMillis();
        int num_of_formulas = formulas.size();
        BigInteger[] solutions = new BigInteger[num_of_formulas];
        int index = 0;
        System.out.println("Counting...");
        for (Formula f : formulas) {
            long initialTime = System.currentTimeMillis();
            System.out.println(index + " Formula: " + LabelledFormula.of(f, vars));
            BigInteger result = null;
            try {
                if (!re_counting)
                    result = countExhaustiveAutomataBasedPrefixes(f, vars, bound);
                else
                    result = countExhaustivePrefixesRltl(f, vars, bound);
                System.out.println(result);
            } catch (Exception e) {
                System.out.println(e);
            }
            long finalTime = System.currentTimeMillis();
            long totalTime = finalTime - initialTime;
            int min = (int) (totalTime) / 60000;
            int sec = (int) (totalTime - min * 60000) / 1000;
            String time = String.format("Time: %s m  %s s", min, sec);
            System.out.println(time);
            if (outname != null && result != null) {
                String filename = outname.replace(".out", (re_counting ? "re-" : "auto-") + index + ".out");
                writeFile(filename, result, time);
            }
            solutions[index] = result;
            index++;
        }


        System.out.println("Global ranking...");
        SortedMap<BigInteger, List<Integer>> global_ranking = new TreeMap<>();
        for (int i = 0; i < num_of_formulas; i++) {
            BigInteger key = solutions[i];
            if (key != null) {
                List<Integer> value;
                if (global_ranking.containsKey(key))
                    value = global_ranking.get(key);
                else
                    value = new LinkedList<>();
                value.add(i);
                global_ranking.put(key, value);
            }
        }

        StringBuilder global = new StringBuilder();
        StringBuilder flatten_ranking_str = new StringBuilder();
        int[] formula_ranking = new int[num_of_formulas];
        int pos = 0;
        for (BigInteger key : global_ranking.keySet()) {
            global.append(global_ranking.get(key)).append("\n");
            for (Integer f_pos : global_ranking.get(key)) {
                formula_ranking[f_pos] = pos;
                flatten_ranking_str.append(f_pos).append("\n");
            }
            pos++;
        }

        global.append("\nRanking Levels: ").append(pos).append("\n");
        StringBuilder formula_ranking_str = new StringBuilder();
        for (int i = 0; i < num_of_formulas; i++) {
            formula_ranking_str.append(formula_ranking[i]).append("\n");
        }

        System.out.println(global);

        long finalTOTALTime = System.currentTimeMillis();
        long totalTime = finalTOTALTime - initialTOTALTime;
        int min = (int) (totalTime) / 60000;
        int sec = (int) (totalTime - min * 60000) / 1000;
        String time = String.format("Time: %s m  %s s", min, sec);
        System.out.println(time);

        if (outname != null) {
            String filename = outname.replace(".out", (re_counting ? "re-" : "auto-") + "global.out");
            writeRanking(filename, global.toString(), time);
            String ranking_formula_filename = outname.replace(".out", (re_counting ? "re-" : "auto-") + "ranking-by-formula.out");
            writeRanking(ranking_formula_filename, formula_ranking_str.toString(), "");
            String ranking_filename = outname.replace(".out", (re_counting ? "re-" : "auto-") + "ranking.out");
            writeRanking(ranking_filename, flatten_ranking_str.toString(), "");
            String rank_as_number = outname.replace(".out", (re_counting ? "re-" : "auto-") + "rankingNumber.out");
            writeRanking(rank_as_number, global_ranking);
        }
    }


    static BigInteger countModelsExact(Formula formula, int vars, int bound) throws IOException, InterruptedException {
        PreciseLTLModelCounter counter = new PreciseLTLModelCounter();
        if (solver.equals(PreciseLTLModelCounter.MODEL_COUNTER.MINIC2D.toString()))
            counter.modelcounter = PreciseLTLModelCounter.MODEL_COUNTER.MINIC2D;
        else if (solver.equals(PreciseLTLModelCounter.MODEL_COUNTER.CACHET.toString()))
            counter.modelcounter = PreciseLTLModelCounter.MODEL_COUNTER.CACHET;
        else counter.modelcounter = PreciseLTLModelCounter.MODEL_COUNTER.RELSAT;
        System.out.println(counter.modelcounter);
        Settings.MC_BOUND = bound;
        return counter.count(formula, vars);
    }

    static BigInteger countExhaustivePrefixesRltl(Formula f, List<String> vars, int bound) throws IOException, InterruptedException {
        LabelledFormula form_lost = LabelledFormula.of(f, vars);
        CountREModels counter = new CountREModels();
        return counter.countPrefixes(form_lost, bound);
    }

    /** @return the count, or {@code null} if counting failed */
    static BigInteger countExhaustiveAutomataBasedPrefixes(Formula f, List<String> vars, int bound) {
        CountResult result = ModelCounter.count(LabelledFormula.of(f, vars), bound);
        return result.isFailure() ? null : result.count();
    }

    private static void writeFile(String filename, BigInteger result, String time) throws IOException {
        File file = new File(filename);
        FileWriter fw = new FileWriter(file.getAbsoluteFile());
        BufferedWriter bw = new BufferedWriter(fw);
        bw.write("0 0\n");
        bw.write(" ");
        bw.write(result.toString());
        bw.write("\n");
        bw.write(time + "\n");
        bw.flush();
        bw.close();
    }

    private static void writeRanking(String filename, SortedMap<BigInteger, List<Integer>> ranking) throws IOException {
        File file = new File(filename);
        FileWriter fw = new FileWriter(file.getAbsoluteFile());
        BufferedWriter bw = new BufferedWriter(fw);
        for (BigInteger key : ranking.keySet()) {
            for (Integer ignored : ranking.get(key)) {
                bw.write(String.valueOf(key));
                bw.write("\n");
            }
        }
        bw.close();
    }

    private static void writeRanking(String filename, String ranking, String time) throws IOException {
        File file = new File(filename);
        FileWriter fw = new FileWriter(file.getAbsoluteFile());
        BufferedWriter bw = new BufferedWriter(fw);
        bw.write(ranking + "\n");
        bw.write(time + "\n");
        bw.flush();
        bw.close();
    }

    static int runCount(String[] args, PrintStream out) {
        int bound = Settings.MC_BOUND;
        List<String> vars = null;
        boolean dump = false;
        String input = null;
        boolean countOnly = Arrays.asList(args).contains("-c") || Arrays.asList(args).contains("-count");
        // With -c, standard output holds a number or nothing at all.
        PrintStream errors = countOnly ? System.err : out;

        try {
            for (String arg : args) {
                if (arg.equals("--help") || arg.equals("-h")) {
                    printHelp(out);
                    return EXIT_OK;
                } else if (arg.startsWith("-k=")) {
                    bound = Integer.parseInt(arg.substring(3));
                } else if (arg.startsWith("-vars=")) {
                    vars = Arrays.stream(arg.substring(6).split(",")).map(String::trim).collect(Collectors.toList());
                } else if (arg.startsWith("-sat-bin=")) {
                    Settings.SAT_BIN = arg.substring(9);
                } else if (arg.startsWith("-sat=")) {
                    String sat = arg.substring(5);
                    if (!sat.equalsIgnoreCase("aalta") && !sat.equalsIgnoreCase("black"))
                        return usage(errors, "unknown value for -sat: " + sat);
                    Settings.SAT_SOLVER = sat;
                } else if (arg.startsWith("--timeout=")) {
                    int seconds = Integer.parseInt(arg.substring(10));
                    Settings.PARSING_TIMEOUT = seconds;
                    Settings.MC_TIMEOUT = seconds;
                } else if (arg.equals("--dump")) {
                    dump = true;
                } else if (arg.equals("-c") || arg.equals("-count")) {
                    continue;
                } else if (arg.startsWith("-")) {
                    return usage(errors, "unknown option: " + arg);
                } else if (input != null) {
                    return usage(errors, "more than one formula given");
                } else {
                    input = arg;
                }
            }
        } catch (NumberFormatException e) {
            return usage(errors, "invalid number: " + e.getMessage());
        }
        if (input == null)
            return usage(errors, "no formula given; see --help");
        if (countOnly && dump)
            return usage(errors, "-c and --dump cannot be combined");
        if (input.endsWith(".tlsf") && vars != null)
            return usage(errors, "-vars cannot be used with a TLSF file, which declares its own variables");

        long start = System.nanoTime();
        try {
            LabelledFormula formula = input.endsWith(".tlsf") ? fromTlsf(new File(input))
                    : vars != null ? LtlParser.parse(input, vars) : LtlParser.parse(input);
            if (dump && formula.variables().size() > AutomatonDump.MAX_VARIABLES)
                return failure(errors, "alphabet too large to dump: " + formula.variables().size()
                        + " variables (maximum " + AutomatonDump.MAX_VARIABLES + ")", start);

            CountResult result = ModelCounter.count(formula, bound);
            if (countOnly && !result.isFailure()) {
                out.println(result.count());
                return EXIT_OK;
            }
            StringBuilder line = new StringBuilder("{\"status\":\"").append(result.status().label()).append('"');
            if (result.isFailure()) {
                line.append(",\"detail\":\"").append(result.status().name().toLowerCase()).append('"');
            } else {
                line.append(",\"count\":\"").append(result.count()).append('"');
            }
            line.append(",\"ms\":").append(millisSince(start));
            if (dump && result.status() == CountResult.Status.OK) {
                Optional<AutomatonDump> automaton = ModelCounter.dump(formula);
                automaton.ifPresent(a -> line.append(",\"dump\":").append(toJson(a)));
            }
            errors.println(line.append('}'));
            return result.isFailure() ? EXIT_FAILURE : EXIT_OK;
        } catch (IOException | InterruptedException | RuntimeException e) {
            if (e instanceof InterruptedException)
                Thread.currentThread().interrupt();
            return failure(errors,e.toString(), start);
        }
    }

    /** Translates a copy, because the syfco step writes next to its input. */
    private static LabelledFormula fromTlsf(File spec) throws IOException, InterruptedException {
        if (!spec.isFile())
            throw new IOException("file not found: " + spec);
        Path dir = Files.createTempDirectory("count-models");
        try {
            Path copy = dir.resolve("spec.tlsf");
            Files.copy(spec.toPath(), copy);
            return TlsfUtils.toBasicTLSF(copy.toFile()).toFormula();
        } finally {
            try (Stream<Path> files = Files.walk(dir)) {
                files.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            }
        }
    }

    /** Reports that {@code --det} is unavailable, in the format of the command that asked for it. */
    static int rejectDeterminism(String[] args, PrintStream out, String message) {
        boolean json = !usesClassicInterface(args)
                && !Arrays.asList(args).contains("-c") && !Arrays.asList(args).contains("-count");
        return usage(json ? out : System.err, message);
    }

    private static long millisSince(long start) {
        return (System.nanoTime() - start) / 1_000_000L;
    }

    private static int failure(PrintStream out, String message, long start) {
        out.println("{\"status\":\"error\",\"detail\":" + quote(message) + ",\"ms\":" + millisSince(start) + "}");
        return EXIT_FAILURE;
    }

    private static int usage(PrintStream out, String message) {
        out.println("{\"status\":\"error\",\"detail\":" + quote(message) + ",\"ms\":0}");
        return EXIT_USAGE;
    }

    private static String toJson(AutomatonDump dump) {
        String transitions = dump.transitions().stream()
                .map(t -> "{\"state\":" + t.state + ",\"letter\":" + t.letter + ",\"successor\":"
                        + (t.successor == AutomatonDump.NO_SUCCESSOR ? "null" : String.valueOf(t.successor))
                        + ",\"accepting\":" + t.accepting + "}")
                .collect(Collectors.joining(","));
        String variables = dump.variables().stream().map(Main::quote).collect(Collectors.joining(","));
        return "{\"variables\":[" + variables + "],\"states\":" + dump.stateCount()
                + ",\"initial\":" + dump.initialStates().toString().replace(" ", "")
                + ",\"final\":" + dump.finalStates().toString().replace(" ", "")
                + ",\"transitions\":[" + transitions + "]}";
    }

    private static String quote(String text) {
        StringBuilder quoted = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            if (c == '"' || c == '\\') {
                quoted.append('\\').append(c);
            } else if (c < 0x20) {
                quoted.append(String.format("\\u%04x", (int) c));
            } else {
                quoted.append(c);
            }
        }
        return quoted.append('"').toString();
    }

    private static void printHelp(PrintStream out) {
        out.println("Usage: ./modelcount.sh [options] 'FORMULA' | FILE.tlsf");
        out.println();
        out.println("Counts the words of length k accepted by the formula's automaton and prints");
        out.println("one JSON line: {\"status\":\"ok\",\"count\":\"...\",\"ms\":...}");
        out.println("status is ok, unsat_simplified, unsat_solver, timeout or error.");
        out.println("The exit code is 0 for ok and unsat_*, 1 on a failure, 2 on bad usage.");
        out.println();
        out.println("  -k=N            word length (default " + Settings.MC_BOUND + ")");
        out.println("  -vars=a,b,...   declared variables; they set the alphabet and so the count.");
        out.println("                  Default: the variables that appear in the formula.");
        out.println("                  Not allowed with TLSF files, which declare their own.");
        out.println("  -sat=NAME       satisfiability solver: aalta (default) or black");
        out.println("  -sat-bin=PATH   override the satisfiability solver binary");
        out.println("  --timeout=S     translation and counting timeouts, in seconds");
        out.println("  --dump          add the automaton to the output (up to "
                + AutomatonDump.MAX_VARIABLES + " variables, status ok only)");
        out.println("  -c, -count      print only the number; on a failure print nothing on standard");
        out.println("                  output, report on standard error and exit non-zero");
        out.println("  --det           repeatable counts; experimental, see docs/non-determinism.md");
        out.println("  --help          show this help");
        out.println();
        out.println("Older options, which print the number on its own:");
        out.println("  -formula=F      the formula to count");
        out.println("  -to=S           counting timeout, in seconds");
        out.println("  -out=FILE       where to write the results for a file of formulas");
        out.println("  -re             use the regular-expression counter");
        out.println("  -exact[=NAME]   use the exact counter: relsat (default), cachet or miniC2D");
    }
}
