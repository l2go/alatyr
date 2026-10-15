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
package com.l2jfree.gameserver.gameobjects.stat;

import com.l2jfree.Config;
import com.l2jfree.gameserver.datatables.PetDataTable;
import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.L2Summon;
import com.l2jfree.gameserver.gameobjects.base.Experience;
import com.l2jfree.gameserver.gameobjects.instance.L2ClassMasterInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2PetInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2SummonInstance;
import com.l2jfree.gameserver.model.L2PetData;
import com.l2jfree.gameserver.model.quest.QuestState;
import com.l2jfree.gameserver.model.restriction.global.GlobalRestrictions;
import com.l2jfree.gameserver.model.skills.Stats;
import com.l2jfree.gameserver.model.zone.L2Zone;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.ExManagePartyRoomMember;
import com.l2jfree.gameserver.network.packets.server.ExVitalityPointInfo;
import com.l2jfree.gameserver.network.packets.server.PledgeShowMemberListUpdate;
import com.l2jfree.gameserver.network.packets.server.SocialAction;
import com.l2jfree.gameserver.network.packets.server.StatusUpdate;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;
import com.l2jfree.gameserver.network.packets.server.TutorialShowQuestionMark;
import com.l2jfree.gameserver.network.packets.server.UserInfo;

public class PlayerStat extends PlayableStat
{
	private float _vitalityPoints = 1;
	private byte _vitalityLevel = 0;
	
	public static final int VITALITY_LEVELS[] = { 240, 1800, 14600, 18200, 20000 };
	public static final int MAX_VITALITY_POINTS = VITALITY_LEVELS[4];
	public static final int MIN_VITALITY_POINTS = 1;
	
	// =========================================================
	// Data Field
	
	private int _oldMaxHp; // stats watch
	private int _oldMaxMp; // stats watch
	private int _oldMaxCp; // stats watch
	
	// =========================================================
	// Constructor
	public PlayerStat(L2Player activeChar)
	{
		super(activeChar);
	}
	
	// =========================================================
	// Method - Public
	@Override
	public boolean addExp(long value)
	{
		L2Player activeChar = getActiveChar();
		
		//Player is Gm and acces level is below or equal to GM_DONT_TAKE_EXPSP and is in party, don't give Xp
		if (getActiveChar().isGM() && getActiveChar().getAccessLevel() <= Config.GM_DONT_TAKE_EXPSP
				&& getActiveChar().isInParty())
			return false;
		
		if (!super.addExp(value))
			return false;
		
		// Set new karma
		if (!activeChar.isCursedWeaponEquipped() && activeChar.getKarma() > 0
				&& (activeChar.isGM() || !activeChar.isInsideZone(L2Zone.FLAG_PVP)))
		{
			int karmaLost = activeChar.calculateKarmaLost((int)value);
			if (karmaLost > 0)
				activeChar.setKarma(activeChar.getKarma() - karmaLost);
		}
		
		//StatusUpdate su = new StatusUpdate(activeChar.getObjectId());
		//su.addAttribute(StatusUpdate.EXP, getExp());
		//activeChar.sendPacket(su);
		activeChar.sendPacket(new UserInfo(activeChar));
		
		return true;
	}
	
	/**
	 * Add Experience and SP rewards to the L2Player, remove its Karma (if necessary) and Launch increase level task.<BR><BR>
	 *
	 * <B><U> Actions </U> :</B><BR><BR>
	 * <li>Remove Karma when the player kills L2MonsterInstance</li>
	 * <li>Send a Server->Client packet StatusUpdate to the L2Player</li>
	 * <li>Send a Server->Client System Message to the L2Player </li>
	 * <li>If the L2Player increases it's level, send a Server->Client packet SocialAction (broadcast) </li>
	 * <li>If the L2Player increases it's level, manage the increase level task (Max MP, Max MP, Recommandation, Expertise and beginner skills...) </li>
	 * <li>If the L2Player increases it's level, send a Server->Client packet UserInfo to the L2Player </li><BR><BR>
	 *
	 * @param addToExp The Experience value to add
	 * @param addToSp The SP value to add
	 */
	@Override
	public boolean addExpAndSp(long addToExp, int addToSp)
	{
		float ratioTakenByPet = 0;
		//Player is Gm and acces level is below or equal to GM_DONT_TAKE_EXPSP and is in party, don't give Xp/Sp
		L2Player activeChar = getActiveChar();
		if (activeChar.isGM() && activeChar.getAccessLevel() <= Config.GM_DONT_TAKE_EXPSP && activeChar.isInParty())
			return false;
		
		// if this player has a pet that takes from the owner's Exp, give the pet Exp now
		
		if (activeChar.getPet() instanceof L2PetInstance)
		{
			L2PetInstance pet = (L2PetInstance)activeChar.getPet();
			ratioTakenByPet = pet.getPetData().getOwnerExpTaken();
			
			// only give exp/sp to the pet by taking from the owner if the pet has a non-zero, positive ratio
			// allow possible customizations that would have the pet earning more than 100% of the owner's exp/sp
			if (ratioTakenByPet > 0 && !pet.isDead())
				pet.addExpAndSp((long)(addToExp * ratioTakenByPet), (int)(addToSp * ratioTakenByPet));
			// now adjust the max ratio to avoid the owner earning negative exp/sp
			if (ratioTakenByPet > 1)
				ratioTakenByPet = 1;
			addToExp = (long)(addToExp * (1 - ratioTakenByPet));
			addToSp = (int)(addToSp * (1 - ratioTakenByPet));
		}
		
		if (!super.addExpAndSp(addToExp, addToSp))
			return false;
		
		if (addToExp == 0 && addToSp > 0)
		{
			SystemMessage sm = new SystemMessage(SystemMessageId.ACQUIRED_S1_SP);
			sm.addNumber(addToSp);
			activeChar.sendPacket(sm);
		}
		else if (addToExp > 0 && addToSp == 0)
		{
			SystemMessage sm = new SystemMessage(SystemMessageId.EARNED_S1_EXPERIENCE);
			sm.addExpNumber(addToExp);
			activeChar.sendPacket(sm);
		}
		else
		{
			SystemMessage sm = new SystemMessage(SystemMessageId.YOU_EARNED_S1_EXP_AND_S2_SP);
			sm.addExpNumber(addToExp);
			sm.addNumber(addToSp);
			activeChar.sendPacket(sm);
		}
		
		return true;
	}
	
	public boolean addExpAndSp(long addToExp, int addToSp, boolean useVitality)
	{
		if (useVitality && Config.ENABLE_VITALITY)
		{
			switch (_vitalityLevel)
			{
				case 1:
					addToExp *= Config.RATE_VITALITY_LEVEL_1;
					addToSp *= Config.RATE_VITALITY_LEVEL_1;
					break;
				case 2:
					addToExp *= Config.RATE_VITALITY_LEVEL_2;
					addToSp *= Config.RATE_VITALITY_LEVEL_2;
					break;
				case 3:
					addToExp *= Config.RATE_VITALITY_LEVEL_3;
					addToSp *= Config.RATE_VITALITY_LEVEL_3;
					break;
				case 4:
					addToExp *= Config.RATE_VITALITY_LEVEL_4;
					addToSp *= Config.RATE_VITALITY_LEVEL_4;
					break;
			}
		}
		return addExpAndSp(addToExp, addToSp);
	}
	
	@Override
	public boolean removeExpAndSp(long addToExp, int addToSp)
	{
		return removeExpAndSp(addToExp, addToSp, true);
	}
	
	public boolean removeExpAndSp(long addToExp, int addToSp, boolean sendMessage)
	{
		if (!super.removeExpAndSp(addToExp, addToSp))
			return false;
		
		if (sendMessage)
		{
			// Send a Server->Client System Message to the L2Player
			SystemMessage sm = new SystemMessage(SystemMessageId.EXP_DECREASED_BY_S1);
			sm.addNumber((int)addToExp);
			getActiveChar().sendPacket(sm);
			sm = new SystemMessage(SystemMessageId.SP_DECREASED_S1);
			sm.addNumber(addToSp);
			getActiveChar().sendPacket(sm);
		}
		return true;
	}
	
	@Override
	public final boolean addLevel(byte value)
	{
		if (getLevel() + value > Experience.MAX_LEVEL - 1)
			return false;
		
		boolean levelIncreased = super.addLevel(value);
		
		if (levelIncreased)
		{
			QuestState qs = getActiveChar().getQuestState("255_Tutorial");
			if (qs != null)
				qs.getQuest().notifyEvent("CE40", null, getActiveChar());
			
			getActiveChar().getStatus().setCurrentCp(getMaxCp());
			getActiveChar().broadcastPacket(new SocialAction(getActiveChar().getObjectId(), SocialAction.LEVEL_UP));
			getActiveChar().sendPacket(SystemMessageId.YOU_INCREASED_YOUR_LEVEL);
			
			L2ClassMasterInstance.showQuestionMark(getActiveChar());
			
			if (getActiveChar().getLevel() == 28)
				getActiveChar().sendPacket(new TutorialShowQuestionMark(1002));
			
			GlobalRestrictions.levelChanged(getActiveChar());
		}
		
		getActiveChar().rewardSkills(); // Give Expertise skill of this level
		if (getActiveChar().getClan() != null)
		{
			getActiveChar().getClan().updateClanMember(getActiveChar());
			getActiveChar().getClan().broadcastToOnlineMembers(new PledgeShowMemberListUpdate(getActiveChar()));
		}
		if (getActiveChar().isInParty())
			getActiveChar().getParty().recalculatePartyLevel(); // Recalculate the party level
			
		if (getActiveChar().getTransformation() != null)
			getActiveChar().getTransformation().onLevelUp(getActiveChar());
		
		if (getActiveChar().getPartyRoom() != null)
			getActiveChar().getPartyRoom().broadcastPacket(
					new ExManagePartyRoomMember(ExManagePartyRoomMember.MODIFIED, getActiveChar()));
		
		StatusUpdate su = new StatusUpdate(getActiveChar().getObjectId());
		su.addAttribute(StatusUpdate.LEVEL, getLevel());
		su.addAttribute(StatusUpdate.MAX_CP, getMaxCp());
		su.addAttribute(StatusUpdate.MAX_HP, getMaxHp());
		su.addAttribute(StatusUpdate.MAX_MP, getMaxMp());
		getActiveChar().sendPacket(su);
		
		// Update the overloaded status of the L2Player
		getActiveChar().refreshOverloaded();
		// Update the expertise status of the L2Player
		getActiveChar().refreshExpertisePenalty();
		
		// Send a Server->Client packet UserInfo to the L2Player
		getActiveChar().sendPacket(new UserInfo(getActiveChar()));
		
		return levelIncreased;
	}
	
	@Override
	public boolean addSp(int value)
	{
		if (!super.addSp(value))
			return false;
		/* Micht : Use of UserInfo for C5
		StatusUpdate su = new StatusUpdate(getActiveChar().getObjectId());
		su.addAttribute(StatusUpdate.SP, getSp());
		getActiveChar().sendPacket(su);
		*/
		getActiveChar().sendPacket(new UserInfo(getActiveChar()));
		return true;
	}
	
	@Override
	public final long getExpForLevel(int level)
	{
		return Experience.LEVEL[level];
	}
	
	// =========================================================
	// Method - Private
	
	// =========================================================
	// Property - Public
	@Override
	public final L2Player getActiveChar()
	{
		return (L2Player)_activeChar;
	}
	
	@Override
	public final long getExp()
	{
		if (getActiveChar().isSubClassActive())
			return getActiveChar().getSubClasses().get(getActiveChar().getClassIndex()).getExp();
		
		return super.getExp();
	}
	
	/** The experience of the base class, also while a subclass is active: what the player row stores. */
	public final long getBaseClassExp()
	{
		return super.getExp();
	}
	
	/** The level of the base class, also while a subclass is active. */
	public final byte getBaseClassLevel()
	{
		return super.getLevel();
	}
	
	/** The skill points of the base class, also while a subclass is active. */
	public final int getBaseClassSp()
	{
		return super.getSp();
	}
	
	@Override
	public final void setExp(long value)
	{
		if (getActiveChar().isSubClassActive())
			getActiveChar().getSubClasses().get(getActiveChar().getClassIndex()).setExp(value);
		else
			super.setExp(value);
	}
	
	@Override
	public final byte getLevel()
	{
		if (getActiveChar().isSubClassActive())
			return getActiveChar().getSubClasses().get(getActiveChar().getClassIndex()).getLevel();
		
		return super.getLevel();
	}
	
	@Override
	public final void setLevel(byte value)
	{
		if (value > Experience.MAX_LEVEL - 1)
			value = Experience.MAX_LEVEL - 1;
		
		if (getActiveChar().isSubClassActive())
			getActiveChar().getSubClasses().get(getActiveChar().getClassIndex()).setLevel(value);
		else
			super.setLevel(value);
	}
	
	@Override
	public final int getMaxHp()
	{
		// Get the Max HP (base+modifier) of the L2Player
		int val = super.getMaxHp();
		if (val != _oldMaxHp)
		{
			_oldMaxHp = val;
			
			// Launch a regen task if the new Max HP is higher than the old one
			if (getActiveChar().getStatus().getCurrentHp() != val)
				getActiveChar().getStatus().setCurrentHp(getActiveChar().getStatus().getCurrentHp()); // trigger start of regeneration
		}
		
		return val;
	}
	
	@Override
	public final int getMaxMp()
	{
		// Get the Max MP (base+modifier) of the L2Player
		int val = super.getMaxMp();
		
		if (val != _oldMaxMp)
		{
			_oldMaxMp = val;
			
			// Launch a regen task if the new Max MP is higher than the old one
			if (getActiveChar().getStatus().getCurrentMp() != val)
				getActiveChar().getStatus().setCurrentMp(getActiveChar().getStatus().getCurrentMp()); // trigger start of regeneration
		}
		
		return val;
	}
	
	@Override
	public final int getMaxCp()
	{
		// Get the Max CP (base+modifier) of the L2Player
		int val = super.getMaxCp();
		
		if (val != _oldMaxCp)
		{
			_oldMaxCp = val;
			
			// Launch a regen task if the new Max CP is higher than the old one
			if (getActiveChar().getStatus().getCurrentCp() != val)
				getActiveChar().getStatus().setCurrentCp(getActiveChar().getStatus().getCurrentCp()); // trigger start of regeneration
		}
		
		return val;
	}
	
	@Override
	public final int getSp()
	{
		if (getActiveChar().isSubClassActive())
			return getActiveChar().getSubClasses().get(getActiveChar().getClassIndex()).getSp();
		
		return super.getSp();
	}
	
	@Override
	public final void setSp(int value)
	{
		if (getActiveChar().isSubClassActive())
			getActiveChar().getSubClasses().get(getActiveChar().getClassIndex()).setSp(value);
		else
			super.setSp(value);
	}
	
	/**
	 * Return the RunSpeed (base+modifier) of the L2Creature in function of the
	 * Armour Expertise Penalty.
	 */
	@Override
	public int getRunSpeed()
	{
		int val = super.getRunSpeed();
		
		/**
		 * @Deprecated
		 */
		//val /= getActiveChar().getArmourExpertisePenalty();
		
		// Apply max run speed cap.
		if (val > Config.ALT_MAX_RUN_SPEED && Config.ALT_MAX_RUN_SPEED > 0 && !getActiveChar().isGM())
			return Config.ALT_MAX_RUN_SPEED;
		
		return val;
	}
	
	@Override
	protected int getBaseRunSpd()
	{
		if (getActiveChar().isMounted())
		{
			L2PetData stats =
					PetDataTable.getInstance().getPetData(getActiveChar().getMountNpcId(),
							getActiveChar().getMountLevel());
			if (stats != null)
				return stats.getPetSpeed();
		}
		
		return super.getBaseRunSpd();
	}
	
	/**
	 * Return the PAtk Speed (base+modifier) of the L2Creature in function of
	 * the Armour Expertise Penalty.
	 */
	@Override
	public int getPAtkSpd()
	{
		int val = super.getPAtkSpd();
		
		/**
		 * @Deprecated
		 */
		//val /= _activeChar.getArmourExpertisePenalty();
		
		if (val > Config.ALT_MAX_PATK_SPEED && Config.ALT_MAX_PATK_SPEED > 0 && !getActiveChar().isGM())
			return Config.ALT_MAX_PATK_SPEED;
		return val;
	}
	
	/**
	 * Return the MAtk Speed (base+modifier) of the L2Creature in function of
	 * the Armour Expertise Penalty.
	 */
	@Override
	public int getMAtkSpd()
	{
		int val = super.getMAtkSpd();
		
		/**
		 * @Deprecated
		 */
		//val /= _activeChar.getArmourExpertisePenalty();
		
		if (val > Config.ALT_MAX_MATK_SPEED && Config.ALT_MAX_MATK_SPEED > 0 && !getActiveChar().isGM())
			return Config.ALT_MAX_MATK_SPEED;
		return val;
	}
	
	/** Return the Attack Evasion rate (base+modifier) of the L2Creature. */
	@Override
	public int getEvasionRate(L2Creature target)
	{
		int val = super.getEvasionRate(target);
		
		if (val > Config.ALT_MAX_EVASION && Config.ALT_MAX_EVASION > 0 && !getActiveChar().isGM())
			return Config.ALT_MAX_EVASION;
		return val;
	}
	
	@Override
	public int getAttackElementValue(byte attribute)
	{
		int value = super.getAttackElementValue(attribute);
		
		// 20% if summon exist
		if (summonShouldHaveAttackElemental(getActiveChar().getPet()))
			return value / 5;
		
		return value;
	}
	
	public boolean summonShouldHaveAttackElemental(L2Summon pet)
	{
		return getActiveChar().getClassId().isSummoner() && pet instanceof L2SummonInstance && !pet.isDead()
				&& !getActiveChar().getExpertisePenalty();
	}
	
	@Override
	public int getWalkSpeed()
	{
		return (getRunSpeed() * 70) / 100;
	}
	
	private void updateVitalityLevel(boolean quiet)
	{
		final byte level;
		
		if (_vitalityPoints <= VITALITY_LEVELS[0])
			level = 0;
		else if (_vitalityPoints <= VITALITY_LEVELS[1])
			level = 1;
		else if (_vitalityPoints <= VITALITY_LEVELS[2])
			level = 2;
		else if (_vitalityPoints <= VITALITY_LEVELS[3])
			level = 3;
		else
			level = 4;
		
		if (!quiet && level != _vitalityLevel)
		{
			if (level < _vitalityLevel)
				getActiveChar().sendPacket(SystemMessageId.VITALITY_HAS_DECREASED);
			else
				getActiveChar().sendPacket(SystemMessageId.VITALITY_HAS_INCREASED);
			if (_vitalityPoints <= MIN_VITALITY_POINTS)
				getActiveChar().sendPacket(SystemMessageId.VITALITY_IS_EXHAUSTED);
			else if (_vitalityPoints >= MAX_VITALITY_POINTS)
				getActiveChar().sendPacket(SystemMessageId.VITALITY_IS_AT_MAXIMUM);
		}
		
		_vitalityLevel = level;
	}
	
	/*
	 * Return current vitality points in integer format
	 */
	public int getVitalityPoints()
	{
		return (int)_vitalityPoints;
	}
	
	/*
	 * Set current vitality points to this value
	 * 
	 * if quiet = true - does not send system messages
	 */
	public void setVitalityPoints(int points, boolean quiet)
	{
		points = Math.min(Math.max(points, MIN_VITALITY_POINTS), MAX_VITALITY_POINTS);
		if (points == _vitalityPoints)
			return;
		
		_vitalityPoints = points;
		updateVitalityLevel(quiet);
		getActiveChar().sendPacket(new ExVitalityPointInfo(getVitalityPoints()));
	}
	
	public void updateVitalityPoints(float points, boolean useRates, boolean quiet)
	{
		if (points == 0 || !Config.ENABLE_VITALITY)
			return;
		
		if (useRates)
		{
			byte level = getLevel();
			if (level < 10)
				return;
			
			if (points < 0) // vitality consumed
			{
				int stat = (int)calcStat(Stats.VITALITY_CONSUME_RATE, 1, getActiveChar(), null);
				
				if (stat == 0) // is vitality consumption stopped ?
					return;
				if (stat < 0) // is vitality gained ?
					points = -points;
			}
			
			if (level >= 79)
				points *= 2;
			else if (level >= 76)
				points += points / 2;
			
			if (points > 0)
			{
				// vitality increased
				points *= Config.RATE_VITALITY_GAIN;
			}
			else
			{
				// vitality decreased
				points *= Config.RATE_VITALITY_LOST;
			}
		}
		
		if (points > 0)
		{
			points = Math.min(_vitalityPoints + points, MAX_VITALITY_POINTS);
		}
		else
		{
			points = Math.max(_vitalityPoints + points, MIN_VITALITY_POINTS);
		}
		
		if (points == _vitalityPoints)
			return;
		
		_vitalityPoints = points;
		updateVitalityLevel(quiet);
	}
}
