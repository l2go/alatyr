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

import static com.l2jfree.gameserver.gameobjects.ai.CtrlIntention.AI_INTENTION_IDLE;

import java.util.concurrent.Future;

import com.l2jfree.gameserver.ThreadPoolManager;
import com.l2jfree.gameserver.datatables.SkillTable;
import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Object;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.ai.CtrlIntention;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.skills.templates.L2SkillType;
import com.l2jfree.gameserver.network.packets.server.StopMove;
import com.l2jfree.tools.geometry.Point3D;
import com.l2jfree.tools.random.Rnd;

/**
 * While a tamed beast behaves a lot like a pet (ingame) and does have
 * an owner, in all other aspects, it acts like a mob.
 * In addition, it can be fed in order to increase its duration.
 * This class handles the running tasks, AI, and feed of the mob.
 * The (mostly optional) AI on feeding the spawn is handled by the datapack ai script
 */
public final class L2TamedBeastInstance extends L2FeedableBeastInstance
{
	private int _foodSkillId;
	private static final int MAX_DISTANCE_FROM_HOME = 30000;
	private static final int MAX_DISTANCE_FROM_OWNER = 2000;
	private static final int MAX_DURATION = 1200000; // 20 minutes
	private static final int DURATION_CHECK_INTERVAL = 60000; // 1 minute
	private static final int DURATION_INCREASE_INTERVAL = 20000; // 20 secs (gained upon feeding)
	private static final int BUFF_INTERVAL = 5000; // 5 seconds
	private int _remainingTime = MAX_DURATION;
	private int _homeX, _homeY, _homeZ;
	private L2Player _owner;
	private Future<?> _buffTask = null;
	private Future<?> _durationCheckTask = null;
	
	public L2TamedBeastInstance(int objectId, L2NpcTemplate template)
	{
		super(objectId, template);
		setHome(this);
	}
	
	public L2TamedBeastInstance(int objectId, L2NpcTemplate template, L2Player owner, int foodSkillId, int x,
			int y, int z)
	{
		super(objectId, template);
		
		getStatus().setCurrentHp(getMaxHp());
		getStatus().setCurrentMp(getMaxMp());
		setOwner(owner);
		setFoodType(foodSkillId);
		setHome(x, y, z);
		spawnMe(x, y, z);
	}
	
	public void onReceiveFood()
	{
		// Eating food extends the duration by 20secs, to a max of 20minutes
		_remainingTime = _remainingTime + DURATION_INCREASE_INTERVAL;
		if (_remainingTime > MAX_DURATION)
			_remainingTime = MAX_DURATION;
	}
	
	public Point3D getHome()
	{
		return new Point3D(_homeX, _homeY, _homeZ);
	}
	
	public void setHome(int x, int y, int z)
	{
		_homeX = x;
		_homeY = y;
		_homeZ = z;
	}
	
	public void setHome(L2Creature c)
	{
		setHome(c.getX(), c.getY(), c.getZ());
	}
	
	public int getRemainingTime()
	{
		return _remainingTime;
	}
	
	public void setRemainingTime(int duration)
	{
		_remainingTime = duration;
	}
	
	public int getFoodType()
	{
		return _foodSkillId;
	}
	
	public void setFoodType(int foodItemId)
	{
		if (foodItemId > 0)
		{
			_foodSkillId = foodItemId;
			
			// Start the duration checks
			// Start the buff tasks
			if (_durationCheckTask != null)
				_durationCheckTask.cancel(true);
			_durationCheckTask =
					ThreadPoolManager.getInstance().scheduleGeneralAtFixedRate(new CheckDuration(this),
							DURATION_CHECK_INTERVAL, DURATION_CHECK_INTERVAL);
		}
	}
	
	@Override
	public boolean doDie(L2Creature killer)
	{
		if (!super.doDie(killer))
			return false;
		
		getAI().stopFollow();
		if (_buffTask != null)
			_buffTask.cancel(false);
		if (_durationCheckTask != null)
			_durationCheckTask.cancel(false);
		
		// Clean up variables
		if (_owner != null)
			_owner.setTrainedBeast(null);
		
		_buffTask = null;
		_durationCheckTask = null;
		_owner = null;
		_foodSkillId = 0;
		_remainingTime = 0;
		return true;
	}
	
	public L2Player getOwner()
	{
		return _owner;
	}
	
	public void setOwner(L2Player owner)
	{
		if (owner != null)
		{
			_owner = owner;
			setTitle(owner.getName());
			// Broadcast the new title
			broadcastFullInfo();
			
			owner.setTrainedBeast(this);
			
			// Always and automatically follow the owner.
			getAI().startFollow(_owner);
			
			// Instead of calculating this value each time, let's get this now and pass it on
			int totalBuffsAvailable = 0;
			for (L2Skill skill : getAllSkills())
			{
				// If the skill is a buff, check if the owner has it already [  owner.getEffect(L2Skill skill) ]
				if (skill.getSkillType() == L2SkillType.BUFF)
					totalBuffsAvailable++;
			}
			
			// Start the buff tasks
			if (_buffTask != null)
				_buffTask.cancel(false);
			_buffTask =
					ThreadPoolManager.getInstance().scheduleGeneralAtFixedRate(
							new CheckOwnerBuffs(this, totalBuffsAvailable), BUFF_INTERVAL, BUFF_INTERVAL);
		}
		else
		{
			doDespawn(); // Despawn if no owner
		}
	}
	
	public boolean isTooFarFromHome()
	{
		return !(isInsideRadius(_homeX, _homeY, _homeZ, MAX_DISTANCE_FROM_HOME, true, true));
	}
	
	public void doDespawn()
	{
		// Stop running tasks
		getAI().stopFollow();
		if (_buffTask != null)
			_buffTask.cancel(false);
		if (_durationCheckTask != null)
			_durationCheckTask.cancel(false);
		getStatus().stopHpMpRegeneration();
		
		// Clean up variables
		if (_owner != null)
			_owner.setTrainedBeast(null);
		
		setTarget(null);
		_buffTask = null;
		_durationCheckTask = null;
		_owner = null;
		_foodSkillId = 0;
		_remainingTime = 0;
		
		// Remove the spawn
		onDecay();
	}
	
	// Notification triggered by the owner when the owner is attacked.
	// Tamed mobs will heal/recharge or debuff the enemy according to their skills
	public void onOwnerGotAttacked(L2Creature attacker)
	{
		// Check if the owner is no longer around...if so, despawn
		if ((_owner == null) || (_owner.isOnline() == 0))
		{
			doDespawn();
			return;
		}
		// If the owner is too far away, stop anything else and immediately run towards the owner.
		if (!_owner.isInsideRadius(this, MAX_DISTANCE_FROM_OWNER, true, true))
		{
			getAI().startFollow(_owner);
			return;
		}
		// If the owner is dead, do nothing...
		if (_owner.isDead())
			return;
		
		if (attacker == null)
			return;
		
		// If the tamed beast is currently in the middle of casting, let it complete its skill...
		if (isCastingNow())
			return;
		
		float HPRatio = ((float)_owner.getStatus().getCurrentHp()) / _owner.getMaxHp();
		
		// If the owner has a lot of HP, then debuff the enemy with a random debuff among the available skills
		// use of more than one debuff at this moment is acceptable
		if (HPRatio >= 0.8)
		{
			for (L2Skill skill : getAllSkills())
			{
				// If the skill is a debuff, check if the attacker has it already [  attacker.getEffect(L2Skill skill) ]
				if ((skill.getSkillType() == L2SkillType.DEBUFF) && Rnd.get(3) < 1
						&& (attacker.getFirstEffect(skill) != null))
				{
					sitCastAndFollow(skill, attacker);
				}
			}
		}
		// For HP levels between 80% and 50%, do not react to attack events (so that MP can regenerate a bit)
		// For lower HP ranges, heal or recharge the owner with 1 skill use per attack.
		else if (HPRatio < 0.5)
		{
			int chance = 1;
			if (HPRatio < 0.25)
				chance = 2;
			
			// If the owner has a lot of HP, then debuff the enemy with a random debuff among the available skills
			for (L2Skill skill : getAllSkills())
			{
				// If the skill is a buff, check if the owner has it already [  owner.getEffect(L2Skill skill) ]
				if ((Rnd.get(5) < chance)
						&& ((skill.getSkillType() == L2SkillType.HEAL) || (skill.getSkillType() == L2SkillType.HOT)
								|| (skill.getSkillType() == L2SkillType.BALANCE_LIFE)
								|| (skill.getSkillType() == L2SkillType.HEAL_PERCENT)
								|| (skill.getSkillType() == L2SkillType.HEAL_STATIC)
								|| (skill.getSkillType() == L2SkillType.COMBATPOINTHEAL)
								|| (skill.getSkillType() == L2SkillType.CPHOT)
								|| (skill.getSkillType() == L2SkillType.MANAHEAL)
								|| (skill.getSkillType() == L2SkillType.MANAHEAL_PERCENT)
								|| (skill.getSkillType() == L2SkillType.MANARECHARGE) || (skill.getSkillType() == L2SkillType.MPHOT)))
				{
					sitCastAndFollow(skill, _owner);
					return;
				}
			}
		}
	}
	
	/**
	 * Prepare and cast a skill:
	 *   First smoothly prepare the beast for casting, by abandoning other actions
	 *   Next, call super.doCast(skill) in order to actually cast the spell
	 *   Finally, return to auto-following the owner.
	 * 
	 * @see com.l2jfree.gameserver.gameobjects.L2Creature#doCast(com.l2jfree.gameserver.model.skills.L2Skill)
	 */
	protected void sitCastAndFollow(L2Skill skill, L2Creature target)
	{
		stopMove(null);
		broadcastPacket(new StopMove(this));
		getAI().setIntention(AI_INTENTION_IDLE);
		
		setTarget(target);
		doCast(skill);
		getAI().setIntention(CtrlIntention.AI_INTENTION_FOLLOW, _owner);
	}
	
	private class CheckDuration implements Runnable
	{
		private final L2TamedBeastInstance _tamedBeast;
		
		CheckDuration(L2TamedBeastInstance tamedBeast)
		{
			_tamedBeast = tamedBeast;
		}
		
		@Override
		public void run()
		{
			int foodTypeSkillId = _tamedBeast.getFoodType();
			L2Player owner = _tamedBeast.getOwner();
			_tamedBeast.setRemainingTime(_tamedBeast.getRemainingTime() - DURATION_CHECK_INTERVAL);
			
			// I tried to avoid this as much as possible...but it seems I can't avoid hardcoding
			// ids further, except by carrying an additional variable just for these two lines...
			// Find which food item needs to be consumed.
			L2ItemInstance item = null;
			if (foodTypeSkillId == 2188)
				item = owner.getInventory().getItemByItemId(6643);
			else if (foodTypeSkillId == 2189)
				item = owner.getInventory().getItemByItemId(6644);
			
			// If the owner has enough food, call the item handler (use the food and triffer all necessary actions)
			if (item != null && item.getCount() >= 1)
			{
				L2Object oldTarget = owner.getTarget();
				owner.setTarget(_tamedBeast);
				L2Creature[] targets = { _tamedBeast };
				
				// Emulate a call to the owner using food, but bypass all checks for range, etc
				// this also causes a call to the AI tasks handling feeding, which may call onReceiveFood as required.
				owner.callSkill(SkillTable.getInstance().getInfo(foodTypeSkillId, 1), targets);
				owner.setTarget(oldTarget);
			}
			else
			{
				// If the owner has no food, the beast immediately despawns, except when it was only
				// newly spawned.  Newly spawned beasts can last up to 5 minutes
				if (_tamedBeast.getRemainingTime() < MAX_DURATION - 300000)
					_tamedBeast.setRemainingTime(-1);
			}
			
			/* There are too many conflicting reports about whether distance from home should
			* be taken into consideration.  Disabled for now.
			* 
			if (_tamedBeast.isTooFarFromHome())
				_tamedBeast.setRemainingTime(-1);
			*/
			
			if (_tamedBeast.getRemainingTime() <= 0)
				_tamedBeast.doDespawn();
		}
	}
	
	private class CheckOwnerBuffs implements Runnable
	{
		private final L2TamedBeastInstance _tamedBeast;
		private final int _numBuffs;
		
		CheckOwnerBuffs(L2TamedBeastInstance tamedBeast, int numBuffs)
		{
			_tamedBeast = tamedBeast;
			_numBuffs = numBuffs;
		}
		
		@Override
		public void run()
		{
			L2Player owner = _tamedBeast.getOwner();
			
			// Check if the owner is no longer around...if so, despawn
			if ((owner == null) || (owner.isOnline() == 0))
			{
				doDespawn();
				return;
			}
			// If the owner is too far away, stop anything else and immediately run towards the owner.
			if (!isInsideRadius(owner, MAX_DISTANCE_FROM_OWNER, true, true))
			{
				getAI().startFollow(owner);
				return;
			}
			// If the owner is dead, do nothing...
			if (owner.isDead())
				return;
			// If the tamed beast is currently casting a spell, do not interfere (do not attempt to cast anything new yet).
			if (isCastingNow())
				return;
			
			int totalBuffsOnOwner = 0;
			int i = 0;
			int rand = Rnd.get(_numBuffs);
			L2Skill buffToGive = null;
			
			// Get this npc's skills:  getSkills()
			for (L2Skill skill : getAllSkills())
			{
				// If the skill is a buff, check if the owner has it already [  owner.getEffect(L2Skill skill) ]
				if (skill.getSkillType() == L2SkillType.BUFF)
				{
					if (i++ == rand)
						buffToGive = skill;
					if (owner.getFirstEffect(skill) != null)
					{
						totalBuffsOnOwner++;
					}
				}
			}
			// If the owner has less than 60% of this beast's available buff, cast a random buff
			if (_numBuffs * 2 / 3 > totalBuffsOnOwner)
			{
				_tamedBeast.sitCastAndFollow(buffToGive, owner);
			}
			getAI().setIntention(CtrlIntention.AI_INTENTION_FOLLOW, _tamedBeast.getOwner());
		}
	}
}
