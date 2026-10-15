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
package com.l2jfree.gameserver.model.clan;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.Config;
import com.l2jfree.gameserver.communitybbs.Manager.ForumsBBSManager;
import com.l2jfree.gameserver.communitybbs.bb.Forum;
import com.l2jfree.gameserver.datatables.ClanTable;
import com.l2jfree.gameserver.datatables.SkillTable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.L2Player.TimeStamp;
import com.l2jfree.gameserver.gameobjects.itemcontainer.ClanWarehouse;
import com.l2jfree.gameserver.instancemanager.CastleManager;
import com.l2jfree.gameserver.instancemanager.CrownManager;
import com.l2jfree.gameserver.instancemanager.SiegeManager;
import com.l2jfree.gameserver.model.BlockList;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;
import com.l2jfree.gameserver.network.packets.server.CreatureSay;
import com.l2jfree.gameserver.network.packets.server.ItemList;
import com.l2jfree.gameserver.network.packets.server.PledgeReceiveSubPledgeCreated;
import com.l2jfree.gameserver.network.packets.server.PledgeShowInfoUpdate;
import com.l2jfree.gameserver.network.packets.server.PledgeShowMemberListAll;
import com.l2jfree.gameserver.network.packets.server.PledgeShowMemberListDeleteAll;
import com.l2jfree.gameserver.network.packets.server.PledgeShowMemberListUpdate;
import com.l2jfree.gameserver.network.packets.server.PledgeSkillListAdd;
import com.l2jfree.gameserver.network.packets.server.StatusUpdate;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;
import com.l2jfree.gameserver.network.packets.server.UserInfo;
import com.l2jfree.gameserver.persistence.WorldTransaction;
import com.l2jfree.gameserver.persistence.clan.ClanRepository;
import com.l2jfree.gameserver.persistence.clan.ClanRepository.ClanRecord;
import com.l2jfree.gameserver.persistence.clan.ClanRepository.MemberRecord;
import com.l2jfree.gameserver.persistence.clan.ClanRepository.RankPrivilegeRecord;
import com.l2jfree.gameserver.persistence.clan.ClanRepository.SkillRecord;
import com.l2jfree.gameserver.persistence.clan.ClanRepository.SubPledgeRecord;
import com.l2jfree.util.ArrayBunch;

/**
 * This class ...
 * 
 * @version $Revision: 1.7.2.4.2.7 $ $Date: 2005/04/06 16:13:41 $
 */
public class L2Clan
{
	private static final Logger _log = LoggerFactory.getLogger(L2Clan.class);
	/** The notice of a new clan. */
	private static final String NEW_CLAN_NOTICE = "Change me";
	private String _name;
	private int _clanId;
	private L2ClanMember _leader;
	private final Map<Integer, L2ClanMember> _members = new LinkedHashMap<Integer, L2ClanMember>();
	
	private String _allyName;
	private int _allyId;
	private int _level;
	private int _hasCastle;
	private int _hasHideout;
	private int _hasFort;
	private boolean _hasCrest;
	private int _hiredGuards;
	private int _crestId;
	private int _crestLargeId;
	private int _allyCrestId;
	private int _auctionBiddedAt = 0;
	
	private long _allyPenaltyExpiryTime;
	private int _allyPenaltyType;
	private long _charPenaltyExpiryTime;
	private long _dissolvingExpiryTime;
	// Ally Penalty Types
	/** Clan leaved ally */
	public static final int PENALTY_TYPE_CLAN_LEAVED = 1;
	/** Clan was dismissed from ally */
	public static final int PENALTY_TYPE_CLAN_DISMISSED = 2;
	/** Leader clan dismiss clan from ally */
	public static final int PENALTY_TYPE_DISMISS_CLAN = 3;
	/** Leader clan dissolve ally */
	public static final int PENALTY_TYPE_DISSOLVE_ALLY = 4;
	
	private final ClanWarehouse _warehouse = new ClanWarehouse(this);
	private final List<Integer> _atWarWith = new ArrayList<Integer>();
	private final List<Integer> _atWarAttackers = new ArrayList<Integer>();
	
	private boolean _hasCrestLarge;
	
	private Forum _forum;
	
	private final List<L2Skill> _skillList = new ArrayList<L2Skill>();
	
	//  Clan Privileges
	public static final int CP_NOTHING = 0;
	public static final int CP_CL_JOIN_CLAN = 2; // Join clan
	public static final int CP_CL_GIVE_TITLE = 4; // Give a title
	public static final int CP_CL_VIEW_WAREHOUSE = 8; // View warehouse content
	public static final int CP_CL_MANAGE_RANKS = 16; // Manage clan ranks
	public static final int CP_CL_PLEDGE_WAR = 32;
	public static final int CP_CL_DISMISS = 64;
	public static final int CP_CL_REGISTER_CREST = 128; // Register clan crest
	public static final int CP_CL_APPRENTICE = 256;
	public static final int CP_CL_TROOPS_FAME = 512;
	public static final int CP_CL_SUMMON_AIRSHIP = 1024;
	public static final int CP_CH_OPEN_DOOR = 2048; // open a door
	public static final int CP_CH_OTHER_RIGHTS = 4096;
	public static final int CP_CH_AUCTION = 8192;
	public static final int CP_CH_DISMISS = 16384;
	public static final int CP_CH_SET_FUNCTIONS = 32768;
	public static final int CP_CS_OPEN_DOOR = 65536;
	public static final int CP_CS_MANOR_ADMIN = 131072;
	public static final int CP_CS_MANAGE_SIEGE = 262144;
	public static final int CP_CS_USE_FUNCTIONS = 524288;
	public static final int CP_CS_DISMISS = 1048576;
	public static final int CP_CS_TAXES = 2097152;
	public static final int CP_CS_MERCENARIES = 4194304;
	public static final int CP_CS_SET_FUNCTIONS = 8388608;
	public static final int CP_ALL = 16777214;
	
	// Sub-unit types
	public static final int SUBUNIT_ACADEMY = -1;
	public static final int SUBUNIT_NONE = 0;
	public static final int SUBUNIT_ROYAL1 = 100;
	public static final int SUBUNIT_ROYAL2 = 200;
	public static final int SUBUNIT_KNIGHT1 = 1001;
	public static final int SUBUNIT_KNIGHT2 = 1002;
	public static final int SUBUNIT_KNIGHT3 = 2001;
	public static final int SUBUNIT_KNIGHT4 = 2002;
	
	// Player ranks
	public static final int RANK_VAGABOND = 0;
	public static final int RANK_VASSAL = 1;
	public static final int RANK_HEIR = 2;
	public static final int RANK_KNIGHT = 3;
	public static final int RANK_ELDER = 4;
	public static final int RANK_BARON = 5;
	public static final int RANK_VISCOUNT = 6;
	public static final int RANK_COUNT = 7;
	public static final int RANK_MARQUIS = 8;
	public static final int RANK_DUKE = 9;
	public static final int RANK_GRAND_DUKE = 10;
	public static final int RANK_DISTINGUISHED_KING = 11;
	
	/** Map(Integer, L2Skill) containing all skills of the L2Clan */
	protected final Map<Integer, L2Skill> _skills = new LinkedHashMap<Integer, L2Skill>();
	protected final Map<Integer, RankPrivs> _privs = new LinkedHashMap<Integer, RankPrivs>();
	protected final Map<Integer, SubPledge> _subPledges = new LinkedHashMap<Integer, SubPledge>();
	
	private int _reputationScore = 0;
	private int _rank = 0;
	
	private String _notice;
	
	//	private boolean							_noticeEnabled				= true;
	
	/**
	 * Called if a clan is referenced only by id.
	 * In this case all other data needs to be fetched from db
	 *
	 * @param clanId A valid clan Id to create and restore
	 */
	public L2Clan(int clanId)
	{
		_clanId = clanId;
		initializePrivs();
		restore();
		getWarehouse().restore();
	}
	
	/**
	 * Called only if a new clan is created
	 *
	 * @param clanId  A valid clan Id to create
	 * @param clanName  A valid clan name
	 */
	public L2Clan(int clanId, String clanName)
	{
		_clanId = clanId;
		_name = clanName;
		initializePrivs();
		// The notice of a new clan is inserted with the clan itself, see store().
	}
	
	/** Adds the (blank) notice of the clan unless it has one. */
	public void insertNotice()
	{
		try
		{
			ClanRepository.getInstance().insertNotice(getClanId(), NEW_CLAN_NOTICE, false);
		}
		catch (Exception e)
		{
			_log.warn("BBS: Error while creating clan notice for clan " + getClanId(), e);
		}
	}
	
	/**
	 * @return Returns the clan notice.
	 */
	public String getNotice()
	{
		try
		{
			String notice = ClanRepository.getInstance().loadNotice(getClanId());
			if (notice != null)
				_notice = notice;
		}
		catch (Exception e)
		{
			_log.warn("BBS: Error while getting notice from DB for clan " + getClanId(), e);
		}
		
		return _notice;
	}
	
	public String getNoticeForBBS()
	{
		String notice = "";
		try
		{
			String stored = ClanRepository.getInstance().loadNotice(getClanId());
			if (stored != null)
				notice = stored;
		}
		catch (Exception e)
		{
			_log.warn("BBS: Error while getting notice from DB for clan " + getClanId(), e);
		}
		return notice.replaceAll("<br>", "\n");
	}
	
	/**
	 * @param notice The new clan notice.
	 */
	public void setNotice(String notice)
	{
		
		notice = notice.replaceAll("\n", "<br>");
		
		try
		{
			ClanRepository.getInstance().updateNotice(getClanId(), notice);
			
			_notice = notice;
		}
		catch (Exception e)
		{
			_log.warn("BBS: Error while saving notice for clan " + getClanId(), e);
		}
	}
	
	/**
	 * @return Returns the noticeEnabled.
	 */
	public boolean isNoticeEnabled()
	{
		Boolean result = null;
		try
		{
			result = ClanRepository.getInstance().loadNoticeEnabled(getClanId());
		}
		catch (Exception e)
		{
			_log.warn("BBS: Error while reading _noticeEnabled for clan " + getClanId(), e);
		}
		if (result == null)
		{
			insertNotice();
			return false;
		}
		else
			return result.booleanValue();
	}
	
	/**
	 * @param noticeEnabled The noticeEnabled to set.
	 */
	public void setNoticeEnabled(boolean noticeEnabled)
	{
		try
		{
			ClanRepository.getInstance().updateNoticeEnabled(getClanId(), noticeEnabled);
		}
		catch (Exception e)
		{
			_log.warn("BBS: Error while updating notice status for clan " + getClanId(), e);
		}
		
		//		_noticeEnabled = noticeEnabled;
		
	}
	
	/**
	 * @return Returns the clanId.
	 */
	public int getClanId()
	{
		return _clanId;
	}
	
	/**
	 * @param clanId The clanId to set.
	 */
	public void setClanId(int clanId)
	{
		_clanId = clanId;
	}
	
	/**
	 * @return Returns the leaderId.
	 */
	public int getLeaderId()
	{
		return (_leader != null ? _leader.getObjectId() : 0);
	}
	
	/**
	 * @return L2ClanMember of clan leader.
	 */
	public L2ClanMember getLeader()
	{
		return _leader;
	}
	
	/**
	 * @param leader The leaderId to set.
	 */
	public void setLeader(L2ClanMember leader)
	{
		_leader = leader;
		_members.put(leader.getObjectId(), leader);
	}
	
	public void setNewLeader(L2ClanMember member)
	{
		if (!getLeader().isOnline())
		{
			return;
		}
		if (member == null)
		{
			return;
		}
		if (!member.isOnline())
		{
			return;
		}
		
		L2Player exLeader = getLeader().getPlayerInstance();
		SiegeManager.getInstance().removeSiegeSkills(exLeader);
		exLeader.setClan(this);
		exLeader.setClanPrivileges(L2Clan.CP_NOTHING);
		exLeader.broadcastUserInfo();
		
		setLeader(member);
		updateClanInDB();
		
		exLeader.setPledgeClass(L2ClanMember.getCurrentPledgeClass(exLeader));
		exLeader.broadcastUserInfo();
		exLeader.checkItemRestriction();
		L2Player newLeader = member.getPlayerInstance();
		newLeader.setClan(this);
		newLeader.setPledgeClass(L2ClanMember.getCurrentPledgeClass(newLeader));
		newLeader.setClanPrivileges(L2Clan.CP_ALL);
		if (getLevel() >= Config.SIEGE_CLAN_MIN_LEVEL)
		{
			SiegeManager.getInstance().addSiegeSkills(newLeader);
			
			// Transferring siege skills TimeStamps from old leader to new leader to prevent unlimited headquarters
			for (L2Skill sk : SkillTable.getInstance().getSiegeSkills(newLeader.isNoble()))
			{
				TimeStamp ts = exLeader.getReuseTimeStamps().get(sk.getId());
				if (ts != null)
					newLeader.disableSkill(ts);
			}
		}
		newLeader.broadcastUserInfo();
		
		broadcastClanStatus();
		
		SystemMessage sm = new SystemMessage(SystemMessageId.CLAN_LEADER_PRIVILEGES_HAVE_BEEN_TRANSFERRED_TO_C1);
		sm.addString(newLeader.getName());
		broadcastToOnlineMembers(sm);
		sm = null;
		
		CrownManager.checkCrowns(exLeader);
		CrownManager.checkCrowns(newLeader);
	}
	
	/**
	 * @return Returns the leaderName.
	 */
	public String getLeaderName()
	{
		return _leader == null ? "None" : _leader.getName();
	}
	
	/**
	 * @return Returns the name.
	 */
	public String getName()
	{
		return _name;
	}
	
	/**
	 * @param name The name to set.
	 */
	public void setName(String name)
	{
		_name = name;
	}
	
	private void addClanMember(L2ClanMember member)
	{
		_members.put(member.getObjectId(), member);
	}
	
	public void addClanMember(L2Player player)
	{
		// Using a different constructor, to make it easier to read
		// L2ClanMember(L2Clan, L2Player)
		//L2ClanMember member = new L2ClanMember(this,player.getName(), player.getLevel(), player.getClassId().getId(), player.getObjectId(), player.getSubPledgeType(), player.getPledgeRank(), player.getTitle(), player.getAppearance().getSex() ? 1 : 0, player.getRace().ordinal());
		L2ClanMember member = new L2ClanMember(this, player);
		// store in memory
		addClanMember(member);
		member.setPlayerInstance(player);
		player.setClan(this);
		player.setPledgeClass(L2ClanMember.getCurrentPledgeClass(player));
		player.sendPacket(new PledgeShowMemberListUpdate(player));
		
		//player.sendPacket(new UserInfo(player));
		// Crest update
		player.broadcastUserInfo();
	}
	
	public void updateClanMember(L2Player player)
	{
		L2ClanMember member = new L2ClanMember(player);
		if (player.isClanLeader())
			setLeader(member);
		
		addClanMember(member);
	}
	
	public L2ClanMember getClanMember(String name)
	{
		String tmp = name.toLowerCase();
		for (L2ClanMember temp : _members.values())
		{
			if (temp.getName().toLowerCase().equals(tmp))
				return temp;
		}
		return null;
	}
	
	public L2ClanMember getClanMember(int objectID)
	{
		return _members.get(objectID);
	}
	
	public void removeClanMember(int objectId, long clanJoinExpiryTime)
	{
		L2ClanMember exMember = _members.remove(objectId);
		if (exMember == null)
		{
			_log.warn("Member Object ID: " + objectId + " not found in clan while trying to remove");
			return;
		}
		int leadssubpledge = getLeaderSubPledge(objectId);
		if (leadssubpledge != 0)
		{
			// Sub-unit leader withdraws, position becomes vacant and leader
			// should appoint new via NPC
			getSubPledge(leadssubpledge).setLeaderId(0);
			updateSubPledgeInDB(leadssubpledge);
		}
		
		if (exMember.getApprentice() != 0)
		{
			L2ClanMember apprentice = getClanMember(exMember.getApprentice());
			if (apprentice != null)
			{
				if (apprentice.getPlayerInstance() != null)
					apprentice.getPlayerInstance().setSponsor(0);
				else
					apprentice.initApprenticeAndSponsor(0, 0);
				
				apprentice.saveApprenticeAndSponsor(0, 0);
			}
		}
		if (exMember.getSponsor() != 0)
		{
			L2ClanMember sponsor = getClanMember(exMember.getSponsor());
			if (sponsor != null)
			{
				if (sponsor.getPlayerInstance() != null)
					sponsor.getPlayerInstance().setApprentice(0);
				else
					sponsor.initApprenticeAndSponsor(0, 0);
				
				sponsor.saveApprenticeAndSponsor(0, 0);
			}
		}
		exMember.saveApprenticeAndSponsor(0, 0);
		
		if (Config.ALT_REMOVE_CASTLE_CIRCLETS)
		{
			CastleManager.getInstance().removeCirclet(exMember, getHasCastle());
		}
		
		if (exMember.isOnline())
		{
			L2Player player = exMember.getPlayerInstance();
			player.setTitle("");
			player.setApprentice(0);
			player.setSponsor(0);
			
			if (player.isClanLeader())
			{
				SiegeManager.getInstance().removeSiegeSkills(player);
				player.setClanCreateExpiryTime(System.currentTimeMillis() + Config.ALT_CLAN_CREATE_DAYS * 86400000L); //24*60*60*1000 = 86400000
			}
			
			// remove Clanskills from Player
			for (L2Skill skill : getAllSkills())
				player.removeSkill(skill, false);
			
			// remove Residential skills
			player.enableResidentialSkills(false);
			
			// players leaving from clan academy have no penalty
			if (exMember.getSubPledgeType() != -1)
				player.setClanJoinExpiryTime(clanJoinExpiryTime);
			
			player.setClan(null);
			player.setPledgeClass(L2ClanMember.getCurrentPledgeClass(player));
			
			player.updateNameTitleColor();
			// disable clan tab
			player.sendPacket(new PledgeShowMemberListDeleteAll());
		}
		else
		{
			removeMemberInDatabase(exMember, clanJoinExpiryTime, getLeaderId() == objectId ? System.currentTimeMillis()
					+ Config.ALT_CLAN_CREATE_DAYS * 86400000L : 0);
		}
	}
	
	public L2ClanMember[] getMembers()
	{
		return _members.values().toArray(new L2ClanMember[_members.size()]);
	}
	
	public int getMembersCount()
	{
		return _members.size();
	}
	
	public int getSubPledgeMembersCount(int subpl)
	{
		int result = 0;
		for (L2ClanMember temp : _members.values())
		{
			if (temp.getSubPledgeType() == subpl)
				result++;
		}
		return result;
	}
	
	public int getMaxNrOfMembers(int subpledgetype)
	{
		int limit = 0;
		
		switch (subpledgetype)
		{
			case 0:
				switch (getLevel())
				{
					case 4:
						limit = 40;
						break;
					case 3:
						limit = 30;
						break;
					case 2:
						limit = 20;
						break;
					case 1:
						limit = 15;
						break;
					case 0:
						limit = 10;
						break;
					default:
						limit = 40;
						break;
				}
				break;
			case -1:
			case 100:
			case 200:
				limit = 20;
				break;
			case 1001:
			case 1002:
			case 2001:
			case 2002:
				switch (getLevel())
				{
					case 9:
					case 10:
						limit = 25;
						break;
					default:
						limit = 10;
						break;
				}
				break;
			default:
				break;
		}
		
		return limit;
	}
	
	public List<L2Player> getOnlineMembersList()
	{
		List<L2Player> result = new ArrayList<L2Player>();
		for (L2ClanMember temp : _members.values())
		{
			if (temp != null)
			{
				if (temp.isOnline() && temp.getPlayerInstance() != null)
					result.add(temp.getPlayerInstance());
			}
		}
		
		return result;
	}
	
	public L2Player[] getOnlineMembers(int exclude)
	{
		ArrayBunch<L2Player> result = new ArrayBunch<L2Player>();
		for (L2ClanMember temp : _members.values())
		{
			if (temp != null)
			{
				if (temp.isOnline() && temp.getObjectId() != exclude)
					result.add(temp.getPlayerInstance());
			}
		}
		
		return result.moveToArray(new L2Player[result.size()]);
	}
	
	/**
	 * @return
	 */
	public int getAllyId()
	{
		return _allyId;
	}
	
	/**
	 * @return
	 */
	public String getAllyName()
	{
		return _allyName;
	}
	
	public void setAllyCrestId(int allyCrestId)
	{
		_allyCrestId = allyCrestId;
	}
	
	/**
	 * @return
	 */
	public int getAllyCrestId()
	{
		return _allyCrestId;
	}
	
	/**
	 * @return
	 */
	public int getLevel()
	{
		return _level;
	}
	
	/**
	 * @return
	 */
	public int getHasCastle()
	{
		return _hasCastle;
	}
	
	/**
	 * @return
	 */
	public int getHasHideout()
	{
		return _hasHideout;
	}
	
	/**
	 * @return
	 */
	public int getHasFort()
	{
		return _hasFort;
	}
	
	/**
	 * @param crestId The id of pledge crest.
	 */
	public void setCrestId(int crestId)
	{
		_crestId = crestId;
	}
	
	/**
	 * @return Returns the clanCrestId.
	 */
	public int getCrestId()
	{
		return _crestId;
	}
	
	/**
	 * @param crestLargeId The id of pledge LargeCrest.
	 */
	public void setCrestLargeId(int crestLargeId)
	{
		_crestLargeId = crestLargeId;
	}
	
	/**
	 * @return Returns the clan CrestLargeId
	 */
	public int getCrestLargeId()
	{
		return _crestLargeId;
	}
	
	/**
	 * @param allyId The allyId to set.
	 */
	public void setAllyId(int allyId)
	{
		_allyId = allyId;
	}
	
	/**
	 * @param allyName The allyName to set.
	 */
	public void setAllyName(String allyName)
	{
		_allyName = allyName;
	}
	
	/**
	 * @param hasCastle The hasCastle to set.
	 */
	public void setHasCastle(int hasCastle)
	{
		_hasCastle = hasCastle;
	}
	
	/**
	 * @param hasHideout The hasHideout to set.
	 */
	public void setHasHideout(int hasHideout)
	{
		_hasHideout = hasHideout;
	}
	
	/**
	 * @param hasFort Fortress The hasFortress to set.
	 */
	public void setHasFort(int hasFort)
	{
		_hasFort = hasFort;
	}
	
	/**
	 * @param level The level to set.
	 */
	public void setLevel(int level)
	{
		_level = level;
		if (_level >= 2 && _forum == null && Config.COMMUNITY_TYPE > 0)
		{
			Forum forum = ForumsBBSManager.getInstance().getForumByName("ClanRoot");
			
			if (forum != null)
			{
				_forum = forum.getChildByName(_name);
				
				if (_forum == null)
				{
					_forum =
							ForumsBBSManager.getInstance().createNewForum(_name,
									ForumsBBSManager.getInstance().getForumByName("ClanRoot"), Forum.CLAN,
									Forum.CLANMEMBERONLY, getClanId());
				}
			}
		}
	}
	
	/**
	 * @param id name
	 * @return
	 */
	public boolean isMember(int id)
	{
		return (id != 0 && _members.containsKey(id));
	}
	
	/** The clan as the database stores it. */
	private ClanRecord toRecord()
	{
		return new ClanRecord(getClanId(), getName(), getLevel(), getHasCastle(), getAllyId(), getAllyName(),
				getLeaderId(), getCrestId(), getCrestLargeId(), getAllyCrestId(), getReputationScore(),
				getAuctionBiddedAt(), getAllyPenaltyExpiryTime(), getAllyPenaltyType(), getCharPenaltyExpiryTime(),
				getDissolvingExpiryTime());
	}
	
	public void updateClanInDB()
	{
		try
		{
			ClanRepository.getInstance().updateClan(toRecord());
			if (_log.isDebugEnabled())
				_log.info("New clan leader saved in db: " + getClanId());
		}
		catch (Exception e)
		{
			_log.error("Error while saving new clan leader.", e);
		}
	}
	
	/**
	 * Stores a new clan and its notice in one transaction.
	 *
	 * @return true if the clan was stored
	 */
	public boolean store()
	{
		boolean stored = ClanRepository.getInstance().createClan(toRecord(), NEW_CLAN_NOTICE);
		if (stored && _log.isDebugEnabled())
			_log.info("New clan saved in db: " + getClanId());
		else if (!stored)
			_log.error("Error saving new clan.");
		return stored;
	}
	
	private void removeMemberInDatabase(L2ClanMember member, long clanJoinExpiryTime, long clanCreateExpiryTime)
	{
		if (ClanRepository.getInstance().removeMember(member.getObjectId(), clanJoinExpiryTime, clanCreateExpiryTime))
		{
			if (_log.isDebugEnabled())
				_log.info("clan member removed in db: " + getClanId());
		}
		else
			_log.error("Error removing clan member.");
	}
	
	private void restore()
	{
		//restorewars();
		try
		{
			ClanRepository repository = ClanRepository.getInstance();
			L2ClanMember member;
			
			ClanRecord clanData = repository.loadClan(getClanId());
			
			if (clanData != null)
			{
				setName(clanData.name());
				setLevel(clanData.level());
				setHasCastle(clanData.castleId());
				setAllyId(clanData.allianceId());
				setAllyName(clanData.allianceName());
				setAllyPenaltyExpiryTime(clanData.allyPenaltyExpiryTime(), clanData.allyPenaltyType());
				if (getAllyPenaltyExpiryTime() < System.currentTimeMillis())
				{
					setAllyPenaltyExpiryTime(0, 0);
				}
				setCharPenaltyExpiryTime(clanData.memberPenaltyExpiryTime());
				if (getCharPenaltyExpiryTime() + Config.ALT_CLAN_JOIN_DAYS * 86400000L < System.currentTimeMillis()) //24*60*60*1000 = 86400000
				{
					setCharPenaltyExpiryTime(0);
				}
				setDissolvingExpiryTime(clanData.dissolveTime());
				
				setCrestId(clanData.crestId());
				if (getCrestId() != 0)
				{
					setHasCrest(true);
				}
				
				setCrestLargeId(clanData.largeCrestId());
				if (getCrestLargeId() != 0)
				{
					setHasCrestLarge(true);
				}
				
				setAllyCrestId(clanData.allianceCrestId());
				setReputationScore(clanData.reputationScore(), false);
				setAuctionBiddedAt(clanData.auctionBidAt(), false);
				
				int leaderId = clanData.leaderId();
				
				for (MemberRecord clanMember : repository.loadMembers(getClanId()))
				{
					member =
							new L2ClanMember(this, clanMember.name(), clanMember.level(), clanMember.classId(),
									clanMember.objectId(), clanMember.pledgeType(), clanMember.pledgeRank(),
									clanMember.title(), clanMember.sex(), clanMember.race());
					if (member.getObjectId() == leaderId)
						setLeader(member);
					else
						addClanMember(member);
					member.initApprenticeAndSponsor(clanMember.apprenticeId(), clanMember.sponsorId());
				}
			}
			
			if (getName() != null && _log.isDebugEnabled())
				_log.info("Restored clan data for \"" + getName() + "\" from database.");
			restoreSubPledges();
			restoreRankPrivs();
			restoreSkills();
		}
		catch (Exception e)
		{
			_log.error("Error restoring clan data.", e);
			_log.warn(String.valueOf(getClanId()), e);
		}
	}
	
	private void restoreSkills()
	{
		try
		{
			// Retrieve all skills of this L2Player from the database
			for (SkillRecord record : ClanRepository.getInstance().loadSkills(getClanId()))
			{
				// Create a L2Skill object for each record
				L2Skill skill = SkillTable.getInstance().getInfo(record.skillId(), record.level());
				// Add the L2Skill object to the L2Clan _skills
				_skills.put(skill.getId(), skill);
			}
		}
		catch (Exception e)
		{
			_log.error("Error restoring clan skills.", e);
		}
	}
	
	/** used to retrieve all skills */
	public final L2Skill[] getAllSkills()
	{
		if (_skills == null)
			return new L2Skill[0];
		
		return _skills.values().toArray(new L2Skill[_skills.values().size()]);
	}
	
	/** used to add a skill to skill list of this L2Clan */
	public L2Skill addSkill(L2Skill newSkill)
	{
		L2Skill oldSkill = null;
		
		if (newSkill != null)
		{
			// Replace oldSkill by newSkill or Add the newSkill
			oldSkill = addSkill(newSkill);
		}
		
		return oldSkill;
	}
	
	/** used to add a new skill to the list, send a packet to all online clan members, update their stats and store it in db*/
	public L2Skill addNewSkill(L2Skill newSkill)
	{
		L2Skill oldSkill = null;
		
		if (newSkill != null)
		{
			
			// Replace oldSkill by newSkill or Add the newSkill
			oldSkill = _skills.put(newSkill.getId(), newSkill);
			
			try
			{
				// Adds the skill or sets its new level
				ClanRepository.getInstance().saveSkill(getClanId(), newSkill.getId(), newSkill.getLevel());
			}
			catch (Exception e)
			{
				_log.error("Error saving clan skills.", e);
			}
			
			// notify clan members
			addSkillEffects(true);
		}
		
		return oldSkill;
	}
	
	public void addSkillEffects(boolean notify)
	{
		if (_skills.size() < 1)
			return;
		for (L2ClanMember temp : _members.values())
		{
			if (temp != null)
			{
				if (temp.isOnline() && temp.getPlayerInstance() != null)
					addSkillEffects(temp.getPlayerInstance(), notify);
			}
		}
	}
	
	public void addSkillEffects(L2Player cm, boolean notify)
	{
		if (cm == null)
			return;
		
		// Add clan leader skills
		if (cm.isClanLeader())
			SiegeManager.getInstance().addSiegeSkills(cm);
		
		for (L2Skill skill : _skills.values())
		{
			if (skill.getMinPledgeClass() <= cm.getPledgeClass())
			{
				cm.addSkill(skill, false); // Skill is not saved to player DB
				if (notify)
					cm.sendPacket(new PledgeSkillListAdd(skill.getId(), skill.getLevel()));
			}
		}
	}
	
	public void broadcastToOnlineAllyMembers(L2ServerPacket packet)
	{
		if (getAllyId() == 0)
		{
			return;
		}
		for (L2Clan clan : ClanTable.getInstance().getClans())
		{
			if (clan.getAllyId() == getAllyId())
			{
				clan.broadcastToOnlineMembers(packet);
			}
		}
	}
	
	public void broadcastToOnlineMembers(L2ServerPacket packet)
	{
		for (L2ClanMember member : _members.values())
		{
			if (member == null)
				continue;
			
			if (member.isOnline() && member.getPlayerInstance() != null)
				member.getPlayerInstance().sendPacket(packet);
		}
	}
	
	public void broadcastCreatureSayToOnlineMembers(CreatureSay packet, L2Player broadcaster)
	{
		for (L2ClanMember member : _members.values())
		{
			if (member.isOnline()
					&& member.getPlayerInstance() != null
					&& !(Config.REGION_CHAT_ALSO_BLOCKED && BlockList
							.isBlocked(member.getPlayerInstance(), broadcaster)))
				member.getPlayerInstance().sendPacket(packet);
		}
	}
	
	public void broadcastToOtherOnlineMembers(L2ServerPacket packet, L2Player player)
	{
		for (L2ClanMember member : _members.values())
		{
			if (member.isOnline() && member.getPlayerInstance() != null && member.getPlayerInstance() != player)
				member.getPlayerInstance().sendPacket(packet);
		}
	}
	
	@Override
	public String toString()
	{
		return getName();
	}
	
	/**
	 * @return
	 */
	public boolean hasCrest()
	{
		return _hasCrest;
	}
	
	public boolean hasCrestLarge()
	{
		return _hasCrestLarge;
	}
	
	public void setHasCrest(boolean flag)
	{
		_hasCrest = flag;
	}
	
	public void setHasCrestLarge(boolean flag)
	{
		_hasCrestLarge = flag;
	}
	
	public ClanWarehouse getWarehouse()
	{
		return _warehouse;
	}
	
	public boolean isAtWarWith(Integer id)
	{
		if (_atWarWith != null && !_atWarWith.isEmpty())
			if (_atWarWith.contains(id))
				return true;
		return false;
	}
	
	public boolean isAtWarAttacker(Integer id)
	{
		if (_atWarAttackers != null && !_atWarAttackers.isEmpty())
			if (_atWarAttackers.contains(id))
				return true;
		return false;
	}
	
	public void setEnemyClan(L2Clan clan)
	{
		Integer id = clan.getClanId();
		_atWarWith.add(id);
	}
	
	public void setEnemyClan(Integer clan)
	{
		_atWarWith.add(clan);
	}
	
	public void setAttackerClan(L2Clan clan)
	{
		Integer id = clan.getClanId();
		_atWarAttackers.add(id);
	}
	
	public void setAttackerClan(Integer clan)
	{
		_atWarAttackers.add(clan);
	}
	
	public void deleteEnemyClan(L2Clan clan)
	{
		Integer id = clan.getClanId();
		_atWarWith.remove(id);
	}
	
	public void deleteAttackerClan(L2Clan clan)
	{
		Integer id = clan.getClanId();
		_atWarAttackers.remove(id);
	}
	
	public int getHiredGuards()
	{
		return _hiredGuards;
	}
	
	public void incrementHiredGuards()
	{
		_hiredGuards++;
	}
	
	public boolean isAtWar()
	{
		return _atWarWith != null && !_atWarWith.isEmpty();
	}
	
	public List<Integer> getWarList()
	{
		return _atWarWith;
	}
	
	public List<Integer> getAttackerList()
	{
		return _atWarAttackers;
	}
	
	public void broadcastClanStatus()
	{
		broadcastToOnlineMembers(new PledgeShowMemberListDeleteAll());
		broadcastToOnlineMembers(new PledgeShowMemberListAll(this));
	}
	
	public void removeSkill(int id)
	{
		L2Skill deleteSkill = null;
		for (L2Skill sk : _skillList)
		{
			if (sk.getId() == id)
			{
				deleteSkill = sk;
				return;
			}
		}
		_skillList.remove(deleteSkill);
	}
	
	public void removeSkill(L2Skill deleteSkill)
	{
		_skillList.remove(deleteSkill);
	}
	
	/**
	 * @return
	 */
	public List<L2Skill> getSkills()
	{
		return _skillList;
	}
	
	public class SubPledge
	{
		private final int _id;
		private String _subPledgeName;
		private int _leaderId;
		
		public SubPledge(int id, String name, int leaderId)
		{
			_id = id;
			_subPledgeName = name;
			_leaderId = leaderId;
		}
		
		public int getId()
		{
			return _id;
		}
		
		public String getName()
		{
			return _subPledgeName;
		}
		
		public void setName(String newName)
		{
			_subPledgeName = newName;
		}
		
		public int getLeaderId()
		{
			return _leaderId;
		}
		
		public void setLeaderId(int leaderId)
		{
			_leaderId = leaderId;
		}
	}
	
	public class RankPrivs
	{
		private final int _rankId;
		private final int _party;
		private int _rankPrivs;
		
		public RankPrivs(int rank, int party, int privs)
		{
			_rankId = rank;
			_party = party;
			_rankPrivs = privs;
		}
		
		public int getRank()
		{
			return _rankId;
		}
		
		public int getParty()
		{
			return _party;
		}
		
		public int getPrivs()
		{
			return _rankPrivs;
		}
		
		public void setPrivs(int privs)
		{
			_rankPrivs = privs;
		}
	}
	
	private void restoreSubPledges()
	{
		try
		{
			// Retrieve all subpledges of this clan from the database
			for (SubPledgeRecord record : ClanRepository.getInstance().loadSubPledges(getClanId()))
			{
				// Create a SubPledge object for each record
				SubPledge pledge = new SubPledge(record.type(), record.name(), record.leaderId());
				_subPledges.put(record.type(), pledge);
			}
		}
		catch (Exception e)
		{
			_log.error("Error restoring clan sub-units.", e);
		}
	}
	
	/** used to retrieve subPledge by type */
	public final SubPledge getSubPledge(int subpledgeType)
	{
		if (_subPledges == null)
			return null;
		
		return _subPledges.get(subpledgeType);
	}
	
	/** used to retrieve subPledge by type */
	public final SubPledge getSubPledge(String pledgeName)
	{
		if (_subPledges == null)
			return null;
		
		for (SubPledge sp : _subPledges.values())
		{
			if (sp.getName().equalsIgnoreCase(pledgeName))
			{
				return sp;
			}
		}
		return null;
	}
	
	/** used to retrieve all subPledges */
	public final SubPledge[] getAllSubPledges()
	{
		if (_subPledges == null)
			return new SubPledge[0];
		
		return _subPledges.values().toArray(new SubPledge[_subPledges.values().size()]);
	}
	
	public SubPledge createSubPledge(L2Player player, int subPledgeType, int leaderId, String subPledgeName)
	{
		final int originalSubPledgeType = subPledgeType;
		SubPledge subPledge = null;
		subPledgeType = getAvailablePledgeTypes(subPledgeType);
		if (subPledgeType == 0)
		{
			if (originalSubPledgeType == L2Clan.SUBUNIT_ACADEMY)
				player.sendPacket(SystemMessageId.CLAN_HAS_ALREADY_ESTABLISHED_A_CLAN_ACADEMY);
			else
				player.sendMessage("You can't create any more sub-units of this type");
			return null;
		}
		if (_leader.getObjectId() == leaderId)
		{
			player.sendMessage("Leader is not correct");
			return null;
		}
		
		//TODO: clan lvl9 or more can reinforce knights cheaper if first knight unit already created, use Config.KNIGHT_REINFORCE_COST
		
		int neededRepu = 0;
		if (subPledgeType != -1)
		{
			if (subPledgeType < L2Clan.SUBUNIT_KNIGHT1)
				neededRepu = Config.ROYAL_GUARD_COST;
			else if (subPledgeType > L2Clan.SUBUNIT_ROYAL2)
				neededRepu = Config.KNIGHT_UNIT_COST;
		}
		
		// Royal Guard 5000 points per each
		// Order of Knights 10000 points per each
		if (getReputationScore() < neededRepu)
		{
			player.sendPacket(SystemMessageId.CLAN_REPUTATION_SCORE_IS_TOO_LOW);
			return null;
		}
		
		final int newSubPledgeType = subPledgeType;
		final int cost = neededRepu;
		try
		{
			// The new sub-unit and the reputation it costs are saved together.
			boolean saved = ClanRepository.transaction("create sub-unit of clan " + getClanId(), () -> {
				ClanRepository.getInstance().insertSubPledge(getClanId(), newSubPledgeType, subPledgeName,
						newSubPledgeType != -1 ? leaderId : 0);
				
				if (newSubPledgeType != -1)
				{
					setReputationScore(getReputationScore() - cost, true);
				}
			});
			
			if (saved)
			{
				_subPledges.put(newSubPledgeType, new SubPledge(newSubPledgeType, subPledgeName, leaderId));
				subPledge = _subPledges.get(newSubPledgeType);
				
				if (_log.isDebugEnabled())
					_log.debug("New sub_clan saved in db: " + getClanId() + "; " + newSubPledgeType);
			}
		}
		catch (Exception e)
		{
			_log.error("Error saving sub clan data.", e);
		}
		
		broadcastToOnlineMembers(new PledgeShowInfoUpdate(_leader.getClan()));
		broadcastToOnlineMembers(new PledgeReceiveSubPledgeCreated(subPledge, _leader.getClan()));
		return subPledge;
	}
	
	public int getAvailablePledgeTypes(int pledgeType)
	{
		if (_subPledges.get(pledgeType) != null)
		{
			//_log.warning("found sub-unit with id: "+pledgeType);
			switch (pledgeType)
			{
				case SUBUNIT_ACADEMY:
					return 0;
				case SUBUNIT_ROYAL1:
					pledgeType = getAvailablePledgeTypes(SUBUNIT_ROYAL2);
					break;
				case SUBUNIT_ROYAL2:
					return 0;
				case SUBUNIT_KNIGHT1:
					pledgeType = getAvailablePledgeTypes(SUBUNIT_KNIGHT2);
					break;
				case SUBUNIT_KNIGHT2:
					pledgeType = getAvailablePledgeTypes(SUBUNIT_KNIGHT3);
					break;
				case SUBUNIT_KNIGHT3:
					pledgeType = getAvailablePledgeTypes(SUBUNIT_KNIGHT4);
					break;
				case SUBUNIT_KNIGHT4:
					return 0;
			}
		}
		return pledgeType;
	}
	
	public void updateSubPledgeInDB(int pledgeType)
	{
		try
		{
			SubPledge subPledge = getSubPledge(pledgeType);
			ClanRepository.getInstance().updateSubPledge(getClanId(), pledgeType, subPledge.getName(),
					subPledge.getLeaderId());
			if (_log.isDebugEnabled())
				_log.info("New subpledge leader and/or name saved in db: " + getClanId());
		}
		catch (Exception e)
		{
			_log.error("Error saving new sub clan leader.", e);
		}
	}
	
	private void restoreRankPrivs()
	{
		try
		{
			// Retrieve all skills of this L2Player from the database
			//_log.warning("clanPrivs restore for ClanId : "+getClanId());
			
			// Go though the recordset of this SQL query
			for (RankPrivilegeRecord record : ClanRepository.getInstance().loadRankPrivileges(getClanId()))
			{
				// Rows with rank -1 come from older versions and are ignored
				if (record.rank() == -1)
					continue;
				_privs.get(record.rank()).setPrivs(record.privileges());
			}
		}
		catch (Exception e)
		{
			_log.error("Error restoring clan privs by rank.", e);
		}
	}
	
	public void initializePrivs()
	{
		RankPrivs privs;
		for (int i = 1; i < 10; i++)
		{
			privs = new RankPrivs(i, 0, CP_NOTHING);
			_privs.put(i, privs);
		}
	}
	
	public int getRankPrivs(int rank)
	{
		if (_privs.get(rank) != null)
			return _privs.get(rank).getPrivs();
		
		return CP_NOTHING;
	}
	
	public void setRankPrivs(int rank, int privs)
	{
		if (_privs.get(rank) != null)
		{
			_privs.get(rank).setPrivs(privs);
			
			try
			{
				//_log.warning("requested store clan privs in db for rank: "+rank+", privs: "+privs);
				ClanRepository.getInstance().saveRankPrivileges(getClanId(), rank, privs);
			}
			catch (Exception e)
			{
				_log.warn("Could not store clan privs for rank: ", e);
			}
			
			L2Player mem;
			for (L2ClanMember cm : getMembers())
			{
				if (cm.isOnline())
				{
					if (cm.getPledgeRank() == rank)
					{
						if ((mem = cm.getPlayerInstance()) != null)
						{
							mem.setClanPrivileges(privs);
							mem.sendPacket(new UserInfo(mem));
						}
					}
				}
			}
			broadcastClanStatus();
		}
		else
		{
			_privs.put(rank, new RankPrivs(rank, 0, privs));
			
			try
			{
				//_log.warning("requested store clan new privs in db for rank: "+rank);
				ClanRepository.getInstance().saveRankPrivileges(getClanId(), rank, privs);
			}
			catch (Exception e)
			{
				_log.warn("Could not create new rank and store clan privs for rank: ", e);
			}
		}
	}
	
	/** used to retrieve all RankPrivs */
	public final RankPrivs[] getAllRankPrivs()
	{
		if (_privs == null)
			return new RankPrivs[0];
		
		return _privs.values().toArray(new RankPrivs[_privs.values().size()]);
	}
	
	public int getLeaderSubPledge(int leaderId)
	{
		int id = SUBUNIT_NONE;
		for (SubPledge sp : _subPledges.values())
		{
			if (sp.getLeaderId() == 0)
				continue;
			if (sp.getLeaderId() == leaderId)
			{
				id = sp.getId();
				break;
			}
		}
		return id;
	}
	
	public void setReputationScore(int value, boolean save)
	{
		if (_reputationScore >= 0 && value < 0)
		{
			broadcastToOnlineMembers(SystemMessageId.REPUTATION_POINTS_0_OR_LOWER_CLAN_SKILLS_DEACTIVATED
					.getSystemMessage());
			L2Skill[] skills = getAllSkills();
			for (L2ClanMember member : _members.values())
			{
				if (member.isOnline() && member.getPlayerInstance() != null)
				{
					for (L2Skill sk : skills)
						member.getPlayerInstance().removeSkill(sk, false);
				}
			}
		}
		else if (_reputationScore < 0 && value >= 0)
		{
			broadcastToOnlineMembers(SystemMessageId.CLAN_SKILLS_WILL_BE_ACTIVATED_SINCE_REPUTATION_IS_0_OR_HIGHER
					.getSystemMessage());
			L2Skill[] skills = getAllSkills();
			for (L2ClanMember member : _members.values())
			{
				if (member.isOnline() && member.getPlayerInstance() != null)
				{
					for (L2Skill sk : skills)
					{
						if (sk.getMinPledgeClass() <= member.getPlayerInstance().getPledgeClass())
							member.getPlayerInstance().addSkill(sk, false);
					}
				}
			}
		}
		
		_reputationScore = value;
		if (_reputationScore > 100000000)
			_reputationScore = 100000000;
		if (_reputationScore < -100000000)
			_reputationScore = -100000000;
		if (save)
			updateClanInDB();
		
		broadcastClanStatus();
	}
	
	public int getReputationScore()
	{
		return _reputationScore;
	}
	
	public void setRank(int rank)
	{
		_rank = rank;
	}
	
	public int getRank()
	{
		return _rank;
	}
	
	public int getAuctionBiddedAt()
	{
		return _auctionBiddedAt;
	}
	
	public void setAuctionBiddedAt(int id, boolean storeInDb)
	{
		_auctionBiddedAt = id;
		
		if (storeInDb)
		{
			try
			{
				ClanRepository.getInstance().updateAuctionBid(getClanId(), id);
			}
			catch (Exception e)
			{
				_log.warn("Could not store auction for clan: ", e);
			}
		}
	}
	
	/**
	 * Checks if activeChar and target meet various conditions to join a clan
	 *
	 * @param activeChar
	 * @param target
	 * @param pledgeType
	 * @return
	 */
	public boolean checkClanJoinCondition(L2Player activeChar, L2Player target, int pledgeType)
	{
		if (activeChar == null)
		{
			return false;
		}
		if (target == null)
		{
			activeChar.sendPacket(SystemMessageId.YOU_HAVE_INVITED_THE_WRONG_TARGET);
			return false;
		}
		if (activeChar.getObjectId() == target.getObjectId())
		{
			activeChar.sendPacket(SystemMessageId.CANNOT_INVITE_YOURSELF);
			return false;
		}
		if (getCharPenaltyExpiryTime() > System.currentTimeMillis())
		{
			activeChar.sendPacket(SystemMessageId.YOU_MUST_WAIT_BEFORE_ACCEPTING_A_NEW_MEMBER);
			return false;
		}
		if (target.getClanId() != 0)
		{
			SystemMessage sm = new SystemMessage(SystemMessageId.S1_WORKING_WITH_ANOTHER_CLAN);
			sm.addString(target.getName());
			activeChar.sendPacket(sm);
			return false;
		}
		if (target.getClanJoinExpiryTime() > System.currentTimeMillis())
		{
			SystemMessage sm = new SystemMessage(SystemMessageId.C1_MUST_WAIT_BEFORE_JOINING_ANOTHER_CLAN);
			sm.addString(target.getName());
			activeChar.sendPacket(sm);
			return false;
		}
		if ((target.getLevel() > 40 || target.getClassId().level() >= 2) && pledgeType == -1)
		{
			SystemMessage sm = new SystemMessage(SystemMessageId.S1_DOESNOT_MEET_REQUIREMENTS_TO_JOIN_ACADEMY);
			sm.addString(target.getName());
			activeChar.sendPacket(sm);
			activeChar.sendPacket(SystemMessageId.ACADEMY_REQUIREMENTS);
			return false;
		}
		if (getSubPledgeMembersCount(pledgeType) >= getMaxNrOfMembers(pledgeType))
		{
			if (pledgeType == 0)
			{
				SystemMessage sm = new SystemMessage(SystemMessageId.S1_CLAN_IS_FULL);
				sm.addString(getName());
				activeChar.sendPacket(sm);
			}
			else
			{
				activeChar.sendPacket(SystemMessageId.SUBCLAN_IS_FULL);
			}
			return false;
		}
		return true;
	}
	
	/**
	 * Checks if activeChar and target meet various conditions to join a clan
	 *
	 * @param activeChar
	 * @param target
	 * @return
	 */
	public static boolean checkAllyJoinCondition(L2Player activeChar, L2Player target)
	{
		if (activeChar == null)
			return false;
		
		if (activeChar.getAllyId() == 0 || !activeChar.isClanLeader()
				|| activeChar.getClanId() != activeChar.getAllyId())
		{
			activeChar.sendPacket(SystemMessageId.FEATURE_ONLY_FOR_ALLIANCE_LEADER);
			return false;
		}
		
		L2Clan leaderClan = activeChar.getClan();
		if (leaderClan.getAllyPenaltyExpiryTime() > System.currentTimeMillis()
				&& leaderClan.getAllyPenaltyType() == PENALTY_TYPE_DISMISS_CLAN)
		{
			activeChar.sendPacket(SystemMessageId.CANT_INVITE_CLAN_WITHIN_1_DAY);
			return false;
		}
		else if (target == null)
		{
			activeChar.sendPacket(SystemMessageId.YOU_HAVE_INVITED_THE_WRONG_TARGET);
			return false;
		}
		else if (activeChar.getObjectId() == target.getObjectId())
		{
			activeChar.sendPacket(SystemMessageId.CANNOT_INVITE_YOURSELF);
			return false;
		}
		else if (target.getClan() == null)
		{
			activeChar.sendPacket(SystemMessageId.TARGET_MUST_BE_IN_CLAN);
			return false;
		}
		else if (!target.isClanLeader())
		{
			SystemMessage sm = new SystemMessage(SystemMessageId.S1_IS_NOT_A_CLAN_LEADER);
			sm.addString(target.getName());
			activeChar.sendPacket(sm);
			return false;
		}
		L2Clan targetClan = target.getClan();
		if (target.getAllyId() != 0)
		{
			SystemMessage sm = new SystemMessage(SystemMessageId.S1_CLAN_ALREADY_MEMBER_OF_S2_ALLIANCE);
			sm.addString(targetClan.getName());
			sm.addString(targetClan.getAllyName());
			activeChar.sendPacket(sm);
			return false;
		}
		else if (targetClan.getAllyPenaltyExpiryTime() > System.currentTimeMillis())
		{
			if (targetClan.getAllyPenaltyType() == PENALTY_TYPE_CLAN_LEAVED)
			{
				SystemMessage sm = new SystemMessage(SystemMessageId.S1_CANT_ENTER_ALLIANCE_WITHIN_1_DAY);
				sm.addString(target.getClan().getName());
				sm.addString(target.getClan().getAllyName());
				activeChar.sendPacket(sm);
				return false;
			}
			else if (targetClan.getAllyPenaltyType() == PENALTY_TYPE_CLAN_DISMISSED)
			{
				activeChar.sendPacket(SystemMessageId.CANT_ENTER_ALLIANCE_WITHIN_1_DAY);
				return false;
			}
		}
		if (SiegeManager.getInstance().checkIfInZone(activeChar) && SiegeManager.getInstance().checkIfInZone(target))
		{
			activeChar.sendPacket(SystemMessageId.OPPOSING_CLAN_IS_PARTICIPATING_IN_SIEGE);
			return false;
		}
		else if (leaderClan.isAtWarWith(targetClan.getClanId()))
		{
			activeChar.sendPacket(SystemMessageId.MAY_NOT_ALLY_CLAN_BATTLE);
			return false;
		}
		
		int numOfClansInAlly = 0;
		for (L2Clan clan : ClanTable.getInstance().getClans())
			if (clan.getAllyId() == activeChar.getAllyId())
				++numOfClansInAlly;
		
		if (numOfClansInAlly >= Config.ALT_MAX_NUM_OF_CLANS_IN_ALLY)
		{
			activeChar.sendPacket(SystemMessageId.YOU_HAVE_EXCEEDED_THE_LIMIT);
			return false;
		}
		
		return true;
	}
	
	public long getAllyPenaltyExpiryTime()
	{
		return _allyPenaltyExpiryTime;
	}
	
	public int getAllyPenaltyType()
	{
		return _allyPenaltyType;
	}
	
	public void setAllyPenaltyExpiryTime(long expiryTime, int penaltyType)
	{
		_allyPenaltyExpiryTime = expiryTime;
		_allyPenaltyType = penaltyType;
	}
	
	public long getCharPenaltyExpiryTime()
	{
		return _charPenaltyExpiryTime;
	}
	
	public void setCharPenaltyExpiryTime(long time)
	{
		_charPenaltyExpiryTime = time;
	}
	
	public long getDissolvingExpiryTime()
	{
		return _dissolvingExpiryTime;
	}
	
	public void setDissolvingExpiryTime(long time)
	{
		_dissolvingExpiryTime = time;
	}
	
	public void createAlly(L2Player player, String allyName)
	{
		if (null == player)
			return;
		
		if (_log.isDebugEnabled())
			_log.info(player.getObjectId() + "(" + player.getName() + ") requested ally creation from ");
		
		if (!player.isClanLeader())
		{
			player.sendPacket(SystemMessageId.ONLY_CLAN_LEADER_CREATE_ALLIANCE);
			return;
		}
		if (getAllyId() != 0)
		{
			player.sendPacket(SystemMessageId.ALREADY_JOINED_ALLIANCE);
			return;
		}
		if (getLevel() < 5)
		{
			player.sendPacket(SystemMessageId.TO_CREATE_AN_ALLY_YOU_CLAN_MUST_BE_LEVEL_5_OR_HIGHER);
			return;
		}
		if (getAllyPenaltyExpiryTime() > System.currentTimeMillis())
		{
			if (getAllyPenaltyType() == L2Clan.PENALTY_TYPE_DISSOLVE_ALLY)
			{
				player.sendPacket(SystemMessageId.CANT_CREATE_ALLIANCE_10_DAYS_DISOLUTION);
				return;
			}
		}
		if (getDissolvingExpiryTime() > System.currentTimeMillis())
		{
			player.sendPacket(SystemMessageId.YOU_MAY_NOT_CREATE_ALLY_WHILE_DISSOLVING);
			return;
		}
		if (allyName.length() > 16 || allyName.length() < 3)
		{
			player.sendPacket(SystemMessageId.INCORRECT_ALLIANCE_NAME_LENGTH);
			return;
		}
		if (!Config.CLAN_ALLY_NAME_PATTERN.matcher(allyName).matches())
		{
			player.sendPacket(SystemMessageId.INCORRECT_ALLIANCE_NAME);
			return;
		}
		if (ClanTable.getInstance().isAllyExists(allyName))
		{
			player.sendPacket(SystemMessageId.ALLIANCE_ALREADY_EXISTS);
			return;
		}
		
		setAllyId(getClanId());
		setAllyName(allyName.trim());
		setAllyPenaltyExpiryTime(0, 0);
		updateClanInDB();
		
		player.sendPacket(new UserInfo(player));
		
		player.sendMessage("Alliance " + allyName + " has been created.");
	}
	
	public void dissolveAlly(L2Player player)
	{
		if (getAllyId() == 0)
		{
			player.sendPacket(SystemMessageId.NO_CURRENT_ALLIANCES);
			return;
		}
		if (!player.isClanLeader() || getClanId() != getAllyId())
		{
			player.sendPacket(SystemMessageId.FEATURE_ONLY_FOR_ALLIANCE_LEADER);
			return;
		}
		if (SiegeManager.getInstance().checkIfInZone(player))
		{
			player.sendPacket(SystemMessageId.CANNOT_DISSOLVE_ALLY_WHILE_IN_SIEGE);
			return;
		}
		
		broadcastToOnlineAllyMembers(SystemMessageId.ALLIANCE_DISOLVED.getSystemMessage());
		
		long currentTime = System.currentTimeMillis();
		// Every clan of the alliance and the leader clan leave the alliance together.
		WorldTransaction.run("dissolve alliance " + getAllyId(), () -> {
			for (L2Clan clan : ClanTable.getInstance().getClans())
			{
				if (clan.getAllyId() == getAllyId() && clan.getClanId() != getClanId())
				{
					clan.setAllyId(0);
					clan.setAllyName(null);
					clan.setAllyCrestId(0);
					clan.setAllyPenaltyExpiryTime(0, 0);
					clan.updateClanInDB();
				}
			}
			
			setAllyId(0);
			setAllyName(null);
			setAllyCrestId(0);
			setAllyPenaltyExpiryTime(currentTime + Config.ALT_CREATE_ALLY_DAYS_WHEN_DISSOLVED * 86400000L,
					L2Clan.PENALTY_TYPE_DISSOLVE_ALLY); //24*60*60*1000 = 86400000
			updateClanInDB();
		});
		
		// The clan leader should take the XP penalty of a full death.
		player.deathPenalty(false, false, false);
	}
	
	public boolean levelUpClan(L2Player player)
	{
		if (!player.isClanLeader())
		{
			player.sendPacket(SystemMessageId.YOU_ARE_NOT_AUTHORIZED_TO_DO_THAT);
			return false;
		}
		if (System.currentTimeMillis() < getDissolvingExpiryTime())
		{
			player.sendPacket(SystemMessageId.CANNOT_RISE_LEVEL_WHILE_DISSOLUTION_IN_PROGRESS);
			return false;
		}
		
		boolean increaseClanLevel = false;
		
		switch (getLevel())
		{
			case 0:
			{
				// Upgrade to 1
				if (player.getSp() >= 20000 && player.getAdena() >= 650000)
				{
					if (player.reduceAdena("ClanLvl", 650000, player.getTarget(), true))
					{
						player.setSp(player.getSp() - 20000);
						SystemMessage sp = new SystemMessage(SystemMessageId.SP_DECREASED_S1);
						sp.addNumber(20000);
						player.sendPacket(sp);
						increaseClanLevel = true;
					}
				}
				break;
			}
			case 1:
			{
				// Upgrade to 2
				if (player.getSp() >= 100000 && player.getAdena() >= 2500000)
				{
					if (player.reduceAdena("ClanLvl", 2500000, player.getTarget(), true))
					{
						player.setSp(player.getSp() - 100000);
						SystemMessage sp = new SystemMessage(SystemMessageId.SP_DECREASED_S1);
						sp.addNumber(100000);
						player.sendPacket(sp);
						increaseClanLevel = true;
					}
				}
				break;
			}
			case 2:
			{
				// Upgrade to 3
				if (player.getSp() >= 350000 && player.getInventory().getItemByItemId(1419) != null)
				{
					// itemId 1419 == Blood Mark
					if (player.destroyItemByItemId("ClanLvl", 1419, 1, player.getTarget(), false))
					{
						player.setSp(player.getSp() - 350000);
						SystemMessage sm = new SystemMessage(SystemMessageId.SP_DECREASED_S1);
						sm.addNumber(350000);
						player.sendPacket(sm);
						sm = new SystemMessage(SystemMessageId.S2_S1_DISAPPEARED);
						sm.addItemName(1419);
						sm.addItemNumber(1);
						player.sendPacket(sm);
						increaseClanLevel = true;
					}
				}
				break;
			}
			case 3:
			{
				// Upgrade to 4
				if (player.getSp() >= 1000000 && player.getInventory().getItemByItemId(3874) != null)
				{
					// itemId 3874 == Alliance Manifesto
					if (player.destroyItemByItemId("ClanLvl", 3874, 1, player.getTarget(), false))
					{
						player.setSp(player.getSp() - 1000000);
						SystemMessage sm = new SystemMessage(SystemMessageId.SP_DECREASED_S1);
						sm.addNumber(1000000);
						player.sendPacket(sm);
						sm = new SystemMessage(SystemMessageId.S2_S1_DISAPPEARED);
						sm.addItemName(3874);
						sm.addItemNumber(1);
						player.sendPacket(sm);
						increaseClanLevel = true;
					}
				}
				break;
			}
			case 4:
			{
				// Upgrade to 5
				if (player.getSp() >= 2500000 && player.getInventory().getItemByItemId(3870) != null)
				{
					// itemId 3870 == Seal of Aspiration
					if (player.destroyItemByItemId("ClanLvl", 3870, 1, player.getTarget(), false))
					{
						player.setSp(player.getSp() - 2500000);
						SystemMessage sm = new SystemMessage(SystemMessageId.SP_DECREASED_S1);
						sm.addNumber(2500000);
						player.sendPacket(sm);
						sm = new SystemMessage(SystemMessageId.S2_S1_DISAPPEARED);
						sm.addItemName(3870);
						sm.addItemNumber(1);
						player.sendPacket(sm);
						increaseClanLevel = true;
					}
				}
				break;
			}
			case 5:
			{
				// Upgrade to 6
				if (getReputationScore() >= Config.CLAN_LEVEL_6_COST
						&& getMembersCount() >= Config.MEMBER_FOR_LEVEL_SIX)
				{
					setReputationScore(getReputationScore() - Config.CLAN_LEVEL_6_COST, true);
					SystemMessage sm = new SystemMessage(SystemMessageId.S1_DEDUCTED_FROM_CLAN_REP);
					sm.addNumber(Config.CLAN_LEVEL_6_COST);
					player.sendPacket(sm);
					increaseClanLevel = true;
				}
				break;
			}
			case 6:
			{
				// Upgrade to 7
				if (getReputationScore() >= Config.CLAN_LEVEL_7_COST
						&& getMembersCount() >= Config.MEMBER_FOR_LEVEL_SEVEN)
				{
					setReputationScore(getReputationScore() - Config.CLAN_LEVEL_7_COST, true);
					SystemMessage sm = new SystemMessage(SystemMessageId.S1_DEDUCTED_FROM_CLAN_REP);
					sm.addNumber(Config.CLAN_LEVEL_7_COST);
					player.sendPacket(sm);
					increaseClanLevel = true;
				}
				break;
			}
			case 7:
			{
				// Upgrade to 8
				if (getReputationScore() >= Config.CLAN_LEVEL_8_COST
						&& getMembersCount() >= Config.MEMBER_FOR_LEVEL_EIGHT)
				{
					setReputationScore(getReputationScore() - Config.CLAN_LEVEL_8_COST, true);
					SystemMessage sm = new SystemMessage(SystemMessageId.S1_DEDUCTED_FROM_CLAN_REP);
					sm.addNumber(Config.CLAN_LEVEL_8_COST);
					player.sendPacket(sm);
					increaseClanLevel = true;
				}
				break;
			}
			case 8:
			{
				// Upgrade to 9
				if (getReputationScore() >= Config.CLAN_LEVEL_9_COST
						&& player.getInventory().getItemByItemId(9910) != null
						&& getMembersCount() >= Config.MEMBER_FOR_LEVEL_NINE)
				{
					// itemId 9910 == Blood Oath
					if (player.destroyItemByItemId("ClanLvl", 9910, 150, player.getTarget(), false))
					{
						setReputationScore(getReputationScore() - Config.CLAN_LEVEL_9_COST, true);
						SystemMessage sm = new SystemMessage(SystemMessageId.S1_DEDUCTED_FROM_CLAN_REP);
						sm.addNumber(Config.CLAN_LEVEL_9_COST);
						player.sendPacket(sm);
						sm = new SystemMessage(SystemMessageId.S2_S1_DISAPPEARED);
						sm.addItemName(9910);
						sm.addItemNumber(150);
						player.sendPacket(sm);
						increaseClanLevel = true;
					}
				}
				break;
			}
			case 9:
			{
				// Upgrade to 10
				if (getReputationScore() >= Config.CLAN_LEVEL_10_COST
						&& player.getInventory().getItemByItemId(9911) != null
						&& getMembersCount() >= Config.MEMBER_FOR_LEVEL_TEN)
				{
					// itemId 9911 == Blood Alliance
					if (player.destroyItemByItemId("ClanLvl", 9911, 5, player.getTarget(), false))
					{
						setReputationScore(getReputationScore() - Config.CLAN_LEVEL_10_COST, true);
						SystemMessage sm = new SystemMessage(SystemMessageId.S1_DEDUCTED_FROM_CLAN_REP);
						sm.addNumber(Config.CLAN_LEVEL_10_COST);
						player.sendPacket(sm);
						sm = new SystemMessage(SystemMessageId.S2_S1_DISAPPEARED);
						sm.addItemName(9911);
						sm.addItemNumber(5);
						player.sendPacket(sm);
						increaseClanLevel = true;
					}
				}
				break;
			}
			case 10:
			{
				// Upgrade to 11
				// Missing check for territory
				if (getReputationScore() >= Config.CLAN_LEVEL_11_COST
						&& getMembersCount() >= Config.MEMBER_FOR_LEVEL_ELEVEN)
				{
					setReputationScore(getReputationScore() - Config.CLAN_LEVEL_11_COST, true);
					SystemMessage sm = new SystemMessage(SystemMessageId.S1_DEDUCTED_FROM_CLAN_REP);
					sm.addNumber(Config.CLAN_LEVEL_11_COST);
					player.sendPacket(sm);
					increaseClanLevel = true;
				}
				break;
			}
			default:
				return false;
		}
		
		if (!increaseClanLevel)
		{
			player.sendPacket(SystemMessageId.FAILED_TO_INCREASE_CLAN_LEVEL);
			return false;
		}
		
		// the player should know that he has less sp now :p
		StatusUpdate su = new StatusUpdate(player.getObjectId());
		su.addAttribute(StatusUpdate.SP, player.getSp());
		player.sendPacket(su);
		
		ItemList il = new ItemList(player, false);
		player.sendPacket(il);
		
		changeLevel(getLevel() + 1);
		
		player.updateNameTitleColor();
		
		return true;
	}
	
	public void changeLevel(int level)
	{
		try
		{
			ClanRepository.getInstance().updateLevel(getClanId(), level);
		}
		catch (Exception e)
		{
			_log.warn("could not increase clan level:", e);
		}
		
		setLevel(level);
		
		if (getLeader().isOnline())
		{
			L2Player leader = getLeader().getPlayerInstance();
			if (4 < level)
			{
				SiegeManager.getInstance().addSiegeSkills(leader);
			}
			else if (5 > level)
			{
				SiegeManager.getInstance().removeSiegeSkills(leader);
			}
			if (4 < level)
			{
				leader.sendPacket(SystemMessageId.CLAN_CAN_ACCUMULATE_CLAN_REPUTATION_POINTS);
			}
		}
		
		// notify all the members about it
		broadcastToOnlineMembers(SystemMessageId.CLAN_LEVEL_INCREASED.getSystemMessage());
		broadcastToOnlineMembers(new PledgeShowInfoUpdate(this));
		/*
		 * Micht :
		 * 	- use PledgeShowInfoUpdate instead of PledgeStatusChanged
		 * 		to update clan level ingame
		 * 	- remove broadcastClanStatus() to avoid members duplication
		 */
		//clan.broadcastToOnlineMembers(new PledgeStatusChanged(clan));
		//clan.broadcastClanStatus();
	}
	
	public List<L2Player> getOnlineAllyMembers()
	{
		List<L2Player> list = new ArrayList<L2Player>();
		if (getAllyId() == 0)
		{
			return list;
		}
		for (L2Clan clan : ClanTable.getInstance().getClans())
		{
			if (clan.getAllyId() == getAllyId())
			{
				list.addAll(clan.getOnlineMembersList());
			}
		}
		return list;
	}
	
	/**
	 * Checks if player has sufficient privileges for an action.<BR>
	 * This method considers these two facts (fully retail):<BR>
	 * Player has no clan - no privileges<BR>
	 * Player is clan leader - all privileges<BR>
	 * Current action types:
	 * <LI>{@link #CP_CL_JOIN_CLAN}</LI>
	 * <LI>{@link #CP_CL_GIVE_TITLE}</LI>
	 * <LI>{@link #CP_CL_VIEW_WAREHOUSE}</LI>
	 * <LI>{@link #CP_CL_MANAGE_RANKS}</LI>
	 * <LI>{@link #CP_CL_PLEDGE_WAR}</LI>
	 * <LI>{@link #CP_CL_DISMISS}</LI>
	 * <LI>{@link #CP_CL_REGISTER_CREST}</LI>
	 * <LI>{@link #CP_CL_APPRENTICE}</LI>
	 * <LI>{@link #CP_CL_TROOPS_FAME}</LI>
	 * <LI>{@link #CP_CL_SUMMON_AIRSHIP}</LI>
	 * <LI>{@link #CP_CH_OPEN_DOOR}</LI>
	 * <LI>{@link #CP_CH_OTHER_RIGHTS}</LI>
	 * <LI>{@link #CP_CH_AUCTION}</LI>
	 * <LI>{@link #CP_CH_DISMISS}</LI>
	 * <LI>{@link #CP_CH_SET_FUNCTIONS}</LI>
	 * <LI>{@link #CP_CS_OPEN_DOOR}</LI>
	 * <LI>{@link #CP_CS_MANOR_ADMIN}</LI>
	 * <LI>{@link #CP_CS_MANAGE_SIEGE}</LI>
	 * <LI>{@link #CP_CS_USE_FUNCTIONS}</LI>
	 * <LI>{@link #CP_CS_DISMISS}</LI>
	 * <LI>{@link #CP_CS_TAXES}</LI>
	 * <LI>{@link #CP_CS_MERCENARIES}</LI>
	 * <LI>{@link #CP_CS_SET_FUNCTIONS}</LI>
	 * <LI>{@link #CP_ALL}</LI>
	 * @param player a player
	 * @param privs action type (see above)
	 * @return whether player has these privileges
	 */
	public static final boolean checkPrivileges(L2Player player, int privs)
	{
		if (player.getClan() == null)
			return false;
		else if (player.isClanLeader())
			return true;
		else
			return (player.getClanPrivileges() & privs) == privs;
	}
}
