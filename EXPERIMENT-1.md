# Experiment 1 — does authorization survive an agent's relocation?

This branch, `prototype/outgoing-message-gate`, adds an optional enforcement point to
FLASH-MAS and four tests that ask whether it still applies after a mobile agent moves.
It is a research artefact, cut from upstream `3fcf07d2`. Java 21 and Maven are all it needs.

## Run it

```bash
./run-e1.sh          # all five arms, printing the measurements
./run-e1-t1.sh       # T1 only
```

Each arm runs in its own JVM. That is not a style preference: the deployments boot with
`keep:-1`, so nodes never stop, and a previous arm's agent would keep incrementing the shared
static counters. Run the arms separately or use the scripts.

Set `MVN` if `mvn` is not on your path: `MVN=/path/to/mvn ./run-e1.sh`.

## What is added

| Class | Role |
| --- | --- |
| `OutgoingMessageGate` | the enforcement interface: sees an outgoing action, returns a decision |
| `MessageDecision` | permit or deny, with reason, policy and evidence identifiers |
| `OutgoingMessageContext` | an immutable snapshot of the action being authorized |
| `GatedMessagingShard` | the capability a messaging component exposes to accept gates |

`AbstractMessagingShard.authorizeOutgoing` consults the registered gates on all four dispatch
entry points, fail-closed within the call, re-taking the snapshot after the observation hooks
so that a hook cannot mutate an approved action.

## The four tests

| Test | Property | Question |
| --- | --- | --- |
| T1 | `NoUngatedDispatch` | Is the enforcement point still consulted after the agent moves? |
| T2 | `AtMostOneActiveEpoch` | Can one serialized agent be activated on several nodes at once? |
| T3 | `NoStaleQueuedDispatch` | Is work queued before a move re-authorized on arrival? |
| T4 | coverage | Which egress paths reach the enforcement point at all? (static inventory) |

T1 and T3 run two arms each: *naive*, registering the gate once at first start, and
*reinstall*, re-registering on every agent start. The naive arm is what a straightforward
consumer of the API would write, and its outcome is the finding.

Measured 3 August 2026, five runs per arm with identical figures, and re-run unchanged on
2 September 2026. The write-up of each test, with the per-run records, lives outside this
repository; `run-e1.sh` prints the numbers those write-ups quote.

## Related branch

`prototype/recorder-seam-policy`, cut from `verification`, asks the follow-up question: what
changes if the enforcement point belongs to the host process rather than to the agent. See
`EXPERIMENT-1R.md` there.

## Licence

GPL-3, as the rest of FLASH-MAS. Files added by this branch carry
`Copyright (C) 2026 Anton Sokolov`; everything else belongs to its original authors, listed in
`CONTRIBUTORS.md`.
