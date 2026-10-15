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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.instancemanager.SiegeManager;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.persistence.clan.ClanRepository;

public class L2ClanMember
{
	private static final Logger _log = LoggerFactory.getLogger(L2ClanMember.class);
	
	private final L2Clan _clan;
	private int _objectId;
	private String _name;
	private String _title;
	private int _pledgeRank;
	private int _level;
	private int _classId;
	private L2Player _player;
	private int _subPledgeType;
	private int _apprentice;
	private int _sponsor;
	private int _sex;
	private int _race;
	
	public L2ClanMember(L2Clan clan, String name, int level, int classId, int objectId, int subPledgeType,
			int pledgeRank, String title, int sex, int race)
	{
		if (clan == null)
			throw new IllegalArgumentException("Can not create a ClanMember with a null clan.");
		_clan = clan;
		_name = name;
		_level = level;
		_classId = classId;
		_objectId = objectId;
		_pledgeRank = pledgeRank;
		_title = title;
		_subPledgeType = subPledgeType;
		_apprentice = 0;
		_sponsor = 0;
		_sex = sex;
		_race = race;
	}
	
	public L2ClanMember(L2Player player)
	{
		if (player.getClan() == null)
			throw new IllegalArgumentException("Can not create a ClanMember if player has a null clan.");
		_clan = player.getClan();
		_player = player;
		_name = _player.getName();
		_level = _player.getLevel();
		_classId = _player.getClassId().getId();
		_objectId = _player.getObjectId();
		_pledgeRank = _player.getPledgeRank();
		_subPledgeType = _player.getSubPledgeType();
		_title = _player.getTitle();
		_apprentice = 0;
		_sponsor = 0;
		_sex = _player.getAppearance().getSex() ? 1 : 0;
		_race = _player.getRace().ordinal();
	}
	
	public L2ClanMember(L2Clan clan, L2Player player)
	{
		_clan = clan;
		_player = player;
		_name = _player.getName();
		_level = _player.getLevel();
		_classId = _player.getClassId().getId();
		_objectId = _player.getObjectId();
		_pledgeRank = _player.getPledgeRank();
		_subPledgeType = _player.getSubPledgeType();
		_title = _player.getTitle();
		_apprentice = 0;
		_sponsor = 0;
		_sex = _player.getAppearance().getSex() ? 1 : 0;
		_race = _player.getRace().ordinal();
	}
	
	public void setPlayerInstance(L2Player player)
	{
		if (player == null && _player != null)
		{
			// this is here to keep the data when the player logs off
			_name = _player.getName();
			_level = _player.getLevel();
			_classId = _player.getClassId().getId();
			_objectId = _player.getObjectId();
			_pledgeRank = _player.getPledgeRank();
			_subPledgeType = _player.getSubPledgeType();
			_title = _player.getTitle();
			_apprentice = _player.getApprentice();
			_sponsor = _player.getSponsor();
			_sex = _player.getAppearance().getSex() ? 1 : 0;
			_race = _player.getRace().ordinal();
		}
		if (player != null)
		{
			if (_clan.getReputationScore() >= 0)
			{
				L2Skill[] skills = _clan.getAllSkills();
				for (L2Skill sk : skills)
				{
					if (sk.getMinPledgeClass() <= player.getPledgeClass())
						player.addSkill(sk, false);
				}
			}
			if (_clan.getLevel() > 3 && player.isClanLeader())
				SiegeManager.getInstance().addSiegeSkills(player);
			if (player.isClanLeader())
				_clan.setLeader(this);
		}
		_player = player;
	}
	
	public L2Player getPlayerInstance()
	{
		return _player;
	}
	
	public boolean isOnline()
	{
		return _player != null;
	}
	
	/**
	 * @return Returns the classId.
	 */
	public int getClassId()
	{
		if (_player != null)
		{
			return _player.getClassId().getId();
		}
		return _classId;
	}
	
	/**
	 * @return Returns the level.
	 */
	public int getLevel()
	{
		if (_player != null)
		{
			return _player.getLevel();
		}
		return _level;
	}
	
	/**
	 * @return Returns the name.
	 */
	public String getName()
	{
		if (_player != null)
		{
			return _player.getName();
		}
		return _name;
	}
	
	/**
	 * @return Returns the objectId.
	 */
	public int getObjectId()
	{
		if (_player != null)
		{
			return _player.getObjectId();
		}
		return _objectId;
	}
	
	public String getTitle()
	{
		if (_player != null)
		{
			return _player.getTitle();
		}
		return _title;
	}
	
	public int getSubPledgeType()
	{
		if (_player != null)
		{
			return _player.getSubPledgeType();
		}
		return _subPledgeType;
	}
	
	public void setSubPledgeType(int subPledgeType)
	{
		_subPledgeType = subPledgeType;
		if (_player != null)
		{
			_player.setSubPledgeType(subPledgeType);
		}
		else
		{
			//db save if char not logged in
			updateSubPledgeType();
		}
	}
	
	public void updateSubPledgeType()
	{
		try
		{
			ClanRepository.getInstance().updateMemberPledgeType(getObjectId(), _subPledgeType);
		}
		catch (Exception e)
		{
			_log.warn("could not set char subpledge:", e);
		}
	}
	
	public int getPledgeRank()
	{
		if (_player != null)
			return _player.getPledgeRank();
		return _pledgeRank;
	}
	
	/**
	 * @param pledgeRank
	 */
	public void setPledgeRank(int pledgeRank)
	{
		_pledgeRank = pledgeRank;
		if (_player != null)
		{
			_player.setPledgeRank(pledgeRank);
		}
		else
		{
			// db save if char not logged in
			updatePledgeRank();
		}
	}
	
	/**
	 * Update the characters table of the database with power grade.<BR><BR>
	 */
	public void updatePledgeRank()
	{
		try
		{
			ClanRepository.getInstance().updateMemberPledgeRank(getObjectId(), _pledgeRank);
		}
		catch (Exception e)
		{
			_log.warn("could not set char pledge_rank:", e);
		}
	}
	
	public void initApprenticeAndSponsor(int apprenticeID, int sponsorID)
	{
		_apprentice = apprenticeID;
		_sponsor = sponsorID;
	}
	
	public int getSponsor()
	{
		if (_player != null)
			return _player.getSponsor();
		
		return _sponsor;
	}
	
	public int getApprentice()
	{
		if (_player != null)
			return _player.getApprentice();
		
		return _apprentice;
	}
	
	public int getSex()
	{
		if (_player != null)
			return _player.getAppearance().getSex() ? 1 : 0;
		
		return _sex;
	}
	
	public int getRace()
	{
		if (_player != null)
			return _player.getRace().ordinal();
		
		return _race;
	}
	
	public String getApprenticeOrSponsorName()
	{
		if (_player != null)
		{
			_apprentice = _player.getApprentice();
			_sponsor = _player.getSponsor();
		}
		
		if (_apprentice != 0)
		{
			L2ClanMember apprentice = _clan.getClanMember(_apprentice);
			if (apprentice != null)
				return apprentice.getName();
			
			return "Error";
		}
		if (_sponsor != 0)
		{
			L2ClanMember sponsor = _clan.getClanMember(_sponsor);
			if (sponsor != null)
				return sponsor.getName();
			
			return "Error";
		}
		return "";
	}
	
	public L2Clan getClan()
	{
		return _clan;
	}
	
	/**
	 * Returns player's pledge rank.
	 * @param activeChar player
	 * @return pledge rank
	 */
	public static int getCurrentPledgeClass(L2Player activeChar)
	{
		int pledgeClass = calculatePledgeClass(activeChar);
		
		if (activeChar.isHero())
			return Math.max(pledgeClass, L2Clan.RANK_MARQUIS);
		else if (activeChar.isNoble())
			return Math.max(pledgeClass, L2Clan.RANK_BARON);
		
		return pledgeClass;
	}
	
	/**
	 * Returns player's <B>RAW</B> pledge rank.
	 * Only clan status is factored.
	 * @param player A player (may be null)
	 * @return raw pledge rank
	 */
	private static int calculatePledgeClass(L2Player player)
	{
		int pledgeClass = L2Clan.RANK_VAGABOND;
		if (player == null)
			return pledgeClass;
		
		L2Clan clan = player.getClan();
		if (clan != null)
		{
			switch (player.getClan().getLevel())
			{
				case 3:
					if (player.isClanLeader())
						pledgeClass = L2Clan.RANK_HEIR;
					else
						pledgeClass = L2Clan.RANK_VASSAL;
					break;
				case 4:
					if (player.isClanLeader())
						pledgeClass = L2Clan.RANK_KNIGHT;
					else
						pledgeClass = L2Clan.RANK_HEIR;
					break;
				case 5: // academy may now be created
					switch (player.getSubPledgeType())
					{
						case L2Clan.SUBUNIT_ACADEMY:
							pledgeClass = L2Clan.RANK_VASSAL;
							break;
						case L2Clan.SUBUNIT_NONE:
							if (player.isClanLeader())
								pledgeClass = L2Clan.RANK_ELDER;
							else
								pledgeClass = L2Clan.RANK_HEIR;
							break;
					}
					break;
				case 6: // royal guard may now be created
					switch (player.getSubPledgeType())
					{
						case L2Clan.SUBUNIT_ACADEMY:
							pledgeClass = L2Clan.RANK_VASSAL;
							break;
						case L2Clan.SUBUNIT_ROYAL1:
						case L2Clan.SUBUNIT_ROYAL2:
							pledgeClass = L2Clan.RANK_HEIR; // because L7 RG are Knights
							break;
						case L2Clan.SUBUNIT_NONE:
							if (player.isClanLeader())
								pledgeClass = L2Clan.RANK_BARON;
							else
								switch (clan.getLeaderSubPledge(player.getObjectId()))
								{
									case L2Clan.SUBUNIT_ROYAL1:
									case L2Clan.SUBUNIT_ROYAL2:
										pledgeClass = L2Clan.RANK_ELDER;
										break;
									default:
										pledgeClass = L2Clan.RANK_KNIGHT;
										break;
								}
							break;
					}
					break;
				case 7: // order of knights may now be created
					switch (player.getSubPledgeType())
					{
						case L2Clan.SUBUNIT_ACADEMY:
							pledgeClass = L2Clan.RANK_VASSAL;
							break;
						case L2Clan.SUBUNIT_ROYAL1:
						case L2Clan.SUBUNIT_ROYAL2:
							pledgeClass = L2Clan.RANK_KNIGHT;
							break;
						case L2Clan.SUBUNIT_KNIGHT1:
						case L2Clan.SUBUNIT_KNIGHT2:
						case L2Clan.SUBUNIT_KNIGHT3:
						case L2Clan.SUBUNIT_KNIGHT4:
							pledgeClass = L2Clan.RANK_HEIR; // because L8 KU are Knights
							break;
						case L2Clan.SUBUNIT_NONE:
							if (player.isClanLeader())
								pledgeClass = L2Clan.RANK_COUNT;
							else
								switch (clan.getLeaderSubPledge(player.getObjectId()))
								{
									case L2Clan.SUBUNIT_ROYAL1:
									case L2Clan.SUBUNIT_ROYAL2:
										pledgeClass = L2Clan.RANK_VISCOUNT;
										break;
									case L2Clan.SUBUNIT_KNIGHT1:
									case L2Clan.SUBUNIT_KNIGHT2:
									case L2Clan.SUBUNIT_KNIGHT3:
									case L2Clan.SUBUNIT_KNIGHT4:
										pledgeClass = L2Clan.RANK_BARON;
										break;
									default:
										pledgeClass = L2Clan.RANK_ELDER;
										break;
								}
							break;
					}
					break;
				case 8:
					switch (player.getSubPledgeType())
					{
						case L2Clan.SUBUNIT_ACADEMY:
							pledgeClass = L2Clan.RANK_VASSAL;
							break;
						case L2Clan.SUBUNIT_ROYAL1:
						case L2Clan.SUBUNIT_ROYAL2:
							pledgeClass = L2Clan.RANK_ELDER;
							break;
						case L2Clan.SUBUNIT_KNIGHT1:
						case L2Clan.SUBUNIT_KNIGHT2:
						case L2Clan.SUBUNIT_KNIGHT3:
						case L2Clan.SUBUNIT_KNIGHT4:
							pledgeClass = L2Clan.RANK_KNIGHT;
							break;
						case L2Clan.SUBUNIT_NONE:
							if (player.isClanLeader())
								pledgeClass = L2Clan.RANK_MARQUIS;
							else
								switch (clan.getLeaderSubPledge(player.getObjectId()))
								{
									case L2Clan.SUBUNIT_ROYAL1:
									case L2Clan.SUBUNIT_ROYAL2:
										pledgeClass = L2Clan.RANK_COUNT;
										break;
									case L2Clan.SUBUNIT_KNIGHT1:
									case L2Clan.SUBUNIT_KNIGHT2:
									case L2Clan.SUBUNIT_KNIGHT3:
									case L2Clan.SUBUNIT_KNIGHT4:
										pledgeClass = L2Clan.RANK_VISCOUNT;
										break;
									default:
										pledgeClass = L2Clan.RANK_BARON;
										break;
								}
							break;
					}
					break;
				case 9:
					switch (player.getSubPledgeType())
					{
						case L2Clan.SUBUNIT_ACADEMY:
							pledgeClass = L2Clan.RANK_VASSAL;
							break;
						case L2Clan.SUBUNIT_ROYAL1:
						case L2Clan.SUBUNIT_ROYAL2:
							pledgeClass = L2Clan.RANK_BARON;
							break;
						case L2Clan.SUBUNIT_KNIGHT1:
						case L2Clan.SUBUNIT_KNIGHT2:
						case L2Clan.SUBUNIT_KNIGHT3:
						case L2Clan.SUBUNIT_KNIGHT4:
							pledgeClass = L2Clan.RANK_ELDER;
							break;
						case L2Clan.SUBUNIT_NONE:
							if (player.isClanLeader())
								pledgeClass = L2Clan.RANK_DUKE;
							else
								switch (clan.getLeaderSubPledge(player.getObjectId()))
								{
									case L2Clan.SUBUNIT_ROYAL1:
									case L2Clan.SUBUNIT_ROYAL2:
										pledgeClass = L2Clan.RANK_MARQUIS;
										break;
									case L2Clan.SUBUNIT_KNIGHT1:
									case L2Clan.SUBUNIT_KNIGHT2:
									case L2Clan.SUBUNIT_KNIGHT3:
									case L2Clan.SUBUNIT_KNIGHT4:
										pledgeClass = L2Clan.RANK_COUNT;
										break;
									default:
										pledgeClass = L2Clan.RANK_VISCOUNT;
										break;
								}
							break;
					}
					break;
				case 10:
					switch (player.getSubPledgeType())
					{
						case L2Clan.SUBUNIT_ACADEMY:
							pledgeClass = L2Clan.RANK_VASSAL;
							break;
						case L2Clan.SUBUNIT_ROYAL1:
						case L2Clan.SUBUNIT_ROYAL2:
							pledgeClass = L2Clan.RANK_VISCOUNT;
							break;
						case L2Clan.SUBUNIT_KNIGHT1:
						case L2Clan.SUBUNIT_KNIGHT2:
						case L2Clan.SUBUNIT_KNIGHT3:
						case L2Clan.SUBUNIT_KNIGHT4:
							pledgeClass = L2Clan.RANK_BARON;
							break;
						case L2Clan.SUBUNIT_NONE:
							if (player.isClanLeader())
								pledgeClass = L2Clan.RANK_GRAND_DUKE;
							else
								switch (clan.getLeaderSubPledge(player.getObjectId()))
								{
									case L2Clan.SUBUNIT_ROYAL1:
									case L2Clan.SUBUNIT_ROYAL2:
										pledgeClass = L2Clan.RANK_DUKE;
										break;
									case L2Clan.SUBUNIT_KNIGHT1:
									case L2Clan.SUBUNIT_KNIGHT2:
									case L2Clan.SUBUNIT_KNIGHT3:
									case L2Clan.SUBUNIT_KNIGHT4:
										pledgeClass = L2Clan.RANK_MARQUIS;
										break;
									default:
										pledgeClass = L2Clan.RANK_COUNT;
										break;
								}
							break;
					}
					break;
				case 11:
					switch (player.getSubPledgeType())
					{
						case L2Clan.SUBUNIT_ACADEMY:
							pledgeClass = L2Clan.RANK_VASSAL;
							break;
						case L2Clan.SUBUNIT_ROYAL1:
						case L2Clan.SUBUNIT_ROYAL2:
							pledgeClass = L2Clan.RANK_COUNT;
							break;
						case L2Clan.SUBUNIT_KNIGHT1:
						case L2Clan.SUBUNIT_KNIGHT2:
						case L2Clan.SUBUNIT_KNIGHT3:
						case L2Clan.SUBUNIT_KNIGHT4:
							pledgeClass = L2Clan.RANK_VISCOUNT;
							break;
						case L2Clan.SUBUNIT_NONE:
							if (player.isClanLeader())
								pledgeClass = L2Clan.RANK_DISTINGUISHED_KING;
							else
								switch (clan.getLeaderSubPledge(player.getObjectId()))
								{
									case L2Clan.SUBUNIT_ROYAL1:
									case L2Clan.SUBUNIT_ROYAL2:
										pledgeClass = L2Clan.RANK_GRAND_DUKE;
										break;
									case L2Clan.SUBUNIT_KNIGHT1:
									case L2Clan.SUBUNIT_KNIGHT2:
									case L2Clan.SUBUNIT_KNIGHT3:
									case L2Clan.SUBUNIT_KNIGHT4:
										pledgeClass = L2Clan.RANK_DUKE;
										break;
									default:
										pledgeClass = L2Clan.RANK_MARQUIS;
										break;
								}
							break;
					}
					break;
				default: // player is in a clan; clan level < 3
					pledgeClass = L2Clan.RANK_VASSAL;
					break;
			}
		}
		return pledgeClass;
	}
	
	public void saveApprenticeAndSponsor(int apprentice, int sponsor)
	{
		try
		{
			ClanRepository.getInstance().updateMemberSponsors(getObjectId(), apprentice, sponsor);
		}
		catch (Exception e)
		{
			_log.warn("could not set apprentice/sponsor:", e);
		}
	}
}
