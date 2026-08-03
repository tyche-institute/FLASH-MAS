#!/bin/bash
# Experiment 1 / test T1 — "vanishing gate".
# Each arm runs in its own JVM: the deployment uses keep:-1, so nodes never stop and a
# previous arm's agent would keep incrementing the shared static counters.
set -u
MVN=${MVN:-/srv/tyche/tools/apache-maven-3.9.11/bin/mvn}
cd "$(dirname "$0")" || exit 1
for arm in t1_naiveArm_gateDoesNotSurviveMigration t1_reinstallArm_gateSurvivesIfReinstalled; do
  echo "--- $arm"
  timeout 300 "$MVN" -q -Dtest="T1VanishingGateTest#$arm" -DfailIfNoTests=false test 2>&1 \
    | grep -E "^=== |^  |Tests run"
done
