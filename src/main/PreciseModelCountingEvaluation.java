package main;

import modelcounter.estimate.EmersonLeiAutomatonBasedModelCounting;
import modelcounter.exact.PreciseLTLModelCounter;
import modelcounter.re.CountREModels;
import owl.ltl.Conjunction;
import owl.ltl.Formula;
import owl.ltl.LabelledFormula;
import owl.ltl.parser.LtlParser;

import java.io.*;
import java.math.BigInteger;
import java.util.*;

/**
 * The type Precise model counting evaluation.
 */
public class PreciseModelCountingEvaluation {
    /**
     * The entry point of application.
     *
     * @param args the input arguments
     * @throws IOException          the io exception
     * @throws InterruptedException the interrupted exception
     */
    public static void main(String[] args) throws IOException, InterruptedException {
        String formula = null;
        List<String> refinemets = new LinkedList<>();
        String outname = null;
        int solver = 0;
        boolean re_counting = false;
        boolean automaton_counting = false;
        int bound = 0;
        String filepath = null;
        boolean benchmarkusage = false;
        List<String> vars = new LinkedList<>();
        for (String arg : args) {
            if (arg.startsWith("-b=")) {
                filepath = arg.replace("-b=", "");
                benchmarkusage = true;
            } else if (arg.startsWith("-k=")) {
                String val = arg.replace("-k=", "");
                bound = Integer.parseInt(val);
            } else if (arg.startsWith("-vars=")) {
                Collections.addAll(vars, arg.replace("-vars=", "").split(","));
            } else if (arg.startsWith("-out=")) {
                outname = arg.replace("-out=", "");
            } else if (arg.startsWith("-cachet")) {
                solver = 1;
            } else if (arg.startsWith("-ganak")) {
                solver = 2;
            } else if (arg.startsWith("-re")) {
                re_counting = true;
            } else if (arg.startsWith("-auto")) {
                automaton_counting = true;
            } else if (arg.startsWith("-ref=")) {
                refinemets.add(arg.replace("-ref=", ""));
            } else if (arg.startsWith("-ltl=")) {
                formula = arg.replace("-ltl=", "");
            } else {
                //assume that the argument with no flag is the formula
                formula = arg;
            }
        }

        Formula original_formula = null;
        List<Formula> refined_formulas = new LinkedList<>();

        if (benchmarkusage && filepath == null) {
            System.out.println("Use ./modelcounter.sh [-b=pathToFile] [-k=bound | -vars=a,b,c | -no-precise]");
            return;
        } else if (benchmarkusage) {

            BufferedReader reader;
            reader = new BufferedReader(new FileReader(filepath));

            //First formula as original one
            if (formula == null)
                formula = reader.readLine();

            original_formula = LtlParser.syntax(formula, vars);

            refined_formulas = new ArrayList<Formula>();
            String line = reader.readLine();
            while (line != null) {
                if (!line.startsWith("--"))
                    refined_formulas.add(LtlParser.syntax(line, vars));
                line = reader.readLine();
            }
            reader.close();
        } else if (formula == null || refinemets.isEmpty()) {
            correctUsage();
            return;
        }

        if (!benchmarkusage) {
            if (vars.isEmpty())
                original_formula = LtlParser.syntax(formula);
            else
                original_formula = LtlParser.syntax(formula, vars);

            for (String s : refinemets) {
                if (vars.isEmpty())
                    refined_formulas.add(LtlParser.syntax(s));
                else
                    refined_formulas.add(LtlParser.syntax(s, vars));
            }
        }
        String directoryName = "result";
        String filename = "result/default.out";
        if (outname != null) {
            if (outname.contains(".")) {
                directoryName = outname.substring(0, outname.lastIndexOf('.'));

            }
            File outfolder = new File(directoryName);
            if (!outfolder.exists() && !outfolder.mkdirs()) {
                // Handle the case where directory creation failed, e.g., due to permission issues.
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
            runPrefixesMC(automaton_counting, original_formula, refined_formulas, vars, bound, filename);
        else
            runPreciseMC(original_formula, refined_formulas, vars, bound, solver, filename);

    }

    /**
     * Run precise mc.
     *
     * @param original_formula the original formula
     * @param refined_formulas the refined formulas
     * @param vars             the vars
     * @param bound            the bound
     * @param solver           the solver
     * @param outname          the outname
     * @throws IOException          the io exception
     * @throws InterruptedException the interrupted exception
     */
    static void runPreciseMC(Formula original_formula, List<Formula> refined_formulas, List<String> vars, int bound, int solver, String outname) throws IOException, InterruptedException {
        long initialTOTALTime = System.currentTimeMillis();
        int num_of_formulas = refined_formulas.size();
        List<BigInteger>[] solutions = new List[num_of_formulas];
        List<Integer> timeout_formulas = new LinkedList<>();
        int index = 0;
        System.out.println("Base: " + LabelledFormula.of(original_formula, vars));
        System.out.println("Counting...");

        for (Formula ref : refined_formulas) {
            long initialTime = System.currentTimeMillis();
            System.out.println(index + " Formula: " + LabelledFormula.of(ref, vars));
            List<BigInteger> result = countModelsExact(original_formula, ref, vars.size(), bound, solver);
            if (result != null) {
                System.out.println(result);
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
                timeout_formulas.add(index);
            }
            index++;
        }
        System.out.println("Formula ranking for bounds 1..k");
        SortedMap<BigInteger, List<Integer>>[] ranking = new TreeMap[bound];
        for (int k = 0; k < bound; k++) {
            List<BigInteger> k_values = new LinkedList<>();
            for (int i = 0; i < num_of_formulas; i++) {
                if (solutions[i] != null)
                    k_values.add(solutions[i].get(k));
                else
                    k_values.add(null);
            }

            SortedMap<BigInteger, List<Integer>> order = getBigIntegerListSortedMap(num_of_formulas, timeout_formulas, k_values);
            ranking[k] = order;
            System.out.println((k + 1) + " " + order.values());
        }
        if (outname != null)
            writeRanking(outname, ranking);

        System.out.println("Global ranking...");
        List<BigInteger> totalNumOfModels = new LinkedList<>();
        StringBuilder sumTotalNumOfModels = new StringBuilder();
        for (int i = 0; i < num_of_formulas; i++) {
            BigInteger f_result = BigInteger.ZERO;
            if (solutions[i] == null)
                f_result = null;
            else {
                for (BigInteger v : solutions[i])
                    f_result = f_result.add(v);
            }
            sumTotalNumOfModels.append(i).append(" ").append(f_result).append("\n");
            totalNumOfModels.add(f_result);
        }

        if (outname != null)
            writeRanking(outname.replace(".out", "-summary.out"), sumTotalNumOfModels.toString(), "");

        SortedMap<BigInteger, List<Integer>> global_ranking = new TreeMap<>();
        for (int i = 0; i < num_of_formulas; i++) {
            BigInteger key = totalNumOfModels.get(i);
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
//        int i = 0;
        int pos = 0;
        for (BigInteger key : global_ranking.keySet()) {
            global.append(global_ranking.get(key)).append("\n");
//            i += global_ranking.get(key).size();
//            if (i < num_of_formulas-1)
//                global +=", ";
//            else
//                global +="]";
            for (Integer f_pos : global_ranking.get(key)) {
                //int f_pos = refined_formulas.indexOf(f);
                formula_ranking[f_pos] = pos;
                flatten_ranking_str.append(f_pos).append("\n");
            }
            pos++;
        }

        global.append("\nRanking Levels: ").append(pos).append("\n");
        if (!timeout_formulas.isEmpty()) {
            global.append("\nTimeout Formulas: ").append(timeout_formulas);
        }

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
        }
    }

    private static SortedMap<BigInteger, List<Integer>> getBigIntegerListSortedMap(int num_of_formulas, List<Integer> timeout_formulas, List<BigInteger> k_values) {
        SortedMap<BigInteger, List<Integer>> order = new TreeMap<>();
        for (int i = 0; i < num_of_formulas; i++) {
            if (timeout_formulas.contains(i))
                continue;
            BigInteger key = k_values.get(i);
            List<Integer> value;
            if (order.containsKey(key))
                value = order.get(key);
            else
                value = new LinkedList<>();
            value.add(i);
            order.put(key, value);
        }
        return order;
    }

    /**
     * Run prefixes mc.
     *
     * @param automaton        the automaton
     * @param original_formula the original formula
     * @param refined_formulas the refined formulas
     * @param vars             the vars
     * @param bound            the bound
     * @param outname          the outname
     * @throws IOException          the io exception
     * @throws InterruptedException the interrupted exception
     */
    static void runPrefixesMC(boolean automaton, Formula original_formula, List<Formula> refined_formulas, List<String> vars, int bound, String outname) throws IOException, InterruptedException {
        long initialTOTALTime = System.currentTimeMillis();
        int num_of_formulas = refined_formulas.size();
        BigInteger[] solutions = new BigInteger[num_of_formulas];
        int index = 0;
        System.out.println("Counting...");
        for (Formula ref : refined_formulas) {
            long initialTime = System.currentTimeMillis();
            System.out.println(index + " Formula: " + LabelledFormula.of(ref, vars));
            BigInteger result;
            if (automaton)
                result = countExhaustiveAutomataBasedPrefixes(original_formula, ref, vars, bound);
            else
                result = countExhaustivePrefixesRltl(original_formula, ref, vars, bound);
            System.out.println(result);
            long finalTime = System.currentTimeMillis();
            long totalTime = finalTime - initialTime;
            int min = (int) (totalTime) / 60000;
            int sec = (int) (totalTime - min * 60000) / 1000;
            String time = String.format("Time: %s m  %s s", min, sec);
            System.out.println(time);
            if (outname != null) {
                String filename = automaton ? outname.replace(".out", "auto-" + index + ".out") : outname.replace(".out", "re-" + index + ".out");
                writeFile(filename, List.of(result), time);
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
//        int i = 0;
        int pos = 0;
        for (BigInteger key : global_ranking.keySet()) {
            global.append(global_ranking.get(key)).append("\n");
//            i += global_ranking.get(key).size();
//            if (i < num_of_formulas-1)
//                global +=", ";
//            else
//                global +="]";

            for (Integer f_pos : global_ranking.get(key)) {
                //int f_pos = refined_formulas.indexOf(f);
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
            String filename = automaton ? outname.replace(".out", "auto-global.out") : outname.replace(".out", "re-global.out");
            writeRanking(filename, global.toString(), time);
            String ranking_formula_filename = automaton ? outname.replace(".out", "auto-ranking-by-formula.out") : outname.replace(".out", "re-ranking-by-formula.out");
            writeRanking(ranking_formula_filename, formula_ranking_str.toString(), "");
            String ranking_filename = automaton ? outname.replace(".out", "auto-ranking.out") : outname.replace(".out", "re-ranking.out");
            writeRanking(ranking_filename, flatten_ranking_str.toString(), "");
        }
    }

    /**
     * Count models exact list.
     *
     * @param original the original
     * @param refined  the refined
     * @param vars     the vars
     * @param bound    the bound
     * @param solver   the solver
     * @return the list
     * @throws IOException          the io exception
     * @throws InterruptedException the interrupted exception
     */
    static List<BigInteger> countModelsExact(Formula original, Formula refined, int vars, int bound, int solver) throws IOException, InterruptedException {

        PreciseLTLModelCounter counter = new PreciseLTLModelCounter();
        Settings.MC_BOUND = bound;
        if (solver == 1)
            counter.modelcounter = PreciseLTLModelCounter.MODEL_COUNTER.CACHET;
        else if (solver == 2)
            counter.modelcounter = PreciseLTLModelCounter.MODEL_COUNTER.GANAK;
        Formula f = Conjunction.of(original, refined.not());
        BigInteger lostModels = counter.count(f, vars);
        if (lostModels == null)
            return null;


        PreciseLTLModelCounter counter2 = new PreciseLTLModelCounter();
        Settings.MC_BOUND = bound;
        if (solver == 1)
            counter2.modelcounter = PreciseLTLModelCounter.MODEL_COUNTER.CACHET;
        else if (solver == 2)
            counter2.modelcounter = PreciseLTLModelCounter.MODEL_COUNTER.GANAK;
        Formula f2 = Conjunction.of(original.not(), refined);
        BigInteger wonModels = counter2.count(f2, vars);
        if (wonModels == null)
            return null;

        List<BigInteger> result = new LinkedList<>();
        for (int i = 0; i < bound - 1; i++) {
            result.add(BigInteger.ZERO);
        }
        result.add(lostModels.add(wonModels));
        return result;
    }


    /**
     * Count exhaustive prefixes using RE big integer.
     *
     * @param original the original
     * @param refined  the refined
     * @param vars     the vars
     * @param bound    the bound
     * @return the big integer
     * @throws IOException          the io exception
     * @throws InterruptedException the interrupted exception
     */
    static BigInteger countExhaustivePrefixesRltl(Formula original, Formula refined, List<String> vars, int bound) throws IOException, InterruptedException {
        Formula conj_lost = Conjunction.of(original, refined.not());
        LabelledFormula form_lost = LabelledFormula.of(conj_lost, vars);
        CountREModels counter = new CountREModels();
        BigInteger lostModels = counter.countPrefixes(form_lost, bound);

        Formula conj_won = Conjunction.of(original.not(), refined);
        LabelledFormula form_won = LabelledFormula.of(conj_won, vars);
        CountREModels counter2 = new CountREModels();
        BigInteger wonModels = counter2.countPrefixes(form_won, bound);
        BigInteger result = lostModels.add(wonModels);
        return result;
    }

    /**
     * Count exhaustive automata based prefixes big integer.
     *
     * @param original the original
     * @param refined  the refined
     * @param vars     the vars
     * @param bound    the bound
     * @return the big integer
     * @throws IOException          the io exception
     * @throws InterruptedException the interrupted exception
     */
    static BigInteger countExhaustiveAutomataBasedPrefixes(Formula original, Formula refined, List<String> vars, int bound) {
        Formula conj_lost = Conjunction.of(original, refined.not());
        LabelledFormula form_lost = LabelledFormula.of(conj_lost, vars);
        EmersonLeiAutomatonBasedModelCounting counter = new EmersonLeiAutomatonBasedModelCounting<>(form_lost);
        BigInteger lostModels = counter.count(bound);

        Formula conj_won = Conjunction.of(original.not(), refined);
        LabelledFormula form_won = LabelledFormula.of(conj_won, vars);
        EmersonLeiAutomatonBasedModelCounting counter2 = new EmersonLeiAutomatonBasedModelCounting<>(form_won);
        BigInteger wonModels = counter2.count(bound);
        BigInteger result = lostModels.add(wonModels);
        return result;
    }

    private static void writeFile(String filename, List<BigInteger> result, String time) throws IOException {
        File file = new File(filename);
        FileWriter fw = new FileWriter(file.getAbsoluteFile());
        BufferedWriter bw = new BufferedWriter(fw);
        bw.write("0 0\n");
        for (int i = 0; i < result.size(); i++) {
            BigInteger sol = result.get(i);
            bw.write(String.valueOf(i + 1));
            bw.write(" ");
            bw.write(sol.toString());
            bw.write("\n");
//            System.out.println((i+1) + " " + sol);
        }
        bw.write(time + "\n");
        bw.flush();
        bw.close();
    }

    private static void writeRanking(String filename, SortedMap<BigInteger, List<Integer>>[] ranking) throws IOException {
        File file = new File(filename);
        FileWriter fw = new FileWriter(file.getAbsoluteFile());
        BufferedWriter bw = new BufferedWriter(fw);

        for (int i = 0; i < ranking.length; i++) {
            for (BigInteger key : ranking[i].keySet())
                bw.write(ranking[i].get(key).toString());
            bw.write("\n");
//            System.out.println((i+1) + " " + ranking[i].toString());
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

    private static void correctUsage() {
        System.out.println("Use ./modelcounter.sh [-ref=refined=formula | -k=bound | -vars=a,b,c | -no-precise] [-ltl=]LTL_original_formula");
    }
}
