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
package com.l2jfree;

import static com.l2jfree.L2AutoInitialization.EXTENDED_LOG_LEVEL;
import static com.l2jfree.L2AutoInitialization.TELNET_FILE;
import static com.l2jfree.L2AutoInitialization.registerConfig;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.L2AutoInitialization.ConfigFileLoader;
import com.l2jfree.L2AutoInitialization.ConfigLoader;
import com.l2jfree.L2AutoInitialization.ConfigPropertiesLoader;
import com.l2jfree.config.Deployment;
import com.l2jfree.config.L2Properties;
import com.l2jfree.gameserver.datatables.SkillTable;
import com.l2jfree.gameserver.model.Location;
import com.l2jfree.gameserver.model.entity.events.AutomatedTvT;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.util.Util;
import com.l2jfree.lang.management.StartupManager;
import com.l2jfree.lang.management.StartupManager.StartupHook;
import com.l2jfree.util.L2FastSet;

/**
 * This class contains global server configuration.<br>
 * It has static final fields initialized from configuration files.<br>
 * It's initialized at the very begin of startup, and later JIT will optimize
 * away debug/unused code.
 */
public final class Config
{
	private static final Logger _log = LoggerFactory.getLogger(Config.class);
	
	static
	{
		System.setProperty("python.home", ".");
		
		registerConfig(new AltConfig());
		registerConfig(new AutoEventConfig());
		registerConfig(new BossConfig());
		registerConfig(new CastleConfig());
		registerConfig(new ChampionsConfig());
		registerConfig(new ClanHallConfig());
		registerConfig(new ClansConfig());
		registerConfig(new CommandPrivilegesConfig());
		registerConfig(new ElayneConfig());
		registerConfig(new EnchantConfig());
		registerConfig(new FortSiegeConfig());
		registerConfig(new FunEnginesConfig());
		registerConfig(new GMAccessConfig());
		registerConfig(new GeoConfig());
		registerConfig(new LotteryConfig());
		registerConfig(new OptionsConfig());
		registerConfig(new OtherConfig());
		registerConfig(new PvPConfig());
		registerConfig(new RatesConfig());
		registerConfig(new SayFilterConfig());
		registerConfig(new SevenSignsConfig());
		registerConfig(new SiegeConfig());
		registerConfig(new VitalityConfig());
		registerConfig(new WeddingConfig());
		registerConfig(new CustomConfig());
	}
	
	// *******************************************************************************************
	public static final String CONFIGURATION_FILE = "./config/server.properties";
	// *******************************************************************************************
	public static String INTERNAL_HOSTNAME;
	public static String INTERNAL_NETWORKS;
	public static String EXTERNAL_HOSTNAME;
	public static String OPTIONAL_NETWORKS;
	public static boolean PACKET_HANDLER_DEBUG;
	public static int SERVER_ID; // Number the client's server list shows for this world
	public static long IP_UPDATE_TIME; // How often the host names of the world are resolved again, in ms; 0 resolves them once
	public static int ACCEPT_WARN = 10; // Connections from one address in the short period above which the server warns
	public static int ACCEPT_REJECT = 20; // ... above which it refuses
	public static int ACCEPT_SECONDS = 10; // The short period
	public static int ACCEPT_WARN_LONG = 30; // The same for the long period
	public static int ACCEPT_REJECT_LONG = 60;
	public static int ACCEPT_SECONDS_LONG = 60;
	public static Pattern CNAME_PATTERN; // Character name template
	public static Pattern PET_NAME_PATTERN; // Pet name template
	public static Pattern CLAN_ALLY_NAME_PATTERN; // Clan and ally name template
	public static Pattern TITLE_PATTERN; // Clan title template
	public static int MAX_CHARACTERS_NUMBER_PER_ACCOUNT; // Maximum number of characters per account
	public static int PORT_GAME; // Game Server ports
	public static String GAMESERVER_HOSTNAME; // Hostname of the Game Server
	public static String DATABASE_DRIVER; // Driver to access to database
	public static String DATABASE_URL; // Path to access to database
	public static String DATABASE_LOGIN; // Database login
	public static String DATABASE_PASSWORD; // Database password
	public static String CATALOG_DIRECTORY; // Directory of the catalog CSV files, loaded into the catalog schema at start
	public static int DATABASE_MAX_CONNECTIONS; // Maximum number of connections to the
	// database
	public static int DATABASE_MIN_IDLE_CONNECTIONS; // Idle connections kept open, never above the maximum
	public static int MAXIMUM_ONLINE_USERS; // Maximum number of players allowed to play
	// simultaneously on server
	public static boolean SAFE_REBOOT = false; // Safe mode will disable some feature
	// during restart/shutdown to prevent
	// exploit
	public static int SAFE_REBOOT_TIME = 10;
	public static boolean SAFE_REBOOT_DISABLE_ENCHANT = false;
	public static boolean SAFE_REBOOT_DISABLE_TELEPORT = false;
	public static boolean SAFE_REBOOT_DISABLE_CREATEITEM = false;
	public static boolean SAFE_REBOOT_DISABLE_TRANSACTION = false;
	public static boolean SAFE_REBOOT_DISABLE_PC_ITERACTION = false;
	public static boolean SAFE_REBOOT_DISABLE_NPC_ITERACTION = false;
	public static int MIN_PROTOCOL_REVISION; // protocol revision
	public static int MAX_PROTOCOL_REVISION;
	public static File DATAPACK_ROOT; // Datapack root directory
	public static boolean PACKET_FINAL;
	public static boolean STRICT_FINAL;
	public static boolean CONNECTION_FILTERING;
	// not to be loaded from file
	public static boolean DISABLE_ALL_CHAT = false;
	
	// *******************************************************************************************
	public static void loadConfiguration()
	{
		_log.info("loading " + CONFIGURATION_FILE);
		try
		{
			L2Properties serverSettings = new L2Properties(CONFIGURATION_FILE);
			
			SERVER_ID = Integer.parseInt(serverSettings.getProperty("ServerId", "1"));
			IP_UPDATE_TIME = Long.parseLong(serverSettings.getProperty("IpUpdateTime", "0")) * 60 * 1000;
			ACCEPT_WARN = Integer.parseInt(serverSettings.getProperty("AcceptWarn", "10"));
			ACCEPT_REJECT = Integer.parseInt(serverSettings.getProperty("AcceptReject", "20"));
			ACCEPT_SECONDS = Integer.parseInt(serverSettings.getProperty("AcceptSeconds", "10"));
			ACCEPT_WARN_LONG = Integer.parseInt(serverSettings.getProperty("AcceptWarnLong", "30"));
			ACCEPT_REJECT_LONG = Integer.parseInt(serverSettings.getProperty("AcceptRejectLong", "60"));
			ACCEPT_SECONDS_LONG = Integer.parseInt(serverSettings.getProperty("AcceptSecondsLong", "60"));
			PORT_GAME = Integer.parseInt(serverSettings.getProperty("GameserverPort", "7777"));
			try
			{
				CNAME_PATTERN = Pattern.compile(serverSettings.getProperty("CnameTemplate", "[A-Za-z0-9\\-]{2,16}"));
			}
			catch (PatternSyntaxException e)
			{
				_log.warn("Character name pattern is wrong!", e);
				CNAME_PATTERN = Pattern.compile("[A-Za-z0-9\\-]{2,16}");
			}
			
			try
			{
				PET_NAME_PATTERN =
						Pattern.compile(serverSettings.getProperty("PetNameTemplate", "[A-Za-z0-9\\-]{2,16}"));
			}
			catch (PatternSyntaxException e)
			{
				_log.warn("Pet name pattern is wrong!", e);
				PET_NAME_PATTERN = Pattern.compile("[A-Za-z0-9\\-]{2,16}");
			}
			
			try
			{
				CLAN_ALLY_NAME_PATTERN =
						Pattern.compile(serverSettings.getProperty("ClanAllyNameTemplate", "[A-Za-z0-9 \\-]{3,16}"));
			}
			catch (PatternSyntaxException e)
			{
				_log.warn("Clan and ally name pattern is wrong!", e);
				CLAN_ALLY_NAME_PATTERN = Pattern.compile("[A-Za-z0-9 \\-]{3,16}");
			}
			
			try
			{
				TITLE_PATTERN =
						Pattern.compile(serverSettings.getProperty("TitleTemplate",
								"[A-Za-z0-9 \\\\[\\\\]\\(\\)\\<\\>\\|\\!]{3,16}"));
			}
			catch (PatternSyntaxException e)
			{
				_log.warn("Character title pattern is wrong!", e);
				TITLE_PATTERN = Pattern.compile("[A-Za-z0-9 \\\\[\\\\]\\(\\)\\<\\>\\|\\!]{3,16}");
			}
			MAX_CHARACTERS_NUMBER_PER_ACCOUNT = Integer.parseInt(serverSettings.getProperty("CharMaxNumber", "0"));
			GAMESERVER_HOSTNAME = Deployment.value("BIND", serverSettings.getProperty("GameserverHostname"));
			DATAPACK_ROOT = new File(serverSettings.getProperty("DatapackRoot", ".")).getCanonicalFile();
			MIN_PROTOCOL_REVISION = Integer.parseInt(serverSettings.getProperty("MinProtocolRevision", "694"));
			MAX_PROTOCOL_REVISION = Integer.parseInt(serverSettings.getProperty("MaxProtocolRevision", "709"));
			if (MIN_PROTOCOL_REVISION > MAX_PROTOCOL_REVISION)
			{
				throw new Error("MinProtocolRevision is bigger than MaxProtocolRevision in server configuration file.");
			}
			INTERNAL_HOSTNAME = serverSettings.getProperty("InternalHostname", "127.0.0.1");
			INTERNAL_NETWORKS = serverSettings.getProperty("InternalNetworks", "");
			EXTERNAL_HOSTNAME = Deployment.value("EXTERNAL_HOST", serverSettings.getProperty("ExternalHostname", "127.0.0.1"));
			OPTIONAL_NETWORKS = serverSettings.getProperty("OptionalNetworks", "");
			PACKET_HANDLER_DEBUG = Boolean.parseBoolean(serverSettings.getProperty("PacketHandlerDebug", "false"));
			MAXIMUM_ONLINE_USERS = Integer.parseInt(serverSettings.getProperty("MaximumOnlineUsers", "100"));
			DATABASE_DRIVER = serverSettings.getProperty("Driver", "org.postgresql.Driver");
			DATABASE_URL = Deployment.value("DB_URL", serverSettings.getProperty("URL", "jdbc:postgresql://localhost/l2jfree"));
			DATABASE_LOGIN = Deployment.value("WORLD_DB_USER", serverSettings.getProperty("Login", "l2jfree_world"));
			DATABASE_PASSWORD = Deployment.secret("WORLD_DB_PASSWORD", serverSettings.getProperty("Password", ""));
			CATALOG_DIRECTORY = serverSettings.getProperty("CatalogDirectory", "catalog");
			DATABASE_MAX_CONNECTIONS = Integer.parseInt(serverSettings.getProperty("MaximumDbConnections", "10"));
			DATABASE_MIN_IDLE_CONNECTIONS = Integer.parseInt(serverSettings.getProperty("MinimumDbIdleConnections", "10"));
			
			SAFE_REBOOT = Boolean.parseBoolean(serverSettings.getProperty("SafeReboot", "False"));
			SAFE_REBOOT_TIME = Integer.parseInt(serverSettings.getProperty("SafeRebootTime", "10"));
			SAFE_REBOOT_DISABLE_ENCHANT =
					Boolean.parseBoolean(serverSettings.getProperty("SafeRebootDisableEnchant", "False"));
			SAFE_REBOOT_DISABLE_TELEPORT =
					Boolean.parseBoolean(serverSettings.getProperty("SafeRebootDisableTeleport", "False"));
			SAFE_REBOOT_DISABLE_CREATEITEM =
					Boolean.parseBoolean(serverSettings.getProperty("SafeRebootDisableCreateItem", "False"));
			SAFE_REBOOT_DISABLE_TRANSACTION =
					Boolean.parseBoolean(serverSettings.getProperty("SafeRebootDisableTransaction", "False"));
			SAFE_REBOOT_DISABLE_PC_ITERACTION =
					Boolean.parseBoolean(serverSettings.getProperty("SafeRebootDisablePcIteraction", "False"));
			SAFE_REBOOT_DISABLE_NPC_ITERACTION =
					Boolean.parseBoolean(serverSettings.getProperty("SafeRebootDisableNpcIteraction", "False"));
			PACKET_FINAL = Boolean.parseBoolean(serverSettings.getProperty("PacketFinal", "True"));
			STRICT_FINAL = Boolean.parseBoolean(serverSettings.getProperty("StrictFinal", "False"));
			CONNECTION_FILTERING = Boolean.parseBoolean(serverSettings.getProperty("ConnectionFiltering", "True"));
			
			EXTENDED_LOG_LEVEL = Level.parse(serverSettings.getProperty("ExtendedLogLevel", "OFF"));
		}
		catch (Exception e)
		{
			_log.error(e.getMessage(), e);
			throw new Error("Failed to Load " + CONFIGURATION_FILE + " File.");
		}
	}
	
	// *******************************************************************************************
	public static final String GEO_FILE = "./config/geodata.properties";
	// *******************************************************************************************
	public static int GEODATA;
	public static boolean GEODATA_CELLFINDING;
	public static boolean FORCE_GEODATA;
	
	public static enum CorrectSpawnsZ
	{
		TOWN,
		MONSTER,
		ALL,
		NONE
	}
	
	public static CorrectSpawnsZ GEO_CORRECT_Z; // Enable spawns' z-correction
	public static boolean ACCEPT_GEOEDITOR_CONN; // Accept connection from geodata editor
	public static boolean DEGUG_DOOR_GEODATA;
	
	// *******************************************************************************************
	private static final class GeoConfig extends ConfigPropertiesLoader
	{
		@Override
		protected String getName()
		{
			return "geodata";
		}
		
		@Override
		protected void loadImpl(L2Properties geoSettings)
		{
			GEODATA = Integer.parseInt(geoSettings.getProperty("GeoData", "0"));
			GEODATA_CELLFINDING = Boolean.parseBoolean(geoSettings.getProperty("CellPathFinding", "False"));
			FORCE_GEODATA = Boolean.parseBoolean(geoSettings.getProperty("ForceGeoData", "True"));
			String correctZ = geoSettings.getProperty("GeoCorrectZ", "ALL");
			GEO_CORRECT_Z = CorrectSpawnsZ.valueOf(correctZ.toUpperCase());
			ACCEPT_GEOEDITOR_CONN = Boolean.parseBoolean(geoSettings.getProperty("AcceptGeoeditorConn", "False"));
			DEGUG_DOOR_GEODATA = Boolean.parseBoolean(geoSettings.getProperty("DebugDoorGeodata", "False"));
		}
	}
	
	// *******************************************************************************************
	public static final String CLANS_FILE = "./config/clans.properties";
	// *******************************************************************************************
	public static int ALT_CLAN_MEMBERS_FOR_WAR; // Number of members needed to request a clan war
	public static int ALT_CLAN_JOIN_DAYS; // Number of days before joining a new clan
	public static int ALT_CLAN_CREATE_DAYS; // Number of days before creating a new clan
	public static int ALT_CLAN_DISSOLVE_DAYS; // Number of days it takes to dissolve a clan
	public static int ALT_ALLY_JOIN_DAYS_WHEN_LEAVED; // Number of days before joining a new alliance when clan voluntarily leave an alliance
	public static int ALT_ALLY_JOIN_DAYS_WHEN_DISMISSED; // Number of days before joining a new alliance when clan was dismissed from an
	// alliance
	public static int ALT_ACCEPT_CLAN_DAYS_WHEN_DISMISSED; // Number of days before accepting a new clan for alliance when clan was dismissed
	// from an alliance
	public static int ALT_CREATE_ALLY_DAYS_WHEN_DISSOLVED; // Number of days before creating a new alliance when dissolved an alliance
	public static int ALT_MAX_NUM_OF_CLANS_IN_ALLY; // Maximum number of clans in ally
	
	public static enum ClanLeaderColored
	{
		name,
		title
	} // Clan leader name color
	
	public static boolean CLAN_LEADER_COLOR_ENABLED;
	public static ClanLeaderColored CLAN_LEADER_COLORED;
	public static int CLAN_LEADER_COLOR;
	public static int CLAN_LEADER_COLOR_CLAN_LEVEL;
	public static int MEMBER_FOR_LEVEL_SIX; // Number of members to level up a clan to lvl 6
	public static int MEMBER_FOR_LEVEL_SEVEN; // Number of members to level up a clan to lvl 7
	public static int MEMBER_FOR_LEVEL_EIGHT; // Number of members to level up a clan to lvl 8
	public static int MEMBER_FOR_LEVEL_NINE; // Number of members to level up a clan to lvl 9
	public static int MEMBER_FOR_LEVEL_TEN; // Number of members to level up a clan to lvl 10
	public static int MEMBER_FOR_LEVEL_ELEVEN; // Number of members to level up a clan to lvl 11
	
	// Clan Fame
	public static int TAKE_FORT_POINTS;
	public static int LOOSE_FORT_POINTS;
	public static int TAKE_CASTLE_POINTS;
	public static int LOOSE_CASTLE_POINTS;
	public static int CASTLE_DEFENDED_POINTS;
	public static int FESTIVAL_WIN_POINTS;
	public static int HERO_POINTS;
	public static int ROYAL_GUARD_COST;
	public static int KNIGHT_UNIT_COST;
	public static int KNIGHT_REINFORCE_COST;
	public static int BALLISTA_POINTS;
	public static int BLOODALLIANCE_POINTS;
	public static int BLOODOATH_POINTS;
	public static int KNIGHTSEPAULETTE_POINTS;
	public static int REPUTATION_SCORE_PER_KILL;
	public static int JOIN_ACADEMY_MIN_REP_SCORE;
	public static int JOIN_ACADEMY_MAX_REP_SCORE;
	public static int RAID_RANKING_1ST;
	public static int RAID_RANKING_2ND;
	public static int RAID_RANKING_3RD;
	public static int RAID_RANKING_4TH;
	public static int RAID_RANKING_5TH;
	public static int RAID_RANKING_6TH;
	public static int RAID_RANKING_7TH;
	public static int RAID_RANKING_8TH;
	public static int RAID_RANKING_9TH;
	public static int RAID_RANKING_10TH;
	public static int RAID_RANKING_UP_TO_50TH;
	public static int RAID_RANKING_UP_TO_100TH;
	public static int CLAN_LEVEL_6_COST;
	public static int CLAN_LEVEL_7_COST;
	public static int CLAN_LEVEL_8_COST;
	public static int CLAN_LEVEL_9_COST;
	public static int CLAN_LEVEL_10_COST;
	public static int CLAN_LEVEL_11_COST;
	
	// *******************************************************************************************
	private static final class ClansConfig extends ConfigPropertiesLoader
	{
		@Override
		protected String getName()
		{
			return "clans";
		}
		
		@Override
		protected void loadImpl(L2Properties clansSettings)
		{
			ALT_CLAN_MEMBERS_FOR_WAR = Integer.parseInt(clansSettings.getProperty("AltClanMembersForWar", "15"));
			ALT_CLAN_JOIN_DAYS = Integer.parseInt(clansSettings.getProperty("DaysBeforeJoinAClan", "5"));
			ALT_CLAN_CREATE_DAYS = Integer.parseInt(clansSettings.getProperty("DaysBeforeCreateAClan", "10"));
			ALT_CLAN_DISSOLVE_DAYS = Integer.parseInt(clansSettings.getProperty("DaysToPassToDissolveAClan", "7"));
			ALT_ALLY_JOIN_DAYS_WHEN_LEAVED =
					Integer.parseInt(clansSettings.getProperty("DaysBeforeJoinAllyWhenLeaved", "1"));
			ALT_ALLY_JOIN_DAYS_WHEN_DISMISSED =
					Integer.parseInt(clansSettings.getProperty("DaysBeforeJoinAllyWhenDismissed", "1"));
			ALT_ACCEPT_CLAN_DAYS_WHEN_DISMISSED =
					Integer.parseInt(clansSettings.getProperty("DaysBeforeAcceptNewClanWhenDismissed", "1"));
			ALT_CREATE_ALLY_DAYS_WHEN_DISSOLVED =
					Integer.parseInt(clansSettings.getProperty("DaysBeforeCreateNewAllyWhenDissolved", "1"));
			ALT_MAX_NUM_OF_CLANS_IN_ALLY = Integer.parseInt(clansSettings.getProperty("AltMaxNumOfClansInAlly", "3"));
			CLAN_LEADER_COLOR_ENABLED =
					Boolean.parseBoolean(clansSettings.getProperty("ClanLeaderNameColorEnabled", "True"));
			CLAN_LEADER_COLORED = ClanLeaderColored.valueOf(clansSettings.getProperty("ClanLeaderColored", "name"));
			CLAN_LEADER_COLOR = Integer.decode("0x" + clansSettings.getProperty("ClanLeaderColor", "00FFFF"));
			CLAN_LEADER_COLOR_CLAN_LEVEL =
					Integer.parseInt(clansSettings.getProperty("ClanLeaderColorAtClanLevel", "1"));
			MEMBER_FOR_LEVEL_SIX = Integer.parseInt(clansSettings.getProperty("MemberForLevel6", "30"));
			MEMBER_FOR_LEVEL_SEVEN = Integer.parseInt(clansSettings.getProperty("MemberForLevel7", "80"));
			MEMBER_FOR_LEVEL_EIGHT = Integer.parseInt(clansSettings.getProperty("MemberForLevel8", "120"));
			MEMBER_FOR_LEVEL_NINE = Integer.parseInt(clansSettings.getProperty("MemberForLevel9", "120"));
			MEMBER_FOR_LEVEL_TEN = Integer.parseInt(clansSettings.getProperty("MemberForLevel10", "140"));
			MEMBER_FOR_LEVEL_ELEVEN = Integer.parseInt(clansSettings.getProperty("MemberForLevel11", "170"));
			
			// Clan Fame
			TAKE_FORT_POINTS = Integer.parseInt(clansSettings.getProperty("TakeFortPoints", "200"));
			LOOSE_FORT_POINTS = Integer.parseInt(clansSettings.getProperty("LooseFortPoints", "400"));
			TAKE_CASTLE_POINTS = Integer.parseInt(clansSettings.getProperty("TakeCastlePoints", "1500"));
			LOOSE_CASTLE_POINTS = Integer.parseInt(clansSettings.getProperty("LooseCastlePoints", "3000"));
			CASTLE_DEFENDED_POINTS = Integer.parseInt(clansSettings.getProperty("CastleDefendedPoints", "750"));
			FESTIVAL_WIN_POINTS = Integer.parseInt(clansSettings.getProperty("FestivalOfDarknessWin", "200"));
			HERO_POINTS = Integer.parseInt(clansSettings.getProperty("HeroPoints", "1000"));
			ROYAL_GUARD_COST = Integer.parseInt(clansSettings.getProperty("CreateRoyalGuardCost", "5000"));
			KNIGHT_UNIT_COST = Integer.parseInt(clansSettings.getProperty("CreateKnightUnitCost", "10000"));
			KNIGHT_REINFORCE_COST = Integer.parseInt(clansSettings.getProperty("ReinforceKnightUnitCost", "5000"));
			BALLISTA_POINTS = Integer.parseInt(clansSettings.getProperty("KillBallistaPoints", "30"));
			BLOODALLIANCE_POINTS = Integer.parseInt(clansSettings.getProperty("BloodAlliancePoints", "500"));
			BLOODOATH_POINTS = Integer.parseInt(clansSettings.getProperty("BloodOathPoints", "200"));
			KNIGHTSEPAULETTE_POINTS = Integer.parseInt(clansSettings.getProperty("KnightsEpaulettePoints", "20"));
			REPUTATION_SCORE_PER_KILL = Integer.parseInt(clansSettings.getProperty("ReputationScorePerKill", "1"));
			JOIN_ACADEMY_MIN_REP_SCORE = Integer.parseInt(clansSettings.getProperty("CompleteAcademyMinPoints", "190"));
			JOIN_ACADEMY_MAX_REP_SCORE = Integer.parseInt(clansSettings.getProperty("CompleteAcademyMaxPoints", "650"));
			RAID_RANKING_1ST = Integer.parseInt(clansSettings.getProperty("1stRaidRankingPoints", "1250"));
			RAID_RANKING_2ND = Integer.parseInt(clansSettings.getProperty("2ndRaidRankingPoints", "900"));
			RAID_RANKING_3RD = Integer.parseInt(clansSettings.getProperty("3rdRaidRankingPoints", "700"));
			RAID_RANKING_4TH = Integer.parseInt(clansSettings.getProperty("4thRaidRankingPoints", "600"));
			RAID_RANKING_5TH = Integer.parseInt(clansSettings.getProperty("5thRaidRankingPoints", "450"));
			RAID_RANKING_6TH = Integer.parseInt(clansSettings.getProperty("6thRaidRankingPoints", "350"));
			RAID_RANKING_7TH = Integer.parseInt(clansSettings.getProperty("7thRaidRankingPoints", "300"));
			RAID_RANKING_8TH = Integer.parseInt(clansSettings.getProperty("8thRaidRankingPoints", "200"));
			RAID_RANKING_9TH = Integer.parseInt(clansSettings.getProperty("9thRaidRankingPoints", "150"));
			RAID_RANKING_10TH = Integer.parseInt(clansSettings.getProperty("10thRaidRankingPoints", "100"));
			RAID_RANKING_UP_TO_50TH = Integer.parseInt(clansSettings.getProperty("UpTo50thRaidRankingPoints", "25"));
			RAID_RANKING_UP_TO_100TH = Integer.parseInt(clansSettings.getProperty("UpTo100thRaidRankingPoints", "12"));
			CLAN_LEVEL_6_COST = Integer.parseInt(clansSettings.getProperty("ClanLevel6Cost", "10000"));
			CLAN_LEVEL_7_COST = Integer.parseInt(clansSettings.getProperty("ClanLevel7Cost", "20000"));
			CLAN_LEVEL_8_COST = Integer.parseInt(clansSettings.getProperty("ClanLevel8Cost", "40000"));
			CLAN_LEVEL_9_COST = Integer.parseInt(clansSettings.getProperty("ClanLevel9Cost", "40000"));
			CLAN_LEVEL_10_COST = Integer.parseInt(clansSettings.getProperty("ClanLevel10Cost", "40000"));
			CLAN_LEVEL_11_COST = Integer.parseInt(clansSettings.getProperty("ClanLevel11Cost", "75000"));
		}
	}
	
	// *******************************************************************************************
	public static final String CHAMPIONS_FILE = "./config/champions.properties";
	// *******************************************************************************************
	public static int CHAMPION_FREQUENCY; // Frequency of spawn
	public static boolean CHAMPION_PASSIVE;
	public static String CHAMPION_TITLE;
	public static int CHAMPION_HP; // Hp multiplier
	public static float CHAMPION_HP_REGEN; // Hp.reg multiplier
	public static float CHAMPION_ATK; // P.Atk & M.Atk multiplier
	public static float CHAMPION_SPD_ATK; // Attack speed multiplier
	public static int CHAMPION_SEALSTONE;
	public static int CHAMPION_ADENA; // Adena/Sealstone reward multiplier
	public static int CHAMPION_REWARDS; // Drop/Spoil reward multiplier
	public static int CHAMPION_EXP_SP; // Exp/Sp reward multiplier
	public static boolean CHAMPION_BOSS; // Bosses can be champions
	public static int CHAMPION_MIN_LEVEL; // Champion Minimum Level
	public static int CHAMPION_MAX_LEVEL; // Champion Maximum Level
	public static boolean CHAMPION_MINIONS; // set Minions to champions when leader champion
	public static int CHAMPION_SPCL_LVL_DIFF; // level diff with mob level is more this value - don't drop an special reward
	// item.
	public static int CHAMPION_SPCL_CHANCE; // Chance in % to drop an special reward item.
	public static int CHAMPION_SPCL_ITEM; // Item ID that drops from Champs.
	public static int CHAMPION_SPCL_QTY; // Amount of special champ drop items.
	
	// *******************************************************************************************
	private static final class ChampionsConfig extends ConfigPropertiesLoader
	{
		@Override
		protected String getName()
		{
			return "champions";
		}
		
		@Override
		protected void loadImpl(L2Properties championsSettings)
		{
			CHAMPION_FREQUENCY = Integer.parseInt(championsSettings.getProperty("ChampionFrequency", "0"));
			CHAMPION_PASSIVE = Boolean.parseBoolean(championsSettings.getProperty("ChampionPassive", "false"));
			CHAMPION_TITLE = championsSettings.getProperty("ChampionTitle", "Champion").trim();
			CHAMPION_HP = Integer.parseInt(championsSettings.getProperty("ChampionHp", "7"));
			CHAMPION_HP_REGEN = Float.parseFloat(championsSettings.getProperty("ChampionHpRegen", "1."));
			CHAMPION_REWARDS = Integer.parseInt(championsSettings.getProperty("ChampionRewards", "8"));
			CHAMPION_SEALSTONE = Integer.parseInt(championsSettings.getProperty("ChampionSealStoneRewards", "1"));
			CHAMPION_ADENA = Integer.parseInt(championsSettings.getProperty("ChampionAdenasRewards", "1"));
			CHAMPION_ATK = Float.parseFloat(championsSettings.getProperty("ChampionAtk", "1."));
			CHAMPION_SPD_ATK = Float.parseFloat(championsSettings.getProperty("ChampionSpdAtk", "1."));
			CHAMPION_EXP_SP = Integer.parseInt(championsSettings.getProperty("ChampionExpSp", "8"));
			CHAMPION_BOSS = Boolean.parseBoolean(championsSettings.getProperty("ChampionBoss", "false"));
			CHAMPION_MIN_LEVEL = Integer.parseInt(championsSettings.getProperty("ChampionMinLevel", "20"));
			CHAMPION_MAX_LEVEL = Integer.parseInt(championsSettings.getProperty("ChampionMaxLevel", "60"));
			CHAMPION_MINIONS = Boolean.parseBoolean(championsSettings.getProperty("ChampionMinions", "false"));
			CHAMPION_SPCL_LVL_DIFF =
					Integer.parseInt(championsSettings.getProperty("ChampionSpecialItemLevelDiff", "0"));
			CHAMPION_SPCL_CHANCE = Integer.parseInt(championsSettings.getProperty("ChampionSpecialItemChance", "0"));
			CHAMPION_SPCL_ITEM = Integer.parseInt(championsSettings.getProperty("ChampionSpecialItemID", "6393"));
			CHAMPION_SPCL_QTY = Integer.parseInt(championsSettings.getProperty("ChampionSpecialItemAmount", "1"));
		}
	}
	
	// *******************************************************************************************
	public static final String LOTTERY_FILE = "./config/lottery.properties";
	// *******************************************************************************************
	public static long ALT_LOTTERY_PRIZE; // Initial Lottery prize
	public static long ALT_LOTTERY_TICKET_PRICE; // Lottery Ticket Price
	public static float ALT_LOTTERY_5_NUMBER_RATE; // What part of jackpot amount should receive characters who pick 5 wining
	// numbers
	public static float ALT_LOTTERY_4_NUMBER_RATE; // What part of jackpot amount should receive characters who pick 4 wining
	// numbers
	public static float ALT_LOTTERY_3_NUMBER_RATE; // What part of jackpot amount should receive characters who pick 3 wining
	// numbers
	public static long ALT_LOTTERY_2_AND_1_NUMBER_PRIZE; // How much adena receive characters who pick two or less of the winning
	
	// number
	
	// *******************************************************************************************
	private static final class LotteryConfig extends ConfigPropertiesLoader
	{
		@Override
		protected String getName()
		{
			return "lottery";
		}
		
		@Override
		protected void loadImpl(L2Properties lotterySettings)
		{
			ALT_LOTTERY_PRIZE = Long.parseLong(lotterySettings.getProperty("AltLotteryPrize", "50000"));
			ALT_LOTTERY_TICKET_PRICE = Long.parseLong(lotterySettings.getProperty("AltLotteryTicketPrice", "2000"));
			ALT_LOTTERY_5_NUMBER_RATE = Float.parseFloat(lotterySettings.getProperty("AltLottery5NumberRate", "0.6"));
			ALT_LOTTERY_4_NUMBER_RATE = Float.parseFloat(lotterySettings.getProperty("AltLottery4NumberRate", "0.2"));
			ALT_LOTTERY_3_NUMBER_RATE = Float.parseFloat(lotterySettings.getProperty("AltLottery3NumberRate", "0.2"));
			ALT_LOTTERY_2_AND_1_NUMBER_PRIZE =
					Long.parseLong(lotterySettings.getProperty("AltLottery2and1NumberPrize", "200"));
		}
	}
	
	// *******************************************************************************************
	public static final String WEDDING_FILE = "./config/wedding.properties";
	// *******************************************************************************************
	public static int WEDDING_PRICE;
	public static boolean WEDDING_PUNISH_INFIDELITY;
	public static boolean WEDDING_TELEPORT;
	public static int WEDDING_TELEPORT_PRICE;
	public static int WEDDING_TELEPORT_INTERVAL;
	public static boolean WEDDING_SAMESEX;
	public static boolean WEDDING_FORMALWEAR;
	public static boolean WEDDING_GIVE_CUPID_BOW;
	public static boolean WEDDING_HONEYMOON_PORT;
	public static int WEDDING_DIVORCE_COSTS;
	
	// *******************************************************************************************
	private static final class WeddingConfig extends ConfigPropertiesLoader
	{
		@Override
		protected String getName()
		{
			return "wedding";
		}
		
		@Override
		protected void loadImpl(L2Properties weddingSettings)
		{
			WEDDING_PRICE = Integer.parseInt(weddingSettings.getProperty("WeddingPrice", "500000"));
			WEDDING_PUNISH_INFIDELITY =
					Boolean.parseBoolean(weddingSettings.getProperty("WeddingPunishInfidelity", "true"));
			WEDDING_TELEPORT = Boolean.parseBoolean(weddingSettings.getProperty("WeddingTeleport", "true"));
			WEDDING_TELEPORT_PRICE = Integer.parseInt(weddingSettings.getProperty("WeddingTeleportPrice", "500000"));
			WEDDING_TELEPORT_INTERVAL = Integer.parseInt(weddingSettings.getProperty("WeddingTeleportInterval", "120"));
			WEDDING_SAMESEX = Boolean.parseBoolean(weddingSettings.getProperty("WeddingAllowSameSex", "true"));
			WEDDING_FORMALWEAR = Boolean.parseBoolean(weddingSettings.getProperty("WeddingFormalWear", "true"));
			WEDDING_DIVORCE_COSTS = Integer.parseInt(weddingSettings.getProperty("WeddingDivorceCosts", "20"));
			WEDDING_GIVE_CUPID_BOW = Boolean.parseBoolean(weddingSettings.getProperty("WeddingGiveBow", "true"));
			WEDDING_HONEYMOON_PORT = Boolean.parseBoolean(weddingSettings.getProperty("WeddingHoneyMoon", "false"));
		}
	}
	
	// *******************************************************************************************
	public static final String RATES_CONFIG_FILE = "./config/rates.properties";
	// *******************************************************************************************
	public static float RATE_XP;
	public static float RATE_SP;
	public static float RATE_PARTY_XP;
	public static float RATE_PARTY_SP;
	public static float RATE_QUESTS_REWARD_EXPSP;
	public static float RATE_QUESTS_REWARD_ADENA;
	public static float RATE_QUESTS_REWARD_ITEMS;
	public static float RATE_DROP_SEALSTONE;
	public static float RATE_DROP_ADENA;
	public static float RATE_DROP_ADENA_RAID;
	public static float RATE_DROP_ADENA_GRAND_BOSS;
	public static float RATE_CONSUMABLE_COST;
	public static float RATE_CRAFT_COST;
	public static int RATE_MASTERWORK;
	public static int RATE_CRITICAL_CRAFT_CHANCE;
	public static int RATE_CRITICAL_CRAFT_MULTIPLIER;
	public static float RATE_DROP_ITEMS;
	public static float RATE_DROP_ITEMS_RAID;
	public static float RATE_DROP_ITEMS_GRAND_BOSS;
	public static float RATE_DROP_ITEMS_JEWEL;
	public static float RATE_DROP_SPOIL;
	public static float RATE_DROP_SPOIL_RAID;
	public static float RATE_DROP_SPOIL_GRAND_BOSS;
	public static int RATE_DROP_MANOR;
	public static float RATE_DROP_QUEST;
	public static int RATE_EXTR_FISH;
	public static float RATE_KARMA_EXP_LOST;
	public static float RATE_SIEGE_GUARDS_PRICE;
	public static float RATE_RUN_SPEED;
	public static float ALT_GAME_EXPONENT_XP; // Alternative Xp/Sp rewards, if not 0, then calculated as
	// 2^((mob.level-player.level) / coef
	public static float ALT_GAME_EXPONENT_SP;
	public static int PLAYER_DROP_LIMIT;
	public static int PLAYER_RATE_DROP;
	public static int PLAYER_RATE_DROP_ITEM;
	public static int PLAYER_RATE_DROP_EQUIP;
	public static int PLAYER_RATE_DROP_EQUIP_WEAPON;
	public static float PET_XP_RATE;
	public static float PET_FOOD_RATE;
	public static float SINEATER_XP_RATE;
	public static float RATE_DROP_COMMON_HERBS;
	public static float RATE_DROP_MP_HP_HERBS;
	public static float RATE_DROP_GREATER_HERBS;
	public static float RATE_DROP_SUPERIOR_HERBS;
	public static float RATE_DROP_SPECIAL_HERBS;
	public static int KARMA_DROP_LIMIT;
	public static int KARMA_RATE_DROP;
	public static int KARMA_RATE_DROP_ITEM;
	public static int KARMA_RATE_DROP_EQUIP;
	public static int KARMA_RATE_DROP_EQUIP_WEAPON;
	public static double[] PLAYER_XP_PERCENT_LOST;
	
	public static boolean COMPENSATE_QUEST_ITEM_REWARDS;
	
	// *******************************************************************************************
	private static final class RatesConfig extends ConfigPropertiesLoader
	{
		@Override
		protected String getName()
		{
			return "rates";
		}
		
		@Override
		protected void loadImpl(L2Properties ratesSettings)
		{
			RATE_XP = Float.parseFloat(ratesSettings.getProperty("RateXp", "1."));
			RATE_SP = Float.parseFloat(ratesSettings.getProperty("RateSp", "1."));
			RATE_PARTY_XP = Float.parseFloat(ratesSettings.getProperty("RatePartyXp", "1."));
			RATE_PARTY_SP = Float.parseFloat(ratesSettings.getProperty("RatePartySp", "1."));
			
			RATE_QUESTS_REWARD_EXPSP = Float.parseFloat(ratesSettings.getProperty("RateQuestsRewardExpSp", "1."));
			RATE_QUESTS_REWARD_ADENA = Float.parseFloat(ratesSettings.getProperty("RateQuestsRewardAdena", "1."));
			RATE_QUESTS_REWARD_ITEMS = Float.parseFloat(ratesSettings.getProperty("RateQuestsRewardItems", "1."));
			
			RATE_DROP_SEALSTONE = Float.parseFloat(ratesSettings.getProperty("RateDropSealStone", "1."));
			RATE_DROP_ADENA = Float.parseFloat(ratesSettings.getProperty("RateDropAdena", "1."));
			RATE_DROP_ADENA_RAID = Float.parseFloat(ratesSettings.getProperty("RateDropAdenaRaid", "1."));
			RATE_DROP_ADENA_GRAND_BOSS = Float.parseFloat(ratesSettings.getProperty("RateDropAdenaGrandBoss", "1."));
			RATE_CONSUMABLE_COST = Float.parseFloat(ratesSettings.getProperty("RateConsumableCost", "1."));
			RATE_CRAFT_COST = Float.parseFloat(ratesSettings.getProperty("RateCraftCost", "1."));
			RATE_MASTERWORK = Integer.parseInt(ratesSettings.getProperty("RateMasterwork", "20"));
			RATE_CRITICAL_CRAFT_CHANCE = Integer.parseInt(ratesSettings.getProperty("RateCriticalCraftChance", "5"));
			RATE_CRITICAL_CRAFT_MULTIPLIER =
					Integer.parseInt(ratesSettings.getProperty("RateCriticalCraftMutliplier", "2"));
			RATE_DROP_ITEMS = Float.parseFloat(ratesSettings.getProperty("RateDropItems", "1."));
			RATE_DROP_ITEMS_RAID = Float.parseFloat(ratesSettings.getProperty("RateDropItemsRaid", "1."));
			RATE_DROP_ITEMS_GRAND_BOSS = Float.parseFloat(ratesSettings.getProperty("RateDropItemsGrandBoss", "1."));
			RATE_DROP_ITEMS_JEWEL = Float.parseFloat(ratesSettings.getProperty("RateDropItemsJewel", "1."));
			RATE_DROP_SPOIL = Float.parseFloat(ratesSettings.getProperty("RateDropSpoil", "1."));
			RATE_DROP_SPOIL_RAID = Float.parseFloat(ratesSettings.getProperty("RateDropSpoilRaid", "1."));
			RATE_DROP_SPOIL_GRAND_BOSS = Float.parseFloat(ratesSettings.getProperty("RateDropSpoilGrandBoss", "1."));
			RATE_DROP_MANOR = Integer.parseInt(ratesSettings.getProperty("RateDropManor", "1"));
			RATE_DROP_QUEST = Float.parseFloat(ratesSettings.getProperty("RateDropQuest", "1."));
			RATE_EXTR_FISH = Integer.parseInt(ratesSettings.getProperty("RateExtractFish", "1"));
			RATE_RUN_SPEED = Float.parseFloat(ratesSettings.getProperty("RateRunSpeed", "1."));
			RATE_KARMA_EXP_LOST = Float.parseFloat(ratesSettings.getProperty("RateKarmaExpLost", "1."));
			RATE_SIEGE_GUARDS_PRICE = Float.parseFloat(ratesSettings.getProperty("RateSiegeGuardsPrice", "1."));
			
			RATE_DROP_COMMON_HERBS = Float.parseFloat(ratesSettings.getProperty("RateCommonHerbs", "15."));
			RATE_DROP_MP_HP_HERBS = Float.parseFloat(ratesSettings.getProperty("RateHpMpHerbs", "10."));
			RATE_DROP_GREATER_HERBS = Float.parseFloat(ratesSettings.getProperty("RateGreaterHerbs", "4."));
			RATE_DROP_SUPERIOR_HERBS = Float.parseFloat(ratesSettings.getProperty("RateSuperiorHerbs", "0.8")) * 10;
			RATE_DROP_SPECIAL_HERBS = Float.parseFloat(ratesSettings.getProperty("RateSpecialHerbs", "0.2")) * 10;
			
			PLAYER_DROP_LIMIT = Integer.parseInt(ratesSettings.getProperty("PlayerDropLimit", "3"));
			PLAYER_RATE_DROP = Integer.parseInt(ratesSettings.getProperty("PlayerRateDrop", "5"));
			PLAYER_RATE_DROP_ITEM = Integer.parseInt(ratesSettings.getProperty("PlayerRateDropItem", "70"));
			PLAYER_RATE_DROP_EQUIP = Integer.parseInt(ratesSettings.getProperty("PlayerRateDropEquip", "25"));
			PLAYER_RATE_DROP_EQUIP_WEAPON =
					Integer.parseInt(ratesSettings.getProperty("PlayerRateDropEquipWeapon", "5"));
			
			PET_XP_RATE = Float.parseFloat(ratesSettings.getProperty("PetXpRate", "1."));
			PET_FOOD_RATE = Float.parseFloat(ratesSettings.getProperty("PetFoodRate", "1"));
			SINEATER_XP_RATE = Float.parseFloat(ratesSettings.getProperty("SinEaterXpRate", "1."));
			
			KARMA_DROP_LIMIT = Integer.parseInt(ratesSettings.getProperty("KarmaDropLimit", "10"));
			KARMA_RATE_DROP = Integer.parseInt(ratesSettings.getProperty("KarmaRateDrop", "70"));
			KARMA_RATE_DROP_ITEM = Integer.parseInt(ratesSettings.getProperty("KarmaRateDropItem", "50"));
			KARMA_RATE_DROP_EQUIP = Integer.parseInt(ratesSettings.getProperty("KarmaRateDropEquip", "40"));
			KARMA_RATE_DROP_EQUIP_WEAPON =
					Integer.parseInt(ratesSettings.getProperty("KarmaRateDropEquipWeapon", "10"));
			
			// Initializing table
			PLAYER_XP_PERCENT_LOST = new double[Byte.MAX_VALUE + 1];
			
			// Default value
			for (int i = 0; i <= Byte.MAX_VALUE; i++)
				PLAYER_XP_PERCENT_LOST[i] = 1.;
			
			// Now loading into table parsed values
			try
			{
				String[] values =
						ratesSettings.getProperty("PlayerXPPercentLost",
								"0,39-7.0;40,75-4.0;76,76-2.5;77,77-2.0;78,78-1.5").split(";");
				
				for (String s : values)
				{
					int min;
					int max;
					double val;
					
					String[] vals = s.split("-");
					String[] mM = vals[0].split(",");
					
					min = Integer.parseInt(mM[0]);
					max = Integer.parseInt(mM[1]);
					val = Double.parseDouble(vals[1]);
					
					for (int i = min; i <= max; i++)
						PLAYER_XP_PERCENT_LOST[i] = val;
				}
			}
			catch (Exception e)
			{
				_log.warn("Error while loading Player XP percent lost");
				e.printStackTrace();
			}
			
			COMPENSATE_QUEST_ITEM_REWARDS =
					Boolean.parseBoolean(ratesSettings.getProperty("AdenaInsteadOfMoreItems", "False"));
		}
	}
	
	// *******************************************************************************************
	public static final String ENCHANT_CONFIG_FILE = "./config/enchant.properties";
	// *******************************************************************************************
	public static int ENCHANT_CHANCE_WEAPON;
	public static int ENCHANT_CHANCE_ARMOR;
	public static int ENCHANT_CHANCE_JEWELRY;
	public static int ENCHANT_CHANCE_WEAPON_CRYSTAL;
	public static int ENCHANT_CHANCE_ARMOR_CRYSTAL;
	public static int ENCHANT_CHANCE_JEWELRY_CRYSTAL;
	public static int BLESSED_ENCHANT_CHANCE_WEAPON;
	public static int BLESSED_ENCHANT_CHANCE_ARMOR;
	public static int BLESSED_ENCHANT_CHANCE_JEWELRY;
	public static int ENCHANT_CHANCE_ELEMENT;
	public static boolean ALLOW_CRYSTAL_SCROLL;
	public static boolean ENCHANT_HERO_WEAPONS; // Enchant hero weapons?
	public static boolean ENCHANT_DWARF_SYSTEM; // Dwarf enchant System?
	public static int ENCHANT_MAX_WEAPON; // Maximum level of enchantment
	public static int ENCHANT_MAX_ARMOR;
	public static int ENCHANT_MAX_JEWELRY;
	public static int ENCHANT_SAFE_MAX; // maximum level of safe enchantment
	public static int ENCHANT_SAFE_MAX_FULL;
	public static int ENCHANT_DWARF_1_ENCHANTLEVEL; // Dwarf enchant System Dwarf 1 Enchantlevel?
	public static int ENCHANT_DWARF_2_ENCHANTLEVEL; // Dwarf enchant System Dwarf 2 Enchantlevel?
	public static int ENCHANT_DWARF_3_ENCHANTLEVEL; // Dwarf enchant System Dwarf 3 Enchantlevel?
	public static int ENCHANT_DWARF_1_CHANCE; // Dwarf enchant System Dwarf 1 chance?
	public static int ENCHANT_DWARF_2_CHANCE; // Dwarf enchant System Dwarf 2 chance?
	public static int ENCHANT_DWARF_3_CHANCE; // Dwarf enchant System Dwarf 3 chance?
	
	public static boolean AUGMENT_EXCLUDE_NOTDONE;
	public static int AUGMENTATION_NG_SKILL_CHANCE; // Chance to get a skill while using a NoGrade Life Stone
	public static int AUGMENTATION_NG_GLOW_CHANCE; // Chance to get a Glow effect while using a NoGrade Life Stone(only if you get a skill)
	public static int AUGMENTATION_MID_SKILL_CHANCE; // Chance to get a skill while using a MidGrade Life Stone
	public static int AUGMENTATION_MID_GLOW_CHANCE; // Chance to get a Glow effect while using a MidGrade Life Stone(only if you get a skill)
	public static int AUGMENTATION_HIGH_SKILL_CHANCE; // Chance to get a skill while using a HighGrade Life Stone
	public static int AUGMENTATION_HIGH_GLOW_CHANCE; // Chance to get a Glow effect while using a HighGrade Life Stone
	public static int AUGMENTATION_TOP_SKILL_CHANCE; // Chance to get a skill while using a TopGrade Life Stone
	public static int AUGMENTATION_TOP_GLOW_CHANCE; // Chance to get a Glow effect while using a TopGrade Life Stone
	public static int AUGMENTATION_BASESTAT_CHANCE; // Chance to get a BaseStatModifier in the augmentation process
	public static int AUGMENTATION_ACC_SKILL_CHANCE;
	public static int[] AUGMENTATION_BLACKLIST;
	
	// *******************************************************************************************
	private static final class EnchantConfig extends ConfigPropertiesLoader
	{
		@Override
		protected String getName()
		{
			return "enchant";
		}
		
		@Override
		protected void loadImpl(L2Properties enchantSettings)
		{
			/* chance to enchant an item normal scroll */
			ENCHANT_CHANCE_WEAPON = Integer.parseInt(enchantSettings.getProperty("EnchantChanceWeapon", "65"));
			ENCHANT_CHANCE_ARMOR = Integer.parseInt(enchantSettings.getProperty("EnchantChanceArmor", "65"));
			ENCHANT_CHANCE_JEWELRY = Integer.parseInt(enchantSettings.getProperty("EnchantChanceJewelry", "65"));
			/* chance to enchant an item crystal scroll */
			ALLOW_CRYSTAL_SCROLL = Boolean.parseBoolean(enchantSettings.getProperty("AllowCrystalScroll", "False"));
			ENCHANT_CHANCE_WEAPON_CRYSTAL =
					Integer.parseInt(enchantSettings.getProperty("EnchantChanceWeaponCrystal", "75"));
			ENCHANT_CHANCE_ARMOR_CRYSTAL =
					Integer.parseInt(enchantSettings.getProperty("EnchantChanceArmorCrystal", "75"));
			ENCHANT_CHANCE_JEWELRY_CRYSTAL =
					Integer.parseInt(enchantSettings.getProperty("EnchantChanceJewelryCrystal", "75"));
			/* chance to enchant an item blessed scroll */
			BLESSED_ENCHANT_CHANCE_WEAPON =
					Integer.parseInt(enchantSettings.getProperty("EnchantChanceWeaponBlessed", "65"));
			BLESSED_ENCHANT_CHANCE_ARMOR =
					Integer.parseInt(enchantSettings.getProperty("EnchantChanceArmorBlessed", "65"));
			BLESSED_ENCHANT_CHANCE_JEWELRY =
					Integer.parseInt(enchantSettings.getProperty("EnchantChanceJewelryBlessed", "65"));
			/* chance to enchant an item with elemental attribute */
			ENCHANT_CHANCE_ELEMENT = Integer.parseInt(enchantSettings.getProperty("EnchantChanceElement", "50"));
			/* enchat hero weapons? */
			ENCHANT_HERO_WEAPONS = Boolean.parseBoolean(enchantSettings.getProperty("EnchantHeroWeapons", "False"));
			/* enchant dwarf system */
			ENCHANT_DWARF_SYSTEM = Boolean.parseBoolean(enchantSettings.getProperty("EnchantDwarfSystem", "False"));
			/* limit on enchant */
			ENCHANT_MAX_WEAPON = Integer.parseInt(enchantSettings.getProperty("EnchantMaxWeapon", "255"));
			ENCHANT_MAX_ARMOR = Integer.parseInt(enchantSettings.getProperty("EnchantMaxArmor", "255"));
			ENCHANT_MAX_JEWELRY = Integer.parseInt(enchantSettings.getProperty("EnchantMaxJewelry", "255"));
			/* limit of safe enchant */
			ENCHANT_SAFE_MAX = Integer.parseInt(enchantSettings.getProperty("EnchantSafeMax", "3"));
			ENCHANT_SAFE_MAX_FULL = Integer.parseInt(enchantSettings.getProperty("EnchantSafeMaxFull", "4"));
			ENCHANT_DWARF_1_ENCHANTLEVEL =
					Integer.parseInt(enchantSettings.getProperty("EnchantDwarf1Enchantlevel", "8"));
			ENCHANT_DWARF_2_ENCHANTLEVEL =
					Integer.parseInt(enchantSettings.getProperty("EnchantDwarf2Enchantlevel", "10"));
			ENCHANT_DWARF_3_ENCHANTLEVEL =
					Integer.parseInt(enchantSettings.getProperty("EnchantDwarf3Enchantlevel", "12"));
			ENCHANT_DWARF_1_CHANCE = Integer.parseInt(enchantSettings.getProperty("EnchantDwarf1Chance", "15"));
			ENCHANT_DWARF_2_CHANCE = Integer.parseInt(enchantSettings.getProperty("EnchantDwarf2Chance", "15"));
			ENCHANT_DWARF_3_CHANCE = Integer.parseInt(enchantSettings.getProperty("EnchantDwarf3Chance", "15"));
			
			AUGMENT_EXCLUDE_NOTDONE =
					Boolean.parseBoolean(enchantSettings.getProperty("AugmentExcludeNotdone", "False"));
			AUGMENTATION_NG_SKILL_CHANCE =
					Integer.parseInt(enchantSettings.getProperty("AugmentationNGSkillChance", "15"));
			AUGMENTATION_NG_GLOW_CHANCE =
					Integer.parseInt(enchantSettings.getProperty("AugmentationNGGlowChance", "0"));
			AUGMENTATION_MID_SKILL_CHANCE =
					Integer.parseInt(enchantSettings.getProperty("AugmentationMidSkillChance", "30"));
			AUGMENTATION_MID_GLOW_CHANCE =
					Integer.parseInt(enchantSettings.getProperty("AugmentationMidGlowChance", "40"));
			AUGMENTATION_HIGH_SKILL_CHANCE =
					Integer.parseInt(enchantSettings.getProperty("AugmentationHighSkillChance", "45"));
			AUGMENTATION_HIGH_GLOW_CHANCE =
					Integer.parseInt(enchantSettings.getProperty("AugmentationHighGlowChance", "70"));
			AUGMENTATION_TOP_SKILL_CHANCE =
					Integer.parseInt(enchantSettings.getProperty("AugmentationTopSkillChance", "60"));
			AUGMENTATION_TOP_GLOW_CHANCE =
					Integer.parseInt(enchantSettings.getProperty("AugmentationTopGlowChance", "100"));
			AUGMENTATION_BASESTAT_CHANCE =
					Integer.parseInt(enchantSettings.getProperty("AugmentationBaseStatChance", "1"));
			AUGMENTATION_ACC_SKILL_CHANCE =
					Integer.parseInt(enchantSettings.getProperty("AugmentationAccSkillChance", "0"));
			StringTokenizer st =
					new StringTokenizer(enchantSettings.getProperty("AugmentationBlackList",
							"6656,6657,6658,6659,6660,6661,6662,8191,10170,10314"), ",");
			AUGMENTATION_BLACKLIST = new int[st.countTokens()];
			for (int i = 0; st.hasMoreTokens(); i++)
				AUGMENTATION_BLACKLIST[i] = Integer.parseInt(st.nextToken());
		}
	}
	
	// *******************************************************************************************
	public static final String PVP_CONFIG_FILE = "./config/pvp.properties";
	// *******************************************************************************************
	public static int KARMA_MIN_KARMA;
	public static int KARMA_MAX_KARMA;
	public static float KARMA_RATE;
	public static int KARMA_XP_DIVIDER;
	public static int KARMA_LOST_BASE;
	public static boolean KARMA_DROP_GM;
	public static boolean KARMA_AWARD_PK_KILL;
	public static int KARMA_PK_LIMIT;
	public static int[] KARMA_LIST_NONDROPPABLE_PET_ITEMS;
	public static int[] KARMA_LIST_NONDROPPABLE_ITEMS;
	public static int PVP_TIME;
	public static boolean ALT_PLAYER_CAN_DROP_ADENA; // Player can drop adena ?
	public static boolean ALT_ANNOUNCE_PK; // Announce Pks ?
	public static boolean ALT_ANNOUNCE_PK_NORMAL_MESSAGE;
	public static int PLAYER_RATE_DROP_ADENA;
	public static int PVP_NORMAL_TIME; // Duration (in ms) while a player stay in PVP mode
	// after hitting an innocent
	public static int PVP_PVP_TIME; // Duration (in ms) while a player stay in PVP mode
	// after hitting a purple player
	public static boolean CURSED_WEAPON_NPC_INTERACT;
	
	// *******************************************************************************************
	private static final class PvPConfig extends ConfigPropertiesLoader
	{
		@Override
		protected String getName()
		{
			return "pvp";
		}
		
		@Override
		protected void loadImpl(L2Properties pvpSettings)
		{
			/* KARMA SYSTEM */
			KARMA_MIN_KARMA = Integer.parseInt(pvpSettings.getProperty("MinKarma", "240"));
			KARMA_MAX_KARMA = Integer.parseInt(pvpSettings.getProperty("MaxKarma", "10000"));
			KARMA_RATE = Float.parseFloat(pvpSettings.getProperty("KarmaRate", "1."));
			KARMA_XP_DIVIDER = Integer.parseInt(pvpSettings.getProperty("XPDivider", "260"));
			KARMA_LOST_BASE = Integer.parseInt(pvpSettings.getProperty("BaseKarmaLost", "0"));
			
			KARMA_DROP_GM = Boolean.parseBoolean(pvpSettings.getProperty("CanGMDropEquipment", "false"));
			KARMA_AWARD_PK_KILL = Boolean.parseBoolean(pvpSettings.getProperty("AwardPKKillPVPPoint", "true"));
			
			KARMA_PK_LIMIT = Integer.parseInt(pvpSettings.getProperty("MinimumPKRequiredToDrop", "5"));
			
			String KARMA_NONDROPPABLE_PET_ITEMS =
					pvpSettings.getProperty("ListOfPetItems",
							"2375,3500,3501,3502,4422,4423,4424,4425,6648,6649,6650,9882");
			String KARMA_NONDROPPABLE_ITEMS =
					pvpSettings.getProperty("ListOfNonDroppableItems", "57,1147,425,1146,461,10,2368,7,6,2370,2369");
			
			String[] ids = KARMA_NONDROPPABLE_PET_ITEMS.trim().split(",");
			KARMA_LIST_NONDROPPABLE_PET_ITEMS = new int[ids.length];
			for (int i = 0; i < ids.length; i++)
			{
				KARMA_LIST_NONDROPPABLE_PET_ITEMS[i] = Integer.parseInt(ids[i].trim());
			}
			
			ids = KARMA_NONDROPPABLE_ITEMS.trim().split(",");
			KARMA_LIST_NONDROPPABLE_ITEMS = new int[ids.length];
			for (int i = 0; i < ids.length; i++)
			{
				KARMA_LIST_NONDROPPABLE_ITEMS[i] = Integer.parseInt(ids[i].trim());
			}
			
			// sorting so binarySearch can be used later
			Arrays.sort(KARMA_LIST_NONDROPPABLE_PET_ITEMS);
			Arrays.sort(KARMA_LIST_NONDROPPABLE_ITEMS);
			
			ALT_PLAYER_CAN_DROP_ADENA = Boolean.parseBoolean(pvpSettings.getProperty("PlayerCanDropAdena", "false"));
			PLAYER_RATE_DROP_ADENA = Integer.parseInt(pvpSettings.getProperty("PlayerRateDropAdena", "1"));
			ALT_ANNOUNCE_PK = Boolean.parseBoolean(pvpSettings.getProperty("AnnouncePk", "false"));
			ALT_ANNOUNCE_PK_NORMAL_MESSAGE =
					Boolean.parseBoolean(pvpSettings.getProperty("AnnouncePkNormalMessage", "false"));
			PVP_NORMAL_TIME = Integer.parseInt(pvpSettings.getProperty("PvPVsNormalTime", "120000"));
			PVP_PVP_TIME = Integer.parseInt(pvpSettings.getProperty("PvPVsPvPTime", "60000"));
			PVP_TIME = PVP_NORMAL_TIME;
			CURSED_WEAPON_NPC_INTERACT =
					Boolean.parseBoolean(pvpSettings.getProperty("CursedWeaponNpcInteract", "false"));
		}
	}
	
	// *******************************************************************************************
	public static final String ID_CONFIG_FILE = "./config/idfactory.properties";
	
	// *******************************************************************************************
	public static enum IdFactoryType
	{
		BitSet,
		Stack,
		Increment
	}
	
	public static IdFactoryType IDFACTORY_TYPE; // ID Factory type
	public static boolean BAD_ID_CHECKING; // Check for bad ID ?
	
	// *******************************************************************************************
	public static void loadIdFactoryConfig()
	{
		_log.info("loading " + ID_CONFIG_FILE);
		try
		{
			L2Properties idSettings = new L2Properties(ID_CONFIG_FILE);
			
			IDFACTORY_TYPE = IdFactoryType.valueOf(idSettings.getProperty("IDFactory", "BitSet"));
			BAD_ID_CHECKING = Boolean.parseBoolean(idSettings.getProperty("BadIdChecking", "True"));
		}
		catch (Exception e)
		{
			_log.error(String.valueOf(e));
			throw new Error("Failed to Load " + ID_CONFIG_FILE + " File.");
		}
	}
	
	// *******************************************************************************************
	public static final String OTHER_CONFIG_FILE = "./config/other.properties";
	// *******************************************************************************************
	public static boolean JAIL_IS_PVP; // Jail config
	public static boolean JAIL_DISABLE_CHAT; // Jail config
	public static int GREAT_WOLF_MOUNT_LEVEL;
	public static boolean ALLOW_WYVERN_UPGRADER;
	public static boolean PETITIONING_ALLOWED;
	public static int MAX_PETITIONS_PER_PLAYER;
	public static int MAX_PETITIONS_PENDING;
	public static boolean STORE_EFFECTS; // Store skills cooltime on char exit/relogin
	public static boolean STORE_EFFECTS_ON_SUBCLASS_CHANGE;
	public static int SEND_NOTDONE_SKILLS;
	public static boolean ANNOUNCE_MAMMON_SPAWN;
	public static double RESPAWN_RESTORE_CP; // Percent CP is restore on respawn
	public static double RESPAWN_RESTORE_HP; // Percent HP is restore on respawn
	public static double RESPAWN_RESTORE_MP; // Percent MP is restore on respawn
	public static boolean RESPAWN_RANDOM_ENABLED; // Allow randomizing of the respawn point in towns.
	public static int RESPAWN_RANDOM_MAX_OFFSET; // The maximum offset from the base respawn point to allow.
	
	// Slot limits for private store
	public static int MAX_PVTSTORESELL_SLOTS_DWARF;
	public static int MAX_PVTSTORESELL_SLOTS_OTHER;
	public static int MAX_PVTSTOREBUY_SLOTS_DWARF;
	public static int MAX_PVTSTOREBUY_SLOTS_OTHER;
	
	public static String PARTY_XP_CUTOFF_METHOD; // Define Party XP cutoff point method - Possible values: level and
	// percentage
	public static int PARTY_XP_CUTOFF_LEVEL; // Define the cutoff point value for the "level" method
	public static double PARTY_XP_CUTOFF_PERCENT; // Define the cutoff point value for the "percentage" method
	public static int MAX_PARTY_LEVEL_DIFFERENCE; // Maximum level difference between party members in levels
	public static double RAID_HP_REGEN_MULTIPLIER; // Multiplier for Raid boss HP regeneration
	public static double RAID_MP_REGEN_MULTIPLIER; // Mulitplier for Raid boss MP regeneration
	public static double RAID_PDEFENCE_MULTIPLIER; // Multiplier for Raid boss power defense multiplier
	public static double RAID_MDEFENCE_MULTIPLIER; // Multiplier for Raid boss magic defense multiplier
	public static double RAID_MINION_RESPAWN_TIMER; // Raid Boss Minin Spawn Timer
	public static float RAID_MIN_RESPAWN_MULTIPLIER; // Mulitplier for Raid boss minimum time respawn
	public static float RAID_MAX_RESPAWN_MULTIPLIER; // Mulitplier for Raid boss maximum time respawn
	public static long STARTING_ADENA; // Amount of adenas when starting a new character
	public static byte STARTING_LEVEL;
	public static int STARTING_SP;
	public static boolean DEEPBLUE_DROP_RULES; // Deep Blue Mobs' Drop Rules Enabled
	public static boolean DEEPBLUE_DROP_RULES_RAID; // Deep Blue Mobs' Drop Rules Enabled
	public static int UNSTUCK_INTERVAL;
	public static int TELEPORT_WATCHDOG_TIMEOUT;
	public static int PLAYER_SPAWN_PROTECTION; // Player Protection control
	public static int PLAYER_FAKEDEATH_UP_PROTECTION;
	public static double NPC_HP_REGEN_MULTIPLIER; // NPC regen multipliers
	public static double NPC_MP_REGEN_MULTIPLIER; // NPC regen multipliers
	public static double PLAYER_HP_REGEN_MULTIPLIER; // Player regen multipliers
	public static double PLAYER_MP_REGEN_MULTIPLIER; // Player regen multipliers
	public static double PLAYER_CP_REGEN_MULTIPLIER; // Player regen multipliers
	public static int MAX_ITEM_IN_PACKET;
	
	/**
	 * Allow lesser effects to be canceled if stronger effects are used when
	 * effects of the same stack group are used.<br>
	 * New effects that are added will be canceled if they are of lesser
	 * priority to the old one.
	 */
	public static boolean EFFECT_CANCELING;
	public static String NONDROPPABLE_ITEMS;
	public static String PET_RENT_NPC;
	public static final Set<Integer> LIST_PET_RENT_NPC = new L2FastSet<Integer>();
	public static boolean LEVEL_ADD_LOAD;
	public static int WAREHOUSE_SLOTS_NO_DWARF; // Warehouse slots limits
	public static int WAREHOUSE_SLOTS_DWARF; // Warehouse slots limits
	public static int WAREHOUSE_SLOTS_CLAN; // Warehouse slots limits
	public static int FREIGHT_SLOTS; // Freight slots limits
	public static int INVENTORY_MAXIMUM_NO_DWARF; // Inventory slots limits
	public static int INVENTORY_MAXIMUM_DWARF; // Inventory slots limits
	public static int INVENTORY_MAXIMUM_GM; // Inventory slots limits
	public static int DEATH_PENALTY_CHANCE; // Death Penalty chance
	public static boolean BANKING_SYSTEM_ENABLED;
	public static int BANKING_SYSTEM_GOLDBARS;
	public static int BANKING_SYSTEM_ADENA;
	
	// *******************************************************************************************
	// *******************************************************************************************
	private static final class OtherConfig extends ConfigPropertiesLoader
	{
		@Override
		protected String getName()
		{
			return "other";
		}
		
		@Override
		protected void loadImpl(L2Properties otherSettings)
		{
			DEEPBLUE_DROP_RULES = Boolean.parseBoolean(otherSettings.getProperty("UseDeepBlueDropRules", "True"));
			DEEPBLUE_DROP_RULES_RAID =
					Boolean.parseBoolean(otherSettings.getProperty("UseDeepBlueDropRulesRaid", "True"));
			EFFECT_CANCELING = Boolean.parseBoolean(otherSettings.getProperty("CancelLesserEffect", "True"));
			
			GREAT_WOLF_MOUNT_LEVEL = Integer.parseInt(otherSettings.getProperty("GreatWolfMountLevel", "70"));
			
			ALLOW_WYVERN_UPGRADER = Boolean.parseBoolean(otherSettings.getProperty("AllowWyvernUpgrader", "False"));
			
			/* Inventory slots limits */
			INVENTORY_MAXIMUM_NO_DWARF = Integer.parseInt(otherSettings.getProperty("MaximumSlotsForNoDwarf", "80"));
			INVENTORY_MAXIMUM_DWARF = Integer.parseInt(otherSettings.getProperty("MaximumSlotsForDwarf", "100"));
			INVENTORY_MAXIMUM_GM = Integer.parseInt(otherSettings.getProperty("MaximumSlotsForGMPlayer", "250"));
			
			/* Config weight limit */
			LEVEL_ADD_LOAD = Boolean.parseBoolean(otherSettings.getProperty("IncreaseWeightLimitByLevel", "false"));
			
			/* Inventory slots limits */
			WAREHOUSE_SLOTS_NO_DWARF =
					Integer.parseInt(otherSettings.getProperty("MaximumWarehouseSlotsForNoDwarf", "100"));
			WAREHOUSE_SLOTS_DWARF = Integer.parseInt(otherSettings.getProperty("MaximumWarehouseSlotsForDwarf", "120"));
			WAREHOUSE_SLOTS_CLAN = Integer.parseInt(otherSettings.getProperty("MaximumWarehouseSlotsForClan", "150"));
			FREIGHT_SLOTS = Integer.parseInt(otherSettings.getProperty("MaximumFreightSlots", "20"));
			MAX_ITEM_IN_PACKET =
					Math.max(INVENTORY_MAXIMUM_NO_DWARF, Math.max(INVENTORY_MAXIMUM_DWARF, INVENTORY_MAXIMUM_GM));
			
			/* if different from 100 (ie 100%) heal rate is modified acordingly */
			NPC_HP_REGEN_MULTIPLIER =
					Double.parseDouble(otherSettings.getProperty("NPCHpRegenMultiplier", "100")) / 100;
			NPC_MP_REGEN_MULTIPLIER =
					Double.parseDouble(otherSettings.getProperty("NPCMpRegenMultiplier", "100")) / 100;
			
			PLAYER_HP_REGEN_MULTIPLIER =
					Double.parseDouble(otherSettings.getProperty("PlayerHpRegenMultiplier", "100")) / 100;
			PLAYER_MP_REGEN_MULTIPLIER =
					Double.parseDouble(otherSettings.getProperty("PlayerMpRegenMultiplier", "100")) / 100;
			PLAYER_CP_REGEN_MULTIPLIER =
					Double.parseDouble(otherSettings.getProperty("PlayerCpRegenMultiplier", "100")) / 100;
			
			RAID_HP_REGEN_MULTIPLIER =
					Double.parseDouble(otherSettings.getProperty("RaidHpRegenMultiplier", "100")) / 100;
			RAID_MP_REGEN_MULTIPLIER =
					Double.parseDouble(otherSettings.getProperty("RaidMpRegenMultiplier", "100")) / 100;
			RAID_PDEFENCE_MULTIPLIER =
					Double.parseDouble(otherSettings.getProperty("RaidPDefenceMultiplier", "100")) / 100;
			RAID_MDEFENCE_MULTIPLIER =
					Double.parseDouble(otherSettings.getProperty("RaidMDefenceMultiplier", "100")) / 100;
			RAID_MINION_RESPAWN_TIMER = Integer.parseInt(otherSettings.getProperty("RaidMinionRespawnTime", "300000"));
			
			RAID_MIN_RESPAWN_MULTIPLIER =
					Float.parseFloat(otherSettings.getProperty("RaidMinRespawnMultiplier", "1.0"));
			RAID_MAX_RESPAWN_MULTIPLIER =
					Float.parseFloat(otherSettings.getProperty("RaidMaxRespawnMultiplier", "1.0"));
			
			STARTING_ADENA = Long.parseLong(otherSettings.getProperty("StartingAdena", "100"));
			STARTING_LEVEL = Byte.parseByte(otherSettings.getProperty("StartingLevel", "1"));
			STARTING_SP = Integer.parseInt(otherSettings.getProperty("StartingSP", "0"));
			UNSTUCK_INTERVAL = Integer.parseInt(otherSettings.getProperty("UnstuckInterval", "300"));
			TELEPORT_WATCHDOG_TIMEOUT = Integer.parseInt(otherSettings.getProperty("TeleportWatchdogTimeout", "0"));
			
			/* Player protection after teleport or login */
			PLAYER_SPAWN_PROTECTION = Integer.parseInt(otherSettings.getProperty("PlayerSpawnProtection", "0"));
			
			/* Player protection after recovering from fake death (works against mobs only) */
			PLAYER_FAKEDEATH_UP_PROTECTION =
					Integer.parseInt(otherSettings.getProperty("PlayerFakeDeathUpProtection", "0"));
			
			/* Defines some Party XP related values */
			PARTY_XP_CUTOFF_METHOD = otherSettings.getProperty("PartyXpCutoffMethod", "percentage");
			PARTY_XP_CUTOFF_PERCENT = Double.parseDouble(otherSettings.getProperty("PartyXpCutoffPercent", "3."));
			PARTY_XP_CUTOFF_LEVEL = Integer.parseInt(otherSettings.getProperty("PartyXpCutoffLevel", "30"));
			MAX_PARTY_LEVEL_DIFFERENCE = Integer.parseInt(otherSettings.getProperty("PartyMaxLevelDifference", "20"));
			
			/* Amount of HP, MP, and CP is restored */
			RESPAWN_RESTORE_CP = Double.parseDouble(otherSettings.getProperty("RespawnRestoreCP", "0")) / 100;
			RESPAWN_RESTORE_HP = Double.parseDouble(otherSettings.getProperty("RespawnRestoreHP", "70")) / 100;
			RESPAWN_RESTORE_MP = Double.parseDouble(otherSettings.getProperty("RespawnRestoreMP", "70")) / 100;
			
			RESPAWN_RANDOM_ENABLED = Boolean.parseBoolean(otherSettings.getProperty("RespawnRandomInTown", "False"));
			RESPAWN_RANDOM_MAX_OFFSET = Integer.parseInt(otherSettings.getProperty("RespawnRandomMaxOffset", "50"));
			
			/* Maximum number of available slots for pvt stores */
			MAX_PVTSTORESELL_SLOTS_DWARF =
					Integer.parseInt(otherSettings.getProperty("MaxPvtStoreSellSlotsDwarf", "4"));
			MAX_PVTSTORESELL_SLOTS_OTHER =
					Integer.parseInt(otherSettings.getProperty("MaxPvtStoreSellSlotsOther", "3"));
			MAX_PVTSTOREBUY_SLOTS_DWARF = Integer.parseInt(otherSettings.getProperty("MaxPvtStoreBuySlotsDwarf", "5"));
			MAX_PVTSTOREBUY_SLOTS_OTHER = Integer.parseInt(otherSettings.getProperty("MaxPvtStoreBuySlotsOther", "4"));
			
			STORE_EFFECTS = Boolean.parseBoolean(otherSettings.getProperty("StoreSkillCooltime", "true"));
			STORE_EFFECTS_ON_SUBCLASS_CHANGE =
					Boolean.parseBoolean(otherSettings.getProperty("SubclassStoreSkillCooltime", "false"));
			
			SEND_NOTDONE_SKILLS = Integer.parseInt(otherSettings.getProperty("SendNOTDONESkills", "2"));
			
			PET_RENT_NPC = otherSettings.getProperty("ListPetRentNpc", "30827");
			LIST_PET_RENT_NPC.clear();
			for (String id : PET_RENT_NPC.split(","))
			{
				LIST_PET_RENT_NPC.add(Integer.parseInt(id));
			}
			
			ANNOUNCE_MAMMON_SPAWN = Boolean.parseBoolean(otherSettings.getProperty("AnnounceMammonSpawn", "True"));
			
			PETITIONING_ALLOWED = Boolean.parseBoolean(otherSettings.getProperty("PetitioningAllowed", "True"));
			MAX_PETITIONS_PER_PLAYER = Integer.parseInt(otherSettings.getProperty("MaxPetitionsPerPlayer", "5"));
			MAX_PETITIONS_PENDING = Integer.parseInt(otherSettings.getProperty("MaxPetitionsPending", "25"));
			
			JAIL_IS_PVP = Boolean.parseBoolean(otherSettings.getProperty("JailIsPvp", "True"));
			JAIL_DISABLE_CHAT = Boolean.parseBoolean(otherSettings.getProperty("JailDisableChat", "True"));
			DEATH_PENALTY_CHANCE = Integer.parseInt(otherSettings.getProperty("DeathPenaltyChance", "20"));
			
			BANKING_SYSTEM_ENABLED = Boolean.parseBoolean(otherSettings.getProperty("BankingEnabled", "false"));
			BANKING_SYSTEM_GOLDBARS = Integer.parseInt(otherSettings.getProperty("BankingGoldbarCount", "1"));
			BANKING_SYSTEM_ADENA = Integer.parseInt(otherSettings.getProperty("BankingAdenaCount", "500000000"));
		}
	}
	
	// *******************************************************************************************
	public static final String OPTIONS_FILE = "./config/options.properties";
	// *******************************************************************************************
	public static boolean ASSERT; // Enable/disable assertions
	public static boolean DEVELOPER; // Enable/disable DEVELOPER TREATMENT
	public static boolean TEST_KNOWNLIST = false; // Internal properties for developers tests only
	public static boolean ALLOW_WEDDING;
	public static boolean SERVER_PVP; // Is PvP combat enabled? [NO CORE SUPPORT]
	public static int SERVER_AGE_LIM; // Server age limitation
	public static boolean SERVER_GMONLY; // Set the server as GM only at startup?
	public static boolean SERVER_BIT_1; // UNK
	public static boolean SERVER_BIT_3; // UNK/HideName
	public static boolean SERVER_LIST_BRACKET; // Display [] in front of server name
	
	public static int THREAD_POOL_SIZE;
	
	public static boolean AUTODELETE_INVALID_QUEST_DATA; // Auto-delete invalid quest data ?
	public static boolean FORCE_INVENTORY_UPDATE;
	public static boolean SHOW_LICENSE; // Show License at login
	public static boolean SHOW_HTML_WELCOME; // Show html window at login
	public static boolean SHOW_HTML_NEWBIE;
	public static boolean SHOW_HTML_GM;
	public static int LEVEL_HTML_NEWBIE; // Show newbie html when player's level is < to define level
	public static boolean USE_SAY_FILTER; // Config for use chat filter
	public static Pattern[] FILTER_LIST = new Pattern[0];
	public static int AUTODESTROY_ITEM_AFTER; // Time after which item will auto-destroy
	public static int HERB_AUTO_DESTROY_TIME; // Auto destroy herb time
	public static final Set<Integer> LIST_PROTECTED_ITEMS = new L2FastSet<Integer>(); // List of items that will not be destroyed
	public static int CHAR_STORE_INTERVAL; // Interval that the gameserver will update and store character information
	public static boolean UPDATE_ITEMS_ON_CHAR_STORE; // Update items owned by this char when storing the char on DB
	public static boolean LAZY_ITEMS_UPDATE; // Update items only when strictly necessary
	public static boolean DESTROY_DROPPED_PLAYER_ITEM; // Auto destroy nonequipable items dropped by players
	public static boolean DESTROY_PLAYER_INVENTORY_DROP; // Auto destroy items dropped by players from inventory
	public static boolean DESTROY_EQUIPABLE_PLAYER_ITEM; // Auto destroy equipable items dropped by players
	public static boolean SAVE_DROPPED_ITEM; // Save items on ground for restoration on server restart
	public static boolean EMPTY_DROPPED_ITEM_TABLE_AFTER_LOAD; // Empty table ItemsOnGround after load all items
	public static int SAVE_DROPPED_ITEM_INTERVAL; // Time interval to save into db items on ground
	public static boolean CLEAR_DROPPED_ITEM_TABLE; // Clear all items stored in ItemsOnGround table
	public static boolean MULTIPLE_ITEM_DROP; // Accept multi-items drop ?
	/**
	 * This is setting of experimental Client <--> Server Player coordinates
	 * synchronization<br>
	 * <b><u>Values :</u></b> <li>0 - no synchronization at all</li> <li>1 -
	 * parcial synchronization Client --> Server only * using this option it is
	 * difficult for players to bypass obstacles</li> <li>2 - parcial
	 * synchronization Server --> Client only</li> <li>3 - full synchronization
	 * Client <--> Server</li> <li>-1 - Old system: will synchronize Z only</li>
	 */
	public static int COORD_SYNCHRONIZE;
	public static boolean RESTORE_PLAYER_INSTANCE;
	public static boolean ALLOW_SUMMON_TO_INSTANCE;
	public static int DELETE_DAYS;
	public static int MAX_DRIFT_RANGE; // Maximum range mobs can randomly go from spawn point
	public static boolean ALLOW_FISHING;
	public static boolean ALLOW_MANOR; // Allow Manor system
	public static boolean ALLOW_GUARDS; // Allow guards against aggressive monsters
	
	public static boolean ALLOW_DISCARDITEM;
	public static boolean ALLOW_FREIGHT;
	public static boolean ALLOW_WAREHOUSE;
	public static boolean ENABLE_WAREHOUSESORTING_CLAN; // Warehouse Sorting Clan
	public static boolean ENABLE_WAREHOUSESORTING_PRIVATE; // Warehouse Sorting Privat
	public static boolean ENABLE_WAREHOUSESORTING_FREIGHT; // Warehouse Sorting freight
	public static boolean WAREHOUSE_CACHE; // Allow warehouse cache?
	public static int WAREHOUSE_CACHE_TIME; // How long store WH datas
	public static boolean ALLOW_WEAR;
	public static int WEAR_DELAY;
	public static int WEAR_PRICE;
	public static boolean ALLOW_LOTTERY;
	// public static boolean ALLOW_RACE;
	public static boolean ALLOW_WATER;
	public static boolean ALLOW_RENTPET;
	public static boolean ALLOW_BOAT;
	public static boolean ALLOW_CURSED_WEAPONS; // Allow cursed weapons ?
	public static boolean ALLOW_NPC_WALKERS; // WALKER NPC
	public static boolean ALLOW_PET_WALKERS;
	
	public static enum ChatMode
	{
		GLOBAL,
		REGION,
		GM,
		OFF
	}
	
	public static ChatMode DEFAULT_GLOBAL_CHAT; // Global chat state
	public static int GLOBAL_CHAT_TIME;
	public static ChatMode DEFAULT_TRADE_CHAT; // Trade chat state
	public static int TRADE_CHAT_TIME;
	public static boolean REGION_CHAT_ALSO_BLOCKED;
	public static int SOCIAL_TIME; // Flood protector delay between socials
	public static boolean LOG_CHAT; // Logging Chat Window
	public static boolean LOG_ITEMS;
	public static int DEFAULT_PUNISH; // Default punishment for illegal actions
	public static int DEFAULT_PUNISH_PARAM; // Parameter for default punishment
	public static boolean GM_AUDIT;
	public static int COMMUNITY_TYPE; // Community Board
	public static boolean BBS_SHOW_PLAYERLIST;
	public static String BBS_DEFAULT;
	public static boolean SHOW_LEVEL_COMMUNITYBOARD;
	public static boolean SHOW_STATUS_COMMUNITYBOARD;
	public static int NAME_PAGE_SIZE_COMMUNITYBOARD;
	public static int NAME_PER_ROW_COMMUNITYBOARD;
	public static boolean SHOW_LEGEND;
	public static boolean SHOW_CLAN_LEADER;
	public static int SHOW_CLAN_LEADER_CLAN_LEVEL;
	public static boolean SHOW_CURSED_WEAPON_OWNER; // Show Owner(s) of Cursed Weapons in CB ?
	public static boolean SHOW_KARMA_PLAYERS; // Show Player(s) with karma in CB ?
	public static boolean SHOW_JAILED_PLAYERS; // Show player(s) in jail in CB ?
	public static int ZONE_TOWN; // Zone Setting
	public static int MIN_NPC_ANIMATION; // random animation interval
	public static int MAX_NPC_ANIMATION;
	public static int MIN_MONSTER_ANIMATION;
	public static int MAX_MONSTER_ANIMATION;
	public static boolean SHOW_NPC_LVL; // Show L2Monster level and aggro ?
	public static boolean GAMEGUARD_ENFORCE;
	public static boolean GAMEGUARD_PROHIBITACTION;
	public static boolean ONLINE_PLAYERS_AT_STARTUP; // Show Online Players announce
	public static int ONLINE_PLAYERS_ANNOUNCE_INTERVAL;
	public static boolean GRIDS_ALWAYS_ON; // Grid Options
	public static int GRID_NEIGHBOR_TURNON_TIME; // Grid Options
	public static int GRID_NEIGHBOR_TURNOFF_TIME; // Grid Options
	public static boolean CHECK_SKILLS_ON_ENTER; // Skill Tree check on EnterWorld
	public static final Set<Integer> ALLOWED_SKILLS_LIST = new L2FastSet<Integer>();
	public static boolean CHAR_VIP_SKIP_SKILLS_CHECK; // VIP Characters configuration
	public static boolean CHAR_VIP_COLOR_ENABLED; // VIP Characters configuration
	public static int CHAR_VIP_COLOR; // VIP Characters configuration
	public static boolean ALT_DEV_NO_QUESTS; // Alt Settings for devs
	public static boolean ALT_DEV_NO_SPAWNS; // Alt Settings for devs
	public static boolean ALT_DEV_NO_HTMLS; // Alt Settings for devs
	public static boolean ENABLE_JYTHON_SHELL; // JythonShell
	public static boolean ALT_DEV_VERIFY_NPC_SKILLS; // Alt Settings for devs
	public static boolean ONLY_GM_ITEMS_FREE; // Only GM buy items for free
	public static int DEADLOCKCHECK_INTERVAL;
	public static boolean ALT_MASTERWORK_CONFIG;
	public static boolean ALLOW_MASTERWORK;
	public static boolean ALLOW_CRITICAL_CRAFT;
	public static boolean BAN_CLIENT_EMULATORS;
	
	public static boolean SERVER_LIST_CLOCK; // Displays a clock next to the server name ?
	public static boolean SERVER_LIST_TESTSERVER; // Display test server in the list of servers ?
	
	public static int AUCTION_SPECIAL_CURRENCY;
	public static String AUCTION_SPECIAL_CURRENCY_ICON;
	public static String AUCTION_EXCLUDED_ITEMS;
	public static final Set<Integer> AUCTION_EXCLUDED_ITEMS_LIST = new L2FastSet<Integer>();
	public static String AUCTION_INCLUDED_ITEMS;
	public static final Set<Integer> AUCTION_INCLUDED_ITEMS_LIST = new L2FastSet<Integer>();
	
	// *******************************************************************************************
	// offline trade
	public static boolean ALLOW_OFFLINE_TRADE;
	public static boolean ALLOW_OFFLINE_TRADE_CRAFT;
	public static boolean ALLOW_OFFLINE_TRADE_COLOR_NAME;
	public static int OFFLINE_TRADE_COLOR_NAME;
	public static boolean ALLOW_OFFLINE_TRADE_PROTECTION;
	public static long OFFLINE_TRADE_PRICE;
	public static int OFFLINE_TRADE_PRICE_ITEM;
	public static boolean ENABLE_OFFLINE_TRADERS_RESTORE;
	
	public static boolean ENTERWORLD_QUEUING;
	public static int ENTERWORLD_TICK;
	public static int ENTERWORLD_PPT;
	
	public static int MERCENARY_SAVING_DELAY;
	
	public static int RETARGET_BLOCKING_PERIOD;
	
	// *******************************************************************************************
	public static String HTML_CACHE_FILE;
	
	public static boolean BAN_DUPLICATE_ITEM_OWNER;
	
	// *******************************************************************************************
	private static final class OptionsConfig extends ConfigPropertiesLoader
	{
		@Override
		protected String getName()
		{
			return "options";
		}
		
		@Override
		protected void loadImpl(L2Properties optionsSettings)
		{
			SERVER_LIST_BRACKET = Boolean.parseBoolean(optionsSettings.getProperty("ServerListBrackets", "false"));
			SERVER_LIST_CLOCK = Boolean.parseBoolean(optionsSettings.getProperty("ServerListClock", "false"));
			
			ASSERT = Boolean.parseBoolean(optionsSettings.getProperty("Assert", "false"));
			DEVELOPER = Boolean.parseBoolean(optionsSettings.getProperty("Developer", "false"));
			SERVER_LIST_TESTSERVER = Boolean.parseBoolean(optionsSettings.getProperty("TestServer", "false"));
			
			SERVER_LIST_BRACKET = Boolean.parseBoolean(optionsSettings.getProperty("ServerListBrackets", "false"));
			SERVER_BIT_1 = Boolean.parseBoolean(optionsSettings.getProperty("ServerB1UNK", "false"));
			SERVER_BIT_3 = !Boolean.parseBoolean(optionsSettings.getProperty("ServerShowName", "true"));
			SERVER_GMONLY = Boolean.parseBoolean(optionsSettings.getProperty("ServerGMOnly", "false"));
			SERVER_PVP = Boolean.parseBoolean(optionsSettings.getProperty("ServerPvPEnabled", "true"));
			SERVER_AGE_LIM = Integer.parseInt(optionsSettings.getProperty("ServerAgeLimitation", "15"));
			
			AUTODESTROY_ITEM_AFTER =
					Integer.parseInt(optionsSettings.getProperty("AutoDestroyDroppedItemAfter", "600")) * 1000;
			HERB_AUTO_DESTROY_TIME = Integer.parseInt(optionsSettings.getProperty("AutoDestroyHerbTime", "15")) * 1000;
			String PROTECTED_ITEMS = optionsSettings.getProperty("ListOfProtectedItems", "0");
			LIST_PROTECTED_ITEMS.clear();
			for (String id : PROTECTED_ITEMS.trim().split(","))
			{
				LIST_PROTECTED_ITEMS.add(Integer.parseInt(id.trim()));
			}
			CHAR_STORE_INTERVAL = Integer.parseInt(optionsSettings.getProperty("CharacterDataStoreInterval", "15"));
			UPDATE_ITEMS_ON_CHAR_STORE =
					Boolean.parseBoolean(optionsSettings.getProperty("UpdateItemsOnCharStore", "false"));
			LAZY_ITEMS_UPDATE = Boolean.parseBoolean(optionsSettings.getProperty("LazyItemsUpdate", "false"));
			
			DESTROY_DROPPED_PLAYER_ITEM =
					Boolean.parseBoolean(optionsSettings.getProperty("DestroyPlayerDroppedItem", "false"));
			DESTROY_PLAYER_INVENTORY_DROP =
					Boolean.parseBoolean(optionsSettings.getProperty("DestroyPlayerInventoryDrop", "false"));
			DESTROY_EQUIPABLE_PLAYER_ITEM =
					Boolean.parseBoolean(optionsSettings.getProperty("DestroyEquipableItem", "false"));
			SAVE_DROPPED_ITEM = Boolean.parseBoolean(optionsSettings.getProperty("SaveDroppedItem", "false"));
			EMPTY_DROPPED_ITEM_TABLE_AFTER_LOAD =
					Boolean.parseBoolean(optionsSettings.getProperty("EmptyDroppedItemTableAfterLoad", "false"));
			SAVE_DROPPED_ITEM_INTERVAL =
					Integer.parseInt(optionsSettings.getProperty("SaveDroppedItemInterval", "0")) * 60000;
			CLEAR_DROPPED_ITEM_TABLE =
					Boolean.parseBoolean(optionsSettings.getProperty("ClearDroppedItemTable", "false"));
			
			MULTIPLE_ITEM_DROP = Boolean.parseBoolean(optionsSettings.getProperty("MultipleItemDrop", "True"));
			
			COORD_SYNCHRONIZE = Integer.parseInt(optionsSettings.getProperty("CoordSynchronize", "-1"));
			
			RESTORE_PLAYER_INSTANCE =
					Boolean.parseBoolean(optionsSettings.getProperty("RestorePlayerInstance", "False"));
			ALLOW_SUMMON_TO_INSTANCE =
					Boolean.parseBoolean(optionsSettings.getProperty("AllowSummonToInstance", "True"));
			
			ALLOW_WAREHOUSE = Boolean.parseBoolean(optionsSettings.getProperty("AllowWarehouse", "True"));
			ENABLE_WAREHOUSESORTING_CLAN =
					Boolean.parseBoolean(optionsSettings.getProperty("EnableWarehouseSortingClan", "False"));
			ENABLE_WAREHOUSESORTING_PRIVATE =
					Boolean.parseBoolean(optionsSettings.getProperty("EnableWarehouseSortingPrivate", "False"));
			ENABLE_WAREHOUSESORTING_FREIGHT =
					Boolean.parseBoolean(optionsSettings.getProperty("EnableWarehouseSortingFreight", "False"));
			WAREHOUSE_CACHE = Boolean.parseBoolean(optionsSettings.getProperty("WarehouseCache", "False"));
			WAREHOUSE_CACHE_TIME = Integer.parseInt(optionsSettings.getProperty("WarehouseCacheTime", "15"));
			ALLOW_FREIGHT = Boolean.parseBoolean(optionsSettings.getProperty("AllowFreight", "True"));
			ALLOW_WEAR = Boolean.parseBoolean(optionsSettings.getProperty("AllowWear", "False"));
			WEAR_DELAY = Integer.parseInt(optionsSettings.getProperty("WearDelay", "5"));
			WEAR_PRICE = Integer.parseInt(optionsSettings.getProperty("WearPrice", "10"));
			ALLOW_LOTTERY = Boolean.parseBoolean(optionsSettings.getProperty("AllowLottery", "False"));
			// ALLOW_RACE = Boolean.parseBoolean(optionsSettings.getProperty("AllowRace", "False"));
			ALLOW_WATER = Boolean.parseBoolean(optionsSettings.getProperty("AllowWater", "True"));
			ALLOW_RENTPET = Boolean.parseBoolean(optionsSettings.getProperty("AllowRentPet", "False"));
			ALLOW_DISCARDITEM = Boolean.parseBoolean(optionsSettings.getProperty("AllowDiscardItem", "True"));
			ALLOW_FISHING = Boolean.parseBoolean(optionsSettings.getProperty("AllowFishing", "True"));
			ALLOW_MANOR = Boolean.parseBoolean(optionsSettings.getProperty("AllowManor", "False"));
			ALLOW_BOAT = Boolean.parseBoolean(optionsSettings.getProperty("AllowBoat", "False"));
			ALLOW_NPC_WALKERS = Boolean.parseBoolean(optionsSettings.getProperty("AllowNpcWalkers", "True"));
			ALLOW_PET_WALKERS = Boolean.parseBoolean(optionsSettings.getProperty("AllowPetWalkers", "False"));
			ALLOW_CURSED_WEAPONS = Boolean.parseBoolean(optionsSettings.getProperty("AllowCursedWeapons", "False"));
			ALLOW_WEDDING = Boolean.parseBoolean(optionsSettings.getProperty("AllowWedding", "False"));
			ALLOW_GUARDS = Boolean.parseBoolean(optionsSettings.getProperty("AllowGuards", "False"));
			
			DEFAULT_GLOBAL_CHAT = ChatMode.valueOf(optionsSettings.getProperty("GlobalChat", "REGION").toUpperCase());
			GLOBAL_CHAT_TIME = Integer.parseInt(optionsSettings.getProperty("GlobalChatTime", "1"));
			DEFAULT_TRADE_CHAT = ChatMode.valueOf(optionsSettings.getProperty("TradeChat", "REGION").toUpperCase());
			TRADE_CHAT_TIME = Integer.parseInt(optionsSettings.getProperty("TradeChatTime", "1"));
			REGION_CHAT_ALSO_BLOCKED =
					Boolean.parseBoolean(optionsSettings.getProperty("RegionChatAlsoBlocked", "false"));
			SOCIAL_TIME = Integer.parseInt(optionsSettings.getProperty("SocialTime", "26"));
			
			LOG_CHAT = Boolean.parseBoolean(optionsSettings.getProperty("LogChat", "false"));
			LOG_ITEMS = Boolean.parseBoolean(optionsSettings.getProperty("LogItems", "false"));
			
			GM_AUDIT = Boolean.parseBoolean(optionsSettings.getProperty("GMAudit", "False"));
			
			COMMUNITY_TYPE = Integer.parseInt(optionsSettings.getProperty("CommunityType", "1"));
			BBS_SHOW_PLAYERLIST = Boolean.parseBoolean(optionsSettings.getProperty("BBSShowPlayerList", "false"));
			BBS_DEFAULT = optionsSettings.getProperty("BBSDefault", "_bbshome");
			SHOW_LEVEL_COMMUNITYBOARD =
					Boolean.parseBoolean(optionsSettings.getProperty("ShowLevelOnCommunityBoard", "False"));
			SHOW_STATUS_COMMUNITYBOARD =
					Boolean.parseBoolean(optionsSettings.getProperty("ShowStatusOnCommunityBoard", "True"));
			NAME_PAGE_SIZE_COMMUNITYBOARD =
					Integer.parseInt(optionsSettings.getProperty("NamePageSizeOnCommunityBoard", "50"));
			if (NAME_PAGE_SIZE_COMMUNITYBOARD > 70)
				NAME_PAGE_SIZE_COMMUNITYBOARD = 70; //can be displayed more then 70 but for best window showup limited to 70
			NAME_PER_ROW_COMMUNITYBOARD =
					Integer.parseInt(optionsSettings.getProperty("NamePerRowOnCommunityBoard", "5"));
			if (NAME_PER_ROW_COMMUNITYBOARD > 5)
				NAME_PER_ROW_COMMUNITYBOARD = 5;
			SHOW_LEGEND = Boolean.parseBoolean(optionsSettings.getProperty("ShowLegend", "False"));
			SHOW_CLAN_LEADER = Boolean.parseBoolean(optionsSettings.getProperty("ShowClanLeader", "False"));
			SHOW_CLAN_LEADER_CLAN_LEVEL =
					Integer.parseInt(optionsSettings.getProperty("ShowClanLeaderAtClanLevel", "3"));
			SHOW_CURSED_WEAPON_OWNER =
					Boolean.parseBoolean(optionsSettings.getProperty("ShowCursedWeaponOwner", "False"));
			SHOW_KARMA_PLAYERS = Boolean.parseBoolean(optionsSettings.getProperty("ShowKarmaPlayers", "False"));
			SHOW_JAILED_PLAYERS = Boolean.parseBoolean(optionsSettings.getProperty("ShowJailedPlayers", "False"));
			
			ZONE_TOWN = Integer.parseInt(optionsSettings.getProperty("ZoneTown", "0"));
			
			MAX_DRIFT_RANGE = Integer.parseInt(optionsSettings.getProperty("MaxDriftRange", "300"));
			
			MIN_NPC_ANIMATION = Integer.parseInt(optionsSettings.getProperty("MinNPCAnimation", "10"));
			MAX_NPC_ANIMATION = Integer.parseInt(optionsSettings.getProperty("MaxNPCAnimation", "20"));
			MIN_MONSTER_ANIMATION = Integer.parseInt(optionsSettings.getProperty("MinMonsterAnimation", "5"));
			MAX_MONSTER_ANIMATION = Integer.parseInt(optionsSettings.getProperty("MaxMonsterAnimation", "20"));
			
			SHOW_NPC_LVL = Boolean.parseBoolean(optionsSettings.getProperty("ShowNpcLevel", "False"));
			
			FORCE_INVENTORY_UPDATE = Boolean.parseBoolean(optionsSettings.getProperty("ForceInventoryUpdate", "False"));
			
			AUTODELETE_INVALID_QUEST_DATA =
					Boolean.parseBoolean(optionsSettings.getProperty("AutoDeleteInvalidQuestData", "False"));
			
			final int baseThreadPoolSize = Integer.parseInt(optionsSettings.getProperty("BaseThreadPoolSize", "2"));
			final int extraThreadPerCore = Integer.parseInt(optionsSettings.getProperty("ExtraThreadPerCore", "4"));
			
			THREAD_POOL_SIZE = baseThreadPoolSize + Runtime.getRuntime().availableProcessors() * extraThreadPerCore;
			
			DELETE_DAYS = Integer.parseInt(optionsSettings.getProperty("DeleteCharAfterDays", "7"));
			
			DEFAULT_PUNISH = Integer.parseInt(optionsSettings.getProperty("DefaultPunish", "2"));
			DEFAULT_PUNISH_PARAM = Integer.parseInt(optionsSettings.getProperty("DefaultPunishParam", "0"));
			
			GAMEGUARD_ENFORCE = Boolean.parseBoolean(optionsSettings.getProperty("GameGuardEnforce", "False"));
			GAMEGUARD_PROHIBITACTION =
					Boolean.parseBoolean(optionsSettings.getProperty("GameGuardProhibitAction", "False"));
			GRIDS_ALWAYS_ON = Boolean.parseBoolean(optionsSettings.getProperty("GridsAlwaysOn", "False"));
			GRID_NEIGHBOR_TURNON_TIME = Integer.parseInt(optionsSettings.getProperty("GridNeighborTurnOnTime", "1"));
			GRID_NEIGHBOR_TURNOFF_TIME = Integer.parseInt(optionsSettings.getProperty("GridNeighborTurnOffTime", "90"));
			
			SHOW_LICENSE = Boolean.parseBoolean(optionsSettings.getProperty("ShowLicense", "false"));
			SHOW_HTML_WELCOME = Boolean.parseBoolean(optionsSettings.getProperty("ShowHTMLWelcome", "false"));
			SHOW_HTML_NEWBIE = Boolean.parseBoolean(optionsSettings.getProperty("ShowHTMLNewbie", "False"));
			SHOW_HTML_GM = Boolean.parseBoolean(optionsSettings.getProperty("ShowHTMLGm", "False"));
			LEVEL_HTML_NEWBIE = Integer.parseInt(optionsSettings.getProperty("LevelShowHTMLNewbie", "10"));
			USE_SAY_FILTER = Boolean.parseBoolean(optionsSettings.getProperty("UseSayFilter", "false"));
			
			CHAR_VIP_SKIP_SKILLS_CHECK =
					Boolean.parseBoolean(optionsSettings.getProperty("CharViPSkipSkillsCheck", "false"));
			CHAR_VIP_COLOR_ENABLED = Boolean.parseBoolean(optionsSettings.getProperty("CharViPAllowColor", "false"));
			CHAR_VIP_COLOR = Integer.decode("0x" + optionsSettings.getProperty("CharViPNameColor", "00CCFF"));
			
			ONLINE_PLAYERS_AT_STARTUP =
					Boolean.parseBoolean(optionsSettings.getProperty("ShowOnlinePlayersAtStartup", "True"));
			ONLINE_PLAYERS_ANNOUNCE_INTERVAL =
					Integer.parseInt(optionsSettings.getProperty("OnlinePlayersAnnounceInterval", "900000"));
			
			ONLY_GM_ITEMS_FREE = Boolean.parseBoolean(optionsSettings.getProperty("OnlyGMItemsFree", "True"));
			
			// ---------------------------------------------------
			// Configuration values not found in config files
			// ---------------------------------------------------
			
			CHECK_SKILLS_ON_ENTER = Boolean.parseBoolean(optionsSettings.getProperty("CheckSkillsOnEnter", "false"));
			
			String ALLOWED_SKILLS = optionsSettings.getProperty("AllowedSkills", ""); // List of Skills that are allowed for all Classes if CHECK_SKILLS_ON_ENTER = true
			ALLOWED_SKILLS_LIST.clear();
			for (String id : StringUtils.split(ALLOWED_SKILLS, ","))
			{
				ALLOWED_SKILLS_LIST.add(Integer.parseInt(id.trim()));
			}
			
			ALT_DEV_NO_QUESTS = Boolean.parseBoolean(optionsSettings.getProperty("AltDevNoQuests", "False"));
			ALT_DEV_NO_SPAWNS = Boolean.parseBoolean(optionsSettings.getProperty("AltDevNoSpawns", "False"));
			ALT_DEV_NO_HTMLS = Boolean.parseBoolean(optionsSettings.getProperty("AltDevNoHtmls", "False"));
			ENABLE_JYTHON_SHELL = Boolean.parseBoolean(optionsSettings.getProperty("EnableJythonShell", "False"));
			ALT_DEV_VERIFY_NPC_SKILLS =
					Boolean.parseBoolean(optionsSettings.getProperty("AltDevVerifyNpcSkills", "False"));
			DEADLOCKCHECK_INTERVAL = Integer.parseInt(optionsSettings.getProperty("DeadLockCheck", "10000"));
			ALT_MASTERWORK_CONFIG = Boolean.parseBoolean(optionsSettings.getProperty("AltMasterworkConfig", "False"));
			ALLOW_MASTERWORK = Boolean.parseBoolean(optionsSettings.getProperty("AllowMasterwork", "False"));
			ALLOW_CRITICAL_CRAFT = Boolean.parseBoolean(optionsSettings.getProperty("AllowCriticalCraft", "False"));
			BAN_CLIENT_EMULATORS = Boolean.parseBoolean(optionsSettings.getProperty("AutoBanClientEmulators", "True"));
			
			AUCTION_SPECIAL_CURRENCY = Integer.parseInt(optionsSettings.getProperty("AuctionSpecialCurrency", "8575"));
			AUCTION_SPECIAL_CURRENCY_ICON =
					optionsSettings.getProperty("AuctionSpecialCurrencyIcon", "etc_box_of_adventure_3_i00");
			AUCTION_EXCLUDED_ITEMS = optionsSettings.getProperty("AuctionExcludedItems", "57,5575");
			AUCTION_EXCLUDED_ITEMS_LIST.clear();
			for (String id : AUCTION_EXCLUDED_ITEMS.trim().split(","))
			{
				AUCTION_EXCLUDED_ITEMS_LIST.add(Integer.parseInt(id.trim()));
			}
			
			AUCTION_INCLUDED_ITEMS = optionsSettings.getProperty("AuctionIncludedItems", "8575");
			AUCTION_INCLUDED_ITEMS_LIST.clear();
			for (String id : AUCTION_INCLUDED_ITEMS.trim().split(","))
			{
				AUCTION_INCLUDED_ITEMS_LIST.add(Integer.parseInt(id.trim()));
			}
			
			// *******************************************************************************************
			// offline trade
			ALLOW_OFFLINE_TRADE = Boolean.parseBoolean(optionsSettings.getProperty("AllowOfflineTrade", "false"));
			ALLOW_OFFLINE_TRADE_CRAFT =
					Boolean.parseBoolean(optionsSettings.getProperty("AllowOfflineTradeCraft", "true"));
			ALLOW_OFFLINE_TRADE_COLOR_NAME =
					Boolean.parseBoolean(optionsSettings.getProperty("AllowOfflineTradeColorName", "true"));
			OFFLINE_TRADE_COLOR_NAME =
					Integer.decode("0x" + optionsSettings.getProperty("OfflineTradeColorName", "999999"));
			ALLOW_OFFLINE_TRADE_PROTECTION =
					Boolean.parseBoolean(optionsSettings.getProperty("AllowOfflineTradeProtection", "true"));
			ENABLE_OFFLINE_TRADERS_RESTORE =
					Boolean.parseBoolean(optionsSettings.getProperty("EnableOfflineTradersRestore", "false"));
			OFFLINE_TRADE_PRICE = Long.parseLong(optionsSettings.getProperty("OfflineTradePrice", "0"));
			OFFLINE_TRADE_PRICE_ITEM = Integer.parseInt(optionsSettings.getProperty("OfflineTradePriceItem", "57"));
			
			ENTERWORLD_QUEUING = Boolean.parseBoolean(optionsSettings.getProperty("DiscreteStarterPackets", "false"));
			ENTERWORLD_TICK = Integer.parseInt(optionsSettings.getProperty("StarterPacketTick", "100"));
			ENTERWORLD_PPT = Integer.parseInt(optionsSettings.getProperty("StarterPacketsPerTick", "8"));
			
			MERCENARY_SAVING_DELAY = Integer.parseInt(optionsSettings.getProperty("MercenaryPosUpdateDelay", "90000"));
			
			RETARGET_BLOCKING_PERIOD = Integer.parseInt(optionsSettings.getProperty("CannotRetargetFor", "400"));
			
			HTML_CACHE_FILE = optionsSettings.getProperty("HtmlCacheFile", "./cache/html.cache");
			
			BAN_DUPLICATE_ITEM_OWNER =
					Boolean.parseBoolean(optionsSettings.getProperty("BanDuplicateItemOwner", "False"));
		}
	}
	
	// *******************************************************************************************
	public static final String ALT_SETTINGS_FILE = "./config/altsettings.properties";
	// *******************************************************************************************
	public static int ALT_DEFAULT_RESTARTTOWN; // Set alternative default restarttown
	public static boolean ALT_GAME_CREATION; // Alternative game crafting
	public static double ALT_GAME_CREATION_SPEED; // Alternative game crafting speed mutiplier - default 0
	// (fastest but still not instant)
	public static double ALT_GAME_CREATION_XP_RATE; // Alternative game crafting XP rate multiplier - default 1
	public static double ALT_GAME_CREATION_RARE_XPSP_RATE;
	public static double ALT_GAME_CREATION_SP_RATE; // Alternative game crafting SP rate multiplier - default 1
	public static boolean ALT_BLACKSMITH_USE_RECIPES; // Alternative setting to blacksmith use of recipes to craft -
	// default true
	public static boolean ALT_REMOVE_CASTLE_CIRCLETS; // Remove Castle circlets after clan lose his castle? - default
	public static boolean ALT_ONLY_CLANLEADER_CAN_SIT_ON_THRONE;
	// true
	public static double ALT_WEIGHT_LIMIT; // Alternative game weight limit multiplier - default 1
	public static int ALT_BUFFS_MAX_AMOUNT; // Alternative number of cumulated buff
	public static int ALT_DANCES_SONGS_MAX_AMOUNT; // Alternative number of cumulated dances/songs
	public static boolean DANCE_CANCEL_BUFF;
	public static int ALT_MINIMUM_FALL_HEIGHT; // Minimum Height(Z) that a character needs to fall, in
	// order for it to be considered a fall.
	public static boolean ALT_DISABLE_RAIDBOSS_PETRIFICATION; // Disable Raidboss Petrification
	public static int ALT_PCRITICAL_CAP; // PCritical Cap
	public static int ALT_MCRITICAL_CAP; // MCritical Cap
	public static int ALT_MAX_EVASION;
	public static int ALT_MAX_RUN_SPEED; // Runspeed limit
	public static float ALT_MCRIT_RATE;
	public static float ALT_MCRIT_PVP_RATE;
	
	public static double ALT_POLEARM_DAMAGE_MULTI;
	public static double ALT_POLEARM_VAMPIRIC_MULTI;
	
	public static boolean ALT_GAME_SKILL_LEARN; // Alternative game skill learning
	public static boolean ALT_GAME_CANCEL_BOW; // Cancel attack bow by hit
	public static boolean ALT_GAME_CANCEL_CAST; // Cancel cast by hit
	public static boolean ALT_GAME_TIREDNESS; // Alternative game - use tiredness, instead of CP
	public static int ALT_PARTY_RANGE;
	public static int ALT_PARTY_RANGE2;
	public static boolean ALT_GAME_SHIELD_BLOCKS; // Alternative shield defence
	public static int ALT_PERFECT_SHLD_BLOCK; // Alternative Perfect shield defence rate
	public static boolean ALT_MOB_AGGRO_IN_PEACEZONE; // -
	public static boolean ALT_ATTACKABLE_NPCS;
	public static float ALT_INSTANT_KILL_EFFECT_2; // Rate of Instant kill effect 2(CP no change ,HP =1,no kill
	public static boolean ALT_DAGGER_FORMULA; // Alternative success rate formulas for skills such
	// dagger/critical skills and blows
	public static float ALT_ATTACK_DELAY; // Alternative config for next hit delay
	public static int ALT_DAGGER_RATE; // Alternative success rate for dagger blow,MAX value 100
	// (100% rate)
	public static int ALT_DAGGER_FAIL_RATE; // Alternative fail rate for dagger blow,MAX value 100 (100%
	// rate)
	public static int ALT_DAGGER_RATE_BEHIND; // Alternative increasement to success rate for dagger/critical
	// skills if activeChar is Behind the target
	public static int ALT_DAGGER_RATE_FRONT; // Alternative increasement to success rate for
	// dagger/critical skills if activeChar is in Front of
	// target
	public static boolean ALT_GAME_FREIGHTS; // Alternative freight modes - Freights can be withdrawed
	// from any village
	public static int ALT_GAME_FREIGHT_PRICE; // Alternative freight modes - Sets the price value for each
	// freightened item
	public static boolean ALT_GAME_DELEVEL; // Alternative gameing - loss of XP on death
	public static boolean ALT_GAME_MAGICFAILURES; // Alternative gameing - magic dmg failures
	public static boolean ALT_GAME_FREE_TELEPORT; // Alternative gaming - allow free teleporting around the world.
	public static boolean ALT_RECOMMEND; // Disallow recommend character twice or more a day ?
	public static boolean ALT_GAME_SUBCLASS_WITHOUT_QUESTS; // Alternative gaming - allow sub-class addition without
	public static boolean ALT_GAME_SUBCLASS_EVERYWHERE;
	// quest completion.
	public static int ALT_MAX_SUBCLASS; // Allow to change max number of subclasses
	public static byte ALT_MAX_SUBCLASS_LEVEL;
	public static boolean ALT_GAME_VIEWNPC; // View npc stats/drop by shift-cliking it for nongm-players
	public static boolean ALT_GAME_NEW_CHAR_ALWAYS_IS_NEWBIE = true; // Alternative gaming - all new characters always are newbies.
	public static boolean ALT_MEMBERS_CAN_WITHDRAW_FROM_CLANWH; // Alternative gaming - clan members with see privilege can
	// also withdraw from clan warehouse.
	public static boolean ALT_CASTLE_SHIELD; // Alternative gaming - Castle Shield can be equiped by all clan members if they own a castle. - default True
	public static boolean ALT_CLANHALL_SHIELD; // Alternative gaming - Clan Hall Shield can be equiped by all clan members if they own a clan hall. - default True
	public static boolean ALT_APELLA_ARMORS; // Alternative gaming - Apella armors can be equiped only by clan members if their class is Baron or higher - default True
	public static boolean ALT_OATH_ARMORS; // Alternative gaming - Clan Oath Armors can be equiped only by clan members - default True
	public static boolean ALT_CASTLE_CROWN; // Alternative gaming - Castle Crown can be equiped only by castle lord - default True
	public static boolean ALT_CASTLE_CIRCLETS; // Alternative gaming - Castle Circlets can be equiped only by clan members if they own a castle - default True
	
	public static boolean ALT_STRICT_HERO_SYSTEM; // Strict Hero Mode
	public static boolean ALT_STRICT_SEVENSIGNS; // Strict Seven Signs
	public static boolean ALT_SP_BOOK_NEEDED; // Spell Book needed to learn skill
	public static boolean ALT_LIFE_CRYSTAL_NEEDED; // Clan Item needed to learn clan skills
	public static boolean ALT_ES_SP_BOOK_NEEDED; // Spell Book needet to enchant skill
	public static boolean DIVINE_SP_BOOK_NEEDED;
	public static int ALT_BUFF_TIME;
	public static int ALT_DANCE_TIME;
	public static boolean ALT_DANCE_MP_CONSUME;
	public static int ALT_MAX_PATK_SPEED; // Config for limit physical attack speed
	public static int ALT_MAX_MATK_SPEED; // Config for limit magical attack speed
	public static float ALT_MAGES_PHYSICAL_DAMAGE_MULTI; // Config for damage multiplies
	public static float ALT_MAGES_MAGICAL_DAMAGE_MULTI; // Config for damage multiplies
	public static float ALT_FIGHTERS_PHYSICAL_DAMAGE_MULTI; // Config for damage multiplies
	public static float ALT_FIGHTERS_MAGICAL_DAMAGE_MULTI; // Config for damage multiplies
	public static float ALT_PETS_PHYSICAL_DAMAGE_MULTI; // Config for damage multiplies
	public static float ALT_PETS_MAGICAL_DAMAGE_MULTI; // Config for damage multiplies
	public static float ALT_NPC_PHYSICAL_DAMAGE_MULTI; // Config for damage multiplies
	public static float ALT_NPC_MAGICAL_DAMAGE_MULTI; // Config for damage multiplies
	public static int ALT_URN_TEMP_FAIL; // Config for URN temp fail
	public static int ALT_BUFFER_HATE; // Buffer Hate
	public static int ALT_DIFF_CUTOFF; // No exp cutoff
	public static boolean ALT_SPAWN_WYVERN_MANAGER;
	public static int ALT_MANAGER_CRYSTAL_COUNT;
	public static boolean ALT_SPAWN_CLASS_MASTER;
	public static boolean ALT_CLASS_MASTER_STRIDER_UPDATE;
	public static String ALT_CLASS_MASTER_SETTINGS_LINE;
	public static ClassMasterSettings ALT_CLASS_MASTER_SETTINGS;
	public static boolean ALT_L2J_CLASS_MASTER;
	public static boolean ALT_CLASS_MASTER_ENTIRE_TREE;
	public static boolean ALT_CLASS_MASTER_TUTORIAL;
	public static double ALT_CRAFT_PRICE; // reference price multiplier
	public static int ALT_CRAFT_DEFAULT_PRICE; // default price, in case reference is 0
	public static boolean ALT_CRAFT_ALLOW_CRAFT; // allow to craft dwarven recipes
	public static boolean ALT_CRAFT_ALLOW_CRYSTALLIZE; // allow to break items
	public static boolean ALT_CRAFT_ALLOW_COMMON; // allow to craft common craft recipes
	public static boolean ALT_AUTO_LOOT; // Accept auto-loot ?
	public static boolean ALT_AUTO_LOOT_RAID;
	public static boolean ALT_AUTO_LOOT_ADENA;
	public static boolean ALT_AUTO_LOOT_HERBS;
	public static boolean ALT_SPAWN_SIEGE_GUARD; // Config for spawn siege guards
	public static int ALT_TIME_IN_A_DAY_OF_OPEN_A_DOOR;
	public static int ALT_TIME_OF_OPENING_A_DOOR;
	public static int ALT_TIMELIMITOFINVADE; //Time limit of invade to lair of bosses after server restarted
	public static int ALT_DWARF_RECIPE_LIMIT; // Recipebook limits
	public static int ALT_COMMON_RECIPE_LIMIT;
	public static int ALT_CHANCE_BREAK; // Chance For Soul Crystal to Break
	public static int ALT_CHANCE_LEVEL; // Chance For Soul Crystal to Level
	public static boolean ALT_GAME_KARMA_PLAYER_CAN_BE_KILLED_IN_PEACEZONE; // Karma Punishment
	public static boolean ALT_GAME_KARMA_PLAYER_CAN_SHOP;
	public static boolean ALT_GAME_KARMA_PLAYER_CAN_USE_GK; // Allow player with karma to use GK ?
	public static boolean ALT_GAME_KARMA_PLAYER_CAN_TELEPORT;
	public static boolean ALT_GAME_KARMA_PLAYER_CAN_TRADE;
	public static boolean ALT_GAME_KARMA_PLAYER_CAN_USE_WAREHOUSE;
	public static int ALT_PLAYER_PROTECTION_LEVEL; // Player Protection Level
	public static boolean ALT_AUTO_LEARN_SKILLS; // Config for Auto Learn Skills
	public static boolean ALT_AUTO_LEARN_DIVINE_INSPIRATION; // Alternative auto skill learning for divine inspiration (+4 max buff count)
	public static boolean ALT_GRADE_PENALTY; // Disable Grade penalty
	public static boolean ALT_FAME_FOR_DEAD_PLAYERS;
	public static boolean ALT_IS_CRAFTING_ENABLED; // Crafting Enabled?
	//public static boolean				ALT_FAIL_FAKEDEATH;														// Config for Fake Death Fail Feature
	public static boolean ALT_FLYING_WYVERN_IN_SIEGE; // Config for Wyvern enable flying in siege **/
	public static int ALT_OLY_START_TIME; // Olympiad Competition Starting time
	public static int ALT_OLY_MIN; // Olympiad Minutes
	public static int ALT_OLY_CPERIOD; // Olympaid Competition Period
	public static int ALT_OLY_BATTLE; // Olympiad Battle Period
	public static int ALT_OLY_WPERIOD; // Olympiad Weekly Period
	public static int ALT_OLY_VPERIOD; // Olympiad Validation Period
	public static boolean ALT_OLY_SAME_IP; // Olympiad allow Matches from same Ip
	public static int ALT_OLY_CLASSED;
	public static int ALT_OLY_BATTLE_REWARD_ITEM;
	public static int ALT_OLY_CLASSED_RITEM_C;
	public static int ALT_OLY_NONCLASSED_RITEM_C;
	public static int ALT_OLY_REG_DISPLAY;
	public static int ALT_OLY_GP_PER_POINT;
	public static int ALT_OLY_HERO_POINTS;
	public static int ALT_OLY_RANK1_POINTS;
	public static int ALT_OLY_RANK2_POINTS;
	public static int ALT_OLY_RANK3_POINTS;
	public static int ALT_OLY_RANK4_POINTS;
	public static int ALT_OLY_RANK5_POINTS;
	public static int ALT_OLY_MAX_POINTS;
	public static boolean ALT_OLY_LOG_FIGHTS;
	public static boolean ALT_OLY_SHOW_MONTHLY_WINNERS;
	public static boolean ALT_OLY_ANNOUNCE_GAMES;
	public static int ALT_OLY_ENCHANT_LIMIT;
	public static final Set<Integer> ALT_LIST_OLY_RESTRICTED_ITEMS = new L2FastSet<Integer>();
	public static int ALT_OLY_NONCLASSED;
	public static boolean ALT_OLY_REMOVE_CUBICS;
	public static float ALT_GAME_SUMMON_PENALTY_RATE; // Alternative game summon penalty
	public static int ALT_MANOR_REFRESH_TIME; // Manor Refresh Starting time
	public static int ALT_MANOR_REFRESH_MIN; // Manor Refresh Min
	public static int ALT_MANOR_APPROVE_TIME; // Manor Next Period Approve Starting time
	public static int ALT_MANOR_APPROVE_MIN; // Manor Next Period Approve Min
	public static int ALT_MANOR_MAINTENANCE_PERIOD; // Manor Maintenance Time
	public static boolean ALT_MANOR_SAVE_ALL_ACTIONS; // Manor Save All Actions
	public static int ALT_MANOR_SAVE_PERIOD_RATE; // Manor Save Period Rate
	
	// Four Sepulchers
	public static int ALT_FS_TIME_ATTACK;
	public static int ALT_FS_TIME_COOLDOWN;
	public static int ALT_FS_TIME_ENTRY;
	public static int ALT_FS_TIME_WARMUP;
	public static int ALT_FS_PARTY_MEMBER_COUNT;
	
	// Dimensional Rift
	public static int ALT_RIFT_MIN_PARTY_SIZE; // Minimum siz e of a party that may enter dimensional rift
	public static int ALT_RIFT_SPAWN_DELAY; // Time in ms the party has to wait until the mobs spawn
	// when entering a room
	public static int ALT_RIFT_MAX_JUMPS; // Amount of random rift jumps before party is ported back
	public static int ALT_RIFT_AUTO_JUMPS_TIME_MIN; // Random time between two jumps in dimensional rift - in
	// seconds
	public static int ALT_RIFT_AUTO_JUMPS_TIME_MAX;
	public static int ALT_RIFT_ENTER_COST_RECRUIT; // Dimensional Fragment cost for entering rift
	public static int ALT_RIFT_ENTER_COST_SOLDIER;
	public static int ALT_RIFT_ENTER_COST_OFFICER;
	public static int ALT_RIFT_ENTER_COST_CAPTAIN;
	public static int ALT_RIFT_ENTER_COST_COMMANDER;
	public static int ALT_RIFT_ENTER_COST_HERO;
	public static float ALT_RIFT_BOSS_ROOM_TIME_MUTIPLY; // Time multiplier for boss room
	
	public static boolean ALT_ITEM_SKILLS_NOT_INFLUENCED;
	public static boolean ALT_MANA_POTIONS;
	public static int ALT_AUTOCHAT_DELAY;
	public static boolean ALT_SPECIAL_PETS_FOR_ALL;
	public static int ALT_INVENTORY_MAXIMUM_PET;
	
	public static int MAX_PERSONAL_FAME_POINTS;
	public static int FORTRESS_ZONE_FAME_TASK_FREQUENCY;
	public static int FORTRESS_ZONE_FAME_AQUIRE_POINTS;
	public static int CASTLE_ZONE_FAME_TASK_FREQUENCY;
	public static int CASTLE_ZONE_FAME_AQUIRE_POINTS;
	
	public static boolean ALT_TAX_CHANGE_DELAYED;
	public static boolean ALT_CCH_REPUTATION;
	public static boolean ALT_SHOW_FULL_HENNA_LIST;
	public static boolean ALT_SHOW_RESTART_TOWN;
	public static boolean ALT_AUTO_FISHING_SHOT;
	public static boolean ALT_ENABLE_DIMENSIONAL_MERCHANTS;
	public static boolean ALT_MONSTER_HAVE_ENCHANTED_WEAPONS;
	
	public static boolean ALT_KEEP_ITEM_BUFFS;
	
	public static boolean ALLOW_NAIA_MULTY_PARTY_INVASION;
	public static int ALT_NAIA_ROOM_DURATION;
	
	// *******************************************************************************************
	// *******************************************************************************************
	// *******************************************************************************************
	private static final class AltConfig extends ConfigPropertiesLoader
	{
		@Override
		protected String getName()
		{
			return "altsettings";
		}
		
		@Override
		protected void loadImpl(L2Properties altSettings)
		{
			ALT_DEFAULT_RESTARTTOWN = Integer.parseInt(altSettings.getProperty("AltDefaultRestartTown", "0"));
			ALT_GAME_TIREDNESS = Boolean.parseBoolean(altSettings.getProperty("AltGameTiredness", "false"));
			ALT_GAME_CREATION = Boolean.parseBoolean(altSettings.getProperty("AltGameCreation", "false"));
			ALT_GAME_CREATION_SPEED = Double.parseDouble(altSettings.getProperty("AltGameCreationSpeed", "1"));
			ALT_GAME_CREATION_XP_RATE = Double.parseDouble(altSettings.getProperty("AltGameCreationXpRate", "1"));
			ALT_GAME_CREATION_RARE_XPSP_RATE =
					Double.parseDouble(altSettings.getProperty("AltGameCreationRareXpSpRate", "2"));
			ALT_GAME_CREATION_SP_RATE = Double.parseDouble(altSettings.getProperty("AltGameCreationSpRate", "1"));
			ALT_WEIGHT_LIMIT = Double.parseDouble(altSettings.getProperty("AltWeightLimit", "1."));
			ALT_BLACKSMITH_USE_RECIPES =
					Boolean.parseBoolean(altSettings.getProperty("AltBlacksmithUseRecipes", "true"));
			ALT_MINIMUM_FALL_HEIGHT = Integer.parseInt(altSettings.getProperty("AltMinimumFallHeight", "400"));
			ALT_BUFFS_MAX_AMOUNT = Integer.parseInt(altSettings.getProperty("MaxBuffAmount", "20"));
			ALT_DANCES_SONGS_MAX_AMOUNT = Integer.parseInt(altSettings.getProperty("MaxDanceSongAmount", "12"));
			DANCE_CANCEL_BUFF = Boolean.parseBoolean(altSettings.getProperty("DanceCancelBuff", "false"));
			ALT_GAME_SKILL_LEARN = Boolean.parseBoolean(altSettings.getProperty("AltGameSkillLearn", "false"));
			String cancel = altSettings.getProperty("AltGameCancelByHit", "Cast").trim();
			ALT_GAME_CANCEL_BOW = (cancel.equalsIgnoreCase("bow") || cancel.equalsIgnoreCase("all"));
			ALT_GAME_CANCEL_CAST = (cancel.equalsIgnoreCase("cast") || cancel.equalsIgnoreCase("all"));
			ALT_GAME_SHIELD_BLOCKS = Boolean.parseBoolean(altSettings.getProperty("AltShieldBlocks", "false"));
			ALT_PERFECT_SHLD_BLOCK = Integer.parseInt(altSettings.getProperty("AltPerfectShieldBlockRate", "10"));
			ALT_ITEM_SKILLS_NOT_INFLUENCED =
					Boolean.parseBoolean(altSettings.getProperty("AltItemSkillsNotInfluenced", "false"));
			ALT_GAME_DELEVEL = Boolean.parseBoolean(altSettings.getProperty("Delevel", "true"));
			ALT_GAME_MAGICFAILURES = Boolean.parseBoolean(altSettings.getProperty("MagicFailures", "false"));
			ALT_MOB_AGGRO_IN_PEACEZONE = Boolean.parseBoolean(altSettings.getProperty("AltMobAgroInPeaceZone", "true"));
			ALT_ATTACKABLE_NPCS = Boolean.parseBoolean(altSettings.getProperty("AltAttackableNpcs", "True"));
			ALT_INSTANT_KILL_EFFECT_2 = Float.parseFloat(altSettings.getProperty("InstantKillEffect2", "2"));
			ALT_DAGGER_FORMULA = Boolean.parseBoolean(altSettings.getProperty("AltGameDaggerFormula", "false"));
			ALT_DAGGER_RATE = Integer.parseInt(altSettings.getProperty("AltCancelRate", "85"));
			ALT_DAGGER_FAIL_RATE = Integer.parseInt(altSettings.getProperty("AltFailRate", "15"));
			ALT_DAGGER_RATE_BEHIND = Integer.parseInt(altSettings.getProperty("AltSuccessRateBehind", "20"));
			ALT_DAGGER_RATE_FRONT = Integer.parseInt(altSettings.getProperty("AltSuccessRateFront", "5"));
			ALT_ATTACK_DELAY = Float.parseFloat(altSettings.getProperty("AltAttackDelay", "1.00"));
			ALT_GAME_EXPONENT_XP = Float.parseFloat(altSettings.getProperty("AltGameExponentXp", "0."));
			ALT_GAME_EXPONENT_SP = Float.parseFloat(altSettings.getProperty("AltGameExponentSp", "0."));
			
			ALT_SPAWN_WYVERN_MANAGER = Boolean.parseBoolean(altSettings.getProperty("SpawnWyvernManager", "True"));
			ALT_MANAGER_CRYSTAL_COUNT = Integer.parseInt(altSettings.getProperty("ManagerCrystalCount", "25"));
			ALT_SPAWN_CLASS_MASTER = Boolean.parseBoolean(altSettings.getProperty("SpawnClassMaster", "False"));
			ALT_CLASS_MASTER_STRIDER_UPDATE =
					Boolean.parseBoolean(altSettings.getProperty("ClassMasterUpdateStrider", "False"));
			if (!altSettings.getProperty("ConfigClassMaster").trim().equalsIgnoreCase("False"))
				ALT_CLASS_MASTER_SETTINGS_LINE = altSettings.getProperty("ConfigClassMaster");
			
			ALT_CLASS_MASTER_SETTINGS = new ClassMasterSettings(ALT_CLASS_MASTER_SETTINGS_LINE);
			
			ALT_L2J_CLASS_MASTER = Boolean.parseBoolean(altSettings.getProperty("L2JClassMaster", "False"));
			ALT_CLASS_MASTER_ENTIRE_TREE =
					Boolean.parseBoolean(altSettings.getProperty("ClassMasterEntireTree", "False"));
			ALT_CLASS_MASTER_TUTORIAL = Boolean.parseBoolean(altSettings.getProperty("AltClassMaster", "False"));
			
			ALT_GAME_FREIGHTS = Boolean.parseBoolean(altSettings.getProperty("AltGameFreights", "false"));
			ALT_GAME_FREIGHT_PRICE = Integer.parseInt(altSettings.getProperty("AltGameFreightPrice", "1000"));
			ALT_REMOVE_CASTLE_CIRCLETS = Boolean.parseBoolean(altSettings.getProperty("RemoveCastleCirclets", "true"));
			ALT_ONLY_CLANLEADER_CAN_SIT_ON_THRONE =
					Boolean.parseBoolean(altSettings.getProperty("AltOnlyClanleaderCanSitOnThrone", "false"));
			ALT_PARTY_RANGE = Integer.parseInt(altSettings.getProperty("AltPartyRange", "1600"));
			ALT_PARTY_RANGE2 = Integer.parseInt(altSettings.getProperty("AltPartyRange2", "1400"));
			ALT_CHANCE_BREAK = Integer.parseInt(altSettings.getProperty("ChanceToBreak", "10"));
			ALT_CHANCE_LEVEL = Integer.parseInt(altSettings.getProperty("ChanceToLevel", "32"));
			ALT_IS_CRAFTING_ENABLED = Boolean.parseBoolean(altSettings.getProperty("CraftingEnabled", "true"));
			ALT_FAME_FOR_DEAD_PLAYERS = Boolean.parseBoolean(altSettings.getProperty("FameForDeadPlayers", "true"));
			//ALT_FAIL_FAKEDEATH = Boolean.parseBoolean(altSettings.getProperty("FailFakeDeath", "true"));
			ALT_FLYING_WYVERN_IN_SIEGE =
					Boolean.parseBoolean(altSettings.getProperty("AltFlyingWyvernInSiege", "false"));
			ALT_SP_BOOK_NEEDED = Boolean.parseBoolean(altSettings.getProperty("SpBookNeeded", "false"));
			ALT_LIFE_CRYSTAL_NEEDED = Boolean.parseBoolean(altSettings.getProperty("LifeCrystalNeeded", "true"));
			ALT_ES_SP_BOOK_NEEDED = Boolean.parseBoolean(altSettings.getProperty("EnchantSkillSpBookNeeded", "true"));
			DIVINE_SP_BOOK_NEEDED =
					Boolean.parseBoolean(altSettings.getProperty("DivineInspirationSpBookNeeded", "true"));
			ALT_AUTO_LOOT = Boolean.parseBoolean(altSettings.getProperty("AutoLoot", "true"));
			ALT_AUTO_LOOT_RAID = Boolean.parseBoolean(altSettings.getProperty("AutoLootRaid", "true"));
			ALT_AUTO_LOOT_ADENA = Boolean.parseBoolean(altSettings.getProperty("AutoLootAdena", "true"));
			ALT_AUTO_LOOT_HERBS = Boolean.parseBoolean(altSettings.getProperty("AutoLootHerbs", "true"));
			ALT_GAME_KARMA_PLAYER_CAN_BE_KILLED_IN_PEACEZONE =
					Boolean.parseBoolean(altSettings.getProperty("AltKarmaPlayerCanBeKilledInPeaceZone", "false"));
			ALT_GAME_KARMA_PLAYER_CAN_SHOP =
					Boolean.parseBoolean(altSettings.getProperty("AltKarmaPlayerCanShop", "true"));
			ALT_GAME_KARMA_PLAYER_CAN_USE_GK =
					Boolean.parseBoolean(altSettings.getProperty("AltKarmaPlayerCanUseGK", "false"));
			ALT_GAME_KARMA_PLAYER_CAN_TELEPORT =
					Boolean.parseBoolean(altSettings.getProperty("AltKarmaPlayerCanTeleport", "true"));
			ALT_GAME_KARMA_PLAYER_CAN_TRADE =
					Boolean.parseBoolean(altSettings.getProperty("AltKarmaPlayerCanTrade", "true"));
			ALT_GAME_KARMA_PLAYER_CAN_USE_WAREHOUSE =
					Boolean.parseBoolean(altSettings.getProperty("AltKarmaPlayerCanUseWareHouse", "true"));
			ALT_PLAYER_PROTECTION_LEVEL = Integer.parseInt(altSettings.getProperty("AltPlayerProtectionLevel", "0"));
			ALT_GAME_FREE_TELEPORT = Boolean.parseBoolean(altSettings.getProperty("AltFreeTeleporting", "False"));
			ALT_RECOMMEND = Boolean.parseBoolean(altSettings.getProperty("AltRecommend", "False"));
			ALT_GAME_SUBCLASS_WITHOUT_QUESTS =
					Boolean.parseBoolean(altSettings.getProperty("AltSubClassWithoutQuests", "False"));
			ALT_GAME_SUBCLASS_EVERYWHERE =
					Boolean.parseBoolean(altSettings.getProperty("AltSubclassEverywhere", "False"));
			ALT_MAX_SUBCLASS = Integer.parseInt(altSettings.getProperty("MaxSubclass", "3"));
			ALT_MAX_SUBCLASS_LEVEL = Byte.parseByte(altSettings.getProperty("MaxSubclassLevel", "80"));
			ALT_GAME_VIEWNPC = Boolean.parseBoolean(altSettings.getProperty("AltGameViewNpc", "False"));
			//ALT_GAME_NEW_CHAR_ALWAYS_IS_NEWBIE = Boolean.parseBoolean(altSettings.getProperty("AltNewCharAlwaysIsNewbie", "False"));
			ALT_MEMBERS_CAN_WITHDRAW_FROM_CLANWH =
					Boolean.parseBoolean(altSettings.getProperty("AltMembersCanWithdrawFromClanWH", "False"));
			ALT_CASTLE_SHIELD = Boolean.parseBoolean(altSettings.getProperty("CastleShieldRestriction", "True"));
			ALT_CLANHALL_SHIELD = Boolean.parseBoolean(altSettings.getProperty("ClanHallShieldRestriction", "True"));
			ALT_APELLA_ARMORS = Boolean.parseBoolean(altSettings.getProperty("ApellaArmorsRestriction", "True"));
			ALT_OATH_ARMORS = Boolean.parseBoolean(altSettings.getProperty("OathArmorsRestriction", "True"));
			ALT_CASTLE_CROWN = Boolean.parseBoolean(altSettings.getProperty("CastleLordsCrownRestriction", "True"));
			ALT_CASTLE_CIRCLETS = Boolean.parseBoolean(altSettings.getProperty("CastleCircletsRestriction", "True"));
			ALT_DWARF_RECIPE_LIMIT = Integer.parseInt(altSettings.getProperty("DwarfRecipeLimit", "50"));
			ALT_COMMON_RECIPE_LIMIT = Integer.parseInt(altSettings.getProperty("CommonRecipeLimit", "50"));
			
			ALT_STRICT_HERO_SYSTEM = Boolean.parseBoolean(altSettings.getProperty("StrictHeroSystem", "True"));
			ALT_STRICT_SEVENSIGNS = Boolean.parseBoolean(altSettings.getProperty("StrictSevenSigns", "True"));
			
			ALT_BUFF_TIME = Integer.parseInt(altSettings.getProperty("AltBuffTime", "1"));
			ALT_DANCE_TIME = Integer.parseInt(altSettings.getProperty("AltDanceTime", "1"));
			ALT_DANCE_MP_CONSUME = Boolean.parseBoolean(altSettings.getProperty("AltDanceMpConsume", "false"));
			ALT_SPAWN_SIEGE_GUARD = Boolean.parseBoolean(altSettings.getProperty("SpawnSiegeGuard", "true"));
			ALT_AUTO_LEARN_SKILLS = Boolean.parseBoolean(altSettings.getProperty("AutoLearnSkills", "false"));
			ALT_AUTO_LEARN_DIVINE_INSPIRATION =
					Boolean.parseBoolean(altSettings.getProperty("AutoLearnDivineInspiration", "false"));
			ALT_MAX_PATK_SPEED = Integer.parseInt(altSettings.getProperty("MaxPAtkSpeed", "1500"));
			ALT_MAX_MATK_SPEED = Integer.parseInt(altSettings.getProperty("MaxMAtkSpeed", "1999"));
			ALT_MAGES_PHYSICAL_DAMAGE_MULTI = Float.parseFloat(altSettings.getProperty("AltPDamageMages", "1.00"));
			ALT_MAGES_MAGICAL_DAMAGE_MULTI = Float.parseFloat(altSettings.getProperty("AltMDamageMages", "1.00"));
			ALT_FIGHTERS_PHYSICAL_DAMAGE_MULTI =
					Float.parseFloat(altSettings.getProperty("AltPDamageFighters", "1.00"));
			ALT_FIGHTERS_MAGICAL_DAMAGE_MULTI = Float.parseFloat(altSettings.getProperty("AltMDamageFighters", "1.00"));
			ALT_PETS_PHYSICAL_DAMAGE_MULTI = Float.parseFloat(altSettings.getProperty("AltPDamagePets", "1.00"));
			ALT_PETS_MAGICAL_DAMAGE_MULTI = Float.parseFloat(altSettings.getProperty("AltMDamagePets", "1.00"));
			ALT_NPC_PHYSICAL_DAMAGE_MULTI = Float.parseFloat(altSettings.getProperty("AltPDamageNpc", "1.00"));
			ALT_NPC_MAGICAL_DAMAGE_MULTI = Float.parseFloat(altSettings.getProperty("AltMDamageNpc", "1.00"));
			ALT_BUFFER_HATE = Integer.parseInt(altSettings.getProperty("BufferHate", "4"));
			ALT_GRADE_PENALTY = Boolean.parseBoolean(altSettings.getProperty("GradePenalty", "true"));
			ALT_URN_TEMP_FAIL = Integer.parseInt(altSettings.getProperty("UrnTempFail", "10"));
			ALT_DISABLE_RAIDBOSS_PETRIFICATION =
					Boolean.parseBoolean(altSettings.getProperty("DisableRaidBossPetrification", "false"));
			ALT_PCRITICAL_CAP = Integer.parseInt(altSettings.getProperty("AltPCriticalCap", "500"));
			ALT_MCRITICAL_CAP = Integer.parseInt(altSettings.getProperty("AltMCriticalCap", "200"));
			ALT_MAX_EVASION = Integer.parseInt(altSettings.getProperty("MaxEvasion", "200"));
			ALT_MAX_RUN_SPEED = Integer.parseInt(altSettings.getProperty("MaxRunSpeed", "250"));
			ALT_MCRIT_RATE = Float.parseFloat(altSettings.getProperty("AltMCritRate", "3.0"));
			ALT_MCRIT_PVP_RATE = Float.parseFloat(altSettings.getProperty("AltMCritPvpRate", "2.5"));
			
			ALT_POLEARM_DAMAGE_MULTI = Double.parseDouble(altSettings.getProperty("AltPolearmDamageMulti", "1.0"));
			ALT_POLEARM_VAMPIRIC_MULTI = Double.parseDouble(altSettings.getProperty("AltPolearmVampiricMulti", "0.5"));
			
			ALT_OLY_START_TIME = Integer.parseInt(altSettings.getProperty("AltOlyStartTime", "18"));
			ALT_OLY_MIN = Integer.parseInt(altSettings.getProperty("AltOlyMin", "00"));
			ALT_OLY_CPERIOD = Integer.parseInt(altSettings.getProperty("AltOlyCPeriod", "21600000"));
			ALT_OLY_BATTLE = Integer.parseInt(altSettings.getProperty("AltOlyBattle", "360000"));
			ALT_OLY_WPERIOD = Integer.parseInt(altSettings.getProperty("AltOlyWperiod", "604800000"));
			ALT_OLY_VPERIOD = Integer.parseInt(altSettings.getProperty("AltOlyVperiod", "86400000"));
			ALT_OLY_SAME_IP = Boolean.parseBoolean(altSettings.getProperty("AltOlySameIp", "true"));
			ALT_OLY_CLASSED = Integer.parseInt(altSettings.getProperty("AltOlyClassedParticipants", "5"));
			ALT_OLY_NONCLASSED = Integer.parseInt(altSettings.getProperty("AltOlyNonClassedParticipants", "9"));
			ALT_OLY_REMOVE_CUBICS = Boolean.parseBoolean(altSettings.getProperty("AltOlyRemoveCubics", "false"));
			ALT_OLY_BATTLE_REWARD_ITEM = Integer.parseInt(altSettings.getProperty("AltOlyBattleRewItem", "13722"));
			ALT_OLY_CLASSED_RITEM_C = Integer.parseInt(altSettings.getProperty("AltOlyClassedRewItemCount", "50"));
			ALT_OLY_NONCLASSED_RITEM_C =
					Integer.parseInt(altSettings.getProperty("AltOlyNonClassedRewItemCount", "30"));
			ALT_OLY_REG_DISPLAY = Integer.parseInt(altSettings.getProperty("AltOlyRegistrationDisplayNumber", "100"));
			ALT_OLY_GP_PER_POINT = Integer.parseInt(altSettings.getProperty("AltOlyGPPerPoint", "1000"));
			ALT_OLY_HERO_POINTS = Integer.parseInt(altSettings.getProperty("AltOlyHeroPoints", "180"));
			ALT_OLY_RANK1_POINTS = Integer.parseInt(altSettings.getProperty("AltOlyRank1Points", "120"));
			ALT_OLY_RANK2_POINTS = Integer.parseInt(altSettings.getProperty("AltOlyRank2Points", "80"));
			ALT_OLY_RANK3_POINTS = Integer.parseInt(altSettings.getProperty("AltOlyRank3Points", "55"));
			ALT_OLY_RANK4_POINTS = Integer.parseInt(altSettings.getProperty("AltOlyRank4Points", "35"));
			ALT_OLY_RANK5_POINTS = Integer.parseInt(altSettings.getProperty("AltOlyRank5Points", "20"));
			ALT_OLY_MAX_POINTS = Integer.parseInt(altSettings.getProperty("AltOlyMaxPoints", "10"));
			ALT_OLY_LOG_FIGHTS = Boolean.parseBoolean(altSettings.getProperty("AlyOlyLogFights", "false"));
			ALT_OLY_SHOW_MONTHLY_WINNERS =
					Boolean.parseBoolean(altSettings.getProperty("AltOlyShowMonthlyWinners", "true"));
			ALT_OLY_ANNOUNCE_GAMES = Boolean.parseBoolean(altSettings.getProperty("AltOlyAnnounceGames", "true"));
			ALT_LIST_OLY_RESTRICTED_ITEMS.clear();
			for (String id : altSettings.getProperty("AltOlyRestrictedItems", "0").split(","))
			{
				ALT_LIST_OLY_RESTRICTED_ITEMS.add(Integer.parseInt(id));
			}
			ALT_OLY_ENCHANT_LIMIT = Integer.parseInt(altSettings.getProperty("AltOlyEnchantLimit", "-1"));
			
			ALT_MANOR_REFRESH_TIME = Integer.parseInt(altSettings.getProperty("AltManorRefreshTime", "20"));
			ALT_MANOR_REFRESH_MIN = Integer.parseInt(altSettings.getProperty("AltManorRefreshMin", "00"));
			ALT_MANOR_APPROVE_TIME = Integer.parseInt(altSettings.getProperty("AltManorApproveTime", "6"));
			ALT_MANOR_APPROVE_MIN = Integer.parseInt(altSettings.getProperty("AltManorApproveMin", "00"));
			ALT_MANOR_MAINTENANCE_PERIOD =
					Integer.parseInt(altSettings.getProperty("AltManorMaintenancePeriod", "360000"));
			ALT_MANOR_SAVE_ALL_ACTIONS =
					Boolean.parseBoolean(altSettings.getProperty("AltManorSaveAllActions", "false"));
			ALT_MANOR_SAVE_PERIOD_RATE = Integer.parseInt(altSettings.getProperty("AltManorSavePeriodRate", "2"));
			
			ALT_CRAFT_ALLOW_CRAFT = Boolean.parseBoolean(altSettings.getProperty("CraftManagerDwarvenCraft", "True"));
			ALT_CRAFT_ALLOW_COMMON = Boolean.parseBoolean(altSettings.getProperty("CraftManagerCommonCraft", "False"));
			ALT_CRAFT_ALLOW_CRYSTALLIZE =
					Boolean.parseBoolean(altSettings.getProperty("CraftManagerCrystallize", "True"));
			ALT_CRAFT_PRICE = Float.parseFloat(altSettings.getProperty("CraftManagerPriceMultiplier", "0.1"));
			ALT_CRAFT_DEFAULT_PRICE = Integer.parseInt(altSettings.getProperty("CraftManagerDefaultPrice", "50000"));
			
			ALT_TIME_IN_A_DAY_OF_OPEN_A_DOOR = Integer.parseInt(altSettings.getProperty("TimeInADayOfOpenADoor", "0"));
			ALT_TIME_OF_OPENING_A_DOOR = Integer.parseInt(altSettings.getProperty("TimeOfOpeningADoor", "5"));
			
			ALT_TIMELIMITOFINVADE = Integer.parseInt(altSettings.getProperty("TimeLimitOfInvade", "1800000"));
			
			ALT_GAME_SUMMON_PENALTY_RATE = Float.parseFloat(altSettings.getProperty("AltSummonPenaltyRate", "1"));
			
			// Dimensional Rift Config
			ALT_RIFT_MIN_PARTY_SIZE = Integer.parseInt(altSettings.getProperty("RiftMinPartySize", "5"));
			ALT_RIFT_MAX_JUMPS = Integer.parseInt(altSettings.getProperty("MaxRiftJumps", "4"));
			ALT_RIFT_SPAWN_DELAY = Integer.parseInt(altSettings.getProperty("RiftSpawnDelay", "10000"));
			ALT_RIFT_AUTO_JUMPS_TIME_MIN = Integer.parseInt(altSettings.getProperty("AutoJumpsDelayMin", "480"));
			ALT_RIFT_AUTO_JUMPS_TIME_MAX = Integer.parseInt(altSettings.getProperty("AutoJumpsDelayMax", "600"));
			ALT_RIFT_ENTER_COST_RECRUIT = Integer.parseInt(altSettings.getProperty("RecruitCost", "18"));
			ALT_RIFT_ENTER_COST_SOLDIER = Integer.parseInt(altSettings.getProperty("SoldierCost", "21"));
			ALT_RIFT_ENTER_COST_OFFICER = Integer.parseInt(altSettings.getProperty("OfficerCost", "24"));
			ALT_RIFT_ENTER_COST_CAPTAIN = Integer.parseInt(altSettings.getProperty("CaptainCost", "27"));
			ALT_RIFT_ENTER_COST_COMMANDER = Integer.parseInt(altSettings.getProperty("CommanderCost", "30"));
			ALT_RIFT_ENTER_COST_HERO = Integer.parseInt(altSettings.getProperty("HeroCost", "33"));
			ALT_RIFT_BOSS_ROOM_TIME_MUTIPLY = Float.parseFloat(altSettings.getProperty("BossRoomTimeMultiply", "1.5"));
			
			ALT_FS_TIME_ATTACK = Integer.parseInt(altSettings.getProperty("TimeOfAttack", "50"));
			ALT_FS_TIME_COOLDOWN = Integer.parseInt(altSettings.getProperty("TimeOfCoolDown", "5"));
			ALT_FS_TIME_ENTRY = Integer.parseInt(altSettings.getProperty("TimeOfEntry", "3"));
			ALT_FS_TIME_WARMUP = Integer.parseInt(altSettings.getProperty("TimeOfWarmUp", "2"));
			ALT_FS_PARTY_MEMBER_COUNT = Integer.parseInt(altSettings.getProperty("NumberOfNecessaryPartyMembers", "4"));
			if (ALT_FS_TIME_ATTACK <= 0)
				ALT_FS_TIME_ATTACK = 50;
			if (ALT_FS_TIME_COOLDOWN <= 0)
				ALT_FS_TIME_COOLDOWN = 5;
			if (ALT_FS_TIME_ENTRY <= 0)
				ALT_FS_TIME_ENTRY = 3;
			if (ALT_FS_TIME_ENTRY <= 0)
				ALT_FS_TIME_ENTRY = 3;
			if (ALT_FS_TIME_ENTRY <= 0)
				ALT_FS_TIME_ENTRY = 3;
			
			ALT_MANA_POTIONS = Boolean.parseBoolean(altSettings.getProperty("AllowManaPotions", "false"));
			ALT_AUTOCHAT_DELAY = Integer.parseInt(altSettings.getProperty("AutoChatDelay", "30000"));
			ALT_SPECIAL_PETS_FOR_ALL =
					Boolean.parseBoolean(altSettings.getProperty("EverybodyCanUseSpecPets", "false"));
			ALT_INVENTORY_MAXIMUM_PET = Integer.parseInt(altSettings.getProperty("MaximumSlotsForPet", "12"));
			
			MAX_PERSONAL_FAME_POINTS = Integer.parseInt(altSettings.getProperty("MaxPersonalFamePoints", "65535"));
			FORTRESS_ZONE_FAME_TASK_FREQUENCY =
					Integer.parseInt(altSettings.getProperty("FortressZoneFameTaskFrequency", "300"));
			FORTRESS_ZONE_FAME_AQUIRE_POINTS =
					Integer.parseInt(altSettings.getProperty("FortressZoneFameAquirePoints", "31"));
			CASTLE_ZONE_FAME_TASK_FREQUENCY =
					Integer.parseInt(altSettings.getProperty("CastleZoneFameTaskFrequency", "300"));
			CASTLE_ZONE_FAME_AQUIRE_POINTS =
					Integer.parseInt(altSettings.getProperty("CastleZoneFameAquirePoints", "125"));
			
			ALT_TAX_CHANGE_DELAYED = Boolean.parseBoolean(altSettings.getProperty("CastleTaxChangeDelayed", "true"));
			ALT_CCH_REPUTATION = Boolean.parseBoolean(altSettings.getProperty("AltClanHallSiegeReputation", "false"));
			ALT_SHOW_FULL_HENNA_LIST = Boolean.parseBoolean(altSettings.getProperty("AltShowFullHennaList", "false"));
			ALT_SHOW_RESTART_TOWN = Boolean.parseBoolean(altSettings.getProperty("AltShowRestartTown", "false"));
			ALT_AUTO_FISHING_SHOT = Boolean.parseBoolean(altSettings.getProperty("AltAllowAutoFishShot", "false"));
			ALT_ENABLE_DIMENSIONAL_MERCHANTS =
					Boolean.parseBoolean(altSettings.getProperty("AltEnableDimensionalMerchants", "false"));
			ALT_MONSTER_HAVE_ENCHANTED_WEAPONS =
					Boolean.parseBoolean(altSettings.getProperty("AltEnableEnchantedWeaponOnMonsters", "false"));
			
			ALT_KEEP_ITEM_BUFFS = Boolean.parseBoolean(altSettings.getProperty("AltKeepItemBuffs", "false"));
			
			ALLOW_NAIA_MULTY_PARTY_INVASION =
					Boolean.parseBoolean(altSettings.getProperty("AllowNaiaMultiPartyInvasion", "false"));
			ALT_NAIA_ROOM_DURATION = Integer.parseInt(altSettings.getProperty("AltNaiaRoomDuration", "5"));
		}
	}
	
	// *******************************************************************************************
	public static final String GM_ACCESS_FILE = "./config/GMAccess.properties";
	// *******************************************************************************************
	public static int GM_ACCESSLEVEL;
	public static int GM_MIN; // General GM Minimal AccessLevel
	public static int GM_ALTG_MIN_LEVEL; // Minimum privileges level for a GM to do Alt+G
	//	public static int			GM_ANNOUNCE;										// General GM AccessLevel to change announcements
	//	public static int			GM_BAN;											// General GM AccessLevel can /ban /unban
	//	public static int			GM_JAIL;											// General GM AccessLevel can /jail /unjail
	//	public static int			GM_BAN_CHAT;										// General GM AccessLevel can /ban /unban for chat
	//	public static int			GM_CAMERA;
	public static int GM_CREATE_ITEM; // General GM AccessLevel can /create_item and /gmshop
	public static int GM_FREE_SHOP; // General GM AccessLevel can shop for free
	//	public static int			GM_ENCHANT;										// General GM AccessLevel can enchant armor
	//	public static int			GM_DELETE;											// General GM AccessLevel can /delete
	//	public static int			GM_KICK;											// General GM AccessLevel can /kick /disconnect
	//	public static int			GM_MENU;											// General GM AccessLevel for access to GMMenu
	//	public static int			GM_GODMODE;										// General GM AccessLevel to use god mode command
	//	public static int			GM_CHAR_EDIT;										// General GM AccessLevel with character edit rights
	//	public static int			GM_CHAR_EDIT_OTHER;								// General GM AccessLevel with edit rights for other characters
	//	public static int			GM_CHAR_VIEW;										// General GM AccessLevel with character view rights
	public static int GM_CHAR_VIEW_INFO; // General GM AccessLevel with character view rights ALT+G
	public static int GM_CHAR_INVENTORY; // General GM AccessLevel with character view inventory rights ALT+G
	public static int GM_CHAR_CLAN_VIEW; // General GM AccessLevel with character view clan info rights ALT+G
	public static int GM_CHAR_VIEW_QUEST; // General GM AccessLevel with character view quest rights ALT+G
	public static int GM_CHAR_VIEW_SKILL; // General GM AccessLevel with character view skill rights ALT+G
	public static int GM_CHAR_VIEW_WAREHOUSE; // General GM AccessLevel with character warehouse view rights ALT+G
	//	public static int			GM_NPC_EDIT;										// General GM AccessLevel with NPC edit rights
	//	public static int			GM_NPC_VIEW;
	//	public static int			GM_TELEPORT;										// General GM AccessLevel to teleport to any location
	//	public static int			GM_TELEPORT_OTHER;									// General GM AccessLevel to teleport character to any location
	//	public static int			GM_RESTART;										// General GM AccessLevel to restart server
	//	public static int			GM_MONSTERRACE;									// General GM AccessLevel for MonsterRace
	//	public static int			GM_RIDER;											// General GM AccessLevel to ride Wyvern
	public static int GM_ESCAPE; // General GM AccessLevel to unstuck without 5min delay
	public static int GM_FIXED; // General GM AccessLevel to resurect fixed after death
	//	public static int			GM_CREATE_NODES;									// General GM AccessLevel to create Path Nodes
	//	public static int			GM_DOOR;											// General GM AccessLevel to close/open Doors
	//	public static int			GM_RES;											// General GM AccessLevel with Resurrection rights
	public static int GM_PEACEATTACK; // General GM AccessLevel to attack in the peace zone
	//	public static int			GM_HEAL;											// General GM AccessLevel to heal
	//	public static int			GM_IRC;											// General GM AccessLevel to IRC commands
	//	public static int			GM_UNBLOCK;										// General GM AccessLevel to unblock IPs detected as hack IPs
	//	public static int			GM_CACHE;											// General GM AccessLevel to use Cache commands
	//	public static int			GM_TALK_BLOCK;										// General GM AccessLevel to use test&st commands
	//	public static int			GM_TEST;
	//	public static int			GM_INSTANCE;
	public static boolean GM_DISABLE_TRANSACTION; // Disable transaction on AccessLevel
	public static int GM_TRANSACTION_MIN;
	public static int GM_TRANSACTION_MAX;
	public static int GM_CAN_GIVE_DAMAGE; // Minimum level to allow a GM giving damage
	public static int GM_DONT_TAKE_EXPSP; // Minimum level to don't give Exp/Sp in party
	public static int GM_DONT_TAKE_AGGRO; // Minimum level to don't take aggro
	public static boolean GM_NAME_COLOR_ENABLED; // GM name color
	public static boolean GM_TITLE_COLOR_ENABLED;
	public static int GM_NAME_COLOR;
	public static int GM_TITLE_COLOR;
	public static int ADMIN_NAME_COLOR;
	public static int ADMIN_TITLE_COLOR;
	public static boolean SHOW_GM_LOGIN; // GM Announce at login
	//	public static boolean		HIDE_GM_STATUS;
	public static boolean GM_STARTUP_INVISIBLE;
	public static boolean GM_STARTUP_SILENCE;
	public static boolean GM_STARTUP_AUTO_LIST;
	public static String GM_ADMIN_MENU_STYLE;
	public static int STANDARD_RESPAWN_DELAY; // Standard Respawn Delay
	public static boolean GM_HERO_AURA; // Place an aura around the GM ?
	public static boolean GM_STARTUP_INVULNERABLE; // Set the GM invulnerable at startup ?
	public static boolean GM_ANNOUNCER_NAME;
	public static boolean GM_ITEM_RESTRICTION;
	public static boolean GM_SKILL_RESTRICTION;
	public static boolean GM_TRADE_RESTRICTED_ITEMS;
	public static boolean GM_RESTART_FIGHTING;
	public static boolean GM_ALLOW_CHAT_INVISIBLE;
	public static boolean GM_NAME_HAS_BRACELETS;
	public static boolean GM_EVERYBODY_HAS_ADMIN_RIGHTS;
	
	// *******************************************************************************************
	private static final class GMAccessConfig extends ConfigPropertiesLoader
	{
		@Override
		protected String getName()
		{
			return "GMAccess";
		}
		
		@Override
		protected void loadImpl(L2Properties gmSettings)
		{
			GM_ACCESSLEVEL = Integer.parseInt(gmSettings.getProperty("GMAccessLevel", "100"));
			GM_MIN = Integer.parseInt(gmSettings.getProperty("GMMinLevel", "100"));
			GM_ALTG_MIN_LEVEL = Integer.parseInt(gmSettings.getProperty("GMCanAltG", "100"));
			//			GM_ANNOUNCE = Integer.parseInt(gmSettings.getProperty("GMCanAnnounce", "100"));
			//			GM_BAN = Integer.parseInt(gmSettings.getProperty("GMCanBan", "100"));
			//			GM_JAIL = Integer.parseInt(gmSettings.getProperty("GMCanJail", "100"));
			//			GM_BAN_CHAT = Integer.parseInt(gmSettings.getProperty("GMCanBanChat", "100"));
			//			GM_CAMERA = Integer.parseInt(gmSettings.getProperty("GMCamera", "100"));
			GM_CREATE_ITEM = Integer.parseInt(gmSettings.getProperty("GMCanShop", "100"));
			GM_FREE_SHOP = Integer.parseInt(gmSettings.getProperty("GMCanBuyFree", "100"));
			//			GM_DELETE = Integer.parseInt(gmSettings.getProperty("GMCanDelete", "100"));
			//			GM_KICK = Integer.parseInt(gmSettings.getProperty("GMCanKick", "100"));
			//			GM_MENU = Integer.parseInt(gmSettings.getProperty("GMMenu", "100"));
			//			GM_GODMODE = Integer.parseInt(gmSettings.getProperty("GMGodMode", "100"));
			//			GM_CHAR_EDIT = Integer.parseInt(gmSettings.getProperty("GMCanEditChar", "100"));
			//			GM_CHAR_EDIT_OTHER = Integer.parseInt(gmSettings.getProperty("GMCanEditCharOther", "100"));
			//			GM_CHAR_VIEW = Integer.parseInt(gmSettings.getProperty("GMCanViewChar", "100"));
			GM_CHAR_VIEW_INFO = Integer.parseInt(gmSettings.getProperty("GMViewCharacterInfo", "100"));
			GM_CHAR_INVENTORY = Integer.parseInt(gmSettings.getProperty("GMViewItemList", "100"));
			GM_CHAR_CLAN_VIEW = Integer.parseInt(gmSettings.getProperty("GMViewClanInfo", "100"));
			GM_CHAR_VIEW_QUEST = Integer.parseInt(gmSettings.getProperty("GMViewQuestList", "100"));
			GM_CHAR_VIEW_SKILL = Integer.parseInt(gmSettings.getProperty("GMViewSkillInfo", "100"));
			GM_CHAR_VIEW_WAREHOUSE = Integer.parseInt(gmSettings.getProperty("GMViewWarehouseWithdrawList", "100"));
			//			GM_NPC_EDIT = Integer.parseInt(gmSettings.getProperty("GMCanEditNPC", "100"));
			//			GM_NPC_VIEW = Integer.parseInt(gmSettings.getProperty("GMCanViewNPC", "100"));
			//			GM_TELEPORT = Integer.parseInt(gmSettings.getProperty("GMCanTeleport", "100"));
			//			GM_TELEPORT_OTHER = Integer.parseInt(gmSettings.getProperty("GMCanTeleportOther", "100"));
			//			GM_RESTART = Integer.parseInt(gmSettings.getProperty("GMCanRestart", "100"));
			//			GM_MONSTERRACE = Integer.parseInt(gmSettings.getProperty("GMMonsterRace", "100"));
			//			GM_RIDER = Integer.parseInt(gmSettings.getProperty("GMRider", "100"));
			GM_ESCAPE = Integer.parseInt(gmSettings.getProperty("GMFastUnstuck", "100"));
			GM_FIXED = Integer.parseInt(gmSettings.getProperty("GMResurectFixed", "100"));
			//			GM_CREATE_NODES = Integer.parseInt(gmSettings.getProperty("GMCreateNodes", "100"));
			//			GM_DOOR = Integer.parseInt(gmSettings.getProperty("GMDoor", "100"));
			//			GM_RES = Integer.parseInt(gmSettings.getProperty("GMRes", "100"));
			GM_PEACEATTACK = Integer.parseInt(gmSettings.getProperty("GMPeaceAttack", "100"));
			//			GM_HEAL = Integer.parseInt(gmSettings.getProperty("GMHeal", "100"));
			//			GM_ENCHANT = Integer.parseInt(gmSettings.getProperty("GMEnchant", "100"));
			//			GM_UNBLOCK = Integer.parseInt(gmSettings.getProperty("GMUnblock", "100"));
			//			GM_CACHE = Integer.parseInt(gmSettings.getProperty("GMCache", "100"));
			//			GM_TALK_BLOCK = Integer.parseInt(gmSettings.getProperty("GMTalkBlock", "100"));
			//			GM_TEST = Integer.parseInt(gmSettings.getProperty("GMTest", "100"));
			//			GM_INSTANCE = Integer.parseInt(gmSettings.getProperty("GMInstance", "100"));
			GM_STARTUP_AUTO_LIST = Boolean.parseBoolean(gmSettings.getProperty("GMStartupAutoList", "True"));
			GM_ADMIN_MENU_STYLE = gmSettings.getProperty("GMAdminMenuStyle", "modern");
			GM_HERO_AURA = Boolean.parseBoolean(gmSettings.getProperty("GMHeroAura", "True"));
			GM_STARTUP_INVULNERABLE = Boolean.parseBoolean(gmSettings.getProperty("GMStartupInvulnerable", "True"));
			STANDARD_RESPAWN_DELAY = Integer.parseInt(gmSettings.getProperty("StandardRespawnDelay", "0"));
			GM_ANNOUNCER_NAME = Boolean.parseBoolean(gmSettings.getProperty("GMShowAnnouncerName", "false"));
			
			String gmTrans = gmSettings.getProperty("GMDisableTransaction", "False");
			
			if (!gmTrans.trim().equalsIgnoreCase("false"))
			{
				String[] params = gmTrans.trim().split(",");
				GM_DISABLE_TRANSACTION = true;
				GM_TRANSACTION_MIN = Integer.parseInt(params[0].trim());
				GM_TRANSACTION_MAX = Integer.parseInt(params[1].trim());
			}
			else
			{
				GM_DISABLE_TRANSACTION = false;
			}
			GM_CAN_GIVE_DAMAGE = Integer.parseInt(gmSettings.getProperty("GMCanGiveDamage", "90"));
			GM_DONT_TAKE_AGGRO = Integer.parseInt(gmSettings.getProperty("GMDontTakeAggro", "90"));
			GM_DONT_TAKE_EXPSP = Integer.parseInt(gmSettings.getProperty("GMDontGiveExpSp", "90"));
			
			GM_NAME_COLOR_ENABLED = Boolean.parseBoolean(gmSettings.getProperty("GMNameColorEnabled", "True"));
			GM_TITLE_COLOR_ENABLED = Boolean.parseBoolean(gmSettings.getProperty("GMTitleColorEnabled", "True"));
			GM_NAME_COLOR = Integer.decode("0x" + gmSettings.getProperty("GMNameColor", "00FF00"));
			GM_TITLE_COLOR = Integer.decode("0x" + gmSettings.getProperty("GMTitleColor", "00FF00"));
			ADMIN_NAME_COLOR = Integer.decode("0x" + gmSettings.getProperty("AdminNameColor", "00FF00"));
			ADMIN_TITLE_COLOR = Integer.decode("0x" + gmSettings.getProperty("AdminTitleColor", "00FF00"));
			SHOW_GM_LOGIN = Boolean.parseBoolean(gmSettings.getProperty("ShowGMLogin", "false"));
			//			HIDE_GM_STATUS = Boolean.parseBoolean(gmSettings.getProperty("HideGMStatus", "false"));
			GM_STARTUP_INVISIBLE = Boolean.parseBoolean(gmSettings.getProperty("GMStartupInvisible", "True"));
			GM_STARTUP_SILENCE = Boolean.parseBoolean(gmSettings.getProperty("GMStartupSilence", "True"));
			
			GM_ITEM_RESTRICTION = Boolean.parseBoolean(gmSettings.getProperty("GMItemRestriction", "True"));
			GM_SKILL_RESTRICTION = Boolean.parseBoolean(gmSettings.getProperty("GMSkillRestriction", "True"));
			GM_TRADE_RESTRICTED_ITEMS = Boolean.parseBoolean(gmSettings.getProperty("GMTradeRestrictedItems", "False"));
			GM_RESTART_FIGHTING = Boolean.parseBoolean(gmSettings.getProperty("GMRestartFighting", "False"));
			GM_ALLOW_CHAT_INVISIBLE = Boolean.parseBoolean(gmSettings.getProperty("GMChatInvisible", "False"));
			
			GM_NAME_HAS_BRACELETS = Boolean.parseBoolean(gmSettings.getProperty("GmBracelets", "true"));
			
			GM_EVERYBODY_HAS_ADMIN_RIGHTS =
					Boolean.parseBoolean(gmSettings.getProperty("EverybodyHasAdminRights", "false"));
		}
	}
	
	// *******************************************************************************************
	public static final String DATETIME_FILE = "./config/datetime.properties";
	// *******************************************************************************************
	public static boolean DATETIME_SAVECAL;
	public static int DATETIME_SUNRISE;
	public static int DATETIME_SUNSET;
	public static int DATETIME_MULTI;
	public static int DATETIME_MOVE_DELAY;
	
	// *******************************************************************************************
	public static void loadDateTimeConfig()
	{
		_log.info("loading " + DATETIME_FILE);
		try
		{
			L2Properties datetimeSettings = new L2Properties(DATETIME_FILE);
			
			DATETIME_SAVECAL = Boolean.parseBoolean(datetimeSettings.getProperty("SaveDate", "false"));
			DATETIME_SUNSET = Integer.parseInt(datetimeSettings.getProperty("SunSet", "24"));
			DATETIME_SUNRISE = Integer.parseInt(datetimeSettings.getProperty("SunRise", "6"));
			DATETIME_MULTI = Integer.parseInt(datetimeSettings.getProperty("TimeMulti", "6"));
			DATETIME_MOVE_DELAY = Integer.parseInt(datetimeSettings.getProperty("MoveDelay", "2"));
		}
		catch (Exception e)
		{
			_log.error(String.valueOf(e));
			throw new Error("Failed to Load " + DATETIME_FILE + " File.");
		}
	}
	
	// *******************************************************************************************
	public static boolean IS_TELNET_ENABLED; // Is telnet enabled ?
	public static boolean ALT_TELNET; // Use alternative telnet ?
	public static boolean ALT_TELNET_GM_ANNOUNCER_NAME; // Show GM's name behind his announcement ? (only works if AltTelnet = true)
	
	// *******************************************************************************************
	public static void loadTelnetConfig()
	{
		_log.info("loading " + TELNET_FILE);
		try
		{
			L2Properties telnetSettings = new L2Properties(TELNET_FILE);
			
			IS_TELNET_ENABLED = Boolean.parseBoolean(telnetSettings.getProperty("EnableTelnet", "false"));
			ALT_TELNET = Boolean.parseBoolean(telnetSettings.getProperty("AltTelnet", "false"));
			ALT_TELNET_GM_ANNOUNCER_NAME =
					Boolean.parseBoolean(telnetSettings.getProperty("AltTelnetGmAnnouncerName", "false"));
		}
		catch (Exception e)
		{
			_log.error(String.valueOf(e));
			throw new Error("Failed to Load " + TELNET_FILE + " File.");
		}
	}
	
	// *******************************************************************************************
	public static final String SIEGE_CONFIGURATION_FILE = "./config/siege.properties";
	// *******************************************************************************************
	
	public static int SIEGE_MAX_ATTACKER;
	public static int SIEGE_MAX_DEFENDER;
	public static int SIEGE_RESPAWN_DELAY_ATTACKER;
	public static int SIEGE_RESPAWN_DELAY_DEFENDER;
	
	public static int SIEGE_CT_LOSS_PENALTY;
	public static int SIEGE_FLAG_MAX_COUNT;
	public static int SIEGE_CLAN_MIN_LEVEL;
	public static int SIEGE_LENGTH_MINUTES;
	
	public static boolean SIEGE_ONLY_REGISTERED;
	public static boolean SIEGE_GATE_CONTROL;
	public static boolean USE_MISSING_CCH_MESSAGES;
	
	public static int GLUDIO_MAX_MERCENARIES;
	public static int DION_MAX_MERCENARIES;
	public static int GIRAN_MAX_MERCENARIES;
	public static int OREN_MAX_MERCENARIES;
	public static int ADEN_MAX_MERCENARIES;
	public static int INNADRIL_MAX_MERCENARIES;
	public static int GODDARD_MAX_MERCENARIES;
	public static int RUNE_MAX_MERCENARIES;
	public static int SCHUTTGART_MAX_MERCENARIES;
	
	public static final Set<String> CL_SET_SIEGE_TIME_LIST = new L2FastSet<String>();
	public static final Set<Integer> SIEGE_HOUR_LIST_MORNING = new L2FastSet<Integer>();
	public static final Set<Integer> SIEGE_HOUR_LIST_AFTERNOON = new L2FastSet<Integer>();
	
	private static final class SiegeConfig extends ConfigPropertiesLoader
	{
		@Override
		protected String getName()
		{
			return "siege";
		}
		
		@Override
		protected void loadImpl(L2Properties siegeSettings)
		{
			SIEGE_MAX_ATTACKER = Integer.parseInt(siegeSettings.getProperty("AttackerMaxClans", "500"));
			SIEGE_MAX_DEFENDER = Integer.parseInt(siegeSettings.getProperty("DefenderMaxClans", "500"));
			SIEGE_RESPAWN_DELAY_ATTACKER = Integer.parseInt(siegeSettings.getProperty("AttackerRespawn", "0"));
			SIEGE_RESPAWN_DELAY_DEFENDER = Integer.parseInt(siegeSettings.getProperty("DefenderRespawn", "30000"));
			
			SIEGE_CT_LOSS_PENALTY = Integer.parseInt(siegeSettings.getProperty("CTLossPenalty", "60000"));
			SIEGE_FLAG_MAX_COUNT = Integer.parseInt(siegeSettings.getProperty("MaxFlags", "1"));
			SIEGE_CLAN_MIN_LEVEL = Integer.parseInt(siegeSettings.getProperty("SiegeClanMinLevel", "5"));
			SIEGE_LENGTH_MINUTES = Integer.parseInt(siegeSettings.getProperty("SiegeLength", "120"));
			
			SIEGE_ONLY_REGISTERED = Boolean.parseBoolean(siegeSettings.getProperty("OnlyRegistered", "true"));
			SIEGE_GATE_CONTROL = Boolean.parseBoolean(siegeSettings.getProperty("AllowGateControl", "false"));
			USE_MISSING_CCH_MESSAGES = Boolean.parseBoolean(siegeSettings.getProperty("ClanHallSiegeSysMsgs", "false"));
			
			GLUDIO_MAX_MERCENARIES = Integer.parseInt(siegeSettings.getProperty("GludioMaxMercenaries", "100"));
			DION_MAX_MERCENARIES = Integer.parseInt(siegeSettings.getProperty("DionMaxMercenaries", "150"));
			GIRAN_MAX_MERCENARIES = Integer.parseInt(siegeSettings.getProperty("GiranMaxMercenaries", "200"));
			OREN_MAX_MERCENARIES = Integer.parseInt(siegeSettings.getProperty("OrenMaxMercenaries", "300"));
			ADEN_MAX_MERCENARIES = Integer.parseInt(siegeSettings.getProperty("AdenMaxMercenaries", "400"));
			INNADRIL_MAX_MERCENARIES = Integer.parseInt(siegeSettings.getProperty("InnadrilMaxMercenaries", "400"));
			GODDARD_MAX_MERCENARIES = Integer.parseInt(siegeSettings.getProperty("GoddardMaxMercenaries", "400"));
			RUNE_MAX_MERCENARIES = Integer.parseInt(siegeSettings.getProperty("RuneMaxMercenaries", "400"));
			SCHUTTGART_MAX_MERCENARIES = Integer.parseInt(siegeSettings.getProperty("SchuttgartMaxMercenaries", "400"));
			
			CL_SET_SIEGE_TIME_LIST.clear();
			SIEGE_HOUR_LIST_MORNING.clear();
			SIEGE_HOUR_LIST_AFTERNOON.clear();
			String[] sstl = siegeSettings.getProperty("CLSetSiegeTimeList", "").split(",");
			if (sstl.length != 0)
			{
				boolean isHour = false;
				for (String st : sstl)
				{
					if (st.equalsIgnoreCase("day") || st.equalsIgnoreCase("hour") || st.equalsIgnoreCase("minute"))
					{
						if (st.equalsIgnoreCase("hour"))
							isHour = true;
						CL_SET_SIEGE_TIME_LIST.add(st.toLowerCase());
					}
					else
					{
						System.out.println("[CLSetSiegeTimeList]: invalid config property -> CLSetSiegeTimeList \""
								+ st + "\"");
					}
				}
				if (isHour)
				{
					String[] shl = siegeSettings.getProperty("SiegeHourList", "").split(",");
					for (String st : shl)
					{
						if (!st.equalsIgnoreCase(""))
						{
							int val = Integer.valueOf(st);
							if (val > 23 || val < 0)
								System.out.println("[SiegeHourList]: invalid config property -> SiegeHourList \"" + st
										+ "\"");
							else if (val < 12)
								SIEGE_HOUR_LIST_MORNING.add(val);
							else
							{
								val -= 12;
								SIEGE_HOUR_LIST_AFTERNOON.add(val);
							}
						}
					}
					if (Config.SIEGE_HOUR_LIST_AFTERNOON.isEmpty() && Config.SIEGE_HOUR_LIST_AFTERNOON.isEmpty())
					{
						System.out.println("[SiegeHourList]: invalid config property -> SiegeHourList is empty");
						CL_SET_SIEGE_TIME_LIST.remove("hour");
					}
				}
			}
		}
	}
	
	// *******************************************************************************************
	public static final String FORTSIEGE_CONFIGURATION_FILE = "./config/fortsiege.properties";
	// *******************************************************************************************
	
	public static int FORTSIEGE_MAX_ATTACKER;
	public static int FORTSIEGE_FLAG_MAX_COUNT;
	public static int FORTSIEGE_CLAN_MIN_LEVEL;
	public static int FORTSIEGE_LENGTH_MINUTES;
	public static int FORTSIEGE_COUNTDOWN_LENGTH;
	public static int FORTSIEGE_MERCHANT_DELAY;
	
	public static long FS_TELE_FEE_RATIO;
	public static int FS_TELE1_FEE;
	public static int FS_TELE2_FEE;
	public static long FS_MPREG_FEE_RATIO;
	public static int FS_MPREG1_FEE;
	public static int FS_MPREG2_FEE;
	public static long FS_HPREG_FEE_RATIO;
	public static int FS_HPREG1_FEE;
	public static int FS_HPREG2_FEE;
	public static long FS_EXPREG_FEE_RATIO;
	public static int FS_EXPREG1_FEE;
	public static int FS_EXPREG2_FEE;
	public static long FS_SUPPORT_FEE_RATIO;
	public static int FS_SUPPORT1_FEE;
	public static int FS_SUPPORT2_FEE;
	public static int FS_BLOOD_OATH_COUNT;
	public static int FS_BLOOD_OATH_FRQ;
	
	private static final class FortSiegeConfig extends ConfigPropertiesLoader
	{
		@Override
		protected String getName()
		{
			return "fortsiege";
		}
		
		@Override
		protected void loadImpl(L2Properties fortSiegeSettings)
		{
			FORTSIEGE_MAX_ATTACKER = Integer.parseInt(fortSiegeSettings.getProperty("AttackerMaxClans", "500"));
			FORTSIEGE_FLAG_MAX_COUNT = Integer.parseInt(fortSiegeSettings.getProperty("MaxFlags", "1"));
			FORTSIEGE_CLAN_MIN_LEVEL = Integer.parseInt(fortSiegeSettings.getProperty("SiegeClanMinLevel", "4"));
			FORTSIEGE_LENGTH_MINUTES = Integer.parseInt(fortSiegeSettings.getProperty("SiegeLength", "60"));
			FORTSIEGE_COUNTDOWN_LENGTH = Integer.decode(fortSiegeSettings.getProperty("CountDownLength", "10"));
			FORTSIEGE_MERCHANT_DELAY =
					Integer.decode(fortSiegeSettings.getProperty("SuspiciousMerchantRespawnDelay", "180"));
			
			FS_TELE_FEE_RATIO =
					Long.parseLong(fortSiegeSettings.getProperty("FortressTeleportFunctionFeeRatio", "604800000"));
			FS_TELE1_FEE = Integer.parseInt(fortSiegeSettings.getProperty("FortressTeleportFunctionFeeLvl1", "1000"));
			FS_TELE2_FEE = Integer.parseInt(fortSiegeSettings.getProperty("FortressTeleportFunctionFeeLvl2", "10000"));
			FS_SUPPORT_FEE_RATIO =
					Long.parseLong(fortSiegeSettings.getProperty("FortressSupportFunctionFeeRatio", "86400000"));
			FS_SUPPORT1_FEE = Integer.parseInt(fortSiegeSettings.getProperty("FortressSupportFeeLvl1", "7000"));
			FS_SUPPORT2_FEE = Integer.parseInt(fortSiegeSettings.getProperty("FortressSupportFeeLvl2", "17000"));
			FS_MPREG_FEE_RATIO =
					Long.parseLong(fortSiegeSettings.getProperty("FortressMpRegenerationFunctionFeeRatio", "86400000"));
			FS_MPREG1_FEE = Integer.parseInt(fortSiegeSettings.getProperty("FortressMpRegenerationFeeLvl1", "6500"));
			FS_MPREG2_FEE = Integer.parseInt(fortSiegeSettings.getProperty("FortressMpRegenerationFeeLvl2", "9300"));
			FS_HPREG_FEE_RATIO =
					Long.parseLong(fortSiegeSettings.getProperty("FortressHpRegenerationFunctionFeeRatio", "86400000"));
			FS_HPREG1_FEE = Integer.parseInt(fortSiegeSettings.getProperty("FortressHpRegenerationFeeLvl1", "2000"));
			FS_HPREG2_FEE = Integer.parseInt(fortSiegeSettings.getProperty("FortressHpRegenerationFeeLvl2", "3500"));
			FS_EXPREG_FEE_RATIO =
					Long.parseLong(fortSiegeSettings.getProperty("FortressExpRegenerationFunctionFeeRatio", "86400000"));
			FS_EXPREG1_FEE = Integer.parseInt(fortSiegeSettings.getProperty("FortressExpRegenerationFeeLvl1", "9000"));
			FS_EXPREG2_FEE = Integer.parseInt(fortSiegeSettings.getProperty("FortressExpRegenerationFeeLvl2", "10000"));
			
			FS_BLOOD_OATH_COUNT = Integer.parseInt(fortSiegeSettings.getProperty("FortressBloodOathCount", "1"));
			FS_BLOOD_OATH_FRQ = Integer.parseInt(fortSiegeSettings.getProperty("FortressBloodOathFrequency", "360"));
		}
	}
	
	//  *******************************************************************************************
	public static final String SUBNETS_FILE = "./config/subnets.properties";
	//  *******************************************************************************************
	public static String SUBNETWORKS;
	
	/**
	 * Resolves a configuration file that is not a properties file against the defaults and operator directories. A file of the
	 * operator directory replaces the default file as a whole, because these files are lists without keys to merge.
	 * Without the two system properties the path is used as given, so a checkout starts unchanged.
	 */
	private static File resolveConfigFile(String path)
	{
		return L2Properties.resolveFile(path);
	}
	
	//  *******************************************************************************************
	public static void loadSubnets()
	{
		_log.info("loading " + SUBNETS_FILE);
		LineNumberReader lnr = null;
		try
		{
			
			String subnet = null;
			String line = null;
			SUBNETWORKS = "";
			
			lnr = new LineNumberReader(new InputStreamReader(new FileInputStream(resolveConfigFile(SUBNETS_FILE))));
			
			while ((line = lnr.readLine()) != null)
			{
				line = line.trim().toLowerCase().replace(" ", "");
				
				if (line.startsWith("subnet"))
				{
					if (SUBNETWORKS.length() > 0 && !SUBNETWORKS.endsWith(";"))
						SUBNETWORKS += ";";
					subnet = line.split("=")[1];
					SUBNETWORKS += subnet;
				}
			}
			
			SUBNETWORKS = SUBNETWORKS.toLowerCase().replaceAll("internal", Config.INTERNAL_HOSTNAME);
			SUBNETWORKS = SUBNETWORKS.toLowerCase().replaceAll("external", Config.EXTERNAL_HOSTNAME);
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		finally
		{
			try
			{
				if (lnr != null)
					lnr.close();
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
	}
	
	// *******************************************************************************************
	public static final String COMMAND_PRIVILEGES_FILE = "./config/command-privileges.properties";
	// *******************************************************************************************
	public static final Map<String, Integer> GM_COMMAND_PRIVILEGES = new LinkedHashMap<String, Integer>();
	
	// *******************************************************************************************
	private static final class CommandPrivilegesConfig extends ConfigPropertiesLoader
	{
		@Override
		protected String getName()
		{
			return "command-privileges";
		}
		
		@Override
		protected void loadImpl(L2Properties commandPrivileges)
		{
			for (Map.Entry<Object, Object> _command : commandPrivileges.entrySet())
			{
				String command = String.valueOf(_command.getKey());
				String commandLevel = String.valueOf(_command.getValue()).trim();
				
				int accessLevel = GM_ACCESSLEVEL;
				
				try
				{
					accessLevel = Integer.parseInt(commandLevel);
				}
				catch (Exception e)
				{
					_log.warn("Failed to parse command \"" + command + "\"!", e);
				}
				
				GM_COMMAND_PRIVILEGES.put(command, accessLevel);
			}
		}
	}
	
	// *******************************************************************************************
	public static final String SEVENSIGNS_FILE = "./config/sevensigns.properties";
	// *******************************************************************************************
	public static boolean ALT_GAME_CASTLE_DAWN; // Alternative gaming - players must be in a castle-owning clan or ally to sign up for Dawn.
	public static boolean ALT_GAME_CASTLE_DUSK; // Alternative gaming - players being in a castle-owning clan or ally cannot sign up for Dusk.
	public static int ALT_FESTIVAL_MIN_PLAYER; // Minimum number of player to participate in SevenSigns Festival
	public static long ALT_MAXIMUM_PLAYER_CONTRIB; // Maximum of player contrib during Festival
	public static long ALT_FESTIVAL_MANAGER_START; // Festival Manager start time.
	public static long ALT_FESTIVAL_LENGTH; // Festival Length
	public static long ALT_FESTIVAL_CYCLE_LENGTH; // Festival Cycle Length
	public static long ALT_FESTIVAL_FIRST_SPAWN; // Festival First Spawn
	public static long ALT_FESTIVAL_FIRST_SWARM; // Festival First Swarm
	public static long ALT_FESTIVAL_SECOND_SPAWN; // Festival Second Spawn
	public static long ALT_FESTIVAL_SECOND_SWARM; // Festival Second Swarm
	public static long ALT_FESTIVAL_CHEST_SPAWN; // Festival Chest Spawn
	public static int ALT_FESTIVAL_ARCHER_AGGRO; // Aggro value of Archer in SevenSigns Festival
	public static int ALT_FESTIVAL_CHEST_AGGRO; // Aggro value of Chest in SevenSigns Festival
	public static int ALT_FESTIVAL_MONSTER_AGGRO; // Aggro value of Monster in SevenSigns Festival
	public static long ALT_DAWN_JOIN_COST; // Amount of adena to pay to join Dawn Cabal
	
	public static double ALT_SIEGE_DAWN_GATES_PDEF_MULT;
	public static double ALT_SIEGE_DUSK_GATES_PDEF_MULT;
	public static double ALT_SIEGE_DAWN_GATES_MDEF_MULT;
	public static double ALT_SIEGE_DUSK_GATES_MDEF_MULT;
	
	// *******************************************************************************************
	private static final class SevenSignsConfig extends ConfigPropertiesLoader
	{
		@Override
		protected String getName()
		{
			return "sevensigns";
		}
		
		@Override
		protected void loadImpl(L2Properties SevenSettings)
		{
			ALT_GAME_CASTLE_DAWN = Boolean.parseBoolean(SevenSettings.getProperty("AltCastleForDawn", "True"));
			ALT_GAME_CASTLE_DUSK = Boolean.parseBoolean(SevenSettings.getProperty("AltCastleForDusk", "True"));
			ALT_FESTIVAL_MIN_PLAYER = Integer.parseInt(SevenSettings.getProperty("AltFestivalMinPlayer", "5"));
			ALT_MAXIMUM_PLAYER_CONTRIB = Long.parseLong(SevenSettings.getProperty("AltMaxPlayerContrib", "1000000"));
			ALT_FESTIVAL_MANAGER_START = Long.parseLong(SevenSettings.getProperty("AltFestivalManagerStart", "120000"));
			ALT_FESTIVAL_LENGTH = Long.parseLong(SevenSettings.getProperty("AltFestivalLength", "1080000"));
			ALT_FESTIVAL_CYCLE_LENGTH = Long.parseLong(SevenSettings.getProperty("AltFestivalCycleLength", "2280000"));
			ALT_FESTIVAL_FIRST_SPAWN = Long.parseLong(SevenSettings.getProperty("AltFestivalFirstSpawn", "120000"));
			ALT_FESTIVAL_FIRST_SWARM = Long.parseLong(SevenSettings.getProperty("AltFestivalFirstSwarm", "300000"));
			ALT_FESTIVAL_SECOND_SPAWN = Long.parseLong(SevenSettings.getProperty("AltFestivalSecondSpawn", "540000"));
			ALT_FESTIVAL_SECOND_SWARM = Long.parseLong(SevenSettings.getProperty("AltFestivalSecondSwarm", "720000"));
			ALT_FESTIVAL_CHEST_SPAWN = Long.parseLong(SevenSettings.getProperty("AltFestivalChestSpawn", "900000"));
			ALT_FESTIVAL_ARCHER_AGGRO = Integer.parseInt(SevenSettings.getProperty("AltFestivalArcherAggro", "200"));
			ALT_FESTIVAL_CHEST_AGGRO = Integer.parseInt(SevenSettings.getProperty("AltFestivalChestAggro", "0"));
			ALT_FESTIVAL_MONSTER_AGGRO = Integer.parseInt(SevenSettings.getProperty("AltFestivalMonsterAggro", "200"));
			ALT_DAWN_JOIN_COST = Long.parseLong(SevenSettings.getProperty("AltJoinDawnCost", "50000"));
			
			ALT_SIEGE_DAWN_GATES_PDEF_MULT =
					Double.parseDouble(SevenSettings.getProperty("AltDawnGatesPdefMult", "1.1"));
			ALT_SIEGE_DUSK_GATES_PDEF_MULT =
					Double.parseDouble(SevenSettings.getProperty("AltDuskGatesPdefMult", "0.8"));
			ALT_SIEGE_DAWN_GATES_MDEF_MULT =
					Double.parseDouble(SevenSettings.getProperty("AltDawnGatesMdefMult", "1.1"));
			ALT_SIEGE_DUSK_GATES_MDEF_MULT =
					Double.parseDouble(SevenSettings.getProperty("AltDuskGatesMdefMult", "0.8"));
		}
	}
	
	// *******************************************************************************************
	public static final String CLANHALL_CONFIG_FILE = "./config/clanhall.properties";
	// *******************************************************************************************
	/** Clan Hall function related configs */
	public static long CH_TELE_FEE_RATIO;
	public static int CH_TELE1_FEE;
	public static int CH_TELE2_FEE;
	public static long CH_ITEM_FEE_RATIO;
	public static int CH_ITEM1_FEE;
	public static int CH_ITEM2_FEE;
	public static int CH_ITEM3_FEE;
	public static long CH_MPREG_FEE_RATIO;
	public static int CH_MPREG1_FEE;
	public static int CH_MPREG2_FEE;
	public static int CH_MPREG3_FEE;
	public static int CH_MPREG4_FEE;
	public static int CH_MPREG5_FEE;
	public static long CH_HPREG_FEE_RATIO;
	public static int CH_HPREG1_FEE;
	public static int CH_HPREG2_FEE;
	public static int CH_HPREG3_FEE;
	public static int CH_HPREG4_FEE;
	public static int CH_HPREG5_FEE;
	public static int CH_HPREG6_FEE;
	public static int CH_HPREG7_FEE;
	public static int CH_HPREG8_FEE;
	public static int CH_HPREG9_FEE;
	public static int CH_HPREG10_FEE;
	public static int CH_HPREG11_FEE;
	public static int CH_HPREG12_FEE;
	public static int CH_HPREG13_FEE;
	public static long CH_EXPREG_FEE_RATIO;
	public static int CH_EXPREG1_FEE;
	public static int CH_EXPREG2_FEE;
	public static int CH_EXPREG3_FEE;
	public static int CH_EXPREG4_FEE;
	public static int CH_EXPREG5_FEE;
	public static int CH_EXPREG6_FEE;
	public static int CH_EXPREG7_FEE;
	public static long CH_SUPPORT_FEE_RATIO;
	public static int CH_SUPPORT1_FEE;
	public static int CH_SUPPORT2_FEE;
	public static int CH_SUPPORT3_FEE;
	public static int CH_SUPPORT4_FEE;
	public static int CH_SUPPORT5_FEE;
	public static int CH_SUPPORT6_FEE;
	public static int CH_SUPPORT7_FEE;
	public static int CH_SUPPORT8_FEE;
	public static long CH_CURTAIN_FEE_RATIO;
	public static int CH_CURTAIN1_FEE;
	public static int CH_CURTAIN2_FEE;
	public static long CH_FRONT_FEE_RATIO;
	public static int CH_FRONT1_FEE;
	public static int CH_FRONT2_FEE;
	
	// *******************************************************************************************
	private static final class ClanHallConfig extends ConfigPropertiesLoader
	{
		@Override
		protected String getName()
		{
			return "clanhall";
		}
		
		@Override
		protected void loadImpl(L2Properties clanhallSettings)
		{
			CH_TELE_FEE_RATIO =
					Long.parseLong(clanhallSettings.getProperty("ClanHallTeleportFunctionFeeRatio", "604800000"));
			CH_TELE1_FEE = Integer.parseInt(clanhallSettings.getProperty("ClanHallTeleportFunctionFeeLvl1", "7000"));
			CH_TELE2_FEE = Integer.parseInt(clanhallSettings.getProperty("ClanHallTeleportFunctionFeeLvl2", "14000"));
			CH_SUPPORT_FEE_RATIO =
					Long.parseLong(clanhallSettings.getProperty("ClanHallSupportFunctionFeeRatio", "86400000"));
			CH_SUPPORT1_FEE = Integer.parseInt(clanhallSettings.getProperty("ClanHallSupportFeeLvl1", "2500"));
			CH_SUPPORT2_FEE = Integer.parseInt(clanhallSettings.getProperty("ClanHallSupportFeeLvl2", "5000"));
			CH_SUPPORT3_FEE = Integer.parseInt(clanhallSettings.getProperty("ClanHallSupportFeeLvl3", "7000"));
			CH_SUPPORT4_FEE = Integer.parseInt(clanhallSettings.getProperty("ClanHallSupportFeeLvl4", "11000"));
			CH_SUPPORT5_FEE = Integer.parseInt(clanhallSettings.getProperty("ClanHallSupportFeeLvl5", "21000"));
			CH_SUPPORT6_FEE = Integer.parseInt(clanhallSettings.getProperty("ClanHallSupportFeeLvl6", "36000"));
			CH_SUPPORT7_FEE = Integer.parseInt(clanhallSettings.getProperty("ClanHallSupportFeeLvl7", "37000"));
			CH_SUPPORT8_FEE = Integer.parseInt(clanhallSettings.getProperty("ClanHallSupportFeeLvl8", "52000"));
			CH_MPREG_FEE_RATIO =
					Long.parseLong(clanhallSettings.getProperty("ClanHallMpRegenerationFunctionFeeRatio", "86400000"));
			CH_MPREG1_FEE = Integer.parseInt(clanhallSettings.getProperty("ClanHallMpRegenerationFeeLvl1", "2000"));
			CH_MPREG2_FEE = Integer.parseInt(clanhallSettings.getProperty("ClanHallMpRegenerationFeeLvl2", "3750"));
			CH_MPREG3_FEE = Integer.parseInt(clanhallSettings.getProperty("ClanHallMpRegenerationFeeLvl3", "6500"));
			CH_MPREG4_FEE = Integer.parseInt(clanhallSettings.getProperty("ClanHallMpRegenerationFeeLvl4", "13750"));
			CH_MPREG5_FEE = Integer.parseInt(clanhallSettings.getProperty("ClanHallMpRegenerationFeeLvl5", "20000"));
			CH_HPREG_FEE_RATIO =
					Long.parseLong(clanhallSettings.getProperty("ClanHallHpRegenerationFunctionFeeRatio", "86400000"));
			CH_HPREG1_FEE = Integer.parseInt(clanhallSettings.getProperty("ClanHallHpRegenerationFeeLvl1", "700"));
			CH_HPREG2_FEE = Integer.parseInt(clanhallSettings.getProperty("ClanHallHpRegenerationFeeLvl2", "800"));
			CH_HPREG3_FEE = Integer.parseInt(clanhallSettings.getProperty("ClanHallHpRegenerationFeeLvl3", "1000"));
			CH_HPREG4_FEE = Integer.parseInt(clanhallSettings.getProperty("ClanHallHpRegenerationFeeLvl4", "1166"));
			CH_HPREG5_FEE = Integer.parseInt(clanhallSettings.getProperty("ClanHallHpRegenerationFeeLvl5", "1500"));
			CH_HPREG6_FEE = Integer.parseInt(clanhallSettings.getProperty("ClanHallHpRegenerationFeeLvl6", "1750"));
			CH_HPREG7_FEE = Integer.parseInt(clanhallSettings.getProperty("ClanHallHpRegenerationFeeLvl7", "2000"));
			CH_HPREG8_FEE = Integer.parseInt(clanhallSettings.getProperty("ClanHallHpRegenerationFeeLvl8", "2250"));
			CH_HPREG9_FEE = Integer.parseInt(clanhallSettings.getProperty("ClanHallHpRegenerationFeeLvl9", "2500"));
			CH_HPREG10_FEE = Integer.parseInt(clanhallSettings.getProperty("ClanHallHpRegenerationFeeLvl10", "3250"));
			CH_HPREG11_FEE = Integer.parseInt(clanhallSettings.getProperty("ClanHallHpRegenerationFeeLvl11", "3270"));
			CH_HPREG12_FEE = Integer.parseInt(clanhallSettings.getProperty("ClanHallHpRegenerationFeeLvl12", "4250"));
			CH_HPREG13_FEE = Integer.parseInt(clanhallSettings.getProperty("ClanHallHpRegenerationFeeLvl13", "5166"));
			CH_EXPREG_FEE_RATIO =
					Long.parseLong(clanhallSettings.getProperty("ClanHallExpRegenerationFunctionFeeRatio", "86400000"));
			CH_EXPREG1_FEE = Integer.parseInt(clanhallSettings.getProperty("ClanHallExpRegenerationFeeLvl1", "3000"));
			CH_EXPREG2_FEE = Integer.parseInt(clanhallSettings.getProperty("ClanHallExpRegenerationFeeLvl2", "6000"));
			CH_EXPREG3_FEE = Integer.parseInt(clanhallSettings.getProperty("ClanHallExpRegenerationFeeLvl3", "9000"));
			CH_EXPREG4_FEE = Integer.parseInt(clanhallSettings.getProperty("ClanHallExpRegenerationFeeLvl4", "15000"));
			CH_EXPREG5_FEE = Integer.parseInt(clanhallSettings.getProperty("ClanHallExpRegenerationFeeLvl5", "21000"));
			CH_EXPREG6_FEE = Integer.parseInt(clanhallSettings.getProperty("ClanHallExpRegenerationFeeLvl6", "23330"));
			CH_EXPREG7_FEE = Integer.parseInt(clanhallSettings.getProperty("ClanHallExpRegenerationFeeLvl7", "30000"));
			CH_ITEM_FEE_RATIO =
					Long.parseLong(clanhallSettings.getProperty("ClanHallItemCreationFunctionFeeRatio", "86400000"));
			CH_ITEM1_FEE =
					Integer.parseInt(clanhallSettings.getProperty("ClanHallItemCreationFunctionFeeLvl1", "30000"));
			CH_ITEM2_FEE =
					Integer.parseInt(clanhallSettings.getProperty("ClanHallItemCreationFunctionFeeLvl2", "70000"));
			CH_ITEM3_FEE =
					Integer.parseInt(clanhallSettings.getProperty("ClanHallItemCreationFunctionFeeLvl3", "140000"));
			CH_CURTAIN_FEE_RATIO =
					Long.parseLong(clanhallSettings.getProperty("ClanHallCurtainFunctionFeeRatio", "86400000"));
			CH_CURTAIN1_FEE = Integer.parseInt(clanhallSettings.getProperty("ClanHallCurtainFunctionFeeLvl1", "2000"));
			CH_CURTAIN2_FEE = Integer.parseInt(clanhallSettings.getProperty("ClanHallCurtainFunctionFeeLvl2", "2500"));
			CH_FRONT_FEE_RATIO =
					Long.parseLong(clanhallSettings.getProperty("ClanHallFrontPlatformFunctionFeeRatio", "259200000"));
			CH_FRONT1_FEE =
					Integer.parseInt(clanhallSettings.getProperty("ClanHallFrontPlatformFunctionFeeLvl1", "1300"));
			CH_FRONT2_FEE =
					Integer.parseInt(clanhallSettings.getProperty("ClanHallFrontPlatformFunctionFeeLvl2", "4000"));
		}
	}
	
	// *******************************************************************************************
	public static final String CASTLE_CONFIG_FILE = "./config/castle.properties";
	// *******************************************************************************************
	/** Clan Hall function related configs */
	public static long CS_TELE_FEE_RATIO;
	public static int CS_TELE1_FEE;
	public static int CS_TELE2_FEE;
	public static long CS_MPREG_FEE_RATIO;
	public static int CS_MPREG1_FEE;
	public static int CS_MPREG2_FEE;
	public static int CS_MPREG3_FEE;
	public static int CS_MPREG4_FEE;
	public static long CS_HPREG_FEE_RATIO;
	public static int CS_HPREG1_FEE;
	public static int CS_HPREG2_FEE;
	public static int CS_HPREG3_FEE;
	public static int CS_HPREG4_FEE;
	public static int CS_HPREG5_FEE;
	public static long CS_EXPREG_FEE_RATIO;
	public static int CS_EXPREG1_FEE;
	public static int CS_EXPREG2_FEE;
	public static int CS_EXPREG3_FEE;
	public static int CS_EXPREG4_FEE;
	public static long CS_SUPPORT_FEE_RATIO;
	public static int CS_SUPPORT1_FEE;
	public static int CS_SUPPORT2_FEE;
	public static int CS_SUPPORT3_FEE;
	public static int CS_SUPPORT4_FEE;
	public static int CS_TRAP1_FEE;
	public static int CS_TRAP2_FEE;
	public static int CS_TRAP3_FEE;
	public static int CS_TRAP4_FEE;
	public static int CS_REINFORCE_OUTER1_FEE;
	public static int CS_REINFORCE_OUTER2_FEE;
	public static int CS_REINFORCE_OUTER3_FEE;
	public static int CS_REINFORCE_INNER1_FEE;
	public static int CS_REINFORCE_INNER2_FEE;
	public static int CS_REINFORCE_INNER3_FEE;
	public static int CS_REINFORCE_WALL1_FEE;
	public static int CS_REINFORCE_WALL2_FEE;
	public static int CS_REINFORCE_WALL3_FEE;
	public static long CS_SECURITY_FEE_RATIO;
	public static int CS_SECURITY1_FEE;
	public static int CS_SECURITY2_FEE;
	public static int CS_SECURITY3_FEE;
	
	// *******************************************************************************************
	private static final class CastleConfig extends ConfigPropertiesLoader
	{
		@Override
		protected String getName()
		{
			return "castle";
		}
		
		@Override
		protected void loadImpl(L2Properties castleSettings)
		{
			CS_TELE_FEE_RATIO =
					Long.parseLong(castleSettings.getProperty("CastleTeleportFunctionFeeRatio", "604800000"));
			CS_TELE1_FEE = Integer.parseInt(castleSettings.getProperty("CastleTeleportFunctionFeeLvl1", "7000"));
			CS_TELE2_FEE = Integer.parseInt(castleSettings.getProperty("CastleTeleportFunctionFeeLvl2", "14000"));
			CS_SUPPORT_FEE_RATIO =
					Long.parseLong(castleSettings.getProperty("CastleSupportFunctionFeeRatio", "86400000"));
			CS_SUPPORT1_FEE = Integer.parseInt(castleSettings.getProperty("CastleSupportFeeLvl1", "7000"));
			CS_SUPPORT2_FEE = Integer.parseInt(castleSettings.getProperty("CastleSupportFeeLvl2", "21000"));
			CS_SUPPORT3_FEE = Integer.parseInt(castleSettings.getProperty("CastleSupportFeeLvl3", "37000"));
			CS_SUPPORT4_FEE = Integer.parseInt(castleSettings.getProperty("CastleSupportFeeLvl4", "52000"));
			CS_MPREG_FEE_RATIO =
					Long.parseLong(castleSettings.getProperty("CastleMpRegenerationFunctionFeeRatio", "86400000"));
			CS_MPREG1_FEE = Integer.parseInt(castleSettings.getProperty("CastleMpRegenerationFeeLvl1", "2000"));
			CS_MPREG2_FEE = Integer.parseInt(castleSettings.getProperty("CastleMpRegenerationFeeLvl2", "6500"));
			CS_MPREG3_FEE = Integer.parseInt(castleSettings.getProperty("CastleMpRegenerationFeeLvl3", "13750"));
			CS_MPREG4_FEE = Integer.parseInt(castleSettings.getProperty("CastleMpRegenerationFeeLvl4", "20000"));
			CS_HPREG_FEE_RATIO =
					Long.parseLong(castleSettings.getProperty("CastleHpRegenerationFunctionFeeRatio", "86400000"));
			CS_HPREG1_FEE = Integer.parseInt(castleSettings.getProperty("CastleHpRegenerationFeeLvl1", "1000"));
			CS_HPREG2_FEE = Integer.parseInt(castleSettings.getProperty("CastleHpRegenerationFeeLvl2", "1500"));
			CS_HPREG3_FEE = Integer.parseInt(castleSettings.getProperty("CastleHpRegenerationFeeLvl3", "2250"));
			CS_HPREG4_FEE = Integer.parseInt(castleSettings.getProperty("CastleHpRegenerationFeeLvl4", "3270"));
			CS_HPREG5_FEE = Integer.parseInt(castleSettings.getProperty("CastleHpRegenerationFeeLvl5", "5166"));
			CS_EXPREG_FEE_RATIO =
					Long.parseLong(castleSettings.getProperty("CastleExpRegenerationFunctionFeeRatio", "86400000"));
			CS_EXPREG1_FEE = Integer.parseInt(castleSettings.getProperty("CastleExpRegenerationFeeLvl1", "9000"));
			CS_EXPREG2_FEE = Integer.parseInt(castleSettings.getProperty("CastleExpRegenerationFeeLvl2", "15000"));
			CS_EXPREG3_FEE = Integer.parseInt(castleSettings.getProperty("CastleExpRegenerationFeeLvl3", "21000"));
			CS_EXPREG4_FEE = Integer.parseInt(castleSettings.getProperty("CastleExpRegenerationFeeLvl4", "30000"));
			CS_TRAP1_FEE = Integer.parseInt(castleSettings.getProperty("CastleTrapFeeLvl1", "3000000"));
			CS_TRAP2_FEE = Integer.parseInt(castleSettings.getProperty("CastleTrapFeeLvl2", "6000000"));
			CS_TRAP3_FEE = Integer.parseInt(castleSettings.getProperty("CastleTrapFeeLvl3", "9000000"));
			CS_TRAP4_FEE = Integer.parseInt(castleSettings.getProperty("CastleTrapFeeLvl4", "12000000"));
			CS_REINFORCE_OUTER1_FEE =
					Integer.parseInt(castleSettings.getProperty("CastleReinforceOuterFeeLvl1", "750000"));
			CS_REINFORCE_OUTER2_FEE =
					Integer.parseInt(castleSettings.getProperty("CastleReinforceOuterFeeLvl2", "4000000"));
			CS_REINFORCE_OUTER3_FEE =
					Integer.parseInt(castleSettings.getProperty("CastleReinforceOuterFeeLvl3", "5000000"));
			CS_REINFORCE_INNER1_FEE =
					Integer.parseInt(castleSettings.getProperty("CastleReinforceInnerFeeLvl1", "1600000"));
			CS_REINFORCE_INNER2_FEE =
					Integer.parseInt(castleSettings.getProperty("CastleReinforceInnerFeeLvl2", "1800000"));
			CS_REINFORCE_INNER3_FEE =
					Integer.parseInt(castleSettings.getProperty("CastleReinforceInnerFeeLvl3", "3000000"));
			CS_REINFORCE_WALL1_FEE =
					Integer.parseInt(castleSettings.getProperty("CastleReinforceWallsFeeLvl1", "1600000"));
			CS_REINFORCE_WALL2_FEE =
					Integer.parseInt(castleSettings.getProperty("CastleReinforceWallsFeeLvl2", "1800000"));
			CS_REINFORCE_WALL3_FEE =
					Integer.parseInt(castleSettings.getProperty("CastleReinforceWallsFeeLvl3", "3000000"));
			CS_SECURITY_FEE_RATIO =
					Long.parseLong(castleSettings.getProperty("CastleSecurityFunctionFeeRatio", "86400000"));
			CS_SECURITY1_FEE = Integer.parseInt(castleSettings.getProperty("CastleSecurityFeeLvl1", "1000000"));
			CS_SECURITY2_FEE = Integer.parseInt(castleSettings.getProperty("CastleSecurityFeeLvl2", "1000000"));
			CS_SECURITY3_FEE = Integer.parseInt(castleSettings.getProperty("CastleSecurityFeeLvl3", "1000000"));
		}
	}
	
	// *******************************************************************************************
	
	// *******************************************************************************************
	public static final String FUN_ENGINES_FILE = "./config/fun_engines.properties";
	// *******************************************************************************************
	public static String CTF_EVEN_TEAMS;
	public static boolean CTF_ALLOW_INTERFERENCE;
	public static boolean CTF_ALLOW_POTIONS;
	public static boolean CTF_ALLOW_SUMMON;
	public static boolean CTF_ON_START_REMOVE_ALL_EFFECTS;
	public static boolean CTF_ON_START_UNSUMMON_PET;
	public static boolean CTF_ANNOUNCE_TEAM_STATS;
	public static boolean CTF_ANNOUNCE_REWARD;
	public static boolean CTF_JOIN_CURSED;
	public static boolean CTF_REVIVE_RECOVERY;
	public static long CTF_REVIVE_DELAY;
	public static String TVT_EVEN_TEAMS;
	public static boolean TVT_ALLOW_INTERFERENCE;
	public static boolean TVT_ALLOW_POTIONS;
	public static boolean TVT_ALLOW_SUMMON;
	public static boolean TVT_ON_START_REMOVE_ALL_EFFECTS;
	public static boolean TVT_ON_START_UNSUMMON_PET;
	public static boolean TVT_REVIVE_RECOVERY;
	public static boolean TVT_ANNOUNCE_TEAM_STATS;
	public static boolean TVT_ANNOUNCE_REWARD;
	public static boolean TVT_PRICE_NO_KILLS;
	public static boolean TVT_JOIN_CURSED;
	public static long TVT_REVIVE_DELAY;
	public static boolean DM_ALLOW_INTERFERENCE;
	public static boolean DM_ALLOW_POTIONS;
	public static boolean DM_ALLOW_SUMMON;
	public static boolean DM_ON_START_REMOVE_ALL_EFFECTS;
	public static boolean DM_ON_START_UNSUMMON_PET;
	public static long DM_REVIVE_DELAY;
	public static boolean VIP_ALLOW_INTERFERENCE;
	public static boolean VIP_ALLOW_POTIONS;
	public static boolean VIP_ON_START_REMOVE_ALL_EFFECTS;
	public static int VIP_MIN_LEVEL;
	public static int VIP_MAX_LEVEL;
	public static int VIP_MIN_PARTICIPANTS;
	public static boolean ARENA_ENABLED;
	public static int ARENA_INTERVAL;
	public static int ARENA_REWARD_ID;
	public static int ARENA_REWARD_COUNT;
	public static boolean FISHERMAN_ENABLED;
	public static int FISHERMAN_INTERVAL;
	public static int FISHERMAN_REWARD_ID;
	public static int FISHERMAN_REWARD_COUNT;
	public static String TVTI_INSTANCE_XML;
	public static boolean TVTI_ALLOW_TIE;
	public static boolean TVTI_CHECK_WEIGHT_AND_INVENTORY;
	public static boolean TVTI_ALLOW_INTERFERENCE;
	public static boolean TVTI_ALLOW_POTIONS;
	public static boolean TVTI_ALLOW_SUMMON;
	public static boolean TVTI_ON_START_REMOVE_ALL_EFFECTS;
	public static boolean TVTI_ON_START_UNSUMMON_PET;
	public static boolean TVTI_REVIVE_RECOVERY;
	public static boolean TVTI_ANNOUNCE_TEAM_STATS;
	public static boolean TVTI_ANNOUNCE_REWARD;
	public static boolean TVTI_PRICE_NO_KILLS;
	public static boolean TVTI_JOIN_CURSED;
	public static boolean TVTI_SHOW_STATS_PAGE;
	public static int TVTI_SORT_TEAMS;
	public static int TVTI_JOIN_NPC_SKILL;
	public static long TVTI_REVIVE_DELAY;
	public static long TVTI_JOIN_NPC_DO_SKILL_AGAIN;
	
	// *******************************************************************************************
	// *******************************************************************************************
	private static final class FunEnginesConfig extends ConfigPropertiesLoader
	{
		@Override
		protected String getName()
		{
			return "fun_engines";
		}
		
		@Override
		protected void loadImpl(L2Properties funEnginesSettings)
		{
			CTF_EVEN_TEAMS = funEnginesSettings.getProperty("CTFEvenTeams", "BALANCE");
			CTF_ALLOW_INTERFERENCE =
					Boolean.parseBoolean(funEnginesSettings.getProperty("CTFAllowInterference", "false"));
			CTF_ALLOW_POTIONS = Boolean.parseBoolean(funEnginesSettings.getProperty("CTFAllowPotions", "false"));
			CTF_ALLOW_SUMMON = Boolean.parseBoolean(funEnginesSettings.getProperty("CTFAllowSummon", "false"));
			CTF_ON_START_REMOVE_ALL_EFFECTS =
					Boolean.parseBoolean(funEnginesSettings.getProperty("CTFOnStartRemoveAllEffects", "true"));
			CTF_ON_START_UNSUMMON_PET =
					Boolean.parseBoolean(funEnginesSettings.getProperty("CTFOnStartUnsummonPet", "true"));
			CTF_ANNOUNCE_TEAM_STATS =
					Boolean.parseBoolean(funEnginesSettings.getProperty("CTFAnnounceTeamStats", "false"));
			CTF_ANNOUNCE_REWARD = Boolean.parseBoolean(funEnginesSettings.getProperty("CTFAnnounceReward", "false"));
			CTF_JOIN_CURSED = Boolean.parseBoolean(funEnginesSettings.getProperty("CTFJoinWithCursedWeapon", "true"));
			CTF_REVIVE_RECOVERY = Boolean.parseBoolean(funEnginesSettings.getProperty("CTFReviveRecovery", "false"));
			CTF_REVIVE_DELAY = Long.parseLong(funEnginesSettings.getProperty("CTFReviveDelay", "20000"));
			if (CTF_REVIVE_DELAY < 1000)
				CTF_REVIVE_DELAY = 1000; //can't be set less then 1 second
				
			TVT_EVEN_TEAMS = funEnginesSettings.getProperty("TvTEvenTeams", "BALANCE");
			TVT_ALLOW_INTERFERENCE =
					Boolean.parseBoolean(funEnginesSettings.getProperty("TvTAllowInterference", "false"));
			TVT_ALLOW_POTIONS = Boolean.parseBoolean(funEnginesSettings.getProperty("TvTAllowPotions", "false"));
			TVT_ALLOW_SUMMON = Boolean.parseBoolean(funEnginesSettings.getProperty("TvTAllowSummon", "false"));
			TVT_ON_START_REMOVE_ALL_EFFECTS =
					Boolean.parseBoolean(funEnginesSettings.getProperty("TvTOnStartRemoveAllEffects", "true"));
			TVT_ON_START_UNSUMMON_PET =
					Boolean.parseBoolean(funEnginesSettings.getProperty("TvTOnStartUnsummonPet", "true"));
			TVT_REVIVE_RECOVERY = Boolean.parseBoolean(funEnginesSettings.getProperty("TvTReviveRecovery", "false"));
			TVT_ANNOUNCE_TEAM_STATS =
					Boolean.parseBoolean(funEnginesSettings.getProperty("TvTAnnounceTeamStats", "false"));
			TVT_ANNOUNCE_REWARD = Boolean.parseBoolean(funEnginesSettings.getProperty("TvTAnnounceReward", "false"));
			TVT_PRICE_NO_KILLS = Boolean.parseBoolean(funEnginesSettings.getProperty("TvTPriceNoKills", "false"));
			TVT_JOIN_CURSED = Boolean.parseBoolean(funEnginesSettings.getProperty("TvTJoinWithCursedWeapon", "true"));
			TVT_REVIVE_DELAY = Long.parseLong(funEnginesSettings.getProperty("TVTReviveDelay", "20000"));
			if (TVT_REVIVE_DELAY < 1000)
				TVT_REVIVE_DELAY = 1000; //can't be set less then 1 second
				
			DM_ALLOW_INTERFERENCE =
					Boolean.parseBoolean(funEnginesSettings.getProperty("DMAllowInterference", "false"));
			DM_ALLOW_POTIONS = Boolean.parseBoolean(funEnginesSettings.getProperty("DMAllowPotions", "false"));
			DM_ALLOW_SUMMON = Boolean.parseBoolean(funEnginesSettings.getProperty("DMAllowSummon", "false"));
			DM_ON_START_REMOVE_ALL_EFFECTS =
					Boolean.parseBoolean(funEnginesSettings.getProperty("DMOnStartRemoveAllEffects", "true"));
			DM_ON_START_UNSUMMON_PET =
					Boolean.parseBoolean(funEnginesSettings.getProperty("DMOnStartUnsummonPet", "true"));
			DM_REVIVE_DELAY = Long.parseLong(funEnginesSettings.getProperty("DMReviveDelay", "20000"));
			if (DM_REVIVE_DELAY < 1000)
				DM_REVIVE_DELAY = 1000; //can't be set less then 1 second
				
			VIP_ALLOW_INTERFERENCE =
					Boolean.parseBoolean(funEnginesSettings.getProperty("VIPAllowInterference", "false"));
			VIP_ALLOW_POTIONS = Boolean.parseBoolean(funEnginesSettings.getProperty("VIPAllowPotions", "false"));
			VIP_ON_START_REMOVE_ALL_EFFECTS =
					Boolean.parseBoolean(funEnginesSettings.getProperty("VIPOnStartRemoveAllEffects", "true"));
			VIP_MIN_LEVEL = Integer.parseInt(funEnginesSettings.getProperty("VIPMinLevel", "1"));
			if (VIP_MIN_LEVEL < 1)
				VIP_MIN_LEVEL = 1; //can't be set less then lvl 1
			VIP_MAX_LEVEL = Integer.parseInt(funEnginesSettings.getProperty("VIPMaxLevel", "85"));
			if (VIP_MAX_LEVEL < VIP_MIN_LEVEL)
				VIP_MAX_LEVEL = VIP_MIN_LEVEL + 1; //can't be set less then Min Level
			VIP_MIN_PARTICIPANTS = Integer.parseInt(funEnginesSettings.getProperty("VIPMinParticipants", "10"));
			if (VIP_MIN_PARTICIPANTS < 10)
				VIP_MIN_PARTICIPANTS = 10; //can't be set less then lvl 10
				
			ARENA_ENABLED = Boolean.parseBoolean(funEnginesSettings.getProperty("ArenaEnabled", "false"));
			ARENA_INTERVAL = Integer.parseInt(funEnginesSettings.getProperty("ArenaInterval", "60"));
			ARENA_REWARD_ID = Integer.parseInt(funEnginesSettings.getProperty("ArenaRewardId", "57"));
			ARENA_REWARD_COUNT = Integer.parseInt(funEnginesSettings.getProperty("ArenaRewardCount", "100"));
			
			FISHERMAN_ENABLED = Boolean.parseBoolean(funEnginesSettings.getProperty("FishermanEnabled", "false"));
			FISHERMAN_INTERVAL = Integer.parseInt(funEnginesSettings.getProperty("FishermanInterval", "60"));
			FISHERMAN_REWARD_ID = Integer.parseInt(funEnginesSettings.getProperty("FishermanRewardId", "57"));
			FISHERMAN_REWARD_COUNT = Integer.parseInt(funEnginesSettings.getProperty("FishermanRewardCount", "100"));
			
			TVTI_INSTANCE_XML = funEnginesSettings.getProperty("TvTIInstanceXML", "TvTI.xml");
			TVTI_ALLOW_TIE = Boolean.parseBoolean(funEnginesSettings.getProperty("TvTIAllowTie", "false"));
			TVTI_CHECK_WEIGHT_AND_INVENTORY =
					Boolean.parseBoolean(funEnginesSettings.getProperty("TvTICheckWeightAndInventory", "true"));
			TVTI_ALLOW_INTERFERENCE =
					Boolean.parseBoolean(funEnginesSettings.getProperty("TvTIAllowInterference", "false"));
			TVTI_ALLOW_POTIONS = Boolean.parseBoolean(funEnginesSettings.getProperty("TvTIAllowPotions", "false"));
			TVTI_ALLOW_SUMMON = Boolean.parseBoolean(funEnginesSettings.getProperty("TvTIAllowSummon", "false"));
			TVTI_ON_START_REMOVE_ALL_EFFECTS =
					Boolean.parseBoolean(funEnginesSettings.getProperty("TvTIOnStartRemoveAllEffects", "true"));
			TVTI_ON_START_UNSUMMON_PET =
					Boolean.parseBoolean(funEnginesSettings.getProperty("TvTIOnStartUnsummonPet", "true"));
			TVTI_REVIVE_RECOVERY = Boolean.parseBoolean(funEnginesSettings.getProperty("TvTIReviveRecovery", "false"));
			TVTI_ANNOUNCE_TEAM_STATS =
					Boolean.parseBoolean(funEnginesSettings.getProperty("TvTIAnnounceTeamStats", "false"));
			TVTI_ANNOUNCE_REWARD = Boolean.parseBoolean(funEnginesSettings.getProperty("TvTIAnnounceReward", "false"));
			TVTI_PRICE_NO_KILLS = Boolean.parseBoolean(funEnginesSettings.getProperty("TvTIPriceNoKills", "false"));
			TVTI_JOIN_CURSED = Boolean.parseBoolean(funEnginesSettings.getProperty("TvTIJoinWithCursedWeapon", "true"));
			TVTI_SHOW_STATS_PAGE = Boolean.parseBoolean(funEnginesSettings.getProperty("TvTIShowStatistics", "true"));
			TVTI_SORT_TEAMS = Integer.parseInt(funEnginesSettings.getProperty("TvTISortTeams", "0"));
			TVTI_JOIN_NPC_SKILL = Integer.parseInt(funEnginesSettings.getProperty("TvTIJoinNpcSkill", "1034"));
			TVTI_REVIVE_DELAY = Long.parseLong(funEnginesSettings.getProperty("TvTIReviveDelay", "20000"));
			if (TVTI_REVIVE_DELAY < 1000)
				TVTI_REVIVE_DELAY = 1000; //can't be set less then 1 second
			TVTI_JOIN_NPC_DO_SKILL_AGAIN =
					Long.parseLong(funEnginesSettings.getProperty("TvTIJoinNpcDoSkillAgain", "0"));
			if (TVTI_JOIN_NPC_DO_SKILL_AGAIN < 1000 && TVTI_JOIN_NPC_DO_SKILL_AGAIN != 0)
				TVTI_JOIN_NPC_DO_SKILL_AGAIN = 1000; //can't be set less then 1 second
		}
	}
	
	// *******************************************************************************************
	public static final String BOSS_FILE = "./config/boss.properties";
	// *******************************************************************************************
	/*****************************************
	 * Queen Ant CONFIG *
	 *****************************************/
	public static int NURSEANT_RESPAWN_DELAY;
	
	/*****************************************
	 * Antharas CONFIG *
	 *****************************************/
	public static int FWA_FIXINTERVALOFANTHARAS;
	public static int FWA_RANDOMINTERVALOFANTHARAS;
	public static int FWA_APPTIMEOFANTHARAS;
	public static int FWA_ACTIVITYTIMEOFANTHARAS;
	public static boolean FWA_OLDANTHARAS;
	public static int FWA_LIMITOFWEAK;
	public static int FWA_LIMITOFNORMAL;
	public static int FWA_INTERVALOFBEHEMOTHONWEAK;
	public static int FWA_INTERVALOFBEHEMOTHONNORMAL;
	public static int FWA_INTERVALOFBEHEMOTHONSTRONG;
	public static int FWA_INTERVALOFBOMBERONWEAK;
	public static int FWA_INTERVALOFBOMBERONNORMAL;
	public static int FWA_INTERVALOFBOMBERONSTRONG;
	public static boolean FWA_MOVEATRANDOM;
	
	/******************************************
	 * Baium CONFIG *
	 ******************************************/
	public static int FWB_FIXINTERVALOFBAIUM;
	public static int FWB_RANDOMINTERVALOFBAIUM;
	public static int FWB_ACTIVITYTIMEOFBAIUM;
	public static boolean FWB_MOVEATRANDOM;
	public static int FWB_LIMITUNTILSLEEP;
	
	/******************************************
	 * Valakas CONFIG *
	 ******************************************/
	public static int FWV_FIXINTERVALOFVALAKAS;
	public static int FWV_RANDOMINTERVALOFVALAKAS;
	public static int FWV_APPTIMEOFVALAKAS;
	public static int FWV_ACTIVITYTIMEOFVALAKAS;
	public static int FWV_CAPACITYOFLAIR;
	public static boolean FWV_MOVEATRANDOM;
	
	/*******************************************
	 * Baylor CONFIG *
	 *******************************************/
	public static boolean FWBA_ENABLESINGLEPLAYER;
	public static int FWBA_FIXINTERVALOFBAYLORSPAWN;
	public static int FWBA_RANDOMINTERVALOFBAYLORSPAWN;
	public static int FWBA_INTERVALOFNEXTMONSTER;
	public static int FWBA_ACTIVITYTIMEOFMOBS;
	
	/*******************************************
	 * Frintezza CONFIG *
	 *******************************************/
	public static int FWF_INTERVALOFNEXTMONSTER;
	public static int FWF_INTERVALOFFRINTEZZA;
	public static int FWF_ACTIVITYTIMEOFFRINTEZZA;
	
	/*******************************************
	 * Sailren CONFIG *
	 *******************************************/
	public static boolean FWS_ENABLESINGLEPLAYER;
	public static int FWS_FIXINTERVALOFSAILRENSPAWN;
	public static int FWS_RANDOMINTERVALOFSAILRENSPAWN;
	public static int FWS_INTERVALOFNEXTMONSTER;
	public static int FWS_ACTIVITYTIMEOFMOBS;
	
	/*******************************************
	 * High Priestess van Halter CONFIG *
	 *******************************************/
	public static int HPH_FIXINTERVALOFHALTER;
	public static int HPH_RANDOMINTERVALOFHALTER;
	public static int HPH_APPTIMEOFHALTER;
	public static int HPH_ACTIVITYTIMEOFHALTER;
	public static int HPH_FIGHTTIMEOFHALTER;
	public static int HPH_CALLROYALGUARDHELPERCOUNT;
	public static int HPH_CALLROYALGUARDHELPERINTERVAL;
	public static int HPH_INTERVALOFDOOROFALTER;
	public static int HPH_TIMEOFLOCKUPDOOROFALTAR;
	
	/***************************************************************************
	 * JP Attack Last Imperial Tomb Custom CONFIG *
	 **************************************************************************/
	public static int LIT_REGISTRATION_MODE;
	public static int LIT_REGISTRATION_TIME;
	public static int LIT_MIN_PARTY_CNT;
	public static int LIT_MAX_PARTY_CNT;
	public static int LIT_MIN_PLAYER_CNT;
	public static int LIT_MAX_PLAYER_CNT;
	public static int LIT_TIME_LIMIT;
	
	// *******************************************************************************************
	private static final class BossConfig extends ConfigPropertiesLoader
	{
		@Override
		protected String getName()
		{
			return "boss";
		}
		
		@Override
		protected void loadImpl(L2Properties bossSettings)
		{
			// Queen Ant
			NURSEANT_RESPAWN_DELAY = Integer.parseInt(bossSettings.getProperty("NurseAntRespawnDelay", "15"));
			if (NURSEANT_RESPAWN_DELAY < 15)
				NURSEANT_RESPAWN_DELAY = 15;
			else if (NURSEANT_RESPAWN_DELAY > 120)
				NURSEANT_RESPAWN_DELAY = 120;
			NURSEANT_RESPAWN_DELAY = NURSEANT_RESPAWN_DELAY * 1000;
			
			// antharas
			FWA_FIXINTERVALOFANTHARAS = Integer.parseInt(bossSettings.getProperty("FixIntervalOfAntharas", "11520"));
			if (FWA_FIXINTERVALOFANTHARAS < 5 || FWA_FIXINTERVALOFANTHARAS > 20160)
				FWA_FIXINTERVALOFANTHARAS = 11520;
			FWA_FIXINTERVALOFANTHARAS *= 60000;
			
			FWA_RANDOMINTERVALOFANTHARAS =
					Integer.parseInt(bossSettings.getProperty("RandomIntervalOfAntharas", "8640"));
			if (FWA_RANDOMINTERVALOFANTHARAS < 5 || FWA_RANDOMINTERVALOFANTHARAS > 20160)
				FWA_RANDOMINTERVALOFANTHARAS = 8640;
			FWA_RANDOMINTERVALOFANTHARAS *= 60000;
			
			FWA_APPTIMEOFANTHARAS = Integer.parseInt(bossSettings.getProperty("AppTimeOfAntharas", "10"));
			if (FWA_APPTIMEOFANTHARAS < 5 || FWA_APPTIMEOFANTHARAS > 60)
				FWA_APPTIMEOFANTHARAS = 10;
			FWA_APPTIMEOFANTHARAS *= 60000;
			
			FWA_ACTIVITYTIMEOFANTHARAS = Integer.parseInt(bossSettings.getProperty("ActivityTimeOfAntharas", "120"));
			if (FWA_ACTIVITYTIMEOFANTHARAS < 120 || FWA_ACTIVITYTIMEOFANTHARAS > 720)
				FWA_ACTIVITYTIMEOFANTHARAS = 120;
			FWA_ACTIVITYTIMEOFANTHARAS *= 60000;
			
			FWA_OLDANTHARAS = Boolean.parseBoolean(bossSettings.getProperty("OldAntharas", "False"));
			FWA_LIMITOFWEAK = Integer.parseInt(bossSettings.getProperty("LimitOfWeak", "299"));
			
			FWA_LIMITOFNORMAL = Integer.parseInt(bossSettings.getProperty("LimitOfNormal", "399"));
			if (FWA_LIMITOFWEAK >= FWA_LIMITOFNORMAL)
				FWA_LIMITOFNORMAL = FWA_LIMITOFWEAK + 1;
			
			FWA_INTERVALOFBEHEMOTHONWEAK = Integer.parseInt(bossSettings.getProperty("IntervalOfBehemothOnWeak", "8"));
			if (FWA_INTERVALOFBEHEMOTHONWEAK < 1 || FWA_INTERVALOFBEHEMOTHONWEAK > 10)
				FWA_INTERVALOFBEHEMOTHONWEAK = 8;
			FWA_INTERVALOFBEHEMOTHONWEAK *= 60000;
			
			FWA_INTERVALOFBEHEMOTHONNORMAL =
					Integer.parseInt(bossSettings.getProperty("IntervalOfBehemothOnNormal", "5"));
			if (FWA_INTERVALOFBEHEMOTHONNORMAL < 1 || FWA_INTERVALOFBEHEMOTHONNORMAL > 10)
				FWA_INTERVALOFBEHEMOTHONNORMAL = 5;
			FWA_INTERVALOFBEHEMOTHONNORMAL *= 60000;
			
			FWA_INTERVALOFBEHEMOTHONSTRONG =
					Integer.parseInt(bossSettings.getProperty("IntervalOfBehemothOnStrong", "3"));
			if (FWA_INTERVALOFBEHEMOTHONSTRONG < 1 || FWA_INTERVALOFBEHEMOTHONSTRONG > 10)
				FWA_INTERVALOFBEHEMOTHONSTRONG = 3;
			FWA_INTERVALOFBEHEMOTHONSTRONG *= 60000;
			
			FWA_INTERVALOFBOMBERONWEAK = Integer.parseInt(bossSettings.getProperty("IntervalOfBomberOnWeak", "6"));
			if (FWA_INTERVALOFBOMBERONWEAK < 1 || FWA_INTERVALOFBOMBERONWEAK > 10)
				FWA_INTERVALOFBOMBERONWEAK = 6;
			FWA_INTERVALOFBOMBERONWEAK *= 60000;
			
			FWA_INTERVALOFBOMBERONNORMAL = Integer.parseInt(bossSettings.getProperty("IntervalOfBomberOnNormal", "4"));
			if (FWA_INTERVALOFBOMBERONNORMAL < 1 || FWA_INTERVALOFBOMBERONNORMAL > 10)
				FWA_INTERVALOFBOMBERONNORMAL = 4;
			FWA_INTERVALOFBOMBERONNORMAL *= 60000;
			
			FWA_INTERVALOFBOMBERONSTRONG = Integer.parseInt(bossSettings.getProperty("IntervalOfBomberOnStrong", "3"));
			if (FWA_INTERVALOFBOMBERONSTRONG < 1 || FWA_INTERVALOFBOMBERONSTRONG > 10)
				FWA_INTERVALOFBOMBERONSTRONG = 3;
			FWA_INTERVALOFBOMBERONSTRONG *= 60000;
			
			FWA_MOVEATRANDOM = Boolean.parseBoolean(bossSettings.getProperty("MoveAtRandom", "True"));
			
			//baium
			FWB_FIXINTERVALOFBAIUM = Integer.parseInt(bossSettings.getProperty("FixIntervalOfBaium", "7200"));
			if (FWB_FIXINTERVALOFBAIUM < 5 || FWB_FIXINTERVALOFBAIUM > 12960)
				FWB_FIXINTERVALOFBAIUM = 7200;
			FWB_FIXINTERVALOFBAIUM *= 60000;
			
			FWB_RANDOMINTERVALOFBAIUM = Integer.parseInt(bossSettings.getProperty("RandomIntervalOfBaium", "5760"));
			if (FWB_RANDOMINTERVALOFBAIUM < 5 || FWB_RANDOMINTERVALOFBAIUM > 12960)
				FWB_RANDOMINTERVALOFBAIUM = 5760;
			FWB_RANDOMINTERVALOFBAIUM *= 60000;
			
			FWB_ACTIVITYTIMEOFBAIUM = Integer.parseInt(bossSettings.getProperty("ActivityTimeOfBaium", "120"));
			if (FWB_ACTIVITYTIMEOFBAIUM < 120 || FWB_ACTIVITYTIMEOFBAIUM > 720)
				FWB_ACTIVITYTIMEOFBAIUM = 120;
			FWB_ACTIVITYTIMEOFBAIUM *= 60000;
			
			FWB_MOVEATRANDOM = Boolean.parseBoolean(bossSettings.getProperty("MoveAtRandom", "True"));
			FWB_LIMITUNTILSLEEP = Integer.parseInt(bossSettings.getProperty("LimitUntilSleep", "30"));
			if (FWB_LIMITUNTILSLEEP < 30 || FWB_LIMITUNTILSLEEP > 90)
				FWB_LIMITUNTILSLEEP = 30;
			FWB_LIMITUNTILSLEEP *= 60000;
			
			//baylor
			FWBA_ENABLESINGLEPLAYER =
					Boolean.parseBoolean(bossSettings.getProperty("EnableSinglePlayerBaylor", "False"));
			
			FWBA_FIXINTERVALOFBAYLORSPAWN =
					Integer.parseInt(bossSettings.getProperty("FixIntervalOfBaylorSpawn", "1440"));
			if (FWBA_FIXINTERVALOFBAYLORSPAWN < 5 || FWBA_FIXINTERVALOFBAYLORSPAWN > 2880)
				FWBA_FIXINTERVALOFBAYLORSPAWN = 1440;
			FWBA_FIXINTERVALOFBAYLORSPAWN *= 60000;
			
			FWBA_RANDOMINTERVALOFBAYLORSPAWN =
					Integer.parseInt(bossSettings.getProperty("RandomIntervalOfBaylorSpawn", "1440"));
			if (FWBA_RANDOMINTERVALOFBAYLORSPAWN < 5 || FWBA_RANDOMINTERVALOFBAYLORSPAWN > 2880)
				FWBA_RANDOMINTERVALOFBAYLORSPAWN = 1440;
			FWBA_RANDOMINTERVALOFBAYLORSPAWN *= 60000;
			
			FWBA_INTERVALOFNEXTMONSTER = Integer.parseInt(bossSettings.getProperty("IntervalOfNextMonsterBaylor", "1"));
			if (FWBA_INTERVALOFNEXTMONSTER < 1 || FWBA_INTERVALOFNEXTMONSTER > 10)
				FWBA_INTERVALOFNEXTMONSTER = 1;
			FWBA_INTERVALOFNEXTMONSTER *= 60000;
			
			FWBA_ACTIVITYTIMEOFMOBS = Integer.parseInt(bossSettings.getProperty("ActivityTimeOfMobsBaylor", "120"));
			if (FWBA_ACTIVITYTIMEOFMOBS < 1 || FWBA_ACTIVITYTIMEOFMOBS > 120)
				FWS_ACTIVITYTIMEOFMOBS = 120;
			FWBA_ACTIVITYTIMEOFMOBS *= 60000;
			
			//Frintezza
			FWF_INTERVALOFFRINTEZZA = Integer.parseInt(bossSettings.getProperty("IntervalOfFrintezzaSpawn", "1440"));
			if (FWF_INTERVALOFFRINTEZZA < 5 || FWF_INTERVALOFFRINTEZZA > 5760)
				FWF_INTERVALOFFRINTEZZA = 1440;
			FWF_INTERVALOFFRINTEZZA *= 60000;
			
			FWF_INTERVALOFNEXTMONSTER =
					Integer.parseInt(bossSettings.getProperty("IntervalOfNextMonsterFrintezza", "1"));
			if (FWF_INTERVALOFNEXTMONSTER < 1 || FWF_INTERVALOFNEXTMONSTER > 10)
				FWF_INTERVALOFNEXTMONSTER = 1;
			FWF_INTERVALOFNEXTMONSTER *= 60000;
			
			FWF_ACTIVITYTIMEOFFRINTEZZA =
					Integer.parseInt(bossSettings.getProperty("ActivityTimeOfMobsFrintezza", "120"));
			if (FWF_ACTIVITYTIMEOFFRINTEZZA < 120 || FWF_ACTIVITYTIMEOFFRINTEZZA > 240)
				FWF_ACTIVITYTIMEOFFRINTEZZA = 120;
			FWF_ACTIVITYTIMEOFFRINTEZZA *= 60000;
			
			//sailren
			FWS_ENABLESINGLEPLAYER = Boolean.parseBoolean(bossSettings.getProperty("EnableSinglePlayer", "False"));
			
			FWS_FIXINTERVALOFSAILRENSPAWN =
					Integer.parseInt(bossSettings.getProperty("FixIntervalOfSailrenSpawn", "1440"));
			if (FWS_FIXINTERVALOFSAILRENSPAWN < 5 || FWS_FIXINTERVALOFSAILRENSPAWN > 2880)
				FWS_FIXINTERVALOFSAILRENSPAWN = 1440;
			FWS_FIXINTERVALOFSAILRENSPAWN *= 60000;
			
			FWS_RANDOMINTERVALOFSAILRENSPAWN =
					Integer.parseInt(bossSettings.getProperty("RandomIntervalOfSailrenSpawn", "1440"));
			if (FWS_RANDOMINTERVALOFSAILRENSPAWN < 5 || FWS_RANDOMINTERVALOFSAILRENSPAWN > 2880)
				FWS_RANDOMINTERVALOFSAILRENSPAWN = 1440;
			FWS_RANDOMINTERVALOFSAILRENSPAWN *= 60000;
			
			FWS_INTERVALOFNEXTMONSTER = Integer.parseInt(bossSettings.getProperty("IntervalOfNextMonster", "1"));
			if (FWS_INTERVALOFNEXTMONSTER < 1 || FWS_INTERVALOFNEXTMONSTER > 10)
				FWS_INTERVALOFNEXTMONSTER = 1;
			FWS_INTERVALOFNEXTMONSTER *= 60000;
			
			FWS_ACTIVITYTIMEOFMOBS = Integer.parseInt(bossSettings.getProperty("ActivityTimeOfMobs", "120"));
			if (FWS_ACTIVITYTIMEOFMOBS < 1 || FWS_ACTIVITYTIMEOFMOBS > 120)
				FWS_ACTIVITYTIMEOFMOBS = 120;
			FWS_ACTIVITYTIMEOFMOBS *= 60000;
			
			//valakas
			FWV_FIXINTERVALOFVALAKAS = Integer.parseInt(bossSettings.getProperty("FixIntervalOfValakas", "11520"));
			if (FWV_FIXINTERVALOFVALAKAS < 5 || FWV_FIXINTERVALOFVALAKAS > 20160)
				FWV_FIXINTERVALOFVALAKAS = 11520;
			FWV_FIXINTERVALOFVALAKAS *= 60000;
			
			FWV_RANDOMINTERVALOFVALAKAS = Integer.parseInt(bossSettings.getProperty("RandomIntervalOfValakas", "8640"));
			if (FWV_RANDOMINTERVALOFVALAKAS < 5 || FWV_RANDOMINTERVALOFVALAKAS > 20160)
				FWV_RANDOMINTERVALOFVALAKAS = 8640;
			FWV_RANDOMINTERVALOFVALAKAS *= 60000;
			
			FWV_APPTIMEOFVALAKAS = Integer.parseInt(bossSettings.getProperty("AppTimeOfValakas", "20"));
			if (FWV_APPTIMEOFVALAKAS < 5 || FWV_APPTIMEOFVALAKAS > 60)
				FWV_APPTIMEOFVALAKAS = 10;
			FWV_APPTIMEOFVALAKAS *= 60000;
			
			FWV_ACTIVITYTIMEOFVALAKAS = Integer.parseInt(bossSettings.getProperty("ActivityTimeOfValakas", "120"));
			if (FWV_ACTIVITYTIMEOFVALAKAS < 120 || FWV_ACTIVITYTIMEOFVALAKAS > 720)
				FWV_ACTIVITYTIMEOFVALAKAS = 120;
			FWV_ACTIVITYTIMEOFVALAKAS *= 60000;
			
			FWV_CAPACITYOFLAIR = Integer.parseInt(bossSettings.getProperty("CapacityOfLairOfValakas", "200"));
			FWV_MOVEATRANDOM = Boolean.parseBoolean(bossSettings.getProperty("MoveAtRandom", "True"));
			
			//High Priestess van Halter
			HPH_FIXINTERVALOFHALTER = Integer.parseInt(bossSettings.getProperty("FixIntervalOfHalter", "172800"));
			
			if (HPH_FIXINTERVALOFHALTER < 300 || HPH_FIXINTERVALOFHALTER > 864000)
				HPH_FIXINTERVALOFHALTER = 172800;
			HPH_FIXINTERVALOFHALTER *= 6000;
			
			HPH_RANDOMINTERVALOFHALTER = Integer.parseInt(bossSettings.getProperty("RandomIntervalOfHalter", "86400"));
			if (HPH_RANDOMINTERVALOFHALTER < 300 || HPH_RANDOMINTERVALOFHALTER > 864000)
				HPH_RANDOMINTERVALOFHALTER = 86400;
			HPH_RANDOMINTERVALOFHALTER *= 6000;
			
			HPH_APPTIMEOFHALTER = Integer.parseInt(bossSettings.getProperty("AppTimeOfHalter", "20"));
			if (HPH_APPTIMEOFHALTER < 5 || HPH_APPTIMEOFHALTER > 60)
				HPH_APPTIMEOFHALTER = 20;
			HPH_APPTIMEOFHALTER *= 6000;
			
			HPH_ACTIVITYTIMEOFHALTER = Integer.parseInt(bossSettings.getProperty("ActivityTimeOfHalter", "21600"));
			if (HPH_ACTIVITYTIMEOFHALTER < 7200 || HPH_ACTIVITYTIMEOFHALTER > 86400)
				HPH_ACTIVITYTIMEOFHALTER = 21600;
			HPH_ACTIVITYTIMEOFHALTER *= 1000;
			
			HPH_FIGHTTIMEOFHALTER = Integer.parseInt(bossSettings.getProperty("FightTimeOfHalter", "7200"));
			if (HPH_FIGHTTIMEOFHALTER < 7200 || HPH_FIGHTTIMEOFHALTER > 21600)
				HPH_FIGHTTIMEOFHALTER = 7200;
			HPH_FIGHTTIMEOFHALTER *= 6000;
			
			HPH_CALLROYALGUARDHELPERCOUNT =
					Integer.parseInt(bossSettings.getProperty("CallRoyalGuardHelperCount", "6"));
			if (HPH_CALLROYALGUARDHELPERCOUNT < 1 || HPH_CALLROYALGUARDHELPERCOUNT > 6)
				HPH_CALLROYALGUARDHELPERCOUNT = 6;
			
			HPH_CALLROYALGUARDHELPERINTERVAL =
					Integer.parseInt(bossSettings.getProperty("CallRoyalGuardHelperInterval", "10"));
			if (HPH_CALLROYALGUARDHELPERINTERVAL < 1 || HPH_CALLROYALGUARDHELPERINTERVAL > 60)
				HPH_CALLROYALGUARDHELPERINTERVAL = 10;
			HPH_CALLROYALGUARDHELPERINTERVAL *= 6000;
			
			HPH_INTERVALOFDOOROFALTER = Integer.parseInt(bossSettings.getProperty("IntervalOfDoorOfAlter", "5400"));
			if (HPH_INTERVALOFDOOROFALTER < 60 || HPH_INTERVALOFDOOROFALTER > 5400)
				HPH_INTERVALOFDOOROFALTER = 5400;
			HPH_INTERVALOFDOOROFALTER *= 6000;
			
			HPH_TIMEOFLOCKUPDOOROFALTAR = Integer.parseInt(bossSettings.getProperty("TimeOfLockUpDoorOfAltar", "180"));
			if (HPH_TIMEOFLOCKUPDOOROFALTAR < 60 || HPH_TIMEOFLOCKUPDOOROFALTAR > 600)
				HPH_TIMEOFLOCKUPDOOROFALTAR = 180;
			HPH_TIMEOFLOCKUPDOOROFALTAR *= 6000;
			
			// Last imperial tomb
			LIT_REGISTRATION_MODE = Integer.parseInt(bossSettings.getProperty("RegistrationMode", "0"));
			LIT_REGISTRATION_TIME = Integer.parseInt(bossSettings.getProperty("RegistrationTime", "10"));
			LIT_MIN_PARTY_CNT = Integer.parseInt(bossSettings.getProperty("MinPartyCount", "4"));
			LIT_MAX_PARTY_CNT = Integer.parseInt(bossSettings.getProperty("MaxPartyCount", "5"));
			LIT_MIN_PLAYER_CNT = Integer.parseInt(bossSettings.getProperty("MinPlayerCount", "7"));
			LIT_MAX_PLAYER_CNT = Integer.parseInt(bossSettings.getProperty("MaxPlayerCount", "45"));
			LIT_TIME_LIMIT = Integer.parseInt(bossSettings.getProperty("TimeLimit", "35"));
		}
	}
	
	// *******************************************************************************************
	public static final String SAY_FILTER_FILE = "./config/sayfilter.txt";
	
	// *******************************************************************************************
	private static final class SayFilterConfig extends ConfigFileLoader
	{
		@Override
		protected String getFileName()
		{
			return resolveConfigFile(SAY_FILTER_FILE).getPath();
		}
		
		@Override
		protected String getName()
		{
			return "sayfilter";
		}
		
		@Override
		protected void loadReader(BufferedReader reader) throws Exception
		{
			try
			{
				FILTER_LIST = new Pattern[0];
				
				if (USE_SAY_FILTER)
				{
					for (String line; (line = reader.readLine()) != null;)
					{
						line = line.trim();
						
						if (line.length() == 0 || line.startsWith("#"))
							continue;
						
						final Pattern pattern = Pattern.compile(line);
						
						FILTER_LIST = ArrayUtils.add(FILTER_LIST, pattern);
					}
					
					_log.info("Say Filter: Loaded " + FILTER_LIST.length + " words");
				}
			}
			finally
			{
				IOUtils.closeQuietly(reader);
			}
		}
	}
	
	// *******************************************************************************************
	public static final String ELAYNE_FILE = "./config/elayne.properties";
	// *******************************************************************************************
	public static boolean ALLOW_RMI_SERVER;
	public static String RMI_SERVER_PASSWORD;
	public static int RMI_SERVER_PORT;
	
	// *******************************************************************************************
	// *******************************************************************************************
	private static final class ElayneConfig extends ConfigPropertiesLoader
	{
		@Override
		protected String getName()
		{
			return "elayne";
		}
		
		@Override
		protected void loadImpl(L2Properties elayneSettings)
		{
			ALLOW_RMI_SERVER = Boolean.valueOf(elayneSettings.getProperty("AllowRMIServer", "False"));
			RMI_SERVER_PASSWORD = elayneSettings.getProperty("RMIServerPassword", "");
			RMI_SERVER_PORT = Integer.parseInt(elayneSettings.getProperty("RMIServerPort", "1099"));
		}
	}
	
	// *******************************************************************************************
	
	// *******************************************************************************************
	public static final String VITALITY_FILE = "./config/vitality.properties";
	// *******************************************************************************************
	public static boolean ENABLE_VITALITY;
	public static boolean RECOVER_VITALITY_ON_RECONNECT;
	public static boolean ENABLE_VITALITY_CHAMPION;
	public static boolean ENABLE_DROP_VITALITY_HERBS;
	public static float RATE_VITALITY_LEVEL_1;
	public static float RATE_VITALITY_LEVEL_2;
	public static float RATE_VITALITY_LEVEL_3;
	public static float RATE_VITALITY_LEVEL_4;
	public static float RATE_DROP_VITALITY_HERBS;
	public static float RATE_RECOVERY_VITALITY_PEACE_ZONE;
	public static float RATE_VITALITY_LOST;
	public static float RATE_VITALITY_GAIN;
	public static float RATE_RECOVERY_ON_RECONNECT;
	
	// *******************************************************************************************
	private static final class VitalityConfig extends ConfigPropertiesLoader
	{
		@Override
		protected String getName()
		{
			return "vitality";
		}
		
		@Override
		protected void loadImpl(L2Properties vitalitySettings)
		{
			ENABLE_VITALITY = Boolean.parseBoolean(vitalitySettings.getProperty("EnableVitality", "False"));
			RECOVER_VITALITY_ON_RECONNECT =
					Boolean.parseBoolean(vitalitySettings.getProperty("RecoverVitalityOnReconnect", "True"));
			ENABLE_VITALITY_CHAMPION =
					Boolean.parseBoolean(vitalitySettings.getProperty("EnableVitalityOnChampion", "False"));
			ENABLE_DROP_VITALITY_HERBS =
					Boolean.parseBoolean(vitalitySettings.getProperty("EnableVitalityHerbs", "True"));
			RATE_VITALITY_LEVEL_1 = Float.parseFloat(vitalitySettings.getProperty("RateVitalityLevel1", "1.5"));
			RATE_VITALITY_LEVEL_2 = Float.parseFloat(vitalitySettings.getProperty("RateVitalityLevel2", "2."));
			RATE_VITALITY_LEVEL_3 = Float.parseFloat(vitalitySettings.getProperty("RateVitalityLevel3", "2.5"));
			RATE_VITALITY_LEVEL_4 = Float.parseFloat(vitalitySettings.getProperty("RateVitalityLevel4", "3."));
			RATE_DROP_VITALITY_HERBS = Float.parseFloat(vitalitySettings.getProperty("RateVitalityHerbs", "2."));
			RATE_RECOVERY_VITALITY_PEACE_ZONE =
					Float.parseFloat(vitalitySettings.getProperty("RateRecoveryPeaceZone", "1."));
			RATE_VITALITY_LOST = Float.parseFloat(vitalitySettings.getProperty("RateVitalityLost", "1."));
			RATE_VITALITY_GAIN = Float.parseFloat(vitalitySettings.getProperty("RateVitalityGain", "1."));
			RATE_RECOVERY_ON_RECONNECT =
					Float.parseFloat(vitalitySettings.getProperty("RateRecoveryOnReconnect", "4."));
		}
	}
	
	public static boolean AUTO_TVT_ENABLED;
	public static Location[] AUTO_TVT_TEAM_LOCATIONS;
	public static boolean AUTO_TVT_TEAM_COLORS_RANDOM;
	public static int[] AUTO_TVT_TEAM_COLORS;
	public static boolean AUTO_TVT_OVERRIDE_TELE_BACK;
	public static Location AUTO_TVT_DEFAULT_TELE_BACK;
	public static int AUTO_TVT_REWARD_MIN_POINTS;
	public static int[] AUTO_TVT_REWARD_IDS;
	public static int[] AUTO_TVT_REWARD_COUNT;
	public static int AUTO_TVT_LEVEL_MAX;
	public static int AUTO_TVT_LEVEL_MIN;
	public static int AUTO_TVT_PARTICIPANTS_MAX;
	public static int AUTO_TVT_PARTICIPANTS_MIN;
	public static long AUTO_TVT_DELAY_INITIAL_REGISTRATION;
	public static long AUTO_TVT_DELAY_BETWEEN_EVENTS;
	public static long AUTO_TVT_PERIOD_LENGHT_REGISTRATION;
	public static long AUTO_TVT_PERIOD_LENGHT_PREPARATION;
	public static long AUTO_TVT_PERIOD_LENGHT_EVENT;
	public static long AUTO_TVT_PERIOD_LENGHT_REWARDS;
	public static int AUTO_TVT_REGISTRATION_ANNOUNCEMENT_COUNT;
	public static boolean AUTO_TVT_REGISTER_CURSED;
	public static boolean AUTO_TVT_REGISTER_HERO;
	public static boolean AUTO_TVT_REGISTER_CANCEL;
	public static boolean AUTO_TVT_REGISTER_AFTER_RELOG;
	public static int[] AUTO_TVT_DISALLOWED_ITEMS;
	public static boolean AUTO_TVT_START_CANCEL_BUFFS;
	public static boolean AUTO_TVT_START_CANCEL_CUBICS;
	public static boolean AUTO_TVT_START_CANCEL_SERVITORS;
	public static boolean AUTO_TVT_START_CANCEL_TRANSFORMATION;
	public static boolean AUTO_TVT_START_CANCEL_PARTY;
	public static boolean AUTO_TVT_START_RECOVER;
	public static boolean AUTO_TVT_GODLIKE_SYSTEM;
	public static boolean AUTO_TVT_GODLIKE_ANNOUNCE;
	public static int AUTO_TVT_GODLIKE_MIN_KILLS;
	public static int AUTO_TVT_GODLIKE_POINT_MULTIPLIER;
	public static String AUTO_TVT_GODLIKE_TITLE;
	public static boolean AUTO_TVT_TK_PUNISH;
	public static boolean AUTO_TVT_TK_PUNISH_CANCEL;
	public static boolean AUTO_TVT_TK_RESET_GODLIKE;
	public static int AUTO_TVT_TK_PUNISH_POINTS_LOST;
	public static L2Skill[] AUTO_TVT_TK_PUNISH_EFFECTS;
	public static boolean AUTO_TVT_REVIVE_SELF;
	public static boolean AUTO_TVT_REVIVE_RECOVER;
	public static long AUTO_TVT_REVIVE_DELAY;
	public static int AUTO_TVT_SPAWN_PROTECT;
	
	private static final class AutoEventConfig extends ConfigPropertiesLoader
	{
		@Override
		protected String getName()
		{
			return "autoevent";
		}
		
		@Override
		protected void loadImpl(L2Properties properties)
		{
			AUTO_TVT_ENABLED = Boolean.parseBoolean(properties.getProperty("EnableAutoTvT", "false"));
			
			if (AUTO_TVT_ENABLED)
				AutomatedTvT.startIfNecessary();
			StringTokenizer coords;
			StringTokenizer locations = new StringTokenizer(properties.getProperty("TvTTeamLocations", ""), ";");
			AUTO_TVT_TEAM_LOCATIONS = new Location[locations.countTokens()];
			for (int i = 0; i < AUTO_TVT_TEAM_LOCATIONS.length; i++)
			{
				coords = new StringTokenizer(locations.nextToken(), ",");
				if (coords.countTokens() == 3)
				{
					int x = Integer.parseInt(coords.nextToken());
					int y = Integer.parseInt(coords.nextToken());
					int z = Integer.parseInt(coords.nextToken());
					
					AUTO_TVT_TEAM_LOCATIONS[i] = new Location(x, y, z);
				}
				else
					throw new IllegalArgumentException("Cannot parse TvTTeamLocations!");
			}
			AUTO_TVT_TEAM_COLORS_RANDOM = Boolean.parseBoolean(properties.getProperty("TvTTeamColorsRandom", "true"));
			AUTO_TVT_TEAM_COLORS = new int[AUTO_TVT_TEAM_LOCATIONS.length];
			if (!AUTO_TVT_TEAM_COLORS_RANDOM)
			{
				coords = new StringTokenizer(properties.getProperty("TvTTeamColors", ""), ",");
				if (coords.countTokens() == AUTO_TVT_TEAM_COLORS.length)
				{
					for (int i = 0; i < AUTO_TVT_TEAM_COLORS.length; i++)
						AUTO_TVT_TEAM_COLORS[i] = Integer.decode("0x" + coords.nextToken());
				}
				else
					throw new IllegalArgumentException("Cannot parse TvTTeamColors!");
			}
			AUTO_TVT_OVERRIDE_TELE_BACK =
					Boolean.parseBoolean(properties.getProperty("TvTTeleportAfterEventSpecial", "false"));
			coords = new StringTokenizer(properties.getProperty("TvTTeleportSpecialLocation", ""), ",");
			if (coords.countTokens() == 3)
			{
				int x = Integer.parseInt(coords.nextToken());
				int y = Integer.parseInt(coords.nextToken());
				int z = Integer.parseInt(coords.nextToken());
				
				AUTO_TVT_DEFAULT_TELE_BACK = new Location(x, y, z);
			}
			else
				throw new IllegalArgumentException("Cannot parse TvTTeleportSpecialLocation!");
			AUTO_TVT_REWARD_MIN_POINTS = Integer.parseInt(properties.getProperty("TvTRewardedMinScore", "1"));
			locations = new StringTokenizer(properties.getProperty("TvTRewards", ""), ";");
			AUTO_TVT_REWARD_IDS = new int[locations.countTokens()];
			AUTO_TVT_REWARD_COUNT = new int[locations.countTokens()];
			for (int i = 0; i < AUTO_TVT_REWARD_IDS.length; i++)
			{
				coords = new StringTokenizer(locations.nextToken(), ",");
				if (coords.countTokens() == 2)
				{
					AUTO_TVT_REWARD_IDS[i] = Integer.parseInt(coords.nextToken());
					AUTO_TVT_REWARD_COUNT[i] = Integer.parseInt(coords.nextToken());
				}
				else
					throw new IllegalArgumentException("Cannot parse TvTRewards!");
			}
			AUTO_TVT_LEVEL_MAX = Integer.parseInt(properties.getProperty("TvTMaxLevel", "85"));
			AUTO_TVT_LEVEL_MIN = Integer.parseInt(properties.getProperty("TvTMinLevel", "1"));
			AUTO_TVT_PARTICIPANTS_MAX = Integer.parseInt(properties.getProperty("TvTMaxParticipants", "25"));
			AUTO_TVT_PARTICIPANTS_MIN = Integer.parseInt(properties.getProperty("TvTMinParticipants", "6"));
			AUTO_TVT_DELAY_INITIAL_REGISTRATION = Long.parseLong(properties.getProperty("TvTDelayInitial", "900000"));
			AUTO_TVT_DELAY_BETWEEN_EVENTS = Long.parseLong(properties.getProperty("TvTDelayBetweenEvents", "900000"));
			AUTO_TVT_PERIOD_LENGHT_REGISTRATION =
					Long.parseLong(properties.getProperty("TvTLengthRegistration", "300000"));
			AUTO_TVT_PERIOD_LENGHT_PREPARATION =
					Long.parseLong(properties.getProperty("TvTLengthPreparation", "50000"));
			AUTO_TVT_PERIOD_LENGHT_EVENT = Long.parseLong(properties.getProperty("TvTLengthCombat", "240000"));
			AUTO_TVT_PERIOD_LENGHT_REWARDS = Long.parseLong(properties.getProperty("TvTLengthRewards", "15000"));
			AUTO_TVT_REGISTRATION_ANNOUNCEMENT_COUNT =
					Integer.parseInt(properties.getProperty("TvTAnnounceRegistration", "3"));
			AUTO_TVT_REGISTER_CURSED =
					Boolean.parseBoolean(properties.getProperty("TvTRegisterCursedWeaponOwner", "false"));
			AUTO_TVT_REGISTER_HERO = Boolean.parseBoolean(properties.getProperty("TvTRegisterHero", "true"));
			AUTO_TVT_REGISTER_CANCEL =
					Boolean.parseBoolean(properties.getProperty("TvTCanCancelRegistration", "false"));
			AUTO_TVT_REGISTER_AFTER_RELOG =
					Boolean.parseBoolean(properties.getProperty("TvTRegisterOnRelogin", "true"));
			coords = new StringTokenizer(properties.getProperty("TvTItemsNotAllowed", ""), ",");
			AUTO_TVT_DISALLOWED_ITEMS = new int[coords.countTokens()];
			for (int i = 0; i < AUTO_TVT_DISALLOWED_ITEMS.length; i++)
				AUTO_TVT_DISALLOWED_ITEMS[i] = Integer.parseInt(coords.nextToken());
			AUTO_TVT_START_CANCEL_BUFFS =
					Boolean.parseBoolean(properties.getProperty("TvTOnStartCancelBuffs", "false"));
			AUTO_TVT_START_CANCEL_CUBICS =
					Boolean.parseBoolean(properties.getProperty("TvTOnStartCancelCubics", "false"));
			AUTO_TVT_START_CANCEL_SERVITORS =
					Boolean.parseBoolean(properties.getProperty("TvTOnStartCancelServitors", "true"));
			AUTO_TVT_START_CANCEL_TRANSFORMATION =
					Boolean.parseBoolean(properties.getProperty("TvTOnStartCancelTransformation", "true"));
			AUTO_TVT_START_CANCEL_PARTY =
					Boolean.parseBoolean(properties.getProperty("TvTOnStartDisbandParty", "true"));
			AUTO_TVT_START_RECOVER = Boolean.parseBoolean(properties.getProperty("TvTOnStartRecover", "true"));
			AUTO_TVT_GODLIKE_SYSTEM = Boolean.parseBoolean(properties.getProperty("TvTGodlikeSystem", "true"));
			AUTO_TVT_GODLIKE_ANNOUNCE = Boolean.parseBoolean(properties.getProperty("TvTGodlikeAnnounce", "true"));
			AUTO_TVT_GODLIKE_MIN_KILLS = Integer.parseInt(properties.getProperty("TvTGodlikeMinKills", "5"));
			AUTO_TVT_GODLIKE_POINT_MULTIPLIER = Integer.parseInt(properties.getProperty("TvTGodlikeKillPoints", "3"));
			AUTO_TVT_GODLIKE_TITLE = properties.getProperty("TvTGodlikeTitle", "God-like").trim();
			AUTO_TVT_TK_PUNISH = Boolean.parseBoolean(properties.getProperty("TvTPunishTeamKillers", "true"));
			AUTO_TVT_TK_PUNISH_CANCEL = Boolean.parseBoolean(properties.getProperty("TvTPunishTKCancelBuffs", "true"));
			AUTO_TVT_TK_RESET_GODLIKE = Boolean.parseBoolean(properties.getProperty("TvTPunishTKResetGodlike", "true"));
			AUTO_TVT_TK_PUNISH_POINTS_LOST = Integer.parseInt(properties.getProperty("TvTPunishTKDecreaseScore", "5"));
			final StringTokenizer skills = new StringTokenizer(properties.getProperty("TvTPunishTKDebuff", ""), ";");
			AUTO_TVT_TK_PUNISH_EFFECTS = new L2Skill[skills.countTokens()];
			StartupManager.addStartupHook(new StartupHook() {
				@Override
				public void onStartup()
				{
					for (int i = 0; i < AUTO_TVT_TK_PUNISH_EFFECTS.length; i++)
					{
						StringTokenizer skill = new StringTokenizer(skills.nextToken(), ",");
						if (skill.countTokens() == 2)
						{
							AUTO_TVT_TK_PUNISH_EFFECTS[i] =
									SkillTable.getInstance().getInfo(Integer.parseInt(skill.nextToken()),
											Integer.parseInt(skill.nextToken()));
						}
						else
							throw new IllegalArgumentException("Cannot parse TvTPunishTKDebuff!");
					}
				}
			});
			AUTO_TVT_REVIVE_SELF = Boolean.parseBoolean(properties.getProperty("TvTReviveSelf", "false"));
			AUTO_TVT_REVIVE_RECOVER = Boolean.parseBoolean(properties.getProperty("TvTReviveRecover", "true"));
			AUTO_TVT_REVIVE_DELAY = Long.parseLong(properties.getProperty("TvTReviveDelay", "5000"));
			AUTO_TVT_SPAWN_PROTECT = Integer.parseInt(properties.getProperty("TvTSpawnProtection", "0"));
		}
	}
	
	// *******************************************************************************************
	public static final String CUSTOM_FILE = "./config/custom.properties";
	
	// *******************************************************************************************
	// FOR YOUR PRIVATE CONFIGS
	// *******************************************************************************************
	private static final class CustomConfig extends ConfigPropertiesLoader
	{
		@Override
		protected String getName()
		{
			return "custom";
		}
		
		@Override
		protected void loadImpl(L2Properties customSettings)
		{
		}
	}
	
	public static class ClassMasterSettings
	{
		private final Map<Integer, Map<Integer, Integer>> _claimItems;
		private final Map<Integer, Map<Integer, Integer>> _rewardItems;
		private final Map<Integer, Boolean> _allowedClassChange;
		
		public ClassMasterSettings(String _configLine)
		{
			_claimItems = new LinkedHashMap<Integer, Map<Integer, Integer>>();
			_rewardItems = new LinkedHashMap<Integer, Map<Integer, Integer>>();
			_allowedClassChange = new LinkedHashMap<Integer, Boolean>();
			if (_configLine != null)
				parseConfigLine(_configLine.trim());
		}
		
		private void parseConfigLine(String _configLine)
		{
			StringTokenizer st = new StringTokenizer(_configLine, ";");
			
			while (st.hasMoreTokens())
			{
				// get allowed class change
				int job = Integer.parseInt(st.nextToken());
				
				_allowedClassChange.put(job, true);
				
				Map<Integer, Integer> _items = new LinkedHashMap<Integer, Integer>();
				// parse items needed for class change
				if (st.hasMoreTokens())
				{
					StringTokenizer st2 = new StringTokenizer(st.nextToken(), "[],");
					
					while (st2.hasMoreTokens())
					{
						StringTokenizer st3 = new StringTokenizer(st2.nextToken(), "()");
						int _itemId = Integer.parseInt(st3.nextToken());
						int _quantity = Integer.parseInt(st3.nextToken());
						_items.put(_itemId, _quantity);
					}
				}
				
				_claimItems.put(job, _items);
				
				_items = new LinkedHashMap<Integer, Integer>();
				// parse gifts after class change
				if (st.hasMoreTokens())
				{
					StringTokenizer st2 = new StringTokenizer(st.nextToken(), "[],");
					
					while (st2.hasMoreTokens())
					{
						StringTokenizer st3 = new StringTokenizer(st2.nextToken(), "()");
						int _itemId = Integer.parseInt(st3.nextToken());
						int _quantity = Integer.parseInt(st3.nextToken());
						_items.put(_itemId, _quantity);
					}
				}
				
				_rewardItems.put(job, _items);
			}
		}
		
		public boolean isAllowed(int job)
		{
			if (_allowedClassChange == null)
				return false;
			if (_allowedClassChange.containsKey(job))
				return _allowedClassChange.get(job);
			
			return false;
		}
		
		public Map<Integer, Integer> getRewardItems(int job)
		{
			if (_rewardItems.containsKey(job))
				return _rewardItems.get(job);
			
			return null;
		}
		
		public Map<Integer, Integer> getRequireItems(int job)
		{
			if (_claimItems.containsKey(job))
				return _claimItems.get(job);
			
			return null;
		}
		
	}
	
	public static void load() throws Exception
	{
		Util.printSection("Configuration");
		loadConfiguration();
		loadSubnets();
		loadTelnetConfig();
		loadIdFactoryConfig();
		loadDateTimeConfig();
		initDBProperties();
		
		L2AutoInitialization.loadConfigs();
		
		registerConfig(new AllConfig());
		
		if (L2AutoInitialization.isIDEMode())
			Config.DATAPACK_ROOT = new File("../../../world");
	}
	
	private static final class AllConfig extends ConfigLoader
	{
		@Override
		protected String getName()
		{
			return "all";
		}
		
		private boolean _reloading = false;
		
		@Override
		protected void load() throws Exception
		{
			if (_reloading)
				return;
			
			_reloading = true;
			try
			{
				Config.load();
			}
			finally
			{
				_reloading = false;
			}
		}
	}
	
	/**
	 * Set a new value to a game parameter from the admin console.
	 *
	 * @param pName (String) : name of the parameter to change
	 * @param pValue (String) : new value of the parameter
	 * @return boolean : true if modification has been made
	 * @link useAdminCommand
	 */
	public static boolean setParameterValue(String pName, String pValue)
	{
		// Server settings
		if (pName.equalsIgnoreCase("RateXp"))
			RATE_XP = Float.parseFloat(pValue);
		else if (pName.equalsIgnoreCase("RateSp"))
			RATE_SP = Float.parseFloat(pValue);
		else if (pName.equalsIgnoreCase("RatePartyXp"))
			RATE_PARTY_XP = Float.parseFloat(pValue);
		else if (pName.equalsIgnoreCase("RatePartySp"))
			RATE_PARTY_SP = Float.parseFloat(pValue);
		else if (pName.equalsIgnoreCase("RateQuestsRewardExpSp"))
			RATE_QUESTS_REWARD_EXPSP = Float.parseFloat(pValue);
		else if (pName.equalsIgnoreCase("RateQuestsRewardAdena"))
			RATE_QUESTS_REWARD_ADENA = Float.parseFloat(pValue);
		else if (pName.equalsIgnoreCase("RateQuestsRewardItems"))
			RATE_QUESTS_REWARD_ITEMS = Float.parseFloat(pValue);
		else if (pName.equalsIgnoreCase("RateDropAdena"))
			RATE_DROP_ADENA = Float.parseFloat(pValue);
		else if (pName.equalsIgnoreCase("RateConsumableCost"))
			RATE_CONSUMABLE_COST = Float.parseFloat(pValue);
		else if (pName.equalsIgnoreCase("RateDropItems"))
			RATE_DROP_ITEMS = Float.parseFloat(pValue);
		else if (pName.equalsIgnoreCase("RateDropSpoil"))
			RATE_DROP_SPOIL = Float.parseFloat(pValue);
		else if (pName.equalsIgnoreCase("RateDropManor"))
			RATE_DROP_MANOR = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("RateDropQuest"))
			RATE_DROP_QUEST = Float.parseFloat(pValue);
		else if (pName.equalsIgnoreCase("RateKarmaExpLost"))
			RATE_KARMA_EXP_LOST = Float.parseFloat(pValue);
		else if (pName.equalsIgnoreCase("RateSiegeGuardsPrice"))
			RATE_SIEGE_GUARDS_PRICE = Float.parseFloat(pValue);
		
		else if (pName.equalsIgnoreCase("PlayerDropLimit"))
			PLAYER_DROP_LIMIT = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("PlayerRateDrop"))
			PLAYER_RATE_DROP = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("PlayerRateDropItem"))
			PLAYER_RATE_DROP_ITEM = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("PlayerRateDropEquip"))
			PLAYER_RATE_DROP_EQUIP = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("PlayerRateDropEquipWeapon"))
			PLAYER_RATE_DROP_EQUIP_WEAPON = Integer.parseInt(pValue);
		
		else if (pName.equalsIgnoreCase("KarmaDropLimit"))
			KARMA_DROP_LIMIT = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("KarmaRateDrop"))
			KARMA_RATE_DROP = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("KarmaRateDropItem"))
			KARMA_RATE_DROP_ITEM = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("KarmaRateDropEquip"))
			KARMA_RATE_DROP_EQUIP = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("KarmaRateDropEquipWeapon"))
			KARMA_RATE_DROP_EQUIP_WEAPON = Integer.parseInt(pValue);
		
		else if (pName.equalsIgnoreCase("AutoDestroyDroppedItemAfter"))
			AUTODESTROY_ITEM_AFTER = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("SaveDroppedItem"))
			SAVE_DROPPED_ITEM = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("CoordSynchronize"))
			COORD_SYNCHRONIZE = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("DeleteCharAfterDays"))
			DELETE_DAYS = Integer.parseInt(pValue);
		
		else if (pName.equalsIgnoreCase("ChanceToBreak"))
			ALT_CHANCE_BREAK = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("ChanceToLevel"))
			ALT_CHANCE_LEVEL = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("AllowDiscardItem"))
			ALLOW_DISCARDITEM = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("ChampionFrequency"))
			CHAMPION_FREQUENCY = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("ChampionHp"))
			CHAMPION_HP = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("ChampionHpRegen"))
			CHAMPION_HP_REGEN = Float.parseFloat(pValue);
		else if (pName.equalsIgnoreCase("ChampionAtk"))
			CHAMPION_ATK = Float.parseFloat(pValue);
		else if (pName.equalsIgnoreCase("ChampionSpdAtk"))
			CHAMPION_SPD_ATK = Float.parseFloat(pValue);
		else if (pName.equalsIgnoreCase("ChampionRewards"))
			CHAMPION_REWARDS = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("ChampionAdenasRewards"))
			CHAMPION_ADENA = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("ChampionExpSp"))
			CHAMPION_EXP_SP = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("ChampionBoss"))
			CHAMPION_BOSS = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("ChampionMinLevel"))
			CHAMPION_MIN_LEVEL = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("ChampionMaxLevel"))
			CHAMPION_MAX_LEVEL = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("ChampionMinions"))
			CHAMPION_MINIONS = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("ChampionSpecialItemLevelDiff"))
			CHAMPION_SPCL_LVL_DIFF = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("ChampionSpecialItemChance"))
			CHAMPION_SPCL_CHANCE = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("ChampionSpecialItemID"))
			CHAMPION_SPCL_ITEM = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("ChampionSpecialItemAmount"))
			CHAMPION_SPCL_QTY = Integer.parseInt(pValue);
		
		else if (pName.equalsIgnoreCase("AllowFreight"))
			ALLOW_FREIGHT = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("AllowWarehouse"))
			ALLOW_WAREHOUSE = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("AllowWear"))
			ALLOW_WEAR = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("WearDelay"))
			WEAR_DELAY = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("WearPrice"))
			WEAR_PRICE = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("AllowWater"))
			ALLOW_WATER = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("AllowRentPet"))
			ALLOW_RENTPET = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("CommunityType"))
			COMMUNITY_TYPE = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("BBSShowPlayerList"))
			BBS_SHOW_PLAYERLIST = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("BBSDefault"))
			BBS_DEFAULT = pValue;
		else if (pName.equalsIgnoreCase("AllowBoat"))
			ALLOW_BOAT = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("AllowCursedWeapons"))
			ALLOW_CURSED_WEAPONS = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("AllowManor"))
			ALLOW_MANOR = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("AllowPetWalkers"))
			ALLOW_PET_WALKERS = Boolean.parseBoolean(pValue);
		
		else if (pName.equalsIgnoreCase("ShowLevelOnCommunityBoard"))
			SHOW_LEVEL_COMMUNITYBOARD = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("ShowStatusOnCommunityBoard"))
			SHOW_STATUS_COMMUNITYBOARD = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("NamePageSizeOnCommunityBoard"))
			NAME_PAGE_SIZE_COMMUNITYBOARD = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("NamePerRowOnCommunityBoard"))
			NAME_PER_ROW_COMMUNITYBOARD = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("ShowCursedWeaponOwner"))
			SHOW_CURSED_WEAPON_OWNER = Boolean.parseBoolean(pValue);
		
		else if (pName.equalsIgnoreCase("ShowNpcLevel"))
			SHOW_NPC_LVL = Boolean.parseBoolean(pValue);
		
		else if (pName.equalsIgnoreCase("ForceInventoryUpdate"))
			FORCE_INVENTORY_UPDATE = Boolean.parseBoolean(pValue);
		
		else if (pName.equalsIgnoreCase("AutoDeleteInvalidQuestData"))
			AUTODELETE_INVALID_QUEST_DATA = Boolean.parseBoolean(pValue);
		
		else if (pName.equalsIgnoreCase("MaximumOnlineUsers"))
			MAXIMUM_ONLINE_USERS = Integer.parseInt(pValue);
		
		else if (pName.equalsIgnoreCase("ZoneTown"))
			ZONE_TOWN = Integer.parseInt(pValue);
		
		else if (pName.equalsIgnoreCase("ShowGMLogin"))
			SHOW_GM_LOGIN = Boolean.parseBoolean(pValue);
		
		// Other settings
		else if (pName.equalsIgnoreCase("UseDeepBlueDropRules"))
			DEEPBLUE_DROP_RULES = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("UseDeepBlueDropRulesRaid"))
			DEEPBLUE_DROP_RULES_RAID = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("CancelLesserEffect"))
			EFFECT_CANCELING = Boolean.parseBoolean(pValue);
		
		else if (pName.equalsIgnoreCase("MaximumSlotsForNoDwarf"))
			INVENTORY_MAXIMUM_NO_DWARF = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("MaximumSlotsForDwarf"))
			INVENTORY_MAXIMUM_DWARF = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("MaximumSlotsForGMPlayer"))
			INVENTORY_MAXIMUM_GM = Integer.parseInt(pValue);
		
		else if (pName.equalsIgnoreCase("MaximumWarehouseSlotsForNoDwarf"))
			WAREHOUSE_SLOTS_NO_DWARF = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("MaximumWarehouseSlotsForDwarf"))
			WAREHOUSE_SLOTS_DWARF = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("MaximumWarehouseSlotsForClan"))
			WAREHOUSE_SLOTS_CLAN = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("MaximumFreightSlots"))
			FREIGHT_SLOTS = Integer.parseInt(pValue);
		
		else if (pName.equalsIgnoreCase("EnchantChanceWeapon"))
			ENCHANT_CHANCE_WEAPON = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("EnchantChanceArmor"))
			ENCHANT_CHANCE_ARMOR = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("AllowCrystalScroll"))
			ALLOW_CRYSTAL_SCROLL = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("EnchantChanceWeaponCrystal"))
			ENCHANT_CHANCE_WEAPON_CRYSTAL = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("EnchantChanceArmorCrystal"))
			ENCHANT_CHANCE_ARMOR_CRYSTAL = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("EnchantChanceWeaponBlessed"))
			BLESSED_ENCHANT_CHANCE_WEAPON = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("EnchantChanceArmorBlessed"))
			BLESSED_ENCHANT_CHANCE_ARMOR = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("EnchantChanceJewelryBlessed"))
			BLESSED_ENCHANT_CHANCE_JEWELRY = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("EnchantChanceElement"))
			ENCHANT_CHANCE_ELEMENT = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("EnchantMaxWeapon"))
			ENCHANT_MAX_WEAPON = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("EnchantMaxArmor"))
			ENCHANT_MAX_ARMOR = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("EnchantSafeMax"))
			ENCHANT_SAFE_MAX = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("EnchantSafeMaxFull"))
			ENCHANT_SAFE_MAX_FULL = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("EnchantDwarf1Enchantlevel"))
			ENCHANT_DWARF_1_ENCHANTLEVEL = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("EnchantDwarf2Enchantlevel"))
			ENCHANT_DWARF_2_ENCHANTLEVEL = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("EnchantDwarf3Enchantlevel"))
			ENCHANT_DWARF_3_ENCHANTLEVEL = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("EnchantDwarf1Chance"))
			ENCHANT_DWARF_1_CHANCE = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("EnchantDwarf2Chance"))
			ENCHANT_DWARF_2_CHANCE = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("EnchantDwarf3Chance"))
			ENCHANT_DWARF_3_CHANCE = Integer.parseInt(pValue);
		
		else if (pName.equalsIgnoreCase("AugmentationNGSkillChance"))
			AUGMENTATION_NG_SKILL_CHANCE = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("AugmentationNGGlowChance"))
			AUGMENTATION_NG_GLOW_CHANCE = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("AugmentationMidSkillChance"))
			AUGMENTATION_MID_SKILL_CHANCE = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("AugmentationMidGlowChance"))
			AUGMENTATION_MID_GLOW_CHANCE = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("AugmentationHighSkillChance"))
			AUGMENTATION_HIGH_SKILL_CHANCE = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("AugmentationHighGlowChance"))
			AUGMENTATION_HIGH_GLOW_CHANCE = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("AugmentationTopSkillChance"))
			AUGMENTATION_TOP_SKILL_CHANCE = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("AugmentationTopGlowChance"))
			AUGMENTATION_TOP_GLOW_CHANCE = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("AugmentationBaseStatChance"))
			AUGMENTATION_BASESTAT_CHANCE = Integer.parseInt(pValue);
		
		else if (pName.equalsIgnoreCase("NPCHpRegenMultiplier"))
			NPC_HP_REGEN_MULTIPLIER = Double.parseDouble(pValue);
		else if (pName.equalsIgnoreCase("NPCMpRegenMultiplier"))
			NPC_MP_REGEN_MULTIPLIER = Double.parseDouble(pValue);
		else if (pName.equalsIgnoreCase("PlayerHpRegenMultiplier"))
			PLAYER_HP_REGEN_MULTIPLIER = Double.parseDouble(pValue);
		else if (pName.equalsIgnoreCase("PlayerMpRegenMultiplier"))
			PLAYER_MP_REGEN_MULTIPLIER = Double.parseDouble(pValue);
		else if (pName.equalsIgnoreCase("PlayerCpRegenMultiplier"))
			PLAYER_CP_REGEN_MULTIPLIER = Double.parseDouble(pValue);
		
		else if (pName.equalsIgnoreCase("RaidHpRegenMultiplier"))
			RAID_HP_REGEN_MULTIPLIER = Double.parseDouble(pValue);
		else if (pName.equalsIgnoreCase("RaidMpRegenMultiplier"))
			RAID_MP_REGEN_MULTIPLIER = Double.parseDouble(pValue);
		else if (pName.equalsIgnoreCase("RaidPDefenceMultiplier"))
			RAID_PDEFENCE_MULTIPLIER = Double.parseDouble(pValue) / 100;
		else if (pName.equalsIgnoreCase("RaidMDefenceMultiplier"))
			RAID_MDEFENCE_MULTIPLIER = Double.parseDouble(pValue) / 100;
		else if (pName.equalsIgnoreCase("RaidMinionRespawnTime"))
			RAID_MINION_RESPAWN_TIMER = Integer.parseInt(pValue);
		
		else if (pName.equalsIgnoreCase("StartingAdena"))
			STARTING_ADENA = Long.parseLong(pValue);
		else if (pName.equalsIgnoreCase("StartingLevel"))
			STARTING_LEVEL = Byte.parseByte(pValue);
		else if (pName.equalsIgnoreCase("StartingSP"))
			STARTING_SP = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("UnstuckInterval"))
			UNSTUCK_INTERVAL = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("TeleportWatchdogTimeout"))
			TELEPORT_WATCHDOG_TIMEOUT = Integer.parseInt(pValue);
		
		else if (pName.equalsIgnoreCase("PlayerSpawnProtection"))
			PLAYER_SPAWN_PROTECTION = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("PlayerFakeDeathUpProtection"))
			PLAYER_FAKEDEATH_UP_PROTECTION = Integer.parseInt(pValue);
		
		else if (pName.equalsIgnoreCase("PartyXpCutoffMethod"))
			PARTY_XP_CUTOFF_METHOD = pValue;
		else if (pName.equalsIgnoreCase("PartyXpCutoffPercent"))
			PARTY_XP_CUTOFF_PERCENT = Double.parseDouble(pValue);
		else if (pName.equalsIgnoreCase("PartyXpCutoffLevel"))
			PARTY_XP_CUTOFF_LEVEL = Integer.parseInt(pValue);
		
		else if (pName.equalsIgnoreCase("RespawnRestoreCP"))
			RESPAWN_RESTORE_CP = Double.parseDouble(pValue) / 100;
		else if (pName.equalsIgnoreCase("RespawnRestoreHP"))
			RESPAWN_RESTORE_HP = Double.parseDouble(pValue) / 100;
		else if (pName.equalsIgnoreCase("RespawnRestoreMP"))
			RESPAWN_RESTORE_MP = Double.parseDouble(pValue) / 100;
		
		else if (pName.equalsIgnoreCase("MaxPvtStoreSellSlotsDwarf"))
			MAX_PVTSTORESELL_SLOTS_DWARF = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("MaxPvtStoreSellSlotsOther"))
			MAX_PVTSTORESELL_SLOTS_OTHER = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("MaxPvtStoreBuySlotsDwarf"))
			MAX_PVTSTOREBUY_SLOTS_DWARF = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("MaxPvtStoreBuySlotsOther"))
			MAX_PVTSTOREBUY_SLOTS_OTHER = Integer.parseInt(pValue);
		
		else if (pName.equalsIgnoreCase("StoreSkillCooltime"))
			STORE_EFFECTS = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("AnnounceMammonSpawn"))
			ANNOUNCE_MAMMON_SPAWN = Boolean.parseBoolean(pValue);
		
		// Alternative settings
		else if (pName.equalsIgnoreCase("AltGameTiredness"))
			ALT_GAME_TIREDNESS = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("AltGameCreation"))
			ALT_GAME_CREATION = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("AltGameCreationSpeed"))
			ALT_GAME_CREATION_SPEED = Double.parseDouble(pValue);
		else if (pName.equalsIgnoreCase("AltGameCreationXpRate"))
			ALT_GAME_CREATION_XP_RATE = Double.parseDouble(pValue);
		else if (pName.equalsIgnoreCase("AltGameCreationRareXpSpRate"))
			ALT_GAME_CREATION_RARE_XPSP_RATE = Double.parseDouble(pValue);
		else if (pName.equalsIgnoreCase("AltGameCreationSpRate"))
			ALT_GAME_CREATION_SP_RATE = Double.parseDouble(pValue);
		else if (pName.equalsIgnoreCase("AltWeightLimit"))
			ALT_WEIGHT_LIMIT = Double.parseDouble(pValue);
		else if (pName.equalsIgnoreCase("AltBlacksmithUseRecipes"))
			ALT_BLACKSMITH_USE_RECIPES = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("AltGameSkillLearn"))
			ALT_GAME_SKILL_LEARN = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("AltMinimumFallHeight"))
			ALT_MINIMUM_FALL_HEIGHT = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("AltNbCumulatedBuff"))
			ALT_BUFFS_MAX_AMOUNT = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("AltBuffTime"))
			ALT_BUFF_TIME = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("AltSuccessRate"))
			ALT_DAGGER_RATE = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("InstantKillEffect2"))
			ALT_INSTANT_KILL_EFFECT_2 = Float.parseFloat(pValue);
		else if (pName.equalsIgnoreCase("AltAttackDelay"))
			ALT_ATTACK_DELAY = Float.parseFloat(pValue);
		else if (pName.equalsIgnoreCase("AltFailRate"))
			ALT_DAGGER_FAIL_RATE = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("AltBehindRate"))
			ALT_DAGGER_RATE_BEHIND = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("AltFrontRate"))
			ALT_DAGGER_RATE_FRONT = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("AltDanceTime"))
			ALT_DANCE_TIME = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("MaxPAtkSpeed"))
			ALT_MAX_PATK_SPEED = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("MaxMAtkSpeed"))
			ALT_MAX_MATK_SPEED = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("GradePenalty"))
			ALT_GRADE_PENALTY = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("RemoveCastleCirclets"))
			ALT_REMOVE_CASTLE_CIRCLETS = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("AltGameCancelByHit"))
		{
			ALT_GAME_CANCEL_BOW = pValue.equalsIgnoreCase("bow") || pValue.equalsIgnoreCase("all");
			ALT_GAME_CANCEL_CAST = pValue.equalsIgnoreCase("cast") || pValue.equalsIgnoreCase("all");
		}
		
		else if (pName.equalsIgnoreCase("AltShieldBlocks"))
			ALT_GAME_SHIELD_BLOCKS = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("AltPerfectShieldBlockRate"))
			ALT_PERFECT_SHLD_BLOCK = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("Delevel"))
			ALT_GAME_DELEVEL = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("MagicFailures"))
			ALT_GAME_MAGICFAILURES = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("AltMobAgroInPeaceZone"))
			ALT_MOB_AGGRO_IN_PEACEZONE = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("AltAttackableNpcs"))
			ALT_ATTACKABLE_NPCS = Boolean.parseBoolean(pValue);
		
		else if (pName.equalsIgnoreCase("AltGameExponentXp"))
			ALT_GAME_EXPONENT_XP = Float.parseFloat(pValue);
		else if (pName.equalsIgnoreCase("AltGameExponentSp"))
			ALT_GAME_EXPONENT_SP = Float.parseFloat(pValue);
		
		else if (pName.equalsIgnoreCase("AltGameFreights"))
			ALT_GAME_FREIGHTS = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("AltGameFreightPrice"))
			ALT_GAME_FREIGHT_PRICE = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("AltPartyRange"))
			ALT_PARTY_RANGE = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("AltPartyRange2"))
			ALT_PARTY_RANGE2 = Integer.parseInt(pValue);
		
		else if (pName.equalsIgnoreCase("CraftingEnabled"))
			ALT_IS_CRAFTING_ENABLED = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("ManagerCrystalCount"))
			ALT_MANAGER_CRYSTAL_COUNT = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("SpBookNeeded"))
			ALT_SP_BOOK_NEEDED = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("LifeCrystalNeeded"))
			ALT_LIFE_CRYSTAL_NEEDED = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("EnchantSkillSpBookNeeded"))
			ALT_ES_SP_BOOK_NEEDED = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("AutoLoot"))
			ALT_AUTO_LOOT = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("AutoLootRaid"))
			ALT_AUTO_LOOT_RAID = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("AutoLootAdena"))
			ALT_AUTO_LOOT_ADENA = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("AutoLootHerbs"))
			ALT_AUTO_LOOT_HERBS = Boolean.parseBoolean(pValue);
		
		else if (pName.equalsIgnoreCase("AltKarmaPlayerCanBeKilledInPeaceZone"))
			ALT_GAME_KARMA_PLAYER_CAN_BE_KILLED_IN_PEACEZONE = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("AltKarmaPlayerCanShop"))
			ALT_GAME_KARMA_PLAYER_CAN_SHOP = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("AltKarmaPlayerCanUseGK"))
			ALT_GAME_KARMA_PLAYER_CAN_USE_GK = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("AltKarmaPlayerCanTeleport"))
			ALT_GAME_KARMA_PLAYER_CAN_TELEPORT = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("AltKarmaPlayerCanTrade"))
			ALT_GAME_KARMA_PLAYER_CAN_TRADE = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("AltKarmaPlayerCanUseWareHouse"))
			ALT_GAME_KARMA_PLAYER_CAN_USE_WAREHOUSE = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("AltCastleForDawn"))
			ALT_GAME_CASTLE_DAWN = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("AltCastleForDusk"))
			ALT_GAME_CASTLE_DUSK = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("AltFreeTeleporting"))
			ALT_GAME_FREE_TELEPORT = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("AltSubClassWithoutQuests"))
			ALT_GAME_SUBCLASS_WITHOUT_QUESTS = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("AltSubclassEverywhere"))
			ALT_GAME_SUBCLASS_EVERYWHERE = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("MaxSubclass"))
			ALT_MAX_SUBCLASS = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("MaxSubclassLevel"))
			ALT_MAX_SUBCLASS_LEVEL = Byte.parseByte(pValue);
		//else if (pName.equalsIgnoreCase("AltNewCharAlwaysIsNewbie"))
		//ALT_GAME_NEW_CHAR_ALWAYS_IS_NEWBIE = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("DwarfRecipeLimit"))
			ALT_DWARF_RECIPE_LIMIT = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("CommonRecipeLimit"))
			ALT_COMMON_RECIPE_LIMIT = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("CastleShieldRestriction"))
			ALT_CASTLE_SHIELD = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("ClanHallShieldRestriction"))
			ALT_CLANHALL_SHIELD = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("ApellaArmorsRestriction"))
			ALT_APELLA_ARMORS = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("OathArmorsRestriction"))
			ALT_OATH_ARMORS = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("CastleLordsCrownRestriction"))
			ALT_CASTLE_CROWN = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("CastleCircletsRestriction"))
			ALT_CASTLE_CIRCLETS = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("AllowManaPotions"))
			ALT_MANA_POTIONS = Boolean.parseBoolean(pValue);
		
		// PvP settings
		else if (pName.equalsIgnoreCase("MinKarma"))
			KARMA_MIN_KARMA = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("MaxKarma"))
			KARMA_MAX_KARMA = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("XPDivider"))
			KARMA_XP_DIVIDER = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("BaseKarmaLost"))
			KARMA_LOST_BASE = Integer.parseInt(pValue);
		
		else if (pName.equalsIgnoreCase("CanGMDropEquipment"))
			KARMA_DROP_GM = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("AwardPKKillPVPPoint"))
			KARMA_AWARD_PK_KILL = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("MinimumPKRequiredToDrop"))
			KARMA_PK_LIMIT = Integer.parseInt(pValue);
		
		else if (pName.equalsIgnoreCase("PvPTime"))
			PVP_TIME = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("GlobalChat"))
			DEFAULT_GLOBAL_CHAT = ChatMode.valueOf(pValue.toUpperCase());
		else if (pName.equalsIgnoreCase("TradeChat"))
			DEFAULT_TRADE_CHAT = ChatMode.valueOf(pValue.toUpperCase());
		else if (pName.equalsIgnoreCase("GMAdminMenuStyle"))
			GM_ADMIN_MENU_STYLE = pValue;
		
		else if (pName.equalsIgnoreCase("VIPAllowInterference"))
			VIP_ALLOW_INTERFERENCE = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("VIPAllowPotions"))
			VIP_ALLOW_POTIONS = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("VIPOnStartRemoveAllEffects"))
			VIP_ON_START_REMOVE_ALL_EFFECTS = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("VIPMinLevel"))
			VIP_MIN_LEVEL = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("VIPMaxLevel"))
			VIP_MAX_LEVEL = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("VIPMinParticipants"))
			VIP_MIN_PARTICIPANTS = Integer.parseInt(pValue);
		
		else if (pName.equalsIgnoreCase("CTFEvenTeams"))
			CTF_EVEN_TEAMS = pValue;
		else if (pName.equalsIgnoreCase("CTFAllowInterference"))
			CTF_ALLOW_INTERFERENCE = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("CTFAllowPotions"))
			CTF_ALLOW_POTIONS = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("CTFAllowSummon"))
			CTF_ALLOW_SUMMON = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("CTFOnStartRemoveAllEffects"))
			CTF_ON_START_REMOVE_ALL_EFFECTS = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("CTFOnStartUnsummonPet"))
			CTF_ON_START_UNSUMMON_PET = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("CTFReviveDelay"))
			CTF_REVIVE_DELAY = Long.parseLong(pValue);
		
		else if (pName.equalsIgnoreCase("TvTEvenTeams"))
			TVT_EVEN_TEAMS = pValue;
		else if (pName.equalsIgnoreCase("TvTAllowInterference"))
			TVT_ALLOW_INTERFERENCE = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("TvTAllowPotions"))
			TVT_ALLOW_POTIONS = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("TvTAllowSummon"))
			TVT_ALLOW_SUMMON = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("TvTOnStartRemoveAllEffects"))
			TVT_ON_START_REMOVE_ALL_EFFECTS = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("TvTOnStartUnsummonPet"))
			TVT_ON_START_UNSUMMON_PET = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("TVTReviveDelay"))
			TVT_REVIVE_DELAY = Long.parseLong(pValue);
		
		else if (pName.equalsIgnoreCase("DMAllowInterference"))
			DM_ALLOW_INTERFERENCE = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("DMAllowPotions"))
			DM_ALLOW_POTIONS = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("DMAllowSummon"))
			DM_ALLOW_SUMMON = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("DMOnStartRemoveAllEffects"))
			DM_ON_START_REMOVE_ALL_EFFECTS = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("DMOnStartUnsummonPet"))
			DM_ON_START_UNSUMMON_PET = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("DMReviveDelay"))
			DM_REVIVE_DELAY = Long.parseLong(pValue);
		
		//else if (pName.equalsIgnoreCase("FailFakeDeath"))
		//	ALT_FAIL_FAKEDEATH = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("AltFlyingWyvernInSiege"))
			ALT_FLYING_WYVERN_IN_SIEGE = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("TimeInADayOfOpenADoor"))
			ALT_TIME_IN_A_DAY_OF_OPEN_A_DOOR = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("TimeOfOpeningADoor"))
			ALT_TIME_OF_OPENING_A_DOOR = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("NurseAntRespawnDelay"))
			NURSEANT_RESPAWN_DELAY = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("TimeLimitOfInvade"))
			ALT_TIMELIMITOFINVADE = Integer.parseInt(pValue);
		
		// JP fight with Antharas Custom Setting
		else if (pName.equalsIgnoreCase("FixIntervalOfAntharas"))
			FWA_FIXINTERVALOFANTHARAS = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("RandomIntervalOfAntharas"))
			FWA_RANDOMINTERVALOFANTHARAS = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("AppTimeOfAntharas"))
			FWA_APPTIMEOFANTHARAS = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("ActivityTimeOfAntharas"))
			FWA_ACTIVITYTIMEOFANTHARAS = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("OldAntharas"))
			FWA_OLDANTHARAS = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("LimitOfWeak"))
			FWA_LIMITOFWEAK = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("LimitOfNormal"))
			FWA_LIMITOFNORMAL = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("IntervalOfBehemothOnWeak"))
			FWA_INTERVALOFBEHEMOTHONWEAK = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("IntervalOfBehemothOnNormal"))
			FWA_INTERVALOFBEHEMOTHONNORMAL = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("IntervalOfBehemothOnStrong"))
			FWA_INTERVALOFBEHEMOTHONSTRONG = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("IntervalOfBomberOnWeak"))
			FWA_INTERVALOFBOMBERONWEAK = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("IntervalOfBomberOnNormal"))
			FWA_INTERVALOFBOMBERONNORMAL = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("IntervalOfBomberOnStrong"))
			FWA_INTERVALOFBOMBERONSTRONG = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("MoveAtRandom"))
			FWA_MOVEATRANDOM = Boolean.parseBoolean(pValue);
		
		// JP fight with Baium Custom Setting
		else if (pName.equalsIgnoreCase("FixIntervalOfBaium"))
			FWB_FIXINTERVALOFBAIUM = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("RandomIntervalOfBaium"))
			FWB_RANDOMINTERVALOFBAIUM = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("ActivityTimeOfBaium"))
			FWB_ACTIVITYTIMEOFBAIUM = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("MoveAtRandom"))
			FWB_MOVEATRANDOM = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("LimitUntilSleep"))
			FWB_LIMITUNTILSLEEP = Integer.parseInt(pValue);
		
		// fight with Frintezza Custom Setting
		else if (pName.equalsIgnoreCase("IntervalOfFrintezzaSpawn"))
			FWF_INTERVALOFFRINTEZZA = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("IntervalOfNextMonsterFrintezza"))
			FWF_INTERVALOFNEXTMONSTER = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("ActivityTimeOfMobsFrintezza"))
			FWF_ACTIVITYTIMEOFFRINTEZZA = Integer.parseInt(pValue);
		
		// JP fight with Valakas Custom Setting
		else if (pName.equalsIgnoreCase("FixIntervalOfValakas"))
			FWV_FIXINTERVALOFVALAKAS = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("RandomIntervalOfValakas"))
			FWV_RANDOMINTERVALOFVALAKAS = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("AppTimeOfValakas"))
			FWV_APPTIMEOFVALAKAS = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("ActivityTimeOfValakas"))
			FWV_ACTIVITYTIMEOFVALAKAS = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("CapacityOfLairOfValakas"))
			FWV_CAPACITYOFLAIR = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("MoveAtRandom"))
			FWV_MOVEATRANDOM = Boolean.parseBoolean(pValue);
		
		// JP fight with sailren Custom Setting
		else if (pName.equalsIgnoreCase("EnableSinglePlayer"))
			FWS_ENABLESINGLEPLAYER = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("FixIntervalOfSailrenSpawn"))
			FWS_FIXINTERVALOFSAILRENSPAWN = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("RandomIntervalOfSailrenSpawn"))
			FWS_RANDOMINTERVALOFSAILRENSPAWN = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("IntervalOfNextMonster"))
			FWS_INTERVALOFNEXTMONSTER = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("ActivityTimeOfMobs"))
			FWS_ACTIVITYTIMEOFMOBS = Integer.parseInt(pValue);
		
		// Siege settings
		else if (pName.equalsIgnoreCase("AttackerMaxClans"))
			SIEGE_MAX_ATTACKER = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("DefenderMaxClans"))
			SIEGE_MAX_DEFENDER = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("AttackerRespawn"))
			SIEGE_RESPAWN_DELAY_ATTACKER = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("DefenderRespawn"))
			SIEGE_RESPAWN_DELAY_DEFENDER = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("CTLossPenalty"))
			SIEGE_CT_LOSS_PENALTY = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("MaxFlags"))
			SIEGE_FLAG_MAX_COUNT = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("SiegeClanMinLevel"))
			SIEGE_CLAN_MIN_LEVEL = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("SiegeLength"))
			SIEGE_LENGTH_MINUTES = Integer.parseInt(pValue);
		
		// Telnet settings
		else if (pName.equalsIgnoreCase("AltTelnet"))
			ALT_TELNET = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("AltTelnetGmAnnouncerName"))
			ALT_TELNET_GM_ANNOUNCER_NAME = Boolean.parseBoolean(pValue);
		
		// GM options
		else if (pName.equalsIgnoreCase("GMShowAnnouncerName"))
			GM_ANNOUNCER_NAME = Boolean.parseBoolean(pValue);
		
		// Options
		else if (pName.equalsIgnoreCase("ShowHTMLGm"))
			SHOW_HTML_GM = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("ShowLegend"))
			SHOW_LEGEND = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("ShowKarmaPlayers"))
			SHOW_KARMA_PLAYERS = Boolean.parseBoolean(pValue);
		else if (pName.equalsIgnoreCase("ShowJailedPlayers"))
			SHOW_JAILED_PLAYERS = Boolean.parseBoolean(pValue);
		
		else if (pName.equalsIgnoreCase("MaxPersonalFamePoints"))
			MAX_PERSONAL_FAME_POINTS = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("FortressZoneFameTaskFrequency"))
			FORTRESS_ZONE_FAME_TASK_FREQUENCY = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("FortressZoneFameAquirePoints"))
			FORTRESS_ZONE_FAME_AQUIRE_POINTS = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("CastleZoneFameTaskFrequency"))
			CASTLE_ZONE_FAME_TASK_FREQUENCY = Integer.parseInt(pValue);
		else if (pName.equalsIgnoreCase("CastleZoneFameAquirePoints"))
			CASTLE_ZONE_FAME_AQUIRE_POINTS = Integer.parseInt(pValue);
		
		else
			return false;
		return true;
	}
	
	// it has no instancies
	protected Config()
	{
	}
	
	/**
	 * To keep compatibility with old loginserver.properties, add db properties
	 * into system properties Spring will use those values later
	 */
	public static void initDBProperties()
	{
		System.setProperty("com.l2jfree.db.driverclass", DATABASE_DRIVER);
		System.setProperty("com.l2jfree.db.urldb", DATABASE_URL);
		System.setProperty("com.l2jfree.db.user", DATABASE_LOGIN);
		System.setProperty("com.l2jfree.db.password", DATABASE_PASSWORD);
		System.setProperty("com.l2jfree.db.maximum.db.connection", Integer.toString(DATABASE_MAX_CONNECTIONS));
	}
}
