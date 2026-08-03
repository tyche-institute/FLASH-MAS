#!/bin/bash
# Experiment 1 — all runtime tests (T4 is a static inventory, see the vault).
# Each arm runs in its own JVM: deployments use keep:-1, so nodes never stop and
# a previous arm would keep incrementing shared static counters.
set -u
MVN=${MVN:-/srv/tyche/tools/apache-maven-3.9.11/bin/mvn}
cd "$(dirname "$0")" || exit 1
for t in \
  "T1VanishingGateTest#t1_naiveArm_gateDoesNotSurviveMigration" \
  "T1VanishingGateTest#t1_reinstallArm_gateSurvivesIfReinstalled" \
  "T2DoubleActivationTest#t2_oneBlob_twoNodes_bothDispatch" \
  "T3StaleQueueTest#t3_naiveArm_queuedActionDispatchesUngated" \
  "T3StaleQueueTest#t3_reinstallArm_queuedActionReauthorizedAndDenied" ; do
  echo "--- $t"
  timeout 300 "$MVN" -q -Dtest="$t" -DfailIfNoTests=false test 2>&1 | grep -E "^=== |^  |Tests run"
done
