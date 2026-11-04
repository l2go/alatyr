# Participant meaning

The package implements the fixed `alatyr/1` dictionary: 24 fact kinds, 14 verbs,
neutral event and command values, bounded observation lifetimes, saved proposals,
and an actor-owned `State`. `Codec` supplies the dictionary registry to the pinned
protocol codec; it does not duplicate record framing or primitive decoding.

Production uses `Cursor`, which retains reference generations and saved proposal
authority. The world supplies a fresh typed `View` for each checkpoint and
planning call. No scene is retained by the cursor. `Environment` grants authority
only to source incarnations whose references have already been published.
An observation gap retires those references without resetting their counter.

`Input` comes from trusted composition. `Event.MS` keeps source time;
`ReceivedAt` is gateway-relative observation time. A proposal token identifies
the original world-issued offer. `ProposalBorn` preserves its original lifetime
through checkpoints, so snapshots cannot renew authority. Role strings are
opaque labels; a nil classifier grants no target capability.

`State` remains the bounded typed fixture implementation for dictionary and
runtime contracts. Its custom strategies and inferred witness rules let tests
exercise the generic runtime independently. The direct production adapter uses
exact source outcomes and one effect write. It does not infer a world result
from a snapshot or an unrelated event. Unknown lists, known-empty lists and
unknown numeric values remain distinct. Fact records own their returned data.

All 11 gateway State methods remain. Start, Confirmed, Witness, and CandidateValid
return errors for foreign, nil, or wrong-owner action values; Witness also rejects
foreign update values. The runtime closes that participant with `CloseInternal`
and never logs the value or error text. Apply and Proof already return errors;
Validate returns an explicit refusal. A State is discarded after Apply errors,
including invalid shapes or exhausted bounds, rather than reused partially.

From the gateway package directory:

```sh
GOWORK=off go vet ./...
GOWORK=off go test -race -count=1 -timeout=2m ./...
GOWORK=off go test -run='^$' -fuzz='^FuzzDecodeMeaning$' -fuzztime=10s -parallel=1 ./meaning
```

Conformance reads the unchanged V01-V28 fixture from the exact published protocol
snapshot. Missing or changed fixtures fail the check; there is no fallback copy.
The package imports no bridge or running backend.

License: [AGPL-3.0-or-later](../LICENSE).

`use-item` is verb 14. Intent field 21 is the positive signed 32-bit object ID
of a currently carried inventory row, not a template ID or ground reference.
The world chooses the authored slot and toggles equipment, or launches its
consumable effect. No participant slot, quantity or arbitrary skill is accepted.
It shares the `trade` attempt class with buy/sell to serialize inventory actions.
Goods field 7 is change metadata; field 8, when present, is the essential
equipment slot (-1 means unequipped). Stats fields 8 and 9 are physical attack and known defense.

Production Done and Refused come from the captured world's exact commitment.
For a healing item, Done proves consumption, not an HP increase. A refused
commitment certifies that the entire attempt had no effects. Terminal Sent for
walk, strike and cast is unchanged. An unresolved asynchronous operation keeps
its original deadline and may finish Unknown. See the
[participant compatibility note](../../../../docs/engine-go/direct-gateway.md).

The fixture `State` also supports `Source` and attributed `Input` values for
independent tests of inferred evidence. Its item-use witness needs a decreased
quantity or changed equipment slot; trade needs a matching template or object;
a dialogue choice needs a matching live window and NPC lifetime. Those fixture
rules cannot substitute a snapshot or unrelated refresh for completion. The
direct world adapter does not grant evidence authority or use these rules.

## Player appearance

An optional nonessential Fact object at field 35 describes a player: race (1),
sex (2) and base visual branch (3). The closed `wire` vocabulary distinguishes
human, elf, dark elf, Kruvan and dwarf; male and female; fighter and mystic.
Zero explicitly means unknown. An absent appearance object means no report,
not an empty object and not a default fighter. Numeric class IDs are never sent.

The real Go-world producer includes appearance in self descriptions and visible
player appearances, both live and in snapshots. Full self Vitals reports also
carry it; partial health reports may omit it. A missing report preserves known
appearance within an observation lifetime. Gone and scope invalidation discard
it; a checkpoint restores it from the world's snapshot. An explicit refresh is an
honest appearance fact, not a capability or causal witness. NPC descriptions
cannot carry player appearance.

`ClassKindKnown` and `Mage` supply the branch. Missing metadata stays unknown,
regardless of numeric class ID or stats. The world currently exposes no race,
sex or class mutation during an incarnation; changing character creation or
class selection is outside this change. Any future mutator must publish a new
player/self description. The name `alatyr/1`, action policies and historical
contract fixtures are unchanged. Clients select representations from these
facts independently of permissions.

## NPC form

An optional nonessential Fact string at field 36 names an NPC's visual form.
The value is an opaque world label matching `[a-z][a-z0-9_]{0,31}`, not a
closed list of creatures and not a capability. Absence means the template set
no form. An empty string or any other token is an invalid frame.

The field is valid only on `Appeared` for a role other than `player` or `drop`.
Players keep appearance field 35. Drops, movement, vitals and other facts omit
form; placing it there is an invalid frame. A later `Appeared` is a complete
description, so an omitted form clears a previously stored label. `Gone` ends
the observation. A checkpoint restores the form from the world's snapshot of the
current description. The producer copies `client_class` and does not derive a
label from the display name, role, client id or template id. The name
`alatyr/1` and historical contract fixtures are unchanged.
