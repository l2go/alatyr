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
package com.l2jfree.gameserver.gameobjects;

import com.l2jfree.Config;
import com.l2jfree.gameserver.gameobjects.L2Attackable.AggroInfo;
import com.l2jfree.gameserver.gameobjects.ai.CtrlIntention;
import com.l2jfree.gameserver.gameobjects.ai.L2CreatureAI;
import com.l2jfree.gameserver.gameobjects.ai.L2SummonAI;
import com.l2jfree.gameserver.gameobjects.base.Experience;
import com.l2jfree.gameserver.gameobjects.instance.L2DoorInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2MerchantSummonInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2PetInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2SummonInstance;
import com.l2jfree.gameserver.gameobjects.itemcontainer.PetInventory;
import com.l2jfree.gameserver.gameobjects.knownlist.CreatureKnownList;
import com.l2jfree.gameserver.gameobjects.knownlist.SummonKnownList;
import com.l2jfree.gameserver.gameobjects.reference.ImmutableReference;
import com.l2jfree.gameserver.gameobjects.shot.CreatureShots;
import com.l2jfree.gameserver.gameobjects.shot.SummonShots;
import com.l2jfree.gameserver.gameobjects.stat.CreatureStat;
import com.l2jfree.gameserver.gameobjects.stat.SummonStat;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.gameobjects.view.ICreatureView;
import com.l2jfree.gameserver.gameobjects.view.SummonView;
import com.l2jfree.gameserver.geodata.GeoData;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.items.templates.L2Weapon;
import com.l2jfree.gameserver.model.party.L2Party;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.skills.templates.L2SkillType;
import com.l2jfree.gameserver.model.world.L2WorldRegion;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.AbstractNpcInfo;
import com.l2jfree.gameserver.network.packets.server.ActionFailed;
import com.l2jfree.gameserver.network.packets.server.EffectInfoPacket.EffectInfoPacketList;
import com.l2jfree.gameserver.network.packets.server.ExPartyPetWindowAdd;
import com.l2jfree.gameserver.network.packets.server.ExPartyPetWindowDelete;
import com.l2jfree.gameserver.network.packets.server.ExPartyPetWindowUpdate;
import com.l2jfree.gameserver.network.packets.server.PartySpelled;
import com.l2jfree.gameserver.network.packets.server.PetDelete;
import com.l2jfree.gameserver.network.packets.server.PetInfo;
import com.l2jfree.gameserver.network.packets.server.PetItemList;
import com.l2jfree.gameserver.network.packets.server.PetStatusShow;
import com.l2jfree.gameserver.network.packets.server.PetStatusUpdate;
import com.l2jfree.gameserver.network.packets.server.StatusUpdate;
import com.l2jfree.gameserver.network.packets.server.UserInfo;
import com.l2jfree.gameserver.taskmanager.DecayTaskManager;
import com.l2jfree.gameserver.taskmanager.LeakTaskManager;
import com.l2jfree.gameserver.taskmanager.SQLQueue;

public abstract class L2Summon extends L2Playable
{
	public static final int SIEGE_GOLEM_ID = 14737;
	public static final int HOG_CANNON_ID = 14768;
	public static final int SWOOP_CANNON_ID = 14839;
	
	private L2Player _owner;
	//private int				_attackRange			= 36;		//Melee range
	private boolean _follow = true;
	private boolean _previousFollowStatus = true;
	
	// TODO: currently, all servitors use 1 shot.  However, this value should vary depending on the servitor template (id and level)!
	//private int				_soulShotsPerHit		= 1;
	//private int				_spiritShotsPerHit		= 1;
	
	public class AIAccessor extends L2Creature.AIAccessor
	{
		protected AIAccessor()
		{
		}
		
		public L2Summon getSummon()
		{
			return L2Summon.this;
		}
		
		public boolean isAutoFollow()
		{
			return getFollowStatus();
		}
		
		public void doPickupItem(L2Object object)
		{
			L2Summon.this.doPickupItem(object);
		}
	}
	
	public L2Summon(int objectId, L2NpcTemplate template, L2Player owner)
	{
		super(objectId, template);
		getKnownList(); // init knownlist
		getStat(); // init stats
		getStatus(); // init status
		
		_showSummonAnimation = true;
		_owner = owner;
		getAI();
		
		getPosition().setXYZInvisible(owner.getX() + 50, owner.getY() + 100, owner.getZ() + 100);
	}
	
	@Override
	public void onSpawn()
	{
		super.onSpawn();
		
		if (!(this instanceof L2MerchantSummonInstance))
		{
			setFollowStatus(true);
			broadcastFullInfoImpl(0);
			getOwner().broadcastRelationChanged();
			L2Party party = getOwner().getParty();
			if (party != null)
			{
				party.broadcastToPartyMembers(getOwner(), new ExPartyPetWindowAdd(this));
			}
			if (this instanceof L2SummonInstance && getOwner() != null && getOwner().getActiveWeaponInstance() != null)
			{
				getOwner().getActiveWeaponInstance().updateElementAttrBonus(getOwner());
				getOwner().sendPacket(new UserInfo(getOwner()));
			}
		}
		
		setShowSummonAnimation(false); // addVisibleObject created the info packets with summon animation
		// if someone comes into range now, the animation shouldnt show any more
	}
	
	@Override
	protected CreatureKnownList initKnownList()
	{
		return new SummonKnownList(this);
	}
	
	@Override
	public final SummonKnownList getKnownList()
	{
		return (SummonKnownList)_knownList;
	}
	
	@Override
	protected ICreatureView initView()
	{
		return new SummonView(this);
	}
	
	@Override
	public SummonView getView()
	{
		return (SummonView)_view;
	}
	
	@Override
	protected CreatureStat initStat()
	{
		return new SummonStat(this);
	}
	
	@Override
	public SummonStat getStat()
	{
		return (SummonStat)_stat;
	}
	
	@Override
	protected final L2CreatureAI initAI()
	{
		return new L2SummonAI(new L2Summon.AIAccessor());
	}
	
	@Override
	public L2NpcTemplate getTemplate()
	{
		return (L2NpcTemplate)super.getTemplate();
	}
	
	// this defines the action buttons, 1 for Summon, 2 for Pets
	public abstract int getSummonType();
	
	/**
	 * @return Returns the mountable.
	 */
	public boolean isMountable()
	{
		return false;
	}
	
	@Override
	public void onAction(L2Player player)
	{
		// Aggression target lock effect
		if (!player.canChangeLockedTarget(this))
			return;
		
		if (player == _owner && player.getTarget() == this)
		{
			player.sendPacket(new PetStatusShow(this));
			player.sendPacket(ActionFailed.STATIC_PACKET);
		}
		else if (player.getTarget() != this)
		{
			if (_log.isDebugEnabled())
				_log.debug("new target selected:" + getObjectId());
			player.setTarget(this);
			
			//sends HP/MP status of the summon to other characters
			StatusUpdate su = new StatusUpdate(getObjectId());
			su.addAttribute(StatusUpdate.CUR_HP, (int)getStatus().getCurrentHp());
			su.addAttribute(StatusUpdate.MAX_HP, getMaxHp());
			player.sendPacket(su);
		}
		else if (player.getTarget() == this)
		{
			if (isAutoAttackable(player))
			{
				if (GeoData.getInstance().canSeeTarget(player, this))
				{
					player.getAI().setIntention(CtrlIntention.AI_INTENTION_ATTACK, this);
					player.onActionRequest();
				}
			}
			else
			{
				// This Action Failed packet avoids player getting stuck when clicking three or more times
				player.sendPacket(ActionFailed.STATIC_PACKET);
				
				if (GeoData.getInstance().canSeeTarget(player, this))
					player.getAI().setIntention(CtrlIntention.AI_INTENTION_FOLLOW, this);
			}
		}
	}
	
	@Override
	public int getMyTargetSelectedColor(L2Player player)
	{
		return player.getLevel() - getLevel();
	}
	
	public long getExpForThisLevel()
	{
		if (getLevel() >= Experience.LEVEL.length)
		{
			return 0;
		}
		return Experience.LEVEL[getLevel()];
	}
	
	public long getExpForNextLevel()
	{
		if (getLevel() >= Experience.LEVEL.length - 1)
		{
			return 0;
		}
		return Experience.LEVEL[getLevel() + 1];
	}
	
	public final int getKarma()
	{
		return getOwner() != null ? getOwner().getKarma() : 0;
	}
	
	public final byte getPvpFlag()
	{
		return getOwner() != null ? getOwner().getPvpFlag() : 0;
	}
	
	public final int getTeam()
	{
		return getOwner() != null ? getOwner().getTeam() : 0;
	}
	
	public final L2Player getOwner()
	{
		return _owner;
	}
	
	public final int getNpcId()
	{
		return getTemplate().getNpcId();
	}
	
	public final int getSoulShotsPerHit()
	{
		return 1/*_soulShotsPerHit*/;
	}
	
	public final int getSpiritShotsPerHit()
	{
		return 1/*_spiritShotsPerHit*/;
	}
	
	public void followOwner()
	{
		setFollowStatus(true);
	}
	
	@Override
	public boolean doDie(L2Creature killer)
	{
		if (!super.doDie(killer))
			return false;
		
		if (this instanceof L2MerchantSummonInstance)
			return true;
		
		L2Player owner = getOwner();
		
		if (owner != null)
		{
			for (L2Creature TgMob : getKnownList().getKnownCharacters())
			{
				// get the mobs which have aggro on the this instance
				if (TgMob instanceof L2Attackable)
				{
					if (TgMob.isDead())
						continue;
					
					AggroInfo info = ((L2Attackable)TgMob).getAggroListRP().get(this);
					if (info != null)
						((L2Attackable)TgMob).addDamageHate(owner, info._damage, info._hate);
				}
			}
		}
		
		DecayTaskManager.getInstance().addDecayTask(this);
		return true;
	}
	
	public boolean doDie(L2Creature killer, boolean decayed)
	{
		if (!super.doDie(killer))
			return false;
		if (!decayed)
		{
			DecayTaskManager.getInstance().addDecayTask(this);
		}
		
		return true;
	}
	
	public void stopDecay()
	{
		DecayTaskManager.getInstance().cancelDecayTask(this);
	}
	
	@Override
	public void onDecay()
	{
		deleteMe(_owner);
	}
	
	@Override
	public final void broadcastStatusUpdateImpl()
	{
		// TODO: review
		super.broadcastStatusUpdateImpl();
		
		getOwner().sendPacket(new PetStatusUpdate(this));
		
		final L2Party party = getParty();
		
		if (party != null)
			party.broadcastToPartyMembers(getOwner(), new ExPartyPetWindowUpdate(this));
	}
	
	@Override
	public final void updateEffectIconsImpl()
	{
		if (this instanceof L2MerchantSummonInstance)
			return;
		
		final EffectInfoPacketList list = new EffectInfoPacketList(this);
		
		final L2Party party = getParty();
		
		if (party != null)
			party.broadcastToPartyMembers(new PartySpelled(list));
		else
			getOwner().sendPacket(new PartySpelled(list));
	}
	
	public void deleteMe(L2Player owner)
	{
		getAI().stopFollow();
		owner.sendPacket(new PetDelete(getObjectId(), 2));
		L2Party party = owner.getParty();
		if (party != null)
			party.broadcastToPartyMembers(owner, new ExPartyPetWindowDelete(this));
		
		//pet will be deleted along with all his items
		if (getInventory() != null)
		{
			getInventory().destroyAllItems("pet deleted", getOwner(), this);
		}
		
		stopAllEffects();
		getStatus().stopHpMpRegeneration();
		
		L2WorldRegion oldRegion = getWorldRegion();
		decayMe();
		if (oldRegion != null)
			oldRegion.removeFromZones(this);
		
		getKnownList().removeAllKnownObjects();
		owner.setPet(null);
		setTarget(null);
		
		LeakTaskManager.getInstance().add(this);
		
		// to flush item updates to db
		SQLQueue.getInstance().run();
	}
	
	public final void unSummon()
	{
		unSummon(getOwner());
	}
	
	public void unSummon(L2Player owner)
	{
		if (isVisible() && !isDead())
		{
			getAI().stopFollow();
			owner.sendPacket(new PetDelete(getObjectId(), 2));
			L2Party party;
			if ((party = owner.getParty()) != null)
			{
				party.broadcastToPartyMembers(owner, new ExPartyPetWindowDelete(this));
			}
			
			store();
			giveAllToOwner();
			owner.setPet(null);
			
			stopAllEffects();
			getStatus().stopHpMpRegeneration();
			
			L2WorldRegion oldRegion = getWorldRegion();
			decayMe();
			if (oldRegion != null)
				oldRegion.removeFromZones(this);
			
			getKnownList().removeAllKnownObjects();
			setTarget(null);
			
			LeakTaskManager.getInstance().add(this);
			
			// to flush item updates to db
			SQLQueue.getInstance().run();
		}
	}
	
	private ImmutableReference<L2Summon> _immutableReference;
	
	public ImmutableReference<L2Summon> getImmutableReference()
	{
		if (_immutableReference == null)
			_immutableReference = new ImmutableReference<L2Summon>(this);
		
		return _immutableReference;
	}
	
	public int getAttackRange()
	{
		return 36/*_attackRange*/;
	}
	
	//public void setAttackRange(int range)
	//{
	//	if (range < 36)
	//		range = 36;
	//	_attackRange = range;
	//}
	
	public void setFollowStatus(boolean state)
	{
		_follow = state;
		if (_follow)
			getAI().setIntention(CtrlIntention.AI_INTENTION_FOLLOW, getOwner());
		else
			getAI().setIntention(CtrlIntention.AI_INTENTION_IDLE, null);
	}
	
	public boolean getFollowStatus()
	{
		return _follow;
	}
	
	@Override
	public boolean isAutoAttackable(L2Creature attacker)
	{
		return _owner.isAutoAttackable(attacker);
	}
	
	public int getControlItemId()
	{
		return 0;
	}
	
	public L2Weapon getActiveWeapon()
	{
		return null;
	}
	
	@Override
	public PetInventory getInventory()
	{
		return null;
	}
	
	/**
	 * @param object
	 */
	protected void doPickupItem(L2Object object)
	{
	}
	
	public void giveAllToOwner()
	{
	}
	
	public void store()
	{
	}
	
	/**
	 * Return True if the L2Summon is invulnerable or if the summoner is in spawn protection.<BR><BR>
	 */
	@Override
	public boolean isInvul()
	{
		return super.isInvul() || getOwner().getProtection() > 0;
	}
	
	public abstract int getCurrentFed();
	
	public abstract int getMaxFed();
	
	/**
	 * Return the L2Party object of its L2Player owner or null.<BR><BR>
	 */
	@Override
	public L2Party getParty()
	{
		if (_owner == null)
			return null;
		
		return _owner.getParty();
	}
	
	/**
	 * Return True if the L2Creature has a Party in progress.<BR><BR>
	 */
	@Override
	public boolean isInParty()
	{
		if (_owner == null)
			return false;
		
		return _owner.getParty() != null;
	}
	
	@Override
	protected boolean checkUseMagicConditions(L2Skill skill, boolean forceUse)
	{
		if (skill == null || skill.getSkillType() == L2SkillType.NOTDONE || isDead())
			return false;
		
		// Check if the skill is active
		if (skill.isPassive())
		{
			// just ignore the passive skill request. why does the client send it anyway ??
			return false;
		}
		
		//************************************* Check Target *******************************************
		
		// Get the target for the skill
		final L2Creature target = skill.getFirstOfTargetList(this);
		
		// Check the validity of the target
		if (target == null)
		{
			if (getOwner() != null)
				getOwner().sendPacket(SystemMessageId.TARGET_CANT_FOUND);
			return false;
		}
		
		//************************************* Check skill availability *******************************************
		
		// Check if this skill is enabled (e.g. reuse time)
		if (isSkillDisabled(skill.getId()))
		{
			if (getOwner() != null)
				getOwner().sendPacket(SystemMessageId.SERVITOR_SKILL_RECHARGING);
			return false;
		}
		
		//************************************* Check Consumables *******************************************
		if (skill.getItemConsume() > 0 && getOwner().getInventory() != null)
		{
			L2ItemInstance requiredItems = getOwner().getInventory().getItemByItemId(skill.getItemConsumeId());
			// Check if the caster owns enough consumed Item to cast
			if (requiredItems == null || requiredItems.getCount() < skill.getItemConsume())
			{
				// Send a System Message to the caster
				getOwner().sendPacket(SystemMessageId.NOT_ENOUGH_ITEMS);
				return false;
			}
		}
		
		// Check if the summon has enough MP
		if (getStatus().getCurrentMp() < getStat().getMpConsume(skill) + getStat().getMpInitialConsume(skill))
		{
			// Send a System Message to the caster
			if (getOwner() != null)
				getOwner().sendPacket(SystemMessageId.SERVITOR_LACKS_MP);
			return false;
		}
		
		// Check if the summon has enough HP
		if (getStatus().getCurrentHp() <= skill.getHpConsume())
		{
			// Send a System Message to the caster
			if (getOwner() != null)
				getOwner().sendPacket(SystemMessageId.SERVITOR_LACKS_HP);
			return false;
		}
		
		//************************************* Check Summon State *******************************************
		
		// Check if this is offensive magic skill
		if (skill.isOffensive())
		{
			if (isInsidePeaceZone(this, target))
			{
				// If summon or target is in a peace zone, send a system message TARGET_IN_PEACEZONE
				getOwner().sendPacket(SystemMessageId.TARGET_IN_PEACEZONE);
				return false;
			}
			
			if (getOwner() != null && getOwner().isInOlympiadMode() && !getOwner().isOlympiadStart())
			{
				// if L2Player is in Olympia and the match isn't already start, send a Server->Client packet ActionFailed
				getOwner().sendPacket(ActionFailed.STATIC_PACKET);
				return false;
			}
			
			// Check if the target is attackable
			if (target instanceof L2DoorInstance)
			{
				if (!((L2DoorInstance)target).isAttackable(getOwner()))
					return false;
			}
			else
			{
				if (!target.isAttackable() && getOwner() != null
						&& (getOwner().getAccessLevel() < Config.GM_PEACEATTACK))
				{
					return false;
				}
				
				// Check if a Forced ATTACK is in progress on non-attackable target
				if (!target.isAutoAttackable(this) && !forceUse)
				{
					switch (skill.getTargetType())
					{
						case TARGET_AURA:
						case TARGET_FRONT_AURA:
						case TARGET_BEHIND_AURA:
						case TARGET_SERVITOR_AURA:
						case TARGET_CLAN:
						case TARGET_ALLY:
						case TARGET_PARTY:
						case TARGET_SELF:
							break;
						default:
							return false;
					}
				}
			}
		}
		
		final L2Player actingPlayer = getActingPlayer();
		
		if (actingPlayer.isGM() && actingPlayer.getAccessLevel() < Config.GM_CAN_GIVE_DAMAGE)
		{
			actingPlayer.sendPacket(SystemMessageId.TARGET_IS_INCORRECT);
			actingPlayer.sendPacket(ActionFailed.STATIC_PACKET);
			return false;
		}
		
		if (!actingPlayer.checkPvpSkill(getTarget(), skill))
		{
			// Send a System Message to the L2Player
			actingPlayer.sendPacket(SystemMessageId.TARGET_IS_INCORRECT);
			
			// Send a Server->Client packet ActionFailed to the L2Player
			actingPlayer.sendPacket(ActionFailed.STATIC_PACKET);
			return false;
		}
		
		return true;
	}
	
	@Override
	public void setIsImmobilized(boolean value)
	{
		super.setIsImmobilized(value);
		
		if (value)
		{
			_previousFollowStatus = getFollowStatus();
			// if immobilized temporarly disable follow mode
			if (_previousFollowStatus)
				setFollowStatus(false);
		}
		else
		{
			// if not more immobilized restore previous follow mode
			setFollowStatus(_previousFollowStatus);
		}
	}
	
	public void setOwner(L2Player newOwner)
	{
		_owner = newOwner;
	}
	
	@Override
	public final boolean isOutOfControl()
	{
		return isConfused() || isAfraid() || isBetrayed();
	}
	
	@Override
	public final L2Player getActingPlayer()
	{
		return getOwner();
	}
	
	@Override
	public final L2Summon getActingSummon()
	{
		return this;
	}
	
	@Override
	public boolean isInCombat()
	{
		return getOwner().isInCombat();
	}
	
	public int getWeapon()
	{
		return 0;
	}
	
	public int getArmor()
	{
		return 0;
	}
	
	public int getPetSpeed()
	{
		return getTemplate().getBaseRunSpd();
	}
	
	public boolean isHungry()
	{
		return false;
	}
	
	@Override
	public boolean isRunning()
	{
		return true; // summons always run
	}
	
	@Override
	public void sendInfo(L2Player activeChar)
	{
		// Check if the L2Player is the owner of the Pet
		if (activeChar == getOwner() && !(this instanceof L2MerchantSummonInstance))
		{
			activeChar.sendPacket(new PetInfo(this, 0));
			
			if (this instanceof L2PetInstance)
			{
				activeChar.sendPacket(new PetItemList((L2PetInstance)this));
			}
		}
		else
			activeChar.sendPacket(new AbstractNpcInfo.SummonInfo(this, 0));
	}
	
	@Override
	public void broadcastFullInfoImpl()
	{
		broadcastFullInfoImpl(1);
	}
	
	public void broadcastFullInfoImpl(int val)
	{
		getOwner().sendPacket(new PetInfo(this, val));
		getOwner().sendPacket(new PetStatusUpdate(this));
		
		broadcastPacket(new AbstractNpcInfo.SummonInfo(this, val));
		
		final L2Party party = getOwner().getParty();
		
		if (party != null)
			party.broadcastToPartyMembers(getOwner(), new ExPartyPetWindowUpdate(this));
		
		updateEffectIcons();
	}
	
	@Override
	protected final CreatureShots initShots()
	{
		return new SummonShots(this);
	}
	
	@Override
	public final SummonShots getShots()
	{
		return (SummonShots)_shots;
	}
	
	@Override
	public int getInstanceId()
	{
		return getActingPlayer().getInstanceId();
	}
	
	@Deprecated
	@Override
	public void setInstanceId(int id)
	{
	}
}
