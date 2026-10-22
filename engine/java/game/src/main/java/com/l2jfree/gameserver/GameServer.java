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
package com.l2jfree.gameserver;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Calendar;

import com.l2jfree.Config;
import com.l2jfree.L2AutoInitialization;
import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.contract.LoginPort;
import com.l2jfree.contract.WorldPort;
import com.l2jfree.gameserver.cache.CrestCache;
import com.l2jfree.gameserver.cache.HtmCache;
import com.l2jfree.gameserver.cache.WarehouseCacheManager;
import com.l2jfree.gameserver.communitybbs.Manager.ForumsBBSManager;
import com.l2jfree.gameserver.datatables.ArmorSetsTable;
import com.l2jfree.gameserver.datatables.AugmentationData;
import com.l2jfree.gameserver.datatables.BuffTemplateTable;
import com.l2jfree.gameserver.datatables.CharNameTable;
import com.l2jfree.gameserver.datatables.CharTemplateTable;
import com.l2jfree.gameserver.datatables.ClanTable;
import com.l2jfree.gameserver.datatables.DoorTable;
import com.l2jfree.gameserver.datatables.EnchantHPBonusData;
import com.l2jfree.gameserver.datatables.EventDroplist;
import com.l2jfree.gameserver.datatables.ExtractableItemsData;
import com.l2jfree.gameserver.datatables.ExtractableSkillsData;
import com.l2jfree.gameserver.datatables.FishTable;
import com.l2jfree.gameserver.datatables.GmListTable;
import com.l2jfree.gameserver.datatables.HennaTable;
import com.l2jfree.gameserver.datatables.HennaTreeTable;
import com.l2jfree.gameserver.datatables.HeroSkillTable;
import com.l2jfree.gameserver.datatables.ItemTable;
import com.l2jfree.gameserver.datatables.LevelUpData;
import com.l2jfree.gameserver.datatables.MerchantPriceConfigTable;
import com.l2jfree.gameserver.datatables.MultisellTable;
import com.l2jfree.gameserver.datatables.NobleSkillTable;
import com.l2jfree.gameserver.datatables.NpcTable;
import com.l2jfree.gameserver.datatables.NpcWalkerRoutesTable;
import com.l2jfree.gameserver.datatables.PetDataTable;
import com.l2jfree.gameserver.datatables.PetSkillsTable;
import com.l2jfree.gameserver.datatables.RecipeTable;
import com.l2jfree.gameserver.datatables.ResidentialSkillTable;
import com.l2jfree.gameserver.datatables.ShotTable;
import com.l2jfree.gameserver.datatables.SkillSpellbookTable;
import com.l2jfree.gameserver.datatables.SkillTable;
import com.l2jfree.gameserver.datatables.SkillTreeTable;
import com.l2jfree.gameserver.datatables.SpawnTable;
import com.l2jfree.gameserver.datatables.StaticObjects;
import com.l2jfree.gameserver.datatables.SummonItemsData;
import com.l2jfree.gameserver.datatables.TeleportLocationTable;
import com.l2jfree.gameserver.datatables.TradeListTable;
import com.l2jfree.gameserver.elayne.RemoteAdministrationImpl;
import com.l2jfree.gameserver.geodata.GeoData;
import com.l2jfree.gameserver.geodata.pathfinding.PathFinding;
import com.l2jfree.gameserver.geoeditorcon.GeoEditorListener;
import com.l2jfree.gameserver.handler.AdminCommandHandler;
import com.l2jfree.gameserver.handler.ChatHandler;
import com.l2jfree.gameserver.handler.ItemHandler;
import com.l2jfree.gameserver.handler.SkillHandler;
import com.l2jfree.gameserver.handler.SkillTargetHandler;
import com.l2jfree.gameserver.handler.UserCommandHandler;
import com.l2jfree.gameserver.handler.VoicedCommandHandler;
import com.l2jfree.gameserver.idfactory.IdFactory;
import com.l2jfree.gameserver.instancemanager.AirShipManager;
import com.l2jfree.gameserver.instancemanager.AuctionManager;
import com.l2jfree.gameserver.instancemanager.AutoChatManager;
import com.l2jfree.gameserver.instancemanager.AutoSpawnManager;
import com.l2jfree.gameserver.instancemanager.BlockListManager;
import com.l2jfree.gameserver.instancemanager.BoatManager;
import com.l2jfree.gameserver.instancemanager.CCHManager;
import com.l2jfree.gameserver.instancemanager.CastleManager;
import com.l2jfree.gameserver.instancemanager.CastleManorManager;
import com.l2jfree.gameserver.instancemanager.ClanHallManager;
import com.l2jfree.gameserver.instancemanager.CoupleManager;
import com.l2jfree.gameserver.instancemanager.CrownManager;
import com.l2jfree.gameserver.instancemanager.CursedWeaponsManager;
import com.l2jfree.gameserver.instancemanager.DayNightSpawnManager;
import com.l2jfree.gameserver.instancemanager.DimensionalRiftManager;
import com.l2jfree.gameserver.instancemanager.FortManager;
import com.l2jfree.gameserver.instancemanager.FortSiegeManager;
import com.l2jfree.gameserver.instancemanager.FourSepulchersManager;
import com.l2jfree.gameserver.instancemanager.FriendListManager;
import com.l2jfree.gameserver.instancemanager.GameTimeManager;
import com.l2jfree.gameserver.instancemanager.GrandBossSpawnManager;
import com.l2jfree.gameserver.instancemanager.InstanceManager;
import com.l2jfree.gameserver.instancemanager.ItemsAutoDestroyManager;
import com.l2jfree.gameserver.instancemanager.ItemsOnGroundManager;
import com.l2jfree.gameserver.instancemanager.MapRegionManager;
import com.l2jfree.gameserver.instancemanager.MercTicketManager;
import com.l2jfree.gameserver.instancemanager.PartyRoomManager;
import com.l2jfree.gameserver.instancemanager.PetitionManager;
import com.l2jfree.gameserver.instancemanager.QuestManager;
import com.l2jfree.gameserver.instancemanager.RaidBossSpawnManager;
import com.l2jfree.gameserver.instancemanager.RaidPointsManager;
import com.l2jfree.gameserver.instancemanager.RecommendationManager;
import com.l2jfree.gameserver.instancemanager.SiegeManager;
import com.l2jfree.gameserver.instancemanager.TownManager;
import com.l2jfree.gameserver.instancemanager.TransformationManager;
import com.l2jfree.gameserver.instancemanager.ZoneManager;
import com.l2jfree.gameserver.instancemanager.grandbosses.AntharasManager;
import com.l2jfree.gameserver.instancemanager.grandbosses.BaiumManager;
import com.l2jfree.gameserver.instancemanager.grandbosses.BaylorManager;
import com.l2jfree.gameserver.instancemanager.grandbosses.FrintezzaManager;
import com.l2jfree.gameserver.instancemanager.grandbosses.QueenAntManager;
import com.l2jfree.gameserver.instancemanager.grandbosses.SailrenManager;
import com.l2jfree.gameserver.instancemanager.grandbosses.ValakasManager;
import com.l2jfree.gameserver.instancemanager.grandbosses.VanHalterManager;
import com.l2jfree.gameserver.instancemanager.hellbound.HellboundManager;
import com.l2jfree.gameserver.instancemanager.hellbound.TowerOfNaiaManager;
import com.l2jfree.gameserver.instancemanager.lastimperialtomb.LastImperialTombManager;
import com.l2jfree.gameserver.instancemanager.leaderboards.ArenaManager;
import com.l2jfree.gameserver.instancemanager.leaderboards.FishermanManager;
import com.l2jfree.gameserver.model.L2Manor;
import com.l2jfree.gameserver.model.entity.CCHSiege;
import com.l2jfree.gameserver.model.entity.Castle;
import com.l2jfree.gameserver.model.entity.Fort;
import com.l2jfree.gameserver.model.entity.Hero;
import com.l2jfree.gameserver.model.entity.events.AutomatedTvT;
import com.l2jfree.gameserver.model.olympiad.Olympiad;
import com.l2jfree.gameserver.model.restriction.ObjectRestrictions;
import com.l2jfree.gameserver.model.sevensigns.SevenSigns;
import com.l2jfree.gameserver.model.sevensigns.SevenSignsFestival;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.network.L2ClientSelectorThread;
import com.l2jfree.gameserver.network.packets.server.NewCharacterSuccess;
import com.l2jfree.gameserver.persistence.WorldConfigurationException;
import com.l2jfree.gameserver.persistence.WorldPassport;
import com.l2jfree.gameserver.script.faenor.FaenorScriptEngine;
import com.l2jfree.gameserver.scripting.CompiledScriptCache;
import com.l2jfree.gameserver.scripting.L2ScriptEngineManager;
import com.l2jfree.gameserver.status.Status;
import com.l2jfree.gameserver.taskmanager.AttackStanceTaskManager;
import com.l2jfree.gameserver.taskmanager.DecayTaskManager;
import com.l2jfree.gameserver.taskmanager.KnownListUpdateTaskManager;
import com.l2jfree.gameserver.taskmanager.LeakTaskManager;
import com.l2jfree.gameserver.taskmanager.MovementController;
import com.l2jfree.gameserver.taskmanager.PacketBroadcaster;
import com.l2jfree.gameserver.taskmanager.SQLQueue;
import com.l2jfree.gameserver.taskmanager.tasks.TaskManager;
import com.l2jfree.gameserver.threadmanager.DeadlockDetector;
import com.l2jfree.gameserver.util.DynamicExtension;
import com.l2jfree.gameserver.util.OfflineTradeManager;
import com.l2jfree.gameserver.util.Util;
import com.l2jfree.gameserver.util.WorldFiles;
import com.l2jfree.lang.management.StartupManager;
import com.l2jfree.util.concurrent.RunnableStatsManager;

public final class GameServer extends L2AutoInitialization
{
	private static final Calendar _serverStarted = Calendar.getInstance();
	
	/**
	 * Loads the world and opens the world port. The platform calls it once, after the login module is prepared and
	 * before the login port opens, so a client that reaches the login finds a world.
	 * 
	 * @param login the port of the login module, connected to the world just before the world port opens
	 * @return the port the login module uses to reach the world, once the world is listening
	 * @throws Exception when any part of the world fails to load or the world port cannot be opened
	 */
	public static WorldPort start(LoginPort login) throws Exception
	{
		CoreInfo.showStartupInfo();
		
		long serverLoadStart = System.currentTimeMillis();
		
		Config.load();
		
		Path datapackRoot = Config.DATAPACK_ROOT.toPath();
		Path catalogDirectory = Paths.get(Config.CATALOG_DIRECTORY);
		requireWorldDirectory("DatapackRoot", datapackRoot);
		requireWorldDirectory("CatalogDirectory", catalogDirectory);
		
		Files.createDirectories(Paths.get("cache"));
		
		Util.printSection("Database");
		// takes the world lock first: it keeps a second server off the same world database for as long as this one runs
		L2DatabaseFactory.getInstance().prepareSchemas(catalogDirectory);
		WorldPassport.log(datapackRoot, catalogDirectory);
		Util.printSection("World");
		L2World.getInstance();
		if (Config.IS_TELNET_ENABLED)
			new Status().start();
		else
			_log.info("Telnet Server is currently disabled.");
		MapRegionManager.getInstance();
		Announcements.getInstance();
		AutoAnnouncements.getInstance();
		if (!IdFactory.getInstance().isInitialized())
		{
			_log.error("Could not read object IDs from DB. Please Check Your Data.");
			throw new Exception("Could not initialize the ID factory");
		}
		_log.info("IdFactory: Free ObjectID's remaining: " + IdFactory.getInstance().size());
		Class.forName(RunnableStatsManager.class.getName());
		ThreadPoolManager.getInstance();
		if (Config.DEADLOCKCHECK_INTERVAL > 0)
			DeadlockDetector.getInstance();
		SQLQueue.getInstance();
		
		GeoData.getInstance();
		if (Config.GEODATA >= 2)
			PathFinding.getInstance();
		
		StaticObjects.getInstance();
		GameTimeManager.getInstance();
		TeleportLocationTable.getInstance();
		BoatManager.getInstance();
		InstanceManager.getInstance();
		MerchantPriceConfigTable.getInstance().loadInstances();
		
		Util.printSection("TaskManagers");
		AttackStanceTaskManager.getInstance();
		DecayTaskManager.getInstance();
		KnownListUpdateTaskManager.getInstance();
		LeakTaskManager.getInstance();
		MovementController.getInstance();
		PacketBroadcaster.getInstance();
		
		Util.printSection("Skills");
		SkillTreeTable.getInstance();
		SkillTable.getInstance();
		PetSkillsTable.getInstance();
		Class.forName(NobleSkillTable.class.getName());
		Class.forName(HeroSkillTable.class.getName());
		ResidentialSkillTable.getInstance();
		Util.printSection("Items");
		Class.forName(ShotTable.class.getName());
		ItemTable.getInstance();
		ArmorSetsTable.getInstance();
		AugmentationData.getInstance();
		SkillSpellbookTable.getInstance();
		SummonItemsData.getInstance();
		ExtractableItemsData.getInstance();
		ExtractableSkillsData.getInstance();
		EnchantHPBonusData.getInstance();
		MultisellTable.getInstance();
		if (Config.ALLOW_FISHING)
		{
			FishTable.getInstance();
		}
		ItemsOnGroundManager.getInstance();
		if (Config.AUTODESTROY_ITEM_AFTER > 0 || Config.HERB_AUTO_DESTROY_TIME > 0)
		{
			ItemsAutoDestroyManager.getInstance();
		}
		Util.printSection("Characters");
		CharNameTable.getInstance();
		CharTemplateTable.getInstance();
		NewCharacterSuccess.load();
		LevelUpData.getInstance();
		HennaTable.getInstance();
		HennaTreeTable.getInstance();
		if (Config.ALLOW_WEDDING)
		{
			CoupleManager.getInstance();
		}
		CursedWeaponsManager.getInstance();
		
		// forums must be loaded before clan data, because of last forum id used should have also memo included
		if (Config.COMMUNITY_TYPE > 0)
			ForumsBBSManager.getInstance().initRoot();
		
		ClanTable.getInstance();
		CrestCache.getInstance();
		WarehouseCacheManager.getInstance();
		Hero.getInstance();
		BlockListManager.getInstance();
		RecommendationManager.getInstance();
		FriendListManager.getInstance();
		
		Util.printSection("NPCs");
		NpcTable.getInstance();
		HtmCache.getInstance();
		BuffTemplateTable.getInstance();
		if (Config.ALLOW_NPC_WALKERS)
		{
			NpcWalkerRoutesTable.getInstance().load();
		}
		PetDataTable.getInstance().loadPetsData();
		Util.printSection("SevenSigns");
		SevenSigns.getInstance();
		SevenSignsFestival.getInstance();
		
		Util.printSection("Entities and zones");
		Class.forName(CrownManager.class.getName());
		TownManager.getInstance();
		ClanHallManager.getInstance();
		DoorTable.getInstance();
		CastleManager.getInstance().loadInstances();
		SiegeManager.getInstance();
		FortManager.getInstance().loadInstances();
		CCHManager.getInstance();
		FortSiegeManager.getInstance();
		ZoneManager.getInstance();
		MercTicketManager.getInstance();
		DoorTable.getInstance().registerToClanHalls();
		DoorTable.getInstance().setCommanderDoors();
		if (Config.PACKET_FINAL)
			AirShipManager.getInstance();
		// make sure that all the scheduled siege dates are in the Seal Validation period
		for (Castle castle : CastleManager.getInstance().getCastles().values())
			castle.getSiege().correctSiegeDateTime();
		for (CCHSiege siege : CCHManager.getInstance().getSieges())
			siege.correctSiegeDateTime();
		PartyRoomManager.getInstance();
		
		Util.printSection("Spawns");
		QueenAntManager.getInstance();
		SpawnTable.getInstance();
		for (Fort fort : FortManager.getInstance().getForts())
			fort.getSpawnManager().initNpcs();
		DayNightSpawnManager.getInstance().notifyChangeMode();
		RaidBossSpawnManager.getInstance();
		GrandBossSpawnManager.getInstance();
		RaidPointsManager.init();
		AutoChatManager.getInstance();
		AutoSpawnManager.getInstance();
		
		Util.printSection("Quests");
		QuestManager.getInstance();
		TransformationManager.getInstance();
		
		Util.printSection("Events/ScriptEngine");
		File scripts = new File(Config.DATAPACK_ROOT, "data/scripts.cfg");
		if (!WorldFiles.skip(_log, "scripts", scripts))
		{
			try
			{
				L2ScriptEngineManager.getInstance().executeScriptList(scripts);
			}
			catch (IOException ioe)
			{
				_log.error("Failed loading scripts.cfg, no script going to be loaded");
			}
		}
		try
		{
			CompiledScriptCache compiledScriptCache = L2ScriptEngineManager.getInstance().getCompiledScriptCache();
			if (compiledScriptCache == null)
				_log.info("Compiled Scripts Cache is disabled.");
			else
			{
				compiledScriptCache.purge();
				if (compiledScriptCache.isModified())
				{
					compiledScriptCache.save();
					_log.info("Compiled Scripts Cache was saved.");
				}
				else
					_log.info("Compiled Scripts Cache is up-to-date.");
			}
			
		}
		catch (IOException e)
		{
			_log.error("Failed to store Compiled Scripts Cache.", e);
		}
		
		QuestManager.getInstance().report();
		TransformationManager.getInstance().report();
		
		EventDroplist.getInstance();
		FaenorScriptEngine.getInstance();
		
		if (Config.ARENA_ENABLED)
			ArenaManager.getInstance().engineInit();
		if (Config.FISHERMAN_ENABLED)
			FishermanManager.getInstance().engineInit();
		
		Util.printSection("Economy");
		TradeListTable.getInstance();
		CastleManorManager.getInstance();
		L2Manor.getInstance();
		AuctionManager.getInstance();
		RecipeTable.getInstance();
		
		Util.printSection("Olympiad");
		Olympiad.getInstance();
		Util.printSection("Dungeons");
		DimensionalRiftManager.getInstance();
		FourSepulchersManager.getInstance().init();
		Util.printSection("Hellbound");
		HellboundManager.getInstance();
		TowerOfNaiaManager.getInstance().init();
		Util.printSection("Bosses");
		AntharasManager.getInstance().init();
		BaiumManager.getInstance().init();
		BaylorManager.getInstance().init();
		SailrenManager.getInstance().init();
		ValakasManager.getInstance().init();
		VanHalterManager.getInstance().init();
		LastImperialTombManager.getInstance().init();
		FrintezzaManager.getInstance().init();
		
		Util.printSection("Extensions");
		try
		{
			DynamicExtension.getInstance();
		}
		catch (Exception ex)
		{
			_log.warn("DynamicExtension could not be loaded and initialized", ex);
		}
		AutomatedTvT.getInstance();
		Util.printSection("Handlers");
		ItemHandler.getInstance();
		SkillHandler.getInstance();
		AdminCommandHandler.getInstance();
		UserCommandHandler.getInstance();
		VoicedCommandHandler.getInstance();
		ChatHandler.getInstance();
		SkillTargetHandler.getInstance();
		
		Util.printSection("Elayne");
		RemoteAdministrationImpl.getInstance().startServer();
		
		Util.printSection("Misc");
		ObjectRestrictions.getInstance();
		TaskManager.getInstance();
		Class.forName(GmListTable.class.getName());
		PetitionManager.getInstance();
		if (Config.ONLINE_PLAYERS_ANNOUNCE_INTERVAL > 0)
			OnlinePlayers.getInstance();
		
		MerchantPriceConfigTable.getInstance().updateReferences();
		CastleManager.getInstance().activateInstances();
		FortManager.getInstance().activateInstances();
		
		Runtime.getRuntime().addShutdownHook(Shutdown.getInstance());
		
		System.gc();
		System.runFinalization();
		
		Util.printSection("ServerThreads");
		LoginLink.getInstance().connect(login);
		
		L2ClientSelectorThread.getInstance().openServerSocket(Config.GAMESERVER_HOSTNAME, Config.PORT_GAME);
		L2ClientSelectorThread.getInstance().start();
		
		if (Config.ACCEPT_GEOEDITOR_CONN)
			GeoEditorListener.getInstance();
		
		if (Config.ENABLE_OFFLINE_TRADERS_RESTORE)
		{
			Util.printSection("Offline Trade");
			OfflineTradeManager.getInstance().restore();
		}
		
		Util.printSection("l2jfree-core");
		for (String line : CoreInfo.getFullVersionInfo())
			_log.info(line);
		_log.info("Operating System: " + Util.getOSName() + " " + Util.getOSVersion() + " " + Util.getOSArch());
		_log.info("Available CPUs: " + Util.getAvailableProcessors());
		
		Util.printSection("Memory");
		for (String line : Util.getMemUsage())
			_log.info(line);
		
		_log.info("Maximum number of connected players: " + Config.MAXIMUM_ONLINE_USERS);
		_log.info("Server loaded in " + ((System.currentTimeMillis() - serverLoadStart) / 1000) + " seconds.");
		
		StartupManager.onStartup();
		GameServerExtensions.start();
		
		Util.printSection("GameServerLog");
		if (Config.ENABLE_JYTHON_SHELL)
		{
			Util.printSection("JythonShell");
			// The shell reads the console until it is closed. It runs on a thread of its own, so the platform can
			// open the login port once this method returns.
			Thread shell = new Thread("JythonShell")
			{
				@Override
				public void run()
				{
					Util.JythonShell();
				}
			};
			shell.setDaemon(true);
			shell.start();
		}
		
		// The world is loaded and listens: the login module may now ask it for status and admissions.
		return LoginLink.getInstance();
	}
	
	public static Calendar getStartedTime()
	{
		return _serverStarted;
	}
	
	private static void requireWorldDirectory(String key, Path path) throws WorldConfigurationException
	{
		if (!Files.isDirectory(path))
			throw new WorldConfigurationException(key, path.toString());
	}
}
