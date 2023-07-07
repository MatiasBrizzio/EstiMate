package solvers;

import owl.ltl.LabelledFormula;

import java.util.*;

public class SolverUtils {
    static Map<String, String> replacements = new HashMap<String, String>() {
        {
            put("&", "&&");
            put("|", "||");
            put("!", " ! ");
        }
    };

    public static String toLambConvSyntax(String LTLFormula) {
//        LTLFormula = LTLFormula.replaceAll("\\!", " ! ");
//        LTLFormula = LTLFormula.replaceAll("&", "&&");
//        LTLFormula = LTLFormula.replaceAll("\\|", "||");
        return replaceLTLConstructions(LTLFormula);
    }

    public static List<String> genAlphabet(int n) {
        List<String> alphabet = new LinkedList();
        for (int i = 0; i < n; i++) {
            String v = String.valueOf(Character.toChars(97 + i)[0]);
            alphabet.add(v);
        }
        return alphabet;
    }

    public static String toSolverSyntax(LabelledFormula f) {
        String LTLFormula = f.toString();
        for (String v : f.variables())
            LTLFormula = LTLFormula.replaceAll(v, v.toLowerCase());
        LTLFormula = LTLFormula.replaceAll("([A-Z])", " $1 ");
        return replaceLTLConstructions(LTLFormula);
    }

    private static String replaceLTLConstructions(String line) {
        Set<String> keys = replacements.keySet();
        for (String key : keys)
            line = line.replace(key, replacements.get(key));
        return line;
    }
}
