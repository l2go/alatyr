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
package com.l2jfree.gameserver.network.packets.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

import com.l2jfree.Config;
import com.l2jfree.gameserver.datatables.ItemTable;
import com.l2jfree.gameserver.datatables.MultisellTable;
import com.l2jfree.gameserver.datatables.MultisellTable.MultiSellEntry;
import com.l2jfree.gameserver.datatables.MultisellTable.MultiSellIngredient;
import com.l2jfree.gameserver.datatables.MultisellTable.MultiSellListContainer;
import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.NpcCastle;
import com.l2jfree.gameserver.gameobjects.itemcontainer.PlayerInventory;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.items.templates.L2Armor;
import com.l2jfree.gameserver.model.items.templates.L2Item;
import com.l2jfree.gameserver.model.items.templates.L2Weapon;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.server.ItemList;
import com.l2jfree.gameserver.network.packets.server.StatusUpdate;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;
import com.l2jfree.gameserver.network.packets.server.UserInfo;
import com.l2jfree.gameserver.util.FloodProtector;
import com.l2jfree.gameserver.util.FloodProtector.Protected;

public final class MultiSellChoose extends L2ClientPacket
{
	private static final String _C__MULTISELLCHOOSE = "[C] B0 MultiSellChoose c[ddqhddhhhhhhhh]";
	
	private int _listId;
	private int _entryId;
	private long _amount;
	private int _enchantment;
	private long _transactionTax; // local handling of taxation
	
	static L2ItemInstance[] eligibleIngredients(L2ItemInstance[] candidates, int enchantLevel)
	{
		ArrayList<L2ItemInstance> eligible = new ArrayList<L2ItemInstance>();
		for (L2ItemInstance item : candidates)
			if (!item.isEquipped() && !item.isWear()
					&& (enchantLevel < 0 || item.getEnchantLevel() == enchantLevel))
				eligible.add(item);
		L2ItemInstance[] result = eligible.toArray(new L2ItemInstance[eligible.size()]);
		Arrays.sort(result, new Comparator<L2ItemInstance>()
		{
			@Override
			public int compare(L2ItemInstance left, L2ItemInstance right)
			{
				return Integer.compare(left.getEnchantLevel(), right.getEnchantLevel());
			}
		});
		return result;
	}

	static boolean hasRequiredIngredientCount(L2ItemInstance[] eligible, long required)
	{
		if (required <= 0 || eligible.length == 0)
			return false;
		if (eligible[0].isStackable())
		{
			long remaining = required;
			for (L2ItemInstance item : eligible)
			{
				remaining -= Math.min(remaining, item.getCount());
				if (remaining == 0)
					return true;
			}
			return false;
		}
		return eligible.length >= required;
	}

	static boolean hasPersistentStack(PlayerInventory inventory, int itemId)
	{
		for (L2ItemInstance item : inventory.getAllItemsByItemId(itemId))
			if (!item.isWear() && item.isStackable())
				return true;
		return false;
	}

	@Override
	protected void readImpl()
	{
		_listId = readD();
		_entryId = readD();
		_amount = readCompQ();
		if (Config.PACKET_FINAL)
		{
			readH();
			readD();
			readD();
			readH(); // elemental attributes
			readH();// elemental attributes
			readH();// elemental attributes
			readH();// elemental attributes
			readH();// elemental attributes
			readH();// elemental attributes
			readH();// elemental attributes
			readH();// elemental attributes
		}
		_enchantment = _entryId % 100000;
		_entryId = _entryId / 100000;
		_transactionTax = 0; // initialize tax amount to 0...
	}
	
	@Override
	protected void runImpl()
	{
		if (_amount < 1 || _amount > 5000)
			return;
		
		L2Player player = getActiveChar();
		if (player == null)
			return;
		
		// Flood protect Multisell
		if (!FloodProtector.tryPerformAction(player, Protected.MULTISELL))
			return;
		
		MultiSellListContainer list = MultisellTable.getInstance().getList(_listId);
		if (list != null)
		{
			L2Npc target = player.getTarget(L2Npc.class);
			if (!player.isGM()
					&& (target == null || !list.checkNpcId(target.getNpcId()) || !target.canInteract(player)))
				return;
			
			for (MultiSellEntry entry : list.getEntries())
			{
				if (entry.getEntryId() == _entryId)
				{
					doExchange(player, entry, list.getApplyTaxes(), list.getMaintainEnchantment(), _enchantment);
					break;
				}
			}
		}
		
		//will always be sent
		sendAF();
	}
	
	private void doExchange(L2Player player, MultiSellEntry templateEntry, boolean applyTaxes,
			boolean maintainEnchantment, int enchantment)
	{
		PlayerInventory inv = player.getInventory();
		
		// given the template entry and information about maintaining enchantment and applying taxes
		// re-create the instance of the entry that will be used for this exchange
		// i.e. change the enchantment level of select ingredient/products and adena amount appropriately.
		final L2Npc merchant = player.getTarget(L2Npc.class);
		if (merchant == null)
			return;
		
		MultiSellEntry entry = prepareEntry(merchant, templateEntry, applyTaxes, maintainEnchantment, enchantment);
		
		long slots = 0;
		long weight = 0;
		for (MultiSellIngredient e : entry.getProducts())
		{
			final L2Item template = ItemTable.getInstance().getTemplate(e.getItemId());
			if (template == null)
				continue;
			try
			{
				long count = Math.multiplyExact(e.getItemCount(), _amount);
				if (count <= 0)
					return;
				if (!template.isStackable())
					slots = Math.addExact(slots, count);
				else if (!hasPersistentStack(inv, e.getItemId()))
					slots = Math.addExact(slots, 1);
				weight = Math.addExact(weight, Math.multiplyExact(count, template.getWeight()));
			}
			catch (ArithmeticException overflow)
			{
				sendPacket(SystemMessageId.YOU_HAVE_EXCEEDED_QUANTITY_THAT_CAN_BE_INPUTTED);
				return;
			}
		}
		if (weight > Integer.MAX_VALUE || slots > Integer.MAX_VALUE)
		{
			sendPacket(SystemMessageId.YOU_HAVE_EXCEEDED_QUANTITY_THAT_CAN_BE_INPUTTED);
			return;
		}

		if (!inv.validateWeight((int)weight))
		{
			sendPacket(SystemMessageId.WEIGHT_LIMIT_EXCEEDED);
			return;
		}
		
		if (!inv.validateCapacity((int)slots))
		{
			sendPacket(SystemMessageId.SLOTS_FULL);
			return;
		}
		
		// Generate a list of distinct ingredients and counts in order to check if the correct item-counts
		// are possessed by the player
		ArrayList<MultiSellIngredient> _ingredientsList = new ArrayList<MultiSellIngredient>();
		boolean newIng = true;
		for (MultiSellIngredient e : entry.getIngredients())
		{
			newIng = true;
			
			// at this point, the template has already been modified so that enchantments are properly included
			// whenever they need to be applied.  Uniqueness of items is thus judged by item id AND enchantment level
			for (MultiSellIngredient ex : _ingredientsList)
			{
				// if the item was already added in the list, merely increment the count
				// this happens if 1 list entry has the same ingredient twice (example 2 swords = 1 dual)
				if ((ex.getItemId() == e.getItemId()) && (ex.getEnchantmentLevel() == e.getEnchantmentLevel()))
				{
					if (ex.getItemCount() + e.getItemCount() >= Integer.MAX_VALUE)
					{
						sendPacket(SystemMessageId.YOU_HAVE_EXCEEDED_QUANTITY_THAT_CAN_BE_INPUTTED);
						_ingredientsList.clear();
						_ingredientsList = null;
						return;
					}
					ex.setItemCount(ex.getItemCount() + e.getItemCount());
					newIng = false;
				}
			}
			if (newIng)
			{
				// if it's a new ingredient, just store its info directly (item id, count, enchantment)
				_ingredientsList.add(new MultiSellIngredient(e));
			}
		}
		// now check if the player has sufficient items in the inventory to cover the ingredients' expences
		for (MultiSellIngredient e : _ingredientsList)
		{
			if (e.getItemCount() * _amount >= Integer.MAX_VALUE)
			{
				sendPacket(SystemMessageId.YOU_HAVE_EXCEEDED_QUANTITY_THAT_CAN_BE_INPUTTED);
				_ingredientsList.clear();
				_ingredientsList = null;
				return;
			}
			switch (e.getItemId())
			{
				case -200: // Clan Reputation Score
				{
					if (player.getClan() == null)
					{
						sendPacket(SystemMessageId.YOU_ARE_NOT_A_CLAN_MEMBER);
						return;
					}
					else if (!player.isClanLeader())
					{
						sendPacket(SystemMessageId.ONLY_THE_CLAN_LEADER_IS_ENABLED);
						return;
					}
					else if (player.getClan().getReputationScore() < e.getItemCount() * _amount)
					{
						sendPacket(SystemMessageId.CLAN_REPUTATION_SCORE_IS_TOO_LOW);
						return;
					}
					break;
				}
				case -300: // Player Fame
				{
					if (player.getFame() < e.getItemCount() * _amount)
					{
						sendPacket(SystemMessageId.NOT_ENOUGH_FAME_POINTS);
						return;
					}
					break;
				}
				default:
				{
					// if this is not a list that maintains enchantment, check the count of all items that have the given id.
					// otherwise, check only the count of items with exactly the needed enchantment level
					L2ItemInstance[] eligible = eligibleIngredients(inv.getAllItemsByItemId(e.getItemId()),
							maintainEnchantment ? e.getEnchantmentLevel() : -1);
					long required = (Config.ALT_BLACKSMITH_USE_RECIPES || !e.getMaintainIngredient())
							? e.getItemCount() * _amount : e.getItemCount();
					if (!hasRequiredIngredientCount(eligible, required))
					{
						sendPacket(SystemMessageId.NOT_ENOUGH_REQUIRED_ITEMS);
						_ingredientsList.clear();
						_ingredientsList = null;
						return;
					}
					
					//TODO: review
					if (ItemTable.getInstance().getTemplate(e.getItemId()).isStackable())
						_enchantment = 0;
					
					break;
				}
			}
		}
		
		_ingredientsList.clear();
		_ingredientsList = null;
		if (!new MultiSellAtomicExchange(player, entry, _amount, maintainEnchantment,
				merchant.getIsInTown() ? merchant.getCastle() : null, _transactionTax).execute())
		{
			requestFailed(SystemMessageId.NOT_ENOUGH_REQUIRED_ITEMS);
			return;
		}

		boolean fameChanged = false;
		for (MultiSellIngredient ingredient : entry.getIngredients())
		{
			if (ingredient.getItemId() == -200)
				sendPacket(new SystemMessage(SystemMessageId.S1_DEDUCTED_FROM_CLAN_REP)
						.addNumber((int)(ingredient.getItemCount() * _amount)));
			else if (ingredient.getItemId() == -300)
				fameChanged = true;
		}
		for (MultiSellIngredient product : entry.getProducts())
		{
			if (product.getItemId() == -300)
			{
				fameChanged = true;
				continue;
			}
			if (product.getItemId() <= 0)
				continue;
			long count = product.getItemCount() * _amount;
			SystemMessage message;
			if (count > 1)
			{
				message = new SystemMessage(SystemMessageId.EARNED_S2_S1_S);
				message.addItemName(product.getItemId());
				message.addItemNumber(count);
			}
			else if (maintainEnchantment && product.getEnchantmentLevel() > 0)
			{
				message = new SystemMessage(SystemMessageId.ACQUIRED_S1_S2);
				message.addNumber(product.getEnchantmentLevel());
				message.addItemName(product.getItemId());
			}
			else
			{
				message = new SystemMessage(SystemMessageId.EARNED_S1);
				message.addItemName(product.getItemId());
			}
			sendPacket(message);
		}
		if (fameChanged)
			sendPacket(new UserInfo(player));

		sendPacket(new ItemList(player, false));
		
		StatusUpdate su = new StatusUpdate(player.getObjectId());
		su.addAttribute(StatusUpdate.CUR_LOAD, player.getCurrentLoad());
		sendPacket(su);
		
	}
	
	// Regarding taxation, the following appears to be the case:
	// a) The count of aa remains unchanged (taxes do not affect aa directly).
	// b) 5/6 of the amount of aa is taxed by the normal tax rate.
	// c) the resulting taxes are added as normal adena value.
	// d) normal adena are taxed fully.
	// e) Items other than adena and ancient adena are not taxed even when the list is taxable.
	// example: If the template has an item worth 120aa, and the tax is 10%,
	// then from 120aa, take 5/6 so that is 100aa, apply the 10% tax in adena (10a)
	// so the final price will be 120aa and 10a!
	private MultiSellEntry prepareEntry(L2Npc merchant, MultiSellEntry templateEntry, boolean applyTaxes,
			boolean maintainEnchantment, int enchantLevel)
	{
		MultiSellEntry newEntry = new MultiSellEntry(templateEntry.getEntryId());
		
		long totalAdenaCount = 0;
		boolean hasIngredient = false;
		
		for (MultiSellIngredient ing : templateEntry.getIngredients())
		{
			// load the ingredient from the template
			MultiSellIngredient newIngredient = new MultiSellIngredient(ing);
			
			if (newIngredient.getItemId() == PlayerInventory.ADENA_ID && newIngredient.isTaxIngredient())
			{
				double taxRate = 0.0;
				if (applyTaxes)
				{
					if (merchant != null && merchant.getIsInTown())
						taxRate = NpcCastle.multisellRate(merchant.getCastle());
				}
				
				_transactionTax = Math.round(newIngredient.getItemCount() * taxRate);
				totalAdenaCount += _transactionTax;
				continue; // do not yet add this adena amount to the list as non-taxIngredient adena might be entered later (order not guaranteed)
			}
			else if (ing.getItemId() == PlayerInventory.ADENA_ID) // && !ing.isTaxIngredient()
			{
				totalAdenaCount += newIngredient.getItemCount();
				continue; // do not yet add this adena amount to the list as taxIngredient adena might be entered later (order not guaranteed)
			}
			// if it is an armor/weapon, modify the enchantment level appropriately, if necessary
			// not used for clan reputation and fame
			else if (maintainEnchantment && newIngredient.getItemId() > 0)
			{
				L2Item tempItem = ItemTable.getInstance().getTemplate(newIngredient.getItemId());
				if ((tempItem instanceof L2Armor) || (tempItem instanceof L2Weapon))
				{
					newIngredient.setEnchantmentLevel(enchantLevel);
					hasIngredient = true;
				}
			}
			
			// finally, add this ingredient to the entry
			newEntry.addIngredient(newIngredient);
		}
		// Next add the adena amount, if any
		if (totalAdenaCount > 0)
			newEntry.addIngredient(new MultiSellIngredient(PlayerInventory.ADENA_ID, totalAdenaCount));
		
		// Now modify the enchantment level of products, if necessary
		for (MultiSellIngredient ing : templateEntry.getProducts())
		{
			// load the ingredient from the template
			MultiSellIngredient newIngredient = new MultiSellIngredient(ing);
			
			if (maintainEnchantment && hasIngredient)
			{
				// if it is an armor/weapon, modify the enchantment level appropriately
				// (note, if maintain enchantment is "false" this modification will result to a +0)
				L2Item tempItem = ItemTable.getInstance().getTemplate(newIngredient.getItemId());
				if ((tempItem instanceof L2Armor) || (tempItem instanceof L2Weapon))
					newIngredient.setEnchantmentLevel(enchantLevel);
			}
			newEntry.addProduct(newIngredient);
		}
		return newEntry;
	}
	
	@Override
	public String getType()
	{
		return _C__MULTISELLCHOOSE;
	}
}
