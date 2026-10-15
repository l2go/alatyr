/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 */
package com.l2jfree.gameserver.model.items;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.model.items.templates.L2EtcItem;
import com.l2jfree.gameserver.model.items.templates.L2EtcItemType;
import com.l2jfree.gameserver.model.items.templates.L2Item;
import com.l2jfree.gameserver.persistence.WorldDatabase;
import com.l2jfree.gameserver.persistence.WorldTransaction;
import com.l2jfree.gameserver.persistence.item.ItemRepository;
import com.l2jfree.gameserver.persistence.item.ItemRepository.ItemRow;
import com.l2jfree.gameserver.persistence.player.PlayerRepository;
import com.l2jfree.gameserver.persistence.player.PlayerRepository.PlayerRow;
import com.l2jfree.gameserver.persistence.player.PlayerRepository.PlayerSave;
import com.l2jfree.gameserver.templates.StatsSet;
import com.zaxxer.hikari.HikariDataSource;

/**
 * A save of a player and the items it carries is one transaction: a rollback leaves both as they were, and the next
 * save writes them.
 */
@Tag("integration")
class PlayerItemSavePostgresTest
{
	private static final int PLAYER = 1_960_000_001;
	private static final int FRESH = 1_960_000_201;
	private static final int CHANGED = 1_960_000_202;
	private static final String PREFIX = "PlayerItemSavePostgresTest";
	
	private static final PlayerRepository PLAYERS = PlayerRepository.getInstance();
	private static final ItemRepository ITEMS = ItemRepository.getInstance();
	
	@BeforeAll
	static void startTheDatabase()
	{
		WorldDatabase.start();
	}
	
	@AfterEach
	void removeWhatTheTestAdded()
	{
		WorldDatabase.execute("DELETE FROM item WHERE id IN (" + FRESH + ", " + CHANGED + ")");
		WorldDatabase.execute("DELETE FROM player WHERE id = " + PLAYER);
	}
	
	@Test
	@DisplayName("a rolled-back save leaves the player and the items unchanged, and the next save writes them")
	void rolledBackSaveLeavesTheItemsAndTheNextSaveWritesThem() throws Exception
	{
		PLAYERS.insertPlayer(newPlayer());
		L2Item template = adena();
		
		L2ItemInstance changed = item(CHANGED, template, 4);
		assertThat(WorldTransaction.run("store the item", () -> changed.updateDatabase(true))).isTrue();
		assertThat(changed.existsInDatabase()).isTrue();
		assertThat(changed.storedInDatabase()).isTrue();
		assertThat(stored(CHANGED).count()).isEqualTo(4);
		
		changed.setCount(9);
		assertThat(changed.storedInDatabase()).isFalse();
		L2ItemInstance fresh = item(FRESH, template, 3);
		
		boolean committed = WorldTransaction.run("save that fails", () -> {
			fresh.updateDatabase(true);
			changed.updateDatabase(true);
			PlayerRow row = load();
			row.title = "never saved";
			PlayerSave save = new PlayerSave();
			save.player = row;
			PLAYERS.savePlayer(save);
			throw new IllegalStateException("the rest of the work failed");
		});
		
		assertThat(committed).isFalse();
		restart();
		assertThat(load().title).isEmpty();
		assertThat(stored(FRESH)).isNull();
		assertThat(stored(CHANGED).count()).isEqualTo(4);
		assertThat(fresh.existsInDatabase()).isFalse();
		assertThat(fresh.storedInDatabase()).isFalse();
		assertThat(changed.existsInDatabase()).isTrue();
		assertThat(changed.storedInDatabase()).isFalse();
		
		assertThat(WorldTransaction.run("save that commits", () -> {
			fresh.updateDatabase(true);
			changed.updateDatabase(true);
			PlayerRow row = load();
			row.title = "saved";
			PlayerSave save = new PlayerSave();
			save.player = row;
			PLAYERS.savePlayer(save);
		})).isTrue();
		
		restart();
		assertThat(load().title).isEqualTo("saved");
		assertThat(stored(FRESH).count()).isEqualTo(3);
		assertThat(stored(CHANGED).count()).isEqualTo(9);
		assertThat(fresh.existsInDatabase()).isTrue();
		assertThat(fresh.storedInDatabase()).isTrue();
		assertThat(changed.existsInDatabase()).isTrue();
		assertThat(changed.storedInDatabase()).isTrue();
	}
	
	private static PlayerRow newPlayer()
	{
		PlayerRow row = new PlayerRow();
		row.id = PLAYER;
		row.accountName = PREFIX + "_account";
		row.name = PREFIX + "_Hero";
		row.title = "";
		row.raceId = 0;
		row.activeClassId = 0;
		row.baseClassId = 0;
		row.level = 1;
		row.newbieRewardMask = 1;
		return row;
	}
	
	private static PlayerRow load()
	{
		try
		{
			return PLAYERS.loadPlayer(PLAYER);
		}
		catch (Exception e)
		{
			throw new IllegalStateException(e);
		}
	}
	
	private static L2ItemInstance item(int id, L2Item template, long count)
	{
		L2ItemInstance item = new L2ItemInstance(id, template);
		item.setOwnerId(PLAYER);
		item.setLocation(L2ItemInstance.ItemLocation.INVENTORY);
		item.setCount(count);
		return item;
	}
	
	private static L2Item adena()
	{
		StatsSet set = new StatsSet();
		set.set("item_id", 57);
		set.set("item_display_id", 57);
		set.set("name", "Adena");
		set.set("type1", 4);
		set.set("type2", 4);
		set.set("weight", 0);
		set.set("crystallizable", false);
		set.set("stackable", true);
		set.set("material", 0);
		set.set("crystal_type", 0);
		set.set("duration", -1);
		set.set("time", -1);
		set.set("bodypart", 0);
		set.set("price", 0);
		set.set("crystal_count", 0);
		set.set("handler", "none");
		set.set("skills_item", "");
		return new L2EtcItem(L2EtcItemType.MONEY, set);
	}
	
	private static ItemRow stored(int id) throws Exception
	{
		List<ItemRow> rows = ITEMS.loadItems(null, PLAYER, "INVENTORY", "PAPERDOLL");
		for (ItemRow row : rows)
		{
			if (row.id() == id)
				return row;
		}
		return null;
	}
	
	/** Makes the pool open new connections, as after a restart of the server. */
	private static void restart()
	{
		HikariDataSource source = (HikariDataSource)L2DatabaseFactory.getInstance().getDataSource();
		source.getHikariPoolMXBean().softEvictConnections();
	}
}
