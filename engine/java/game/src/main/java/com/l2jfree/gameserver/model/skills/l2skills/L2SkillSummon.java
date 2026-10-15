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
package com.l2jfree.gameserver.model.skills.l2skills;

import com.l2jfree.gameserver.datatables.NpcTable;
import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Object;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.base.Experience;
import com.l2jfree.gameserver.gameobjects.instance.L2CubicInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2MerchantSummonInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2SiegeSummonInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2SummonInstance;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.idfactory.IdFactory;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.templates.StatsSet;

public class L2SkillSummon extends L2Skill
{
	private final int _npcId;
	private final float _expPenalty;
	private final boolean _isCubic;
	
	// cubic AI
	// Activation time for a cubic
	private final int _activationtime;
	// Activation chance for a cubic.
	private final int _activationchance;
	
	// for summon spells:
	// a) What is the total lifetime of summons (in millisecs)
	private final int _summonTotalLifeTime;
	// b) how much lifetime is lost per second of idleness (non-fighting)
	private final int _summonTimeLostIdle;
	// c) how much time is lost per second of activity (fighting)
	private final int _summonTimeLostActive;
	
	// item consume time in milliseconds
	private final int _itemConsumeTime;
	
	// item consume count over time
	private final int _itemConsumeOT;
	// item consume id over time
	private final int _itemConsumeIdOT;
	// how many times to consume an item
	private final int _itemConsumeSteps;
	
	public L2SkillSummon(StatsSet set)
	{
		super(set);
		
		_npcId = set.getInteger("npcId", 0); // default for undescribed skills
		_expPenalty = set.getFloat("expPenalty", 0.f);
		_isCubic = set.getBool("isCubic", false);
		
		_activationtime = set.getInteger("activationtime", 8);
		_activationchance = set.getInteger("activationchance", 30);
		
		_summonTotalLifeTime = set.getInteger("summonTotalLifeTime", 1200000); // 20 minutes default
		_summonTimeLostIdle = set.getInteger("summonTimeLostIdle", 0);
		_summonTimeLostActive = set.getInteger("summonTimeLostActive", 0);
		
		_itemConsumeOT = set.getInteger("itemConsumeCountOT", 0);
		_itemConsumeIdOT = set.getInteger("itemConsumeIdOT", 0);
		_itemConsumeTime = set.getInteger("itemConsumeTime", 0);
		_itemConsumeSteps = set.getInteger("itemConsumeSteps", 0);
	}
	
	/**
	 * @return Returns the activation time for a cubic.
	 */
	public final int getActivationTime()
	{
		return _activationtime;
	}
	
	/**
	 * @return Returns the activation chance for a cubic.
	 */
	public final int getActivationChance()
	{
		return _activationchance;
	}
	
	@Override
	public boolean checkCondition(L2Creature activeChar, L2Object target)
	{
		if (activeChar instanceof L2Player)
		{
			L2Player player = (L2Player)activeChar;
			if (isCubic())
			{
				// Player is always able to cast mass cubic skill
				if (getTargetType() == L2Skill.SkillTargetType.TARGET_SELF)
				{
					int mastery = player.getSkillLevel(L2Skill.SKILL_CUBIC_MASTERY);
					if (mastery < 0)
						mastery = 0;
					int count = player.getCubics().size();
					if (count > mastery)
					{
						player.sendPacket(SystemMessageId.CUBIC_SUMMONING_FAILED);
						return false;
					}
				}
			}
			else
			{
				if (player.inObserverMode())
					return false;
				
				if (player.getPet() != null)
				{
					player.sendPacket(SystemMessageId.SUMMON_ONLY_ONE);
					return false;
				}
			}
		}
		
		return super.checkCondition(activeChar, target);
	}
	
	@Override
	public void useSkill(L2Creature caster, L2Creature... targets)
	{
		if (caster.isAlikeDead() || !(caster instanceof L2Player))
			return;
		
		L2Player activeChar = (L2Player)caster;
		
		if (_npcId == 0)
		{
			activeChar.sendMessage("Summon skill " + getId() + " not implemented yet.");
			return;
		}
		
		if (isCubic())
		{
			if (targets.length > 1) //Mass cubic skill
			{
				for (L2Creature obj : targets)
				{
					if (!(obj instanceof L2Player))
						continue;
					L2Player player = ((L2Player)obj);
					int mastery = player.getSkillLevel(L2Skill.SKILL_CUBIC_MASTERY);
					if (mastery < 0)
						mastery = 0;
					if (mastery == 0 && !player.getCubics().isEmpty())
					{
						//Player can have only 1 cubic - we shuld replace old cubic with new one
						for (L2CubicInstance c : player.getCubics().values())
						{
							c.stopAction();
							c = null;
						}
						player.getCubics().clear();
					}
					//TODO: Should remove first cubic summoned and replace with new cubic
					if (player.getCubics().containsKey(_npcId))
					{
						L2CubicInstance cubic = player.getCubic(_npcId);
						cubic.stopAction();
						cubic.cancelDisappear();
						player.delCubic(_npcId);
					}
					if (player.getCubics().size() > mastery)
						continue;
					player.addCubic(this);
					player.broadcastUserInfo();
				}
				return;
			}
			
			//normal cubic skill
			int mastery = activeChar.getSkillLevel(L2Skill.SKILL_CUBIC_MASTERY);
			if (mastery < 0)
				mastery = 0;
			if (activeChar.getCubics().containsKey(_npcId))
			{
				L2CubicInstance cubic = activeChar.getCubic(_npcId);
				cubic.stopAction();
				cubic.cancelDisappear();
				activeChar.delCubic(_npcId);
			}
			if (activeChar.getCubics().size() > mastery)
			{
				if (_log.isDebugEnabled())
					_log.debug("player can't summon any more cubics. ignore summon skill");
				activeChar.sendPacket(SystemMessageId.CUBIC_SUMMONING_FAILED);
				return;
			}
			activeChar.addCubic(this);
			activeChar.broadcastUserInfo();
			return;
		}
		
		if (activeChar.getPet() != null || activeChar.isMounted())
		{
			if (_log.isDebugEnabled())
				_log.debug("player has a pet already. ignore summon skill");
			return;
		}
		
		L2SummonInstance summon;
		L2NpcTemplate summonTemplate = NpcTable.getInstance().getTemplate(_npcId);
		if (summonTemplate == null)
		{
			_log.warn("Summon attempt for nonexisting NPC ID:" + _npcId + ", skill ID:" + getId());
			return; // npcID doesn't exist
		}
		if (summonTemplate.isAssignableTo(L2SiegeSummonInstance.class))
			summon = new L2SiegeSummonInstance(IdFactory.getInstance().getNextId(), summonTemplate, activeChar, this);
		else if (summonTemplate.isAssignableTo(L2MerchantSummonInstance.class))
			summon =
					new L2MerchantSummonInstance(IdFactory.getInstance().getNextId(), summonTemplate, activeChar, this);
		else
			summon = new L2SummonInstance(IdFactory.getInstance().getNextId(), summonTemplate, activeChar, this);
		
		summon.setName(summonTemplate.getName());
		summon.setTitle(activeChar.getName());
		summon.setExpPenalty(_expPenalty);
		if (summon.getLevel() >= Experience.LEVEL.length)
		{
			summon.getStat().setExp(Experience.LEVEL[Experience.LEVEL.length - 1]);
			_log.warn("Summon (" + summon.getName() + ") NpcID: " + summon.getNpcId()
					+ " has a level above 75. Please rectify.");
		}
		else
		{
			summon.getStat().setExp(Experience.LEVEL[(summon.getLevel() % Experience.LEVEL.length)]);
		}
		summon.getStatus().setCurrentHp(summon.getMaxHp());
		summon.getStatus().setCurrentMp(summon.getMaxMp());
		summon.setHeading(activeChar.getHeading());
		summon.setRunning();
		if (!(summon instanceof L2MerchantSummonInstance))
			activeChar.setPet(summon);
		
		L2World.getInstance().storeObject(summon);
		summon.spawnMe(activeChar.getX() + 50, activeChar.getY() + 100, activeChar.getZ());
	}
	
	public final int getNpcId()
	{
		return _npcId;
	}
	
	public final boolean isCubic()
	{
		return _isCubic;
	}
	
	/**
	 * @return Returns the itemConsume count over time.
	 */
	public final int getTotalLifeTime()
	{
		return _summonTotalLifeTime;
	}
	
	/**
	 * @return Returns the itemConsume count over time.
	 */
	public final int getTimeLostIdle()
	{
		return _summonTimeLostIdle;
	}
	
	/**
	 * @return Returns the itemConsumeId over time.
	 */
	public final int getTimeLostActive()
	{
		return _summonTimeLostActive;
	}
	
	/**
	 * @return Returns the itemConsume count over time.
	 */
	public final int getItemConsumeOT()
	{
		return _itemConsumeOT;
	}
	
	/**
	 * @return Returns the itemConsumeId over time.
	 */
	public final int getItemConsumeIdOT()
	{
		return _itemConsumeIdOT;
	}
	
	/**
	 * @return Returns the itemConsume count over time.
	 */
	public final int getItemConsumeSteps()
	{
		return _itemConsumeSteps;
	}
	
	/**
	 * @return Returns the itemConsume time in milliseconds.
	 */
	public final int getItemConsumeTime()
	{
		return _itemConsumeTime;
	}
	
}
