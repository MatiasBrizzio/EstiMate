package modelcounter;

import helpers.TlsfUtils;
import modelcounter.re.CountREModels;
import org.junit.jupiter.api.Test;
import owl.ltl.Conjunction;
import owl.ltl.Formula;
import owl.ltl.LabelledFormula;
import owl.ltl.parser.LtlParser;
import owl.ltl.parser.TlsfParser;
import owl.ltl.rewriter.NormalForms;
import owl.ltl.tlsf.Tlsf;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigInteger;
import java.util.LinkedList;
import java.util.List;

class CountREModelsTest {

    @Test
    public void testSimple1() throws IOException, InterruptedException {
        List<String> vars = List.of("a", "b");
        LabelledFormula f0 = LtlParser.parse("G F(a & (b))", vars);
        List<LabelledFormula> list = new LinkedList();
        list.add(f0);
        System.out.println(f0);
        CountREModels counter = new CountREModels();
        BigInteger c = counter.count(list, 5, false, true);
        System.out.println(c);
    }

    @Test
    public void testSimple2() throws IOException, InterruptedException {
        List<String> vars = List.of("a", "b");
        LabelledFormula f0 = LtlParser.parse("G(a -> X(b))", vars);
        List<LabelledFormula> list = new LinkedList();
        list.add(f0);
        System.out.println(f0);
        CountREModels counter = new CountREModels();
        BigInteger c = counter.count(list, 10, false, true);
        System.out.println(c);
    }

    @Test
    public void testMinepump() throws IOException, InterruptedException {
        String filename = "examples/minepump.tlsf";
        Tlsf tlsf = TlsfUtils.toBasicTLSF(new File(filename));
        List<String> vars = tlsf.variables();
        LabelledFormula f0 = tlsf.toFormula();
        List<LabelledFormula> list = new LinkedList();
        list.add(f0);
        System.out.println(f0);
        CountREModels counter = new CountREModels();
//		        String re = counter.genABCString(f0);
//		        System.out.println(re);
        BigInteger c = counter.count(list, 5, false, true);
        System.out.println(c);
    }

    @Test
    public void testSimple3() throws IOException, InterruptedException {
        List<String> vars = List.of("a", "b");
        LabelledFormula f0 = LtlParser.parse("G F (!b) -> G F(a)", vars);
//        List<LabelledFormula> list = new LinkedList();
//        list.add(f0);
        System.out.println(f0);
        for (int i = 0; i < 100; i++) {
            CountREModels counter = new CountREModels();
            BigInteger result = counter.countPrefixes(f0, 5);
            System.out.println(result);
        }
    }

    @Test
    void test() throws IOException, InterruptedException {
        FileReader f = new FileReader("examples/minepump.tlsf");
        Tlsf spec = TlsfParser.parse(f);

        FileReader f2 = new FileReader("examples/minepump-4.tlsf");
        Tlsf spec2 = TlsfParser.parse(f2);

        for (int i = 0; i < 10; i++) {
//			LabelledFormula form = LtlParser.parse(spec.toFormula().toString() + " && " + spec2.toFormula().toString(), spec.variables());
            CountREModels counter = new CountREModels();
            Formula cnf = NormalForms.toCnfFormula(Conjunction.of(spec2.toFormula().formula(), spec.toFormula().formula()));
            LabelledFormula form = LabelledFormula.of(cnf, spec.variables());

            System.out.println(form);

            BigInteger result = counter.countPrefixes(form, 5);
            System.out.println(result);
        }
    }
}
