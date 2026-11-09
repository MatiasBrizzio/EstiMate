# Non-determinism in the model counter

EstiMate can give a different count for the same formula on different runs. This document records how often that happens, which formulas are affected, why it happens, and what `--det` does about it.

## Summary

- 4 of 10000 random formulas changed count between runs.
- With `--det`, none of the 10000 changed between two runs.
- The cause is outside EstiMate: Owl's translation depends on iteration orders that Java randomises at startup.
- AuRUS shows the same variation, because it uses the same Owl and the same counting code.

## Method

`fuzz-compare.sh -repeat` generates a seeded stream of random formulas and counts each one in two separate JVM runs.

- Propositions: `a`, `b`, `c`, all declared.
- Word length: `k = 10`.
- Formula depth: 4, using `!`, `X`, `F`, `G`, `&`, `|`, `->`, `<->`, `U`, `W`, `R` and `M`.
- Seeds 1 and 2, with 5000 formulas each. Solver: aalta. Timeout: 10 s.
- Environment: OpenJDK 27.0.1, macOS arm64, Owl 18.10-snapshot.

The results are in [`experiments/`](../experiments), one JSON object per line:

| File | Contents |
| --- | --- |
| `fuzz-default.jsonl` | Two runs without `--det` |
| `fuzz-det.jsonl` | Two runs with `--det` |

Each line has `seed`, `index`, `formula`, `det`, `first` and `second` (each with `status` and `count`), and `varies`.

To reproduce them:

```bash
./fuzz-compare.sh -repeat -n=5000 -seed=1 -jsonl=experiments/fuzz-default.jsonl
./fuzz-compare.sh -repeat --det -n=5000 -seed=1 -jsonl=experiments/fuzz-det.jsonl
```

## Results

| Mode | Formulas | Compared | Varying |
| --- | --- | --- | --- |
| Without `--det` | 10000 | 9995 | 2 in the recorded files |
| With `--det` | 10000 | 9995 | 0 |

Two runs only reveal part of the variation. Across the repeated fuzz runs, these 4 formulas changed:

| Seed | Index | Formula | Values seen |
| --- | --- | --- | --- |
| 1 | 1004 | `((X ((b) & (b))) R ((X (!c)) W ((!b) M (a)))) W (((!(!c)) & (a)) W ((G (b)) U ((b) R (!b))))` | 40954666, 40956136, 40960159 |
| 1 | 3291 | `(((G (a)) R ((a) M (c))) -> (a)) W (a)` | 1069547520, 1073741824 |
| 2 | 1713 | `(F ((!b) W ((!c) R (a)))) W ((b) M (!c))` | 1028989852, 1028995590 |
| 2 | 4529 | `!(((G (b)) R ((!a) M (c))) M (a))` | 1071100966, 1073741824 |

The first formula's three values were also observed when running AuRUS alone. For the first formula, the automaton keeps 27 states and 135 accepting edges, but its structure differs between runs, and each structure gives its own count.

### Formulas that fail

5 formulas, all from seed 2, end in `error`, with both aalta and BLACK. Owl throws an exception during translation (`UnsupportedOperationException` or `ArrayIndexOutOfBoundsException`). All 5 are satisfiable according to Spot. They are reported as failures and never count as 0.

| Index | Formula |
| --- | --- |
| 1330 | `F (G ((G (a)) W ((!c) <-> (!a))))` |
| 2208 | `G ((G (!c)) U (((!b) <-> (a)) & (F (!c))))` |
| 4145 | `F ((a) R (X (X (!b))))` |
| 4619 | `G ((!b) U (((c) \| (!a)) & (F (!c))))` |
| 4979 | `G ((X ((c) -> (!b))) U ((F (c)) M (G (b))))` |

## Cause

The variation comes from two sources in the JVM. Each row is 14 runs of a formula in new JVMs:

| What was fixed | Seed 1, index 1004 | Seed 1, index 3291 |
| --- | --- | --- |
| Nothing | 3 values | 1 value in that batch, 2 across batches |
| Only `REVERSE` | 3 values | 1 value |
| Only `SALT32L` | 2 values | 1 value |
| `REVERSE` and `SALT32L` | 1 value: 40956136 | 1073741824 |
| `REVERSE`, `SALT32L` and the identity hash | 1 value: 40956136 | 1069547520 |

1. **Immutable collections.** Owl stores formula children in `Set.of` collections. `java.util.ImmutableCollections` chooses the iteration order of those collections with `REVERSE` and `SALT32L`, which it randomises at each JVM start.
2. **Identity hashes.** The hash of a formula uses the hash of its operator's class, which changes between runs.

These were ruled out, because the count still varied:

- Running on 1 processor.
- Disabling the common fork-join pool.
- Running in the interpreter (`-Xint`).

**Not established:** the exact place in Owl where the order changes the result. These formulas have a temporal operator at the root, so DELAG hands them to its fallback translator, `SymmetricDRAConstruction`.

Sorting the formula before translating would not help. Owl builds the `Set.of` collections itself when it constructs a formula, and the automaton is built from that object. The printed formula was identical in runs that gave different counts.

## What `--det` does

`--det` makes two changes:

1. `modelcount.sh` starts the JVM with `-XX:+UnlockExperimentalVMOptions -XX:hashCode=2`, which makes every identity hash equal to 1.
2. `Determinism.pin()` writes fixed values into the private fields `REVERSE` and `SALT32L` of `java.util.ImmutableCollections`, before any formula is created.

### Limits

- **Experimental.** It relies on private JDK fields, `sun.misc.Unsafe` and an experimental JVM flag. It has only been tested on OpenJDK 27.0.1 on macOS, and it may stop working on other Java versions.
- **It fails instead of degrading.** If the fields are missing, or the JVM flag is absent, the run ends with an error.
- **The fixed value is arbitrary.** `--det` selects one of the possible automata. Its count is repeatable, and no more correct than the others.
- **Counts differ from runs without `--det`** on the affected formulas.
- **Changing `SALT32L` after startup** affects collections the JDK has already built. No problem appeared in the tests, but it is a risk on other JVMs.
- **Tested for the variation above.** 10000 formulas agreed between two runs, which is strong evidence and not a proof.
