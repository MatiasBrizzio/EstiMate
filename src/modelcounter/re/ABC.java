package modelcounter.re;

import vlab.cs.ucsb.edu.DriverProxy;
import vlab.cs.ucsb.edu.DriverProxy.Option;

import java.math.BigInteger;
import java.util.LinkedList;


public class ABC {

    public DriverProxy abcDriver;
    public boolean result;

    public ABC() {
        result = false;
        abcDriver = new DriverProxy();
    }

    public BigInteger count(LinkedList<String> formulas, int bound, boolean exhaustive, boolean positive) {
        abcDriver.setOption(Option.REGEX_FLAG, Option.REGEX_FLAG_ANYSTRING);

        StringBuilder constraint = new StringBuilder("(set-logic QF_S)\n"
                + "(declare-fun x () String)\n");

        for (String f : formulas) {
            if (positive)
                constraint.append("(assert (in x /").append(f).append("/))\n");
            else
                constraint.append("(assert (not (in x /").append(f).append("/)))\n");

        }
        constraint.append("(assert (= (len x) ").append(bound).append("))\n");
        constraint.append("(check-sat)\n");
        result = abcDriver.isSatisfiable(constraint.toString());
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


