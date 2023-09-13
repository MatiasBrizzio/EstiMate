package modelcounter;

import org.junit.jupiter.api.Test;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.Formula;
import owl.ltl.parser.LtlParser;
import solvers.LTLSolver;

import java.io.IOException;
import java.util.List;

public class CoreTest {
    @Test
    public void testSimple1() throws IOException, InterruptedException {
        List<String> vars = List.of("p", "e0", "e1", "h1", "h0");
        Formula assumption = LtlParser.parse("G(p) || G (!p)", vars).formula();
        Formula g1 = LtlParser.parse("G(!e0 || !e1)", vars).formula();
        Formula g2 = LtlParser.parse("G(F(!h0 || e0))", vars).formula();
        Formula g3 = LtlParser.parse("G(F(!h1 || e1))", vars).formula();
        Formula g4 = LtlParser.parse("G(p) -> G (!e0 && !e1)", vars).formula();
        Formula spec = Disjunction.of(assumption.not(), Conjunction.of(g1, g2, g3, g4));
        System.out.println(LTLSolver.isSAT(spec.toString()));
    }


}
