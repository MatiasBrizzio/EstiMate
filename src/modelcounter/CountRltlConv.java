package modelcounter;

import main.Settings;
import owl.ltl.LabelledFormula;
import solvers.SolverUtils;

import java.io.*;
import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class CountRltlConv {
    public BigInteger countPrefixes(LabelledFormula formula, int bound) throws IOException, InterruptedException {
        String ltlStr = genRltlString(formula);
        BigInteger result = runCount(ltlStr, bound);
        return result;
    }

    public String genRltlString(LabelledFormula formula) throws IOException, InterruptedException {
        List<String> alphabet = SolverUtils.genAlphabet(formula.variables().size());
        LabelledFormula label_formula = LabelledFormula.of(formula.formula(), alphabet);
        String ltl = SolverUtils.toLambConvSyntax(label_formula.toString());
        String alph = alphabet.toString();
        String form = "LTL=" + ltl;
        if (alph != null && !alph.equals(""))
            form += ",ALPHABET=" + alph;
        return form;
    }

    private BigInteger runCount(String ltl, int bound) throws IOException, InterruptedException {
        String[] cmd = {"./modelcount-prefixes.sh", ltl, String.valueOf(bound)};
        Process p = Runtime.getRuntime().exec(cmd);
        boolean timeout = false;
        if (!p.waitFor(Settings.MC_TIMEOUT, TimeUnit.SECONDS)) {
            timeout = true; //kill the process.
            p.destroy(); // consider using destroyForcibly instead
        }

        if (timeout)
            throw new RuntimeException("TIMEOUT reached in CountRltlConv.");

        InputStream in = p.getInputStream();
        InputStreamReader inread = new InputStreamReader(in);
        BufferedReader bufferedreader = new BufferedReader(inread);
        String aux;
        String out = "";
        while ((aux = bufferedreader.readLine()) != null) {
            out = aux;
        }

        bufferedreader.close();
        inread.close();
        in.close();

        OutputStream os = p.getOutputStream();
        if (os != null) os.close();
        p.destroy();
        BigInteger result = new BigInteger(out);
        return result;
    }
}
