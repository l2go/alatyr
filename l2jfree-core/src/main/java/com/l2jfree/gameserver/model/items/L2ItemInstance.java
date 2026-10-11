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
package com.l2jfree.gameserver.model.items;

import static com.l2jfree.gameserver.gameobjects.itemcontainer.PlayerInventory.ADENA_ID;
import static com.l2jfree.gameserver.gameobjects.itemcontainer.PlayerInventory.MAX_ADENA;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.Config;
import com.l2jfree.gameserver.ThreadPoolManager;
import com.l2jfree.gameserver.datatables.ItemTable;
import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Object;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.ai.CtrlIntention;
import com.l2jfree.gameserver.gameobjects.itemcontainer.PlayerInventory;
import com.l2jfree.gameserver.gameobjects.shot.ShotState;
import com.l2jfree.gameserver.geodata.GeoData;
import com.l2jfree.gameserver.instancemanager.ItemsOnGroundManager;
import com.l2jfree.gameserver.instancemanager.MercTicketManager;
import com.l2jfree.gameserver.model.Elementals;
import com.l2jfree.gameserver.model.L2Augmentation;
import com.l2jfree.gameserver.model.Location;
import com.l2jfree.gameserver.model.items.templates.AbstractL2ItemType;
import com.l2jfree.gameserver.model.items.templates.L2Armor;
import com.l2jfree.gameserver.model.items.templates.L2Equip;
import com.l2jfree.gameserver.model.items.templates.L2EtcItem;
import com.l2jfree.gameserver.model.items.templates.L2Item;
import com.l2jfree.gameserver.model.items.templates.L2Weapon;
import com.l2jfree.gameserver.model.quest.QuestState;
import com.l2jfree.gameserver.model.restriction.global.GlobalRestrictions;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.skills.funcs.Func;
import com.l2jfree.gameserver.model.skills.funcs.FuncOwner;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.model.world.L2WorldRegion;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.L2ServerPacket.ElementalOwner;
import com.l2jfree.gameserver.network.packets.server.DropItem;
import com.l2jfree.gameserver.network.packets.server.GetItem;
import com.l2jfree.gameserver.network.packets.server.InventoryUpdate;
import com.l2jfree.gameserver.network.packets.server.SpawnItem;
import com.l2jfree.gameserver.network.packets.server.StatusUpdate;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;
import com.l2jfree.gameserver.persistence.WorldTransaction;
import com.l2jfree.gameserver.persistence.item.ItemRepository;
import com.l2jfree.gameserver.taskmanager.SQLQueue;
import com.l2jfree.sql.SQLQuery;
import com.l2jfree.util.L2Arrays;

/**
 * This class manages items.
 * 
 * @version $Revision: 1.4.2.1.2.11 $ $Date: 2005/03/31 16:07:50 $
 */
public final class L2ItemInstance extends L2Object implements FuncOwner, ElementalOwner
{
	@SuppressWarnings("hiding")
	public static final L2ItemInstance[] EMPTY_ARRAY = new L2ItemInstance[0];
	
	protected static final Logger _log = LoggerFactory.getLogger(L2ItemInstance.class);
	
	private static final Logger _logItems = LoggerFactory.getLogger("item");
	
	/** Enumeration of locations for item */
	public static enum ItemLocation
	{
		VOID,
		INVENTORY,
		PAPERDOLL,
		WAREHOUSE,
		CLANWH,
		PET,
		PET_EQUIP,
		LEASE,
		FREIGHT,
		NPC
	}
	
	/** ID of the owner */
	private int _ownerId;
	
	/** ID of who dropped the item last, used for knownlist */
	private int _dropperObjectId;
	
	/** Quantity of the item */
	private long _count;
	
	/** Initial Quantity of the item */
	private long _initCount;
	
	/** Remaining time (in miliseconds) */
	private long _time;
	
	/** Quantity of the item can decrease */
	private boolean _decrease = false;
	
	/** For NPC buylists */
	private int _restoreTime = -1;
	
	/** Object L2Item associated to the item */
	private final L2Item _item;
	
	/** Location of the item : Inventory, PaperDoll, WareHouse */
	private ItemLocation _loc;
	
	/** Slot where item is stored : Paperdoll slot, inventory order ...*/
	private int _locData;
	
	/** Level of enchantment of the item */
	private int _enchantLevel;
	
	/** Price of the item for selling */
	private long _priceSell;
	
	/** Price of the item for buying */
	private long _priceBuy;
	
	/** Wear Item */
	private boolean _wear;
	
	/** Augmented Item */
	private L2Augmentation _augmentation = null;
	/** Elemental Enchant */
	private Elementals _elementals = null;
	
	public ScheduledFuture<?> _lifeTimeTask;
	
	/** Shadow item */
	private int _mana = -1;
	private boolean _consumingMana = false;
	private static final int MANA_CONSUMPTION_RATE = 60000;
	
	/** Custom item types (used loto, race tickets) */
	private int _type1;
	private int _type2;
	
	private long _dropTime;
	
	private boolean _protected;
	
	public static final int UNCHANGED = 0;
	public static final int ADDED = 1;
	public static final int MODIFIED = 2;
	public static final int REMOVED = 3;
	private int _lastChange = 2; // 1 added, 2 modified, 3 removed
	private boolean _existsInDb; // if a record exists in DB.
	private boolean _storedInDb; // if DB data is up-to-date.
	
	private ScheduledFuture<?> itemLootShedule = null;
	
	/**
	 * Constructor of the L2ItemInstance from the objectId and the itemId.
	 * 
	 * @param objectId :
	 *            int designating the ID of the object in the world
	 * @param itemId :
	 *            int designating the ID of the item
	 */
	public L2ItemInstance(int objectId, int itemId)
	{
		this(objectId, ItemTable.getInstance().getTemplate(itemId));
		_time = (_item.getTime() == -1) ? -1 : (System.currentTimeMillis() + _item.getTime() * 60000);
		scheduleLifeTimeTask();
	}
	
	/**
	 * Constructor of the L2ItemInstance from the objetId and the description of the item given by the L2Item.
	 * 
	 * @param objectId :
	 *            int designating the ID of the object in the world
	 * @param item :
	 *            L2Item containing informations of the item
	 */
	public L2ItemInstance(int objectId, L2Item item)
	{
		super(objectId);
		getKnownList();
		_item = item;
		if (_item == null)
			throw new IllegalArgumentException();
		super.setName(_item.getName());
		setCount(1);
		_loc = ItemLocation.VOID;
		_mana = _item.getDuration();
	}

	/** Creates a product without publishing or scheduling it before the exchange commits. */
	public static L2ItemInstance prepareForMultisell(int objectId, L2Item template)
	{
		L2ItemInstance item = new L2ItemInstance(objectId, template);
		item._time = template.getTime() == -1 ? -1 : System.currentTimeMillis() + template.getTime() * 60000L;
		return item;
	}

	/** Attributes are persisted by the exchange's JDBC transaction. */
	public void setPreparedMultisellAttributes(L2Augmentation augmentation, Elementals elementals)
	{
		_augmentation = augmentation;
		_elementals = elementals;
	}
	
	/**
	 * Sets the ownerID of the item
	 * 
	 * @param process :
	 *            String Identifier of process triggering this action
	 * @param owner_id :
	 *            int designating the ID of the owner
	 * @param creator :
	 *            L2Player Player requesting the item creation
	 * @param reference :
	 *            L2Object Object referencing current action like NPC selling item or previous item in transformation
	 */
	public void setOwnerId(String process, int owner_id, L2Player creator, L2Object reference)
	{
		setOwnerId(owner_id);
		
		if (Config.LOG_ITEMS)
		{
			List<Object> param = new ArrayList<Object>();
			param.add("CHANGE:" + process);
			param.add(this);
			param.add(creator);
			param.add(reference);
			_logItems.info("{}", param);
		}
	}
	
	/**
	 * Sets the ownerID of the item
	 * 
	 * @param owner_id :
	 *            int designating the ID of the owner
	 */
	public void setOwnerId(int owner_id)
	{
		if (owner_id == _ownerId)
			return;
		
		_ownerId = owner_id;
		_storedInDb = false;
	}
	
	/**
	 * Returns the ownerID of the item
	 * 
	 * @return int : ownerID of the item
	 */
	public int getOwnerId()
	{
		return _ownerId;
	}
	
	/**
	 * Sets the location of the item
	 * 
	 * @param loc :
	 *            ItemLocation (enumeration)
	 */
	public void setLocation(ItemLocation loc)
	{
		setLocation(loc, 0);
	}
	
	/**
	 * Sets the location of the item.<BR>
	 * <BR>
	 * <U><I>Remark :</I></U> If loc and loc_data different from database, say datas not up-to-date
	 * 
	 * @param loc :
	 *            ItemLocation (enumeration)
	 * @param loc_data :
	 *            int designating the slot where the item is stored or the village for freights
	 */
	public void setLocation(ItemLocation loc, int loc_data)
	{
		if (loc == _loc && loc_data == _locData)
			return;
		_loc = loc;
		_locData = loc_data;
		_storedInDb = false;
	}
	
	public ItemLocation getLocation()
	{
		return _loc;
	}
	
	/**
	* Sets the quantity of the item.<BR><BR>
	* @param count the new count to set
	*/
	public synchronized void setCount(long count)
	{
		if (getCount() == count)
		{
			return;
		}
		
		_count = count >= -1 ? count : 0;
		_storedInDb = false;
	}
	
	/**
	* @return Returns the count.
	*/
	public long getCount()
	{
		return _count;
	}
	
	// No logging (function designed for shots only)
	public void changeCountWithoutTrace(long count, L2Player creator, L2Object reference)
	{
		changeCount(null, count, creator, reference);
	}
	
	/**
	 * Sets the quantity of the item.<BR>
	 * <BR>
	 * <U><I>Remark :</I></U> If loc and loc_data different from database, say datas not up-to-date
	 * 
	 * @param process :
	 *            String Identifier of process triggering this action
	 * @param count :
	 *            long
	 * @param creator :
	 *            L2Player Player requesting the item creation
	 * @param reference :
	 *            L2Object Object referencing current action like NPC selling item or previous item in transformation
	 */
	public synchronized void changeCount(String process, long count, L2Player creator, L2Object reference)
	{
		if (count == 0)
			return;
		
		long max = getItemId() == ADENA_ID ? MAX_ADENA : Integer.MAX_VALUE;
		
		if (count > 0 && getCount() > max - count)
			setCount(max);
		else
			setCount(getCount() + count);
		
		if (getCount() < 0)
			setCount(0);
		
		_storedInDb = false;
		
		if (Config.LOG_ITEMS && process != null)
		{
			List<Object> param = new ArrayList<Object>();
			param.add("CHANGE:" + process);
			param.add(this);
			param.add(creator);
			param.add(reference);
			_logItems.info("{}", param);
		}
	}
	
	/**
	 * Returns if item is equipable
	 * 
	 * @return boolean
	 */
	public boolean isEquipable()
	{
		return !(_item.getBodyPart() == 0 || _item instanceof L2EtcItem);
	}
	
	/**
	 * Returns if item is equipped
	 * 
	 * @return boolean
	 */
	public boolean isEquipped()
	{
		return _loc == ItemLocation.PAPERDOLL || _loc == ItemLocation.PET_EQUIP;
	}
	
	/**
	 * Returns the slot where the item is stored
	 * 
	 * @return int
	 */
	public int getLocationSlot()
	{
		if (Config.ASSERT)
			assert _loc == ItemLocation.PAPERDOLL || _loc == ItemLocation.PET_EQUIP || _loc == ItemLocation.FREIGHT
					|| _loc == ItemLocation.INVENTORY;
		
		return _locData;
	}
	
	/**
	 * Returns the characteristics of the item
	 * 
	 * @return L2Item
	 */
	
	public L2Item getItem()
	{
		return _item;
	}
	
	public int getCustomType1()
	{
		return _type1;
	}
	
	public int getCustomType2()
	{
		return _type2;
	}
	
	public void setCustomType1(int newtype)
	{
		_type1 = newtype;
	}
	
	public void setCustomType2(int newtype)
	{
		_type2 = newtype;
	}
	
	public void setDropTime(long time)
	{
		_dropTime = time;
	}
	
	public long getDropTime()
	{
		return _dropTime;
	}
	
	public void setDropperObjectId(int id)
	{
		_dropperObjectId = id;
	}
	
	@Override
	public void sendInfo(L2Player activeChar)
	{
		if (_dropperObjectId != 0)
			activeChar.sendPacket(new DropItem(this, _dropperObjectId));
		else
			activeChar.sendPacket(new SpawnItem(this));
	}
	
	public boolean isWear()
	{
		return _wear;
	}
	
	public void setWear(boolean newwear)
	{
		_wear = newwear;
	}
	
	/**
	 * Returns the type of item
	 * 
	 * @return Enum
	 */
	public AbstractL2ItemType getItemType()
	{
		return _item.getItemType();
	}
	
	/**
	 * Returns the ID of the item
	 * 
	 * @return int
	 */
	public int getItemId()
	{
		return _item.getItemId();
	}
	
	public int getItemDisplayId()
	{
		return _item.getItemDisplayId();
	}
	
	/**
	 * Returns true if item is an EtcItem
	 * 
	 * @return boolean
	 */
	public boolean isEtcItem()
	{
		return (_item instanceof L2EtcItem);
	}
	
	/**
	 * Returns true if item is a Weapon/Shield
	 * 
	 * @return boolean
	 */
	public boolean isWeapon()
	{
		return (_item instanceof L2Weapon);
	}
	
	/**
	 * Returns true if item is an Armor
	 * 
	 * @return boolean
	 */
	public boolean isArmor()
	{
		return (_item instanceof L2Armor);
	}
	
	/**
	 * Returns the characteristics of the L2EtcItem
	 * 
	 * @return L2EtcItem
	 */
	public L2EtcItem getEtcItem()
	{
		if (_item instanceof L2EtcItem)
			return (L2EtcItem)_item;
		return null;
	}
	
	/**
	 * Returns the characteristics of the L2Weapon
	 * 
	 * @return L2Weapon
	 */
	public L2Weapon getWeaponItem()
	{
		if (_item instanceof L2Weapon)
			return (L2Weapon)_item;
		return null;
	}
	
	/**
	 * Returns the characteristics of the L2Armor
	 * 
	 * @return L2Armor
	 */
	public L2Armor getArmorItem()
	{
		if (_item instanceof L2Armor)
			return (L2Armor)_item;
		return null;
	}
	
	/**
	 * Returns the quantity of crystals for crystallization
	 * 
	 * @return int
	 */
	public final int getCrystalCount()
	{
		return _item.getCrystalCount(_enchantLevel);
	}
	
	/**
	 * Returns the reference price of the item
	 * 
	 * @return int
	 */
	public int getReferencePrice()
	{
		return _item.getReferencePrice();
	}
	
	/**
	 * Returns the name of the item
	 * 
	 * @return String
	 */
	public String getItemName()
	{
		return _item.getName();
	}
	
	/**
	 * Returns the price of the item for selling
	 * 
	 * @return long
	 */
	public long getPriceToSell()
	{
		return (isConsumable() ? (long)(_priceSell * Config.RATE_CONSUMABLE_COST) : _priceSell);
	}
	
	/**
	 * Sets the price of the item for selling <U><I>Remark :</I></U> If loc and loc_data different from database, say datas not up-to-date
	 * 
	 * @param price :
	 *            long designating the price
	 */
	public void setPriceToSell(long price)
	{
		_priceSell = price;
		_storedInDb = false;
	}
	
	/**
	 * Returns the price of the item for buying
	 * 
	 * @return int
	 */
	public long getPriceToBuy()
	{
		return (isConsumable() ? (long)(_priceBuy * Config.RATE_CONSUMABLE_COST) : _priceBuy);
	}
	
	/**
	 * Sets the price of the item for buying <U><I>Remark :</I></U> If loc and loc_data different from database, say datas not up-to-date
	 * 
	 * @param price :
	 *            long
	 */
	public void setPriceToBuy(long price)
	{
		_priceBuy = price;
		_storedInDb = false;
	}
	
	/**
	 * Returns the last change of the item
	 * 
	 * @return int
	 */
	public int getLastChange()
	{
		return _lastChange;
	}
	
	/**
	 * Sets the last change of the item
	 * 
	 * @param lastChange :
	 *            int
	 */
	public void setLastChange(int lastChange)
	{
		_lastChange = lastChange;
	}
	
	/**
	 * Returns if item is stackable
	 * 
	 * @return boolean
	 */
	public boolean isStackable()
	{
		return _item.isStackable();
	}
	
	/**
	 * Returns if item is dropable
	 * 
	 * @return boolean
	 */
	public boolean isDropable()
	{
		return !isAugmented() && _item.isDropable();
	}
	
	/**
	 * Returns if item is destroyable
	 * 
	 * @return boolean
	 */
	public boolean isDestroyable()
	{
		return _item.isDestroyable();
	}
	
	/**
	 * Returns if item is tradeable
	 * 
	 * @return boolean
	 */
	public boolean isTradeable()
	{
		return !isAugmented() && _item.isTradeable();
	}
	
	/**
	 * Returns if item is sellable
	 * @return boolean
	 */
	public boolean isSellable()
	{
		return !isAugmented() && _item.isSellable();
	}
	
	/**
	 * Returns if item can be deposited in warehouse or freight
	 * @return boolean
	 */
	public boolean isDepositable(boolean isPrivateWareHouse)
	{
		// equipped, hero and quest items
		if (isEquipped() || !_item.isDepositable())
			return false;
		
		if (!isPrivateWareHouse)
		{
			// augmented not tradeable
			if (!isTradeable() || isShadowItem())
				return false;
		}
		
		return true;
	}
	
	/**
	 * Returns if item is consumable
	 * 
	 * @return boolean
	 */
	public boolean isConsumable()
	{
		return _item.isConsumable();
	}
	
	/**
	 * Returns if item is a heroitem
	 * 
	 * @return boolean
	 */
	public boolean isHeroItem()
	{
		return _item.isHeroItem();
	}
	
	/**
	 * Returns if item is a common item.
	 * 
	 * @return boolean
	 */
	public boolean isCommonItem()
	{
		return _item.isCommonItem();
	}
	
	public boolean isCommon()
	{
		return _item.isCommon();
	}
	
	/**
	 * Returns whether this item is pvp or not
	 * 
	 * @return boolean
	 */
	public boolean isPvp()
	{
		return _item.isPvpItem();
	}
	
	/**
	 * Returns if item is available for manipulation
	 * 
	 * @return boolean
	 */
	public boolean isAvailable(L2Player player, boolean allowAdena, boolean allowNonTradeable)
	{
		return ((!isEquipped()) // Not equipped
				&& (getItem().getType2() != 3) // Not Quest Item
				&& (getItem().getType2() != 4 || getItem().getType1() != 1) // Not Money or Shield Armor
				&& (player.getPet() == null || getObjectId() != player.getPet().getControlItemId()) // Not Control item of currently summoned pet
				&& (player.getActiveEnchantItem() != this) // Not momentarily used enchant scroll
				&& (allowAdena || getItemId() != PlayerInventory.ADENA_ID) // Not adena
				&& (player.getCurrentSkill() == null || player.getCurrentSkill().getSkill().getItemConsumeId() != getItemId())
				&& (!player.isCastingSimultaneouslyNow() || player.getLastSimultaneousSkillCast() == null || player
						.getLastSimultaneousSkillCast().getItemConsumeId() != getItemId()) && (allowNonTradeable || isTradeable()));
	}
	
	@Override
	public void onAction(L2Player player)
	{
		if (player.isFlying() || !GlobalRestrictions.canPickUp(player, this, null))
			return;
		
		player.getAI().setIntention(CtrlIntention.AI_INTENTION_PICK_UP, this);
	}
	
	/**
	 * Returns the level of enchantment of the item
	 * 
	 * @return int
	 */
	public int getEnchantLevel()
	{
		return _enchantLevel;
	}
	
	/**
	 * Sets the level of enchantment of the item
	 * 
	 * @param enchantLevel
	 */
	public void setEnchantLevel(int enchantLevel)
	{
		if (_enchantLevel == enchantLevel)
			return;
		_enchantLevel = enchantLevel;
		_storedInDb = false;
	}
	
	/**
	 * Returns the physical defense of the item
	 * 
	 * @return int
	 */
	public int getPDef()
	{
		if (_item instanceof L2Armor)
			return ((L2Armor)_item).getPDef();
		return 0;
	}
	
	/**
	 * Returns whether this item is augmented or not
	 * 
	 * @return true if augmented
	 */
	public boolean isAugmented()
	{
		return _augmentation != null;
	}
	
	/**
	 * Returns the augmentation object for this item
	 * 
	 * @return augmentation
	 */
	public L2Augmentation getAugmentation()
	{
		return _augmentation;
	}
	
	/**
	 * Sets a new augmentation
	 * 
	 * @param augmentation
	 * @return return true if sucessfull
	 */
	public boolean setAugmentation(L2Augmentation augmentation)
	{
		// there shall be no previous augmentation..
		if (_augmentation != null)
			return false;
		_augmentation = augmentation;
		updateItemAttributes();
		return true;
	}
	
	/**
	 * Remove the augmentation
	 */
	public void removeAugmentation()
	{
		_augmentation = null;

		// An item that is left without any attribute loses its row, otherwise the row keeps what is left
		saveAttributes(null, "remove augmentation");
	}

	public void restoreAttributes()
	{
		try
		{
			ItemRepository.ItemAttributes attributes = ItemRepository.getInstance().loadAttributes(null, getObjectId());
			if (attributes != null)
			{
				if (attributes.elementType() != null && attributes.elementValue() != null)
					_elementals = new Elementals(attributes.elementType().byteValue(), attributes.elementValue());
				if (attributes.augmentationAttributes() != null)
				{
					// An augmentation without a skill has no skill columns
					int skillId = attributes.augmentationSkillId() == null ? 0 : attributes.augmentationSkillId();
					int skillLevel = attributes.augmentationSkillLevel() == null ? 0 : attributes.augmentationSkillLevel();
					_augmentation = new L2Augmentation(attributes.augmentationAttributes(), skillId, skillLevel);
				}
			}
		}
		catch (Exception e)
		{
			_log.error(
					"Could not restore augmentation and elemental data for item " + getObjectId() + " from DB: "
							+ e.getMessage(), e);
		}
	}

	public void updateItemAttributes()
	{
		saveAttributes(null, "update attributes");
	}

	/**
	 * Writes the augmentation and the elemental attribute of the item as they are in memory.
	 *
	 * @param con a connection to use, or null
	 * @param what for the log
	 */
	private void saveAttributes(Connection con, String what)
	{
		// A new item is not in the database yet (the attributes belong to its row); its insert writes them
		if (!_existsInDb)
			return;

		Integer augmentationAttributes = null;
		Integer augmentationSkillId = null;
		Integer augmentationSkillLevel = null;
		if (_augmentation != null)
		{
			augmentationAttributes = _augmentation.getAttributes();
			if (_augmentation.getSkill() != null)
			{
				augmentationSkillId = _augmentation.getSkill().getId();
				augmentationSkillLevel = _augmentation.getSkill().getLevel();
			}
		}

		Integer elementType = null;
		Integer elementValue = null;
		// An element that is not set (-1) is no elemental attribute
		if (_elementals != null && _elementals.getElement() >= 0)
		{
			elementType = (int)_elementals.getElement();
			elementValue = _elementals.getValue();
		}

		try
		{
			ItemRepository.getInstance().saveAttributes(
					con,
					getObjectId(),
					new ItemRepository.ItemAttributes(augmentationAttributes, augmentationSkillId,
							augmentationSkillLevel, elementType, elementValue));
		}
		catch (Exception e)
		{
			_log.error("Could not " + what + " for item: " + getObjectId() + " in DB:", e);
		}
	}
	
	public Elementals getElementals()
	{
		return _elementals;
	}
	
	@Override
	public byte getAttackElementType()
	{
		if (isWeapon() && _elementals != null)
			return _elementals.getElement();
		return -2;
	}
	
	@Override
	public int getAttackElementPower()
	{
		if (isWeapon() && _elementals != null)
			return _elementals.getValue();
		return 0;
	}
	
	@Override
	public int getElementDefAttr(byte element)
	{
		if (isArmor() && _elementals != null && _elementals.getElement() == element)
			return _elementals.getValue();
		return 0;
	}
	
	public void setElementAttr(byte element, int value)
	{
		if (_elementals == null)
		{
			_elementals = new Elementals(element, value);
		}
		else
		{
			_elementals.setElement(element);
			if (!isWeapon() && value > Elementals.ARMOR_VALUES[12])
				_elementals.setValue(Elementals.ARMOR_VALUES[12]);
			else if (value > Elementals.WEAPON_VALUES[12])
				_elementals.setValue(Elementals.WEAPON_VALUES[12]);
			else
				_elementals.setValue(value);
		}
		updateItemAttributes();
	}
	
	public void updateElementAttrBonus(L2Player player)
	{
		if (_elementals == null)
			return;
		
		_elementals.updateBonus(player, isArmor());
	}
	
	public void clearElementAttr()
	{
		_elementals = null;

		// An item that is left without any attribute loses its row, otherwise the row keeps what is left
		saveAttributes(null, "remove elemental enchant");
	}
	
	/**
	 * Used to decrease mana (mana means life time for shadow items)
	 */
	private class ScheduleConsumeManaTask implements Runnable
	{
		@Override
		public void run()
		{
			// decrease mana
			decreaseMana(true);
		}
	}
	
	/**
	 * Returns true if this item is a shadow item Shadow items have a limited life-time
	 * 
	 * @return
	 */
	public boolean isShadowItem()
	{
		return (_mana >= 0);
	}
	
	/**
	 * Returns the remaining mana of this shadow item
	 * 
	 * @return lifeTime
	 */
	public int getMana()
	{
		return _mana;
	}
	
	/**
	 * Decreases the mana of this shadow item, sends a inventory update schedules a new consumption task if non is running optionally one could force a new task
	 * 
	 * @param resetConsumingMana
	 *            a new consumption task if item is equipped
	 */
	public void decreaseMana(boolean resetConsumingMana)
	{
		if (!isShadowItem())
			return;
		
		if (_mana > 0)
			_mana--;
		
		if (_storedInDb)
			_storedInDb = false;
		if (resetConsumingMana)
			_consumingMana = false;
		
		final L2Player player = L2World.getInstance().getPlayer(getOwnerId());
		if (player != null)
		{
			SystemMessage sm;
			switch (_mana)
			{
				case 10:
					sm = new SystemMessage(SystemMessageId.S1S_REMAINING_MANA_IS_NOW_10);
					sm.addItemName(_item);
					player.sendPacket(sm);
					break;
				case 5:
					sm = new SystemMessage(SystemMessageId.S1S_REMAINING_MANA_IS_NOW_5);
					sm.addItemName(_item);
					player.sendPacket(sm);
					break;
				case 1:
					sm = new SystemMessage(SystemMessageId.S1S_REMAINING_MANA_IS_NOW_1);
					sm.addItemName(_item);
					player.sendPacket(sm);
					break;
			}
			
			if (_mana == 0) // The life time has expired
			{
				sm = new SystemMessage(SystemMessageId.S1S_REMAINING_MANA_IS_NOW_0);
				sm.addItemName(_item);
				player.sendPacket(sm);
				
				// unequip
				if (isEquipped())
				{
					L2ItemInstance[] unequiped = player.getInventory().unEquipItemInSlotAndRecord(getLocationSlot());
					InventoryUpdate iu = new InventoryUpdate();
					for (L2ItemInstance element : unequiped)
						iu.addModifiedItem(element);
					player.sendPacket(iu);
				}
				
				if (getLocation() != ItemLocation.WAREHOUSE)
				{
					// destroy
					player.getInventory().destroyItem("L2ItemInstance", this, player, null);
					
					// send update
					InventoryUpdate iu = new InventoryUpdate();
					iu.addRemovedItem(this);
					player.sendPacket(iu);
					
					StatusUpdate su = new StatusUpdate(player.getObjectId());
					su.addAttribute(StatusUpdate.CUR_LOAD, player.getCurrentLoad());
					player.sendPacket(su);
				}
				else
				{
					player.getWarehouse().destroyItem("L2ItemInstance", this, player, null);
				}
				
				// delete from world
				L2World.getInstance().removeObject(this);
			}
			else
			{
				// Reschedule if still equipped
				if (!_consumingMana && isEquipped())
				{
					scheduleConsumeManaTask();
				}
				if (getLocation() != ItemLocation.WAREHOUSE)
				{
					InventoryUpdate iu = new InventoryUpdate();
					iu.addModifiedItem(this);
					player.sendPacket(iu);
				}
			}
		}
	}
	
	private void scheduleConsumeManaTask()
	{
		if (_consumingMana)
			return;
		_consumingMana = true;
		ThreadPoolManager.getInstance().scheduleGeneral(new ScheduleConsumeManaTask(), MANA_CONSUMPTION_RATE);
	}
	
	/**
	 * This function basically returns a set of functions from L2Armor/L2Weapon, but may add additional functions, if this particular item instance is
	 * enhanched for a particular player.
	 * 
	 * @param player :
	 *            L2Creature designating the player
	 * @return Func[]
	 */
	private Func[] _statFuncs;
	
	public Func[] getStatFuncs()
	{
		if (_statFuncs == null)
		{
			if (getItem() instanceof L2Equip)
			{
				final L2Equip equip = (L2Equip)getItem();
				
				if (equip.getFuncTemplates() == null)
				{
					_statFuncs = Func.EMPTY_ARRAY;
				}
				else
				{
					final Func[] funcs = new Func[equip.getFuncTemplates().length];
					
					for (int i = 0; i < equip.getFuncTemplates().length; i++)
						funcs[i] = equip.getFuncTemplates()[i].getFunc(this);
					
					_statFuncs = L2Arrays.compact(funcs);
				}
			}
			else
				_statFuncs = Func.EMPTY_ARRAY;
		}
		
		return _statFuncs;
	}
	
	/**
	 * Updates database.<BR>
	 */
	public void updateDatabase()
	{
		updateDatabase(false);
	}
	
	/**
	* Updates the database.<BR>
	* 
	* @param force if the update should necessarilly be done.
	*/
	public void updateDatabase(boolean force)
	{
		// A save writes the item on the transaction's connection. The queue would commit it on its own connection,
		// so a rollback of the player would leave the item behind, or the other way round.
		if (WorldTransaction.current() != null)
		{
			synchronized (this)
			{
				switch (getUpdateMode(force))
				{
					case INSERT:
						insertIntoDb(WorldTransaction.current());
						break;
					case UPDATE:
						updateInDb(WorldTransaction.current());
						break;
					case REMOVE:
						removeFromDb(WorldTransaction.current());
						break;
					case NONE:
						break;
				}
			}
			return;
		}
		
		if (getUpdateMode(force) != UpdateMode.NONE)
			SQLQueue.getInstance().add(UPDATE_DATABASE_QUERY);
	}
	
	/** True when a row for this item is in the database. */
	boolean existsInDatabase()
	{
		return _existsInDb;
	}
	
	/** True when that row matches this item. A change clears it until the next write. */
	boolean storedInDatabase()
	{
		return _storedInDb;
	}
	
	/**
	 * The flags say what the database holds. A write inside a transaction sets them before the commit, so a rollback
	 * puts back the values from before the write.
	 */
	private void restoreFlagsOnRollback()
	{
		if (WorldTransaction.current() == null)
			return;
		
		boolean existed = _existsInDb;
		boolean stored = _storedInDb;
		WorldTransaction.onRollback(() -> {
			synchronized (L2ItemInstance.this)
			{
				_existsInDb = existed;
				_storedInDb = stored;
			}
		});
	}
	
	private final SQLQuery UPDATE_DATABASE_QUERY = new SQLQuery() {
		@Override
		public void execute(Connection con)
		{
			synchronized (L2ItemInstance.this)
			{
				switch (getUpdateMode(true))
				{
					case INSERT:
						insertIntoDb(con);
						break;

					case UPDATE:
						updateInDb(con);
						break;

					case REMOVE:
						removeFromDb(con);
						break;
				}
			}
		}
	};

	/** Records a successful synchronous exchange write before queued writers inspect this item. */
	public synchronized void markStoredAfterExchange(boolean exists)
	{
		_existsInDb = exists;
		_storedInDb = true;
	}
	
	private UpdateMode getUpdateMode(boolean force)
	{
		if (_wear)
			return UpdateMode.NONE;
		
		boolean shouldBeInDb = true;
		shouldBeInDb &= (_ownerId != 0);
		shouldBeInDb &= (_loc != ItemLocation.VOID);
		shouldBeInDb &= (_count != 0 || _loc == ItemLocation.LEASE);
		
		if (_existsInDb)
		{
			if (!shouldBeInDb)
				return UpdateMode.REMOVE;
			
			else if (!Config.LAZY_ITEMS_UPDATE || force)
				return UpdateMode.UPDATE;
		}
		else
		{
			if (shouldBeInDb && _loc != ItemLocation.NPC)
				return UpdateMode.INSERT;
		}
		
		return UpdateMode.NONE;
	}
	
	private static enum UpdateMode
	{
		INSERT,
		UPDATE,
		REMOVE,
		NONE
	}
	
	/**
	 * Returns a L2ItemInstance stored in database from its objectID
	 * 
	 * @param ownerId :
	 *            int designating the objectID of the item
	 * @param row the stored item
	 * @return L2ItemInstance
	 */
	public static L2ItemInstance restoreFromDb(int ownerId, ItemRepository.ItemRow row)
	{
		L2ItemInstance inst = null;
		int objectId, item_id, loc_data, enchant_level, custom_type1, custom_type2, manaLeft;
		long time, count;
		ItemLocation loc;
		try
		{
			objectId = row.id();
			item_id = row.itemTemplateId();
			count = row.count();
			loc = ItemLocation.valueOf(row.location());
			loc_data = row.locationSlot();
			enchant_level = row.enchantLevel();
			custom_type1 = row.customType1();
			custom_type2 = row.customType2();
			manaLeft = row.manaLeft();
			time = row.expireAtMillis();
		}
		catch (Exception e)
		{
			_log.error("Could not restore an item owned by " + ownerId + " from DB:" + e.getMessage(), e);
			return null;
		}
		L2Item item = ItemTable.getInstance().getTemplate(item_id);
		if (item == null)
		{
			_log.error("Item item_id=" + item_id + " not known, object_id=" + objectId);
			return null;
		}
		inst = new L2ItemInstance(objectId, item);
		inst._ownerId = ownerId;
		inst.setCount(count);
		inst._enchantLevel = enchant_level;
		inst._type1 = custom_type1;
		inst._type2 = custom_type2;
		inst._loc = loc;
		inst._locData = loc_data;
		inst._existsInDb = true;
		inst._storedInDb = true;
		
		// Setup life time for shadow weapons
		inst._mana = manaLeft;
		
		inst._time = time;
		
		// consume 1 mana
		if (inst.isShadowItem() && inst.isEquipped())
		{
			inst.decreaseMana(false);
			// if player still not loaded and not found in the world - force task creation
			inst.scheduleConsumeManaTask();
		}
		
		if (inst.isTimeLimitedItem())
			inst.scheduleLifeTimeTask();
		
		//load augmentation and elemental enchant
		if (inst.isEquipable())
		{
			inst.restoreAttributes();
		}
		
		return inst;
	}
	
	/**
	 * Init a dropped L2ItemInstance and add it in the world as a visible object.<BR>
	 * <BR>
	 * <B><U> Actions</U> :</B><BR>
	 * <BR>
	 * <li>Set the x,y,z position of the L2ItemInstance dropped and update its _worldregion </li>
	 * <li>Add the L2ItemInstance dropped to _visibleObjects of its L2WorldRegion</li>
	 * <li>Add the L2ItemInstance dropped in the world as a <B>visible</B> object</li>
	 * <BR>
	 * <BR>
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : This method DOESN'T ADD the object to _allObjects of L2World </B></FONT><BR>
	 * <BR>
	 * <B><U> Assert </U> :</B><BR>
	 * <BR>
	 * <li> _worldRegion == null <I>(L2Object is invisible at the beginning)</I></li>
	 * <BR>
	 * <BR>
	 * <B><U> Example of use </U> :</B><BR>
	 * <BR>
	 * <li> Drop item</li>
	 * <li> Call Pet</li>
	 * <BR>
	 */
	public final void dropMe(L2Creature dropper, int x, int y, int z)
	{
		if (Config.ASSERT)
			assert getPosition().getWorldRegion() == null;
		
		if (Config.GEODATA > 0 && dropper != null)
		{
			Location dropDest =
					GeoData.getInstance().moveCheck(dropper.getX(), dropper.getY(), dropper.getZ(), x, y, z,
							dropper.getInstanceId());
			x = dropDest.getX();
			y = dropDest.getY();
			z = dropDest.getZ();
		}
		
		if (dropper != null)
			setInstanceId(dropper.getInstanceId()); // Inherit instancezone when dropped in visible world
		else
			setInstanceId(-1); // No dropper? Make it a global item...
			
		synchronized (this)
		{
			getPosition().setXYZ(x, y, z);
		}
		
		setDropTime(System.currentTimeMillis());
		setDropperObjectId(dropper != null ? dropper.getObjectId() : 0); //Set the dropper Id for the knownlist packets in sendInfo
		
		// this can synchronize on others instancies, so it's out of
		// synchronized, to avoid deadlocks
		// Add the L2ItemInstance dropped in the world as a visible object
		L2World.getInstance().addVisibleObject(this);
		if (Config.SAVE_DROPPED_ITEM)
			ItemsOnGroundManager.getInstance().save(this);
		setDropperObjectId(0); //Set the dropper Id back to 0 so it no longer shows the drop packet
	}
	
	/**
	 * Update the database with values of the item
	 */
	private void updateInDb(Connection con)
	{
		if (_storedInDb)
			return;
		
		restoreFlagsOnRollback();
		try
		{
			ItemRepository.getInstance().updateItem(con, toRow(), getStoredOwnerKey());
			_existsInDb = true;
			_storedInDb = true;
		}
		catch (Exception e)
		{
			_log.error("Could not update item " + getObjectId(), e);
		}
	}

	/** The item as the database stores it. */
	private ItemRepository.ItemRow toRow()
	{
		return new ItemRepository.ItemRow(getObjectId(), getItemId(), getCount(), getEnchantLevel(), _loc.name(),
				_locData, _type1, _type2, getMana(), getTime());
	}

	/**
	 * @return the id the owner column of the item row holds: the owner id, or for an item that a pet carries the control
	 *         item of the pet (the owner id of such an item is the owner of the pet)
	 */
	private int getStoredOwnerKey()
	{
		if (_loc == ItemLocation.PET || _loc == ItemLocation.PET_EQUIP)
		{
			L2Player owner = L2World.getInstance().getPlayer(_ownerId);
			if (owner == null || owner.getPet() == null)
				throw new IllegalStateException("the pet that carries item " + getObjectId() + " is not summoned");
			return owner.getPet().getControlItemId();
		}
		return _ownerId;
	}
	
	/**
	 * Insert the item in database
	 */
	private void insertIntoDb(Connection con)
	{
		restoreFlagsOnRollback();
		try
		{
			ItemRepository.getInstance().insertItem(con, toRow(), getStoredOwnerKey());
			_existsInDb = true;
			_storedInDb = true;
		}
		catch (Exception e)
		{
			_log.error("Could not insert item " + getObjectId(), e);
		}

		// The attributes belong to the row of the item, so they can only be written now
		if (_existsInDb && (_elementals != null || _augmentation != null))
			saveAttributes(con, "store attributes");
	}
	
	/**
	 * Delete item from database
	 */
	private void removeFromDb(Connection con)
	{
		L2Augmentation augmentation = _augmentation;
		restoreFlagsOnRollback();
		_augmentation = null;
		if (WorldTransaction.current() != null)
			WorldTransaction.onRollback(() -> {
				synchronized (L2ItemInstance.this)
				{
					_augmentation = augmentation;
				}
			});
		
		try
		{
			// The attributes of the item go with its row
			ItemRepository.getInstance().deleteItem(con, getObjectId());
			_existsInDb = false;
			_storedInDb = false;
		}
		catch (Exception e)
		{
			_log.error("Could not delete item " + getObjectId(), e);
		}
	}
	
	/**
	 * Returns the item in String format
	 * 
	 * @return String
	 */
	@Override
	public String toString()
	{
		StringBuffer output = new StringBuffer();
		output.append("item " + getObjectId() + ": ");
		if (getEnchantLevel() > 0)
			output.append("+" + getEnchantLevel() + " ");
		output.append(getItem().getName());
		output.append("(" + getCount() + ")");
		return output.toString();
	}
	
	public void resetOwnerTimer()
	{
		if (itemLootShedule != null)
			itemLootShedule.cancel(true);
		itemLootShedule = null;
	}
	
	public void setItemLootShedule(ScheduledFuture<?> sf)
	{
		itemLootShedule = sf;
	}
	
	public ScheduledFuture<?> getItemLootShedule()
	{
		return itemLootShedule;
	}
	
	public void setProtected(boolean is_protected)
	{
		_protected = is_protected;
	}
	
	public boolean isProtected()
	{
		return _protected;
	}
	
	public boolean isNightLure()
	{
		return (getItemId() >= 8505 && getItemId() <= 8513) || getItemId() == 8485;
	}
	
	public void setCountDecrease(boolean decrease)
	{
		_decrease = decrease;
	}
	
	public boolean getCountDecrease()
	{
		return _decrease;
	}
	
	public void setInitCount(long InitCount)
	{
		_initCount = InitCount;
	}
	
	public long getInitCount()
	{
		return _initCount;
	}
	
	public void restoreInitCount()
	{
		if (_decrease)
			setCount(_initCount);
	}
	
	public int getRestoreTime()
	{
		return _restoreTime;
	}
	
	public void setRestoreTime(int time)
	{
		_restoreTime = time;
	}
	
	public boolean isTimeLimitedItem()
	{
		return (_time > 0);
	}
	
	/**
	 * Returns (current system time + time) of this time limited item
	 * @return Time
	 */
	public long getTime()
	{
		return _time;
	}
	
	public long getRemainingTime()
	{
		return _time - System.currentTimeMillis();
	}
	
	public void endOfLife()
	{
		L2Player player = ((L2Player)L2World.getInstance().findObject(getOwnerId()));
		if (player != null)
		{
			if (isEquipped())
			{
				L2ItemInstance[] unequiped = player.getInventory().unEquipItemInSlotAndRecord(getLocationSlot());
				InventoryUpdate iu = new InventoryUpdate();
				for (L2ItemInstance item : unequiped)
					iu.addModifiedItem(item);
				player.sendPacket(iu);
			}
			
			if (getLocation() != ItemLocation.WAREHOUSE)
			{
				// destroy
				player.getInventory().destroyItem("L2ItemInstance", this, player, null);
				
				// send update
				InventoryUpdate iu = new InventoryUpdate();
				iu.addRemovedItem(this);
				player.sendPacket(iu);
				
				StatusUpdate su = new StatusUpdate(player.getObjectId());
				su.addAttribute(StatusUpdate.CUR_LOAD, player.getCurrentLoad());
				player.sendPacket(su);
			}
			else
			{
				player.getWarehouse().destroyItem("L2ItemInstance", this, player, null);
			}
			player.sendPacket(SystemMessageId.TIME_LIMITED_ITEM_DELETED);
			// delete from world
			L2World.getInstance().removeObject(this);
		}
	}
	
	public void scheduleLifeTimeTask()
	{
		if (!isTimeLimitedItem())
			return;
		if (getRemainingTime() <= 0)
			endOfLife();
		else
		{
			if (_lifeTimeTask != null)
				_lifeTimeTask.cancel(false);
			_lifeTimeTask =
					ThreadPoolManager.getInstance().scheduleGeneral(new ScheduleLifeTimeTask(), getRemainingTime());
		}
	}
	
	private class ScheduleLifeTimeTask implements Runnable
	{
		@Override
		public void run()
		{
			endOfLife();
		}
	}
	
	public boolean isOlyRestrictedItem()
	{
		return (Config.ALT_LIST_OLY_RESTRICTED_ITEMS.contains(getItemId()));
	}
	
	@Override
	public final String getFuncOwnerName()
	{
		return getItem().getFuncOwnerName();
	}
	
	@Override
	public final L2Skill getFuncOwnerSkill()
	{
		return getItem().getFuncOwnerSkill();
	}
	
	/**
	 * Remove a L2ItemInstance from the world and send server->client GetItem packets.<BR>
	 * <BR>
	 * <B><U> Actions</U> :</B><BR>
	 * <BR>
	 * <li>Send a Server->Client Packet GetItem to player that pick up and its _knowPlayers member </li>
	 * <li>Remove the L2Object from the world</li>
	 * <BR>
	 * <BR>
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : This method DOESN'T REMOVE the object from _allObjects of L2World </B></FONT><BR>
	 * <BR>
	 * <B><U> Assert </U> :</B><BR>
	 * <BR>
	 * <li> this instanceof L2ItemInstance</li>
	 * <li> _worldRegion != null <I>(L2Object is visible at the beginning)</I></li>
	 * <BR>
	 * <BR>
	 * <B><U> Example of use </U> :</B><BR>
	 * <BR>
	 * <li> Do Pickup Item : PCInstance and Pet</li>
	 * <BR>
	 * <BR>
	 * 
	 * @param player Player that pick up the item
	 */
	public final void pickupMe(L2Creature player)
	{
		MercTicketManager.getInstance().remPosition(this);
		
		L2WorldRegion oldregion = getPosition().getWorldRegion();
		
		// Create a server->client GetItem packet to pick up the L2ItemInstance
		player.broadcastPacket(new GetItem(this, player.getObjectId()));
		
		synchronized (this)
		{
			getPosition().clearWorldRegion();
		}
		
		ItemsOnGroundManager.getInstance().removeObject(this);
		
		final int itemId = getItemId();
		if (itemId == PlayerInventory.ADENA_ID || itemId == 6353)
		{
			L2Player pc = player.getActingPlayer();
			if (pc != null)
			{
				QuestState qs = pc.getQuestState("255_Tutorial");
				if (qs != null)
					qs.getQuest().notifyEvent("CE" + itemId + "", null, pc);
			}
		}
		
		// this can synchronize on other instances, so it's out of
		// synchronized, to avoid deadlocks
		// Remove the L2ItemInstance from the world
		L2World.getInstance().removeVisibleObject(this, oldregion);
	}
	
	private final ShotState _shotState = new ShotState();
	
	public ShotState getShotState()
	{
		return _shotState;
	}
	
	public boolean isSoulshotCharged()
	{
		return getShotState().isSoulshotCharged();
	}
	
	public boolean isSpiritshotCharged()
	{
		return getShotState().isSpiritshotCharged();
	}
	
	public boolean isBlessedSpiritshotCharged()
	{
		return getShotState().isBlessedSpiritshotCharged();
	}
	
	public boolean isAnySpiritshotCharged()
	{
		return getShotState().isAnySpiritshotCharged();
	}
	
	public boolean isFishshotCharged()
	{
		return getShotState().isFishshotCharged();
	}
	
	public void useSoulshotCharge()
	{
		getShotState().useSoulshotCharge();
	}
	
	public void useSpiritshotCharge()
	{
		getShotState().useSpiritshotCharge();
	}
	
	public void useBlessedSpiritshotCharge()
	{
		getShotState().useBlessedSpiritshotCharge();
	}
	
	public void useFishshotCharge()
	{
		getShotState().useFishshotCharge();
	}
}
