#!/bin/bash
# Counts the same seeded random formulas with every available configuration and
# reports any disagreement. Exits non-zero if there is one.
#
#   ./fuzz-compare.sh [-n=5000] [-seed=1] [-timeout=10] [-aurus=PATH_TO_AURUS]
#   ./fuzz-compare.sh -repeat [--det] [-n=5000] [-seed=1] [-timeout=10] [-jsonl=FILE]
#
# Always compares aalta with BLACK when BLACK is installed; with -aurus it also
# compares EstiMate with AuRUS for each solver. A formula on which a run failed
# (timeout, error) is reported as skipped, not as a disagreement. With -repeat
# it runs EstiMate twice instead, to measure variation between runs; -jsonl
# appends one line per formula with both results to FILE.
set -u

N=5000
SEED=1
TIMEOUT=10
AURUS=""
REPEAT=0
DET=""
DET_FLAGS=""
JSONL=""
for arg in "$@"; do
  case "$arg" in
    -n=*) N="${arg#-n=}" ;;
    -seed=*) SEED="${arg#-seed=}" ;;
    -timeout=*) TIMEOUT="${arg#-timeout=}" ;;
    -aurus=*) AURUS="${arg#-aurus=}" ;;
    -repeat) REPEAT=1 ;;
    -jsonl=*) JSONL="${arg#-jsonl=}" ;;
    --det) DET=det; DET_FLAGS="-XX:+UnlockExperimentalVMOptions -XX:hashCode=2" ;;
    *) echo "unknown option: $arg" >&2; exit 2 ;;
  esac
done

LIBS="lib/commons-math3-3.6.1.jar:lib/rltlconv.jar:lib/JFLAP-7.0_With_Source.jar:lib/owl-18.10-snapshot.jar:lib/ejml/ejml-core-0.34.jar:lib/ejml/ejml-cdense-0.34.jar:lib/ejml/ejml-ddense-0.34.jar:lib/ejml/ejml-fdense-0.34.jar:lib/ejml/ejml-simple-0.34.jar:lib/ejml/ejml-zdense-0.34.jar:lib/ejml/ejml-dsparse-0.34.jar:lib/ejml/ejml-experimental-0.34.jar:lib/ltl2buchi.jar"
ESTIMATE="$(cd "$(dirname "$0")" && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

# run NAME REPO SOLVER: counts the formulas from inside REPO, whose lib/ it needs.
run() {
  mkdir -p "$WORK/$1"
  (cd "$2" && javac -nowarn -cp "bin:$LIBS" -d "$WORK/$1" "$ESTIMATE/test/fuzz/FuzzCounts.java" 2>/dev/null \
    && java $DET_FLAGS -Xmx8g -Djava.library.path=/usr/local/lib -cp "$WORK/$1:bin:$LIBS" FuzzCounts \
       "$N" "$SEED" "$3" "$TIMEOUT" "$WORK/$1.tsv" $DET >/dev/null 2>&1)
  [ -s "$WORK/$1.tsv" ] || { echo "ERROR: run $1 produced no output" >&2; exit 2; }
}

# compare NAME_A NAME_B: prints a summary line and every disagreement.
compare() {
  paste "$WORK/$1.tsv" "$WORK/$2.tsv" | awk -F'\t' -v a="$1" -v b="$2" '
    function conclusive(s) { return s == "OK" || s ~ /^UNSAT_/ }
    {
      if ($1 != $4) { print "ERROR: the two runs generated different formulas"; exit 3 }
      if (!conclusive($2) || !conclusive($5)) { skipped++; next }
      compared++
      if ($2 != $5 || $3 != $6) { differ++; print "DIFF " $1 " => " a "=" $2 "/" $3 " " b "=" $5 "/" $6 }
    }
    END { printf "%s vs %s: compared=%d skipped=%d differ=%d\n", a, b, compared, skipped, differ; exit (differ > 0) }'
}

STATUS=0
if [ "$REPEAT" -eq 1 ]; then
  run first-run "$ESTIMATE" aalta
  run second-run "$ESTIMATE" aalta
  compare first-run second-run || STATUS=1
  if [ -n "$JSONL" ]; then
    paste "$WORK/first-run.tsv" "$WORK/second-run.tsv" | awk -F'\t' -v seed="$SEED" -v det="${DET:+true}" '
      {
        gsub(/\\/, "\\\\", $1); gsub(/"/, "\\\"", $1)
        printf "{\"seed\":%s,\"index\":%d,\"formula\":\"%s\",\"det\":%s,\"first\":{\"status\":\"%s\",\"count\":\"%s\"},\"second\":{\"status\":\"%s\",\"count\":\"%s\"},\"varies\":%s}\n",
          seed, NR - 1, $1, (det == "true" ? "true" : "false"), $2, $3, $5, $6, (($2 != $5 || $3 != $6) ? "true" : "false")
      }' >> "$JSONL"
  fi
  exit "$STATUS"
fi
run estimate-aalta "$ESTIMATE" aalta
HAVE_BLACK=0
if black --sat-backends >/dev/null 2>&1; then
  HAVE_BLACK=1
  run estimate-black "$ESTIMATE" black
  compare estimate-aalta estimate-black || STATUS=1
else
  echo "black is not usable: skipping the solver comparison"
fi
if [ -n "$AURUS" ]; then
  run aurus-aalta "$AURUS" aalta
  compare estimate-aalta aurus-aalta || STATUS=1
  if [ "$HAVE_BLACK" -eq 1 ]; then
    run aurus-black "$AURUS" black
    compare estimate-black aurus-black || STATUS=1
  fi
fi
exit "$STATUS"
