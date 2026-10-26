# Java world bridge

The bridge connects a trusted Go world adapter to the authoritative Java world over IPv4 loopback.
It replaces the client protocol on this internal path. Existing client connections keep their current path.
The endpoint is disabled by default. This document defines the endpoint contract; it does not announce a release.

The Java endpoint is a virtual game client with the same per-client FIFO execution queue as other clients.
It constructs typed requests directly and translates outbound packets before their client encoding.
Packet eligibility, preparation and post-send hooks retain their order. The endpoint never invokes client
packet encoding, encryption or a client login exchange.

The Go core remains one shared implementation. Java has a small implementation of the same framing and field
encoding, checked against the identical immutable conformance vectors. Java does not define a second format.

## Commands and evidence

Each adapter request has a session-scoped number. Register its normalized content before execution.
An identical retained request returns the original result without another world action. Different content
under the same number produces a separate duplicate_mismatch service error and cannot replace that result.
Keep at most eight pending requests and the last 64 completed requests. Pending requests are never evicted.

An execution context is local to the current thread and character. Mutation receipts record actual item,
coin, skill, SP and target changes at their mutation points. Snapshots and arbitrary notices are not receipts.
Nested wrappers record each change once. A mutation on another thread or another character does not enter
the current request's ledger. Keep charges and refunds as separate changes.

For commands with a receipt contract, an exception wins over every receipt. Otherwise an exact expected
set gives done; an approved typed refusal with zero receipts gives refused; partial, extra or unproven effects
give unknown. Movement, attacks, casts and return relocation have no synchronous completion set and return
sent unless execution fails. A shop or inventory window alone never proves a completed transaction.

Bridge Facts never carry a causal request number. Result.effects contains immutable receipts, not commands
to apply to a snapshot. The participant gateway separately enforces its selected dictionary's outcome contract.
A bridge done result alone cannot grant participant done or create a causal Fact.

## Sessions and recovery

The session states are opening, live, detached, closing and closed. Trusted admission shares the existing
account reservation with ordinary clients and checks both waiting and active accounts atomically.
The exact virtual client identity owns the reservation; a late cleanup cannot remove another client's entry.
The bridge does not call the client login admission or leave methods.

Transport loss and output-queue overflow detach the transport for the configured grace period. Unstarted
requests receive transport_lost; a started request finishes and retains its result. Resume uses the next
gateway transport epoch and always sends a complete current checkpoint before retained results and new actions.
It does not run EnterWorld again. A timeout and Resume arbitrate by execution order in the client queue.

Any world disconnect API call for a bridge client requests one complete disconnect. The first call already
includes detach, one transactional save, world removal, virtual channel close and identity-checked reservation
release. Later calls in a fluent chain add no missing work. Failures are reported, and cleanup remains idempotent.

## Relocation

An outbound teleport packet is not completion: Java sends it before publishing the new position.
After the position is published, the originating Java thread queues a generation-tagged completion task.
The task shares the client FIFO with commands and disconnects and uses the common SendAppearing completion
sequence. It has no participant request number and does not wait for a gateway acknowledgement.

For bridge characters, the relocation body and the generation check plus completion share one synchronization
boundary. A stale generation cannot complete a newer relocation. Closing prevents another spawn after removal.
Transport loss does not cancel relocation completion. A checkpoint cannot become ready while own relocation
is unfinished; completion resumes it without blocking the FIFO. Foreign teleports only update foreign positions.
The legacy teleport watchdog is neither required nor enabled for the bridge.

## Projection boundary

The endpoint retains only information communicated to this character. It does not enumerate the entire world.
A checkpoint copies bounded current state and proposal windows, preserving unknown versus known empty values.
Historical damage, rewards, pickups and revival are not replayed as new occurrences. Proposal expiry is not
renewed by snapshot replay. Output is streamed in bounded frames rather than queued as one large snapshot.

Packets have an explicit translated or nonessential classification. An unlisted class, including an unlisted
subclass, is essential and closes the session. A translation error never produces a falsely complete checkpoint.
Raw saved dialogue actions travel only on the trusted internal link and remain outside participant messages.
Texts are rendered as text; the endpoint never executes markup or substitutes participant strings into actions.

## Delivery

Java builds and integration checks run in GitHub Actions. The integration job starts the packaged Java
platform with PostgreSQL, enables this listener in an isolated operator configuration, and runs the public
Go client and participant gateway against it. The ordinary client smoke runs alongside the bridge clients. This endpoint does not change a
release version, expose a public listener, or claim support for every legacy client UI panel.

## Endpoint profile: bridge/1

Use wire version 1 over a length-prefixed TCP byte stream bound to 127.0.0.1. Canonical unsigned base-128
varints, zigzag signed integers, field essentiality, whole-frame validation and presence rules are unchanged.
A record is type, body length and fields. A field key is (number << 4) | (essential << 3) | wire type.
The whole frame is structurally validated before any record is applied. Writers sort fields; readers accept
any order. Unknown odd records are skipped; unknown even records are unsupported. Unknown essential fields
are rejected, never silently applied. An unsupported essential bridge Fact marks its domain incomplete
and closes the bridge session; the neutral participant conformance policy remains separate.

In the tables, ! is required and essential, e is optional but essential when present, and o is optional
and nonessential. U, S, B, Str, Bytes, Obj and lists use the wire core's types.

| Type | Record | Direction | Fields |
|---|---|---|---|
| 1,3 | Ping,Pong | either | 1 nonce U ! |
| 6 | Checkpoint | Java to Go | 1 phase U !; 2 epoch U !; 3 ready B !; 4 incomplete list(Str) e |
| 8 | Fact | Java to Go | 1 kind U !; 2 seq U !; 3 ms S !; 4 epoch U !; 5 domain Str !; 6 kind_essential B !; registered payload |
| 10 | Intent | Go to Java | 1 n U !; 2 verb U !; 3 control U !; 4 queue_ttl_ms U e; registered payload |
| 12 | Result | Java to Go | 1 n U !; 2 stage U !; 3 reason U !; 4 text Text o; 5 requested U !; 6 effective U !; 7 delivery U !; 64 reason_namespace Str e; 65 bridge_reason U e; 66 effects list(Effect) e |
| 16 | Bye | either | 1 reason U !; 2 text Text o |
| 256 | Open | Go to Java | 1 wire_min U !; 2 wire_max U !; 3 profile Str !; 4 secret Bytes !; 5 account Str !; 6 character Str !; 7 gateway_epoch U ! |
| 258 | Opened | Java to Go | 1 status U !; 2 game_session U e; 3 world Str e; 4 gateway_epoch U e; 5 bridge_reason U !; 6 limits Obj e; 7 wire U e; 8 profile Str e |
| 260 | Resume | Go to Java | 1 game_session U !; 2 gateway_epoch U !; 3 secret Bytes !; 4 profile Str ! |
| 262 | Close | Go to Java | 1 game_session U !; 2 gateway_epoch U !; 3 reason U !; 4 text Text o |
| 264 | IntentError | Java to Go | 1 n U !; 2 code U !; 3 text Text o |

Open or Resume is the first and only record of its frame. Open negotiates wire 1 and exactly bridge/1,
provides a 32-byte shared secret and starts gateway_epoch at 1. Account and character are nonempty UTF-8
identifiers of at most 128 bytes without NUL. Compare the secret without data-dependent early exit.
Never log it. Opened status is 1 accepted or 2 refused. Acceptance requires fields 2,3,4,6,7,8 and reason 0;
refusal omits those fields and requires a nonzero reason. World is at most 256 bytes. Resume requires the
same session and the next gateway epoch. Close uses normal reason 0. Hello, Welcome and Control are not
valid records on this listener. These assignments do not alter existing core vectors or allocate record 18.

Stage codes are sent=1, done=2, refused=3, unknown=4, unrecoverable=5. Delivery codes are none=0,
local=1, backend=2, partial=3. Core reason numbers keep their meanings. Internal details use paired Result
fields 64 and 65 with namespace x.l2jfree/bridge; they are not inserted into the core reason enum.
Done requires effects, including a present empty list for pure speech; other stages omit effects.
IntentError code 22 is duplicate_mismatch and 256 is overflow. Neither creates a second Result.

| Internal reason | Code | Internal reason | Code |
|---|---|---|---|
| none | 0 | not_enough_coin | 1 |
| no_room | 2 | too_far | 3 |
| bad_target | 4 | no_skill | 5 |
| invalid | 6 | not_ready | 7 |
| rate | 8 | transport_lost | 9 |
| closing | 10 | already_online | 11 |
| no_character | 12 | closed | 13 |
| not_enough_sp | 14 | missing_item | 15 |
| not_allowed | 16 | out_of_stock | 17 |
| unavailable | 18 | out_of_order | 19 |
| backend_error | 20 | too_old | 21 |

Raw world object identifiers are not participant observation references. Bridge Facts omit Ref fields 10/16
and causal field 32. The gateway assigns observation generations and participant projection epochs.
Fact.epoch and Intent.control identify the gateway transport epoch. A relocation generation is separate.

| Fact extension | Type and presence | Meaning |
|---|---|---|
| 64 raw_id | S e | actor, ground item or scalar quest identifier |
| 65 raw_target | S e | second raw object identifier |
| 66 teleport_generation | U e | own relocation generation |
| 67 own_teleport | B e | true only for own relocation |
| 68 saved_options | list(BridgeOption) e | internal dialogue actions |
| 69 training_type | U e | original training type |
| 70 raw_alive | B e | explicit current actor state, including checkpoints |
| 71 message_id | U e | typed source message identifier |
| 72 message_args | list(MessageArg) e | typed source message arguments |
| 73 npc_template | S e | observed NPC template identifier |
| 74 proposal_ms | S e | original proposal time, never renewed by replay |

BridgeOption fields are 1 option U !, 2 label Text !, 3 saved_action Str !. The saved action is not a
participant option string. MessageArg fields are 1 type U !, 2 text Str e, 3 number S e, 4 second S e,
5 third S e. Types are text0, number1, NPC2, item3, skill4, fortress5, long6, zone7. The type determines
which value fields must be present. Effect fields are 1 kind U !, 2 item S e, 3 object S e, 4 delta S e,
5 skill S e, 6 old S e, 7 new S e, 8 target S e. Kinds are item1, coin2, skill3, SP4 and target5.
Item requires 2/3/4; coin requires 4; skill requires 5/6/7; SP requires 4/6/7; target requires 8.

Intent payloads use the unchanged Position, SkillUse, list and count encodings. Field 64 is the raw target,
item or inventory object; 66 is an item identifier; 68 is a captured dialogue action. Walk uses field 11,
buy/sell use 14 and 16, cast/learn use SkillUse 18, and cast may use force 19. Return relocation has no
payload and selects the ordinary town point. Adapter requests use independent increasing numbers;
target-selection helpers cannot reuse an action's number with different content.

An ordinary sale offer has list identifier 0; preserve that identifier when selling.
A buy command requires a positive shop identifier. The Go client accepts the observed
zero sale identifier rather than substituting an unrelated shop number.

| Setting | Value |
|---|---|
| Maximum frame, string, list, depth | 65536 bytes, 4096 bytes, 1024 elements, 3 |
| Records per frame, fields per object | 1024, 128 |
| Pending and completed requests | 8 and 64 |
| Encoded output queue | 262144 bytes, including the active frame |
| Retained projection and snapshot | 2097152 bytes each; one snapshot per session |
| Visible objects; keyed domain rows | 4096; 1024 |
| Proposal windows and bytes per window | 8; 65536 |
| Result entry and receipts | 8192 bytes; 64 |
| Queue TTL, read/write deadlines | 5000 ms maximum; 5000 ms each |
| Opening and checkpoint deadlines | 10000 ms each |
| Ping interval and Pong timeout | 15000 ms and 30000 ms |
| Best-effort Bye deadline | 100 ms |
| Admission cap | 64 sessions and 64 transports |

The configuration file is bridge.properties in the server configuration directory. Defaults are
bridge.enabled=false, bridge.port=9016, bridge.secret empty, bridge.grace=30000 milliseconds.
An enabled endpoint requires a valid 64-hex-character secret and a successful loopback bind. Invalid enabled
configuration fails startup; it does not weaken authentication. The port range is 1 to 65535 and grace is
1 to 300000 ms. Do not commit a live secret. A disconnect caused by queue overflow detaches the transport;
a translation failure or unlisted essential packet closes the session.

Acceptance uses immutable core and endpoint vectors, deterministic scheduling tests, Java integration tests
and later Go adapter traces. It covers charges and refunds, partial effects, duplicates, both timeout/Resume
orders, complete recovery, generation races and relocation with the legacy watchdog disabled. Java builds
and world smoke tests run in GitHub Actions.
### Packet classifications

Exact class identity selects the following translations. Preserve receiver eligibility and copy values
before enqueueing. No client byte encoding or reflective access is involved.

| Classes | Translation |
|---|---|
| UserInfo | appeared/self vitals; own fields only, exact visible identity and status |
| CharInfo | appeared; visible player fields, no private foreign vitals |
| AbstractNpcInfo$NpcInfo | appeared; observed NPC view and explicit alive state |
| AbstractNpcInfo$SummonInfo,AbstractNpcInfo$TrapInfo,AbstractNpcInfo$PlayerMorphInfo | appeared; receiver-filtered summon, trap or morph view |
| ServerObjectInfo | appeared; HP only where the source attackable branch reveals it |
| NpcInfoPoly,NpcLinkHtmlMessage | NpcInfoPoly: appeared; NpcLinkHtmlMessage: notice with no dialogue authority |
| PetInfo | appeared for the pet; never the player self inventory |
| SpawnItem,SpawnItemPoly | dropped; item identity, position and exact count |
| DropItem | dropped with the known dropper |
| GetItem | took; actor and departing item, distinct from an inventory receipt |
| DeleteObject | gone; retire visibility without inventing death |
| MoveToLocation | moved; source and destination positions |
| MoveToPawn | moved toward the observed pawn position, without promising arrival |
| StopMove | stopped; exact position |
| ValidateLocation,SetToLocation | corrected; position correction alone does not advance participant epoch |
| TeleportToLocation | corrected; own relocation adds generation and own flag, not completion |
| Attack | struck for each typed hit; miss does not imply damage |
| Die,Revive | explicit died or revived, live only; snapshots use current state |
| TargetSelected | targeted; source actor and target |
| MyTargetSelected | targeted for self; the packet object is the selected target |
| TargetUnselected | targeted with target zero; the stored identifier is the actor |
| ItemList | carried; complete own inventory, preserving known empty |
| InventoryUpdate | inventory; exact add/modify/remove rows and any reported purse change |
| StatusUpdate | vitals; only reported level, XP, HP, MP and SP attributes |
| BuyList | shop; observed rows, stock, source list, purse and source tax rounding |
| SellList | offer; observed sale objects, counts and reference-price halves |
| AcquireSkillList | practice; typed lessons and original training type |
| SkillList | known; display skill IDs, levels, passive and the original disabled flag |
| QuestList | quests; reported active quest IDs and conditions/flags, no hidden quests |
| NpcHtmlMessage,NpcQuestHtmlMessage | spoke only for the observed dialogue NPC; otherwise notice; saved actions stay internal |
| NpcSay,CreatureSay,L2FriendSay | notice; ambient speech/chat cannot complete a dialogue request |
| SystemMessage | notice and typed arguments; reward only for award messages 45,331,95; refusal registry is separate |
| ExShowScreenMessage | notice; text or explicit message identifier, never a machine-authority string |
| AutoAttackStart,AutoAttackStop | vitals combat-until; start uses the existing 15000 ms stance interval |
| ChangeMoveType | appeared refresh; only previously reported movement-speed information |

The following exact classes are nonessential in this initial profile. A new subclass is not implicitly
included. Unlisted classes close the session; unsupported game panels are never reported as empty.

| Classes | Reason for omission |
|---|---|
| ActionFailed | non-unique generic action marker |
| AcquireSkillDone,AcquireSkillInfo | training UI; receipts and Known remain authoritative |
| SkillCoolTime,MagicEffectIcons,EffectInfoPacket,EtcStatusUpdate,ShortBuffStatusUpdate | effect/cooldown UI outside the represented state; no inferred cast outcome |
| ShortCutInit,ShortCutRegister,SendMacroList,ExBasicActionList | client hotkeys and macros, never commands |
| HennaInfo,ExStorageMaxCount,ExVitalityPointInfo,ExBrExtraUserInfo | auxiliary UI statistics outside the profile Stats |
| SSQInfo,SSQStatus,ClientSetTime,ExSetCompassZoneCode | time, period and compass UI |
| FriendList,FriendAddRequest | social roster UI |
| PledgeShowMemberListAll,PledgeShowMemberListUpdate,PledgeShowMemberListAdd,PledgeShowMemberListDelete,PledgeShowMemberListDeleteAll,PledgeStatusChanged,PledgeSkillList,PledgeSkillListAdd,PledgeShowInfoUpdate,PledgeInfo,PledgeCrest,ExPledgeCrestLarge,AllyCrest,AllyInfo | clan UI, distinct from own Known and player Learn |
| TutorialShowQuestionMark,TutorialEnableClientEvent,TutorialCloseHtml | tutorial UI without an actionable dialogue proposal |
| ExGetBookMarkInfoPacket,ExNotifyBirthDay | bookmark and birthday UI |
| PlaySound,SocialAction,CameraMode,NormalCamera,SpecialCamera,Earthquake,ExRedSky,SunRise,SunSet,ExShowSlideshowKamael,ExPlayScene,ExStartScenePlayer,ExSpawnEmitter | sound, camera and visual effects |
| StartRotation,StopRotation,FinishRotation,ChangeWaitType | heading and posture only |
| SetupGauge,MagicSkillUse,MagicSkillLaunched,MagicSkillCanceled | cast animation/gauge, no completion witness |
| RelationChanged,NicknameChanged,ExChangeNicknameNColor | cosmetic labels and relations, no action permission |
| PetItemList,PetInventoryUpdate,PetStatusShow,PetStatusUpdate,SetSummonRemainTime | pet-only UI, distinct from player inventory/self |

### Execution and receipt sets

| Command | Required bridge proof |
|---|---|
| buy | Exact requested positive item quantity and exactly one actual negative coin receipt, including tax |
| sell | Exact requested object removal and exactly one actual positive coin receipt |
| take | Exact own item receipt for the captured ground item; one selection receipt may accompany it |
| learn | One skill transition from below the offered level to that level, plus exactly the selected branch's costs |
| talk, choose | Synchronous Spoke from the captured NPC and no mutation receipts; shop or training panels do not qualify |
| internal selection | Exact new target receipt; selecting an existing target does not perform an action |
| walk, strike, cast, return | No synchronous completion set; sent unless execution fails |

The actual branch fixes learning costs before mutation. Normal learning may require one spellbook and SP;
transformation and certification use their selected item costs; fishing and collection use their selected
item/count and SP. Clan training and arbitrary quest learning do not prove a personal skill outcome in this
profile. Refunded charges do not cancel their original receipt. Source wrappers do not record the same change twice.

Typed post-execution refusals use only these source messages: observer 781 for talk/take/selection; range2193
for buy/sell/learn; quantity1036 for buy/sell; weight422 for buy; slots129 for buy/take; stock350, coin279 and
authorization794 for buy; transaction483 for sell; missing item276, SP278, leader236 and clan reputation1852
for learn; active trade149 and pickup55/56/57 for take. Every refusal requires zero receipts. Other messages
are display-only. No post-execution refusal registry is assigned to movement, attacks, casts, return or choose.

Resume checkpoints contain current state and unexpired proposals, without replaying historical effects.
Never apply Result.effects to that state a second time. Do not emit ready until own relocation has completed
and every actionable domain is known. Fail a partial checkpoint rather than announcing readiness.

## Configuration and verification

`bridge.properties` uses the same defaults and operator overlay as the other server properties.
`-Dl2jfree.config.defaults` names the shipped configuration directory;
`-Dl2jfree.config.operator` names the directory of operator overrides. Without these properties,
the server reads `config/bridge.properties` relative to its working directory. Keep the shared
secret in the operator file with access restricted to the server and adapter. The shipped file
leaves the endpoint disabled. The listener always binds IPv4 loopback; it is not a public service.

Open uses the existing character-selection path and runs the ordinary `EnterWorld` handler on
the client FIFO before capturing the initial scene. A failed entry requests full cleanup and
cannot publish an accepted ready checkpoint. Resume retains that character and never reruns entry.

The Go API is [bridge.Client](../../engine/go/bridge/README.md). `Open` returns the complete
initial snapshot. Drain `Updates`, use a new caller key for each `Write`, and call `Resume`
explicitly after a detached update. Caller keys cannot be reused to request another execution.
Recovery returns retained receipts separately from the current snapshot; it never authorizes a
participant outcome or applies receipts to inventory. `Close` waits for the remote save outcome
and reports an unconfirmed close as an error.

The [integration runner](../../tools/ci/bridge-integration.sh) uses the distribution produced by
`./mvnw -B -ntp -Dgroups=integration -Dsurefire.excludedGroups= verify`. It requires Linux, Docker,
PostgreSQL client commands in the database container, Microsoft JDK 25, and Go 1.27.3. Run
`bash tools/ci/bridge-integration.sh` in a disposable CI workspace. It creates a database and
isolated characters, launches the server, then runs `TestJavaBridgeTradeAndRecovery`,
`TestJavaBridgeLearningPersistsAndAnAlreadyKnownSkillIsNotDone`,
`TestJavaBridgeReturnCompletesInTheParticipantAndWorld`, and
`TestJavaBridgeMovingActorSurvivesFreshDescriptions`. Every process and protocol wait is
bounded. Server, database, conventional-client and Go transcripts are retained under
`target/bridge-integration` as CI artifacts; no live secret is included.

The trade scenario checks authentication, initial inventory, target selection, dialogue, a real
purchase and sale, cause-free facts, transport loss, Resume, immutable retained receipts and the saved
inventory. The learning scenario checks the real trainer, exact SP charge, known-skill
observations, the absence of a second done for an already-known skill, and saved skill state.
The return scenario starts a dead character with the legacy watchdog disabled,
requires a live revival and a new ready participant checkpoint, and verifies the spawned
character through another real client and the committed position in PostgreSQL. The raw return
receipt remains sent; the participant dictionary alone proves done from live evidence.
The motion scenario starts a real movement, opens another character into its visibility, and
requires current movement after the actor description in the initial and two resumed checkpoints.

A fresh appeared observation replaces retained motion. Actor descriptions (including repeated
CharInfo/NpcInfo and speed changes) therefore emit current moved immediately afterwards while
the source creature is moving. A stopped creature emits no new moved. Checkpoints order actor
descriptions before retained current movement, so a fresh description cannot erase its destination.

Other deterministic Java tests cover malformed input, ordinary-client coexistence, queue and
projection bounds, queued versus started attempts, deadline orders, exact output accounting,
relocation generations, checkpoint interruption and shutdown disposal. These checks do not
claim support for every legacy panel or grant causal authority to arbitrary world producers.
