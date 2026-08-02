# Optional outgoing-message gate prototype

Status: local discussion branch only. This has not been proposed or submitted
upstream.

## Problem

`MessagingShard.OutgoingMessageHook` observes a message immediately before the
pylon send, but returns `void`. It cannot stop a dispatch or return a structured
policy/evidence reference. Changing that existing interface would break its
implementations.

## Prototype boundary

The prototype leaves `MessagingShard` unchanged and adds a separate optional
capability:

- `GatedMessagingShard` — opt-in extension implemented by
  `AbstractMessagingShard`;
- `OutgoingMessageGate` — one synchronous pre-dispatch decision method;
- `OutgoingMessageContext` — immutable source, destination and serialized
  content snapshot;
- `MessageDecision` — `PERMIT` or `DENY` plus opaque reason, policy and evidence
  references.

With no gates registered, the existing send paths retain their former
behaviour. With one or more gates registered, every gate must return `PERMIT`.
A denial, `null` result or runtime failure stops the pylon dispatch.

## Decision-to-dispatch invariant

For wave messaging, the gate evaluates an immutable snapshot. Existing outgoing
hooks are then called. Because hooks still receive the mutable `AgentWave`, the
snapshot is recomputed after the hooks: if source, destination or serialized
content changed after the permit, dispatch fails closed. This is the minimal
TOCTOU/substitution check required for the permit to describe the wave actually
sent.

For classic messaging, hooks receive strings, so they cannot mutate the values
that follow to the pylon.

## Verification

Targeted test:

```bash
/srv/tyche/tools/apache-maven-3.9.11/bin/mvn \
  -q -Dtest=OutgoingMessageGateTest test
```

Five deterministic cases pass:

1. denial prevents both the observation hook and pylon send;
2. permit preserves the existing wave dispatch path;
3. gate exception fails closed;
4. hook mutation after permit fails closed;
5. classic messaging is also gated.

The project packages successfully with `-Dmaven.test.skip=true`. The unmodified
upstream full test command compiles and runs its suite but does not terminate in
this environment because agent threads remain alive; it also emits existing
`CompositeAgent.eventProcessingCycle` null-pointer failures. That baseline issue
is outside this prototype and must not be represented as caused or fixed here.

## Questions for the maintainer

1. Should a denial be exposed through a new decision event/listener, or should
   each gate persist its evidence before returning as this prototype assumes?
2. Is returning `false` enough for callers, or is a future structured
   `sendMessage` result worth a separate non-breaking API?
3. Should gates be registered through the shard API, configuration/deployment
   descriptors, or a security wrapper shard?
4. Which `AgentWave` fields beyond routing and serialized content must be part
   of an immutable authorization snapshot?
5. Does the security/student branch already define a recorder or decision type
   that this prototype should adapt to instead of introducing parallel names?
