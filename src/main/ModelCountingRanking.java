package main;

import modelcounter.CountRltlConv;
import modelcounter.EmersonLeiAutomatonBasedModelCounting;
import owl.ltl.Formula;
import owl.ltl.LabelledFormula;
import owl.ltl.parser.LtlParser;
import owl.ltl.tlsf.Tlsf;
import solvers.PreciseLTLModelCounter;
import tlsf.TLSF_Utils;

import java.io.*;
import java.math.BigInteger;
import java.util.*;

public class ModelCountingRanking {
    private BigInteger individual_result = BigInteger.ZERO;
    public static void main(String[] args) throws IOException, InterruptedException {
        String outname = null;
        Formula form = null;
        String formula = "";
        boolean automaton_counting = false;
        boolean re_counting = false;
        int bound = 0;
        String filepath = null;
        List<String> vars = new LinkedList<>();
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("-k=")) {
                String val = args[i].replace("-k=", "");
                bound = Integer.parseInt(val);
            } else if (args[i].startsWith("-vars=")) {
                Collections.addAll(vars, args[i].replace("-vars=", "").split(","));
            } else if (args[i].startsWith("-out=")) {
                outname = args[i].replace("-out=", "");
            } else if (args[i].startsWith("-auto")) {
                automaton_counting = true;
            } else if (args[i].startsWith("-re")) {
                re_counting = true;
            } else if (args[i].startsWith("-formula=")) {
                formula = args[i].replace("-formula=", "");
//                System.out.println(formula);
            } else {
                filepath = args[i];
            }

        }

        List<Formula> formulas = new LinkedList<>();
        String directoryName = "result";

        String filename = "result/default.out";

        if (filepath == null && formula.equals("")) {
            System.out.println("Use ./modelcounter.sh [-formula=LTL-Formula | -b=pathToFile] [-k=bound | -auto | -re | -vars=a,b,c | -no-precise]");
            return;
        }
        if (!formula.equals("")) {
            form = LtlParser.parse(formula, vars).formula();
            BigInteger result;
            if (automaton_counting || re_counting)
                try {
                    if (!re_counting)
                        result = countExhaustiveAutomataBasedPrefixes(form, vars, bound);
                    else
                        result = countExhaustivePrefixesRltl(form, vars, bound);
                    System.out.println(result);
                } catch (Exception e) {
                    System.out.println(e);
                }
            else
                System.out.println(countModelsExact(form, vars.size(), bound));
            System.exit(0);
        } else if (filepath.endsWith(".tlsf")) {
            Tlsf tlsf = TLSF_Utils.toBasicTLSF(new File(filepath));
            formulas.add(tlsf.toFormula().formula());
            vars = tlsf.variables();
        } else if (filepath.endsWith(".list")) {
            BufferedReader reader;
            reader = new BufferedReader(new FileReader(filepath));

            String line = reader.readLine();
            int numOfVars = 0;
            while (line != null) {

                if (!line.startsWith("--") && line.endsWith(".tlsf")) {
                    Tlsf tlsf = TLSF_Utils.toBasicTLSF(new File(line));
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

        if (outname != null) {
            if (outname.contains(".")) {
                directoryName = outname.substring(0, outname.lastIndexOf('.'));

            }
            File outfolder = new File(directoryName);
            if (!outfolder.exists())
                outfolder.mkdir();
            if (outname.contains("/")) {
                filename = directoryName + outname.substring(outname.lastIndexOf('/'));
            } else
                filename = outname;
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
        List<Integer> timeout_formulas = new LinkedList<>();
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
                solutions[index] = (BigInteger) result;
            } else {
                System.out.println("MC Timeout reached.");
                timeout_formulas.add(index);
            }
            index++;
        }
//        System.out.println("Formula ranking for bounds 1..k");
//        SortedMap<BigInteger, List<Integer>>[] ranking = new TreeMap[bound];
//        for (int k = 0; k < bound; k++) {
//            List<BigInteger> k_values = new LinkedList<>();
//            for (int i = 0; i < num_of_formulas; i++) {
//                if (solutions[i] != null)
//                    k_values.add(solutions[i].get(k));
//                else
//                    k_values.add(null);
//            }
//
//            SortedMap<BigInteger, List<Integer>> order = new TreeMap<>();
//            for (int i = 0; i < num_of_formulas; i++) {
//                if (timeout_formulas.contains(i))
//                    continue;
//                BigInteger key = k_values.get(i);
//                List<Integer> value;
//                if (order.containsKey(key))
//                    value = order.get(key);
//                else
//                    value = new LinkedList<>();
//                value.add(i);
//                order.put(key, value);
//            }
//            ranking[k] = order;
//            System.out.println((k + 1) + " " + order.values());
//        }
//        if (outname != null)
//            writeRanking(outname, ranking);
//
//        System.out.println("Global ranking...");
//        List<BigInteger> totalNumOfModels = new LinkedList<>();
//        String sumTotalNumOfModels = "";
//        for (int i = 0; i < num_of_formulas; i++) {
//            BigInteger f_result = BigInteger.ZERO;
//            if (solutions[i] == null)
//                f_result = null;
//            else {
//                for (BigInteger v : solutions[i])
//                    f_result = f_result.add(v);
//            }
//            sumTotalNumOfModels += i + " " + f_result + "\n";
//            totalNumOfModels.add(f_result);
//        }
//
//        if (outname != null)
//            writeRanking(outname.replace(".out", "-summary.out"), sumTotalNumOfModels, "");
//
//        SortedMap<BigInteger, List<Integer>> global_ranking = new TreeMap<>();
//        for (int i = 0; i < num_of_formulas; i++) {
//            BigInteger key = totalNumOfModels.get(i);
//            if (key != null) {
//                List<Integer> value;
//                if (global_ranking.containsKey(key))
//                    value = global_ranking.get(key);
//                else
//                    value = new LinkedList<>();
//                value.add(i);
//                global_ranking.put(key, value);
//            }
//        }
//
//        String global = "";
//        String flatten_ranking_str = "";
//        int[] formula_ranking = new int[num_of_formulas];
//        int pos = 0;
//        for (BigInteger key : global_ranking.keySet()) {
//            global += global_ranking.get(key) + "\n";
//            for (Integer f_pos : global_ranking.get(key)) {
//                formula_ranking[f_pos] = pos;
//                flatten_ranking_str += f_pos + "\n";
//            }
//            pos++;
//        }
//
//        global += "\nRanking Levels: " + pos + "\n";
//        if (!timeout_formulas.isEmpty()) {
//            global += "\nTimeout Formulas: " + timeout_formulas;
//        }
//
//        String formula_ranking_str = "";
//        for (int i = 0; i < num_of_formulas; i++) {
//            formula_ranking_str += formula_ranking[i] + "\n";
//        }
//        System.out.println(global);

        System.out.println("Global ranking...");
        SortedMap<BigInteger, List<Integer>> global_ranking = new TreeMap<>();
        for (int i = 0; i < num_of_formulas; i++) {
            BigInteger key = (BigInteger) solutions[i];
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

        String global = "";
        String flatten_ranking_str = "";
        int[] formula_ranking = new int[num_of_formulas];
        int pos = 0;
        for (BigInteger key : global_ranking.keySet()) {
            global += global_ranking.get(key) + "\n";
            for (Integer f_pos : global_ranking.get(key)) {
                formula_ranking[f_pos] = pos;
                flatten_ranking_str += f_pos + "\n";
            }
            pos++;
        }

        global += "\nRanking Levels: " + pos + "\n";
        String formula_ranking_str = "";
        for (int i = 0; i < num_of_formulas; i++) {
            formula_ranking_str += formula_ranking[i] + "\n";
        }

        System.out.println(global);


        long finalTOTALTime = System.currentTimeMillis();
        long totalTime = finalTOTALTime - initialTOTALTime;
        int min = (int) (totalTime) / 60000;
        int sec = (int) (totalTime - min * 60000) / 1000;
        String time = String.format("Time: %s m  %s s", min, sec);
        System.out.println(time);

        if (outname != null) {
            writeRanking(outname.replace(".out", "-global.out"), global, time);
            writeRanking(outname.replace(".out", "-ranking-by-formula.out"), formula_ranking_str, "");
            writeRanking(outname.replace(".out", "-ranking.out"), flatten_ranking_str, "");
        }
    }

    static void runPrefixesMC(List<Formula> formulas, List<String> vars, int bound, String outname, boolean re_counting) throws IOException, InterruptedException {
        long initialTOTALTime = System.currentTimeMillis();
        int num_of_formulas = formulas.size();
        BigInteger[] solutions = new BigInteger[num_of_formulas];
        int index = 0;
        System.out.println("Counting...");
        for (Formula f : formulas) {
            long initialTime = System.currentTimeMillis();
            System.out.println(index + " Formula: " + LabelledFormula.of(f, vars));
            BigInteger result = BigInteger.ZERO;
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
            if (outname != null) {
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

        String global = "";
        String flatten_ranking_str = "";
        int[] formula_ranking = new int[num_of_formulas];
        int pos = 0;
        for (BigInteger key : global_ranking.keySet()) {
            global += global_ranking.get(key) + "\n";
            for (Integer f_pos : global_ranking.get(key)) {
                formula_ranking[f_pos] = pos;
                flatten_ranking_str += f_pos + "\n";
            }
            pos++;
        }

        global += "\nRanking Levels: " + pos + "\n";
        String formula_ranking_str = "";
        for (int i = 0; i < num_of_formulas; i++) {
            formula_ranking_str += formula_ranking[i] + "\n";
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
            writeRanking(filename, global, time);
            String ranking_formula_filename = outname.replace(".out", (re_counting ? "re-" : "auto-") + "ranking-by-formula.out");
            writeRanking(ranking_formula_filename, formula_ranking_str, "");
            String ranking_filename = outname.replace(".out", (re_counting ? "re-" : "auto-") + "ranking.out");
            writeRanking(ranking_filename, flatten_ranking_str, "");
        }
    }


    static BigInteger countModelsExact(Formula formula, int vars, int bound) throws IOException, InterruptedException {

        PreciseLTLModelCounter counter = new PreciseLTLModelCounter();
        Settings.MC_BOUND = bound;

        BigInteger models = counter.count(formula, vars);
        if (models == null)
            return null;


//        List<BigInteger> result = new LinkedList<>();
//        for (int i = 0; i < bound - 1; i++) {
//            result.add(BigInteger.ZERO);
//        }
//        result.add(models);
        return models;
    }

    static BigInteger countExhaustivePrefixesRltl(Formula f, List<String> vars, int bound) throws IOException, InterruptedException {
        LabelledFormula form_lost = LabelledFormula.of(f, vars);
        CountRltlConv counter = new CountRltlConv();
        BigInteger result = counter.countPrefixes(form_lost, bound);
        return result;
    }

    static BigInteger countExhaustiveAutomataBasedPrefixes(Formula f, List<String> vars, int bound) throws IOException, InterruptedException {
        LabelledFormula form_lost = LabelledFormula.of(f, vars);
//        MatrixBigIntegerModelCounting counter = new MatrixBigIntegerModelCounting(form_lost,false);
        EmersonLeiAutomatonBasedModelCounting counter = new EmersonLeiAutomatonBasedModelCounting(form_lost);
        BigInteger result = counter.count(bound);

        return result;
    }

    private static void writeFile(String filename, BigInteger result, String time) throws IOException {
        File file = new File(filename);
        FileWriter fw = new FileWriter(file.getAbsoluteFile());
        BufferedWriter bw = new BufferedWriter(fw);
        bw.write("0 0\n");
//        for (int i = 0; i < result.size(); i++) {
            BigInteger sol = result;
//            bw.write(String.valueOf(i + 1));
            bw.write(" ");
            bw.write(sol.toString());
            bw.write("\n");
//            System.out.println((i+1) + " " + sol);
//        }
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
}
