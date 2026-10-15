/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.l2jfree.gameserver.network.packets.client;

import com.l2jfree.Config;
import com.l2jfree.gameserver.Announcements;
import com.l2jfree.gameserver.CoreInfo;
import com.l2jfree.gameserver.cache.HtmCache;
import com.l2jfree.gameserver.communitybbs.Manager.RegionBBSManager;
import com.l2jfree.gameserver.communitybbs.Manager.RegionBBSManager.PlayerStateOnCommunity;
import com.l2jfree.gameserver.datatables.GmListTable;
import com.l2jfree.gameserver.datatables.SkillTable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.instance.L2ClassMasterInstance;
import com.l2jfree.gameserver.gameobjects.itemcontainer.Inventory;
import com.l2jfree.gameserver.handler.AdminCommandHandler;
import com.l2jfree.gameserver.instancemanager.CoupleManager;
import com.l2jfree.gameserver.instancemanager.CrownManager;
import com.l2jfree.gameserver.instancemanager.DimensionalRiftManager;
import com.l2jfree.gameserver.instancemanager.FortManager;
import com.l2jfree.gameserver.instancemanager.FortSiegeManager;
import com.l2jfree.gameserver.instancemanager.InstanceManager;
import com.l2jfree.gameserver.instancemanager.PetitionManager;
import com.l2jfree.gameserver.instancemanager.QuestManager;
import com.l2jfree.gameserver.instancemanager.SiegeManager;
import com.l2jfree.gameserver.model.L2ShortCut;
import com.l2jfree.gameserver.model.clan.L2Clan;
import com.l2jfree.gameserver.model.clan.L2ClanMember;
import com.l2jfree.gameserver.model.entity.Couple;
import com.l2jfree.gameserver.model.entity.Fort;
import com.l2jfree.gameserver.model.entity.FortSiege;
import com.l2jfree.gameserver.model.entity.Hero;
import com.l2jfree.gameserver.model.entity.Instance;
import com.l2jfree.gameserver.model.entity.Siege;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.mapregion.TeleportWhereType;
import com.l2jfree.gameserver.model.olympiad.Olympiad;
import com.l2jfree.gameserver.model.quest.Quest;
import com.l2jfree.gameserver.model.quest.QuestState;
import com.l2jfree.gameserver.model.restriction.ObjectRestrictions;
import com.l2jfree.gameserver.model.restriction.global.GlobalRestrictions;
import com.l2jfree.gameserver.model.sevensigns.SevenSigns;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.model.zone.L2Zone;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.server.Die;
import com.l2jfree.gameserver.network.packets.server.ExBasicActionList;
import com.l2jfree.gameserver.network.packets.server.ExGetBookMarkInfoPacket;
import com.l2jfree.gameserver.network.packets.server.ExNotifyBirthDay;
import com.l2jfree.gameserver.network.packets.server.ExStorageMaxCount;
import com.l2jfree.gameserver.network.packets.server.FriendList;
import com.l2jfree.gameserver.network.packets.server.HennaInfo;
import com.l2jfree.gameserver.network.packets.server.ItemList;
import com.l2jfree.gameserver.network.packets.server.NpcHtmlMessage;
import com.l2jfree.gameserver.network.packets.server.PledgeShowMemberListAll;
import com.l2jfree.gameserver.network.packets.server.PledgeShowMemberListUpdate;
import com.l2jfree.gameserver.network.packets.server.PledgeSkillList;
import com.l2jfree.gameserver.network.packets.server.PledgeStatusChanged;
import com.l2jfree.gameserver.network.packets.server.QuestList;
import com.l2jfree.gameserver.network.packets.server.SSQInfo;
import com.l2jfree.gameserver.network.packets.server.ShortCutInit;
import com.l2jfree.gameserver.network.packets.server.ShortCutRegister;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;
import com.l2jfree.gameserver.network.packets.server.TutorialShowQuestionMark;
import com.l2jfree.gameserver.network.packets.server.UserInfo;

public class EnterWorld extends L2ClientPacket
{
	private static final String _C__ENTERWORLD = "[C] 11 EnterWorld c[bddddbdcccccccccccccccccccc] (unk)";
	
	@Override
	protected void readImpl()
	{
		/* 16 leading and trailing null bytes and a block of bytes inside.
		 * The block seems not to change (same IP, diff chars/accounts)
		byte[] b = new byte[104];
		readB(b);
		_log.info("EW: " + HexUtil.printData(b));
		*/
		skip(104);
	}
	
	@Override
	protected void runImpl()
	{
		L2Player activeChar = getActiveChar();
		if (activeChar == null)
		{
			_log.warn("EnterWorld failed! activeChar is null...");
			getClient().closeNow();
			return;
		}
		
		getClient().initServerPacketQueue();
		
		if (Config.GM_EVERYBODY_HAS_ADMIN_RIGHTS && !(activeChar.isGM()))
			activeChar.setAccessLevel(200);
		
		// restore instance
		Instance ins = InstanceManager.getInstance().getDynamicInstance(activeChar);
		if (ins != null)
		{
			if (Config.RESTORE_PLAYER_INSTANCE)
				activeChar.setInstanceId(ins.getId());
			else
				ins.removePlayer(activeChar.getObjectId());
		}
		
		// Restore Vitality
		if (Config.ENABLE_VITALITY && Config.RECOVER_VITALITY_ON_RECONNECT)
		{
			float points =
					Config.RATE_RECOVERY_ON_RECONNECT * (System.currentTimeMillis() - activeChar.getLastAccess())
							/ 60000;
			if (points > 0)
				activeChar.updateVitalityPoints(points, false, true);
		}
		
		if (Config.PLAYER_SPAWN_PROTECTION > 0)
			activeChar.setProtection(true);
		activeChar.spawnMe(activeChar.getX(), activeChar.getY(), activeChar.getZ());
		
		activeChar.getKnownList().updateKnownObjects();
		
		sendPacket(new SSQInfo());
		sendPacket(new UserInfo(activeChar));
		sendPacket(new ItemList(activeChar, false));
		activeChar.getMacroses().sendUpdate();
		sendPacket(new ShortCutInit(activeChar));
		activeChar.sendSkillList();
		sendPacket(SystemMessageId.WELCOME_TO_LINEAGE);
		if (Config.SERVER_AGE_LIM >= 18 || Config.SERVER_PVP)
			sendPacket(SystemMessageId.ENTERED_ADULTS_ONLY_SERVER);
		else if (Config.SERVER_AGE_LIM >= 15)
			sendPacket(SystemMessageId.ENTERED_COMMON_SERVER);
		else
			sendPacket(SystemMessageId.ENTERED_JUVENILES_SERVER);
		sendPacket(new HennaInfo(activeChar));
		
		Announcements.getInstance().showAnnouncements(activeChar);
		SevenSigns.getInstance().sendCurrentPeriodMsg(activeChar);
		
		activeChar.updateNameTitleColor();
		
		if (activeChar.isGM())
		{
			if (Config.SHOW_GM_LOGIN)
				Announcements.getInstance().announceToAll("GM " + activeChar.getName() + " has logged on.");
			
			if (Config.GM_STARTUP_INVISIBLE)
				AdminCommandHandler.getInstance().useAdminCommand(activeChar, "admin_invisible");
			
			if (Config.GM_STARTUP_SILENCE)
				AdminCommandHandler.getInstance().useAdminCommand(activeChar, "admin_silence");
			
			if (Config.GM_STARTUP_INVULNERABLE)
				AdminCommandHandler.getInstance().useAdminCommand(activeChar, "admin_invul on");
			
			if (Config.GM_STARTUP_AUTO_LIST)
				GmListTable.addGm(activeChar, false);
			else
				GmListTable.addGm(activeChar, true);
		}
		
		Siege quickfix = SiegeManager.getInstance().getSiege(activeChar);
		if (quickfix != null && quickfix.getIsInProgress() && !quickfix.checkIsDefender(activeChar.getClan()))
		{
			if (activeChar.isInsideZone(L2Zone.FLAG_NO_HQ) // no such zones yet, so
					|| activeChar.isInsideZone(L2Zone.FLAG_CASTLE))
				activeChar.teleToLocation(TeleportWhereType.Town);
		}
		
		// send user info again .. just like the real client
		sendPacket(new UserInfo(activeChar));
		
		if (activeChar.getClanId() != 0 && activeChar.getClan() != null)
		{
			sendPacket(new PledgeShowMemberListAll(activeChar.getClan()));
			sendPacket(new PledgeStatusChanged(activeChar.getClan()));
			
			// Residential skills support
			activeChar.enableResidentialSkills(true);
		}
		
		if (activeChar.getStatus().getCurrentHp() < 0.5) // is dead
			activeChar.setIsDead(true);
		if (activeChar.isAlikeDead()) // dead or fake dead
			// no broadcast needed since the player will already spawn dead to others
			sendPacket(new Die(activeChar));
		
		// engage and notify Partner
		if (Config.ALLOW_WEDDING)
		{
			engage(activeChar);
			notifyPartner(activeChar);
			
			// Check if player is married and remove if necessary Cupid's Bow
			if (!activeChar.isMaried())
			{
				L2ItemInstance item = activeChar.getInventory().getItemByItemId(9140);
				// Remove Cupid's Bow
				if (item != null)
				{
					activeChar.destroyItem("Removing Cupid's Bow", item, activeChar, true);
					
					// No need to update every item in the inventory
					//activeChar.getInventory().updateDatabase();
					
					// Log it
					if (_log.isDebugEnabled())
						_log.debug("Character " + activeChar.getName() + " of account " + activeChar.getAccountName()
								+ " got Cupid's Bow removed.");
				}
			}
		}
		
		L2ItemInstance weapon = activeChar.getInventory().getPaperdollItem(Inventory.PAPERDOLL_LRHAND);
		if (weapon == null)
			weapon = activeChar.getInventory().getPaperdollItem(Inventory.PAPERDOLL_RHAND);
		if (weapon != null)
		{
			if ((weapon.isHeroItem() && !activeChar.isHero() && !activeChar.isGM())
					|| (activeChar.getPkKills() > 0 && weapon.getItemId() > 7815 && weapon.getItemId() < 7832))
				activeChar.getInventory().unEquipItemInBodySlotAndRecord(weapon.getItem().getBodyPart());
		}
		
		activeChar.updateEffectIcons();
		activeChar.sendSkillCoolTime();
		
		Quest.playerEnter(activeChar);
		loadTutorial(activeChar);
		for (Quest quest : QuestManager.getInstance().getAllManagedScripts())
		{
			if (quest != null && quest.getOnEnterWorld())
				quest.notifyEnterWorld(activeChar);
		}
		
		notifyFriends(activeChar);
		notifyClanMembers(activeChar);
		notifySponsorOrApprentice(activeChar);
		
		L2Clan clan = activeChar.getClan();
		if (clan != null)
			sendPacket(new PledgeSkillList(clan));
		
		sendPacket(new ExStorageMaxCount(activeChar));
		sendPacket(new QuestList(activeChar));
		
		activeChar.broadcastUserInfo();
		
		if (Olympiad.getInstance().playerInStadia(activeChar))
		{
			activeChar.doRevive();
			activeChar.teleToLocation(TeleportWhereType.Town);
			activeChar
					.sendMessage("You have been teleported to the nearest town due to you being in an Olympiad Stadium.");
		}
		
		activeChar.revalidateZone(true);
		activeChar.sendEtcStatusUpdate();
		
		if (DimensionalRiftManager.getInstance().checkIfInRiftZone(activeChar.getX(), activeChar.getY(),
				activeChar.getZ(), true)) // Exclude waiting room
			DimensionalRiftManager.getInstance().teleportToWaitingRoom(activeChar);
		
		// Wherever these should be?
		sendPacket(new ShortCutInit(activeChar));
		
		if (Hero.getInstance().getHeroes() != null
				&& Hero.getInstance().getHeroes().containsKey(activeChar.getObjectId()))
			activeChar.setHero(true);
		
		// Restore character's siege state
		if (activeChar.getClan() != null)
		{
			for (Siege siege : SiegeManager.getInstance().getSieges())
			{
				if (!siege.getIsInProgress())
					continue;
				if (siege.checkIsAttacker(activeChar.getClan()))
					activeChar.setSiegeState((byte)1);
				else if (siege.checkIsDefender(activeChar.getClan()))
					activeChar.setSiegeState((byte)2);
			}
			
			for (FortSiege fsiege : FortSiegeManager.getInstance().getSieges())
			{
				if (!fsiege.getIsInProgress())
					continue;
				if (fsiege.checkIsAttacker(activeChar.getClan()))
					activeChar.setSiegeState((byte)1);
				else if (fsiege.checkIsDefender(activeChar.getClan()))
					activeChar.setSiegeState((byte)2);
			}
		}
		
		//Updating Seal of Strife Buff/Debuff
		if (SevenSigns.getInstance().isSealValidationPeriod())
		{
			int owner = SevenSigns.getInstance().getSealOwner(SevenSigns.SEAL_STRIFE);
			if (owner != SevenSigns.CABAL_NULL)
			{
				int cabal = SevenSigns.getInstance().getPlayerCabal(activeChar);
				if (cabal == owner)
					activeChar.addSkill(SkillTable.getInstance().getInfo(5074, 1), false);
				else if (cabal != SevenSigns.CABAL_NULL)
					activeChar.addSkill(SkillTable.getInstance().getInfo(5075, 1), false);
			}
		}
		
		for (L2ItemInstance i : activeChar.getInventory().getItems())
			if (i.isTimeLimitedItem())
				i.scheduleLifeTimeTask();
		
		activeChar.queryGameGuard();
		
		sendPacket(new FriendList(activeChar));
		
		if (Config.SHOW_LICENSE)
			CoreInfo.versionInfo(activeChar);
		
		if (Config.SHOW_HTML_NEWBIE && activeChar.getLevel() < Config.LEVEL_HTML_NEWBIE)
		{
			String Newbie_Path = "data/html/newbie.htm";
			if (HtmCache.getInstance().pathExists(Newbie_Path))
			{
				NpcHtmlMessage html = new NpcHtmlMessage(1);
				html.setFile(Newbie_Path);
				html.replace("%name%", activeChar.getName()); // replaces %name%, so you can say like "welcome to the server %name%"
				sendPacket(html);
			}
		}
		else if (Config.SHOW_HTML_GM && activeChar.isGM())
		{
			String Gm_Path = "data/html/gm.htm";
			if (HtmCache.getInstance().pathExists(Gm_Path))
			{
				NpcHtmlMessage html = new NpcHtmlMessage(1);
				html.setFile(Gm_Path);
				html.replace("%name%", activeChar.getName()); // replaces %name%, so you can say like "welcome to the server %name%"
				sendPacket(html);
			}
		}
		else if (Config.SHOW_HTML_WELCOME)
		{
			String Welcome_Path = "data/html/welcome.htm";
			if (HtmCache.getInstance().pathExists(Welcome_Path))
			{
				NpcHtmlMessage html = new NpcHtmlMessage(1);
				html.setFile(Welcome_Path);
				html.replace("%name%", activeChar.getName()); // replaces %name%, so you can say like "welcome to the server %name%"
				sendPacket(html);
			}
		}
		
		// Resume paused restrictions
		ObjectRestrictions.getInstance().resumeTasks(activeChar.getObjectId());
		
		// check player skills
		activeChar.checkAllowedSkills();
		
		// check for academy
		activeChar.academyCheck(activeChar.getClassId().getId());
		
		// check for crowns
		CrownManager.checkCrowns(activeChar);
		
		if (Config.ONLINE_PLAYERS_AT_STARTUP)
		{
			SystemMessage sm = new SystemMessage(SystemMessageId.S1);
			if (L2World.getInstance().getAllPlayers().size() == 1)
				sm.addString("Player online: " + L2World.getInstance().getAllPlayers().size());
			else
				sm.addString("Players online: " + L2World.getInstance().getAllPlayers().size());
			sendPacket(sm);
		}
		
		PetitionManager.getInstance().checkPetitionMessages(activeChar);
		
		activeChar.onPlayerEnter();
		
		if (activeChar.getClanJoinExpiryTime() > System.currentTimeMillis())
			sendPacket(SystemMessageId.CLAN_MEMBERSHIP_TERMINATED);
		/*
				if (activeChar.getClan() != null)
				{
					// Add message if clanHall not paid. Possibly this is custom...
					ClanHall clanHall = ClanHallManager.getInstance().getClanHallByOwner(activeChar.getClan());
					if (clanHall != null && !clanHall.getPaid())
					{
						SystemMessage sm = new SystemMessage(SystemMessageId.MAKE_CLAN_HALL_PAYMENT_BY_S1_TOMORROW);
						sm.addNumber(???);
						sendPacket(sm);
					}
				}
		*/
		//Sets the appropriate Pledge Class for the clannie (e.g. Viscount, Count, Baron, Marquiz)
		activeChar.setPledgeClass(L2ClanMember.getCurrentPledgeClass(activeChar));
		
		L2ShortCut[] allShortCuts = activeChar.getAllShortCuts();
		for (L2ShortCut sc : allShortCuts)
			sendPacket(new ShortCutRegister(sc));
		
		// remove combat flag before teleporting
		L2ItemInstance flag = activeChar.getInventory().getItemByItemId(9819);
		if (flag != null)
		{
			Fort fort = FortManager.getInstance().getFort(activeChar);
			if (fort != null)
			{
				FortSiegeManager.getInstance().dropCombatFlag(activeChar);
			}
			else
			{
				int slot = flag.getItem().getBodyPart();
				activeChar.getInventory().unEquipItemInBodySlotAndRecord(slot);
				activeChar.destroyItem("CombatFlag", flag, null, true);
			}
		}
		if (!activeChar.isGM()
		// inside siege zone
				&& activeChar.isInsideZone(L2Zone.FLAG_SIEGE)
				// but non-participant or attacker
				&& (!activeChar.isInSiege() || activeChar.getSiegeState() < 2))
		{
			// Attacker or spectator logging in to a siege zone. Actually should be checked for inside castle only?
			activeChar.teleToLocation(TeleportWhereType.Town);
			//activeChar.sendMessage("You have been teleported to the nearest town due to you being in siege zone"); - custom
		}
		
		RegionBBSManager.changeCommunityBoard(activeChar, PlayerStateOnCommunity.NONE);
		
		if (!activeChar.isTransformed())
			activeChar.regiveTemporarySkills();
		
		// Send Teleport Bookmark List
		sendPacket(new ExGetBookMarkInfoPacket(activeChar));
		
		ExBasicActionList.sendTo(activeChar);
		
		int daysLeft = activeChar.canReceiveAnnualPresent();
		if (daysLeft < 8 && daysLeft != -1)
		{
			if (daysLeft == 0)
				sendPacket(ExNotifyBirthDay.PACKET);
			else
				sendPacket(new SystemMessage(SystemMessageId.S1_DAYS_UNTIL_BIRTHDAY).addNumber(daysLeft));
		}
		
		L2ClassMasterInstance.showQuestionMark(activeChar);
		
		if (activeChar.getLevel() == 28)
			sendPacket(new TutorialShowQuestionMark(1002));
		
		GlobalRestrictions.playerLoggedIn(activeChar);
	}
	
	/**
	 * @param activeChar
	 */
	private void engage(L2Player cha)
	{
		int _chaid = cha.getObjectId();
		
		for (Couple cl : CoupleManager.getInstance().getCouples())
		{
			if (cl.getPlayer1Id() == _chaid || cl.getPlayer2Id() == _chaid)
			{
				if (cl.getMaried())
					cha.setMaried(true);
				
				cha.setCoupleId(cl.getId());
				
				if (cl.getPlayer1Id() == _chaid)
					cha.setPartnerId(cl.getPlayer2Id());
				else
					cha.setPartnerId(cl.getPlayer1Id());
			}
		}
	}
	
	/**
	 * @param activeChar partnerid
	 */
	private void notifyPartner(L2Player cha)
	{
		if (cha.getPartnerId() != 0)
		{
			L2Player partner = L2World.getInstance().getPlayer(cha.getPartnerId());
			if (partner != null)
				partner.sendMessage("Your Partner " + cha.getName() + " has logged in.");
		}
	}
	
	/**
	 * @param activeChar
	 */
	private void notifyFriends(L2Player cha)
	{
		SystemMessage sm = new SystemMessage(SystemMessageId.FRIEND_S1_HAS_LOGGED_IN);
		sm.addPcName(cha);
		
		for (Integer objId : cha.getFriendList().getFriendIds())
		{
			L2Player friend = L2World.getInstance().findPlayer(objId);
			if (friend != null)
			{
				friend.sendPacket(new FriendList(friend));
				friend.sendPacket(sm);
			}
		}
	}
	
	/**
	 * @param activeChar
	 */
	private void notifyClanMembers(L2Player activeChar)
	{
		L2Clan clan = activeChar.getClan();
		if (clan != null)
		{
			L2ClanMember clanmember = clan.getClanMember(activeChar.getObjectId());
			if (clanmember != null)
			{
				clanmember.setPlayerInstance(activeChar);
				SystemMessage msg = new SystemMessage(SystemMessageId.CLAN_MEMBER_S1_LOGGED_IN);
				msg.addString(activeChar.getName());
				clan.broadcastToOtherOnlineMembers(msg, activeChar);
				clan.broadcastToOtherOnlineMembers(new PledgeShowMemberListUpdate(activeChar), activeChar);
				if (clan.isNoticeEnabled() && !clan.getNotice().isEmpty())
				{
					NpcHtmlMessage notice = new NpcHtmlMessage(clan.getClanId());
					notice.setFile("data/html/clanNotice.htm");
					notice.replace("%clan_name%", clan.getName());
					// perhaps <br> is in retail?
					notice.replace("%notice_text%", clan.getNotice().replaceAll("\r\n", "<br1>"));
					activeChar.sendPacket(notice);
				}
			}
		}
	}
	
	/**
	 * @param activeChar
	 */
	private void notifySponsorOrApprentice(L2Player activeChar)
	{
		if (activeChar.getSponsor() != 0)
		{
			L2Player sponsor = L2World.getInstance().getPlayer(activeChar.getSponsor());
			
			if (sponsor != null)
			{
				SystemMessage msg = new SystemMessage(SystemMessageId.YOUR_APPRENTICE_S1_HAS_LOGGED_IN);
				msg.addString(activeChar.getName());
				sponsor.sendPacket(msg);
			}
		}
		else if (activeChar.getApprentice() != 0)
		{
			L2Player apprentice = L2World.getInstance().getPlayer(activeChar.getApprentice());
			
			if (apprentice != null)
			{
				SystemMessage msg = new SystemMessage(SystemMessageId.YOUR_SPONSOR_C1_HAS_LOGGED_IN);
				msg.addString(activeChar.getName());
				apprentice.sendPacket(msg);
			}
		}
	}
	
	private void loadTutorial(L2Player player)
	{
		QuestState qs = player.getQuestState("255_Tutorial");
		if (qs != null)
			qs.getQuest().notifyEvent("UC", null, player);
	}
	
	@Override
	public String getType()
	{
		return _C__ENTERWORLD;
	}
}
