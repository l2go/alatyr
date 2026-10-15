/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.l2jfree.gameserver.network.packets.client;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.Config;
import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.datatables.ItemTable;
import com.l2jfree.gameserver.datatables.MultisellTable.MultiSellEntry;
import com.l2jfree.gameserver.datatables.MultisellTable.MultiSellIngredient;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.itemcontainer.PlayerInventory;
import com.l2jfree.gameserver.idfactory.IdFactory;
import com.l2jfree.gameserver.instancemanager.CastleManager;
import com.l2jfree.gameserver.model.Elementals;
import com.l2jfree.gameserver.model.L2Augmentation;
import com.l2jfree.gameserver.model.clan.L2Clan;
import com.l2jfree.gameserver.model.entity.Castle;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.items.L2ItemInstance.ItemLocation;
import com.l2jfree.gameserver.model.items.templates.L2Item;
import com.l2jfree.gameserver.taskmanager.SQLQueue;

/** Prepares all exchange effects before the only durable commit. */
final class MultiSellAtomicExchange
{
	private static final Logger LOG = LoggerFactory.getLogger(MultiSellAtomicExchange.class);

	interface Resources
	{
		void flush(L2ItemInstance[] items);
		L2Item template(int itemId);
		int nextObjectId();
		void releaseObjectId(int objectId);
		Connection connection() throws SQLException;
	}

	interface Actor
	{
		PlayerInventory inventory();
		int objectId();
		L2Clan clan();
		boolean isClanLeader();
		int fame();
		void setFame(int fame);
	}

	private static Actor actor(final L2Player player)
	{
		return new Actor()
		{
			@Override
			public PlayerInventory inventory()
			{
				return player.getInventory();
			}

			@Override
			public int objectId()
			{
				return player.getObjectId();
			}

			@Override
			public L2Clan clan()
			{
				return player.getClan();
			}

			@Override
			public boolean isClanLeader()
			{
				return player.isClanLeader();
			}

			@Override
			public int fame()
			{
				return player.getFame();
			}

			@Override
			public void setFame(int fame)
			{
				player.setFame(fame);
			}
		};
	}

	private static final Resources DEFAULT_RESOURCES = new Resources()
	{
		@Override
		public void flush(L2ItemInstance[] items)
		{
			for (L2ItemInstance item : items)
				item.updateDatabase(true);
			SQLQueue.getInstance().run();
		}

		@Override
		public L2Item template(int itemId)
		{
			return ItemTable.getInstance().getTemplate(itemId);
		}

		@Override
		public int nextObjectId()
		{
			return IdFactory.getInstance().getNextId();
		}

		@Override
		public void releaseObjectId(int objectId)
		{
			IdFactory.getInstance().releaseId(objectId);
		}

		@Override
		public Connection connection() throws SQLException
		{
			return L2DatabaseFactory.getInstance().getConnection();
		}
	};

	private static final class TreasuryChange
	{
		final Castle castle;
		final long before;
		final long after;

		TreasuryChange(Castle castle, long amount)
		{
			this.castle = castle;
			before = castle.getTreasury();
			after = amount >= PlayerInventory.MAX_ADENA - before
					? PlayerInventory.MAX_ADENA : before + amount;
		}
	}

	private final Actor _actor;
	private final PlayerInventory _inventory;
	private final MultiSellEntry _entry;
	private final long _amount;
	private final boolean _maintainEnchantment;
	private final Castle _castle;
	private final long _tax;
	private final Resources _resources;

	MultiSellAtomicExchange(L2Player player, MultiSellEntry entry, long amount,
			boolean maintainEnchantment, Castle castle, long transactionTax)
	{
		this(player, entry, amount, maintainEnchantment, castle, transactionTax, DEFAULT_RESOURCES);
	}

	MultiSellAtomicExchange(L2Player player, MultiSellEntry entry, long amount,
			boolean maintainEnchantment, Castle castle, long transactionTax, Resources resources)
	{
		this(actor(player), entry, amount, maintainEnchantment, castle, transactionTax, resources);
	}

	MultiSellAtomicExchange(Actor actor, MultiSellEntry entry, long amount,
			boolean maintainEnchantment, Castle castle, long transactionTax, Resources resources)
	{
		_actor = actor;
		_inventory = actor.inventory();
		_entry = entry;
		_amount = amount;
		_maintainEnchantment = maintainEnchantment;
		_castle = castle;
		_tax = transactionTax;
		_resources = resources;
	}

	boolean execute()
	{
		L2ItemInstance[] affected;
		synchronized (_inventory.itemSetLock())
		{
			affected = affectedItems(_inventory.getItems());
		}
		_resources.flush(affected);
		Arrays.sort(affected, new Comparator<L2ItemInstance>()
		{
			@Override
			public int compare(L2ItemInstance left, L2ItemInstance right)
			{
				return Integer.compare(left.getObjectId(), right.getObjectId());
			}
		});
		synchronized (_actor)
		{
			return withItemLocks(affected, 0);
		}
	}

	private boolean withItemLocks(L2ItemInstance[] items, int index)
	{
		if (index == items.length)
			return withCastleLocks(items, taxCastles(), 0);
		synchronized (items[index])
		{
			return withItemLocks(items, index + 1);
		}
	}

	private boolean withCastleLocks(L2ItemInstance[] items, List<Castle> castles, int index)
	{
		if (index == castles.size())
		{
			synchronized (_inventory.itemSetLock())
			{
				if (!Arrays.equals(items, affectedItems(_inventory.getItems())))
					return false;
				return prepareAndCommit();
			}
		}
		synchronized (castles.get(index))
		{
			return withCastleLocks(items, castles, index + 1);
		}
	}

	private List<Castle> taxCastles()
	{
		List<Castle> castles = new ArrayList<Castle>();
		if (_castle == null || _castle.getOwnerId() <= 0 || _tax <= 0)
			return castles;
		castles.add(_castle);
		String name = _castle.getName();
		Castle parent = null;
		if (name.equalsIgnoreCase("Schuttgart") || name.equalsIgnoreCase("Goddard"))
			parent = CastleManager.getInstance().getCastleByName("Rune");
		else if (!name.equalsIgnoreCase("Aden") && !name.equalsIgnoreCase("Rune"))
			parent = CastleManager.getInstance().getCastleByName("Aden");
		if (parent != null && parent != _castle)
			castles.add(parent);
		Collections.sort(castles, new Comparator<Castle>()
		{
			@Override
			public int compare(Castle left, Castle right)
			{
				return Integer.compare(left.getCastleId(), right.getCastleId());
			}
		});
		return castles;
	}

	private List<TreasuryChange> treasuryChanges()
	{
		List<TreasuryChange> changes = new ArrayList<TreasuryChange>();
		if (_castle == null || _castle.getOwnerId() <= 0 || _tax <= 0)
			return changes;
		long amount = Math.multiplyExact(_tax, _amount);
		String name = _castle.getName();
		Castle parent = null;
		if (name.equalsIgnoreCase("Schuttgart") || name.equalsIgnoreCase("Goddard"))
			parent = CastleManager.getInstance().getCastleByName("Rune");
		else if (!name.equalsIgnoreCase("Aden") && !name.equalsIgnoreCase("Rune"))
			parent = CastleManager.getInstance().getCastleByName("Aden");
		if (parent != null)
		{
			long share = Math.multiplyExact(amount, parent.getTaxPercent()) / 100;
			if (share < 0 || share > amount)
				throw new IllegalArgumentException("Invalid castle tax share");
			if (parent.getOwnerId() > 0)
				changes.add(new TreasuryChange(parent, share));
			amount -= share;
		}
		changes.add(new TreasuryChange(_castle, amount));
		return changes;
	}

	private L2ItemInstance[] affectedItems(L2ItemInstance[] inventory)
	{
		Set<Integer> ids = new HashSet<Integer>();
		for (MultiSellIngredient ingredient : _entry.getIngredients())
			if (ingredient.getItemId() > 0)
				ids.add(ingredient.getItemId());
		for (MultiSellIngredient product : _entry.getProducts())
			if (product.getItemId() > 0)
				ids.add(product.getItemId());
		List<L2ItemInstance> result = new ArrayList<L2ItemInstance>();
		for (L2ItemInstance item : inventory)
			if (ids.contains(item.getItemId()))
				result.add(item);
		Collections.sort(result, new Comparator<L2ItemInstance>()
		{
			@Override
			public int compare(L2ItemInstance left, L2ItemInstance right)
			{
				return Integer.compare(left.getObjectId(), right.getObjectId());
			}
		});
		return result.toArray(new L2ItemInstance[result.size()]);
	}

	private boolean prepareAndCommit()
	{
		List<L2ItemInstance> stagedProducts = new ArrayList<L2ItemInstance>();
		List<Integer> stagedIds = new ArrayList<Integer>();
		Map<Integer, L2ItemInstance> stagedStacks = new HashMap<Integer, L2ItemInstance>();
		boolean committed = false;
		try
		{
			MultiSellIngredientReservation reservation = new MultiSellIngredientReservation(_inventory.getItems());
			List<MultiSellIngredientReservation.Requirement> retained =
					new ArrayList<MultiSellIngredientReservation.Requirement>();
			for (MultiSellIngredient ingredient : _entry.getIngredients())
			{
				if (ingredient.getItemId() > 0 && !Config.ALT_BLACKSMITH_USE_RECIPES
						&& ingredient.getMaintainIngredient())
					retained.add(new MultiSellIngredientReservation.Requirement(ingredient.getItemId(),
							_maintainEnchantment ? ingredient.getEnchantmentLevel() : -1,
							ingredient.getItemCount()));
			}
			if (!reservation.reserveRetained(retained))
				return false;

			long reputationDebit = 0;
			long fameDelta = 0;
			for (MultiSellIngredient ingredient : _entry.getIngredients())
			{
				long quantity = Math.multiplyExact(ingredient.getItemCount(), _amount);
				if (quantity < 0)
					return false;
				if (ingredient.getItemId() == -200)
					reputationDebit = Math.addExact(reputationDebit, quantity);
				else if (ingredient.getItemId() == -300)
					fameDelta = Math.subtractExact(fameDelta, quantity);
				else if (ingredient.getItemId() > 0 && (Config.ALT_BLACKSMITH_USE_RECIPES
						|| !ingredient.getMaintainIngredient())
						&& !reservation.reserve(ingredient.getItemId(),
								_maintainEnchantment ? ingredient.getEnchantmentLevel() : -1, quantity))
					return false;
			}

			Map<L2ItemInstance, Long> finalCounts = new IdentityHashMap<L2ItemInstance, Long>();
			List<L2Augmentation> augmentations = new ArrayList<L2Augmentation>();
			Elementals elementals = null;
			for (MultiSellIngredientReservation.Debit debit : reservation.debits())
			{
				L2ItemInstance item = debit.item();
				Long prior = finalCounts.get(item);
				long after = (prior == null ? item.getCount() : prior) - debit.count();
				if (after < 0)
					return false;
				finalCounts.put(item, after);
				if (_maintainEnchantment && !item.isStackable())
				{
					if (item.isAugmented())
						augmentations.add(item.getAugmentation());
					if (item.getElementals() != null)
						elementals = item.getElementals();
				}
			}

			for (MultiSellIngredient product : _entry.getProducts())
			{
				long quantity = Math.multiplyExact(product.getItemCount(), _amount);
				if (quantity <= 0)
					return false;
				if (product.getItemId() == -300)
				{
					fameDelta = Math.addExact(fameDelta, quantity);
					continue;
				}
				if (product.getItemId() == -200)
					continue;
				L2Item template = _resources.template(product.getItemId());
				if (template == null)
					return false;
				if (template.isStackable())
				{
					L2ItemInstance stagedStack = stagedStacks.get(product.getItemId());
					if (stagedStack != null)
					{
						long after = Math.addExact(stagedStack.getCount(), quantity);
						long max = product.getItemId() == PlayerInventory.ADENA_ID
								? PlayerInventory.MAX_ADENA : Integer.MAX_VALUE;
						if (after > max)
							return false;
						stagedStack.setCount(after);
						continue;
					}
					L2ItemInstance existing = null;
					for (L2ItemInstance candidate : _inventory.getAllItemsByItemId(product.getItemId()))
					{
						if (!candidate.isWear() && candidate.isStackable())
						{
							existing = candidate;
							break;
						}
					}
					if (existing != null)
					{
						Long prior = finalCounts.get(existing);
						long after = Math.addExact(prior == null ? existing.getCount() : prior, quantity);
						long max = product.getItemId() == PlayerInventory.ADENA_ID
								? PlayerInventory.MAX_ADENA : Integer.MAX_VALUE;
						if (after > max)
							return false;
						finalCounts.put(existing, after);
						continue;
					}
				}
				long copies = template.isStackable() ? 1 : quantity;
				for (long i = 0; i < copies; i++)
				{
					int objectId = _resources.nextObjectId();
					stagedIds.add(objectId);
					L2ItemInstance item = L2ItemInstance.prepareForMultisell(objectId, template);
					stagedProducts.add(item);
					if (template.isStackable())
						stagedStacks.put(product.getItemId(), item);
					item.setCount(template.isStackable() ? quantity : 1);
						item.setOwnerId(_actor.objectId());
					item.setLocation(ItemLocation.INVENTORY);
					if (_maintainEnchantment && !template.isStackable())
					{
						item.setEnchantLevel(product.getEnchantmentLevel());
						L2Augmentation augmentation = i < augmentations.size() ? augmentations.get((int)i) : null;
						item.setPreparedMultisellAttributes(augmentation, elementals);
					}
				}
			}

			L2Clan clan = _actor.clan();
			if (reputationDebit > 0 && (clan == null || !_actor.isClanLeader()
					|| reputationDebit > clan.getReputationScore()))
				return false;
			long newFame = Math.addExact(_actor.fame(), fameDelta);
			if (newFame < 0 || newFame > Integer.MAX_VALUE)
				return false;
			newFame = Math.min(newFame, Config.MAX_PERSONAL_FAME_POINTS);
			List<TreasuryChange> treasury = treasuryChanges();

			MultiSellPersistence persistence = new MultiSellPersistence();
			List<L2ItemInstance> changedItems = new ArrayList<L2ItemInstance>(finalCounts.keySet());
			Collections.sort(changedItems, new Comparator<L2ItemInstance>()
			{
				@Override
				public int compare(L2ItemInstance left, L2ItemInstance right)
				{
					return Integer.compare(left.getObjectId(), right.getObjectId());
				}
			});
			for (L2ItemInstance item : changedItems)
			{
				long after = finalCounts.get(item);
				if (after != item.getCount())
					persistence.changeCount(item.getObjectId(), _actor.objectId(),
							item.getCount(), after);
			}
			for (L2ItemInstance item : stagedProducts)
			{
					persistence.insert(item.getObjectId(), _actor.objectId(), item.getItemId(),
						item.getCount(), item.getEnchantLevel(), item.getMana(), item.getTime());
				L2Augmentation augmentation = item.getAugmentation();
				Elementals elemental = item.getElementals();
				if (augmentation != null || elemental != null)
					persistence.insertAttributes(item.getObjectId(),
							augmentation == null ? -1 : augmentation.getAttributes(),
							augmentation == null || augmentation.getSkill() == null ? -1 : augmentation.getSkill().getId(),
							augmentation == null || augmentation.getSkill() == null ? -1 : augmentation.getSkill().getLevel(),
							elemental == null ? (byte)-1 : elemental.getElement(),
							elemental == null ? -1 : elemental.getValue());
			}
			if (reputationDebit > 0)
				persistence.adjustClanReputation(clan.getClanId(), clan.getReputationScore(),
						(int)(clan.getReputationScore() - reputationDebit));
			if (fameDelta != 0)
				persistence.adjustFame(_actor.objectId(), _actor.fame(), (int)newFame);
			for (TreasuryChange change : treasury)
				persistence.adjustCastleTreasury(change.castle.getCastleId(), change.before, change.after);

			Connection connection = null;
			try
			{
				connection = _resources.connection();
				persistence.commit(connection);
				committed = true;
			}
			finally
			{
				if (connection != null)
				{
					try
					{
						connection.close();
					}
					catch (SQLException closeFailure)
					{
						LOG.warn("Could not close multisell connection", closeFailure);
					}
				}
			}
			_inventory.publishCommittedMultisell(finalCounts, stagedProducts);
			if (reputationDebit > 0)
				clan.setReputationScore((int)(clan.getReputationScore() - reputationDebit), false);
			if (fameDelta != 0)
				_actor.setFame((int)newFame);
			for (TreasuryChange change : treasury)
				change.castle.publishCommittedTreasury(change.after);
			return true;
		}
		catch (SQLException | ArithmeticException | IllegalArgumentException failure)
		{
			LOG.warn("Multisell exchange rejected for player " + _actor.objectId(), failure);
			return false;
		}
		finally
		{
			if (!committed)
				for (Integer objectId : stagedIds)
					_resources.releaseObjectId(objectId);
		}
	}
}
