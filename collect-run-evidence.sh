#!/bin/bash
# Collects per-run evidence records for Experiment 1. Each arm runs in its own JVM.
# Output: TSV on stdout — arm, run, then the arm's measurements.
set -u
MVN=${MVN:-/srv/tyche/tools/apache-maven-3.9.11/bin/mvn}
N=${N:-5}
cd "$(dirname "$0")" || exit 1
printf "arm\trun\tmetric\tvalue\n"
run_and_scrape() {
  local arm="$1" test="$2" run="$3"
  local out
  out=$(timeout 300 "$MVN" -q -Dtest="$test" -DfailIfNoTests=false test 2>&1)
  echo "$out" | grep -E "^  [a-zA-Z]" | while IFS= read -r line; do
    local metric value
    metric=$(echo "$line" | sed 's/^ *//; s/ *:.*//' | tr -s ' ')
    value=$(echo "$line" | sed 's/.*: *//')
    printf "%s\t%s\t%s\t%s\n" "$arm" "$run" "$metric" "$value"
  done
}
for i in $(seq 1 "$N"); do
  run_and_scrape "T1-naive"     "T1VanishingGateTest#t1_naiveArm_gateDoesNotSurviveMigration"        "$i"
  run_and_scrape "T1-reinstall" "T1VanishingGateTest#t1_reinstallArm_gateSurvivesIfReinstalled"      "$i"
  run_and_scrape "T2"           "T2DoubleActivationTest#t2_oneBlob_twoNodes_bothDispatch"            "$i"
  run_and_scrape "T3-naive"     "T3StaleQueueTest#t3_naiveArm_queuedActionDispatchesUngated"         "$i"
  run_and_scrape "T3-reinstall" "T3StaleQueueTest#t3_reinstallArm_queuedActionReauthorizedAndDenied" "$i"
done
