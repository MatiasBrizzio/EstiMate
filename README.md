# EstiMate 🦉

EstiMate estimates how many traces of a given length satisfy an LTL formula. It builds the formula's automaton, encodes it as a transfer matrix, and counts with one matrix power, so it stays fast on formulas where exact counting is expensive.

Use it when you need to compare LTL specifications by how much they allow: for ranking formulas, measuring how far apart two specifications are, or guiding a search. The same counter also ships inside [AuRUS](https://github.com/MatiasBrizzio/AuRUS), for people already using that tool.

## Quick start 🚀

Requires **Java 18 or later** and [Apache Ant](https://ant.apache.org/). The other libraries and the default solver are in `lib/`.

```bash
make compile
./modelcount.sh -k=10 -vars=a,b 'G (a -> X b)'
# {"status":"ok","count":"78732","ms":210}
```

To also build a jar in `dist/`, run `make`.

## Counting models 🔢

`modelcount.sh` is the only command. Give it a formula or a TLSF file. It prints one line of JSON, or just the number with `-c`:

```bash
./modelcount.sh -k=10 -vars=a,b 'G (a -> X b)'
./modelcount.sh -k=10 examples/arbiter.tlsf
./modelcount.sh -c -k=10 -vars=a,b 'G (a -> X b)'
# 78732
```

`status` is one of:

| Status | Meaning | Exit code |
| --- | --- | --- |
| `ok` | The count is in `count` | 0 |
| `unsat_simplified` | Unsatisfiable, found by the simplifier; `count` is 0 | 0 |
| `unsat_solver` | Unsatisfiable, proven by the solver; `count` is 0 | 0 |
| `timeout` | A translation, counting or solver timeout | 1 |
| `error` | A solver or internal failure | 1 |

A failure never carries a count. With `-c`, standard output stays empty on a failure. Bad usage exits with code 2.

| Option | Meaning |
| --- | --- |
| `-k=N` | Word length (default 10) |
| `-vars=a,b` | Declared propositions; default: those in the formula. Not allowed with TLSF files |
| `-sat=NAME` | Satisfiability solver: `aalta` (default) or `black` |
| `-sat-bin=PATH` | Override the solver binary |
| `--timeout=S` | Translation and counting timeouts, in seconds |
| `--dump` | Add the automaton to the JSON (up to 10 propositions) |
| `-c`, `-count` | Print only the number |
| `--det` | Repeatable counts between runs. Experimental: see [Repeatable counts](#repeatable-counts) |

### Older options

These keep the original behaviour, which prints the number on its own (`timeout` if the count failed). With a file, they also print a ranking and write the results to `result/`.

| Option | Meaning |
| --- | --- |
| `-formula=F` | The formula to count |
| `-to=S` | Counting timeout, in seconds |
| `-out=FILE` | Where to write the results for a file of formulas |
| `-re` | Use the regular-expression counter |
| `-exact`, `-exact=NAME` | Use the exact counter, with `relsat` (default), `cachet` or `miniC2D` |

### From Java

```java
LabelledFormula formula = LtlParser.parse("G (a -> X b)", List.of("a", "b"));
CountResult result = ModelCounter.count(formula, 10);
if (!result.isFailure()) {
    System.out.println(result.count());
}
```

`ModelCounter` is in `modelcounter.estimate`. `CountResult` holds a count, a proof of unsatisfiability, or a failure, and `count()` throws on a failure.

## What the number means 🤔

EstiMate counts the **words of length `k` whose run ends in a successor of an accepting edge**. This is an approximation of the number of satisfying traces, and it depends on the automaton: two equivalent formulas can get different counts. On a few formulas the count can also change between runs of the same program (4 of 10000 random formulas in our fuzzing). See [Repeatable counts](#repeatable-counts).

| Formula, with `-vars=a,b` and `-k=10` | Count |
| --- | --- |
| `G a` | 1024 |
| `G a & F b` | 1023 |
| `G (a -> X b)` | 78732 |
| `G (a -> X b) & G (a -> F b)` (equivalent to the previous one) | 65610 |
| `G (a -> X b) & !G (a -> F b)` (unsatisfiable) | 0 |

**Unsatisfiable formulas count 0.** The formula is simplified and then checked with a satisfiability solver before counting. Only a conclusive answer gives 0: a solver timeout, an error, an empty answer or `UNKNOWN` is reported as a failure.

**Declared propositions change the number.** They set the alphabet, so each proposition declared but not used multiplies the count by `2^k`. `G a` counts 1 with `-vars=a` and 1024 with `-vars=a,b`.

**Cost grows with the alphabet.** Counting visits every letter of the alphabet, so it takes time proportional to `2^n` for `n` declared propositions, and declaring unused ones is expensive.

## Satisfiability solvers ⚙️

| | aalta | BLACK |
| --- | --- | --- |
| Installation | Included in `lib/` | Separate: `brew install black-sat/black/black-sat`, or the packages on its [releases page](https://github.com/black-sat/black/releases). Tested with 26.05.0 |
| Simultaneous runs in one directory | **Not safe**: it uses a work file in its current directory, and runs interfere and give wrong answers | Safe |

Use `-sat=black` when several runs execute at once in the same directory.

## Repeatable counts <a name="repeatable-counts" /> 🎯

Without `--det`, a few formulas can get a different count on each run, because Java randomises the order of some collections at startup and Owl's translation depends on that order. `--det` fixes that order so that repeated runs agree:

```bash
./modelcount.sh --det -k=10 -vars=a,b 'G (a -> X b)'
```

**`--det` is experimental.** It writes private JDK fields and uses an experimental JVM flag, so it may stop working on other Java versions. It has only been tested on OpenJDK 27.0.1 on macOS. If it cannot be applied on your JVM, the run fails with an error instead of continuing without repeatable results. It makes runs agree with each other, but the count it gives is one of the possible values, not a more correct one.

The measurements, the formulas that vary and the reasons are in [docs/non-determinism.md](docs/non-determinism.md).

## Comparing configurations 🧪

`fuzz-compare.sh` counts the same seeded random formulas with every available configuration and reports any disagreement. It compares aalta with BLACK when BLACK is installed, and EstiMate with AuRUS when you pass `-aurus`.

```bash
./fuzz-compare.sh -n=5000 -seed=1 -timeout=10 -aurus=../AuRUS
```

With `-repeat` it runs EstiMate twice instead, to measure variation between runs, and `--det` applies repeatable counts to both runs. Formulas on which a run timed out or failed are reported as skipped. The script exits with a non-zero code if any two configurations disagree. Compile EstiMate, and AuRUS if you use it, first.

## How it works 🔄

1. The formula is translated into a deterministic automaton with an Emerson-Lei acceptance condition, using Owl.
2. The automaton is encoded as an N x N transfer matrix T, where N is the number of states and T[i,j] is the number of letters that go from state i to state j.
3. The count is I x T<sup>k</sup> x F, where I marks the initial states and F marks the states reached by an accepting edge.

The automaton accepts infinite traces, so step 3 looks at finite prefixes of them. A prefix can end in a marked state even if no continuation satisfies the formula, which is why the result is an approximation.

## LTL syntax <a name="LTL" /> 🕰️

The grammar follows Spot-style LTL. For the semantics of temporal logic, see [here](https://spot.lrde.epita.fr/tl.pdf).

| Propositional | Syntax |
| --- | --- |
| True | `tt`, `true`, `1` |
| False | `ff`, `false`, `0` |
| Atom | `[a-zA-Z_][a-zA-Z_0-9]*` or quoted `"[^"]+"` |
| Negation | `!`, `NOT` |
| Implication | `->`, `=>`, `IMP` |
| Bi-implication | `<->`, `<=>`, `BIIMP` |
| Exclusive disjunction | `^`, `XOR` |
| Conjunction | `&&`, `&`, `AND` |
| Disjunction | `\|\|`, `\|`, `OR` |

| Temporal | Syntax |
| --- | --- |
| Finally | `F` |
| Globally | `G` |
| Next | `X` |
| (Strong) until | `U` |
| Weak until | `W` |
| (Weak) release | `R` |
| Strong release | `M` |

From loosest to tightest, operators bind as `OR`, `AND`, the other binary operators, then the unary ones. So `a && b || c` is `(a && b) || c`. Chained binary operators without parentheses group to the right: `a -> b U c` is `a -> (b U c)`. Use parentheses when in doubt.

## Citing EstiMate 🌟

If you use EstiMate for research, please cite the paper where the technique was introduced and evaluated: [Automated Repair of Unrealisable LTL Specifications Guided by Model Counting](https://dl.acm.org/doi/10.1145/3583131.3590454).

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

### Publications using EstiMate

EstiMate has been used to resolve conflicts within complex real-world LTL specifications [[1]](https://rdcu.be/dIdOr), to measure the distance between assume-guarantee contracts [[2]](https://arxiv.org/pdf/2211.11908), and to detect bugs in some of the most commonly used SAT solvers [[3]](https://dl.acm.org/doi/abs/10.1145/3597503.3639087).

## Maintainers 👨‍💻

EstiMate is implemented and maintained by [Matias Brizzio](mailto:matias.brizzio@imdea.org?subject=[GitHub]%20Source%20EstiMate). If you have questions or run into problems, get in touch.
