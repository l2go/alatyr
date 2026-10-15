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
package com.l2jfree.gameserver.gameobjects.ai;

import static com.l2jfree.gameserver.gameobjects.ai.CtrlIntention.AI_INTENTION_ATTACK;
import static com.l2jfree.gameserver.gameobjects.ai.CtrlIntention.AI_INTENTION_FOLLOW;
import static com.l2jfree.gameserver.gameobjects.ai.CtrlIntention.AI_INTENTION_IDLE;

import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Creature.AIAccessor;
import com.l2jfree.gameserver.gameobjects.L2Object;
import com.l2jfree.gameserver.gameobjects.L2Summon;
import com.l2jfree.gameserver.gameobjects.instance.L2MerchantSummonInstance;
import com.l2jfree.gameserver.model.skills.L2Skill;

public class L2SummonAI extends L2CreatureAI
{
	private volatile boolean _thinking; // to prevent recursive thinking
	private boolean _startFollow = getActor().getFollowStatus();
	
	public L2SummonAI(AIAccessor accessor)
	{
		super(accessor);
	}
	
	@Override
	public L2Summon getActor()
	{
		return (L2Summon)_actor;
	}
	
	@Override
	protected void onIntentionIdle()
	{
		if (_actor instanceof L2MerchantSummonInstance)
			return;
		
		stopFollow();
		_startFollow = false;
		setIntention(CtrlIntention.AI_INTENTION_ACTIVE);
	}
	
	@Override
	protected void onIntentionActive()
	{
		if (_actor instanceof L2MerchantSummonInstance)
			return;
		
		if (_startFollow)
			setIntention(AI_INTENTION_FOLLOW, getActor().getOwner());
		else
			super.onIntentionActive();
	}
	
	private void thinkAttack()
	{
		if (_actor instanceof L2MerchantSummonInstance)
			return;
		
		final L2Creature target = getAttackTarget();
		
		if (checkTargetLostOrDead(target))
			return;
		
		if (maybeMoveToPawn(target, _actor.getPhysicalAttackRange()))
			return;
		
		clientStopMoving(null);
		_accessor.doAttack(target);
	}
	
	private void thinkCast()
	{
		if (_actor instanceof L2MerchantSummonInstance)
			return;
		
		final L2Skill skill = getCastSkill();
		final L2Creature target = getCastTarget();
		
		if (checkTargetLost(target))
			return;
		
		boolean val = _startFollow;
		if (maybeMoveToPawn(target, _actor.getMagicalAttackRange(skill)))
			return;
		
		clientStopMoving(null);
		_accessor.doCast(skill);
		getActor().setFollowStatus(false);
		setIntention(AI_INTENTION_IDLE);
		_startFollow = val;
	}
	
	private void thinkPickUp()
	{
		if (_actor instanceof L2MerchantSummonInstance)
			return;
		
		final L2Object target = getTarget();
		
		if (checkTargetLost(target))
			return;
		
		if (maybeMoveToPawn(target, 36))
			return;
		
		setIntention(AI_INTENTION_IDLE);
		((L2Summon.AIAccessor)_accessor).doPickupItem(target);
	}
	
	private void thinkInteract()
	{
		if (_actor instanceof L2MerchantSummonInstance)
			return;
		
		final L2Object target = getTarget();
		
		if (checkTargetLost(target))
			return;
		
		if (maybeMoveToPawn(target, 36))
			return;
		
		setIntention(AI_INTENTION_IDLE);
	}
	
	@Override
	protected void onEvtThink()
	{
		if (_actor instanceof L2MerchantSummonInstance)
			return;
		
		if (_thinking || _actor.isCastingNow() || _actor.isAllSkillsDisabled())
			return;
		
		_thinking = true;
		try
		{
			switch (getIntention())
			{
				case AI_INTENTION_ATTACK:
					thinkAttack();
					break;
				case AI_INTENTION_CAST:
					thinkCast();
					break;
				case AI_INTENTION_PICK_UP:
					thinkPickUp();
					break;
				case AI_INTENTION_INTERACT:
					thinkInteract();
					break;
			}
		}
		finally
		{
			_thinking = false;
		}
	}
	
	@Override
	protected void onEvtFinishCasting()
	{
		if (_actor instanceof L2MerchantSummonInstance)
			return;
		
		if (getIntention() != AI_INTENTION_ATTACK)
			getActor().setFollowStatus(_startFollow);
		
		super.onEvtFinishCasting();
	}
	
	public void notifyFollowStatusChange()
	{
		if (_actor instanceof L2MerchantSummonInstance)
			return;
		
		_startFollow = !_startFollow;
		
		switch (getIntention())
		{
			case AI_INTENTION_ACTIVE:
			case AI_INTENTION_FOLLOW:
			case AI_INTENTION_IDLE:
				getActor().setFollowStatus(_startFollow);
				break;
		}
	}
	
	public void setStartFollowController(boolean val)
	{
		if (_actor instanceof L2MerchantSummonInstance)
			return;
		
		_startFollow = val;
	}
}
