# EstiMate 🦉

EstiMate is an approximate model counter, designed to estimate the number of models for a given LTL formula. It utilizes a transfer matrix, generated through a series of tree encodings, to provide approximate model counts.

#### Acknowledgments 🌟

If you utilize EstiMate for research purposes, we kindly request that you cite the corresponding paper where the technique was introduced and evaluated: [Automated Repair of Unrealisable LTL Specifications Guided by Model Counting.](https://dl.acm.org/doi/10.1145/3583131.3590454) Thank you :)

```
@inproceedings{10.1145/3583131.3590454,
author = {Brizzio, Mat\'{\i}as and Cordy, Maxime and Papadakis, Mike and S\'{a}nchez, C\'{e}sar and Aguirre, Nazareno and Degiovanni, Renzo},
title = {Automated Repair of Unrealisable LTL Specifications Guided by Model Counting},
year = {2023},
isbn = {9798400701191},
publisher = {Association for Computing Machinery},
address = {New York, NY, USA},
url = {https://doi.org/10.1145/3583131.3590454},
doi = {10.1145/3583131.3590454},
booktitle = {Proceedings of the Genetic and Evolutionary Computation Conference},
pages = {1499–1507},
numpages = {9},
keywords = {search-based software engineering, LTL-synthesis, model counting},
location = {Lisbon, Portugal},
series = {GECCO '23}
}
```


## Maintainers 👨‍💻

This code is implemented and maintained by [Matias Brizzio](mailto:matias.brizzio@imdea.org?subject=[GitHub]%20Source%20EstiMate) and [Renzo Degiovanni](https://rdegiovanni.github.io)

## Transfer Matrix Generation 🔄

The transfer matrix, denoted as T<sub>ϕ</sub>, is generated using the following steps:

1. Generate the Buchi automaton B<sub>ϕ</sub> that recognizes the specification ϕ.
2. From B<sub>ϕ</sub>, generate a Finite State Automaton A<sub>ϕ</sub>.
3. Generate the transfer matrix T<sub>ϕ</sub> from A<sub>ϕ</sub>.

## Understanding the Process 🤔
The Buchi automaton B<sub>ϕ</sub> recognizes lasso traces satisfying the formula ϕ. A lasso trace is an infinite trace that loops back to a previous state, forming a cycle. It accepts infinite traces, as its accepting condition requires visiting an accepting state infinitely often in the lasso trace. In contrast, finite automata recognize finite traces, as their accepting condition only requires reaching a final state.

When generating the finite automaton A<sub>ϕ</sub> from B<sub>ϕ</sub>, the finite traces recognized by A<sub>ϕ</sub> become part of the lasso traces recognized by B<sub>ϕ</sub>.

The finite automaton A<sub>ϕ</sub> is encoded into an N x N transfer matrix T<sub>ϕ</sub>, where N represents the number of states in A<sub>ϕ</sub>. Each element T<sub>ϕ</sub>[i,j] in the matrix denotes the number of transitions from state i to state j.

By using matrix multiplication, the number of traces of length k accepted by A<sub>ϕ</sub> can be computed as I x T<sub>ϕ</sub><sup>k</sup> x F. Here, I is a row vector representing the initial states, T<sub>ϕ</sub><sup>k</sup> is the matrix resulting from multiplying T<sub>ϕ</sub> k times, and F is a column vector representing the final states of A<sub>ϕ</sub>.

## Linear Temporal Logic (LTL) <a name="LTL" /> 🕰️

The grammar for LTL aims to support Spot-style LTL. For further details on temporal logic, e.g., semantics, see [here](https://spot.lrde.epita.fr/tl.pdf).

### Propositional Logic ✔️

* True: `tt`, `true`, `1`
* False: `ff`, `false`, `0`
* Atom: `[a-zA-Z_][a-zA-Z_0-9]*` or quoted `"[^"]+"`
* Negation: `!`, `NOT`
* Implication: `->`, `=>`, `IMP`
* Bi-implication: `<->`, `<=>`, `BIIMP`
* Exclusive Disjunction: `^`, `XOR`
* Conjunction: `&&`, `&`, `AND`
* Disjunction: `||`, `|`, `OR`
* Parenthesis: `(`, `)`

### Modal Logic 🚪

* Finally: `F`
* Globally: `G`
* Next: `X`
* (Strong) Until: `U`
* Weak Until: `W`
* (Weak) Release: `R`
* Strong Release: `M`

### Precedence Rules 📜

To ensure the accurate interpretation of provided LTL formulas, the parser adheres to the following precedence rules:

- **Binary Expressions**: These expressions involve two operands and an operator. Examples include conjunction (`&&`, `&`, `AND`), disjunction (`||`, `|`, `OR`), implication (`->`, `=>`, `IMP`), bi-implication (`<->`, `<=>`, `BIIMP`), exclusive disjunction (`^`, `XOR`), strong until (`U`), weak until (`W`), weak release (`R`), and strong release (`M`).

- **Unary Expressions**: These expressions involve a single operand and an operator. Examples include negation (`!`, `NOT`), next (`X`), globally (`G`), and finally (`F`).

- **Literals, Constants, Parentheses**: These are fundamental components of the formula. Literals represent atomic propositions, constants denote truth values (e.g., `tt` for true, `ff` for false), and parentheses are used to group subexpressions.

The precedence hierarchy is structured as follows:

`OR` < `AND` < `Binary Expressions` < `Unary Expressions` < `Literals`, `Constants`, `Parentheses`

This means that binary expressions take precedence over unary expressions, which, in turn, take precedence over literals, constants, and parentheses. For instance, in the expression `a && b || c`, the conjunction (`&&`) is evaluated before the disjunction (`||`), and parentheses can be employed to override this precedence.

Moreover, for chained binary expressions lacking parentheses, the rightmost binary operator holds precedence. For example, `a -> b U c` is parsed as `a -> (b U c)`.


## 🛠️ Installation Instructions

### REQUIREMENTS

- Java 18 or later.

### COMPILE ESTIMATE 
To compile Estimate just run the following bash command.

```bash
make compile
```
Additionally, if you desire to get *jar* file together with the compilation run

```bash
make
```

This last rule will not only compile the whole project but also create a *jar* file (into the `dist` folder) you can use inside your projects.

## RUN ESTIMATE 🚀

**The tool can take as input either a specification in TLSF format or directly an LTL formulae. To run, you have to use the script `modelcount.sh`.**

```bash
./modelcount.sh case-studies/arbiter/arbiter.tlsf 
```

or:

```
./modelcount.sh -formula="(F (a && p))" -k=10 -vars=a,p [-flags] [-to=timeout]
```

### Flags 🚩
 * `-auto = enables EstiMate`
 * `-re = uses a Regular expression model counter`
 * `without flag; uses exact model counter`


## Contact Us 📧

This README is here to give you a complete rundown of EstiMate, covering its features and how to use it effectively. If you have any questions or come across any roadblocks, don't hesitate to get in touch with us.

Thanks for taking an interest in EstiMate! 🦉

Happy coding and may your projects soar to new heights! 🚀

## Publications Using EstiMate
EstiMate has recently been employed to resolve conflicts within complex real-world LTL specifications [[1]](https://rdcu.be/dIdOr). Additionally, it has been utilized to detect bugs in some of the most commonly used SAT solvers [[2]](https://dl.acm.org/doi/abs/10.1145/3597503.3639087) within the SE and formal methods communities
