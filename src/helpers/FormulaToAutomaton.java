package helpers;

import automata.fsa.FSAToRegularExpressionConverter;
import automata.fsa.FSATransition;
import automata.fsa.FiniteStateAutomaton;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import jhoafparser.ast.AtomAcceptance;
import jhoafparser.ast.AtomLabel;
import jhoafparser.ast.BooleanExpression;
import owl.automaton.Automaton;
import owl.automaton.acceptance.BooleanExpressions;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.edge.Edge;
import owl.collections.ValuationSet;
import owl.factories.FactorySupplier;
import owl.factories.ValuationSetFactory;
import owl.ltl.LabelledFormula;
import owl.run.DefaultEnvironment;
import owl.run.Environment;
import owl.translations.ltl2dpa.LTL2DPAFunction;

import javax.annotation.Nullable;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

import static com.google.common.base.Preconditions.checkState;

public class FormulaToAutomaton {

    //Map labels to ids
    public Map<String, String> labelIDs = new HashMap<>();
    public int encoded_alphabet;
    public int[] state = {48, 48};//start with char 0
    public int alphabetSize;
    int base;
    private Object2IntMap stateNumbers;

    public FormulaToAutomaton() {
        encoded_alphabet = -1;
        alphabetSize = 0;
    }

    public void generateLabels(List<String> variables) {
        if (!labelIDs.isEmpty())
            return;
        Environment env = DefaultEnvironment.standard();
        FactorySupplier factorySupplier = env.factorySupplier();
        ValuationSetFactory vsFactory = factorySupplier.getValuationSetFactory(variables);
        alphabetSize = variables.size();
        vsFactory.universe().forEach(bitSet -> {
            //get Label
            List<BooleanExpression<AtomLabel>> conjuncts = new ArrayList<>(alphabetSize);
            for (int i = 0; i < alphabetSize; i++) {
                BooleanExpression<AtomLabel> atom = new BooleanExpression<>(AtomLabel.createAPIndex(i));

                if (bitSet.get(i)) {
                    conjuncts.add(atom);
                } else {
                    conjuncts.add(atom.not());
                }
            }
            String l = BooleanExpressions.createConjunction(conjuncts).toString();

            if (variables.size() > 5 && variables.size() < 12)
                encoded_alphabet = 0;
            else if (variables.size() >= 12)
                encoded_alphabet = 1;

            if (encoded_alphabet == -1)
                setLabel(l);
            else
                setLabelEncoded(l);
        });
    }

    public <S> automata.Automaton formulaToDfa(LabelledFormula formula) {
        var environment = DefaultEnvironment.standard();
        var translator = new LTL2DPAFunction(environment, LTL2DPAFunction.RECOMMENDED_SYMMETRIC_CONFIG);

        var automaton = (Automaton<S, ParityAcceptance>) translator.apply(formula);
        return PAtoDfa(automaton);
    }

    private <S> int getStateId(@Nullable S state) {
        checkState(state != null);
        return stateNumbers.computeIntIfAbsent(state, k -> stateNumbers.size());
    }

    public <S> automata.Automaton PAtoDfa(Automaton<S, ParityAcceptance> automaton) {
//        System.out.println("Building DFA...");
        automata.Automaton fsa = new FiniteStateAutomaton();
        stateNumbers = new Object2IntOpenHashMap();
        //Map nodes to states ids
        Map<S, automata.State> ids = new HashMap<>();
        for (S s : automaton.states()) {
            int id = getStateId(s);
            automata.State state = fsa.createStateWithId(new Point(), id);
            ids.put(s, state);
        }

        int N = automaton.size();
        //create one unique initial state
        automata.State is = fsa.createStateWithId(new Point(), N + 1);
        fsa.setInitialState(is);

        //create one unique final state
        automata.State fs = fsa.createStateWithId(new Point(), N + 2);
        fsa.addFinalState(fs);

        //get initial nodes
        for (S in : automaton.initialStates()) {
            //create and set initial state
            automata.State ais = ids.get(in);
            //initial node ids
            FSATransition t = new FSATransition(is, ais, FSAToRegularExpressionConverter.LAMBDA);
            fsa.addTransition(t);
        }

        for (S from : automaton.states()) {
            Map<Edge<S>, ValuationSet> edgeMap = automaton.edgeMap(from);
            edgeMap.forEach((edge, valuationSet) -> {
                S to = edge.successor();
                if (!valuationSet.isEmpty()) {
                    valuationSet.forEach(bitSet -> {
                        //checks if ID exists
                        automata.State fromState = ids.get(from);

                        //get Label
                        List<BooleanExpression<AtomLabel>> conjuncts = new ArrayList<>(alphabetSize);
                        for (int i = 0; i < alphabetSize; i++) {
                            BooleanExpression<AtomLabel> atom = new BooleanExpression<>(AtomLabel.createAPIndex(i));

                            if (bitSet.get(i)) {
                                conjuncts.add(atom);
                            } else {
                                conjuncts.add(atom.not());
                            }
                        }
                        String l = BooleanExpressions.createConjunction(conjuncts).toString();
                        String label = labelIDs.get(l);

                        //check if toState exists
                        automata.State toState = ids.get(to);

                        FSATransition t = new FSATransition(fromState, toState, label);
                        fsa.addTransition(t);

                        //check if it is an acceptance transition
                        IntArrayList acceptanceSets = new IntArrayList();
                        if (edge.acceptanceSetIterator().hasNext())
                            edge.acceptanceSetIterator().forEachRemaining((IntConsumer) acceptanceSets::add);
                        if (accConditionIsSatisfied(automaton.acceptance().booleanExpression(), acceptanceSets)) {
                            FSATransition final_t = new FSATransition(fromState, fs, label);
                            fsa.addTransition(final_t);
                        }
                    });

                }
            });
        }
        return fsa;
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

    public void setLabel(String l) throws RuntimeException {
        if (labelIDs.containsKey(l)) {
            return;
        }

        labelIDs.put(l, String.valueOf(Character.toChars(base)[0]));

        //update base
        if (base == 57)
            base = 65; //jump to A
        else if (base == 90)
            base = 97; //jump to a
        else
            base++;

        if (base > 122)
            throw new RuntimeException("Maximum number of characters reached.");

    }

    public void setLabelEncoded(String l) throws RuntimeException {
        if (labelIDs.containsKey(l)) {
            return;
        }
        String label = "";
        if (encoded_alphabet == 1)
            label += Character.toChars(state[1])[0];
        label += Character.toChars(state[0])[0];
        label += Character.toChars(base)[0];
        labelIDs.put(l, label);

        //update base
        if (base == 57)
            base = 65; //jump to A
        else if (base == 90)
            base = 97; //jump to a
        else
            base++;

        if (base > 122) {
            base = 48;

            //update state[0]
            if (state[0] == 57)
                state[0] = 65; //jump to A
            else if (state[0] == 90)
                state[0] = 97; //jump to a
            else
                state[0]++;

            if (state[0] > 122) {
                state[0] = 48;

                //update state[1]
                if (state[1] == 57)
                    state[1] = 65; //jump to A
                else if (state[1] == 90)
                    state[1] = 97; //jump to a
                else
                    state[1]++;

                if (state[1] > 122)
                    throw new RuntimeException("Maximum number of characters reached.");
            }
        }
    }
}
