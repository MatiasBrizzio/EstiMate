package modelcounter;

import vlab.cs.ucsb.edu.DriverProxy;
import vlab.cs.ucsb.edu.DriverProxy.Option;

import java.math.BigInteger;
import java.util.LinkedList;


public class ABC {

    public DriverProxy abcDriver = null;
    public boolean result = false;

    public ABC() {
        result = false;
        abcDriver = new DriverProxy();
    }

    public BigInteger count(LinkedList<String> formulas, int bound, boolean exhaustive, boolean positive) {
        abcDriver.setOption(Option.REGEX_FLAG, Option.REGEX_FLAG_ANYSTRING);

        String constraint = "(set-logic QF_S)\n"
                + "(declare-fun x () String)\n";

        for (String f : formulas) {
            if (positive)
                constraint += "(assert (in x /" + f + "/))\n";
            else
                constraint += "(assert (not (in x /" + f + "/)))\n";

        }
        constraint += "(assert (= (len x) " + bound + "))\n";
        constraint += "(check-sat)\n";
        result = abcDriver.isSatisfiable(constraint);
        BigInteger count = BigInteger.ZERO;

        if (result) {
//      System.out.println("Satisfiable");
            if (!exhaustive)
                count = abcDriver.countVariable("x", bound);
            else {
                for (long k = 1; k <= bound; k++) {
                    BigInteger r = abcDriver.countVariable("x", k);
                    count = count.add(r);
                }
            }
            abcDriver.dispose(); // release resources
        }
        return count;
    }
}


