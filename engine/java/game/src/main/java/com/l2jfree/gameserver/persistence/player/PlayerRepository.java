/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 */
package com.l2jfree.gameserver.persistence.player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.persistence.WorldTransaction;

/**
 * All the SQL of a player and the rows that belong to the player: subclasses, certifications, skills, skill reuse
 * timers, effects, hennas, shortcuts, macros, teleport bookmarks, recipes, name and title colors, and the birthday.
 * <p>
 * Every method takes a connection from {@link L2DatabaseFactory#getConnection()}, so inside a {@link WorldTransaction}
 * it joins the transaction. The moments are epoch milliseconds in the plain objects, where 0 means "not set" and is
 * stored as NULL. The clan, the apprentice, and the sponsor are stored only when the row they point to exists, so a
 * stale id in memory never makes a save fail.
 */
public final class PlayerRepository
{
	private static final class SingletonHolder
	{
		private static final PlayerRepository INSTANCE = new PlayerRepository();
	}

	public static PlayerRepository getInstance()
	{
		return SingletonHolder.INSTANCE;
	}

	private PlayerRepository()
	{
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Plain objects
	// ---------------------------------------------------------------------------------------------------------------

	/** The columns of a player row that the game keeps in the player. A moment is epoch milliseconds, 0 is not set. */
	public static final class PlayerRow
	{
		public int id;
		public String accountName;
		public String name;
		public String title;
		public int raceId;
		public int activeClassId;
		public int baseClassId;
		public boolean female;
		public int face;
		public int hairStyle;
		public int hairColor;
		public int level;
		public long exp;
		public long expBeforeDeath;
		public int sp;
		public int maxHp;
		public int currentHp;
		public int maxCp;
		public int currentCp;
		public int maxMp;
		public int currentMp;
		public int x;
		public int y;
		public int z;
		public int heading;
		public int karma;
		public int fame;
		public int pvpKills;
		public int pkKills;
		public int accessLevel;
		public boolean online;
		/** Last login or logout, epoch milliseconds; 0 when the character never entered the game. */
		public long lastAccess;
		public long onlineTimeSeconds;
		/** Moment the character will be deleted, epoch milliseconds; 0 when no deletion is pending. */
		public long deleteAt;
		public boolean noble;
		public boolean vip;
		public boolean inSevenSignsDungeon;
		public boolean inJail;
		public long jailRemainingMillis;
		public int newbieRewardMask;
		/** 0 when in no clan. */
		public int clanId;
		public int pledgeType;
		public int pledgeRank;
		public boolean wantsPeace;
		public int academyJoinLevel;
		/** 0 when none. */
		public int apprenticeId;
		/** 0 when none. */
		public int sponsorId;
		/** Epoch milliseconds, 0 when no penalty. */
		public long clanJoinAllowedAt;
		/** Epoch milliseconds, 0 when no penalty. */
		public long clanCreateAllowedAt;
		public int varkaKetraAlliance;
		public int deathPenaltyLevel;
		public int vitalityPoints;
		public int bookmarkSlots;
	}

	public record SubclassRow(int classIndex, int classId, int level, long exp, int sp)
	{
	}

	public record SkillRow(int skillId, int skillLevel)
	{
	}

	/** A skill cooldown: the full delay and the moment (epoch milliseconds) the skill is usable again. */
	public record SkillReuseRow(int skillId, int reuseDelayMillis, long expiresAt)
	{
	}

	public record EffectRow(int skillId, int skillLevel, int remainingCount, int remainingSeconds)
	{
	}

	public record HennaRow(int slot, int hennaId)
	{
	}

	public record ShortcutRow(int slot, int page, int type, int targetId, int level)
	{
	}

	public record MacroRow(int number, int icon, String name, String description, String acronym, String commands)
	{
	}

	public record BookmarkRow(int number, int x, int y, int z, int icon, String tag, String name)
	{
	}

	public record RecipeRow(int recipeId, int classIndex, boolean dwarven)
	{
	}

	/** Colors as six hex digits in RRGGBB order. */
	public record ColorsRow(String nameColor, String titleColor)
	{
	}

	public record BirthdayRow(LocalDate createdOn, int giftClaimedYear)
	{
	}

	/** Everything a player save writes. */
	public static final class PlayerSave
	{
		public PlayerRow player;
		/** The subclasses to update, by class index. */
		public List<SubclassRow> subclasses = new ArrayList<>();
		/** The skill cooldowns that replace the stored ones. */
		public List<SkillReuseRow> skillReuses = new ArrayList<>();
		/** The transformation to store, 0 for none; null leaves the stored one as it is. */
		public Integer transformationId;
		/** The colors to store; null leaves the stored ones as they are. */
		public ColorsRow colors;
	}

	// ---------------------------------------------------------------------------------------------------------------
	// SQL
	// ---------------------------------------------------------------------------------------------------------------

	private static final String INSERT_PLAYER =
			"INSERT INTO player (account_name, id, name, level, max_hp, current_hp, max_cp, current_cp, max_mp, current_mp,"
					+ " face, hair_style, hair_color, is_female, exp, sp, karma, fame, pvp_kills, pk_kills, clan_id, race_id,"
					+ " active_class_id, delete_at, title, access_level, is_online, is_in_seven_signs_dungeon, wants_peace,"
					+ " base_class_id, newbie_reward_mask, is_noble, pledge_rank)"
					+ " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,"
					+ " (SELECT c.id FROM clan c WHERE c.id = ?), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

	private static final String UPDATE_PLAYER =
			"UPDATE player SET level = ?, max_hp = ?, current_hp = ?, max_cp = ?, current_cp = ?, max_mp = ?, current_mp = ?,"
					+ " face = ?, hair_style = ?, hair_color = ?, is_female = ?, heading = ?, x = ?, y = ?, z = ?, exp = ?,"
					+ " exp_before_death = ?, sp = ?, karma = ?, fame = ?, pvp_kills = ?, pk_kills = ?,"
					+ " clan_id = (SELECT c.id FROM clan c WHERE c.id = ?), race_id = ?, active_class_id = ?, delete_at = ?,"
					+ " title = ?, access_level = ?, is_online = ?, is_in_seven_signs_dungeon = ?, wants_peace = ?,"
					+ " base_class_id = ?, online_time_s = ?, is_in_jail = ?, jail_remaining_ms = ?, newbie_reward_mask = ?,"
					+ " is_noble = ?, pledge_rank = ?, pledge_type = ?, academy_join_level = ?,"
					+ " apprentice_player_id = (SELECT p.id FROM player p WHERE p.id = ?),"
					+ " sponsor_player_id = (SELECT p.id FROM player p WHERE p.id = ?), varka_ketra_alliance = ?,"
					+ " clan_join_allowed_at = ?, clan_create_allowed_at = ?, name = ?, death_penalty_level = ?,"
					+ " vitality_points = ?, bookmark_slots = ? WHERE id = ?";

	private static final String SELECT_PLAYER =
			"SELECT id, account_name, name, title, race_id, active_class_id, base_class_id, is_female, face, hair_style,"
					+ " hair_color, level, exp, exp_before_death, sp, max_hp, current_hp, max_cp, current_cp, max_mp,"
					+ " current_mp, x, y, z, heading, karma, fame, pvp_kills, pk_kills, access_level, is_online,"
					+ " last_access_at, online_time_s, delete_at, is_noble, is_vip, is_in_seven_signs_dungeon, is_in_jail,"
					+ " jail_remaining_ms, newbie_reward_mask, clan_id, pledge_type, pledge_rank, wants_peace,"
					+ " academy_join_level, apprentice_player_id, sponsor_player_id, clan_join_allowed_at,"
					+ " clan_create_allowed_at, varka_ketra_alliance, death_penalty_level, vitality_points, bookmark_slots"
					+ " FROM player WHERE id = ?";

	private static final String SELECT_PLAYER_ID_BY_NAME = "SELECT id FROM player WHERE name = ?";
	private static final String DELETE_PLAYER = "DELETE FROM player WHERE id = ?";
	private static final String UPDATE_ONLINE = "UPDATE player SET is_online = ?, last_access_at = ? WHERE id = ?";
	private static final String SELECT_OTHER_CHARACTERS =
			"SELECT id, name FROM player WHERE account_name = ? AND id <> ?";

	private static final String SELECT_TRANSFORMATION = "SELECT transformation_id FROM player WHERE id = ?";
	private static final String UPDATE_TRANSFORMATION = "UPDATE player SET transformation_id = ? WHERE id = ?";

	private static final String SELECT_SUBCLASSES =
			"SELECT class_id, exp, sp, level, class_index FROM player_subclass WHERE player_id = ? ORDER BY class_index";
	private static final String INSERT_SUBCLASS =
			"INSERT INTO player_subclass (player_id, class_id, exp, sp, level, class_index) VALUES (?, ?, ?, ?, ?, ?)";
	private static final String UPDATE_SUBCLASS =
			"UPDATE player_subclass SET exp = ?, sp = ?, level = ?, class_id = ? WHERE player_id = ? AND class_index = ?";
	private static final String DELETE_SUBCLASS = "DELETE FROM player_subclass WHERE player_id = ? AND class_index = ?";

	private static final String SELECT_CERTIFICATION =
			"SELECT certification_level FROM player_subclass_certification WHERE player_id = ? AND class_index = ?";
	private static final String INSERT_CERTIFICATION =
			"INSERT INTO player_subclass_certification (player_id, class_index, certification_level) VALUES (?, ?, ?)"
					+ " ON CONFLICT (player_id, class_index) DO NOTHING";
	private static final String UPDATE_CERTIFICATION =
			"UPDATE player_subclass_certification SET certification_level = ? WHERE player_id = ? AND class_index = ?";
	private static final String DELETE_CERTIFICATIONS = "DELETE FROM player_subclass_certification WHERE player_id = ?";

	private static final String SELECT_SKILLS =
			"SELECT skill_id, skill_level FROM player_skill WHERE player_id = ? AND class_index = ?";
	private static final String UPSERT_SKILL =
			"INSERT INTO player_skill (player_id, class_index, skill_id, skill_level) VALUES (?, ?, ?, ?)"
					+ " ON CONFLICT (player_id, class_index, skill_id) DO UPDATE SET skill_level = EXCLUDED.skill_level";
	private static final String DELETE_SKILL =
			"DELETE FROM player_skill WHERE player_id = ? AND class_index = ? AND skill_id = ?";
	private static final String DELETE_SKILLS = "DELETE FROM player_skill WHERE player_id = ? AND class_index = ?";

	private static final String SELECT_SKILL_REUSES =
			"SELECT skill_id, reuse_delay_ms, expires_at FROM player_skill_reuse WHERE player_id = ?";
	private static final String INSERT_SKILL_REUSE =
			"INSERT INTO player_skill_reuse (player_id, skill_id, reuse_delay_ms, expires_at) VALUES (?, ?, ?, ?)"
					+ " ON CONFLICT (player_id, skill_id) DO UPDATE SET reuse_delay_ms = EXCLUDED.reuse_delay_ms,"
					+ " expires_at = EXCLUDED.expires_at";
	private static final String DELETE_SKILL_REUSES = "DELETE FROM player_skill_reuse WHERE player_id = ?";

	private static final String SELECT_EFFECTS =
			"SELECT skill_id, skill_level, remaining_count, remaining_s FROM player_effect"
					+ " WHERE player_id = ? AND class_index = ? ORDER BY effect_order";
	private static final String INSERT_EFFECT =
			"INSERT INTO player_effect (player_id, class_index, effect_order, skill_id, skill_level, remaining_count,"
					+ " remaining_s) VALUES (?, ?, ?, ?, ?, ?, ?)";
	private static final String DELETE_EFFECTS = "DELETE FROM player_effect WHERE player_id = ? AND class_index = ?";

	private static final String SELECT_HENNAS =
			"SELECT slot, henna_id FROM player_henna WHERE player_id = ? AND class_index = ?";
	private static final String UPSERT_HENNA =
			"INSERT INTO player_henna (player_id, class_index, slot, henna_id) VALUES (?, ?, ?, ?)"
					+ " ON CONFLICT (player_id, class_index, slot) DO UPDATE SET henna_id = EXCLUDED.henna_id";
	private static final String DELETE_HENNA =
			"DELETE FROM player_henna WHERE player_id = ? AND class_index = ? AND slot = ?";
	private static final String DELETE_HENNAS = "DELETE FROM player_henna WHERE player_id = ? AND class_index = ?";

	private static final String SELECT_SHORTCUTS =
			"SELECT slot, page, shortcut_type_id, target_id, level FROM player_shortcut"
					+ " WHERE player_id = ? AND class_index = ?";
	private static final String UPSERT_SHORTCUT =
			"INSERT INTO player_shortcut (player_id, class_index, page, slot, shortcut_type_id, target_id, level)"
					+ " VALUES (?, ?, ?, ?, ?, ?, ?)"
					+ " ON CONFLICT (player_id, class_index, page, slot) DO UPDATE SET"
					+ " shortcut_type_id = EXCLUDED.shortcut_type_id, target_id = EXCLUDED.target_id,"
					+ " level = EXCLUDED.level";
	private static final String DELETE_SHORTCUT =
			"DELETE FROM player_shortcut WHERE player_id = ? AND class_index = ? AND page = ? AND slot = ?";
	private static final String DELETE_SHORTCUTS = "DELETE FROM player_shortcut WHERE player_id = ? AND class_index = ?";

	private static final String SELECT_MACROS =
			"SELECT macro_number, icon, name, description, acronym, commands FROM player_macro WHERE player_id = ?";
	private static final String UPSERT_MACRO =
			"INSERT INTO player_macro (player_id, macro_number, icon, name, description, acronym, commands)"
					+ " VALUES (?, ?, ?, ?, ?, ?, ?)"
					+ " ON CONFLICT (player_id, macro_number) DO UPDATE SET icon = EXCLUDED.icon, name = EXCLUDED.name,"
					+ " description = EXCLUDED.description, acronym = EXCLUDED.acronym, commands = EXCLUDED.commands";
	private static final String DELETE_MACRO = "DELETE FROM player_macro WHERE player_id = ? AND macro_number = ?";

	private static final String SELECT_BOOKMARKS =
			"SELECT bookmark_number, x, y, z, icon, tag, name FROM player_teleport_bookmark WHERE player_id = ?";
	private static final String UPSERT_BOOKMARK =
			"INSERT INTO player_teleport_bookmark (player_id, bookmark_number, x, y, z, icon, tag, name)"
					+ " VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
					+ " ON CONFLICT (player_id, bookmark_number) DO UPDATE SET x = EXCLUDED.x, y = EXCLUDED.y,"
					+ " z = EXCLUDED.z, icon = EXCLUDED.icon, tag = EXCLUDED.tag, name = EXCLUDED.name";
	private static final String UPDATE_BOOKMARK =
			"UPDATE player_teleport_bookmark SET icon = ?, tag = ?, name = ? WHERE player_id = ? AND bookmark_number = ?";
	private static final String DELETE_BOOKMARK =
			"DELETE FROM player_teleport_bookmark WHERE player_id = ? AND bookmark_number = ?";

	private static final String SELECT_RECIPES =
			"SELECT recipe_id, class_index, is_dwarven FROM player_recipe WHERE player_id = ?";
	private static final String SELECT_DWARVEN_RECIPES =
			"SELECT recipe_id FROM player_recipe WHERE player_id = ? AND class_index = ? AND is_dwarven";
	private static final String INSERT_RECIPE =
			"INSERT INTO player_recipe (player_id, class_index, recipe_id, is_dwarven) VALUES (?, ?, ?, ?)"
					+ " ON CONFLICT (player_id, class_index, recipe_id) DO NOTHING";
	private static final String DELETE_RECIPE =
			"DELETE FROM player_recipe WHERE player_id = ? AND recipe_id = ? AND class_index = ?";

	private static final String SELECT_COLORS =
			"SELECT name_color, title_color FROM player_name_title_color WHERE player_id = ?";
	private static final String UPSERT_COLORS =
			"INSERT INTO player_name_title_color (player_id, name_color, title_color) VALUES (?, ?, ?)"
					+ " ON CONFLICT (player_id) DO UPDATE SET name_color = EXCLUDED.name_color,"
					+ " title_color = EXCLUDED.title_color";

	private static final String SELECT_BIRTHDAY =
			"SELECT created_on, gift_claimed_year FROM player_birthday WHERE player_id = ?";
	private static final String UPDATE_BIRTHDAY_CLAIM =
			"UPDATE player_birthday SET gift_claimed_year = ? WHERE player_id = ?";

	// ---------------------------------------------------------------------------------------------------------------
	// Player
	// ---------------------------------------------------------------------------------------------------------------

	/** Adds a new player row; the columns the plain object does not carry keep their defaults. */
	public void insertPlayer(PlayerRow row) throws SQLException
	{
		try (Connection con = L2DatabaseFactory.getInstance().getConnection();
				PreparedStatement ps = con.prepareStatement(INSERT_PLAYER))
		{
			int i = 0;
			ps.setString(++i, row.accountName);
			ps.setInt(++i, row.id);
			ps.setString(++i, row.name);
			ps.setInt(++i, row.level);
			ps.setInt(++i, row.maxHp);
			ps.setInt(++i, row.currentHp);
			ps.setInt(++i, row.maxCp);
			ps.setInt(++i, row.currentCp);
			ps.setInt(++i, row.maxMp);
			ps.setInt(++i, row.currentMp);
			ps.setInt(++i, row.face);
			ps.setInt(++i, row.hairStyle);
			ps.setInt(++i, row.hairColor);
			ps.setBoolean(++i, row.female);
			ps.setLong(++i, row.exp);
			ps.setInt(++i, row.sp);
			ps.setInt(++i, row.karma);
			ps.setInt(++i, row.fame);
			ps.setInt(++i, row.pvpKills);
			ps.setInt(++i, row.pkKills);
			ps.setInt(++i, row.clanId);
			ps.setInt(++i, row.raceId);
			ps.setInt(++i, row.activeClassId);
			setMoment(ps, ++i, row.deleteAt);
			ps.setString(++i, titleOrEmpty(row.title));
			ps.setInt(++i, row.accessLevel);
			ps.setBoolean(++i, row.online);
			ps.setBoolean(++i, row.inSevenSignsDungeon);
			ps.setBoolean(++i, row.wantsPeace);
			ps.setInt(++i, row.baseClassId);
			ps.setInt(++i, row.newbieRewardMask);
			ps.setBoolean(++i, row.noble);
			ps.setInt(++i, row.pledgeRank);
			ps.executeUpdate();
		}
	}

	/** @return the player row, or null when there is none */
	public PlayerRow loadPlayer(int playerId) throws SQLException
	{
		try (Connection con = L2DatabaseFactory.getInstance().getConnection();
				PreparedStatement ps = con.prepareStatement(SELECT_PLAYER))
		{
			ps.setInt(1, playerId);
			try (ResultSet rs = ps.executeQuery())
			{
				if (!rs.next())
					return null;

				PlayerRow row = new PlayerRow();
				row.id = rs.getInt("id");
				row.accountName = rs.getString("account_name");
				row.name = rs.getString("name");
				row.title = rs.getString("title");
				row.raceId = rs.getInt("race_id");
				row.activeClassId = rs.getInt("active_class_id");
				row.baseClassId = rs.getInt("base_class_id");
				row.female = rs.getBoolean("is_female");
				row.face = rs.getInt("face");
				row.hairStyle = rs.getInt("hair_style");
				row.hairColor = rs.getInt("hair_color");
				row.level = rs.getInt("level");
				row.exp = rs.getLong("exp");
				row.expBeforeDeath = rs.getLong("exp_before_death");
				row.sp = rs.getInt("sp");
				row.maxHp = rs.getInt("max_hp");
				row.currentHp = rs.getInt("current_hp");
				row.maxCp = rs.getInt("max_cp");
				row.currentCp = rs.getInt("current_cp");
				row.maxMp = rs.getInt("max_mp");
				row.currentMp = rs.getInt("current_mp");
				row.x = rs.getInt("x");
				row.y = rs.getInt("y");
				row.z = rs.getInt("z");
				row.heading = rs.getInt("heading");
				row.karma = rs.getInt("karma");
				row.fame = rs.getInt("fame");
				row.pvpKills = rs.getInt("pvp_kills");
				row.pkKills = rs.getInt("pk_kills");
				row.accessLevel = rs.getInt("access_level");
				row.online = rs.getBoolean("is_online");
				row.lastAccess = getMoment(rs, "last_access_at");
				row.onlineTimeSeconds = rs.getLong("online_time_s");
				row.deleteAt = getMoment(rs, "delete_at");
				row.noble = rs.getBoolean("is_noble");
				row.vip = rs.getBoolean("is_vip");
				row.inSevenSignsDungeon = rs.getBoolean("is_in_seven_signs_dungeon");
				row.inJail = rs.getBoolean("is_in_jail");
				row.jailRemainingMillis = rs.getLong("jail_remaining_ms");
				row.newbieRewardMask = rs.getInt("newbie_reward_mask");
				row.clanId = rs.getInt("clan_id"); // NULL reads as 0
				row.pledgeType = rs.getInt("pledge_type");
				row.pledgeRank = rs.getInt("pledge_rank");
				row.wantsPeace = rs.getBoolean("wants_peace");
				row.academyJoinLevel = rs.getInt("academy_join_level");
				row.apprenticeId = rs.getInt("apprentice_player_id"); // NULL reads as 0
				row.sponsorId = rs.getInt("sponsor_player_id"); // NULL reads as 0
				row.clanJoinAllowedAt = getMoment(rs, "clan_join_allowed_at");
				row.clanCreateAllowedAt = getMoment(rs, "clan_create_allowed_at");
				row.varkaKetraAlliance = rs.getInt("varka_ketra_alliance");
				row.deathPenaltyLevel = rs.getInt("death_penalty_level");
				row.vitalityPoints = rs.getInt("vitality_points");
				row.bookmarkSlots = rs.getInt("bookmark_slots");
				return row;
			}
		}
	}

	/** @return the id of the player with this name (compared without regard to case), or 0 when there is none */
	public int findPlayerIdByName(String name) throws SQLException
	{
		try (Connection con = L2DatabaseFactory.getInstance().getConnection();
				PreparedStatement ps = con.prepareStatement(SELECT_PLAYER_ID_BY_NAME))
		{
			ps.setString(1, name);
			try (ResultSet rs = ps.executeQuery())
			{
				return rs.next() ? rs.getInt(1) : 0;
			}
		}
	}

	/** Deletes the player; everything that belongs to the player goes with it. */
	public boolean deletePlayer(int playerId) throws SQLException
	{
		try (Connection con = L2DatabaseFactory.getInstance().getConnection();
				PreparedStatement ps = con.prepareStatement(DELETE_PLAYER))
		{
			ps.setInt(1, playerId);
			return ps.executeUpdate() > 0;
		}
	}

	/** @return the ids and names of the other characters of the account, in no particular order */
	public Map<Integer, String> loadOtherCharacters(String accountName, int playerId) throws SQLException
	{
		Map<Integer, String> result = new LinkedHashMap<>();
		try (Connection con = L2DatabaseFactory.getInstance().getConnection();
				PreparedStatement ps = con.prepareStatement(SELECT_OTHER_CHARACTERS))
		{
			ps.setString(1, accountName);
			ps.setInt(2, playerId);
			try (ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
					result.put(rs.getInt("id"), rs.getString("name"));
			}
		}
		return result;
	}

	/** Stores the online flag and the moment of the last login or logout. */
	public void saveOnlineStatus(int playerId, boolean online, long lastAccess) throws SQLException
	{
		try (Connection con = L2DatabaseFactory.getInstance().getConnection();
				PreparedStatement ps = con.prepareStatement(UPDATE_ONLINE))
		{
			ps.setBoolean(1, online);
			setMoment(ps, 2, lastAccess);
			ps.setInt(3, playerId);
			ps.executeUpdate();
		}
	}

	/**
	 * Saves the player row, the subclasses, the skill cooldowns, the transformation, and the colors in one
	 * transaction (it joins the surrounding one, if any).
	 *
	 * @return true if everything was saved; false if the work was rolled back and nothing was saved. When it joins a
	 *         surrounding transaction, a failure is thrown as an exception instead, so the whole transaction rolls back.
	 */
	public boolean savePlayer(PlayerSave save)
	{
		return WorldTransaction.run("Saving player " + save.player.name, () -> {
			try
			{
				updatePlayer(save.player);
				for (SubclassRow subclass : save.subclasses)
					updateSubclass(save.player.id, subclass);
				replaceSkillReuses(save.player.id, save.skillReuses);
				if (save.transformationId != null)
					saveTransformation(save.player.id, save.transformationId);
				if (save.colors != null)
					saveColors(save.player.id, save.colors);
			}
			catch (SQLException e)
			{
				throw new IllegalStateException("Could not save player " + save.player.id, e);
			}
		});
	}

	/** Updates the player row; the account, the VIP flag, the last access, and the transformation are not written. */
	public void updatePlayer(PlayerRow row) throws SQLException
	{
		try (Connection con = L2DatabaseFactory.getInstance().getConnection();
				PreparedStatement ps = con.prepareStatement(UPDATE_PLAYER))
		{
			int i = 0;
			ps.setInt(++i, row.level);
			ps.setInt(++i, row.maxHp);
			ps.setInt(++i, row.currentHp);
			ps.setInt(++i, row.maxCp);
			ps.setInt(++i, row.currentCp);
			ps.setInt(++i, row.maxMp);
			ps.setInt(++i, row.currentMp);
			ps.setInt(++i, row.face);
			ps.setInt(++i, row.hairStyle);
			ps.setInt(++i, row.hairColor);
			ps.setBoolean(++i, row.female);
			ps.setInt(++i, row.heading);
			ps.setInt(++i, row.x);
			ps.setInt(++i, row.y);
			ps.setInt(++i, row.z);
			ps.setLong(++i, row.exp);
			ps.setLong(++i, row.expBeforeDeath);
			ps.setInt(++i, row.sp);
			ps.setInt(++i, row.karma);
			ps.setInt(++i, row.fame);
			ps.setInt(++i, row.pvpKills);
			ps.setInt(++i, row.pkKills);
			ps.setInt(++i, row.clanId);
			ps.setInt(++i, row.raceId);
			ps.setInt(++i, row.activeClassId);
			setMoment(ps, ++i, row.deleteAt);
			ps.setString(++i, titleOrEmpty(row.title));
			ps.setInt(++i, row.accessLevel);
			ps.setBoolean(++i, row.online);
			ps.setBoolean(++i, row.inSevenSignsDungeon);
			ps.setBoolean(++i, row.wantsPeace);
			ps.setInt(++i, row.baseClassId);
			ps.setLong(++i, row.onlineTimeSeconds);
			ps.setBoolean(++i, row.inJail);
			ps.setLong(++i, row.jailRemainingMillis);
			ps.setInt(++i, row.newbieRewardMask);
			ps.setBoolean(++i, row.noble);
			ps.setInt(++i, row.pledgeRank);
			ps.setInt(++i, row.pledgeType);
			ps.setInt(++i, row.academyJoinLevel);
			ps.setInt(++i, row.apprenticeId);
			ps.setInt(++i, row.sponsorId);
			ps.setInt(++i, row.varkaKetraAlliance);
			setMoment(ps, ++i, row.clanJoinAllowedAt);
			setMoment(ps, ++i, row.clanCreateAllowedAt);
			ps.setString(++i, row.name);
			ps.setInt(++i, row.deathPenaltyLevel);
			ps.setInt(++i, row.vitalityPoints);
			ps.setInt(++i, row.bookmarkSlots);
			ps.setInt(++i, row.id);
			ps.executeUpdate();
		}
	}

	/** @return the stored transformation, 0 when the player is not transformed */
	public int loadTransformation(int playerId) throws SQLException
	{
		try (Connection con = L2DatabaseFactory.getInstance().getConnection();
				PreparedStatement ps = con.prepareStatement(SELECT_TRANSFORMATION))
		{
			ps.setInt(1, playerId);
			try (ResultSet rs = ps.executeQuery())
			{
				return rs.next() ? rs.getInt(1) : 0; // NULL reads as 0
			}
		}
	}

	/** @param transformationId the transformation, 0 for none */
	public void saveTransformation(int playerId, int transformationId) throws SQLException
	{
		try (Connection con = L2DatabaseFactory.getInstance().getConnection();
				PreparedStatement ps = con.prepareStatement(UPDATE_TRANSFORMATION))
		{
			if (transformationId > 0)
				ps.setInt(1, transformationId);
			else
				ps.setNull(1, Types.SMALLINT);
			ps.setInt(2, playerId);
			ps.executeUpdate();
		}
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Subclasses and certifications
	// ---------------------------------------------------------------------------------------------------------------

	public List<SubclassRow> loadSubclasses(int playerId) throws SQLException
	{
		List<SubclassRow> result = new ArrayList<>();
		try (Connection con = L2DatabaseFactory.getInstance().getConnection();
				PreparedStatement ps = con.prepareStatement(SELECT_SUBCLASSES))
		{
			ps.setInt(1, playerId);
			try (ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
					result.add(new SubclassRow(rs.getInt("class_index"), rs.getInt("class_id"), rs.getInt("level"),
							rs.getLong("exp"), rs.getInt("sp")));
			}
		}
		return result;
	}

	public void insertSubclass(int playerId, SubclassRow subclass) throws SQLException
	{
		try (Connection con = L2DatabaseFactory.getInstance().getConnection();
				PreparedStatement ps = con.prepareStatement(INSERT_SUBCLASS))
		{
			ps.setInt(1, playerId);
			ps.setInt(2, subclass.classId());
			ps.setLong(3, subclass.exp());
			ps.setInt(4, subclass.sp());
			ps.setInt(5, subclass.level());
			ps.setInt(6, subclass.classIndex());
			ps.executeUpdate();
		}
	}

	public void updateSubclass(int playerId, SubclassRow subclass) throws SQLException
	{
		try (Connection con = L2DatabaseFactory.getInstance().getConnection();
				PreparedStatement ps = con.prepareStatement(UPDATE_SUBCLASS))
		{
			ps.setLong(1, subclass.exp());
			ps.setInt(2, subclass.sp());
			ps.setInt(3, subclass.level());
			ps.setInt(4, subclass.classId());
			ps.setInt(5, playerId);
			ps.setInt(6, subclass.classIndex());
			ps.executeUpdate();
		}
	}

	public void deleteSubclass(int playerId, int classIndex) throws SQLException
	{
		deleteByPlayerAndIndex(DELETE_SUBCLASS, playerId, classIndex);
	}

	/** @return the certification level of the slot, -1 when the slot has no certification row */
	public int loadCertificationLevel(int playerId, int classIndex) throws SQLException
	{
		try (Connection con = L2DatabaseFactory.getInstance().getConnection();
				PreparedStatement ps = con.prepareStatement(SELECT_CERTIFICATION))
		{
			ps.setInt(1, playerId);
			ps.setInt(2, classIndex);
			try (ResultSet rs = ps.executeQuery())
			{
				return rs.next() ? rs.getInt(1) : -1;
			}
		}
	}

	/** Adds the certification row of the slot with level 0; an existing row is kept. */
	public void insertCertification(int playerId, int classIndex) throws SQLException
	{
		try (Connection con = L2DatabaseFactory.getInstance().getConnection();
				PreparedStatement ps = con.prepareStatement(INSERT_CERTIFICATION))
		{
			ps.setInt(1, playerId);
			ps.setInt(2, classIndex);
			ps.setInt(3, 0);
			ps.executeUpdate();
		}
	}

	public void updateCertificationLevel(int playerId, int classIndex, int level) throws SQLException
	{
		try (Connection con = L2DatabaseFactory.getInstance().getConnection();
				PreparedStatement ps = con.prepareStatement(UPDATE_CERTIFICATION))
		{
			ps.setInt(1, level);
			ps.setInt(2, playerId);
			ps.setInt(3, classIndex);
			ps.executeUpdate();
		}
	}

	public void deleteCertifications(int playerId) throws SQLException
	{
		try (Connection con = L2DatabaseFactory.getInstance().getConnection();
				PreparedStatement ps = con.prepareStatement(DELETE_CERTIFICATIONS))
		{
			ps.setInt(1, playerId);
			ps.executeUpdate();
		}
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Skills, skill reuse timers, effects
	// ---------------------------------------------------------------------------------------------------------------

	public List<SkillRow> loadSkills(int playerId, int classIndex) throws SQLException
	{
		List<SkillRow> result = new ArrayList<>();
		try (Connection con = L2DatabaseFactory.getInstance().getConnection();
				PreparedStatement ps = con.prepareStatement(SELECT_SKILLS))
		{
			ps.setInt(1, playerId);
			ps.setInt(2, classIndex);
			try (ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
					result.add(new SkillRow(rs.getInt("skill_id"), rs.getInt("skill_level")));
			}
		}
		return result;
	}

	/** Adds the skill, or sets its level when the slot already has it. */
	public void saveSkill(int playerId, int classIndex, int skillId, int skillLevel) throws SQLException
	{
		try (Connection con = L2DatabaseFactory.getInstance().getConnection();
				PreparedStatement ps = con.prepareStatement(UPSERT_SKILL))
		{
			ps.setInt(1, playerId);
			ps.setInt(2, classIndex);
			ps.setInt(3, skillId);
			ps.setInt(4, skillLevel);
			ps.executeUpdate();
		}
	}

	public void deleteSkill(int playerId, int classIndex, int skillId) throws SQLException
	{
		try (Connection con = L2DatabaseFactory.getInstance().getConnection();
				PreparedStatement ps = con.prepareStatement(DELETE_SKILL))
		{
			ps.setInt(1, playerId);
			ps.setInt(2, classIndex);
			ps.setInt(3, skillId);
			ps.executeUpdate();
		}
	}

	public void deleteSkills(int playerId, int classIndex) throws SQLException
	{
		deleteByPlayerAndIndex(DELETE_SKILLS, playerId, classIndex);
	}

	public List<SkillReuseRow> loadSkillReuses(int playerId) throws SQLException
	{
		List<SkillReuseRow> result = new ArrayList<>();
		try (Connection con = L2DatabaseFactory.getInstance().getConnection();
				PreparedStatement ps = con.prepareStatement(SELECT_SKILL_REUSES))
		{
			ps.setInt(1, playerId);
			try (ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
					result.add(new SkillReuseRow(rs.getInt("skill_id"), rs.getInt("reuse_delay_ms"),
							getMoment(rs, "expires_at")));
			}
		}
		return result;
	}

	/** Replaces all the stored skill cooldowns of the player by the given ones. */
	public void replaceSkillReuses(int playerId, List<SkillReuseRow> reuses) throws SQLException
	{
		try (Connection con = L2DatabaseFactory.getInstance().getConnection())
		{
			try (PreparedStatement ps = con.prepareStatement(DELETE_SKILL_REUSES))
			{
				ps.setInt(1, playerId);
				ps.executeUpdate();
			}

			if (reuses.isEmpty())
				return;

			try (PreparedStatement ps = con.prepareStatement(INSERT_SKILL_REUSE))
			{
				for (SkillReuseRow reuse : reuses)
				{
					ps.setInt(1, playerId);
					ps.setInt(2, reuse.skillId());
					ps.setInt(3, reuse.reuseDelayMillis());
					ps.setTimestamp(4, new Timestamp(reuse.expiresAt()));
					ps.executeUpdate();
				}
			}
		}
	}

	/** @return the effects stored for the slot, in the order they are restored */
	public List<EffectRow> loadEffects(int playerId, int classIndex) throws SQLException
	{
		List<EffectRow> result = new ArrayList<>();
		try (Connection con = L2DatabaseFactory.getInstance().getConnection();
				PreparedStatement ps = con.prepareStatement(SELECT_EFFECTS))
		{
			ps.setInt(1, playerId);
			ps.setInt(2, classIndex);
			try (ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
					result.add(new EffectRow(rs.getInt("skill_id"), rs.getInt("skill_level"),
							rs.getInt("remaining_count"), rs.getInt("remaining_s")));
			}
		}
		return result;
	}

	/** Replaces the effects stored for the slot by the given ones, which are stored in this order. */
	public void replaceEffects(int playerId, int classIndex, List<EffectRow> effects) throws SQLException
	{
		try (Connection con = L2DatabaseFactory.getInstance().getConnection())
		{
			deleteEffects(con, playerId, classIndex);

			if (effects.isEmpty())
				return;

			try (PreparedStatement ps = con.prepareStatement(INSERT_EFFECT))
			{
				int order = 0;
				for (EffectRow effect : effects)
				{
					ps.setInt(1, playerId);
					ps.setInt(2, classIndex);
					ps.setInt(3, ++order);
					ps.setInt(4, effect.skillId());
					ps.setInt(5, effect.skillLevel());
					ps.setInt(6, effect.remainingCount());
					ps.setInt(7, effect.remainingSeconds());
					ps.executeUpdate();
				}
			}
		}
	}

	public void deleteEffects(int playerId, int classIndex) throws SQLException
	{
		deleteByPlayerAndIndex(DELETE_EFFECTS, playerId, classIndex);
	}

	private static void deleteEffects(Connection con, int playerId, int classIndex) throws SQLException
	{
		try (PreparedStatement ps = con.prepareStatement(DELETE_EFFECTS))
		{
			ps.setInt(1, playerId);
			ps.setInt(2, classIndex);
			ps.executeUpdate();
		}
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Hennas, shortcuts, macros, bookmarks, recipes
	// ---------------------------------------------------------------------------------------------------------------

	public List<HennaRow> loadHennas(int playerId, int classIndex) throws SQLException
	{
		List<HennaRow> result = new ArrayList<>();
		try (Connection con = L2DatabaseFactory.getInstance().getConnection();
				PreparedStatement ps = con.prepareStatement(SELECT_HENNAS))
		{
			ps.setInt(1, playerId);
			ps.setInt(2, classIndex);
			try (ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
					result.add(new HennaRow(rs.getInt("slot"), rs.getInt("henna_id")));
			}
		}
		return result;
	}

	public void saveHenna(int playerId, int classIndex, int slot, int hennaId) throws SQLException
	{
		try (Connection con = L2DatabaseFactory.getInstance().getConnection();
				PreparedStatement ps = con.prepareStatement(UPSERT_HENNA))
		{
			ps.setInt(1, playerId);
			ps.setInt(2, classIndex);
			ps.setInt(3, slot);
			ps.setInt(4, hennaId);
			ps.executeUpdate();
		}
	}

	public void deleteHenna(int playerId, int classIndex, int slot) throws SQLException
	{
		try (Connection con = L2DatabaseFactory.getInstance().getConnection();
				PreparedStatement ps = con.prepareStatement(DELETE_HENNA))
		{
			ps.setInt(1, playerId);
			ps.setInt(2, classIndex);
			ps.setInt(3, slot);
			ps.executeUpdate();
		}
	}

	public void deleteHennas(int playerId, int classIndex) throws SQLException
	{
		deleteByPlayerAndIndex(DELETE_HENNAS, playerId, classIndex);
	}

	public List<ShortcutRow> loadShortcuts(int playerId, int classIndex) throws SQLException
	{
		List<ShortcutRow> result = new ArrayList<>();
		try (Connection con = L2DatabaseFactory.getInstance().getConnection();
				PreparedStatement ps = con.prepareStatement(SELECT_SHORTCUTS))
		{
			ps.setInt(1, playerId);
			ps.setInt(2, classIndex);
			try (ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
					result.add(new ShortcutRow(rs.getInt("slot"), rs.getInt("page"), rs.getInt("shortcut_type_id"),
							rs.getInt("target_id"), rs.getInt("level")));
			}
		}
		return result;
	}

	/** Adds the shortcut, or replaces the one in the same slot of the same page. */
	public void saveShortcut(int playerId, int classIndex, ShortcutRow shortcut) throws SQLException
	{
		try (Connection con = L2DatabaseFactory.getInstance().getConnection();
				PreparedStatement ps = con.prepareStatement(UPSERT_SHORTCUT))
		{
			ps.setInt(1, playerId);
			ps.setInt(2, classIndex);
			ps.setInt(3, shortcut.page());
			ps.setInt(4, shortcut.slot());
			ps.setInt(5, shortcut.type());
			ps.setInt(6, shortcut.targetId());
			ps.setInt(7, shortcut.level());
			ps.executeUpdate();
		}
	}

	public void deleteShortcut(int playerId, int classIndex, int page, int slot) throws SQLException
	{
		try (Connection con = L2DatabaseFactory.getInstance().getConnection();
				PreparedStatement ps = con.prepareStatement(DELETE_SHORTCUT))
		{
			ps.setInt(1, playerId);
			ps.setInt(2, classIndex);
			ps.setInt(3, page);
			ps.setInt(4, slot);
			ps.executeUpdate();
		}
	}

	public void deleteShortcuts(int playerId, int classIndex) throws SQLException
	{
		deleteByPlayerAndIndex(DELETE_SHORTCUTS, playerId, classIndex);
	}

	public List<MacroRow> loadMacros(int playerId) throws SQLException
	{
		List<MacroRow> result = new ArrayList<>();
		try (Connection con = L2DatabaseFactory.getInstance().getConnection();
				PreparedStatement ps = con.prepareStatement(SELECT_MACROS))
		{
			ps.setInt(1, playerId);
			try (ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
					result.add(new MacroRow(rs.getInt("macro_number"), rs.getInt("icon"), rs.getString("name"),
							rs.getString("description"), rs.getString("acronym"), rs.getString("commands")));
			}
		}
		return result;
	}

	/** Adds the macro, or replaces the one with the same number. A missing text is stored as an empty one. */
	public void saveMacro(int playerId, MacroRow macro) throws SQLException
	{
		try (Connection con = L2DatabaseFactory.getInstance().getConnection();
				PreparedStatement ps = con.prepareStatement(UPSERT_MACRO))
		{
			ps.setInt(1, playerId);
			ps.setInt(2, macro.number());
			ps.setInt(3, macro.icon());
			ps.setString(4, nonNull(macro.name()));
			ps.setString(5, nonNull(macro.description()));
			ps.setString(6, nonNull(macro.acronym()));
			ps.setString(7, nonNull(macro.commands()));
			ps.executeUpdate();
		}
	}

	public void deleteMacro(int playerId, int macroNumber) throws SQLException
	{
		try (Connection con = L2DatabaseFactory.getInstance().getConnection();
				PreparedStatement ps = con.prepareStatement(DELETE_MACRO))
		{
			ps.setInt(1, playerId);
			ps.setInt(2, macroNumber);
			ps.executeUpdate();
		}
	}

	public List<BookmarkRow> loadBookmarks(int playerId) throws SQLException
	{
		List<BookmarkRow> result = new ArrayList<>();
		try (Connection con = L2DatabaseFactory.getInstance().getConnection();
				PreparedStatement ps = con.prepareStatement(SELECT_BOOKMARKS))
		{
			ps.setInt(1, playerId);
			try (ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
					result.add(new BookmarkRow(rs.getInt("bookmark_number"), rs.getInt("x"), rs.getInt("y"),
							rs.getInt("z"), rs.getInt("icon"), rs.getString("tag"), rs.getString("name")));
			}
		}
		return result;
	}

	/** Adds the bookmark, or replaces the one with the same number. A missing tag is stored as an empty one. */
	public void saveBookmark(int playerId, BookmarkRow bookmark) throws SQLException
	{
		try (Connection con = L2DatabaseFactory.getInstance().getConnection();
				PreparedStatement ps = con.prepareStatement(UPSERT_BOOKMARK))
		{
			ps.setInt(1, playerId);
			ps.setInt(2, bookmark.number());
			ps.setInt(3, bookmark.x());
			ps.setInt(4, bookmark.y());
			ps.setInt(5, bookmark.z());
			ps.setInt(6, bookmark.icon());
			ps.setString(7, nonNull(bookmark.tag()));
			ps.setString(8, nonNull(bookmark.name()));
			ps.executeUpdate();
		}
	}

	public void updateBookmark(int playerId, int bookmarkNumber, int icon, String tag, String name) throws SQLException
	{
		try (Connection con = L2DatabaseFactory.getInstance().getConnection();
				PreparedStatement ps = con.prepareStatement(UPDATE_BOOKMARK))
		{
			ps.setInt(1, icon);
			ps.setString(2, nonNull(tag));
			ps.setString(3, nonNull(name));
			ps.setInt(4, playerId);
			ps.setInt(5, bookmarkNumber);
			ps.executeUpdate();
		}
	}

	public void deleteBookmark(int playerId, int bookmarkNumber) throws SQLException
	{
		try (Connection con = L2DatabaseFactory.getInstance().getConnection();
				PreparedStatement ps = con.prepareStatement(DELETE_BOOKMARK))
		{
			ps.setInt(1, playerId);
			ps.setInt(2, bookmarkNumber);
			ps.executeUpdate();
		}
	}

	/** @return all recipes of the player: the common ones and the dwarven ones of every slot */
	public List<RecipeRow> loadRecipes(int playerId) throws SQLException
	{
		List<RecipeRow> result = new ArrayList<>();
		try (Connection con = L2DatabaseFactory.getInstance().getConnection();
				PreparedStatement ps = con.prepareStatement(SELECT_RECIPES))
		{
			ps.setInt(1, playerId);
			try (ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
					result.add(new RecipeRow(rs.getInt("recipe_id"), rs.getInt("class_index"),
							rs.getBoolean("is_dwarven")));
			}
		}
		return result;
	}

	/** @return the ids of the dwarven recipes of the slot */
	public List<Integer> loadDwarvenRecipes(int playerId, int classIndex) throws SQLException
	{
		List<Integer> result = new ArrayList<>();
		try (Connection con = L2DatabaseFactory.getInstance().getConnection();
				PreparedStatement ps = con.prepareStatement(SELECT_DWARVEN_RECIPES))
		{
			ps.setInt(1, playerId);
			ps.setInt(2, classIndex);
			try (ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
					result.add(rs.getInt(1));
			}
		}
		return result;
	}

	/**
	 * Adds a recipe; a recipe that is already there is kept.
	 *
	 * @param classIndex the slot of a dwarven recipe; 0 for a common one
	 */
	public void insertRecipe(int playerId, int recipeId, int classIndex, boolean dwarven) throws SQLException
	{
		try (Connection con = L2DatabaseFactory.getInstance().getConnection();
				PreparedStatement ps = con.prepareStatement(INSERT_RECIPE))
		{
			ps.setInt(1, playerId);
			ps.setInt(2, classIndex);
			ps.setInt(3, recipeId);
			ps.setBoolean(4, dwarven);
			ps.executeUpdate();
		}
	}

	public void deleteRecipe(int playerId, int recipeId, int classIndex) throws SQLException
	{
		try (Connection con = L2DatabaseFactory.getInstance().getConnection();
				PreparedStatement ps = con.prepareStatement(DELETE_RECIPE))
		{
			ps.setInt(1, playerId);
			ps.setInt(2, recipeId);
			ps.setInt(3, classIndex);
			ps.executeUpdate();
		}
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Colors and birthday
	// ---------------------------------------------------------------------------------------------------------------

	/** @return the colors of the player, or null when the player uses the default colors */
	public ColorsRow loadColors(int playerId) throws SQLException
	{
		try (Connection con = L2DatabaseFactory.getInstance().getConnection();
				PreparedStatement ps = con.prepareStatement(SELECT_COLORS))
		{
			ps.setInt(1, playerId);
			try (ResultSet rs = ps.executeQuery())
			{
				if (!rs.next())
					return null;

				return new ColorsRow(rs.getString("name_color"), rs.getString("title_color"));
			}
		}
	}

	public void saveColors(int playerId, ColorsRow colors) throws SQLException
	{
		try (Connection con = L2DatabaseFactory.getInstance().getConnection();
				PreparedStatement ps = con.prepareStatement(UPSERT_COLORS))
		{
			ps.setInt(1, playerId);
			ps.setString(2, colors.nameColor());
			ps.setString(3, colors.titleColor());
			ps.executeUpdate();
		}
	}

	/** @return the birthday of the player, or null when there is none */
	public BirthdayRow loadBirthday(int playerId) throws SQLException
	{
		try (Connection con = L2DatabaseFactory.getInstance().getConnection();
				PreparedStatement ps = con.prepareStatement(SELECT_BIRTHDAY))
		{
			ps.setInt(1, playerId);
			try (ResultSet rs = ps.executeQuery())
			{
				if (!rs.next())
					return null;

				return new BirthdayRow(rs.getObject("created_on", LocalDate.class), rs.getInt("gift_claimed_year"));
			}
		}
	}

	public void claimBirthdayGift(int playerId, int year) throws SQLException
	{
		try (Connection con = L2DatabaseFactory.getInstance().getConnection();
				PreparedStatement ps = con.prepareStatement(UPDATE_BIRTHDAY_CLAIM))
		{
			ps.setInt(1, year);
			ps.setInt(2, playerId);
			ps.executeUpdate();
		}
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Helpers
	// ---------------------------------------------------------------------------------------------------------------

	/** Runs a statement whose parameters are a player id and a class slot. */
	private static void deleteByPlayerAndIndex(String sql, int playerId, int index) throws SQLException
	{
		try (Connection con = L2DatabaseFactory.getInstance().getConnection();
				PreparedStatement ps = con.prepareStatement(sql))
		{
			ps.setInt(1, playerId);
			ps.setInt(2, index);
			ps.executeUpdate();
		}
	}

	private static String nonNull(String text)
	{
		return text == null ? "" : text;
	}

	/** Binds epoch milliseconds as a moment; 0 or less is "not set" and becomes NULL. */
	/** The column is NOT NULL: a character that was just created has no title yet, which the table stores as "". */
	private static String titleOrEmpty(String title)
	{
		return title == null ? "" : title;
	}

	private static void setMoment(PreparedStatement ps, int index, long millis) throws SQLException
	{
		if (millis > 0)
			ps.setTimestamp(index, new Timestamp(millis));
		else
			ps.setNull(index, Types.TIMESTAMP_WITH_TIMEZONE);
	}

	/** @return the moment as epoch milliseconds, 0 when it is NULL */
	private static long getMoment(ResultSet rs, String column) throws SQLException
	{
		Timestamp moment = rs.getTimestamp(column);
		return moment == null ? 0 : moment.getTime();
	}
}
