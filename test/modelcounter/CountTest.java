package modelcounter;

import org.junit.jupiter.api.Test;
import owl.ltl.Conjunction;
import owl.ltl.Formula;
import owl.ltl.LabelledFormula;
import owl.ltl.parser.LtlParser;
import owl.ltl.parser.TlsfParser;
import owl.ltl.rewriter.NormalForms;
import owl.ltl.tlsf.Tlsf;

import java.io.FileReader;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;

class CountTest {

    @Test
    public void testSimple1() throws IOException, InterruptedException {
        List<String> vars = List.of("a", "b");
        LabelledFormula f0 = LtlParser.parse("G F (!b) -> G F(a)", vars);
//        List<LabelledFormula> list = new LinkedList();
//        list.add(f0);
        System.out.println(f0);
        for (int i = 0; i < 100; i++) {
            Count counter = new Count();
            BigInteger result = counter.count(f0, 5, false, true);
            System.out.println(result);
        }
    }

    @Test
    void test() throws IOException, InterruptedException {
        FileReader f = new FileReader("examples/minepump.tlsf");
        Tlsf spec = TlsfParser.parse(f);

        FileReader f2 = new FileReader("examples/minepump-2.tlsf");
        Tlsf spec2 = TlsfParser.parse(f2);
//		List<LabelledFormula> formulas = new LinkedList<>();
//		formulas.add(spec.toFormula());
//		LabelledFormula form = LtlParser.parse(spec.toFormula().toString() + " && " + spec2.toFormula().toString(), spec.variables());

        for (int i = 0; i < 10; i++) {
            Count counter = new Count();
            Formula cnf = NormalForms.toCnfFormula(Conjunction.of(spec.toFormula().formula(), spec2.toFormula().formula()));
            LabelledFormula form = LabelledFormula.of(cnf, spec.variables());

            System.out.println(form);
            BigInteger result = counter.count(form, 5, false, true);
            System.out.println(result);
        }
    }


}