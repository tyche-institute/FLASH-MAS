#!/bin/bash
# Experiment 1R - enforcement placed where the recorder already is.
#
# T1's naive arm loses its gate in transit because the gate is transient state on the agent's
# own shard. This arm installs nothing on the agent: the policy is named by the host process
# and reached through the same static facade the recorder uses.
#
# Compare with: run-e1.sh, arm t1_naiveArm_gateDoesNotSurviveMigration, on the branch
# prototype/outgoing-message-gate.
set -u
MVN=${MVN:-mvn}
cd "$(dirname "$0")" || exit 1
"$MVN" -q \
  -Dtest="T1RRecorderSeamTest#t1r_hostOwnedPolicySurvivesMigration" \
  -DfailIfNoTests=false \
  -DargLine="-Dflash.policy.enabled=true -Dflash.policy.class=automatedTesting.e1r.CountingDenyAllPolicy -Dflash.recorder.enabled=false" \
  test 2>&1 | grep -E "^=== |^  |Tests run|POLICY"
