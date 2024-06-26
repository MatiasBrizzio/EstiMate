package modelcounter.re;

import helpers.SolverUtils;
import helpers.TranslatorLTL2RE;
import main.Settings;
import owl.ltl.LabelledFormula;

import java.io.*;
import java.math.BigInteger;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class CountREModels {
    private TranslatorLTL2RE translator;

    public CountREModels() {
        translator = new TranslatorLTL2RE();
    }

    public BigInteger countPrefixes(LabelledFormula formula, int bound) throws IOException, InterruptedException {
        String ltlStr = genRltlString(formula);
        return runCount(ltlStr, bound);
    }

    public String genRltlString(LabelledFormula formula) {
        List<String> alphabet = SolverUtils.genAlphabet(formula.variables().size());
        LabelledFormula label_formula = LabelledFormula.of(formula.formula(), alphabet);
        String ltl = SolverUtils.toLambConvSyntax(label_formula.toString());
        String alph = alphabet.toString();
        String form = "LTL=" + ltl;
        if (alph != null && !alph.isEmpty())
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
            throw new RuntimeException("TIMEOUT reached in CountREModels.");

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
        return new BigInteger(out);
    }


    public BigInteger count(java.util.List<LabelledFormula> formulas, int bound, boolean exhaustive, boolean positive) throws IOException, InterruptedException {
        ABC abc = new ABC();
        LinkedList<String> abcStrs = new LinkedList<>();
        for (LabelledFormula f : formulas) {
            String abcStr = translator.genABCString(f);
            if (abcStr != null)
                abcStrs.add(abcStr);
        }
        BigInteger count;
        if (translator.encoded_alphabet == 0)
            count = abc.count(abcStrs, bound * 2, exhaustive, positive);//each state is characterised by 2 characters
        else if (translator.encoded_alphabet == 1)
            count = abc.count(abcStrs, bound * 3, exhaustive, positive);//each state is characterised by 3 characters
        else
            count = abc.count(abcStrs, bound, exhaustive, positive);
        if (!exhaustive)
            return count;
        else {
            return count.divide(BigInteger.valueOf(bound));
        }
    }

}
