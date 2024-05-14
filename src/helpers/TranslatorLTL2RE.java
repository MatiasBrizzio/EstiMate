package helpers;

import automata.fsa.*;
import de.uni_luebeck.isp.rltlconv.automata.DirectedState;
import de.uni_luebeck.isp.rltlconv.automata.Nba;
import de.uni_luebeck.isp.rltlconv.automata.Sign;
import de.uni_luebeck.isp.rltlconv.automata.State;
import de.uni_luebeck.isp.rltlconv.cli.Conversion;
import de.uni_luebeck.isp.rltlconv.cli.Main;
import de.uni_luebeck.isp.rltlconv.cli.RltlConv;
import jhoafparser.ast.AtomLabel;
import jhoafparser.ast.BooleanExpression;
import main.Settings;
import owl.automaton.Automaton;
import owl.automaton.acceptance.BooleanExpressions;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.output.HoaPrinter;
import owl.collections.ValuationSet;
import owl.factories.FactorySupplier;
import owl.factories.ValuationSetFactory;
import owl.ltl.LabelledFormula;
import owl.run.DefaultEnvironment;
import owl.run.Environment;
import owl.translations.LTL2DAFunction;
import scala.Tuple2;
import scala.collection.Iterator;
import scala.collection.immutable.List;
import scala.collection.immutable.Map;
import scala.collection.immutable.Vector;
import scala.collection.immutable.VectorIterator;

import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.io.*;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import static owl.automaton.output.HoaPrinter.HoaOption.SIMPLE_TRANSITION_LABELS;


public class TranslatorLTL2RE {
    //Map labels to ids
    public java.util.Map<String, String> labelIDs;
    public int encoded_alphabet;
    public int[] state = {48, 48};//start with char 0
    int base;
    int alphabetSize;

    public TranslatorLTL2RE() {
        labelIDs = new HashMap<>();
        encoded_alphabet = -1;
        alphabetSize = 0;
    }

    public String toABClanguage(String re) {
        String abcStr;
        abcStr = re.replace("λ", "\"\"");
        abcStr = abcStr.replace("+", "|");
        return abcStr;
    }

    public void generateLabels(Nba nba) {
        if (!labelIDs.isEmpty())
            throw new RuntimeException("labelsID not empty.");
        Iterator<String> it = nba.alphabet().iterator();
        while (it.hasNext()) {
            String l = it.next();
            if (encoded_alphabet == -1)
                setLabel(l);
            else
                setLabelEncoded(l);
        }
    }

    public void generateLabels(java.util.List<String> variables) {
        if (!labelIDs.isEmpty())
            return;
        Environment env = DefaultEnvironment.standard();
        FactorySupplier factorySupplier = env.factorySupplier();
        ValuationSetFactory vsFactory = factorySupplier.getValuationSetFactory(variables);
        alphabetSize = variables.size();
        vsFactory.universe().forEach(bitSet -> {
            //get Label
            java.util.List<BooleanExpression<AtomLabel>> conjuncts = new ArrayList<>(alphabetSize);
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

    public String ltl2RE(String formula) throws IOException, InterruptedException {
        Nba nba = ltl2nba(formula);
        generateLabels(nba);
        return automata2RE(nba);
    }

    private void writeFile(String fname, String text) throws IOException {
        FileOutputStream output = new FileOutputStream(fname);
        output.write(text.getBytes());
        output.flush();
        output.close();
    }

    private void runCommand() throws IOException, InterruptedException {
        Process p = Runtime.getRuntime().exec("./rltlconv.sh @rltlconv.txt --props --formula --apa --nba --min");

        boolean timeout = false;
        if (!p.waitFor(Settings.MC_TIMEOUT, TimeUnit.SECONDS)) {
            timeout = true; //kill the process.
            p.destroy(); // consider using destroyForcibly instead
        }

        if (timeout)
            throw new RuntimeException("TIMEOUT reached in RltlConv translation");

        InputStream in = p.getInputStream();
        InputStreamReader inread = new InputStreamReader(in);
        BufferedReader bufferedreader = new BufferedReader(inread);
        String aux;
        StringBuilder out = new StringBuilder();
        while ((aux = bufferedreader.readLine()) != null) {
            out.append(aux).append("\n");
        }
        // Close the InputStream
        bufferedreader.close();
        inread.close();
        in.close();

        if (!out.toString().isEmpty())
            writeFile("rltlconv-out.txt", out.toString());

        InputStream err = p.getErrorStream();
        InputStreamReader errread = new InputStreamReader(err);
        BufferedReader errbufferedreader = new BufferedReader(errread);

        while ((aux = errbufferedreader.readLine()) != null) {
            System.out.println("ERR: " + aux);
        }
        // Close the ErrorStream
        errbufferedreader.close();
        errread.close();
        err.close();
        // Check for failure
        if (p.waitFor() != 0) {
            System.out.println("exit value = " + p.exitValue());
            throw new RuntimeException("ERROR in RltlConv translation.");
        }

        OutputStream os = p.getOutputStream();
        if (os != null) os.close();
        p.destroy();
    }

    public Nba ltl2nba(String formula) throws IOException, InterruptedException {
        //write results to file
        String fname = "rltlconv.txt";

        writeFile(fname, formula);

        runCommand();

        Object res = Main.load("@rltlconv-out.txt");

        return (Nba) RltlConv.convert(res, Conversion.NBA());
    }

    public String formulaToRegularExpression(LabelledFormula formula) {
        LTL2DAFunction translator = new LTL2DAFunction(DefaultEnvironment.standard(), false, EnumSet.allOf(LTL2DAFunction.Constructions.class));
        Automaton<?, ? extends OmegaAcceptance> automaton = translator.apply(formula);
        if (automaton.size() == 0)
            return null;
        //automaton.acceptance().acceptingSet();
        System.out.println(formula + " ");
        System.out.println(HoaPrinter.toString(automaton, EnumSet.of(SIMPLE_TRANSITION_LABELS)));
        return automata2RE(automaton);
    }

    public String automata2RE(Nba ltl_ba) {
//		System.out.println(ltl_ba);
        FiniteStateAutomaton fsa = new FiniteStateAutomaton();

        //Map nodes to states ids
        java.util.Map<State, automata.State> ids = new HashMap<>();
        Iterator<State> states_it = ltl_ba.states().iterator();
        while (states_it.hasNext()) {
            State s = states_it.next();
            automata.State state = fsa.createState(new Point());
            //initial node ids
            ids.put(s, state);
        }

        //get initial node
        if (ltl_ba.start().size() > 1) throw new RuntimeException("automata2RE: more than 1 initial state found");
        State in = ltl_ba.start().head();
        automata.State is = ids.get(in);
        fsa.setInitialState(is);

        Map<Tuple2<State, Sign>, List<DirectedState>> trans = ltl_ba.transitions();
        Vector<Tuple2<Tuple2<State, Sign>, List<DirectedState>>> vector = trans.toVector();
        VectorIterator<Tuple2<Tuple2<State, Sign>, List<DirectedState>>> ltl_ba_it = vector.iterator();
        while (ltl_ba_it.hasNext()) {
            Tuple2<Tuple2<State, Sign>, List<DirectedState>> o = ltl_ba_it.next();
            State from = o._1()._1();
            automata.State fromState = ids.get(from);
            //get Label
            String l = o._1()._2().toString().replace("\"", "");
            String label = labelIDs.get(l);
            Iterator<DirectedState> listIt = o._2().iterator();
            while (listIt.hasNext()) {
                State to = listIt.next().state();
                automata.State toState = ids.get(to);
                //add transition
                FSATransition t = new FSATransition(fromState, toState, label);
                fsa.addTransition(t);
            }
        }

        //add final states
        Iterator<State> ac_it = ltl_ba.accepting().iterator();
        while (ac_it.hasNext()) {
            State a = ac_it.next();
            automata.State as = ids.get(a);
            fsa.addFinalState(as);
        }

        NFAToDFA determinizer = new NFAToDFA();
        FiniteStateAutomaton dfa = determinizer.convertToDFA(fsa);
        Minimizer min = new Minimizer();
        min.initializeMinimizer();
        automata.Automaton minimizable = min.getMinimizeableAutomaton(dfa);
        DefaultTreeModel tree = min.getDistinguishableGroupsTree(minimizable);
        minimizable = min.getMinimumDfa(minimizable, tree);
        FSAToRegularExpressionConverter.convertToSimpleAutomaton(minimizable);
        return FSAToRegularExpressionConverter.convertToRegularExpression(minimizable);
    }

    public <S> String automata2RE(Automaton<S, ? extends OmegaAcceptance> ltl_ba) {
        FiniteStateAutomaton fsa = new FiniteStateAutomaton();
        //Map nodes to states ids
        java.util.Map<S, automata.State> ids = new HashMap<>();
        for (S s : ltl_ba.states()) {
            automata.State state = fsa.createState(new Point());
            ids.put(s, state);
        }

        //create one unique initial state
        automata.State is = fsa.createState(new Point());
        fsa.setInitialState(is);

        //get initial nodes
        for (S in : ltl_ba.initialStates()) {
            //create and set initial state
            automata.State ais = ids.get(in);
            //initial node ids
            FSATransition t = new FSATransition(is, ais, FSAToRegularExpressionConverter.LAMBDA);
            fsa.addTransition(t);
        }

        for (S from : ltl_ba.states()) {
            java.util.Map<Edge<S>, ValuationSet> edgeMap = ltl_ba.edgeMap(from);
            edgeMap.forEach((edge, valuationSet) -> {
                S to = edge.successor();
                if (!valuationSet.isEmpty()) {

                    valuationSet.forEach(bitSet -> {
                        //checks if ID exists
                        automata.State fromState = ids.get(from);

                        java.util.List<BooleanExpression<AtomLabel>> conjuncts = new ArrayList<>(alphabetSize);
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
                        //add transition
                        FSATransition t = new FSATransition(fromState, toState, label);
                        fsa.addTransition(t);
                    });
                }

                if (edge.acceptanceSetIterator().hasNext()) {
                    //get state
                    automata.State as = ids.get(to);
                    fsa.addFinalState(as);
                }
            });
        }

        NFAToDFA determinizer = new NFAToDFA();
        FiniteStateAutomaton dfa = determinizer.convertToDFA(fsa);
        Minimizer min = new Minimizer();
        min.initializeMinimizer();
        automata.Automaton minimizable = min.getMinimizeableAutomaton(dfa);
        DefaultTreeModel tree = min.getDistinguishableGroupsTree(minimizable);
        minimizable = min.getMinimumDfa(minimizable, tree);
        FSAToRegularExpressionConverter.convertToSimpleAutomaton(minimizable);
        return FSAToRegularExpressionConverter.convertToRegularExpression(minimizable);
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
        if (this.encoded_alphabet == 1)
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

    public String genABCString(LabelledFormula ltl) {
        int vars = ltl.variables().size();
        if (vars > 5 && vars < 12)
            encoded_alphabet = 0;
        else if (vars >= 12)
            encoded_alphabet = 1;
        generateLabels(ltl.variables());
        String s = formulaToRegularExpression(ltl);
        if (s == null)
            return null;
        return toABClanguage(s);
    }

}
