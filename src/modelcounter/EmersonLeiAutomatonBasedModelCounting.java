package modelcounter;


import it.unimi.dsi.fastutil.ints.IntArrayList;
import jhoafparser.ast.AtomAcceptance;
import jhoafparser.ast.BooleanExpression;
import helpers.FormulaUtils;
import main.Settings;
import org.apache.commons.math3.fraction.BigFraction;
import org.apache.commons.math3.linear.Array2DRowFieldMatrix;
import org.apache.commons.math3.linear.FieldMatrix;
import owl.automaton.Automaton;
import owl.automaton.acceptance.EmersonLeiAcceptance;
import owl.automaton.edge.Edge;
import owl.ltl.BooleanConstant;
import owl.ltl.Formula;
import owl.ltl.LabelledFormula;
import owl.ltl.parser.LtlParser;
import owl.ltl.rewriter.SyntacticSimplifier;
import owl.ltl.visitors.SolverSyntaxOperatorReplacer;
import owl.run.DefaultEnvironment;
import owl.translations.delag.DelagBuilder;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;

public class EmersonLeiAutomatonBasedModelCounting<S> {
    private final LabelledFormula formula;
    long transitions = 0;
    private FieldMatrix<BigFraction> T = null;
    private Automaton<S, EmersonLeiAcceptance> automaton = null;
    private ArrayList<S> states = new ArrayList<>();

    public EmersonLeiAutomatonBasedModelCounting(LabelledFormula formula) {
        List<String> vars = new HashSet<>(FormulaUtils.extractAtoms(formula.toString())).stream().toList();
        SolverSyntaxOperatorReplacer visitor = new SolverSyntaxOperatorReplacer();
        SyntacticSimplifier simplifier = new SyntacticSimplifier();
        Formula simplified = formula.formula().accept(visitor).accept(simplifier);
        LabelledFormula simplifiedFormula = LabelledFormula.of(simplified, vars);

        this.formula = LtlParser.parse(simplifiedFormula.toString(), formula.variables());

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        // Do the call in a separate thread, get a Future back
        Future<String> future = executorService.submit(this::parse);
        try {
            // Wait for at most TIMEOUT seconds until the result is returned
            future.get(Settings.PARSING_TIMEOUT, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            System.out.println("EmersonLeiAutomatonBasedModelCounting: TIMEOUT parsing.");
        } catch (InterruptedException | ExecutionException e) {
            System.err.println("EmersonLeiAutomatonBasedModelCounting: ERROR while parsing. " + e.getMessage());
        }
    }

    private String parse() {
        // Convert the ltl formula to an automaton with OWL
        DelagBuilder translator = new DelagBuilder(DefaultEnvironment.standard());
        automaton = (Automaton<S, EmersonLeiAcceptance>) translator.apply(formula);
        var aux = automaton.states().stream().toList();
        IntStream.range(0, aux.size()).forEach(i -> states.add(i, aux.get(i)));
        return "OK";
    }

    public BigInteger count(int bound) {
        // UNSAT formulas have no models.
        if (this.formula.formula().equals(BooleanConstant.FALSE)) return BigInteger.ZERO;

        //We compute uTkv, where u is the row vector such that ui = 1 if and only if it is the start state and 0 otherwise,
        // and v is the column vector where vi = 1 if and only if it is an accepting state and 0 otherwise.
        if (states == null) return null;

        Settings.MC_BOUND = bound;
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        // Do the call in a separate thread, get a Future back
        Future<BigInteger> future = executorService.submit(this::countModels);
        try {
            // Wait for at most TIMEOUT seconds until the result is returned
            return future.get(Settings.MC_TIMEOUT, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            System.out.println("EmersonLeiAutomatonBasedModelCounting::count TIMEOUT.");
        } catch (InterruptedException | ExecutionException e) {
            System.err.println("EmersonLeiAutomatonBasedModelCounting::count ERROR. " + e.getMessage());
        }
        return BigInteger.ZERO;
    }

    private BigInteger countModels() {
        T = buildTransferMatrix();
        //set initial states
        FieldMatrix<BigFraction> u = buildInitialStates();

        //set final states
        FieldMatrix<BigFraction> v = buildFinalStates();

        // count models
        FieldMatrix<BigFraction> T_res = T.power(Settings.MC_BOUND);
        FieldMatrix<BigFraction> reachable = u.multiply(T_res);
        FieldMatrix<BigFraction> result = reachable.multiply(v);
        BigFraction value = result.getEntry(0, 0);
        return value.getNumerator();
    }

    public FieldMatrix<BigFraction> buildTransferMatrix() {
        int n = automaton.size();
        BigFraction[][] pData = new BigFraction[n][n];
        for (int i = 0; i < n; i++) {
            S si = states.get(i);
            for (int j = 0; j < n; j++) {
                S sj = states.get(j);
                transitions = 0;
                automaton.factory().universe().forEach(valuation -> {
                    Set<Edge<S>> edges = automaton.edges(si, valuation);
                    for (Edge<S> edge : edges) {
                        if (edge.successor().equals(sj))
                            transitions++;
                    }
                });
                BigFraction v = new BigFraction(transitions);
                pData[i][j] = v;
            }
        }
        return new Array2DRowFieldMatrix<>(pData, false);
    }

    public FieldMatrix<BigFraction> buildInitialStates() {
        int n = T.getRowDimension();
        //set initial states
        FieldMatrix<BigFraction> u = createMatrix(1, n);
        Set<S> initial_states = automaton.initialStates();
        for (int j = 0; j < n; j++) {
            if (initial_states.contains(states.get(j)))
                u.addToEntry(0, j, new BigFraction(1));
        }
        return u;
    }

    public FieldMatrix<BigFraction> buildFinalStates() {
        int n = T.getRowDimension();
        //set final states
        Set<S> final_states = new HashSet<>();
        for (S s : automaton.states()) {
            Set<Edge<S>> edges = automaton.edges(s);
            for (Edge<S> edge : edges) {
                //check if it is an acceptance transition
                IntArrayList acceptanceSets = new IntArrayList();
                if (edge.acceptanceSetIterator().hasNext())
                    edge.acceptanceSetIterator().forEachRemaining((IntConsumer) acceptanceSets::add);
                if (accConditionIsSatisfied(automaton.acceptance().booleanExpression(), acceptanceSets)) {
                    final_states.add(edge.successor());
                }
            }
        }

        FieldMatrix<BigFraction> v = createMatrix(n, 1);
        for (int i = 0; i < n; i++) {
            if (final_states.contains(states.get(i))) {
                v.addToEntry(i, 0, new BigFraction(1));
            }
        }
        return v;
    }

    public FieldMatrix<BigFraction> createMatrix(int row, int column) {
        BigFraction[][] pData = new BigFraction[row][column];
        for (int i = 0; i < row; i++) {
            for (int j = 0; j < column; j++) {
                pData[i][j] = new BigFraction(0);
            }
        }
        return new Array2DRowFieldMatrix<>(pData, false);
    }

    public boolean accConditionIsSatisfied(BooleanExpression<AtomAcceptance> acceptanceCondition, IntArrayList acceptanceSets) {
        boolean accConditionSatisfied = false;
        switch (acceptanceCondition.getType()) {
            case EXP_TRUE: {
                accConditionSatisfied = true;
                break;
            }
            case EXP_FALSE:
                break;
            case EXP_ATOM: {
                if (acceptanceCondition.getAtom().getType() == AtomAcceptance.Type.TEMPORAL_INF)
                    accConditionSatisfied = (acceptanceSets.contains(acceptanceCondition.getAtom().getAcceptanceSet()));
                else if (acceptanceCondition.getAtom().getType() == AtomAcceptance.Type.TEMPORAL_FIN) {
                    accConditionSatisfied = !(acceptanceSets.contains(acceptanceCondition.getAtom().getAcceptanceSet()));
                }
                break;
            }
            case EXP_AND: {
                if (accConditionIsSatisfied(acceptanceCondition.getLeft(), acceptanceSets))
                    accConditionSatisfied = accConditionIsSatisfied(acceptanceCondition.getRight(), acceptanceSets);
                break;
            }
            case EXP_OR: {
                if (accConditionIsSatisfied(acceptanceCondition.getLeft(), acceptanceSets))
                    accConditionSatisfied = true;
                else
                    accConditionSatisfied = accConditionIsSatisfied(acceptanceCondition.getRight(), acceptanceSets);
                break;
            }
            case EXP_NOT: {
                accConditionSatisfied = !accConditionIsSatisfied(acceptanceCondition.getRight(), acceptanceSets);
                break;
            }
        }

        return accConditionSatisfied;
    }


}	

