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
package com.l2jfree.gameserver.gameobjects.instance;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.gameserver.ThreadPoolManager;
import com.l2jfree.gameserver.datatables.SkillTable;
import com.l2jfree.gameserver.gameobjects.L2Attackable;
import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Object;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.handler.SkillHandler;
import com.l2jfree.gameserver.instancemanager.DuelManager;
import com.l2jfree.gameserver.model.olympiad.Olympiad;
import com.l2jfree.gameserver.model.party.L2Party;
import com.l2jfree.gameserver.model.restriction.global.GlobalRestrictions;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.skills.effects.L2Effect;
import com.l2jfree.gameserver.model.zone.L2Zone;
import com.l2jfree.gameserver.network.packets.server.MagicSkillUse;
import com.l2jfree.gameserver.taskmanager.AttackStanceTaskManager;
import com.l2jfree.gameserver.util.Util;
import com.l2jfree.tools.random.Rnd;

public class L2CubicInstance
{
	protected static Logger _log = LoggerFactory.getLogger(L2CubicInstance.class);
	
	// Type of Cubics
	public static final int STORM_CUBIC = 1;
	public static final int VAMPIRIC_CUBIC = 2;
	public static final int LIFE_CUBIC = 3;
	public static final int VIPER_CUBIC = 4;
	public static final int POLTERGEIST_CUBIC = 5;
	public static final int BINDING_CUBIC = 6;
	public static final int AQUA_CUBIC = 7;
	public static final int SPARK_CUBIC = 8;
	public static final int ATTRACT_CUBIC = 9;
	public static final int SMART_CUBIC_EVATEMPLAR = 10;
	public static final int SMART_CUBIC_SHILLIENTEMPLAR = 11;
	public static final int SMART_CUBIC_ARCANALORD = 12;
	public static final int SMART_CUBIC_ELEMENTALMASTER = 13;
	public static final int SMART_CUBIC_SPECTRALMASTER = 14;
	
	// Max range of cubic skills
	// TODO: Check/fix the max range
	public static final int MAX_MAGIC_RANGE = 900;
	
	// Cubic skills
	public static final int SKILL_CUBIC_HEAL = 4051;
	public static final int SKILL_CUBIC_CURE = 5579;
	
	protected L2Player _owner;
	protected L2Creature _target;
	
	protected int _id;
	protected int _matk;
	protected int _activationtime;
	protected int _activationchance;
	protected boolean _active;
	
	protected List<L2Skill> _skills = new ArrayList<L2Skill>();
	
	private Future<?> _disappearTask;
	private Future<?> _actionTask;
	
	public L2CubicInstance(L2Player owner, int id, int level, int mAtk, int activationtime, int activationchance,
			int totallifetime)
	{
		_owner = owner;
		_id = id;
		_matk = mAtk;
		_activationtime = activationtime * 1000;
		_activationchance = activationchance;
		_active = false;
		
		switch (_id)
		{
			case STORM_CUBIC:
				_skills.add(SkillTable.getInstance().getInfo(4049, level));
				break;
			case VAMPIRIC_CUBIC:
				_skills.add(SkillTable.getInstance().getInfo(4050, level));
				break;
			case LIFE_CUBIC:
				_skills.add(SkillTable.getInstance().getInfo(4051, level));
				doAction();
				break;
			case VIPER_CUBIC:
				_skills.add(SkillTable.getInstance().getInfo(4052, level));
				break;
			case POLTERGEIST_CUBIC:
				_skills.add(SkillTable.getInstance().getInfo(4053, level));
				_skills.add(SkillTable.getInstance().getInfo(4054, level));
				_skills.add(SkillTable.getInstance().getInfo(4055, level));
				break;
			case BINDING_CUBIC:
				_skills.add(SkillTable.getInstance().getInfo(4164, level));
				break;
			case AQUA_CUBIC:
				_skills.add(SkillTable.getInstance().getInfo(4165, level));
				break;
			case SPARK_CUBIC:
				_skills.add(SkillTable.getInstance().getInfo(4166, level));
				break;
			case ATTRACT_CUBIC:
				_skills.add(SkillTable.getInstance().getInfo(5115, level));
				_skills.add(SkillTable.getInstance().getInfo(5116, level));
				break;
			case SMART_CUBIC_ARCANALORD:
				/*
				 _skills.add(SkillTable.getInstance().getInfo(4049,8)); no animation
				 _skills.add(SkillTable.getInstance().getInfo(4050,7)); no animation
				*/
				
				_skills.add(SkillTable.getInstance().getInfo(4051, 7)); // have animation
				
				/*
				_skills.add(SkillTable.getInstance().getInfo(4052,6)); no animation
				_skills.add(SkillTable.getInstance().getInfo(4053,8)); no animation
				_skills.add(SkillTable.getInstance().getInfo(4054,8)); no animation
				_skills.add(SkillTable.getInstance().getInfo(4055,8)); no animation
				_skills.add(SkillTable.getInstance().getInfo(4164,9)); no animation
				*/
				
				_skills.add(SkillTable.getInstance().getInfo(4165, 9)); // have animation
				
				/*
				_skills.add(SkillTable.getInstance().getInfo(4166,9)); no animation
				_skills.add(SkillTable.getInstance().getInfo(5115,4)); no animation
				_skills.add(SkillTable.getInstance().getInfo(5116,4)); no animation
				_skills.add(SkillTable.getInstance().getInfo(5579,4)); no animation
				--> need to add to the cubic skills list
				*/
				break;
			case SMART_CUBIC_ELEMENTALMASTER:
				_skills.add(SkillTable.getInstance().getInfo(4049, 8)); // have animation
				
				/*
				_skills.add(SkillTable.getInstance().getInfo(4050,7)); no animation
				_skills.add(SkillTable.getInstance().getInfo(4051,7)); no animation
				_skills.add(SkillTable.getInstance().getInfo(4052,6)); no animation
				_skills.add(SkillTable.getInstance().getInfo(4053,8)); no animation
				_skills.add(SkillTable.getInstance().getInfo(4054,8)); no animation
				_skills.add(SkillTable.getInstance().getInfo(4055,8)); no animation
				_skills.add(SkillTable.getInstance().getInfo(4164,9)); no animation
				_skills.add(SkillTable.getInstance().getInfo(4165,9)); no animation
				*/
				
				_skills.add(SkillTable.getInstance().getInfo(4166, 9)); // have animation
				
				/*
				_skills.add(SkillTable.getInstance().getInfo(5115,4)); no animation
				_skills.add(SkillTable.getInstance().getInfo(5116,4)); no animation
				_skills.add(SkillTable.getInstance().getInfo(5579,4)); no animation
				--> need to add to the cubic skills list
				*/
				break;
			case SMART_CUBIC_SPECTRALMASTER:
				_skills.add(SkillTable.getInstance().getInfo(4049, 8)); // have animation
				
				/*
				_skills.add(SkillTable.getInstance().getInfo(4050,7)); no animation
				_skills.add(SkillTable.getInstance().getInfo(4051,7)); no animation
				*/
				
				_skills.add(SkillTable.getInstance().getInfo(4052, 6)); // have animation
				
				/*
				_skills.add(SkillTable.getInstance().getInfo(4053,8)); no animation
				_skills.add(SkillTable.getInstance().getInfo(4054,8)); no animation
				_skills.add(SkillTable.getInstance().getInfo(4055,8)); no animation
				_skills.add(SkillTable.getInstance().getInfo(4164,9)); no animation
				_skills.add(SkillTable.getInstance().getInfo(4165,9)); no animation
				_skills.add(SkillTable.getInstance().getInfo(4166,9)); no animation
				_skills.add(SkillTable.getInstance().getInfo(5115,4)); no animation
				_skills.add(SkillTable.getInstance().getInfo(5116,4)); no animation
				_skills.add(SkillTable.getInstance().getInfo(5579,4)); no animation
				--> need to add to the cubic skills list
				*/
				break;
			case SMART_CUBIC_EVATEMPLAR:
				/*
				_skills.add(SkillTable.getInstance().getInfo(4049,8)); no animation
				_skills.add(SkillTable.getInstance().getInfo(4050,7)); no animation
				_skills.add(SkillTable.getInstance().getInfo(4051,7)); no animation
				_skills.add(SkillTable.getInstance().getInfo(4052,6)); no animation
				*/
				
				_skills.add(SkillTable.getInstance().getInfo(4053, 8)); // have animation
				
				/*
				_skills.add(SkillTable.getInstance().getInfo(4054,8)); no animation
				_skills.add(SkillTable.getInstance().getInfo(4055,8)); no animation
				_skills.add(SkillTable.getInstance().getInfo(4164,9)); no animation
				*/
				
				_skills.add(SkillTable.getInstance().getInfo(4165, 9)); // have animation
				
				/*
				_skills.add(SkillTable.getInstance().getInfo(4166,9)); no animation
				_skills.add(SkillTable.getInstance().getInfo(5115,4)); no animation
				_skills.add(SkillTable.getInstance().getInfo(5116,4)); no animation
				_skills.add(SkillTable.getInstance().getInfo(5579,4)); no animation
				--> need to add to the cubic skills list
				*/
				break;
			case SMART_CUBIC_SHILLIENTEMPLAR:
				_skills.add(SkillTable.getInstance().getInfo(4049, 8)); // have animation
				
				/*
				_skills.add(SkillTable.getInstance().getInfo(4050,7)); no animation
				_skills.add(SkillTable.getInstance().getInfo(4051,7)); no animation
				_skills.add(SkillTable.getInstance().getInfo(4052,6)); no animation
				_skills.add(SkillTable.getInstance().getInfo(4053,8)); no animation
				_skills.add(SkillTable.getInstance().getInfo(4054,8)); no animation
				_skills.add(SkillTable.getInstance().getInfo(4055,8)); no animation
				_skills.add(SkillTable.getInstance().getInfo(4164,9)); no animation
				_skills.add(SkillTable.getInstance().getInfo(4165,9)); no animation
				_skills.add(SkillTable.getInstance().getInfo(4166,9)); no animation
				*/
				
				_skills.add(SkillTable.getInstance().getInfo(5115, 4)); // have animation
				
				/*
				_skills.add(SkillTable.getInstance().getInfo(5116,4)); no animation
				_skills.add(SkillTable.getInstance().getInfo(5579,4)); no animation
				--> need to add to the cubic skills list
				*/
				break;
		}
		_disappearTask = ThreadPoolManager.getInstance().scheduleGeneral(new Disappear(), totallifetime); // Disappear in 20 mins
	}
	
	public void doAction()
	{
		if (_active)
			return;
		_active = true;
		
		switch (_id)
		{
			case AQUA_CUBIC:
			case BINDING_CUBIC:
			case SPARK_CUBIC:
			case STORM_CUBIC:
			case POLTERGEIST_CUBIC:
			case VAMPIRIC_CUBIC:
			case VIPER_CUBIC:
			case ATTRACT_CUBIC:
			case SMART_CUBIC_ARCANALORD:
			case SMART_CUBIC_ELEMENTALMASTER:
			case SMART_CUBIC_SPECTRALMASTER:
			case SMART_CUBIC_EVATEMPLAR:
			case SMART_CUBIC_SHILLIENTEMPLAR:
				_actionTask =
						ThreadPoolManager.getInstance().scheduleEffectAtFixedRate(new Action(_activationchance), 0,
								_activationtime);
				break;
			case LIFE_CUBIC:
				_actionTask = ThreadPoolManager.getInstance().scheduleEffectAtFixedRate(new Heal(), 0, _activationtime);
				break;
		}
	}
	
	public int getId()
	{
		return _id;
	}
	
	public L2Player getOwner()
	{
		return _owner;
	}
	
	public final int getMCriticalHit(L2Creature target, L2Skill skill)
	{
		// TODO: Temporary now mcrit for cubics is the baseMCritRate of its owner
		return _owner.getTemplate().getBaseMCritRate();
	}
	
	public int getMAtk()
	{
		return _matk;
	}
	
	public void stopAction()
	{
		_target = null;
		if (_actionTask != null)
		{
			_actionTask.cancel(true);
			_actionTask = null;
		}
		_active = false;
	}
	
	public void cancelDisappear()
	{
		if (_disappearTask != null)
		{
			_disappearTask.cancel(true);
			_disappearTask = null;
		}
	}
	
	/** this sets the enemy target for a cubic */
	public void getCubicTarget()
	{
		try
		{
			_target = null;
			L2Object ownerTarget = _owner.getTarget();
			if (ownerTarget == null)
				return;
			
			// Duel targeting
			if (_owner.isInDuel())
			{
				L2Player PlayerA = DuelManager.getInstance().getDuel(_owner.getDuelId()).getPlayerA();
				L2Player PlayerB = DuelManager.getInstance().getDuel(_owner.getDuelId()).getPlayerB();
				
				if (DuelManager.getInstance().getDuel(_owner.getDuelId()).isPartyDuel())
				{
					L2Party partyA = PlayerA.getParty();
					L2Party partyB = PlayerB.getParty();
					L2Party partyEnemy = null;
					
					if (partyA != null)
					{
						if (partyA.getPartyMembers().contains(_owner))
							if (partyB != null)
								partyEnemy = partyB;
							else
								_target = PlayerB;
						else
							partyEnemy = partyA;
					}
					else
					{
						if (PlayerA == _owner)
							if (partyB != null)
								partyEnemy = partyB;
							else
								_target = PlayerB;
						else
							_target = PlayerA;
					}
					if (_target == PlayerA || _target == PlayerB)
						if (_target == ownerTarget)
							return;
					
					if (partyEnemy != null)
					{
						if (partyEnemy.getPartyMembers().contains(ownerTarget))
							_target = (L2Creature)ownerTarget;
						return;
					}
				}
				if (PlayerA != _owner && ownerTarget == PlayerA)
				{
					_target = PlayerA;
					return;
				}
				if (PlayerB != _owner && ownerTarget == PlayerB)
				{
					_target = PlayerB;
					return;
				}
				_target = null;
				return;
			}
			// Olympiad targeting
			if (_owner.isInOlympiadMode())
			{
				if (_owner.isOlympiadStart())
				{
					L2Player[] players = Olympiad.getInstance().getPlayers(_owner.getOlympiadGameId());
					if (players != null)
					{
						if (_owner.getOlympiadSide() == 1)
						{
							if (ownerTarget == players[1])
								_target = players[1];
							else if (players[1].getPet() != null && ownerTarget == players[1].getPet())
								_target = players[1].getPet();
						}
						else
						{
							if (ownerTarget == players[0])
								_target = players[0];
							else if (players[0].getPet() != null && ownerTarget == players[0].getPet())
								_target = players[0].getPet();
						}
					}
				}
				return;
			}
			
			// Test owners target if it is valid then use it
			if (ownerTarget instanceof L2Creature && ownerTarget != _owner.getPet() && ownerTarget != _owner)
			{
				// target mob which has aggro on you or your summon
				if (ownerTarget instanceof L2Attackable)
				{
					if (((L2Attackable)ownerTarget).getAggroListRP().get(_owner) != null
							&& !((L2Attackable)ownerTarget).isDead())
					{
						_target = (L2Creature)ownerTarget;
						return;
					}
					if (_owner.getPet() != null)
						if (((L2Attackable)ownerTarget).getAggroListRP().get(_owner.getPet()) != null
								&& !((L2Attackable)ownerTarget).isDead())
						{
							_target = (L2Creature)ownerTarget;
							return;
						}
				}
				
				// get target in pvp or in siege
				L2Player enemy = null;
				
				if ((_owner.getPvpFlag() > 0 && !_owner.isInsideZone(L2Zone.FLAG_PEACE))
						|| _owner.isInsideZone(L2Zone.FLAG_PVP))
				{
					if (ownerTarget instanceof L2Creature && !((L2Creature)ownerTarget).isDead())
						enemy = ownerTarget.getActingPlayer();
					
					if (enemy != null)
					{
						boolean targetIt = true;
						if (_owner.getParty() != null)
						{
							if (_owner.getParty().getPartyMembers().contains(enemy))
								targetIt = false;
							else if (_owner.getParty().getCommandChannel() != null)
							{
								if (_owner.getParty().getCommandChannel().getMembers().contains(enemy))
									targetIt = false;
							}
						}
						if (_owner.getClan() != null && !_owner.isInsideZone(L2Zone.FLAG_PVP))
						{
							if (_owner.getClan().isMember(enemy.getObjectId()))
								targetIt = false;
							if (_owner.getAllyId() > 0 && enemy.getAllyId() > 0)
							{
								if (_owner.getAllyId() == enemy.getAllyId())
									targetIt = false;
							}
						}
						if (enemy.getPvpFlag() == 0 && !enemy.isInsideZone(L2Zone.FLAG_PVP))
							targetIt = false;
						if (enemy.isInsideZone(L2Zone.FLAG_PEACE))
							targetIt = false;
						if (_owner.getSiegeState() > 0 && _owner.getSiegeState() == enemy.getSiegeState())
							targetIt = false;
						if (!enemy.isVisible())
							targetIt = false;
						
						switch (GlobalRestrictions.getCombatState(_owner, enemy))
						{
							case ENEMY:
								targetIt = true;
								break;
							case FRIEND:
								targetIt = false;
								break;
						}
						
						if (targetIt)
						{
							_target = enemy;
							return;
						}
					}
				}
			}
		}
		catch (Exception e)
		{
			_log.error("GetCubicTarget:", e);
		}
	}
	
	private class Action implements Runnable
	{
		private final int _chance;
		
		public Action(int chance)
		{
			_chance = chance;
			// Run task
		}
		
		@Override
		public void run()
		{
			if (_owner.isDead() || _owner.isOnline() == 0)
			{
				stopAction();
				_owner.delCubic(_id);
				_owner.broadcastUserInfo();
				cancelDisappear();
				return;
			}
			if (!AttackStanceTaskManager.getInstance().getAttackStanceTask(_owner))
			{
				if (_owner.getPet() != null)
				{
					if (!AttackStanceTaskManager.getInstance().getAttackStanceTask(_owner.getPet()))
					{
						stopAction();
						return;
					}
				}
				else
				{
					stopAction();
					return;
				}
			}
			// Smart Cubic debuff cancel is 100%
			boolean UseCubicCure = false;
			L2Skill skill = null;
			
			if (_id >= SMART_CUBIC_EVATEMPLAR && _id <= SMART_CUBIC_SPECTRALMASTER)
			{
				L2Effect[] effects = _owner.getAllEffects();
				
				for (L2Effect e : effects)
				{
					if (e.tryNegateDebuff())
					{
						UseCubicCure = true;
					}
				}
			}
			
			if (UseCubicCure)
			{
				// Smart Cubic debuff cancel is needed, no other skill is
				// used in this activation period
				MagicSkillUse msu = new MagicSkillUse(_owner, _owner, SKILL_CUBIC_CURE, 1, 0, 0);
				_owner.broadcastPacket(msu);
			}
			else if (Rnd.get(1, 100) < _chance)
			{
				skill = _skills.get(Rnd.get(_skills.size()));
				if (skill != null)
				{
					if (skill.getId() == SKILL_CUBIC_HEAL)
					{
						// Friendly skill, so we look a target in owner's party
						setCubicTargetForHeal();
					}
					else
					{
						// Offensive skill, we look for an enemy target
						getCubicTarget();
						if (!isInCubicRange(_owner, _target))
							_target = null;
					}
					if ((_target != null) && (!_target.isDead()))
					{
						_owner.broadcastPacket(new MagicSkillUse(_owner, _target, skill.getId(), skill.getLevel(), 0, 0));
						
						if (_log.isDebugEnabled())
						{
							_log.info("L2CubicInstance: Action.run();");
							_log.info("Cubic Id: " + _id + " Target: " + _target.getName() + " distance: "
									+ Math.sqrt(_target.getDistanceSq(_owner.getX(), _owner.getY(), _owner.getZ())));
						}
						
						SkillHandler.getInstance().useCubicSkill(L2CubicInstance.this, skill, _target);
					}
				}
			}
		}
	}
	
	/** returns true if the target is inside of the owner's max Cubic range */
	public boolean isInCubicRange(L2Creature owner, L2Creature target)
	{
		if (owner == null || target == null)
			return false;
		
		if (owner == target)
			return true;
		
		// Temporary MAX_MAGIC_RANGE check until real behavior of cubics is known/coded	
		return Util.checkIfInRange(MAX_MAGIC_RANGE, owner, target, true);
	}
	
	/** this sets the friendly target for a cubic */
	public void setCubicTargetForHeal()
	{
		L2Creature target = null;
		double percentleft = 100.0;
		L2Party party = _owner.getParty();
		
		// If owner is in a duel but not in a party duel, then it is the same as
		// he does not have a party
		if (_owner.isInDuel())
			if (!DuelManager.getInstance().getDuel(_owner.getDuelId()).isPartyDuel())
				party = null;
		
		if (party != null && !_owner.isInOlympiadMode())
		{
			// Get all visible objects in a spheric area near the L2Creature
			// Get a list of Party Members
			List<L2Player> partyList = party.getPartyMembers();
			for (L2Creature partyMember : partyList)
			{
				if (!partyMember.isDead())
				{
					// If party member not dead, check if he is in castrange of heal cubic
					if (isInCubicRange(_owner, partyMember))
					{
						// Member is in cubic casting range, check if he need
						// heal and if he have the lowest HP
						if (partyMember.getStatus().getCurrentHp() < partyMember.getMaxHp())
						{
							if (percentleft > (partyMember.getStatus().getCurrentHp() / partyMember.getMaxHp()))
							{
								percentleft = (partyMember.getStatus().getCurrentHp() / partyMember.getMaxHp());
								target = partyMember;
							}
						}
					}
				}
				if (partyMember.getPet() != null)
				{
					if (partyMember.getPet().isDead())
						continue;
					
					// If party member's pet not dead, check if it is in castrange of heal cubic
					if (!isInCubicRange(_owner, partyMember.getPet()))
						continue;
					
					// Member's pet is in cubic casting range, check if he need
					// heal and if he have the lowest HP
					if (partyMember.getPet().getStatus().getCurrentHp() < partyMember.getPet().getMaxHp())
					{
						if (percentleft > (partyMember.getPet().getStatus().getCurrentHp() / partyMember.getPet()
								.getMaxHp()))
						{
							target = partyMember.getPet();
						}
					}
				}
			}
		}
		else
		{
			if (_owner.getStatus().getCurrentHp() < _owner.getMaxHp())
			{
				percentleft = (_owner.getStatus().getCurrentHp() / _owner.getMaxHp());
				target = _owner;
			}
			if (_owner.getPet() != null)
			{
				if (!_owner.getPet().isDead()
						&& _owner.getPet().getStatus().getCurrentHp() < _owner.getPet().getMaxHp()
						&& percentleft > (_owner.getPet().getStatus().getCurrentHp() / _owner.getPet().getMaxHp())
						&& isInCubicRange(_owner, _owner.getPet()))
				{
					target = _owner.getPet();
					percentleft = (_owner.getPet().getStatus().getCurrentHp() / _owner.getPet().getMaxHp());
				}
			}
		}
		_target = target;
	}
	
	private class Heal implements Runnable
	{
		// Run task
		@Override
		public void run()
		{
			if (_owner.isDead() && _owner.isOnline() == 0)
			{
				stopAction();
				_owner.delCubic(_id);
				_owner.broadcastUserInfo();
				cancelDisappear();
				return;
			}
			
			L2Skill skill = null;
			for (L2Skill sk : _skills)
			{
				if (sk.getId() == SKILL_CUBIC_HEAL)
				{
					skill = sk;
					break;
				}
			}
			
			if (skill != null)
			{
				setCubicTargetForHeal();
				L2Creature target = _target;
				if (target != null && !target.isDead())
				{
					if (target.getMaxHp() - target.getStatus().getCurrentHp() > skill.getPower())
					{
						SkillHandler.getInstance().useSkill(_owner, skill, target);
						
						MagicSkillUse msu = new MagicSkillUse(_owner, target, skill.getId(), skill.getLevel(), 0, 0);
						_owner.broadcastPacket(msu);
					}
				}
			}
		}
	}
	
	private class Disappear implements Runnable
	{
		Disappear()
		{
		}
		
		// Run task
		@Override
		public void run()
		{
			stopAction();
			_owner.delCubic(_id);
			_owner.broadcastUserInfo();
		}
	}
}
