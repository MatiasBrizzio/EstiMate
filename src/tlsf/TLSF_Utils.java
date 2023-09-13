package tlsf;

import owl.ltl.*;
import owl.ltl.parser.SpectraParser;
import owl.ltl.parser.TlsfParser;
import owl.ltl.spectra.Spectra;
import owl.ltl.tlsf.Tlsf;
import owl.ltl.tlsf.Tlsf.Semantics;
import solvers.SolverUtils;

import java.io.*;
import java.util.LinkedList;
import java.util.List;

public class TLSF_Utils {
    private static String getCommand() {
        String cmd;
        String currentOS = System.getProperty("os.name");
        if (currentOS.startsWith("Mac"))
            cmd = "./lib/syfco_macos";
        else
            cmd = "./lib/syfco";
        return cmd;
    }

    private static boolean hasSyfcoSintax(File spec) throws InterruptedException, IOException {
        String cmd = getCommand();
        cmd += " " + spec.getPath();
        Process pr = Runtime.getRuntime().exec(cmd);
        int res = pr.waitFor();
        return (res == 0);
    }

    private static boolean isBasic(File spec) throws IOException, InterruptedException {
        String cmd = getCommand();
        cmd += " -p " + spec.getPath();
        Process pr = Runtime.getRuntime().exec(cmd);

        if (pr.waitFor() == 1) return false;

        InputStream in = pr.getInputStream();
        InputStreamReader inread = new InputStreamReader(in);
        BufferedReader bufferedreader = new BufferedReader(inread);
        String aux;
        aux = bufferedreader.readLine();
        return aux.length() == 0;
    }

    public static Tlsf toBasicTLSF(File spec) throws IOException, InterruptedException {
//		System.out.println(hasSyfcoSintax(spec) + "and "+isBasic(spec) + "File: " + spec.getPath() );
        if (spec.getAbsolutePath().endsWith("spectra")) {
            Spectra spectra = SpectraParser.parse(new FileReader(spec));
            Tlsf tlsf = TLSF_Utils.fromSpectra(spectra);
            String tlsf_name = spec.getAbsolutePath().replace(".spectra", ".tlsf");
            FileWriter out = new FileWriter(tlsf_name);
            out.write(adaptTLSFSpec(tlsf));
            out.close();
            spec = new File(tlsf_name);
        }

        if (hasSyfcoSintax(spec)) {
            if (isBasic(spec))
                return TlsfParser.parse(new FileReader(spec));
        } else {
            String tlsfAdapted = adaptTLSFSpec(TlsfParser.parse(new FileReader(spec)));
            FileWriter fileWriter2 = new FileWriter(spec);
            fileWriter2.write(tlsfAdapted);
            fileWriter2.flush();
            fileWriter2.close();
        }

        String cmd = getCommand();
        String tlsfBasic = spec.getAbsolutePath().replace(".tlsf", "_basic.tlsf");
        cmd += " -o " + tlsfBasic + " -f basic -m pretty -s0 " + spec.getAbsolutePath();
        Process p = Runtime.getRuntime().exec(cmd);

        p.waitFor();

        String tlsf = adaptTLSFSpec(TlsfParser.parse(new FileReader(new File(tlsfBasic))));
        return TlsfParser.parse(tlsf);
    }

    public static String adaptTLSFSpec(Tlsf spec) {
        StringBuilder new_tlsf_spec = new StringBuilder("INFO {\n"
                + "  TITLE:       " + spec.title() + "\n"
                + "  DESCRIPTION: " + spec.description() + "\n");
        if (spec.semantics().equals(Semantics.MEALY))
            new_tlsf_spec.append("  SEMANTICS:   Mealy\n");
        else if (spec.semantics().equals(Semantics.MEALY_STRICT))
            new_tlsf_spec.append("  SEMANTICS:   Mealy,Strict\n");
        else if (spec.semantics().equals(Semantics.MOORE))
            new_tlsf_spec.append("  SEMANTICS:   Moore\n");
        else
            new_tlsf_spec.append("  SEMANTICS:   Moore,Strict\n");

        if (spec.target().equals(Semantics.MEALY))
            new_tlsf_spec.append("  TARGET:   Mealy\n");
        else if (spec.target().equals(Semantics.MEALY_STRICT))
            new_tlsf_spec.append("  TARGET:   Mealy,Strict\n");
        else if (spec.target().equals(Semantics.MOORE))
            new_tlsf_spec.append("  TARGET:   Moore\n");
        else
            new_tlsf_spec.append("  TARGET:   Moore,Strict\n");

        new_tlsf_spec.append("}\n" + '\n' + "MAIN {\n" + "  INPUTS {\n" + "    ");
        int i = 0;
        while (spec.inputs().get(i)) {
            new_tlsf_spec.append(spec.variables().get(i)).append(";");
            i++;
        }
        new_tlsf_spec.append("\n" + "  }\n" + "  OUTPUTS {\n" + "    ");
        while (spec.outputs().get(i)) {
            new_tlsf_spec.append(spec.variables().get(i)).append(";");
            i++;
        }
        new_tlsf_spec.append("\n" + "  }\n" + '\n');
        // set new initially
        if (spec.initially().compareTo(BooleanConstant.TRUE) != 0) {
            new_tlsf_spec.append("  INITIALLY {\n" + "    ").append(SolverUtils.toSolverSyntax((LabelledFormula.of(spec.initially(), spec.variables())))).append(";\n").append("  }\n").append('\n');
        }

        if (spec.preset().compareTo(BooleanConstant.TRUE) != 0) {
            new_tlsf_spec.append("  PRESET {\n" + "    ").append(SolverUtils.toSolverSyntax((LabelledFormula.of(spec.preset(), spec.variables())))).append(";\n").append("  }\n").append('\n');
        }
        if (spec.require().compareTo(BooleanConstant.TRUE) != 0) {
            new_tlsf_spec.append("  REQUIRE {\n" + "    ").append(SolverUtils.toSolverSyntax((LabelledFormula.of(spec.require(), spec.variables())))).append(";\n").append("  }\n").append('\n');
        }

        if (!spec.assert_().isEmpty()) {
            new_tlsf_spec.append("  ASSERT {\n");
            for (Formula f : spec.assert_()) {
                new_tlsf_spec.append("    ").append(SolverUtils.toSolverSyntax((LabelledFormula.of(f, spec.variables())))).append(";\n");
            }
            new_tlsf_spec.append("  }\n" + '\n');
        }

        if (spec.assume().compareTo(BooleanConstant.TRUE) != 0) {
            new_tlsf_spec.append("  ASSUMPTIONS {\n");
            for (Formula as : Formula_Utils.splitConjunction(spec.assume())) {
                new_tlsf_spec.append("    ").append(SolverUtils.toSolverSyntax(LabelledFormula.of(as, spec.variables()))).append(";\n");
            }
            new_tlsf_spec.append("  }\n" + '\n');
        }

        if (!spec.guarantee().isEmpty()) {
            new_tlsf_spec.append("  GUARANTEES {\n");

            for (Formula f : spec.guarantee()) {
                new_tlsf_spec.append("    ").append(SolverUtils.toSolverSyntax(((LabelledFormula.of(f, spec.variables()))))).append(";\n");
            }
            new_tlsf_spec.append("  }\n");
        }
        new_tlsf_spec.append('}');

        for (String v : spec.variables())
            new_tlsf_spec = new StringBuilder(new_tlsf_spec.toString().replaceAll(v, v.toLowerCase()));

        return (new_tlsf_spec.toString());
    }

    public static Tlsf fromSpectra(Spectra spec) {
        List<Formula> additionalAssumptions = new LinkedList<Formula>();
        List<Formula> additionalGuarantee = new LinkedList<Formula>();
        StringBuilder new_tlsf_spec = new StringBuilder("INFO {\n"
                + "  TITLE:       " + "\"" + spec.title() + "\"" + "\n"
                + "  DESCRIPTION: " + "\"" + "empty description" + "\"" + "\n");
        new_tlsf_spec.append("  SEMANTICS:   Mealy,Strict\n");
        new_tlsf_spec.append("  TARGET:   Mealy\n");
        new_tlsf_spec.append("}\n" + '\n' + "MAIN {\n" + "  INPUTS {\n" + "    ");
        int i = 0;
        while (spec.inputs().get(i)) {
            new_tlsf_spec.append(spec.variables().get(i)).append(";");
            i++;
        }
        new_tlsf_spec.append("\n" + "  }\n" + "  OUTPUTS {\n" + "    ");
        while (spec.outputs().get(i)) {
            new_tlsf_spec.append(spec.variables().get(i)).append(";");
            i++;
        }
        new_tlsf_spec.append("\n" + "  }\n" + '\n');
        //init

        List<Formula> lst = new LinkedList<Formula>();
        if (!spec.thetaE().isEmpty()) {
            for (Formula f : spec.thetaE()) {
                if (hasGFPattern(f))
                    additionalAssumptions.add(f);
                else if (Formula_Utils.numOfTemporalOperators(f) > 0)
                    additionalAssumptions.add(f);
                else
                    lst.add(f);
            }
            new_tlsf_spec.append("  INITIALLY {\n" + "    ").append(LabelledFormula.of(Conjunction.of(lst), spec.variables())).append(";\n").append("  }\n").append('\n');
        }
        if (!spec.thetaS().isEmpty()) {
            lst.clear();
            for (Formula f : spec.thetaS()) {
                if (hasGFPattern(f))
                    additionalGuarantee.add(f);
                else if (Formula_Utils.numOfTemporalOperators(f) > 0)
                    additionalGuarantee.add(f);
                else
                    lst.add(f);
            }
            new_tlsf_spec.append("  PRESET {\n" + "    ").append(LabelledFormula.of(Conjunction.of(lst), spec.variables())).append(";\n").append("  }\n").append('\n');
        }
        if (!spec.psiE().isEmpty()) {
            new_tlsf_spec.append("  REQUIRE {\n" + "    ");
            for (Formula f : spec.psiE()) {
                new_tlsf_spec.append("    ").append(LabelledFormula.of(GOperator.of(f), spec.variables())).append(";\n");
            }
            new_tlsf_spec.append("  }\n" + '\n');
        }

        if (!spec.psiS().isEmpty()) {
            new_tlsf_spec.append("  ASSERT {\n" + "    ");
            for (Formula f : spec.psiS()) {
                new_tlsf_spec.append("    ").append(LabelledFormula.of(GOperator.of(f), spec.variables())).append(";\n");
            }
            new_tlsf_spec.append("  }\n" + '\n');
        }

        additionalAssumptions.addAll(spec.phiE());
        if (!additionalAssumptions.isEmpty()) {
            new_tlsf_spec.append("  ASSUMPTIONS {\n");
            for (Formula a : additionalAssumptions) {
                if (Formula_Utils.numOfTemporalOperators(a) > 0)
                    new_tlsf_spec.append("    ").append(LabelledFormula.of(a, spec.variables())).append(";\n");
                else
                    new_tlsf_spec.append("    ").append(LabelledFormula.of(GOperator.of(FOperator.of(a)), spec.variables())).append(";\n");
            }
            new_tlsf_spec.append("  }\n" + '\n');
        }
        additionalGuarantee.addAll(spec.phiS());
        if (!additionalGuarantee.isEmpty()) {
            new_tlsf_spec.append("  GUARANTEES {\n");

            for (Formula f : additionalGuarantee) {
                if (Formula_Utils.numOfTemporalOperators(f) > 0)
                    new_tlsf_spec.append("    ").append(LabelledFormula.of(f, spec.variables())).append(";\n");
                else
                    new_tlsf_spec.append("    ").append(LabelledFormula.of(GOperator.of(FOperator.of(f)), spec.variables())).append(";\n");
            }
            new_tlsf_spec.append("  }\n");
        } else {
            new_tlsf_spec.append("  GUARANTEES {\n    true; \n}\n");
        }

        new_tlsf_spec.append('}');

        return TlsfParser.parse(new_tlsf_spec.toString());
    }


    public static boolean hasGFPattern(Formula source) {
        if (source instanceof GOperator) {
            var child = ((GOperator) source).operand;
            if (child instanceof FOperator) {
                return true;
            } else if (child instanceof Conjunction || child instanceof Disjunction) {
                for (Formula c : child.children())
                    if (!(c instanceof FOperator))
                        continue;
                return true;
            }
        }
        return false;
    }

}
