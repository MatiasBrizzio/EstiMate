package main;

import owl.ltl.*;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FormulaUtils {

    public static List<LabelledFormula> subformulas(LabelledFormula f) {//, List<String> variables) {
        List<LabelledFormula> s = new LinkedList();

        for (Formula c : f.formula().children()) {
            LabelledFormula sf = LabelledFormula.of(c, f.variables());
            s.addAll(subformulas(sf));
        }
        s.add(LabelledFormula.of(f.formula(), f.variables()));
        return s;
    }

    public static Set<Formula> subformulas(Formula f) {//, List<String> variables) {
        Set<Formula> s = new HashSet<>();

        for (Formula c : f.children()) {
            s.addAll(subformulas(c));
        }
        s.add(f);
        return s;
    }

    public static int formulaSize(Formula f) {//, List<String> variables) {
        int size = 1;
        for (Formula c : f.children())
            size += formulaSize(c);
        return size;
    }

    public static List<Formula> splitConjunction(Formula f) {
        List<Formula> conjuncts = new LinkedList<>();
        if (f instanceof Conjunction) {
            Conjunction conjunction = (Conjunction) f;
            for (Formula c : conjunction.children)
                if (c != BooleanConstant.TRUE)
                    conjuncts.addAll(splitConjunction(c));
        } else if (f != BooleanConstant.TRUE)
            conjuncts.add(f);
        return conjuncts;
    }

    public static int numOfTemporalOperators(Formula formula) {
        if (formula == null || formula instanceof Literal)
            return 0;
        if (formula instanceof Formula.TemporalOperator && !(formula instanceof XOperator)) {
            int max = 0;
            for (Formula c : formula.children()) {
                int aux = numOfTemporalOperators(c);
                if (max < aux)
                    max = aux;
            }
            return max + 1;
        }
        if (formula instanceof Formula.LogicalOperator) {
            int max = 0;
            for (Formula c : formula.children()) {
                int aux = numOfTemporalOperators(c);
                if (max < aux)
                    max = aux;
            }
            return max;
        }
        return 0;
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
