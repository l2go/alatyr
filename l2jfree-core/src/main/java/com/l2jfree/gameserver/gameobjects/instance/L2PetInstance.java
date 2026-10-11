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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.concurrent.Future;

import com.l2jfree.Config;
import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.ThreadPoolManager;
import com.l2jfree.gameserver.datatables.ItemTable;
import com.l2jfree.gameserver.datatables.PetDataTable;
import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Object;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.L2Summon;
import com.l2jfree.gameserver.gameobjects.ai.CtrlIntention;
import com.l2jfree.gameserver.gameobjects.itemcontainer.Inventory;
import com.l2jfree.gameserver.gameobjects.itemcontainer.PetInventory;
import com.l2jfree.gameserver.gameobjects.itemcontainer.PlayerInventory;
import com.l2jfree.gameserver.gameobjects.stat.CreatureStat;
import com.l2jfree.gameserver.gameobjects.stat.PetStat;
import com.l2jfree.gameserver.gameobjects.status.CreatureStatus;
import com.l2jfree.gameserver.gameobjects.status.PetStatus;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.geodata.GeoData;
import com.l2jfree.gameserver.handler.ItemHandler;
import com.l2jfree.gameserver.idfactory.IdFactory;
import com.l2jfree.gameserver.instancemanager.CursedWeaponsManager;
import com.l2jfree.gameserver.instancemanager.ItemsOnGroundManager;
import com.l2jfree.gameserver.model.L2PetData;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.items.L2ItemInstance.ItemLocation;
import com.l2jfree.gameserver.model.items.templates.L2EtcItemType;
import com.l2jfree.gameserver.model.items.templates.L2Item;
import com.l2jfree.gameserver.model.items.templates.L2Weapon;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.ActionFailed;
import com.l2jfree.gameserver.network.packets.server.InventoryUpdate;
import com.l2jfree.gameserver.network.packets.server.ItemList;
import com.l2jfree.gameserver.network.packets.server.PetInventoryUpdate;
import com.l2jfree.gameserver.network.packets.server.PetItemList;
import com.l2jfree.gameserver.network.packets.server.PetStatusShow;
import com.l2jfree.gameserver.network.packets.server.StatusUpdate;
import com.l2jfree.gameserver.network.packets.server.StopMove;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;
import com.l2jfree.gameserver.network.packets.server.ValidateLocation;
import com.l2jfree.gameserver.persistence.WorldTransaction;
import com.l2jfree.gameserver.taskmanager.DecayTaskManager;
import com.l2jfree.gameserver.taskmanager.SQLQueue;
import com.l2jfree.tools.random.Rnd;

public class L2PetInstance extends L2Summon
{
	private int _curFed;
	public PetInventory _inventory;
	public final int _controlItemId;
	public boolean _respawned;
	public boolean _mountable;
	
	private Future<?> _feedTask;
	private int _weapon;
	private int _armor;
	private int _jewel;
	
	private int _curWeightPenalty = 0;
	
	private L2PetData _data;
	
	/** The Experience before the last Death Penalty */
	private long _expBeforeDeath = 0;
	
	//private int _maxload;
	
	public final L2PetData getPetData()
	{
		if (_data == null)
			_data = PetDataTable.getInstance().getPetData(getTemplate().getNpcId(), getStat().getLevel());
		
		return _data;
	}
	
	public final void setPetData(L2PetData value)
	{
		_data = value;
	}
	
	/**
	 * Manage Feeding Task.<BR>
	 * <BR>
	 * <B><U> Actions</U> :</B><BR>
	 * <li>Feed or kill the pet depending on hunger level</li>
	 * <li>If pet has food in inventory and feed level drops below 55% then consume food from inventory</li>
	 * <li>Send a broadcastStatusUpdate packet for this L2PetInstance</li>
	 * <BR>
	 * <BR>
	 */
	
	class FeedTask implements Runnable
	{
		@Override
		public void run()
		{
			try
			{
				if (getOwner() == null || getOwner().getPet() == null
						|| getOwner().getPet().getObjectId() != getObjectId())
				{
					stopFeed();
					return;
				}
				else if (getCurrentFed() > getFeedConsume())
				{
					// eat
					setCurrentFed(getCurrentFed() - getFeedConsume());
				}
				else
				{
					setCurrentFed(0);
				}
				
				int[] foodIds = PetDataTable.getFoodItemId(getTemplate().getNpcId());
				if (foodIds[0] == 0)
					return;
				L2ItemInstance food = null;
				food = getInventory().getItemByItemId(foodIds[0]);
				
				// use better strider food if exists
				if (PetDataTable.isStrider(getNpcId()))
				{
					if (getInventory().getItemByItemId(foodIds[1]) != null)
						food = getInventory().getItemByItemId(foodIds[1]);
				}
				if (isRunning() && isHungry())
				{
					setWalking();
				}
				else if (!isHungry() && !isRunning())
				{
					setRunning();
				}
				if (food != null && isHungry())
				{
					if (ItemHandler.getInstance().useItem(food.getItemId(), L2PetInstance.this, food))
					{
						SystemMessage sm = new SystemMessage(SystemMessageId.PET_TOOK_S1_BECAUSE_HE_WAS_HUNGRY);
						sm.addItemName(food.getItemId());
						getOwner().sendPacket(sm);
					}
				}
				else
				{
					if (getCurrentFed() == 0)
					{
						getOwner().sendPacket(SystemMessageId.YOUR_PET_IS_VERY_HUNGRY);
						if (Rnd.get(100) < 30)
						{
							stopFeed();
							getOwner().sendPacket(SystemMessageId.STARVING_GRUMPY_AND_FED_UP_YOUR_PET_HAS_LEFT);
							_log.info("Hungry pet deleted for player :" + getOwner().getName() + " Control Item Id :"
									+ getControlItemId());
							deleteMe(getOwner());
						}
					}
					else if (getCurrentFed() < (0.11 * getPetData().getPetMaxFeed()))
					{
						getOwner().sendPacket(SystemMessageId.PET_CAN_RUN_AWAY_WHEN_HUNGER_BELOW_10_PERCENT);
						if (Rnd.get(100) < 3)
						{
							stopFeed();
							getOwner().sendPacket(SystemMessageId.STARVING_GRUMPY_AND_FED_UP_YOUR_PET_HAS_LEFT);
							_log.info("Hungry pet deleted for player :" + getOwner().getName() + " Control Item Id :"
									+ getControlItemId());
							deleteMe(getOwner());
						}
					}
					broadcastStatusUpdate();
				}
			}
			catch (Exception e)
			{
				_log.error("Pet [ObjectId: " + getObjectId() + "] a feed task error has occurred", e);
			}
		}
		
		/**
		 * @return
		 */
		private int getFeedConsume()
		{
			// if pet is attacking
			if (isInCombat())
				return getPetData().getPetFeedBattle();
			else
				return getPetData().getPetFeedNormal();
		}
	}
	
	public synchronized static L2PetInstance
			spawnPet(L2NpcTemplate template, L2Player owner, L2ItemInstance control)
	{
		if (L2World.getInstance().getPet(owner.getObjectId()) != null)
			return null; // Owner has a pet listed in world
			
		L2PetInstance pet = restore(control, template, owner);
		// Add the pet instance to world
		if (pet != null)
		{
			L2World.getInstance().addPet(owner.getObjectId(), pet);
		}
		
		return pet;
	}
	
	public L2PetInstance(int objectId, L2NpcTemplate template, L2Player owner, L2ItemInstance control)
	{
		super(objectId, template, owner);
		getStat();
		
		_controlItemId = control.getObjectId();
		
		// Pet's initial level is supposed to be read from DB
		// Pets start at :
		// Wolf : Level 15
		// Hatchling : Level 35
		// Tested and confirmed on official servers
		// Sin-eaters are defaulted at the owner's level
		if (template.getNpcId() == 12564)
			getStat().setLevel((byte)getOwner().getLevel());
		else
			getStat().setLevel(template.getLevel());
		
		_inventory = new PetInventory(this);
		_inventory.restore();
		int npcId = template.getNpcId();
		_mountable = PetDataTable.isMountable(npcId);
		//_maxload = getPetData().getPetMaxLoad();
	}
	
	@Override
	protected CreatureStat initStat()
	{
		return new PetStat(this);
	}
	
	@Override
	public PetStat getStat()
	{
		return (PetStat)_stat;
	}
	
	@Override
	public double getLevelMod()
	{
		return (100.0 - 11 + getLevel()) / 100.0;
	}
	
	public boolean isRespawned()
	{
		return _respawned;
	}
	
	@Override
	public int getSummonType()
	{
		return 2;
	}
	
	@Override
	public void onAction(L2Player player)
	{
		// Aggression target lock effect
		if (!player.canChangeLockedTarget(this))
			return;
		
		boolean isOwner = player.getObjectId() == getOwner().getObjectId();
		
		if (isOwner && player != getOwner())
			updateRefOwner(player);
		if (this != player.getTarget())
		{
			if (_log.isDebugEnabled())
				_log.info("new target selected:" + getObjectId());
			
			// Set the target of the L2Player player
			player.setTarget(this);
			
			// Send a Server->Client packet StatusUpdate of the L2PetInstance to the L2Player to update its HP bar
			StatusUpdate su = new StatusUpdate(getObjectId());
			su.addAttribute(StatusUpdate.CUR_HP, (int)getCurrentHp());
			su.addAttribute(StatusUpdate.MAX_HP, getMaxHp());
			player.sendPacket(su);
		}
		else
		{
			player.sendPacket(new ValidateLocation(this));
			
			// Check if the pet is attackable (without a forced attack) and isn't dead
			if (isAutoAttackable(player) && !isOwner)
			{
				if (GeoData.getInstance().canSeeTarget(player, this))
				{
					// Set the L2Player Intention to AI_INTENTION_ATTACK
					player.getAI().setIntention(CtrlIntention.AI_INTENTION_ATTACK, this);
					player.onActionRequest();
				}
			}
			else if (!isInsideRadius(player, 150, false, false))
			{
				if (GeoData.getInstance().canSeeTarget(player, this))
				{
					player.getAI().setIntention(CtrlIntention.AI_INTENTION_INTERACT, this);
					player.onActionRequest();
				}
			}
			else
			{
				if (isOwner)
					player.sendPacket(new PetStatusShow(this));
			}
		}
		player.sendPacket(ActionFailed.STATIC_PACKET);
	}
	
	@Override
	public int getControlItemId()
	{
		return _controlItemId;
	}
	
	public L2ItemInstance getControlItem()
	{
		return getOwner().getInventory().getItemByObjectId(_controlItemId);
	}
	
	@Override
	public int getCurrentFed()
	{
		return _curFed;
	}
	
	public void setCurrentFed(int num)
	{
		_curFed = num > getMaxFed() ? getMaxFed() : num;
	}
	
	/**
	 * Returns the pet's currently equipped weapon instance (if any).
	 */
	@Override
	public L2ItemInstance getActiveWeaponInstance()
	{
		for (L2ItemInstance item : getInventory().getItems())
			if (item.getLocation() == ItemLocation.PET_EQUIP && item.getItem().getBodyPart() == L2Item.SLOT_R_HAND)
				return item;
		
		return null;
	}
	
	/**
	 * Returns the pet's currently equipped weapon (if any).
	 */
	@Override
	public L2Weapon getActiveWeaponItem()
	{
		L2ItemInstance weapon = getActiveWeaponInstance();
		
		if (weapon == null)
			return null;
		
		return (L2Weapon)weapon.getItem();
	}
	
	@Override
	public PetInventory getInventory()
	{
		return _inventory;
	}
	
	/**
	 * Destroys item from inventory and send a Server->Client InventoryUpdate packet to the L2Player.
	 * 
	 * @param process : String Identifier of process triggering this action
	 * @param objectId : int Item Instance identifier of the item to be destroyed
	 * @param count : long Quantity of items to be destroyed
	 * @param reference : L2Object Object referencing current action like NPC selling item or previous item in
	 *            transformation
	 * @param sendMessage : boolean Specifies whether to send message to Client about this action
	 * @return boolean informing if the action was successfull
	 */
	@Override
	public boolean destroyItem(String process, int objectId, long count, L2Object reference, boolean sendMessage)
	{
		L2ItemInstance item = _inventory.destroyItem(process, objectId, count, getOwner(), reference);
		
		if (item == null)
		{
			if (sendMessage)
				getOwner().sendPacket(SystemMessageId.NOT_ENOUGH_ITEMS);
			
			return false;
		}
		
		// Send Pet inventory update packet
		_inventory.updateInventory(item);
		if (sendMessage)
		{
			SystemMessage sm = new SystemMessage(SystemMessageId.S2_S1_DISAPPEARED);
			sm.addItemName(item);
			sm.addItemNumber(count);
			getOwner().sendPacket(sm);
		}
		return true;
		
	}
	
	/**
	 * Destroy item from inventory by using its <B>itemId</B> and send a Server->Client InventoryUpdate packet to the
	 * L2Player.
	 * 
	 * @param process : String Identifier of process triggering this action
	 * @param itemId : int Item identifier of the item to be destroyed
	 * @param count : long Quantity of items to be destroyed
	 * @param reference : L2Object Object referencing current action like NPC selling item or previous item in
	 *            transformation
	 * @param sendMessage : boolean Specifies whether to send message to Client about this action
	 * @return boolean informing if the action was successfull
	 */
	@Override
	public boolean destroyItemByItemId(String process, int itemId, long count, L2Object reference, boolean sendMessage)
	{
		L2ItemInstance item = _inventory.destroyItemByItemId(process, itemId, count, getOwner(), reference);
		
		if (item == null)
		{
			if (sendMessage)
				getOwner().sendPacket(SystemMessageId.NOT_ENOUGH_ITEMS);
			return false;
		}
		
		// Send Pet inventory update packet
		_inventory.updateInventory(item);
		
		if (sendMessage)
		{
			SystemMessage sm = new SystemMessage(SystemMessageId.S2_S1_DISAPPEARED);
			sm.addItemName(item);
			sm.addItemNumber(count);
			getOwner().sendPacket(sm);
		}
		
		return true;
	}
	
	@Override
	protected void doPickupItem(L2Object object)
	{
		boolean follow = getFollowStatus();
		if (isDead())
			return;
		getAI().setIntention(CtrlIntention.AI_INTENTION_IDLE);
		StopMove sm = new StopMove(getObjectId(), getX(), getY(), getZ(), getHeading());
		
		if (_log.isDebugEnabled())
			_log.debug("Pet pickup pos: " + object.getX() + " " + object.getY() + " " + object.getZ());
		
		broadcastPacket(sm);
		
		if (!(object instanceof L2ItemInstance))
		{
			// Dont try to pickup anything that is not an item :)
			_log.warn("trying to pickup wrong target." + object);
			getOwner().sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		L2ItemInstance target = (L2ItemInstance)object;
		
		// Cursed weapons
		if (CursedWeaponsManager.getInstance().isCursed(target.getItemId()))
		{
			SystemMessage smsg = new SystemMessage(SystemMessageId.FAILED_TO_PICKUP_S1);
			smsg.addItemName(target);
			getOwner().sendPacket(smsg);
			return;
		}
		
		long weight = ItemTable.getInstance().getTemplate(target.getItemId()).getWeight() * target.getCount();
		
		if (weight > Integer.MAX_VALUE || weight < 0 || !getInventory().validateWeight((int)weight))
		{
			getOwner().sendPacket(SystemMessageId.YOUR_PET_CANNOT_CARRY_ANY_MORE_ITEMS);
			return;
		}
		
		synchronized (target)
		{
			if (!target.isVisible())
			{
				getOwner().sendPacket(ActionFailed.STATIC_PACKET);
				return;
			}
			
			if (!_inventory.validateCapacity(target))
			{
				getOwner().sendPacket(SystemMessageId.YOUR_PET_CANNOT_CARRY_ANY_MORE_ITEMS);
				return;
			}
			if (!_inventory.validateWeight(target, target.getCount()))
			{
				getOwner().sendPacket(SystemMessageId.UNABLE_TO_PLACE_ITEM_YOUR_PET_IS_TOO_ENCUMBERED);
				return;
			}
			
			if (target.getOwnerId() != 0 && target.getOwnerId() != getOwner().getObjectId()
					&& !getOwner().isInLooterParty(target.getOwnerId()))
			{
				getOwner().sendPacket(ActionFailed.STATIC_PACKET);
				
				if (target.getItemId() == PlayerInventory.ADENA_ID)
				{
					SystemMessage smsg = new SystemMessage(SystemMessageId.FAILED_TO_PICKUP_S1_ADENA);
					smsg.addItemNumber(target.getCount());
					getOwner().sendPacket(smsg);
				}
				else if (target.getCount() > 1)
				{
					SystemMessage smsg = new SystemMessage(SystemMessageId.FAILED_TO_PICKUP_S2_S1_S);
					smsg.addItemName(target);
					smsg.addItemNumber(target.getCount());
					getOwner().sendPacket(smsg);
				}
				else
				{
					SystemMessage smsg = new SystemMessage(SystemMessageId.FAILED_TO_PICKUP_S1);
					smsg.addItemName(target);
					getOwner().sendPacket(smsg);
				}
				
				return;
			}
			if (target.getItemLootShedule() != null
					&& (target.getOwnerId() == getOwner().getObjectId() || getOwner().isInLooterParty(
							target.getOwnerId())))
				target.resetOwnerTimer();
			
			target.pickupMe(this);
			
			if (Config.SAVE_DROPPED_ITEM) // Item must be removed from ItemsOnGroundManager if is active
				ItemsOnGroundManager.getInstance().removeObject(target);
		}
		
		// Herbs
		if (target.getItemType() == L2EtcItemType.HERB)
		{
			ItemHandler.getInstance().useItem(target.getItemId(), this, target);
			
			ItemTable.getInstance().destroyItem("Consume", target, getOwner(), null);
			
			broadcastStatusUpdate();
			
			// Commented out and moved below synchronized block by DrHouse.
			// It seems that pets get full effect of herbs when picking them up by themselves
			/*
			SystemMessage smsg = new SystemMessage(SystemMessageId.FAILED_TO_PICKUP_S1);
			smsg.addItemName(target.getItemId());
			getOwner().sendPacket(smsg);
			return;
			 */
		}
		else
		{
			if (target.getItemId() == PlayerInventory.ADENA_ID)
			{
				SystemMessage sm2 = new SystemMessage(SystemMessageId.PET_PICKED_S1_ADENA);
				sm2.addItemNumber(target.getCount());
				getOwner().sendPacket(sm2);
			}
			else if (target.getEnchantLevel() > 0)
			{
				SystemMessage sm2 = new SystemMessage(SystemMessageId.PET_PICKED_S1_S2);
				sm2.addNumber(target.getEnchantLevel());
				sm2.addString(target.getName());
				getOwner().sendPacket(sm2);
			}
			else if (target.getCount() > 1)
			{
				SystemMessage sm2 = new SystemMessage(SystemMessageId.PET_PICKED_S2_S1_S);
				sm2.addItemNumber(target.getCount());
				sm2.addString(target.getName());
				getOwner().sendPacket(sm2);
			}
			else
			{
				SystemMessage sm2 = new SystemMessage(SystemMessageId.PET_PICKED_S1);
				sm2.addString(target.getName());
				getOwner().sendPacket(sm2);
			}
			
			getInventory().addItem("Pickup", target, getOwner(), this);
			// FIXME Just send the updates if possible (old way wasn't working though)
			PetItemList iu = new PetItemList(this);
			getOwner().sendPacket(iu);
		}
		
		getAI().setIntention(CtrlIntention.AI_INTENTION_IDLE);
		
		if (follow)
			followOwner();
	}
	
	@Override
	public void deleteMe(L2Player owner)
	{
		getOwner().removeReviving();
		getOwner().sendPacket(SystemMessageId.YOUR_PETS_CORPSE_HAS_DECAYED);
		super.deleteMe(owner);
		destroyControlItem(owner, false); // This should also delete the pet from the db
	}
	
	@Override
	public boolean doDie(L2Creature killer)
	{
		if (!super.doDie(killer, true))
			return false;
		
		getOwner().sendPacket(SystemMessageId.MAKE_SURE_YOU_RESSURECT_YOUR_PET_WITHIN_24_HOURS);
		
		stopFeed();
		
		getStatus().stopHpMpRegeneration();
		DecayTaskManager.getInstance().addDecayTask(this);
		if (isRespawned())
			deathPenalty();
		return true;
	}
	
	@Override
	public void doRevive()
	{
		getOwner().removeReviving();
		
		super.doRevive();
		super.stopDecay();
		startFeed();
		if (!isHungry())
			setRunning();
		getAI().setIntention(CtrlIntention.AI_INTENTION_ACTIVE, null);
	}
	
	@Override
	public void doRevive(double revivePower)
	{
		// Restore the pet's lost experience,
		// depending on the % return of the skill used (based on its power).
		restoreExp(revivePower);
		doRevive();
	}
	
	/**
	 * Transfers item to another inventory
	 * 
	 * @param process : String Identifier of process triggering this action
	 * @param itemId : int Item Identifier of the item to be transfered
	 * @param count : long Quantity of items to be transfered
	 * @param actor : L2Player Player requesting the item transfer
	 * @param reference : L2Object Object referencing current action like NPC selling item or previous item in
	 *            transformation
	 * @return L2ItemInstance corresponding to the new item or the updated item in inventory
	 */
	public L2ItemInstance transferItem(String process, int objectId, long count, Inventory target, L2Player actor,
			L2Object reference)
	{
		L2ItemInstance oldItem = getInventory().getItemByObjectId(objectId);
		L2ItemInstance newItem = getInventory().transferItem(process, objectId, count, target, actor, reference);
		
		if (newItem == null)
			return null;
		
		// Send inventory update packet
		PetInventoryUpdate petIU = new PetInventoryUpdate();
		if (oldItem.getCount() > 0 && oldItem != newItem)
			petIU.addModifiedItem(oldItem);
		else
			petIU.addRemovedItem(oldItem);
		getOwner().sendPacket(petIU);
		
		// Send target update packet
		if (target instanceof PlayerInventory)
		{
			L2Player targetPlayer = ((PlayerInventory)target).getOwner();
			InventoryUpdate playerUI = new InventoryUpdate();
			if (newItem.getCount() > count)
				playerUI.addModifiedItem(newItem);
			else
				playerUI.addNewItem(newItem);
			targetPlayer.sendPacket(playerUI);
			
			// Update current load as well
			StatusUpdate playerSU = new StatusUpdate(targetPlayer.getObjectId());
			playerSU.addAttribute(StatusUpdate.CUR_LOAD, targetPlayer.getCurrentLoad());
			targetPlayer.sendPacket(playerSU);
		}
		else if (target instanceof PetInventory)
		{
			petIU = new PetInventoryUpdate();
			if (newItem.getCount() > count)
				petIU.addRemovedItem(newItem);
			else
				petIU.addNewItem(newItem);
			((PetInventory)target).getOwner().getOwner().sendPacket(petIU);
		}
		getInventory().refreshWeight();
		return newItem;
	}
	
	@Override
	public void giveAllToOwner()
	{
		try
		{
			Inventory petInventory = getInventory();
			for (L2ItemInstance giveit : petInventory.getItems())
			{
				if (((giveit.getItem().getWeight() * giveit.getCount()) + getOwner().getInventory().getTotalWeight()) < getOwner()
						.getMaxLoad())
				{
					// If the owner can carry it give it to them
					giveItemToOwner(giveit);
				}
				else
				{
					// If they can't carry it, chuck it on the floor :)
					dropItemHere(giveit);
				}
			}
		}
		catch (Exception e)
		{
			_log.error("Give all items error ", e);
		}
	}
	
	public void giveItemToOwner(L2ItemInstance item)
	{
		try
		{
			getInventory().transferItem("PetTransfer", item.getObjectId(), item.getCount(), getOwner().getInventory(),
					getOwner(), this);
			PetInventoryUpdate petiu = new PetInventoryUpdate();
			ItemList PlayerUI = new ItemList(getOwner(), false);
			petiu.addRemovedItem(item);
			getOwner().sendPacket(petiu);
			getOwner().sendPacket(PlayerUI);
		}
		catch (Exception e)
		{
			_log.error("Error while giving item to owner: ", e);
		}
	}
	
	/**
	 * Remove the Pet from DB and its associated item from the player inventory
	 * 
	 * @param owner The owner from whose invenory we should delete the item
	 */
	public void destroyControlItem(L2Player owner, boolean evolve)
	{
		// Remove the pet instance from world
		L2World.getInstance().removePet(owner.getObjectId());
		
		// Delete from inventory
		try
		{
			L2ItemInstance removedItem;
			if (evolve)
			{
				removedItem = owner.getInventory().destroyItem("Evolve", getControlItemId(), 1, getOwner(), this);
			}
			else
			{
				removedItem = owner.getInventory().destroyItem("PetDestroy", getControlItemId(), 1, getOwner(), this);
				owner.sendPacket(new SystemMessage(SystemMessageId.S1_DISAPPEARED).addItemName(removedItem));
			}
			
			InventoryUpdate iu = new InventoryUpdate();
			iu.addRemovedItem(removedItem);
			
			owner.sendPacket(iu);
			
			StatusUpdate su = new StatusUpdate(owner.getObjectId());
			su.addAttribute(StatusUpdate.CUR_LOAD, owner.getCurrentLoad());
			owner.sendPacket(su);
			
			owner.broadcastUserInfo();
			
			L2World world = L2World.getInstance();
			world.removeObject(removedItem);
		}
		catch (Exception e)
		{
			_log.error("Error while destroying control item: ", e);
		}
		
		// Pet control item no longer exists, delete the pet from the db
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement = con.prepareStatement("DELETE FROM pet WHERE item_id=?");
			statement.setInt(1, getControlItemId());
			statement.execute();
			statement.close();
		}
		catch (SQLException e)
		{
			_log.error("Failed to delete Pet [ObjectId: " + getObjectId() + "]", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	public void dropItemHere(L2ItemInstance dropit)
	{
		dropit = getInventory().dropItem("PetDrop", dropit.getObjectId(), dropit.getCount(), getOwner(), this);
		
		if (dropit != null)
		{
			if (_log.isDebugEnabled())
				_log.debug("Item id to drop: " + dropit.getItemId() + " amount: " + dropit.getCount());
			dropit.dropMe(this, getX(), getY(), getZ() + 100);
		}
	}
	
	/** @return Returns the mountable. */
	@Override
	public boolean isMountable()
	{
		return _mountable;
	}
	
	private static L2PetInstance restore(L2ItemInstance control, L2NpcTemplate template, L2Player owner)
	{
		Connection con = null;
		try
		{
			L2PetInstance pet;
			if (template.isAssignableTo(L2BabyPetInstance.class))
				pet = new L2BabyPetInstance(IdFactory.getInstance().getNextId(), template, owner, control);
			else
				pet = new L2PetInstance(IdFactory.getInstance().getNextId(), template, owner, control);
			
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement =
					con.prepareStatement("SELECT name, level, current_hp, current_mp, exp, sp, current_feed, weapon_template_id, armor_template_id, jewel_template_id FROM pet WHERE item_id=?");
			statement.setInt(1, control.getObjectId());
			ResultSet rset = statement.executeQuery();
			if (!rset.next())
			{
				rset.close();
				statement.close();
				return pet;
			}
			
			pet.setName(rset.getString("name"));
			
			pet.getStat().setLevel(rset.getByte("level"));
			pet.getStat().setExp(rset.getLong("exp"));
			pet.getStat().setSp(rset.getInt("sp"));
			
			if (rset.getDouble("current_hp") < 0.5)
			{
				pet.setIsDead(true);
				pet.getStatus().stopHpMpRegeneration();
			}
			
			int curFed = rset.getInt("current_feed");
			
			pet.getStatus().setCurrentHp(rset.getDouble("current_hp"));
			pet.getStatus().setCurrentMp(rset.getDouble("current_mp"));
			pet.getStatus().setCurrentCp(pet.getMaxCp());
			
			// NULL (no item worn) reads as 0
			pet.setWeapon(rset.getInt("weapon_template_id"));
			pet.setArmor(rset.getInt("armor_template_id"));
			pet.setJewel(rset.getInt("jewel_template_id"));
			
			// Hack for zero food
			if (curFed == 0)
			{
				int foodId[] = PetDataTable.getFoodItemId(pet.getTemplate().getNpcId());
				if (foodId[0] != 0)
				{
					L2ItemInstance food = pet.getOwner().getInventory().getItemByItemId(foodId[0]);
					
					if ((food != null) && pet.getOwner().destroyItem("Feed", food.getObjectId(), 1, null, false))
					{
						curFed = pet.getCurrentFed() + 100;
					}
					else
					{
						pet.getOwner().sendPacket(SystemMessageId.YOU_CANNOT_RESTORE_HUNGRY_PETS);
						rset.close();
						statement.close();
						return null;
					}
				}
			}
			
			pet.setCurrentFed(curFed);
			
			rset.close();
			statement.close();
			
			pet._respawned = true;
			
			return pet;
		}
		catch (SQLException e)
		{
			_log.error("Failed to restore pet data", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
		return null;
	}
	
	private static void setTemplateId(PreparedStatement statement, int index, int templateId) throws SQLException
	{
		if (templateId == 0)
			statement.setNull(index, Types.INTEGER);
		else
			statement.setInt(index, templateId);
	}
	
	@Override
	public void store()
	{
		if (getControlItemId() == 0)
		{
			// This is a summon, not a pet, don't store anything
			return;
		}
		
		// pet.item_id references item. The save of the player writes the control item in this transaction before
		// calling here. Outside a transaction the control item may still be waiting in the queue.
		if (!isRespawned() && WorldTransaction.current() == null)
			SQLQueue.getInstance().run();
		
		String req;
		if (!isRespawned())
			req =
					"INSERT INTO pet (name, level, current_hp, current_mp, exp, sp, current_feed, weapon_template_id, armor_template_id, jewel_template_id, item_id) "
							+ "VALUES (?,?,?,?,?,?,?,?,?,?,?)";
		else
			req =
					"UPDATE pet SET name=?, level=?, current_hp=?, current_mp=?, exp=?, sp=?, current_feed=?, weapon_template_id=?, armor_template_id=?, jewel_template_id=? "
							+ "WHERE item_id = ?";
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement = con.prepareStatement(req);
			// An empty name means the pet is unnamed: NULL
			String name = getName();
			if (name == null || name.isEmpty())
				statement.setNull(1, Types.VARCHAR);
			else
				statement.setString(1, name);
			statement.setInt(2, getStat().getLevel());
			statement.setDouble(3, Math.max(0.0, getStatus().getCurrentHp()));
			statement.setDouble(4, Math.max(0.0, getStatus().getCurrentMp()));
			statement.setLong(5, getStat().getExp());
			statement.setInt(6, getStat().getSp());
			statement.setInt(7, Math.max(0, getCurrentFed()));
			// 0 (no item worn) is NULL
			setTemplateId(statement, 8, getWeapon());
			setTemplateId(statement, 9, getArmor());
			setTemplateId(statement, 10, getJewel());
			statement.setInt(11, getControlItemId());
			statement.executeUpdate();
			statement.close();
			
			_respawned = true;
			
		}
		catch (SQLException e)
		{
			_log.error("Failed to store Pet [ObjectId: " + getObjectId() + "] data", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
		
		L2ItemInstance itemInst = getControlItem();
		if (itemInst != null && itemInst.getEnchantLevel() != getStat().getLevel())
		{
			itemInst.setEnchantLevel(getStat().getLevel());
			itemInst.updateDatabase();
		}
	}
	
	public synchronized void stopFeed()
	{
		if (_feedTask != null)
		{
			_feedTask.cancel(false);
			_feedTask = null;
			if (_log.isDebugEnabled())
				_log.debug("Pet [#" + getObjectId() + "] feed task stop");
		}
	}
	
	public synchronized void startFeed()
	{
		// Stop feeding task if its active
		stopFeed();
		if (!isDead() && getOwner().getPet() == this)
		{
			_feedTask = ThreadPoolManager.getInstance().scheduleGeneralAtFixedRate(new FeedTask(), 10000, 10000);
		}
	}
	
	@Override
	public void unSummon(L2Player owner)
	{
		stopFeed();
		getStatus().stopHpMpRegeneration();
		super.unSummon(owner);
		
		if (!isDead())
		{
			store();
			L2World.getInstance().removePet(owner.getObjectId());
		}
	}
	
	/**
	 * Restore the specified % of experience this L2PetInstance has lost.<BR>
	 * <BR>
	 */
	protected void restoreExp(double restorePercent)
	{
		if (_expBeforeDeath > 0)
		{
			// Restore the specified % of lost experience.
			getStat().addExp(Math.round((_expBeforeDeath - getStat().getExp()) * restorePercent / 100));
			_expBeforeDeath = 0;
		}
	}
	
	public long getExpBeforeDeath()
	{
		return _expBeforeDeath;
	}
	
	private void deathPenalty()
	{
		// FIXME: Need Correct Penalty
		
		int lvl = getStat().getLevel();
		double percentLost = -0.07 * lvl + 6.5;
		
		// Calculate the Experience loss
		long lostExp =
				Math.round((getStat().getExpForLevel(lvl + 1) - getStat().getExpForLevel(lvl)) * percentLost / 100);
		
		// Get the Experience before applying penalty
		_expBeforeDeath = getStat().getExp();
		
		// Set the new Experience value of the L2PetInstance
		getStat().addExp(-lostExp);
	}
	
	@Override
	public void addExpAndSp(long addToExp, int addToSp)
	{
		if (getNpcId() == 12564) // SinEater
			getStat().addExpAndSp(Math.round(addToExp * Config.SINEATER_XP_RATE), addToSp);
		else
			getStat().addExpAndSp(Math.round(addToExp * Config.PET_XP_RATE), addToSp);
	}
	
	@Override
	public int getMaxFed()
	{
		return getStat().getMaxFeed();
	}
	
	@Override
	public final int getLevel()
	{
		return getStat().getLevel();
	}
	
	@Override
	public long getExpForThisLevel()
	{
		return getStat().getExpForLevel(getLevel());
	}
	
	@Override
	public long getExpForNextLevel()
	{
		return getStat().getExpForLevel(getLevel() + 1);
	}
	
	@Override
	public int getSkillLevel(int skillId)
	{
		if (!hasSkill(skillId))
			return -1;
		int lvl = getLevel();
		return lvl > 70 ? 7 + (lvl - 70) / 5 : lvl / 10;
	}
	
	public void updateRefOwner(L2Player owner)
	{
		int oldOwnerId = getOwner().getObjectId();
		
		setOwner(owner);
		L2World.getInstance().removePet(oldOwnerId);
		L2World.getInstance().addPet(oldOwnerId, this);
	}
	
	@Override
	public int getCurrentLoad()
	{
		return _inventory.getTotalWeight();
	}
	
	//public final void setMaxLoad(int maxLoad)
	//{
	//	_maxload = maxLoad;
	//}
	
	@Override
	public final int getMaxLoad()
	{
		return getPetData().getPetMaxLoad();
	}
	
	public int getInventoryLimit()
	{
		return Config.ALT_INVENTORY_MAXIMUM_PET;
	}
	
	@Override
	public int getWeightPenalty()
	{
		return _curWeightPenalty;
	}
	
	@Override
	public void setWeightPenalty(int value)
	{
		_curWeightPenalty = value;
	}
	
	@Override
	public final boolean isHungry()
	{
		return getCurrentFed() < (0.55 * getPetData().getPetMaxFeed());
	}
	
	public final void setWeapon(int id)
	{
		_weapon = id;
	}
	
	public final void setArmor(int id)
	{
		_armor = id;
	}
	
	public final void setJewel(int id)
	{
		_jewel = id;
	}
	
	@Override
	public final int getWeapon()
	{
		return _weapon;
	}
	
	@Override
	public final int getArmor()
	{
		return _armor;
	}
	
	public final int getJewel()
	{
		return _jewel;
	}
	
	@Override
	public int getPetSpeed()
	{
		return getPetData().getPetSpeed();
	}
	
	@Override
	protected CreatureStatus initStatus()
	{
		return new PetStatus(this);
	}
	
	@Override
	public final PetStatus getStatus()
	{
		return (PetStatus)_status;
	}
	
	@Override
	public void broadcastFullInfoImpl()
	{
		refreshOverloaded();
		
		super.broadcastFullInfoImpl();
	}
}
