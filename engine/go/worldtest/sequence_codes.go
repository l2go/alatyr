// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldtest

// Fixed alatyr/1 codes. TestSequenceDictionaryCodes checks every name against
// meaning's dictionary; the oracle does not depend on the planner or Witness.
const (
	verbWalk = iota + 1
	verbAim
	verbStrike
	verbTake
	verbTalk
	verbChoose
	verbBuy
	verbSell
	verbRise
	verbFocus
	verbEngage
	verbCast
	verbLearn
	verbUseItem
)

const (
	kindAppeared = iota + 1
	kindMoved
	kindStopped
	kindCorrected
	kindStruck
	kindDied
	kindRevived
	kindDropped
	kindTook
	kindGone
	kindSpoke
	kindShop
	kindOffer
	kindCarried
	kindInventory
	kindVitals
	kindReward
	kindNotice
	kindHistory
	kindQuest
	kindQuests
	kindPractice
	kindKnown
	kindTargeted
)

// Program opcodes preserve the committed native corpus byte format.
const (
	opWalk = iota
	opAim
	opStrike
	opTake
	opTalk
	opChoose
	opBuy
	opSell
	opRise
	opFocus
	opEngage
	opCast
	opLearn
	opUseItem
	opAdvance
	opFocusEngage
	opReplay
	opWatch
	opAct
	opControl
	opResume
	opExpiredResume
	opCloseSocket
	opTeleport
	opHarmSelf
	opHarmTarget
	opRespawn
	opStopWorld
	opFault
	opSnapshot
	opHold
	opRelease
	opCount
)

const (
	faultLive = iota
	faultReceiptsOnly
	faultFactsOnly
	faultSnapshot
	faultHold
	faultCount
)
