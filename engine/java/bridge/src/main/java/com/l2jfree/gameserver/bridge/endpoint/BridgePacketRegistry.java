/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.l2jfree.gameserver.bridge.endpoint;

import java.util.Set;

import com.l2jfree.gameserver.network.packets.server.AbstractNpcInfo;
import com.l2jfree.gameserver.network.packets.server.AcquireSkillDone;
import com.l2jfree.gameserver.network.packets.server.AcquireSkillInfo;
import com.l2jfree.gameserver.network.packets.server.AcquireSkillList;
import com.l2jfree.gameserver.network.packets.server.ActionFailed;
import com.l2jfree.gameserver.network.packets.server.AllyCrest;
import com.l2jfree.gameserver.network.packets.server.AllyInfo;
import com.l2jfree.gameserver.network.packets.server.Attack;
import com.l2jfree.gameserver.network.packets.server.AutoAttackStart;
import com.l2jfree.gameserver.network.packets.server.AutoAttackStop;
import com.l2jfree.gameserver.network.packets.server.BuyList;
import com.l2jfree.gameserver.network.packets.server.CameraMode;
import com.l2jfree.gameserver.network.packets.server.ChangeMoveType;
import com.l2jfree.gameserver.network.packets.server.ChangeWaitType;
import com.l2jfree.gameserver.network.packets.server.CharInfo;
import com.l2jfree.gameserver.network.packets.server.ClientSetTime;
import com.l2jfree.gameserver.network.packets.server.CreatureSay;
import com.l2jfree.gameserver.network.packets.server.DeleteObject;
import com.l2jfree.gameserver.network.packets.server.Die;
import com.l2jfree.gameserver.network.packets.server.DropItem;
import com.l2jfree.gameserver.network.packets.server.Earthquake;
import com.l2jfree.gameserver.network.packets.server.EffectInfoPacket;
import com.l2jfree.gameserver.network.packets.server.EtcStatusUpdate;
import com.l2jfree.gameserver.network.packets.server.ExBasicActionList;
import com.l2jfree.gameserver.network.packets.server.ExBrExtraUserInfo;
import com.l2jfree.gameserver.network.packets.server.ExChangeNicknameNColor;
import com.l2jfree.gameserver.network.packets.server.ExGetBookMarkInfoPacket;
import com.l2jfree.gameserver.network.packets.server.ExNotifyBirthDay;
import com.l2jfree.gameserver.network.packets.server.ExPlayScene;
import com.l2jfree.gameserver.network.packets.server.ExPledgeCrestLarge;
import com.l2jfree.gameserver.network.packets.server.ExRedSky;
import com.l2jfree.gameserver.network.packets.server.ExSetCompassZoneCode;
import com.l2jfree.gameserver.network.packets.server.ExShowScreenMessage;
import com.l2jfree.gameserver.network.packets.server.ExShowSlideshowKamael;
import com.l2jfree.gameserver.network.packets.server.ExSpawnEmitter;
import com.l2jfree.gameserver.network.packets.server.ExStartScenePlayer;
import com.l2jfree.gameserver.network.packets.server.ExStorageMaxCount;
import com.l2jfree.gameserver.network.packets.server.ExVitalityPointInfo;
import com.l2jfree.gameserver.network.packets.server.FinishRotation;
import com.l2jfree.gameserver.network.packets.server.FriendAddRequest;
import com.l2jfree.gameserver.network.packets.server.FriendList;
import com.l2jfree.gameserver.network.packets.server.GetItem;
import com.l2jfree.gameserver.network.packets.server.HennaInfo;
import com.l2jfree.gameserver.network.packets.server.InventoryUpdate;
import com.l2jfree.gameserver.network.packets.server.ItemList;
import com.l2jfree.gameserver.network.packets.server.L2FriendSay;
import com.l2jfree.gameserver.network.packets.server.MagicEffectIcons;
import com.l2jfree.gameserver.network.packets.server.MagicSkillCanceled;
import com.l2jfree.gameserver.network.packets.server.MagicSkillLaunched;
import com.l2jfree.gameserver.network.packets.server.MagicSkillUse;
import com.l2jfree.gameserver.network.packets.server.MoveToLocation;
import com.l2jfree.gameserver.network.packets.server.MoveToPawn;
import com.l2jfree.gameserver.network.packets.server.MyTargetSelected;
import com.l2jfree.gameserver.network.packets.server.NicknameChanged;
import com.l2jfree.gameserver.network.packets.server.NormalCamera;
import com.l2jfree.gameserver.network.packets.server.NpcHtmlMessage;
import com.l2jfree.gameserver.network.packets.server.NpcInfoPoly;
import com.l2jfree.gameserver.network.packets.server.NpcLinkHtmlMessage;
import com.l2jfree.gameserver.network.packets.server.NpcQuestHtmlMessage;
import com.l2jfree.gameserver.network.packets.server.NpcSay;
import com.l2jfree.gameserver.network.packets.server.PetInfo;
import com.l2jfree.gameserver.network.packets.server.PetInventoryUpdate;
import com.l2jfree.gameserver.network.packets.server.PetItemList;
import com.l2jfree.gameserver.network.packets.server.PetStatusShow;
import com.l2jfree.gameserver.network.packets.server.PetStatusUpdate;
import com.l2jfree.gameserver.network.packets.server.PlaySound;
import com.l2jfree.gameserver.network.packets.server.PledgeCrest;
import com.l2jfree.gameserver.network.packets.server.PledgeInfo;
import com.l2jfree.gameserver.network.packets.server.PledgeShowInfoUpdate;
import com.l2jfree.gameserver.network.packets.server.PledgeShowMemberListAdd;
import com.l2jfree.gameserver.network.packets.server.PledgeShowMemberListAll;
import com.l2jfree.gameserver.network.packets.server.PledgeShowMemberListDelete;
import com.l2jfree.gameserver.network.packets.server.PledgeShowMemberListDeleteAll;
import com.l2jfree.gameserver.network.packets.server.PledgeShowMemberListUpdate;
import com.l2jfree.gameserver.network.packets.server.PledgeSkillList;
import com.l2jfree.gameserver.network.packets.server.PledgeSkillListAdd;
import com.l2jfree.gameserver.network.packets.server.PledgeStatusChanged;
import com.l2jfree.gameserver.network.packets.server.QuestList;
import com.l2jfree.gameserver.network.packets.server.RelationChanged;
import com.l2jfree.gameserver.network.packets.server.Revive;
import com.l2jfree.gameserver.network.packets.server.SSQInfo;
import com.l2jfree.gameserver.network.packets.server.SSQStatus;
import com.l2jfree.gameserver.network.packets.server.SellList;
import com.l2jfree.gameserver.network.packets.server.SendMacroList;
import com.l2jfree.gameserver.network.packets.server.ServerObjectInfo;
import com.l2jfree.gameserver.network.packets.server.SetSummonRemainTime;
import com.l2jfree.gameserver.network.packets.server.SetToLocation;
import com.l2jfree.gameserver.network.packets.server.SetupGauge;
import com.l2jfree.gameserver.network.packets.server.ShortBuffStatusUpdate;
import com.l2jfree.gameserver.network.packets.server.ShortCutInit;
import com.l2jfree.gameserver.network.packets.server.ShortCutRegister;
import com.l2jfree.gameserver.network.packets.server.SkillCoolTime;
import com.l2jfree.gameserver.network.packets.server.SkillList;
import com.l2jfree.gameserver.network.packets.server.SocialAction;
import com.l2jfree.gameserver.network.packets.server.SpawnItem;
import com.l2jfree.gameserver.network.packets.server.SpawnItemPoly;
import com.l2jfree.gameserver.network.packets.server.SpecialCamera;
import com.l2jfree.gameserver.network.packets.server.StartRotation;
import com.l2jfree.gameserver.network.packets.server.StatusUpdate;
import com.l2jfree.gameserver.network.packets.server.StopMove;
import com.l2jfree.gameserver.network.packets.server.StopRotation;
import com.l2jfree.gameserver.network.packets.server.SunRise;
import com.l2jfree.gameserver.network.packets.server.SunSet;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;
import com.l2jfree.gameserver.network.packets.server.TargetSelected;
import com.l2jfree.gameserver.network.packets.server.TargetUnselected;
import com.l2jfree.gameserver.network.packets.server.TeleportToLocation;
import com.l2jfree.gameserver.network.packets.server.TutorialCloseHtml;
import com.l2jfree.gameserver.network.packets.server.TutorialEnableClientEvent;
import com.l2jfree.gameserver.network.packets.server.TutorialShowQuestionMark;
import com.l2jfree.gameserver.network.packets.server.UserInfo;
import com.l2jfree.gameserver.network.packets.server.ValidateLocation;

/** Exact class identities form a closed profile; inheriting a known packet grants no permission. */
final class BridgePacketRegistry
{
	enum Kind { TRANSLATED, NONESSENTIAL, FATAL }

	private static final Set<Class<?>> TRANSLATED = Set.of(
			UserInfo.class,
			CharInfo.class,
			AbstractNpcInfo.NpcInfo.class,
			AbstractNpcInfo.SummonInfo.class,
			AbstractNpcInfo.TrapInfo.class,
			AbstractNpcInfo.PlayerMorphInfo.class,
			ServerObjectInfo.class,
			NpcInfoPoly.class,
			NpcLinkHtmlMessage.class,
			PetInfo.class,
			SpawnItem.class,
			SpawnItemPoly.class,
			DropItem.class,
			GetItem.class,
			DeleteObject.class,
			MoveToLocation.class,
			MoveToPawn.class,
			StopMove.class,
			ValidateLocation.class,
			SetToLocation.class,
			TeleportToLocation.class,
			Attack.class,
			Die.class,
			Revive.class,
			TargetSelected.class,
			MyTargetSelected.class,
			TargetUnselected.class,
			ItemList.class,
			InventoryUpdate.class,
			StatusUpdate.class,
			BuyList.class,
			SellList.class,
			AcquireSkillList.class,
			SkillList.class,
			QuestList.class,
			NpcHtmlMessage.class,
			NpcQuestHtmlMessage.class,
			NpcSay.class,
			CreatureSay.class,
			L2FriendSay.class,
			SystemMessage.class,
			ExShowScreenMessage.class,
			AutoAttackStart.class,
			AutoAttackStop.class,
			ChangeMoveType.class);

	private static final Set<Class<?>> NONESSENTIAL = Set.of(
			ActionFailed.class,
			AcquireSkillDone.class,
			AcquireSkillInfo.class,
			SkillCoolTime.class,
			MagicEffectIcons.class,
			EffectInfoPacket.class,
			EtcStatusUpdate.class,
			ShortBuffStatusUpdate.class,
			ShortCutInit.class,
			ShortCutRegister.class,
			SendMacroList.class,
			ExBasicActionList.class,
			HennaInfo.class,
			ExStorageMaxCount.class,
			ExVitalityPointInfo.class,
			ExBrExtraUserInfo.class,
			SSQInfo.class,
			SSQStatus.class,
			ClientSetTime.class,
			ExSetCompassZoneCode.class,
			FriendList.class,
			FriendAddRequest.class,
			PledgeShowMemberListAll.class,
			PledgeShowMemberListUpdate.class,
			PledgeShowMemberListAdd.class,
			PledgeShowMemberListDelete.class,
			PledgeShowMemberListDeleteAll.class,
			PledgeStatusChanged.class,
			PledgeSkillList.class,
			PledgeSkillListAdd.class,
			PledgeShowInfoUpdate.class,
			PledgeInfo.class,
			PledgeCrest.class,
			ExPledgeCrestLarge.class,
			AllyCrest.class,
			AllyInfo.class,
			TutorialShowQuestionMark.class,
			TutorialEnableClientEvent.class,
			TutorialCloseHtml.class,
			ExGetBookMarkInfoPacket.class,
			ExNotifyBirthDay.class,
			PlaySound.class,
			SocialAction.class,
			CameraMode.class,
			NormalCamera.class,
			SpecialCamera.class,
			Earthquake.class,
			ExRedSky.class,
			SunRise.class,
			SunSet.class,
			ExShowSlideshowKamael.class,
			ExPlayScene.class,
			ExStartScenePlayer.class,
			ExSpawnEmitter.class,
			StartRotation.class,
			StopRotation.class,
			FinishRotation.class,
			ChangeWaitType.class,
			SetupGauge.class,
			MagicSkillUse.class,
			MagicSkillLaunched.class,
			MagicSkillCanceled.class,
			RelationChanged.class,
			NicknameChanged.class,
			ExChangeNicknameNColor.class,
			PetItemList.class,
			PetInventoryUpdate.class,
			PetStatusShow.class,
			PetStatusUpdate.class,
			SetSummonRemainTime.class);

	private BridgePacketRegistry() {}

	static Kind classify(Class<?> packetClass)
	{
		if (TRANSLATED.contains(packetClass))
			return Kind.TRANSLATED;
		if (NONESSENTIAL.contains(packetClass))
			return Kind.NONESSENTIAL;
		return Kind.FATAL;
	}
}
