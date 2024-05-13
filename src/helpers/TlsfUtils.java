package helpers;

import owl.ltl.*;
import owl.ltl.parser.TlsfParser;
import owl.ltl.tlsf.Tlsf;
import owl.ltl.tlsf.Tlsf.Semantics;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class TlsfUtils {
    private static String getCommand() {
        String osName = System.getProperty("os.name").toLowerCase();
        return osName.contains("mac") ? "syfco" : "./lib/syfco";
    }

    private static int getExitCode(String cmd) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(cmd.split("\\s+"));
        processBuilder.redirectErrorStream(true);
        Process p = processBuilder.start();
        return p.waitFor();
    }

    private static boolean hasSyfcoSyntax(File spec) throws InterruptedException, IOException {
        String cmd = getCommand() + " " + spec.getPath();
        ProcessBuilder processBuilder = new ProcessBuilder(cmd.split("\\s+"));
        processBuilder.redirectErrorStream(true);
        Process pr = processBuilder.start();
        int exitCode = pr.waitFor();
        return exitCode == 0;
    }

    private static boolean isBasic(File spec) throws IOException, InterruptedException {
        List<String> cmdList = new ArrayList<>();
        cmdList.add(getCommand());
        cmdList.add("-p");
        cmdList.add(spec.getPath());

        ProcessBuilder processBuilder = new ProcessBuilder(cmdList);
        Process pr = processBuilder.start();

        if (pr.waitFor() == 1) {
            return false;
        }

        try (InputStream in = pr.getInputStream();
             InputStreamReader inread = new InputStreamReader(in);
             BufferedReader bufferedreader = new BufferedReader(inread)) {
            String aux = bufferedreader.readLine();
            return aux != null && aux.isEmpty();
        }
    }

    public static Tlsf toBasicTLSF(File spec) throws IOException, InterruptedException {
        if (hasSyfcoSyntax(spec)) {
            if (isBasic(spec)) {
                return TlsfParser.parse(new FileReader(spec));
            }
        } else {
            String tlsfAdapted = adaptTLSFSpec(TlsfParser.parse(new FileReader(spec)));
            try (FileWriter fileWriter2 = new FileWriter(spec)) {
                fileWriter2.write(tlsfAdapted);
            }
        }

        String cmd = getCommand();
        String tlsfBasic = spec.getAbsolutePath().replace(".tlsf", "_basic.tlsf");
        cmd += " -o " + tlsfBasic + " -f basic -m pretty -s0 " + spec.getAbsolutePath();

        int exitCode = getExitCode(cmd);

        if (exitCode != 0) {
            // Handle the case where the external process fails
            throw new IOException("External process failed with exit code: " + exitCode);
        }

        String tlsf = adaptTLSFSpec(TlsfParser.parse(new FileReader(tlsfBasic)));
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
            for (Formula as : FormulaUtils.splitConjunction(spec.assume())) {
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

}
