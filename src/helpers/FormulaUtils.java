package helpers;

import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Formula;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FormulaUtils {

    public static List<Formula> splitConjunction(Formula f) {
        List<Formula> conjuncts = new LinkedList<>();
        if (f instanceof Conjunction conjunction) {
            for (Formula c : conjunction.children)
                if (c != BooleanConstant.TRUE)
                    conjuncts.addAll(splitConjunction(c));
        } else if (f != BooleanConstant.TRUE)
            conjuncts.add(f);
        return conjuncts;
    }

    public static Set<String> extractAtoms(String formula) {
        Set<String> atoms = new HashSet<>();
        Pattern pattern = Pattern.compile("[a-zA-Z_][a-zA-Z_0-9]*|\"[^\"]+\"");
        Matcher matcher = pattern.matcher(formula);

        while (matcher.find()) {
            String atom = matcher.group();
            if (!isKeyword(atom)) { // Check if it's not a keyword
                atoms.add(atom);
            }
        }

        return atoms;
    }

    public static boolean isKeyword(String token) {
        Set<String> keywords = Set.of("!", "NOT", "->", "=>", "IMP", "<->", "<=>",
                "BIIMP", "^", "XOR", "&&", "&", "AND", "||", "|", "OR", "(", ")", "F", "G", "X", "U", "W", "R", "M");
        return keywords.contains(token);
    }

}
