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
        Formula form;
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
            } else if (args[i].startsWith("-to=")) {
                String val = args[i].replace("-to=", "");
                Settings.MC_TIMEOUT = Integer.parseInt(val);
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
            System.out.println("Use ./modelcounter.sh [-formula=LTL-Formula | -b=pathToFile] [-to=timeout | -k=bound | -auto | -re | -vars=a,b,c | -no-precise]");
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
                solutions[index] = (BigInteger) result;
            } else {
                System.out.println("MC Timeout reached.");
//                timeout_formulas.add(index);
            }
            index++;
        }

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

    private static void writeRanking(String filename, SortedMap<BigInteger, List<Integer>> ranking) throws IOException {
        File file = new File(filename);
        FileWriter fw = new FileWriter(file.getAbsoluteFile());
        BufferedWriter bw = new BufferedWriter(fw);
        for (BigInteger key : ranking.keySet()) {
            for (Integer formulas : ranking.get(key)) {
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
}
