# Experiment 1R — enforcement placed where the recorder already is

Branch `prototype/recorder-seam-policy`, cut from `origin/verification` at `900ddbda`.
Measured 2 September 2026, three runs, identical every time.

## What this tests

T1, on the branch `prototype/outgoing-message-gate`, put the enforcement point on the agent's
own messaging component and found that it did not survive relocation. The registry was a
`transient` field, so the arriving incarnation had no gate, its next send went out unchecked,
and nothing in the system said so.

The obvious question is whether that is a property of the platform or a property of where we
put the check. This experiment answers it by changing only the placement.

The `verification` branch already has a seam of exactly the right shape. `RecorderService` is
a static facade whose backend is chosen, once, from system properties read when the class is
loaded in a host process; `record` is called as the first statement of both dispatch methods
in `AbstractMessagingShard`. What it cannot do is refuse, because every method on
`RecorderInterface` returns `void`.

So this branch adds the missing half:

| File | What it is |
| --- | --- |
| `ActionDecision` | permit or deny, with the identifier of the deciding rule |
| `ActionPolicy` | the same two call shapes as `RecorderInterface`, returning a decision |
| `PolicyService` | a static facade built exactly like `RecorderService`, from `flash.policy.enabled`, `flash.policy.class` and `flash.policy.declared_mediated` |

and consults it in `AbstractMessagingShard`, at the same two points where `record` is already
called, refusing the dispatch when the decision is deny and recording the refusal as an
`ACTION_DENIED` event so a denial is an outcome rather than a silence.

Default is off. With `flash.policy.enabled` absent, the cost is one null test per dispatch and
no behaviour changes.

## The result

Deployment identical to T1's: two hosts in one JVM over the WebSocket substrate, a mobile
agent, one message before the move and one after arrival. The one change is that **the agent
installs nothing**. There is no gate to register and no re-installation on arrival; the policy
is named by the host in `flash.policy.class`.

| | T1 naive arm, gate on the agent | T1R, policy on the host |
| --- | --- | --- |
| Agent-side enforcement work | registers a gate at first start | none at all |
| Agent starts (boot + arrival) | 2 | 2 |
| Enforcement consulted about the agent | **1** (pre-move only) | **2** (both sides) |
| Post-move `sendMessage` returned | `true`, dispatched | `false`, refused |
| Post-move message received by peer | **1** | **0** |
| Property P1 `NoUngatedDispatch` | **fails** | **holds** |

Same platform, same commit lineage, same test shape, and an agent that does strictly less
work. The only difference is whether the enforcement point belongs to the agent or to the host
it is running in.

Three runs, every figure identical.

## A second finding, which is the cost of the placement

The first version of this experiment used a policy that refused everything, exactly as T1's
gate did. **The agent never migrated.** A policy at this seam is consulted for every messaging
component in the host process, including the ones the platform itself uses to carry out the
move, so refusing everything refused the migration.

That is not a bug in the policy. It is the shape of the thing: moving enforcement from the
agent to the host widens its reach as well as its coverage. A gate installed on one agent can
only ever see that agent; a host-side policy sees the host. In these runs it was consulted 3
times in total about 2 distinct entities, the agent and the node, while only 2 of those
consultations concerned the agent.

The practical consequence is that a host-side policy has to be written per entity from the
first line, which is why the entity name is the first argument of every method on
`ActionPolicy`. The practical benefit is the other side of the same coin: this seam sees
platform traffic that an agent-side gate structurally cannot.

## What this does not show

Both hosts run in one JVM and therefore share one static facade. What is demonstrated is that
enforcement is consulted after relocation **with no agent-side re-installation**, which is
exactly what T1's naive arm failed to achieve. That a second operating-system process builds
its own policy from its own configuration follows from the construction, not from this run,
and a two-machine version of this experiment is the obvious next step.

This also changes nothing about coverage. The seam is still the messaging component's dispatch
methods, so every path that bypasses those methods bypasses this policy too. Placement fixes
the lifecycle problem. It does not fix the inventory.

## Reproducing

```bash
git clone -b prototype/recorder-seam-policy <fork> && cd FLASH-MAS
./run-e1r.sh
```

The runner sets the three system properties and runs the single test. Java 21, Maven. Expect
the table above, and the assertions in `T1RRecorderSeamTest` enforce it: the agent must
actually migrate, the policy must be configured, the agent must start twice, the policy must
be consulted at least twice, the post-move send must be refused, and the peer must receive
nothing.
