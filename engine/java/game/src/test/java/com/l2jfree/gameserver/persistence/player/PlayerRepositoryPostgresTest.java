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
package com.l2jfree.gameserver.persistence.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.persistence.WorldDatabase;
import com.l2jfree.gameserver.persistence.WorldTransaction;
import com.l2jfree.gameserver.persistence.player.PlayerRepository.BirthdayRow;
import com.l2jfree.gameserver.persistence.player.PlayerRepository.BookmarkRow;
import com.l2jfree.gameserver.persistence.player.PlayerRepository.ColorsRow;
import com.l2jfree.gameserver.persistence.player.PlayerRepository.EffectRow;
import com.l2jfree.gameserver.persistence.player.PlayerRepository.HennaRow;
import com.l2jfree.gameserver.persistence.player.PlayerRepository.MacroRow;
import com.l2jfree.gameserver.persistence.player.PlayerRepository.PlayerRow;
import com.l2jfree.gameserver.persistence.player.PlayerRepository.PlayerSave;
import com.l2jfree.gameserver.persistence.player.PlayerRepository.RecipeRow;
import com.l2jfree.gameserver.persistence.player.PlayerRepository.ShortcutRow;
import com.l2jfree.gameserver.persistence.player.PlayerRepository.SkillReuseRow;
import com.l2jfree.gameserver.persistence.player.PlayerRepository.SkillRow;
import com.l2jfree.gameserver.persistence.player.PlayerRepository.SubclassRow;
import com.zaxxer.hikari.HikariDataSource;

/**
 * The player and its child rows on a real PostgreSQL 18: a saved character is read back after the pool dropped its
 * connections, an update replaces it, a delete takes the children with it, and a save is all or nothing.
 */
@Tag("integration")
class PlayerRepositoryPostgresTest
{
	private static final String PREFIX = "PlayerRepositoryPostgresTest";
	private static final int ID = 1_940_000_001;
	private static final int OTHER_ID = 1_940_000_002;
	private static final String ACCOUNT = PREFIX + "_account";

	private static final PlayerRepository REPOSITORY = PlayerRepository.getInstance();

	@BeforeAll
	static void startTheDatabase()
	{
		WorldDatabase.start();
	}

	@AfterEach
	void removeWhatTheTestAdded()
	{
		WorldDatabase.execute("DELETE FROM player WHERE id IN (" + ID + ", " + OTHER_ID + ")");
	}

	private static PlayerRow newRow(int id, String name)
	{
		PlayerRow row = new PlayerRow();
		row.id = id;
		row.accountName = ACCOUNT;
		row.name = name;
		row.title = "";
		row.raceId = 0;
		row.activeClassId = 0;
		row.baseClassId = 0;
		row.level = 1;
		row.newbieRewardMask = 1;
		return row;
	}

	/** Makes the pool open new connections, as after a restart of the server. */
	private static void restart()
	{
		HikariDataSource source = (HikariDataSource)L2DatabaseFactory.getInstance().getDataSource();
		source.getHikariPoolMXBean().softEvictConnections();
	}

	private static int count(String sql) throws SQLException
	{
		try (Connection connection = L2DatabaseFactory.getInstance().getPoolConnection();
				Statement statement = connection.createStatement();
				ResultSet result = statement.executeQuery(sql))
		{
			result.next();
			return result.getInt(1);
		}
	}

	private static void saveWithChildren() throws SQLException
	{
		PlayerRow row = newRow(ID, PREFIX + "_Hero");
		row.title = "the Tester";
		REPOSITORY.insertPlayer(row);

		REPOSITORY.insertSubclass(ID, new SubclassRow(1, 11, 40, 123_456_789_012L, 5000));
		REPOSITORY.saveSkill(ID, 0, 1001, 3);
		REPOSITORY.saveSkill(ID, 0, 1002, 1);
		REPOSITORY.saveSkill(ID, 1, 1001, 7);
		REPOSITORY.saveShortcut(ID, 0, new ShortcutRow(2, 1, 2, 1001, 3));
		REPOSITORY.saveShortcut(ID, 0, new ShortcutRow(0, 0, 4, 1000, 0));
		REPOSITORY.saveHenna(ID, 0, 1, 7);
		REPOSITORY.saveHenna(ID, 0, 2, 9);
		REPOSITORY.saveMacro(ID, new MacroRow(1000, 3, "heal", "Heals the party", "HP", "3,1,0,/say hi;"));
		REPOSITORY.saveBookmark(ID, new BookmarkRow(1, -71_384, 258_730, -3_104, 2, "town", "Giran"));
		REPOSITORY.insertRecipe(ID, 20, 0, false);
		REPOSITORY.insertRecipe(ID, 21, 1, true);
		REPOSITORY.insertCertification(ID, 1);
		REPOSITORY.updateCertificationLevel(ID, 1, 2);
		REPOSITORY.replaceEffects(ID, 0, List.of(new EffectRow(1204, 2, 5, 120), new EffectRow(1068, 3, 1, 30)));

		PlayerSave save = new PlayerSave();
		save.player = row;
		save.skillReuses.add(new SkillReuseRow(1001, 30_000, 4_102_444_800_000L));
		save.transformationId = 5;
		save.colors = new ColorsRow("00FF00", "0000FF");
		assertThat(REPOSITORY.savePlayer(save)).isTrue();
	}

	@Test
	@DisplayName("a character with its children is read back after the pool opened new connections")
	void saveThenLoadAfterRestart() throws Exception
	{
		saveWithChildren();

		restart();

		PlayerRow row = REPOSITORY.loadPlayer(ID);
		assertThat(row).isNotNull();
		assertThat(row.name).isEqualTo(PREFIX + "_Hero");
		assertThat(row.accountName).isEqualTo(ACCOUNT);
		assertThat(row.title).isEqualTo("the Tester");
		assertThat(row.level).isEqualTo(1);
		assertThat(row.newbieRewardMask).isEqualTo(1);

		assertThat(REPOSITORY.loadSubclasses(ID)).containsExactly(new SubclassRow(1, 11, 40, 123_456_789_012L, 5000));
		assertThat(REPOSITORY.loadSkills(ID, 0)).containsExactlyInAnyOrder(new SkillRow(1001, 3), new SkillRow(1002, 1));
		assertThat(REPOSITORY.loadSkills(ID, 1)).containsExactly(new SkillRow(1001, 7));
		assertThat(REPOSITORY.loadShortcuts(ID, 0)).containsExactlyInAnyOrder(new ShortcutRow(2, 1, 2, 1001, 3),
				new ShortcutRow(0, 0, 4, 1000, 0));
		assertThat(REPOSITORY.loadHennas(ID, 0)).containsExactlyInAnyOrder(new HennaRow(1, 7), new HennaRow(2, 9));
		assertThat(REPOSITORY.loadMacros(ID)).containsExactly(
				new MacroRow(1000, 3, "heal", "Heals the party", "HP", "3,1,0,/say hi;"));
		assertThat(REPOSITORY.loadBookmarks(ID)).containsExactly(
				new BookmarkRow(1, -71_384, 258_730, -3_104, 2, "town", "Giran"));
		assertThat(REPOSITORY.loadRecipes(ID)).containsExactlyInAnyOrder(new RecipeRow(20, 0, false),
				new RecipeRow(21, 1, true));
		assertThat(REPOSITORY.loadDwarvenRecipes(ID, 1)).containsExactly(21);
		assertThat(REPOSITORY.loadDwarvenRecipes(ID, 2)).isEmpty();
		assertThat(REPOSITORY.loadCertificationLevel(ID, 1)).isEqualTo(2);
		assertThat(REPOSITORY.loadCertificationLevel(ID, 2)).isEqualTo(-1);
		assertThat(REPOSITORY.loadEffects(ID, 0)).containsExactly(new EffectRow(1204, 2, 5, 120),
				new EffectRow(1068, 3, 1, 30));
		assertThat(REPOSITORY.loadSkillReuses(ID)).containsExactly(new SkillReuseRow(1001, 30_000, 4_102_444_800_000L));
		assertThat(REPOSITORY.loadTransformation(ID)).isEqualTo(5);
		assertThat(REPOSITORY.loadColors(ID)).isEqualTo(new ColorsRow("00FF00", "0000FF"));
	}

	@Test
	@DisplayName("a saved change replaces the stored values and the replaced child rows")
	void update() throws Exception
	{
		saveWithChildren();

		PlayerRow row = REPOSITORY.loadPlayer(ID);
		row.title = "the Changed";
		row.level = 55;
		row.exp = 9_876_543_210L;
		row.currentHp = 321;
		row.female = true;
		row.online = true;
		row.inJail = true;
		row.jailRemainingMillis = 90_000;
		row.deleteAt = 4_102_444_800_000L;
		row.vitalityPoints = 12_000;
		row.varkaKetraAlliance = -3;

		PlayerSave save = new PlayerSave();
		save.player = row;
		save.subclasses.add(new SubclassRow(1, 12, 41, 5L, 6));
		save.skillReuses.add(new SkillReuseRow(1002, 60_000, 4_102_444_900_000L));
		save.transformationId = 0;
		assertThat(REPOSITORY.savePlayer(save)).isTrue();

		REPOSITORY.saveSkill(ID, 0, 1001, 4);
		REPOSITORY.saveShortcut(ID, 0, new ShortcutRow(2, 1, 2, 1002, 1));
		REPOSITORY.updateBookmark(ID, 1, 5, null, "Aden");
		REPOSITORY.replaceEffects(ID, 0, List.of(new EffectRow(1040, 1, 1, 10)));

		restart();

		PlayerRow loaded = REPOSITORY.loadPlayer(ID);
		assertThat(loaded.title).isEqualTo("the Changed");
		assertThat(loaded.level).isEqualTo(55);
		assertThat(loaded.exp).isEqualTo(9_876_543_210L);
		assertThat(loaded.currentHp).isEqualTo(321);
		assertThat(loaded.female).isTrue();
		assertThat(loaded.online).isTrue();
		assertThat(loaded.inJail).isTrue();
		assertThat(loaded.jailRemainingMillis).isEqualTo(90_000);
		assertThat(loaded.deleteAt).isEqualTo(4_102_444_800_000L);
		assertThat(loaded.vitalityPoints).isEqualTo(12_000);
		assertThat(loaded.varkaKetraAlliance).isEqualTo(-3);
		assertThat(REPOSITORY.loadSubclasses(ID)).containsExactly(new SubclassRow(1, 12, 41, 5L, 6));
		assertThat(REPOSITORY.loadSkills(ID, 0)).containsExactlyInAnyOrder(new SkillRow(1001, 4), new SkillRow(1002, 1));
		assertThat(REPOSITORY.loadShortcuts(ID, 0)).containsExactlyInAnyOrder(new ShortcutRow(2, 1, 2, 1002, 1),
				new ShortcutRow(0, 0, 4, 1000, 0));
		assertThat(REPOSITORY.loadBookmarks(ID)).containsExactly(new BookmarkRow(1, -71_384, 258_730, -3_104, 5, "", "Aden"));
		assertThat(REPOSITORY.loadEffects(ID, 0)).containsExactly(new EffectRow(1040, 1, 1, 10));
		assertThat(REPOSITORY.loadSkillReuses(ID)).containsExactly(new SkillReuseRow(1002, 60_000, 4_102_444_900_000L));
		assertThat(REPOSITORY.loadTransformation(ID)).isZero();
		// The colors were not part of this save and stay as they were
		assertThat(REPOSITORY.loadColors(ID)).isEqualTo(new ColorsRow("00FF00", "0000FF"));

		REPOSITORY.deleteSkill(ID, 0, 1002);
		REPOSITORY.deleteShortcut(ID, 0, 1, 2);
		REPOSITORY.deleteHenna(ID, 0, 1);
		REPOSITORY.deleteMacro(ID, 1000);
		REPOSITORY.deleteBookmark(ID, 1);
		REPOSITORY.deleteRecipe(ID, 20, 0);
		assertThat(REPOSITORY.loadSkills(ID, 0)).containsExactly(new SkillRow(1001, 4));
		assertThat(REPOSITORY.loadShortcuts(ID, 0)).containsExactly(new ShortcutRow(0, 0, 4, 1000, 0));
		assertThat(REPOSITORY.loadHennas(ID, 0)).containsExactly(new HennaRow(2, 9));
		assertThat(REPOSITORY.loadMacros(ID)).isEmpty();
		assertThat(REPOSITORY.loadBookmarks(ID)).isEmpty();
		assertThat(REPOSITORY.loadRecipes(ID)).containsExactly(new RecipeRow(21, 1, true));

		REPOSITORY.deleteSkills(ID, 1);
		REPOSITORY.deleteHennas(ID, 0);
		REPOSITORY.deleteShortcuts(ID, 0);
		REPOSITORY.deleteEffects(ID, 0);
		REPOSITORY.deleteSubclass(ID, 1);
		REPOSITORY.deleteCertifications(ID);
		assertThat(REPOSITORY.loadSkills(ID, 1)).isEmpty();
		assertThat(REPOSITORY.loadHennas(ID, 0)).isEmpty();
		assertThat(REPOSITORY.loadShortcuts(ID, 0)).isEmpty();
		assertThat(REPOSITORY.loadEffects(ID, 0)).isEmpty();
		assertThat(REPOSITORY.loadSubclasses(ID)).isEmpty();
		assertThat(REPOSITORY.loadCertificationLevel(ID, 1)).isEqualTo(-1);
	}

	@Test
	@DisplayName("deleting a character takes all its child rows with it")
	void delete() throws Exception
	{
		saveWithChildren();
		WorldDatabase.execute("INSERT INTO player_birthday (player_id, created_on, gift_claimed_year) VALUES (" + ID
				+ ", DATE '2020-02-29', 2024)");

		for (String table : List.of("player_subclass", "player_subclass_certification", "player_skill",
				"player_skill_reuse", "player_effect", "player_henna", "player_shortcut", "player_macro",
				"player_teleport_bookmark", "player_recipe", "player_name_title_color", "player_birthday"))
			assertThat(count("SELECT count(*) FROM " + table + " WHERE player_id = " + ID)).as(table).isPositive();

		assertThat(REPOSITORY.deletePlayer(ID)).isTrue();
		assertThat(REPOSITORY.deletePlayer(ID)).isFalse();

		assertThat(REPOSITORY.loadPlayer(ID)).isNull();
		for (String table : List.of("player_subclass", "player_subclass_certification", "player_skill",
				"player_skill_reuse", "player_effect", "player_henna", "player_shortcut", "player_macro",
				"player_teleport_bookmark", "player_recipe", "player_name_title_color", "player_birthday"))
			assertThat(count("SELECT count(*) FROM " + table + " WHERE player_id = " + ID)).as(table).isZero();
	}

	@Test
	@DisplayName("a name is found and kept unique without regard to case")
	void caseInsensitiveName() throws Exception
	{
		REPOSITORY.insertPlayer(newRow(ID, PREFIX + "_MixedCase"));

		assertThat(REPOSITORY.findPlayerIdByName(PREFIX.toUpperCase() + "_MIXEDCASE")).isEqualTo(ID);
		assertThat(REPOSITORY.findPlayerIdByName(PREFIX.toLowerCase() + "_mixedcase")).isEqualTo(ID);
		assertThat(REPOSITORY.findPlayerIdByName(PREFIX + "_NoSuchName")).isZero();

		assertThatThrownBy(() -> REPOSITORY.insertPlayer(newRow(OTHER_ID, PREFIX.toLowerCase() + "_MIXEDCASE")))
				.isInstanceOf(SQLException.class);
		assertThat(REPOSITORY.loadPlayer(OTHER_ID)).isNull();
	}

	@Test
	@DisplayName("the other characters of an account are listed by id and name")
	void otherCharactersOfTheAccount() throws Exception
	{
		REPOSITORY.insertPlayer(newRow(ID, PREFIX + "_First"));
		REPOSITORY.insertPlayer(newRow(OTHER_ID, PREFIX + "_Second"));

		assertThat(REPOSITORY.loadOtherCharacters(ACCOUNT.toUpperCase(), ID)).isEqualTo(
				Map.of(OTHER_ID, PREFIX + "_Second"));
		assertThat(REPOSITORY.loadOtherCharacters(ACCOUNT, OTHER_ID)).isEqualTo(Map.of(ID, PREFIX + "_First"));
	}

	@Test
	@DisplayName("a character created without a title is stored with an empty title")
	void aNewCharacterHasAnEmptyTitle() throws Exception
	{
		PlayerRow row = newRow(ID, PREFIX + "_Untitled");
		row.title = null;
		REPOSITORY.insertPlayer(row);

		assertThat(REPOSITORY.loadPlayer(ID).title).isEmpty();

		row.title = "Knight";
		REPOSITORY.updatePlayer(row);
		assertThat(REPOSITORY.loadPlayer(ID).title).isEqualTo("Knight");

		row.title = null;
		REPOSITORY.updatePlayer(row);
		assertThat(REPOSITORY.loadPlayer(ID).title).isEmpty();
	}

	@Test
	@DisplayName("a value that is not set is NULL in the database and 0 in the game")
	void notSetValues() throws Exception
	{
		PlayerRow row = newRow(ID, PREFIX + "_Fresh");
		row.clanId = 0;
		row.lastAccess = 0;
		row.deleteAt = 0;
		row.apprenticeId = 0;
		row.sponsorId = 0;
		row.clanJoinAllowedAt = 0;
		row.clanCreateAllowedAt = 0;
		REPOSITORY.insertPlayer(row);

		assertThat(count("SELECT count(*) FROM player WHERE id = " + ID + " AND clan_id IS NULL"
				+ " AND last_access_at IS NULL AND delete_at IS NULL AND apprentice_player_id IS NULL"
				+ " AND sponsor_player_id IS NULL AND clan_join_allowed_at IS NULL"
				+ " AND clan_create_allowed_at IS NULL AND transformation_id IS NULL")).isEqualTo(1);

		PlayerRow loaded = REPOSITORY.loadPlayer(ID);
		assertThat(loaded.clanId).isZero();
		assertThat(loaded.lastAccess).isZero();
		assertThat(loaded.deleteAt).isZero();
		assertThat(loaded.apprenticeId).isZero();
		assertThat(loaded.sponsorId).isZero();
		assertThat(loaded.clanJoinAllowedAt).isZero();
		assertThat(loaded.clanCreateAllowedAt).isZero();
		assertThat(REPOSITORY.loadTransformation(ID)).isZero();
		assertThat(REPOSITORY.loadColors(ID)).isNull();
		assertThat(REPOSITORY.loadBirthday(ID)).isNull();

		// A moment is set by the login and stays when a save does not know it
		REPOSITORY.saveOnlineStatus(ID, true, 1_700_000_000_000L);
		loaded = REPOSITORY.loadPlayer(ID);
		assertThat(loaded.online).isTrue();
		assertThat(loaded.lastAccess).isEqualTo(1_700_000_000_000L);
		PlayerSave save = new PlayerSave();
		save.player = loaded;
		assertThat(REPOSITORY.savePlayer(save)).isTrue();
		loaded = REPOSITORY.loadPlayer(ID);
		assertThat(loaded.online).isTrue();
		assertThat(loaded.lastAccess).isEqualTo(1_700_000_000_000L);

		// A clan, an apprentice, and a sponsor that do not exist are stored as not set instead of failing the save
		loaded.clanId = 1_940_000_999;
		loaded.apprenticeId = 1_940_000_998;
		loaded.sponsorId = 1_940_000_997;
		save = new PlayerSave();
		save.player = loaded;
		assertThat(REPOSITORY.savePlayer(save)).isTrue();
		loaded = REPOSITORY.loadPlayer(ID);
		assertThat(loaded.clanId).isZero();
		assertThat(loaded.apprenticeId).isZero();
		assertThat(loaded.sponsorId).isZero();
	}

	@Test
	@DisplayName("the birthday of a character is read and its gift is claimed")
	void birthday() throws Exception
	{
		REPOSITORY.insertPlayer(newRow(ID, PREFIX + "_Born"));
		WorldDatabase.execute("INSERT INTO player_birthday (player_id, created_on, gift_claimed_year) VALUES (" + ID
				+ ", DATE '2020-02-29', 2020)");

		assertThat(REPOSITORY.loadBirthday(ID)).isEqualTo(new BirthdayRow(LocalDate.of(2020, 2, 29), 2020));

		REPOSITORY.claimBirthdayGift(ID, 2026);

		assertThat(REPOSITORY.loadBirthday(ID)).isEqualTo(new BirthdayRow(LocalDate.of(2020, 2, 29), 2026));
	}

	@Test
	@DisplayName("a save that fails in one table changes nothing in any table")
	void saveIsAtomic() throws Exception
	{
		saveWithChildren();

		PlayerRow row = REPOSITORY.loadPlayer(ID);
		row.title = "never saved";
		row.level = 80;
		PlayerSave save = new PlayerSave();
		save.player = row;
		save.subclasses.add(new SubclassRow(1, 9_999, 41, 5L, 6)); // no such class: the foreign key refuses it
		save.skillReuses.add(new SkillReuseRow(2002, 10_000, 4_102_444_800_000L));
		save.colors = new ColorsRow("FFFFFF", "FFFFFF");

		assertThat(REPOSITORY.savePlayer(save)).isFalse();

		restart();

		PlayerRow loaded = REPOSITORY.loadPlayer(ID);
		assertThat(loaded.title).isEqualTo("the Tester");
		assertThat(loaded.level).isEqualTo(1);
		assertThat(REPOSITORY.loadSubclasses(ID)).containsExactly(new SubclassRow(1, 11, 40, 123_456_789_012L, 5000));
		assertThat(REPOSITORY.loadSkillReuses(ID)).containsExactly(new SkillReuseRow(1001, 30_000, 4_102_444_800_000L));
		assertThat(REPOSITORY.loadColors(ID)).isEqualTo(new ColorsRow("00FF00", "0000FF"));
	}

	@Test
	@DisplayName("a save inside a surrounding transaction rolls back with it")
	void saveJoinsTheSurroundingTransaction() throws Exception
	{
		REPOSITORY.insertPlayer(newRow(ID, PREFIX + "_Joined"));

		boolean committed = WorldTransaction.run("test", () -> {
			PlayerRow row = newRow(ID, PREFIX + "_Joined");
			row.title = "inside";
			PlayerSave save = new PlayerSave();
			save.player = row;
			REPOSITORY.savePlayer(save);
			throw new IllegalStateException("the rest of the work failed");
		});

		assertThat(committed).isFalse();
		assertThat(REPOSITORY.loadPlayer(ID).title).isEmpty();
	}
}
