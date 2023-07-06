package modelcounter;

import gov.nasa.ltl.trans.ParseErrorException;
import org.junit.Test;
import owl.ltl.LabelledFormula;
import owl.ltl.parser.LtlParser;
import owl.ltl.parser.TlsfParser;
import owl.ltl.tlsf.Tlsf;

import java.io.FileReader;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;

public class AutomataBasedMCTest {

    @Test
    public void test1() throws ParseErrorException, IOException, InterruptedException {
        List<String> vars = List.of("a", "b");
        LabelledFormula formula = LtlParser.parse("G(a & b)", vars);
        AutomataBasedModelCounting counter = new AutomataBasedModelCounting(formula, false);
        BigInteger d = counter.count(2);
        System.out.println(d);
    }

    @Test
    public void test2() throws ParseErrorException, IOException, InterruptedException {
        List<String> vars = List.of("a", "b");
        LabelledFormula formula = LtlParser.parse("G(a -> X(b))", vars);

        AutomataBasedModelCounting counter = new AutomataBasedModelCounting(formula, false);
        BigInteger d = counter.count(4);
        System.out.println(d);
    }


    @Test
    public void test3() throws ParseErrorException, IOException, InterruptedException {
        List<String> vars = List.of("a", "b");
        LabelledFormula formula = LtlParser.parse("F (a && b)", vars);
        AutomataBasedModelCounting counter = new AutomataBasedModelCounting(formula, false);
        BigInteger d = counter.count(3);
        System.out.println(d);
    }

    @Test
    public void testMinepump() throws ParseErrorException, IOException, InterruptedException {

        FileReader f = new FileReader("examples/minepump.tlsf");
        Tlsf spec = TlsfParser.parse(f);

        FileReader f2 = new FileReader("examples/minepump-3.tlsf");
        Tlsf spec2 = TlsfParser.parse(f2);
        LabelledFormula formula = LtlParser.parse(spec.toFormula().not() + " && " + spec2.toFormula().not(), spec.variables());
        System.out.println(formula);
        AutomataBasedModelCounting counter = new AutomataBasedModelCounting(formula, false);
        BigInteger d = counter.count(5);
        System.out.println(d);
    }

    @Test
    public void testMinePumpBrokenMC() throws ParseErrorException, IOException, InterruptedException {
        List<String> vars = List.of("methane", "high_water", "pump_on");
        LabelledFormula formula = LtlParser.parse("((F((methane & X(pump_on))) | F((high_water & X(!pump_on)))) & (F((methane & X(pump_on))) | F((!methane & high_water & X(!pump_on)))) & G((!high_water | !pump_on | X((!high_water | X(!high_water))))))", vars);
        AutomataBasedModelCounting counter = new AutomataBasedModelCounting(formula, false);
        BigInteger d = counter.count(5);
        System.out.println(d);
    }

    @Test
    public void testMinePumpBroken() throws ParseErrorException, IOException, InterruptedException {
        List<String> vars = List.of("methane", "high_water", "pump_on");
        LabelledFormula formula = LtlParser.parse("(((G((!methane | X(!pump_on))) & G((!high_water | X(pump_on)))) | F((high_water & pump_on & X((high_water & X(high_water)))))) & ((G((!methane | X(!pump_on))) & G((methane | !high_water | X(pump_on)))) | F((high_water & pump_on & X((high_water & X(high_water)))))))", vars);

        AutomataBasedModelCounting counter = new AutomataBasedModelCounting(formula, true);
        BigInteger d = counter.count(10);
        System.out.println(d);
    }
}