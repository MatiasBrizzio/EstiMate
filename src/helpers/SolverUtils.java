package helpers;

import owl.ltl.Formula;
import owl.ltl.LabelledFormula;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SolverUtils {

    private static final Map<String, String> replacements = new HashMap<String, String>() {
        {
            put("&", "&&");
            put("|", "||");
        }
    };

    public static List<String> genAlphabet(int n) {
        List<String> alphabet = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            alphabet.add(Character.toString((char) ('a' + i)));
        }
        return alphabet;
    }

    public static String toLambConvSyntax(String LTLFormula) {
        return replaceLTLConstructions(LTLFormula);
    }

    /** Separates the operators, without which aalta reads "Xp1" as a variable. */
    public static String toSolverSyntax(Formula f) {
        return replaceLTLConstructions(f.toString().replaceAll("([A-Z])", " $1 "));
    }

    /**
     * Adapts a formula already in {@link #toSolverSyntax} form to BLACK, whose
     * boolean constants are capitalised: a lowercase {@code false} would be
     * read as a proposition named "false" and turn into a satisfiable atom.
     */
    public static String toBlackSyntax(String solverFormula) {
        return solverFormula.replaceAll("\\btrue\\b", "True").replaceAll("\\bfalse\\b", "False");
    }

    public static String toSolverSyntax(LabelledFormula f) {
        String LTLFormula = f.toString();

        for (String v : f.variables()) {
            LTLFormula = LTLFormula.replaceAll(v, v.toLowerCase());
        }

        return processLTLFormula(LTLFormula, false);
    }

    private static String processLTLFormula(String formula, boolean aalta_syntax) {
        String processedFormula = formula.replaceAll("!", aalta_syntax ? "~" : "!");
        processedFormula = processedFormula.replaceAll("([A-Z])", " $1 ");
        return replaceLTLConstructions(processedFormula);
    }

    private static String replaceLTLConstructions(String line) {
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            line = line.replace(entry.getKey(), entry.getValue());
        }
        return line;
    }

}
