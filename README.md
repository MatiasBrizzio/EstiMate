# EstiMate

EstiMate is an approximate model counter, designed to estimate the number of models for a given LTL formula. 
It utilizes a transfer matrix, generated through a series of tree encodings, to provide approximate model counts.

#### Acknowledgments
If you utilize EstiMate for research purposes, 
we kindly request that you cite the corresponding paper where the technique was introduced and evaluated: 
[Automated Repair of Unrealisable LTL Specifications Guided by Model Counting.](https://arxiv.org/abs/2105.12595) 
Thank you :)

## Transfer Matrix Generation

The transfer matrix, denoted as T<sub>ϕ</sub>, is generated using the following steps:

1. Generate the Buchi automaton B<sub>ϕ</sub> that recognizes the specification ϕ.
2. From B<sub>ϕ</sub>, generate a Finite State Automaton A<sub>ϕ</sub>.
3. Generate the transfer matrix T<sub>ϕ</sub> from A<sub>ϕ</sub>.

## Understanding the Process

The Buchi automaton B<sub>ϕ</sub> recognizes lasso traces satisfying the formula ϕ. 
It accepts infinite traces, as its accepting condition requires visiting an accepting state infinitely often in the 
lasso trace. 
In contrast, finite automata recognize finite traces, as their accepting condition only requires reaching a final state.

When generating the finite automaton A<sub>ϕ</sub> from B<sub>ϕ</sub>, 
the finite traces recognized by A<sub>ϕ</sub> become part of the lasso traces recognized by B<sub>ϕ</sub>.

The finite automaton A<sub>ϕ</sub> is encoded into an N x N transfer matrix T<sub>ϕ</sub>, 
where N represents the number of states in A<sub>ϕ</sub>. 
Each element T<sub>ϕ</sub>[i,j] in the matrix denotes the number of transitions from state i to state j.

By using matrix multiplication, the number of traces of length k accepted by A<sub>ϕ</sub> can be computed as I x T<sub>ϕ</sub><sup>k</sup> x F. Here, I is a row vector representing the initial states, T<sub>ϕ</sub><sup>k</sup> is the matrix resulting from multiplying T<sub>ϕ</sub> k times, and F is a column vector representing the final states of A<sub>ϕ</sub>.
