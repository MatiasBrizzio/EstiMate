package modelcounter.estimate;

import gov.nasa.ltl.graph.Edge;
import gov.nasa.ltl.graph.Graph;
import gov.nasa.ltl.graph.Node;
import gov.nasa.ltl.graphio.Writer;
import helpers.Buchi2Graph;
import main.Settings;
import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.CommonOps_DDRM;
import owl.ltl.LabelledFormula;

import java.io.IOException;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.Set;

public class AutomataBasedModelCounting {
    private DMatrixRMaj T;
    private DMatrixRMaj I;
    private Graph<String> nba;
    private boolean exhaustive;

    public AutomataBasedModelCounting(LabelledFormula formula, boolean exhaustive) throws IOException, InterruptedException {

        this.exhaustive = exhaustive;
        Writer<String> w = Writer.getWriter(Writer.Format.FSP, System.out);
        // Convert the ltl formula to an automaton with OWL
        nba = Buchi2Graph.LTL2Graph(formula);
        if (exhaustive) {
            //We first apply a transformation and add an extra state, sn+1. The resulting
            //automaton is a DFA A0 with λ-transitions from each of the accepting states of A
            //to sn+1 where λ is a new padding symbol that is not in the alphabet of A.

            Node<String> sn1 = new Node<>(nba);
            for (Node<String> node : nba.getNodes()) {
                if (node.getBooleanAttribute("accepting")) {
                    node.setBooleanAttribute("accepting", false);
                    Edge<String> nToSn1 = new Edge<>(node, sn1);
                    node.getOutgoingEdges().add(nToSn1);
                    sn1.getIncomingEdges().add(nToSn1);
                }
            }
            sn1.setBooleanAttribute("accepting", true);
            Edge<String> sn1ToSn1 = new Edge<>(sn1, sn1);
            sn1.getOutgoingEdges().add(sn1ToSn1);
            sn1.getIncomingEdges().add(sn1ToSn1);
        }
        //From A0 we construct the (n + 1) × (n + 1) transfer matrix T. A0 has n + 1
        //states s1, s2, . . . sn+1. The matrix entry Ti,j is the number of transitions from
        //state si to state sj
        T = buildTransferMatrix(nba);
        int n = nba.getNodeCount();
        I = CommonOps_DDRM.identity(n);

    }

    /**
     * λ-closure of the given node with respect to the nfa
     */
    private static Set<Integer> closure(Node<String> node, Set<Integer> visitedNodes) {
        Set<Integer> closureSet = new HashSet<Integer>();
        closureSet.add(node.getId());
        for (Edge<String> edge : node.getOutgoingEdges()) {
            if (edge.getGuard().isTrue()) {
                if (visitedNodes.add(edge.getNext().getId())) {
                    closureSet.addAll(closure(edge.getNext(), visitedNodes));
                }
            }
        }
        return closureSet;
    }

    public BigInteger count(int k) {
        //We compute uTkv, where u is the row vector such that ui = 1 if and only if i is the start state and 0 otherwise,
        // and v is the column vector where vi = 1 if and only if i is an accepting state and 0 otherwise.
        if (nba == null || nba.getNodeCount() == 0)
            return BigInteger.ZERO;
        int n = T.numRows;
        DMatrixRMaj u = new DMatrixRMaj(1, n);
        //TODO: Assume that the initial state is in first position
        u.set(0, nba.getInit().getId(), 1);
        DMatrixRMaj v = new DMatrixRMaj(n, 1);
        for (Node<String> node : nba.getNodes()) {
            if (node.getBooleanAttribute("accepting"))
                v.set(node.getId(), 0, 1);
        }
        DMatrixRMaj T_res = T.copy();
        int bound = exhaustive ? k + 1 : k;
        for (int i = 1; i < bound; i++) {
            long initialTime = System.currentTimeMillis();
            DMatrixRMaj T_i = T.copy();
            DMatrixRMaj T_aux = T_res.copy();
            CommonOps_DDRM.mult(T_aux, T_i, T_res);
            //check for timeout
            long currentTime = System.currentTimeMillis();
            long totalTime = currentTime - initialTime;
            int min = (int) (totalTime) / 60000;
            int sec = (int) (totalTime - min * 60000) / 1000;
            if (sec > Settings.MC_TIMEOUT) {
                System.out.print("TO ");
                return BigInteger.ZERO;
            }
        }
        DMatrixRMaj reachable = new DMatrixRMaj(1, n);
        CommonOps_DDRM.mult(u, T_res, reachable);
        DMatrixRMaj result = new DMatrixRMaj(1, 1);
        CommonOps_DDRM.mult(reachable, v, result);
        long value = (long) result.get(0, 0);
        BigInteger count = BigInteger.valueOf(value);
        return count;
    }


    /**
     * Build the Transfer Matrix for the given DFA
     *
     * @param nba is the DFA
     * @return a n x n matrix M where M[i,j] is the number of transitions from state si to state sj
     */
    public DMatrixRMaj buildTransferMatrix(Graph<String> nba) {
        int n = nba.getNodeCount();
        DMatrixRMaj M = new DMatrixRMaj(n, n);
        for (int i = 0; i < n; i++) {
            Node<String> si = nba.getNodes().get(i);
            for (int j = 0; j < n; j++) {
                Node<String> sj = nba.getNodes().get(j);
                long transitions = 0;
                for (Edge<String> edge : si.getOutgoingEdges()) {
                    if (edge.getNext().getId() == sj.getId())
                        transitions++;
                }
                M.add(i, j, transitions);
            }
        }
        return M;
    }

}	

