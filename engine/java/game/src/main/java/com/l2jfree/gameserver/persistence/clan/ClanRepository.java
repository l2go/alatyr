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
package com.l2jfree.gameserver.persistence.clan;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.persistence.WorldTransaction;

/**
 * All the SQL of clans: the clan itself, its members, notice, rank privileges, skills, sub-units, wars and alliance
 * (schema <code>world</code>, tables <code>clan</code>, <code>clan_notice</code>, <code>clan_rank_privilege</code>,
 * <code>clan_skill</code>, <code>clan_subpledge</code>, <code>clan_war</code>), and of the crest images (table
 * <code>crest</code>).
 * <p>
 * Plain Java values go in and out. The game uses 0 for "none" (no castle, no alliance, no crest, no leader) and
 * milliseconds since the epoch for moments, and this class converts them to NULL and <code>timestamptz</code> and back.
 * Every operation takes its connection from {@link L2DatabaseFactory#getConnection()}, so it joins a surrounding
 * {@link WorldTransaction}. An operation that changes several rows runs in a transaction of its own when none is
 * running.
 */
public final class ClanRepository
{
	/** Crest kind of a clan crest. */
	public static final String KIND_CLAN = "clan";
	/** Crest kind of a large clan crest. */
	public static final String KIND_CLAN_LARGE = "clan_large";
	/** Crest kind of an alliance crest. */
	public static final String KIND_ALLIANCE = "alliance";

	/** The kinds of alliance penalty, indexed by the game's L2Clan.PENALTY_TYPE_* value; index 0 is "no penalty". */
	private static final String[] PENALTY_TYPES = { null, "CLAN_LEAVED", "CLAN_DISMISSED", "DISMISS_CLAN", "DISSOLVE_ALLY" };

	private static final String SELECT_CLAN_IDS = "SELECT id FROM clan";
	private static final String SELECT_CLAN =
			"SELECT name, level, castle_id, alliance_id, alliance_name, leader_player_id, crest_id, large_crest_id, "
					+ "alliance_crest_id, reputation_score, clan_hall_auction_id, alliance_penalty_expire_at, "
					+ "alliance_penalty_type, member_penalty_expire_at, dissolve_at FROM clan WHERE id = ?";
	private static final String INSERT_CLAN =
			"INSERT INTO clan (id, name, level, leader_player_id, crest_id, large_crest_id, castle_id, alliance_id, "
					+ "alliance_name, alliance_crest_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
	private static final String UPDATE_CLAN =
			"UPDATE clan SET leader_player_id = ?, alliance_id = ?, alliance_name = ?, alliance_crest_id = ?, "
					+ "reputation_score = ?, alliance_penalty_expire_at = ?, alliance_penalty_type = ?, "
					+ "member_penalty_expire_at = ?, dissolve_at = ? WHERE id = ?";
	private static final String UPDATE_CLAN_LEVEL = "UPDATE clan SET level = ? WHERE id = ?";
	private static final String UPDATE_CLAN_AUCTION = "UPDATE clan SET clan_hall_auction_id = ? WHERE id = ?";
	private static final String UPDATE_CLAN_CREST = "UPDATE clan SET crest_id = ? WHERE id = ?";
	private static final String UPDATE_CLAN_LARGE_CREST = "UPDATE clan SET large_crest_id = ? WHERE id = ?";
	private static final String UPDATE_ALLIANCE_CREST = "UPDATE clan SET alliance_crest_id = ? WHERE alliance_id = ?";
	private static final String CLEAR_ALLIANCE_COPIES =
			"UPDATE clan SET alliance_name = NULL, alliance_crest_id = NULL WHERE alliance_id = ?";
	private static final String DELETE_CLAN = "DELETE FROM clan WHERE id = ?";
	/** Dissolving a clan that owns a castle resets the tax rate of the castle (a residence table). */
	private static final String RESET_CASTLE_TAX = "UPDATE castle SET tax_percent = 0 WHERE id = ?";

	private static final String SELECT_MEMBERS =
			"SELECT name, level, active_class_id, id, title, pledge_rank, pledge_type, apprentice_player_id, "
					+ "sponsor_player_id, race_id, is_female FROM player WHERE clan_id = ?";
	private static final String UPDATE_MEMBER_PLEDGE_TYPE = "UPDATE player SET pledge_type = ? WHERE id = ?";
	private static final String UPDATE_MEMBER_PLEDGE_RANK = "UPDATE player SET pledge_rank = ? WHERE id = ?";
	private static final String UPDATE_MEMBER_SPONSORS =
			"UPDATE player SET apprentice_player_id = ?, sponsor_player_id = ? WHERE id = ?";
	private static final String REMOVE_MEMBER =
			"UPDATE player SET clan_id = NULL, title = '', clan_join_allowed_at = ?, clan_create_allowed_at = ?, "
					+ "wants_peace = false, pledge_type = 0, academy_join_level = 0, apprentice_player_id = NULL, "
					+ "sponsor_player_id = NULL WHERE id = ?";
	private static final String CLEAR_APPRENTICE = "UPDATE player SET apprentice_player_id = NULL WHERE apprentice_player_id = ?";
	private static final String CLEAR_SPONSOR = "UPDATE player SET sponsor_player_id = NULL WHERE sponsor_player_id = ?";

	private static final String INSERT_NOTICE =
			"INSERT INTO clan_notice (clan_id, notice, is_enabled) VALUES (?, ?, ?) ON CONFLICT (clan_id) DO NOTHING";
	private static final String SELECT_NOTICE = "SELECT notice FROM clan_notice WHERE clan_id = ?";
	private static final String UPDATE_NOTICE = "UPDATE clan_notice SET notice = ? WHERE clan_id = ?";
	private static final String SELECT_NOTICE_ENABLED = "SELECT is_enabled FROM clan_notice WHERE clan_id = ?";
	private static final String UPDATE_NOTICE_ENABLED = "UPDATE clan_notice SET is_enabled = ? WHERE clan_id = ?";

	private static final String SELECT_RANK_PRIVILEGES =
			"SELECT pledge_rank, privileges FROM clan_rank_privilege WHERE clan_id = ?";
	private static final String UPSERT_RANK_PRIVILEGE =
			"INSERT INTO clan_rank_privilege (clan_id, pledge_rank, privileges) VALUES (?, ?, ?) "
					+ "ON CONFLICT (clan_id, pledge_rank) DO UPDATE SET privileges = EXCLUDED.privileges";

	private static final String SELECT_SKILLS = "SELECT skill_id, skill_level FROM clan_skill WHERE clan_id = ?";
	private static final String UPSERT_SKILL =
			"INSERT INTO clan_skill (clan_id, skill_id, skill_level) VALUES (?, ?, ?) "
					+ "ON CONFLICT (clan_id, skill_id) DO UPDATE SET skill_level = EXCLUDED.skill_level";

	private static final String SELECT_SUBPLEDGES =
			"SELECT subpledge_type, name, leader_player_id FROM clan_subpledge WHERE clan_id = ?";
	private static final String INSERT_SUBPLEDGE =
			"INSERT INTO clan_subpledge (clan_id, subpledge_type, name, leader_player_id) VALUES (?, ?, ?, ?)";
	private static final String UPDATE_SUBPLEDGE =
			"UPDATE clan_subpledge SET leader_player_id = ?, name = ? WHERE clan_id = ? AND subpledge_type = ?";

	private static final String SELECT_WARS = "SELECT declaring_clan_id, target_clan_id FROM clan_war";
	private static final String INSERT_WAR =
			"INSERT INTO clan_war (declaring_clan_id, target_clan_id) VALUES (?, ?) ON CONFLICT DO NOTHING";
	private static final String DELETE_WAR = "DELETE FROM clan_war WHERE declaring_clan_id = ? AND target_clan_id = ?";
	/** Clans this clan declared war on that did not declare war on it. */
	private static final String SELECT_WARS_DECLARED =
			"SELECT c.name, c.id, c.alliance_id, c.alliance_name FROM clan c JOIN clan_war w ON c.id = w.target_clan_id "
					+ "WHERE w.declaring_clan_id = ? AND w.target_clan_id NOT IN "
					+ "(SELECT declaring_clan_id FROM clan_war WHERE target_clan_id = ?)";
	/** Clans that declared war on this clan and that it did not declare war on. */
	private static final String SELECT_WARS_RECEIVED =
			"SELECT c.name, c.id, c.alliance_id, c.alliance_name FROM clan c JOIN clan_war w ON c.id = w.declaring_clan_id "
					+ "WHERE w.target_clan_id = ? AND w.declaring_clan_id NOT IN "
					+ "(SELECT target_clan_id FROM clan_war WHERE declaring_clan_id = ?)";
	/** Clans this clan is at war with in both directions. */
	private static final String SELECT_WARS_MUTUAL =
			"SELECT c.name, c.id, c.alliance_id, c.alliance_name FROM clan c JOIN clan_war w ON c.id = w.target_clan_id "
					+ "WHERE w.declaring_clan_id = ? AND w.target_clan_id IN "
					+ "(SELECT declaring_clan_id FROM clan_war WHERE target_clan_id = ?)";

	private static final String SELECT_CRESTS = "SELECT id, kind, image FROM crest";
	private static final String UPSERT_CREST =
			"INSERT INTO crest (id, kind, image) VALUES (?, ?, ?) "
					+ "ON CONFLICT (id) DO UPDATE SET kind = EXCLUDED.kind, image = EXCLUDED.image";
	private static final String DELETE_CREST = "DELETE FROM crest WHERE id = ? AND kind = ?";

	/** Which wars the list of {@link #loadWarOpponents(int, WarList)} shows. */
	public enum WarList
	{
		/** Clans the clan declared war on that did not declare war on it. */
		DECLARED(SELECT_WARS_DECLARED),
		/** Clans that declared war on the clan and that it did not declare war on. */
		RECEIVED(SELECT_WARS_RECEIVED),
		/** Clans at war with the clan in both directions. */
		MUTUAL(SELECT_WARS_MUTUAL);

		private final String _sql;

		WarList(String sql)
		{
			_sql = sql;
		}
	}

	/**
	 * A clan as it is stored. 0 means "none" for the castle, the alliance, the crests, the auction, and the moments.
	 * An alliance penalty type is the L2Clan.PENALTY_TYPE_* value, 0 for none.
	 */
	public record ClanRecord(int id, String name, int level, int castleId, int allianceId, String allianceName,
			int leaderId, int crestId, int largeCrestId, int allianceCrestId, int reputationScore, int auctionBidAt,
			long allyPenaltyExpiryTime, int allyPenaltyType, long memberPenaltyExpiryTime, long dissolveTime)
	{
	}

	/** A player that belongs to a clan. The sex is 1 for female, the apprentice and sponsor are 0 for none. */
	public record MemberRecord(int objectId, String name, int level, int classId, String title, int pledgeRank,
			int pledgeType, int apprenticeId, int sponsorId, int race, int sex)
	{
	}

	public record RankPrivilegeRecord(int rank, int privileges)
	{
	}

	public record SkillRecord(int skillId, int level)
	{
	}

	/** A sub-unit of a clan; the leader is 0 when it has none. */
	public record SubPledgeRecord(int type, String name, int leaderId)
	{
	}

	public record WarRecord(int declaringClanId, int targetClanId)
	{
	}

	/** The clan on the other side of a war, as the war list shows it; the alliance is 0 and null when it has none. */
	public record WarOpponentRecord(int clanId, String clanName, int allianceId, String allianceName)
	{
	}

	public record CrestRecord(int id, String kind, byte[] image)
	{
	}

	/** Work of a transaction that may throw an SQLException. */
	@FunctionalInterface
	public interface SqlWork
	{
		void run() throws SQLException;
	}

	private static final class SingletonHolder
	{
		private static final ClanRepository INSTANCE = new ClanRepository();
	}

	public static ClanRepository getInstance()
	{
		return SingletonHolder.INSTANCE;
	}

	private ClanRepository()
	{
	}

	/**
	 * Runs work of several operations in one transaction (joining the running one).
	 *
	 * @return true if the work was committed
	 */
	public static boolean transaction(String what, SqlWork work)
	{
		return WorldTransaction.run(what, () -> {
			try
			{
				work.run();
			}
			catch (SQLException e)
			{
				throw new IllegalStateException(what + " failed", e);
			}
		});
	}

	private static Connection connection()
	{
		return L2DatabaseFactory.getInstance().getConnection();
	}

	/** 0 is "none" for the game and NULL for the database. */
	private static void setIdOrNull(PreparedStatement statement, int index, int id) throws SQLException
	{
		if (id == 0)
			statement.setNull(index, Types.INTEGER);
		else
			statement.setInt(index, id);
	}

	/** Epoch milliseconds, 0 for "not set"; NULL for the database. */
	private static Timestamp toMoment(long millis)
	{
		return millis > 0 ? new Timestamp(millis) : null;
	}

	private static long fromMoment(Timestamp moment)
	{
		return moment == null ? 0 : moment.getTime();
	}

	private static String penaltyName(long expiryTime, int type)
	{
		if (expiryTime <= 0 || type <= 0 || type >= PENALTY_TYPES.length)
			return null;
		return PENALTY_TYPES[type];
	}

	private static int penaltyType(String name)
	{
		if (name == null)
			return 0;
		for (int i = 1; i < PENALTY_TYPES.length; i++)
		{
			if (PENALTY_TYPES[i].equals(name))
				return i;
		}
		return 0;
	}

	// ---- the clan ----

	public List<Integer> loadClanIds() throws SQLException
	{
		try (Connection con = connection(); PreparedStatement statement = con.prepareStatement(SELECT_CLAN_IDS);
				ResultSet rset = statement.executeQuery())
		{
			List<Integer> ids = new ArrayList<>();
			while (rset.next())
				ids.add(rset.getInt("id"));
			return ids;
		}
	}

	/** @return the clan, or null if there is none with this id */
	public ClanRecord loadClan(int clanId) throws SQLException
	{
		try (Connection con = connection(); PreparedStatement statement = con.prepareStatement(SELECT_CLAN))
		{
			statement.setInt(1, clanId);
			try (ResultSet rset = statement.executeQuery())
			{
				if (!rset.next())
					return null;
				return new ClanRecord(clanId, rset.getString("name"), rset.getInt("level"), rset.getInt("castle_id"),
						rset.getInt("alliance_id"), rset.getString("alliance_name"), rset.getInt("leader_player_id"),
						rset.getInt("crest_id"), rset.getInt("large_crest_id"), rset.getInt("alliance_crest_id"),
						rset.getInt("reputation_score"), rset.getInt("clan_hall_auction_id"),
						fromMoment(rset.getTimestamp("alliance_penalty_expire_at")),
						penaltyType(rset.getString("alliance_penalty_type")),
						fromMoment(rset.getTimestamp("member_penalty_expire_at")),
						fromMoment(rset.getTimestamp("dissolve_at")));
			}
		}
	}

	/**
	 * Creates a clan with its notice in one transaction. A name that another clan has already (without regard to case)
	 * makes it fail.
	 *
	 * @return true if the clan was created
	 */
	public boolean createClan(ClanRecord clan, String initialNotice)
	{
		return transaction("create clan " + clan.id(), () -> {
			try (Connection con = connection(); PreparedStatement statement = con.prepareStatement(INSERT_CLAN))
			{
				statement.setInt(1, clan.id());
				statement.setString(2, clan.name());
				statement.setInt(3, clan.level());
				statement.setInt(4, clan.leaderId());
				setIdOrNull(statement, 5, clan.crestId());
				setIdOrNull(statement, 6, clan.largeCrestId());
				setIdOrNull(statement, 7, clan.castleId());
				setIdOrNull(statement, 8, clan.allianceId());
				statement.setString(9, clan.allianceId() == 0 ? null : clan.allianceName());
				setIdOrNull(statement, 10, clan.allianceCrestId());
				statement.executeUpdate();
			}
			insertNotice(clan.id(), initialNotice, false);
		});
	}

	/**
	 * Saves what changes during the life of a clan: the leader, the alliance, the reputation, and the penalties. The
	 * alliance penalty is stored only while it runs.
	 */
	public void updateClan(ClanRecord clan) throws SQLException
	{
		try (Connection con = connection(); PreparedStatement statement = con.prepareStatement(UPDATE_CLAN))
		{
			statement.setInt(1, clan.leaderId());
			setIdOrNull(statement, 2, clan.allianceId());
			statement.setString(3, clan.allianceId() == 0 ? null : clan.allianceName());
			setIdOrNull(statement, 4, clan.allianceCrestId());
			statement.setInt(5, clan.reputationScore());
			statement.setTimestamp(6, toMoment(clan.allyPenaltyExpiryTime()));
			statement.setString(7, penaltyName(clan.allyPenaltyExpiryTime(), clan.allyPenaltyType()));
			statement.setTimestamp(8, toMoment(clan.memberPenaltyExpiryTime()));
			statement.setTimestamp(9, toMoment(clan.dissolveTime()));
			statement.setInt(10, clan.id());
			statement.executeUpdate();
		}
	}

	public void updateLevel(int clanId, int level) throws SQLException
	{
		try (Connection con = connection(); PreparedStatement statement = con.prepareStatement(UPDATE_CLAN_LEVEL))
		{
			statement.setInt(1, level);
			statement.setInt(2, clanId);
			statement.executeUpdate();
		}
	}

	/** @param auctionId the clan hall auction the clan bids at, 0 for none */
	public void updateAuctionBid(int clanId, int auctionId) throws SQLException
	{
		try (Connection con = connection(); PreparedStatement statement = con.prepareStatement(UPDATE_CLAN_AUCTION))
		{
			setIdOrNull(statement, 1, auctionId);
			statement.setInt(2, clanId);
			statement.executeUpdate();
		}
	}

	/**
	 * Deletes a clan in one transaction. The database removes what belongs to the clan (notice, rank privileges,
	 * skills, sub-units, wars in both directions, warehouse items, siege registrations, forum) and clears the links to
	 * it (the clan of its players, the alliance of its members, the owner of a fortress or a clan hall). The only
	 * work left is the copy of the alliance name and crest that member clans hold, and the tax of the castle the clan
	 * owned.
	 *
	 * @param castleId the castle the clan owns, 0 for none
	 * @return true if the clan was deleted
	 */
	public boolean deleteClan(int clanId, int castleId)
	{
		return transaction("delete clan " + clanId, () -> {
			// The foreign key clears alliance_id of the member clans, not the copies of the name and crest.
			try (Connection con = connection(); PreparedStatement statement = con.prepareStatement(CLEAR_ALLIANCE_COPIES))
			{
				statement.setInt(1, clanId);
				statement.executeUpdate();
			}
			if (castleId != 0)
			{
				try (Connection con = connection(); PreparedStatement statement = con.prepareStatement(RESET_CASTLE_TAX))
				{
					statement.setInt(1, castleId);
					statement.executeUpdate();
				}
			}
			try (Connection con = connection(); PreparedStatement statement = con.prepareStatement(DELETE_CLAN))
			{
				statement.setInt(1, clanId);
				statement.executeUpdate();
			}
		});
	}

	// ---- the members (columns of the player table) ----

	public List<MemberRecord> loadMembers(int clanId) throws SQLException
	{
		try (Connection con = connection(); PreparedStatement statement = con.prepareStatement(SELECT_MEMBERS))
		{
			statement.setInt(1, clanId);
			try (ResultSet rset = statement.executeQuery())
			{
				List<MemberRecord> members = new ArrayList<>();
				while (rset.next())
				{
					members.add(new MemberRecord(rset.getInt("id"), rset.getString("name"), rset.getInt("level"),
							rset.getInt("active_class_id"), rset.getString("title"), rset.getInt("pledge_rank"),
							rset.getInt("pledge_type"), rset.getInt("apprentice_player_id"),
							rset.getInt("sponsor_player_id"), rset.getInt("race_id"), rset.getBoolean("is_female") ? 1 : 0));
				}
				return members;
			}
		}
	}

	public void updateMemberPledgeType(int playerId, int pledgeType) throws SQLException
	{
		try (Connection con = connection(); PreparedStatement statement = con.prepareStatement(UPDATE_MEMBER_PLEDGE_TYPE))
		{
			statement.setInt(1, pledgeType);
			statement.setInt(2, playerId);
			statement.executeUpdate();
		}
	}

	public void updateMemberPledgeRank(int playerId, int pledgeRank) throws SQLException
	{
		try (Connection con = connection(); PreparedStatement statement = con.prepareStatement(UPDATE_MEMBER_PLEDGE_RANK))
		{
			statement.setInt(1, pledgeRank);
			statement.setInt(2, playerId);
			statement.executeUpdate();
		}
	}

	/** @param apprenticeId the apprentice of the player, 0 for none; the same for the sponsor */
	public void updateMemberSponsors(int playerId, int apprenticeId, int sponsorId) throws SQLException
	{
		try (Connection con = connection(); PreparedStatement statement = con.prepareStatement(UPDATE_MEMBER_SPONSORS))
		{
			setIdOrNull(statement, 1, apprenticeId);
			setIdOrNull(statement, 2, sponsorId);
			statement.setInt(3, playerId);
			statement.executeUpdate();
		}
	}

	/**
	 * Takes a player out of the clan in the database, in one transaction: the clan, the title, the sub-unit, the
	 * academy level, the personal peace, and the apprentice and sponsor links in both directions.
	 *
	 * @param joinAllowedTime moment the player may join a clan again, 0 for no penalty
	 * @param createAllowedTime moment the player may create a clan again, 0 for no penalty
	 * @return true if the player was removed
	 */
	public boolean removeMember(int playerId, long joinAllowedTime, long createAllowedTime)
	{
		return transaction("remove clan member " + playerId, () -> {
			try (Connection con = connection(); PreparedStatement statement = con.prepareStatement(REMOVE_MEMBER))
			{
				statement.setTimestamp(1, toMoment(joinAllowedTime));
				statement.setTimestamp(2, toMoment(createAllowedTime));
				statement.setInt(3, playerId);
				statement.executeUpdate();
			}
			try (Connection con = connection(); PreparedStatement statement = con.prepareStatement(CLEAR_APPRENTICE))
			{
				statement.setInt(1, playerId);
				statement.executeUpdate();
			}
			try (Connection con = connection(); PreparedStatement statement = con.prepareStatement(CLEAR_SPONSOR))
			{
				statement.setInt(1, playerId);
				statement.executeUpdate();
			}
		});
	}

	// ---- the notice ----

	/** Adds the notice of a clan unless it has one. */
	public void insertNotice(int clanId, String notice, boolean enabled) throws SQLException
	{
		try (Connection con = connection(); PreparedStatement statement = con.prepareStatement(INSERT_NOTICE))
		{
			statement.setInt(1, clanId);
			statement.setString(2, notice);
			statement.setBoolean(3, enabled);
			statement.executeUpdate();
		}
	}

	/** @return the notice, or null if the clan has none */
	public String loadNotice(int clanId) throws SQLException
	{
		try (Connection con = connection(); PreparedStatement statement = con.prepareStatement(SELECT_NOTICE))
		{
			statement.setInt(1, clanId);
			try (ResultSet rset = statement.executeQuery())
			{
				return rset.next() ? rset.getString("notice") : null;
			}
		}
	}

	public void updateNotice(int clanId, String notice) throws SQLException
	{
		try (Connection con = connection(); PreparedStatement statement = con.prepareStatement(UPDATE_NOTICE))
		{
			statement.setString(1, notice);
			statement.setInt(2, clanId);
			statement.executeUpdate();
		}
	}

	/** @return whether the notice is shown, or null if the clan has no notice */
	public Boolean loadNoticeEnabled(int clanId) throws SQLException
	{
		try (Connection con = connection(); PreparedStatement statement = con.prepareStatement(SELECT_NOTICE_ENABLED))
		{
			statement.setInt(1, clanId);
			try (ResultSet rset = statement.executeQuery())
			{
				return rset.next() ? Boolean.valueOf(rset.getBoolean("is_enabled")) : null;
			}
		}
	}

	public void updateNoticeEnabled(int clanId, boolean enabled) throws SQLException
	{
		try (Connection con = connection(); PreparedStatement statement = con.prepareStatement(UPDATE_NOTICE_ENABLED))
		{
			statement.setBoolean(1, enabled);
			statement.setInt(2, clanId);
			statement.executeUpdate();
		}
	}

	// ---- rank privileges ----

	public List<RankPrivilegeRecord> loadRankPrivileges(int clanId) throws SQLException
	{
		try (Connection con = connection(); PreparedStatement statement = con.prepareStatement(SELECT_RANK_PRIVILEGES))
		{
			statement.setInt(1, clanId);
			try (ResultSet rset = statement.executeQuery())
			{
				List<RankPrivilegeRecord> privileges = new ArrayList<>();
				while (rset.next())
					privileges.add(new RankPrivilegeRecord(rset.getInt("pledge_rank"), rset.getInt("privileges")));
				return privileges;
			}
		}
	}

	/** Sets the privileges of a rank, adding the rank if the clan has no row for it yet. */
	public void saveRankPrivileges(int clanId, int rank, int privileges) throws SQLException
	{
		try (Connection con = connection(); PreparedStatement statement = con.prepareStatement(UPSERT_RANK_PRIVILEGE))
		{
			statement.setInt(1, clanId);
			statement.setInt(2, rank);
			statement.setInt(3, privileges);
			statement.executeUpdate();
		}
	}

	// ---- skills ----

	public List<SkillRecord> loadSkills(int clanId) throws SQLException
	{
		try (Connection con = connection(); PreparedStatement statement = con.prepareStatement(SELECT_SKILLS))
		{
			statement.setInt(1, clanId);
			try (ResultSet rset = statement.executeQuery())
			{
				List<SkillRecord> skills = new ArrayList<>();
				while (rset.next())
					skills.add(new SkillRecord(rset.getInt("skill_id"), rset.getInt("skill_level")));
				return skills;
			}
		}
	}

	/** Adds a skill or raises it to the given level. */
	public void saveSkill(int clanId, int skillId, int level) throws SQLException
	{
		try (Connection con = connection(); PreparedStatement statement = con.prepareStatement(UPSERT_SKILL))
		{
			statement.setInt(1, clanId);
			statement.setInt(2, skillId);
			statement.setInt(3, level);
			statement.executeUpdate();
		}
	}

	// ---- sub-units ----

	public List<SubPledgeRecord> loadSubPledges(int clanId) throws SQLException
	{
		try (Connection con = connection(); PreparedStatement statement = con.prepareStatement(SELECT_SUBPLEDGES))
		{
			statement.setInt(1, clanId);
			try (ResultSet rset = statement.executeQuery())
			{
				List<SubPledgeRecord> subPledges = new ArrayList<>();
				while (rset.next())
				{
					subPledges.add(new SubPledgeRecord(rset.getInt("subpledge_type"), rset.getString("name"),
							rset.getInt("leader_player_id")));
				}
				return subPledges;
			}
		}
	}

	/** @param leaderId the leader of the sub-unit, 0 for none (the academy) */
	public void insertSubPledge(int clanId, int type, String name, int leaderId) throws SQLException
	{
		try (Connection con = connection(); PreparedStatement statement = con.prepareStatement(INSERT_SUBPLEDGE))
		{
			statement.setInt(1, clanId);
			statement.setInt(2, type);
			statement.setString(3, name);
			setIdOrNull(statement, 4, leaderId);
			statement.executeUpdate();
		}
	}

	/** @param leaderId the leader of the sub-unit, 0 for none */
	public void updateSubPledge(int clanId, int type, String name, int leaderId) throws SQLException
	{
		try (Connection con = connection(); PreparedStatement statement = con.prepareStatement(UPDATE_SUBPLEDGE))
		{
			setIdOrNull(statement, 1, leaderId);
			statement.setString(2, name);
			statement.setInt(3, clanId);
			statement.setInt(4, type);
			statement.executeUpdate();
		}
	}

	// ---- wars ----

	public List<WarRecord> loadWars() throws SQLException
	{
		try (Connection con = connection(); PreparedStatement statement = con.prepareStatement(SELECT_WARS);
				ResultSet rset = statement.executeQuery())
		{
			List<WarRecord> wars = new ArrayList<>();
			while (rset.next())
				wars.add(new WarRecord(rset.getInt("declaring_clan_id"), rset.getInt("target_clan_id")));
			return wars;
		}
	}

	/** Records that a clan declared war on another; a war that is already recorded stays as it is. */
	public void insertWar(int declaringClanId, int targetClanId) throws SQLException
	{
		try (Connection con = connection(); PreparedStatement statement = con.prepareStatement(INSERT_WAR))
		{
			statement.setInt(1, declaringClanId);
			statement.setInt(2, targetClanId);
			statement.executeUpdate();
		}
	}

	public void deleteWar(int declaringClanId, int targetClanId) throws SQLException
	{
		try (Connection con = connection(); PreparedStatement statement = con.prepareStatement(DELETE_WAR))
		{
			statement.setInt(1, declaringClanId);
			statement.setInt(2, targetClanId);
			statement.executeUpdate();
		}
	}

	public List<WarOpponentRecord> loadWarOpponents(int clanId, WarList list) throws SQLException
	{
		try (Connection con = connection(); PreparedStatement statement = con.prepareStatement(list._sql))
		{
			statement.setInt(1, clanId);
			statement.setInt(2, clanId);
			try (ResultSet rset = statement.executeQuery())
			{
				List<WarOpponentRecord> opponents = new ArrayList<>();
				while (rset.next())
				{
					opponents.add(new WarOpponentRecord(rset.getInt("id"), rset.getString("name"),
							rset.getInt("alliance_id"), rset.getString("alliance_name")));
				}
				return opponents;
			}
		}
	}

	// ---- crests ----

	public List<CrestRecord> loadCrests() throws SQLException
	{
		try (Connection con = connection(); PreparedStatement statement = con.prepareStatement(SELECT_CRESTS);
				ResultSet rset = statement.executeQuery())
		{
			List<CrestRecord> crests = new ArrayList<>();
			while (rset.next())
				crests.add(new CrestRecord(rset.getInt("id"), rset.getString("kind"), rset.getBytes("image")));
			return crests;
		}
	}

	/** Stores a crest image under a new id (or replaces the image of an id). */
	public void saveCrest(int id, String kind, byte[] image) throws SQLException
	{
		try (Connection con = connection(); PreparedStatement statement = con.prepareStatement(UPSERT_CREST))
		{
			statement.setInt(1, id);
			statement.setString(2, kind);
			statement.setBytes(3, image);
			statement.executeUpdate();
		}
	}

	/** @return true if there was a crest of this kind with this id */
	public boolean deleteCrest(int id, String kind) throws SQLException
	{
		try (Connection con = connection(); PreparedStatement statement = con.prepareStatement(DELETE_CREST))
		{
			statement.setInt(1, id);
			statement.setString(2, kind);
			return statement.executeUpdate() > 0;
		}
	}

	/** @param crestId the crest of the clan, 0 for none */
	public void updateClanCrest(int clanId, int crestId) throws SQLException
	{
		try (Connection con = connection(); PreparedStatement statement = con.prepareStatement(UPDATE_CLAN_CREST))
		{
			setIdOrNull(statement, 1, crestId);
			statement.setInt(2, clanId);
			statement.executeUpdate();
		}
	}

	/** @param crestId the large crest of the clan, 0 for none */
	public void updateClanLargeCrest(int clanId, int crestId) throws SQLException
	{
		try (Connection con = connection(); PreparedStatement statement = con.prepareStatement(UPDATE_CLAN_LARGE_CREST))
		{
			setIdOrNull(statement, 1, crestId);
			statement.setInt(2, clanId);
			statement.executeUpdate();
		}
	}

	/** Sets the alliance crest on every clan of the alliance. */
	public void updateAllianceCrest(int allianceId, int crestId) throws SQLException
	{
		try (Connection con = connection(); PreparedStatement statement = con.prepareStatement(UPDATE_ALLIANCE_CREST))
		{
			setIdOrNull(statement, 1, crestId);
			statement.setInt(2, allianceId);
			statement.executeUpdate();
		}
	}
}
