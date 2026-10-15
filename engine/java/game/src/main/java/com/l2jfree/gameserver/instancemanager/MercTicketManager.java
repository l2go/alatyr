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
package com.l2jfree.gameserver.instancemanager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.Config;
import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.ThreadPoolManager;
import com.l2jfree.gameserver.datatables.NpcTable;
import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.instance.L2SiegeGuardInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2SiegeTeleporterInstance;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.idfactory.IdFactory;
import com.l2jfree.gameserver.model.clan.L2Clan;
import com.l2jfree.gameserver.model.entity.Castle;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.sevensigns.SevenSigns;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.client.ConfirmDlgAnswer.AnswerHandler;
import com.l2jfree.gameserver.network.packets.server.ConfirmDlg;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;
import com.l2jfree.gameserver.persistence.WorldTransaction;

/**
 * Completely revamped mercenary manager.
 * Sorry for breaking compatibility, but I do not like size X hundred arrays
 * with hand-picked indexes.<BR>
 * The new mapped system allows less iterations overall and a more update-friendly
 * structure.<BR>
 * <I>Both the new system is mine, and mercenary item/NPC IDs are mine from
 * http://www.l2jfree.com/index.php?topic=3015.0, [2008-02-16]</I><BR>
 * <B>Please contact me in need of sync review.</B>
 * @author savormix
 */
public class MercTicketManager
{
	protected static final Logger _log = LoggerFactory.getLogger(MercTicketManager.class);
	
	/** Represents all hireling positioning messages */
	public static final String[] MESSAGES = { "To arms!", "I am ready to serve you my lord when the time comes.",
			"You summon me." };
	
	private static final String LOAD_POSITIONS =
			"SELECT item_template_id, x, y, z, heading FROM castle_hired_guard";
	private static final String CLEAN_POSITIONS = "DELETE FROM castle_hired_guard";
	private static final String ADD_POSITION =
			"INSERT INTO castle_hired_guard (item_template_id, x, y, z, heading) VALUES (?,?,?,?,?)"
					+ " ON CONFLICT (x, y, z) DO UPDATE SET item_template_id = EXCLUDED.item_template_id,"
					+ " heading = EXCLUDED.heading";
	private static final String REMOVE_POSITION = "DELETE FROM castle_hired_guard WHERE x=? AND y=? AND z=?";
	
	private static final int DAWN_MERCENARY_MIN = 35020;
	private static final int DAWN_MERCENARY_MAX = 35029;
	private static final int DAWN_MERCENARY_ELITE_MIN = 35060;
	private static final int DAWN_MERCENARY_ELITE_MAX = 35061;
	private static final int RECRUIT_MIN = 35040;
	private static final int RECRUIT_MAX = 35059;
	private static final int[] CASTLE_TYPE_LIMIT = { 10, 15, 10, 10, 20, 20, 20, 20, 20 };
	private static final int[] CASTLE_HIRE_LIMIT = { Config.GLUDIO_MAX_MERCENARIES, Config.DION_MAX_MERCENARIES,
			Config.GIRAN_MAX_MERCENARIES, Config.OREN_MAX_MERCENARIES, Config.ADEN_MAX_MERCENARIES,
			Config.INNADRIL_MAX_MERCENARIES, Config.GODDARD_MAX_MERCENARIES, Config.RUNE_MAX_MERCENARIES,
			Config.SCHUTTGART_MAX_MERCENARIES };
	private static final int WEEK = 604800000;
	
	public static final MercTicketManager getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private final Map<Integer, Integer> _typeLimit;
	private final Map<Integer, MercInfo> _mercenaries;
	private final Map<Integer, List<L2ItemInstance>> _positions;
	private volatile ScheduledFuture<?> _update;
	private int[] _handlerIds;
	
	private MercTicketManager()
	{
		_typeLimit = new ConcurrentHashMap<Integer, Integer>();
		fillTypes();
		_mercenaries = new ConcurrentHashMap<Integer, MercInfo>();
		fillMercenaries();
		_positions = new LinkedHashMap<Integer, List<L2ItemInstance>>();
		fillPositions();
		_update = null;
	}
	
	/**
	 * Fills the "limit per mercenary type" map. It would be reasonable to do so
	 * with the values obtained from the configuration file. This map doesn't have
	 * to map every single mercenary type.
	 */
	private final void fillTypes()
	{
		// Example of a Gludio ticket (normally 10 mercs of each type in Gludio)
		_typeLimit.put(3960, 10);
		
		// Teleporters
		for (int i = 3970; i <= 3972; i++)
			_typeLimit.put(i, 1);
		for (int i = 3983; i <= 3985; i++)
			_typeLimit.put(i, 1);
		for (int i = 3996; i <= 3998; i++)
			_typeLimit.put(i, 1);
		for (int i = 4009; i <= 4011; i++)
			_typeLimit.put(i, 1);
		for (int i = 4022; i <= 4026; i++)
			_typeLimit.put(i, 1);
		_typeLimit.put(5215, 1);
		_typeLimit.put(5218, 1);
		_typeLimit.put(5219, 1);
		for (int i = 6789; i <= 6791; i++)
			_typeLimit.put(i, 1);
		for (int i = 7983; i <= 7987; i++)
			_typeLimit.put(i, 1);
		for (int i = 7928; i <= 7930; i++)
			_typeLimit.put(i, 1);
	}
	
	/** Fills the item-to-npc ID known mercenary map. */
	private final void fillMercenaries()
	{
		// Normal Mercenaries - Gludio
		_mercenaries.put(3960, new MercInfo(35010, 1)); // Sword/Stationary
		_mercenaries.put(3961, new MercInfo(35011, 1)); // Spear/Stationary
		_mercenaries.put(3962, new MercInfo(35012, 1)); // Bow/Stationary
		_mercenaries.put(3963, new MercInfo(35013, 1)); // Cleric/Stationary
		_mercenaries.put(3964, new MercInfo(35014, 1)); // Wizard/Stationary
		_mercenaries.put(3965, new MercInfo(35015, 1)); // Sword/Mobile
		_mercenaries.put(3966, new MercInfo(35016, 1)); // Spear/Mobile
		_mercenaries.put(3967, new MercInfo(35017, 1)); // Bow/Mobile
		_mercenaries.put(3968, new MercInfo(35018, 1)); // Cleric/Mobile
		_mercenaries.put(3969, new MercInfo(35019, 1)); // Wizard/Mobile
		// Teleporters - Gludio
		_mercenaries.put(3970, new MercInfo(35092, 1)); // Teleporter1
		_mercenaries.put(3971, new MercInfo(35093, 1)); // Teleporter2
		_mercenaries.put(3972, new MercInfo(35094, 1)); // Teleporter3
		// Greater/Elite Mercenaries - Gludio
		_mercenaries.put(6038, new MercInfo(35030, 1)); // Sword/Stationary
		_mercenaries.put(6039, new MercInfo(35031, 1)); // Spear/Stationary
		_mercenaries.put(6040, new MercInfo(35032, 1)); // Bow/Stationary
		_mercenaries.put(6041, new MercInfo(35033, 1)); // Cleric/Stationary
		_mercenaries.put(6042, new MercInfo(35034, 1)); // Wizard/Stationary
		_mercenaries.put(6043, new MercInfo(35035, 1)); // Sword/Mobile
		_mercenaries.put(6044, new MercInfo(35036, 1)); // Spear/Mobile
		_mercenaries.put(6045, new MercInfo(35037, 1)); // Bow/Mobile
		_mercenaries.put(6046, new MercInfo(35038, 1)); // Cleric/Mobile
		_mercenaries.put(6047, new MercInfo(35039, 1)); // Wizard/Mobile
		// Dawn Mercenaries - Gludio
		_mercenaries.put(6115, new MercInfo(35020, 1)); // Sword/Stationary
		_mercenaries.put(6116, new MercInfo(35021, 1)); // Spear/Stationary
		_mercenaries.put(6117, new MercInfo(35022, 1)); // Bow/Stationary
		_mercenaries.put(6118, new MercInfo(35023, 1)); // Cleric/Stationary
		_mercenaries.put(6119, new MercInfo(35024, 1)); // Wizard/Stationary
		_mercenaries.put(6120, new MercInfo(35025, 1)); // Sword/Mobile
		_mercenaries.put(6121, new MercInfo(35026, 1)); // Spear/Mobile
		_mercenaries.put(6122, new MercInfo(35027, 1)); // Bow/Mobile
		_mercenaries.put(6123, new MercInfo(35028, 1)); // Cleric/Mobile
		_mercenaries.put(6124, new MercInfo(35029, 1)); // Wizard/Mobile
		// Greater Recruits - Gludio
		_mercenaries.put(6175, new MercInfo(35040, 1)); // Sword/Stationary
		_mercenaries.put(6176, new MercInfo(35041, 1)); // Spear/Stationary
		_mercenaries.put(6177, new MercInfo(35042, 1)); // Bow/Stationary
		_mercenaries.put(6178, new MercInfo(35043, 1)); // Cleric/Stationary
		_mercenaries.put(6179, new MercInfo(35044, 1)); // Wizard/Stationary
		_mercenaries.put(6180, new MercInfo(35045, 1)); // Sword/Mobile
		_mercenaries.put(6181, new MercInfo(35046, 1)); // Spear/Mobile
		_mercenaries.put(6182, new MercInfo(35047, 1)); // Bow/Mobile
		_mercenaries.put(6183, new MercInfo(35048, 1)); // Cleric/Mobile
		_mercenaries.put(6184, new MercInfo(35049, 1)); // Wizard/Mobile
		// Normal Recruits - Gludio
		_mercenaries.put(6235, new MercInfo(35050, 1)); // Sword/Stationary
		_mercenaries.put(6236, new MercInfo(35051, 1)); // Spear/Stationary
		_mercenaries.put(6237, new MercInfo(35052, 1)); // Bow/Stationary
		_mercenaries.put(6238, new MercInfo(35053, 1)); // Cleric/Stationary
		_mercenaries.put(6239, new MercInfo(35054, 1)); // Wizard/Stationary
		_mercenaries.put(6240, new MercInfo(35055, 1)); // Sword/Mobile
		_mercenaries.put(6241, new MercInfo(35056, 1)); // Spear/Mobile
		_mercenaries.put(6242, new MercInfo(35057, 1)); // Bow/Mobile
		_mercenaries.put(6243, new MercInfo(35058, 1)); // Cleric/Mobile
		_mercenaries.put(6244, new MercInfo(35059, 1)); // Wizard/Mobile
		// Greater/Elite Dawn Mercenaries - Gludio
		_mercenaries.put(6295, new MercInfo(35060, 1)); // Sword/Mobile
		_mercenaries.put(6296, new MercInfo(35061, 1)); // Wizard/Mobile
		
		// Normal Mercenaries - Dion
		_mercenaries.put(3973, new MercInfo(35010, 2)); // Sword/Stationary
		_mercenaries.put(3974, new MercInfo(35011, 2)); // Spear/Stationary
		_mercenaries.put(3975, new MercInfo(35012, 2)); // Bow/Stationary
		_mercenaries.put(3976, new MercInfo(35013, 2)); // Cleric/Stationary
		_mercenaries.put(3977, new MercInfo(35014, 2)); // Wizard/Stationary
		_mercenaries.put(3978, new MercInfo(35015, 2)); // Sword/Mobile
		_mercenaries.put(3979, new MercInfo(35016, 2)); // Spear/Mobile
		_mercenaries.put(3980, new MercInfo(35017, 2)); // Bow/Mobile
		_mercenaries.put(3981, new MercInfo(35018, 2)); // Cleric/Mobile
		_mercenaries.put(3982, new MercInfo(35019, 2)); // Wizard/Mobile
		// Teleporters - Dion
		_mercenaries.put(3983, new MercInfo(35134, 2)); // Teleporter
		_mercenaries.put(3984, new MercInfo(35135, 2)); // Teleporter
		_mercenaries.put(3985, new MercInfo(35136, 2)); // Teleporter
		// Greater/Elite Mercenaries - Dion
		_mercenaries.put(6051, new MercInfo(35030, 2)); // Sword/Stationary
		_mercenaries.put(6052, new MercInfo(35031, 2)); // Spear/Stationary
		_mercenaries.put(6053, new MercInfo(35032, 2)); // Bow/Stationary
		_mercenaries.put(6054, new MercInfo(35033, 2)); // Cleric/Stationary
		_mercenaries.put(6055, new MercInfo(35034, 2)); // Wizard/Stationary
		_mercenaries.put(6056, new MercInfo(35035, 2)); // Sword/Mobile
		_mercenaries.put(6057, new MercInfo(35036, 2)); // Spear/Mobile
		_mercenaries.put(6058, new MercInfo(35037, 2)); // Bow/Mobile
		_mercenaries.put(6059, new MercInfo(35038, 2)); // Cleric/Mobile
		_mercenaries.put(6060, new MercInfo(35039, 2)); // Wizard/Mobile
		// Dawn Mercenaries - Dion
		_mercenaries.put(6125, new MercInfo(35020, 2)); // Sword/Stationary
		_mercenaries.put(6126, new MercInfo(35021, 2)); // Spear/Stationary
		_mercenaries.put(6127, new MercInfo(35022, 2)); // Bow/Stationary
		_mercenaries.put(6128, new MercInfo(35023, 2)); // Cleric/Stationary
		_mercenaries.put(6129, new MercInfo(35024, 2)); // Wizard/Stationary
		_mercenaries.put(6130, new MercInfo(35025, 2)); // Sword/Mobile
		_mercenaries.put(6131, new MercInfo(35026, 2)); // Spear/Mobile
		_mercenaries.put(6132, new MercInfo(35027, 2)); // Bow/Mobile
		_mercenaries.put(6133, new MercInfo(35028, 2)); // Cleric/Mobile
		_mercenaries.put(6134, new MercInfo(35029, 2)); // Wizard/Mobile
		// Greater Recruits - Dion
		_mercenaries.put(6185, new MercInfo(35040, 2)); // Sword/Stationary
		_mercenaries.put(6186, new MercInfo(35041, 2)); // Spear/Stationary
		_mercenaries.put(6187, new MercInfo(35042, 2)); // Bow/Stationary
		_mercenaries.put(6188, new MercInfo(35043, 2)); // Cleric/Stationary
		_mercenaries.put(6189, new MercInfo(35044, 2)); // Wizard/Stationary
		_mercenaries.put(6190, new MercInfo(35045, 2)); // Sword/Mobile
		_mercenaries.put(6191, new MercInfo(35046, 2)); // Spear/Mobile
		_mercenaries.put(6192, new MercInfo(35047, 2)); // Bow/Mobile
		_mercenaries.put(6193, new MercInfo(35048, 2)); // Cleric/Mobile
		_mercenaries.put(6194, new MercInfo(35049, 2)); // Wizard/Mobile
		// Normal Recruits - Dion
		_mercenaries.put(6245, new MercInfo(35050, 2)); // Sword/Stationary
		_mercenaries.put(6246, new MercInfo(35051, 2)); // Spear/Stationary
		_mercenaries.put(6247, new MercInfo(35052, 2)); // Bow/Stationary
		_mercenaries.put(6248, new MercInfo(35053, 2)); // Cleric/Stationary
		_mercenaries.put(6249, new MercInfo(35054, 2)); // Wizard/Stationary
		_mercenaries.put(6250, new MercInfo(35055, 2)); // Sword/Mobile
		_mercenaries.put(6251, new MercInfo(35056, 2)); // Spear/Mobile
		_mercenaries.put(6252, new MercInfo(35057, 2)); // Bow/Mobile
		_mercenaries.put(6253, new MercInfo(35058, 2)); // Cleric/Mobile
		_mercenaries.put(6254, new MercInfo(35059, 2)); // Wizard/Mobile
		// Greater/Elite Dawn Mercenaries - Dion
		_mercenaries.put(6297, new MercInfo(35060, 2)); // Sword/Mobile
		_mercenaries.put(6298, new MercInfo(35061, 2)); // Wizard/Mobile
		
		// Normal Mercenaries - Giran
		_mercenaries.put(3986, new MercInfo(35010, 3)); // Sword/Stationary
		_mercenaries.put(3987, new MercInfo(35011, 3)); // Spear/Stationary
		_mercenaries.put(3988, new MercInfo(35012, 3)); // Bow/Stationary
		_mercenaries.put(3989, new MercInfo(35013, 3)); // Cleric/Stationary
		_mercenaries.put(3990, new MercInfo(35014, 3)); // Wizard/Stationary
		_mercenaries.put(3991, new MercInfo(35015, 3)); // Sword/Mobile
		_mercenaries.put(3992, new MercInfo(35016, 3)); // Spear/Mobile
		_mercenaries.put(3993, new MercInfo(35017, 3)); // Bow/Mobile
		_mercenaries.put(3994, new MercInfo(35018, 3)); // Cleric/Mobile
		_mercenaries.put(3995, new MercInfo(35019, 3)); // Wizard/Mobile
		// Teleporters - Giran
		_mercenaries.put(3996, new MercInfo(35176, 3)); // Teleporter
		_mercenaries.put(3997, new MercInfo(35177, 3)); // Teleporter
		_mercenaries.put(3998, new MercInfo(35178, 3)); // Teleporter
		// Greater/Elite Mercenaries - Giran
		_mercenaries.put(6064, new MercInfo(35030, 3)); // Sword/Stationary
		_mercenaries.put(6065, new MercInfo(35031, 3)); // Spear/Stationary
		_mercenaries.put(6066, new MercInfo(35032, 3)); // Bow/Stationary
		_mercenaries.put(6067, new MercInfo(35033, 3)); // Cleric/Stationary
		_mercenaries.put(6068, new MercInfo(35034, 3)); // Wizard/Stationary
		_mercenaries.put(6069, new MercInfo(35035, 3)); // Sword/Mobile
		_mercenaries.put(6070, new MercInfo(35036, 3)); // Spear/Mobile
		_mercenaries.put(6071, new MercInfo(35037, 3)); // Bow/Mobile
		_mercenaries.put(6072, new MercInfo(35038, 3)); // Cleric/Mobile
		_mercenaries.put(6073, new MercInfo(35039, 3)); // Wizard/Mobile
		// Dawn Mercenaries - Giran
		_mercenaries.put(6135, new MercInfo(35020, 3)); // Sword/Stationary
		_mercenaries.put(6136, new MercInfo(35021, 3)); // Spear/Stationary
		_mercenaries.put(6137, new MercInfo(35022, 3)); // Bow/Stationary
		_mercenaries.put(6138, new MercInfo(35023, 3)); // Cleric/Stationary
		_mercenaries.put(6139, new MercInfo(35024, 3)); // Wizard/Stationary
		_mercenaries.put(6140, new MercInfo(35025, 3)); // Sword/Mobile
		_mercenaries.put(6141, new MercInfo(35026, 3)); // Spear/Mobile
		_mercenaries.put(6142, new MercInfo(35027, 3)); // Bow/Mobile
		_mercenaries.put(6143, new MercInfo(35028, 3)); // Cleric/Mobile
		_mercenaries.put(6144, new MercInfo(35029, 3)); // Wizard/Mobile
		// Greater Recruits - Giran
		_mercenaries.put(6195, new MercInfo(35040, 3)); // Sword/Stationary
		_mercenaries.put(6196, new MercInfo(35041, 3)); // Spear/Stationary
		_mercenaries.put(6197, new MercInfo(35042, 3)); // Bow/Stationary
		_mercenaries.put(6198, new MercInfo(35043, 3)); // Cleric/Stationary
		_mercenaries.put(6199, new MercInfo(35044, 3)); // Wizard/Stationary
		_mercenaries.put(6200, new MercInfo(35045, 3)); // Sword/Mobile
		_mercenaries.put(6201, new MercInfo(35046, 3)); // Spear/Mobile
		_mercenaries.put(6202, new MercInfo(35047, 3)); // Bow/Mobile
		_mercenaries.put(6203, new MercInfo(35048, 3)); // Cleric/Mobile
		_mercenaries.put(6204, new MercInfo(35049, 3)); // Wizard/Mobile
		// Normal Recruits - Giran
		_mercenaries.put(6255, new MercInfo(35050, 3)); // Sword/Stationary
		_mercenaries.put(6256, new MercInfo(35051, 3)); // Spear/Stationary
		_mercenaries.put(6257, new MercInfo(35052, 3)); // Bow/Stationary
		_mercenaries.put(6258, new MercInfo(35053, 3)); // Cleric/Stationary
		_mercenaries.put(6259, new MercInfo(35054, 3)); // Wizard/Stationary
		_mercenaries.put(6260, new MercInfo(35055, 3)); // Sword/Mobile
		_mercenaries.put(6261, new MercInfo(35056, 3)); // Spear/Mobile
		_mercenaries.put(6262, new MercInfo(35057, 3)); // Bow/Mobile
		_mercenaries.put(6263, new MercInfo(35058, 3)); // Cleric/Mobile
		_mercenaries.put(6264, new MercInfo(35059, 3)); // Wizard/Mobile
		// Greater/Elite Dawn Mercenaries - Giran
		_mercenaries.put(6299, new MercInfo(35060, 3)); // Sword/Mobile
		_mercenaries.put(6300, new MercInfo(35061, 3)); // Wizard/Mobile
		
		// Normal Mercenaries - Oren
		_mercenaries.put(3999, new MercInfo(35010, 4)); // Sword/Stationary
		_mercenaries.put(4000, new MercInfo(35011, 4)); // Spear/Stationary
		_mercenaries.put(4001, new MercInfo(35012, 4)); // Bow/Stationary
		_mercenaries.put(4002, new MercInfo(35013, 4)); // Cleric/Stationary
		_mercenaries.put(4003, new MercInfo(35014, 4)); // Wizard/Stationary
		_mercenaries.put(4004, new MercInfo(35015, 4)); // Sword/Mobile
		_mercenaries.put(4005, new MercInfo(35016, 4)); // Spear/Mobile
		_mercenaries.put(4006, new MercInfo(35017, 4)); // Bow/Mobile
		_mercenaries.put(4007, new MercInfo(35018, 4)); // Cleric/Mobile
		_mercenaries.put(4008, new MercInfo(35019, 4)); // Wizard/Mobile
		// Teleporters - Oren
		_mercenaries.put(4009, new MercInfo(35218, 4)); // Teleporter
		_mercenaries.put(4010, new MercInfo(35219, 4)); // Teleporter
		_mercenaries.put(4011, new MercInfo(35220, 4)); // Teleporter
		// Greater/Elite Mercenaries - Oren
		_mercenaries.put(6077, new MercInfo(35030, 4)); // Sword/Stationary
		_mercenaries.put(6078, new MercInfo(35031, 4)); // Spear/Stationary
		_mercenaries.put(6079, new MercInfo(35032, 4)); // Bow/Stationary
		_mercenaries.put(6080, new MercInfo(35033, 4)); // Cleric/Stationary
		_mercenaries.put(6081, new MercInfo(35034, 4)); // Wizard/Stationary
		_mercenaries.put(6082, new MercInfo(35035, 4)); // Sword/Mobile
		_mercenaries.put(6083, new MercInfo(35036, 4)); // Spear/Mobile
		_mercenaries.put(6084, new MercInfo(35037, 4)); // Bow/Mobile
		_mercenaries.put(6085, new MercInfo(35038, 4)); // Cleric/Mobile
		_mercenaries.put(6086, new MercInfo(35039, 4)); // Wizard/Mobile
		// Dawn Mercenaries - Oren
		_mercenaries.put(6145, new MercInfo(35020, 4)); // Sword/Stationary
		_mercenaries.put(6146, new MercInfo(35021, 4)); // Spear/Stationary
		_mercenaries.put(6147, new MercInfo(35022, 4)); // Bow/Stationary
		_mercenaries.put(6148, new MercInfo(35023, 4)); // Cleric/Stationary
		_mercenaries.put(6149, new MercInfo(35024, 4)); // Wizard/Stationary
		_mercenaries.put(6150, new MercInfo(35025, 4)); // Sword/Mobile
		_mercenaries.put(6151, new MercInfo(35026, 4)); // Spear/Mobile
		_mercenaries.put(6152, new MercInfo(35027, 4)); // Bow/Mobile
		_mercenaries.put(6153, new MercInfo(35028, 4)); // Cleric/Mobile
		_mercenaries.put(6154, new MercInfo(35029, 4)); // Wizard/Mobile
		// Greater Recruits - Oren
		_mercenaries.put(6205, new MercInfo(35040, 4)); // Sword/Stationary
		_mercenaries.put(6206, new MercInfo(35041, 4)); // Spear/Stationary
		_mercenaries.put(6207, new MercInfo(35042, 4)); // Bow/Stationary
		_mercenaries.put(6208, new MercInfo(35043, 4)); // Cleric/Stationary
		_mercenaries.put(6209, new MercInfo(35044, 4)); // Wizard/Stationary
		_mercenaries.put(6210, new MercInfo(35045, 4)); // Sword/Mobile
		_mercenaries.put(6211, new MercInfo(35046, 4)); // Spear/Mobile
		_mercenaries.put(6212, new MercInfo(35047, 4)); // Bow/Mobile
		_mercenaries.put(6213, new MercInfo(35048, 4)); // Cleric/Mobile
		_mercenaries.put(6214, new MercInfo(35049, 4)); // Wizard/Mobile
		// Normal Recruits - Oren
		_mercenaries.put(6265, new MercInfo(35050, 4)); // Sword/Stationary
		_mercenaries.put(6266, new MercInfo(35051, 4)); // Spear/Stationary
		_mercenaries.put(6267, new MercInfo(35052, 4)); // Bow/Stationary
		_mercenaries.put(6268, new MercInfo(35053, 4)); // Cleric/Stationary
		_mercenaries.put(6269, new MercInfo(35054, 4)); // Wizard/Stationary
		_mercenaries.put(6270, new MercInfo(35055, 4)); // Sword/Mobile
		_mercenaries.put(6271, new MercInfo(35056, 4)); // Spear/Mobile
		_mercenaries.put(6272, new MercInfo(35057, 4)); // Bow/Mobile
		_mercenaries.put(6273, new MercInfo(35058, 4)); // Cleric/Mobile
		_mercenaries.put(6274, new MercInfo(35059, 4)); // Wizard/Mobile
		// Greater/Elite Dawn Mercenaries - Oren
		_mercenaries.put(6301, new MercInfo(35060, 4)); // Sword/Mobile
		_mercenaries.put(6302, new MercInfo(35061, 4)); // Wizard/Mobile
		
		// Normal Mercenaries - Aden
		_mercenaries.put(4012, new MercInfo(35010, 5)); // Sword/Stationary
		_mercenaries.put(4013, new MercInfo(35011, 5)); // Spear/Stationary
		_mercenaries.put(4014, new MercInfo(35012, 5)); // Bow/Stationary
		_mercenaries.put(4015, new MercInfo(35013, 5)); // Cleric/Stationary
		_mercenaries.put(4016, new MercInfo(35014, 5)); // Wizard/Stationary
		_mercenaries.put(4017, new MercInfo(35015, 5)); // Sword/Mobile
		_mercenaries.put(4018, new MercInfo(35016, 5)); // Spear/Mobile
		_mercenaries.put(4019, new MercInfo(35017, 5)); // Bow/Mobile
		_mercenaries.put(4020, new MercInfo(35018, 5)); // Cleric/Mobile
		_mercenaries.put(4021, new MercInfo(35019, 5)); // Wizard/Mobile
		// Teleporters - Aden
		_mercenaries.put(4022, new MercInfo(35261, 5)); // Teleporter
		_mercenaries.put(4023, new MercInfo(35262, 5)); // Teleporter
		_mercenaries.put(4024, new MercInfo(35263, 5)); // Teleporter
		_mercenaries.put(4025, new MercInfo(35264, 5)); // Teleporter
		_mercenaries.put(4026, new MercInfo(35265, 5)); // Teleporter
		// Greater/Elite Mercenaries - Aden
		_mercenaries.put(6090, new MercInfo(35030, 5)); // Sword/Stationary
		_mercenaries.put(6091, new MercInfo(35031, 5)); // Spear/Stationary
		_mercenaries.put(6092, new MercInfo(35032, 5)); // Bow/Stationary
		_mercenaries.put(6093, new MercInfo(35033, 5)); // Cleric/Stationary
		_mercenaries.put(6094, new MercInfo(35034, 5)); // Wizard/Stationary
		_mercenaries.put(6095, new MercInfo(35035, 5)); // Sword/Mobile
		_mercenaries.put(6096, new MercInfo(35036, 5)); // Spear/Mobile
		_mercenaries.put(6097, new MercInfo(35037, 5)); // Bow/Mobile
		_mercenaries.put(6098, new MercInfo(35038, 5)); // Cleric/Mobile
		_mercenaries.put(6099, new MercInfo(35039, 5)); // Wizard/Mobile
		// Dawn Mercenaries - Aden
		_mercenaries.put(6155, new MercInfo(35020, 5)); // Sword/Stationary
		_mercenaries.put(6156, new MercInfo(35021, 5)); // Spear/Stationary
		_mercenaries.put(6157, new MercInfo(35022, 5)); // Bow/Stationary
		_mercenaries.put(6158, new MercInfo(35023, 5)); // Cleric/Stationary
		_mercenaries.put(6159, new MercInfo(35024, 5)); // Wizard/Stationary
		_mercenaries.put(6160, new MercInfo(35025, 5)); // Sword/Mobile
		_mercenaries.put(6161, new MercInfo(35026, 5)); // Spear/Mobile
		_mercenaries.put(6162, new MercInfo(35027, 5)); // Bow/Mobile
		_mercenaries.put(6163, new MercInfo(35028, 5)); // Cleric/Mobile
		_mercenaries.put(6164, new MercInfo(35029, 5)); // Wizard/Mobile
		// Greater Recruits - Aden
		_mercenaries.put(6215, new MercInfo(35040, 5)); // Sword/Stationary
		_mercenaries.put(6216, new MercInfo(35041, 5)); // Spear/Stationary
		_mercenaries.put(6217, new MercInfo(35042, 5)); // Bow/Stationary
		_mercenaries.put(6218, new MercInfo(35043, 5)); // Cleric/Stationary
		_mercenaries.put(6219, new MercInfo(35044, 5)); // Wizard/Stationary
		_mercenaries.put(6220, new MercInfo(35045, 5)); // Sword/Mobile
		_mercenaries.put(6221, new MercInfo(35046, 5)); // Spear/Mobile
		_mercenaries.put(6222, new MercInfo(35047, 5)); // Bow/Mobile
		_mercenaries.put(6223, new MercInfo(35048, 5)); // Cleric/Mobile
		_mercenaries.put(6224, new MercInfo(35049, 5)); // Wizard/Mobile
		// Normal Recruits - Aden
		_mercenaries.put(6275, new MercInfo(35050, 5)); // Sword/Stationary
		_mercenaries.put(6276, new MercInfo(35051, 5)); // Spear/Stationary
		_mercenaries.put(6277, new MercInfo(35052, 5)); // Bow/Stationary
		_mercenaries.put(6278, new MercInfo(35053, 5)); // Cleric/Stationary
		_mercenaries.put(6279, new MercInfo(35054, 5)); // Wizard/Stationary
		_mercenaries.put(6280, new MercInfo(35055, 5)); // Sword/Mobile
		_mercenaries.put(6281, new MercInfo(35056, 5)); // Spear/Mobile
		_mercenaries.put(6282, new MercInfo(35057, 5)); // Bow/Mobile
		_mercenaries.put(6283, new MercInfo(35058, 5)); // Cleric/Mobile
		_mercenaries.put(6284, new MercInfo(35059, 5)); // Wizard/Mobile
		// Greater/Elite Dawn Mercenaries - Aden
		_mercenaries.put(6303, new MercInfo(35060, 5)); // Sword/Mobile
		_mercenaries.put(6304, new MercInfo(35061, 5)); // Wizard/Mobile
		
		// Normal Mercenaries - Innadril
		_mercenaries.put(5205, new MercInfo(35010, 6)); // Sword/Stationary
		_mercenaries.put(5206, new MercInfo(35011, 6)); // Spear/Stationary
		_mercenaries.put(5207, new MercInfo(35012, 6)); // Bow/Stationary
		_mercenaries.put(5208, new MercInfo(35013, 6)); // Cleric/Stationary
		_mercenaries.put(5209, new MercInfo(35014, 6)); // Wizard/Stationary
		_mercenaries.put(5210, new MercInfo(35015, 6)); // Sword/Mobile
		_mercenaries.put(5211, new MercInfo(35016, 6)); // Spear/Mobile
		_mercenaries.put(5212, new MercInfo(35017, 6)); // Bow/Mobile
		_mercenaries.put(5213, new MercInfo(35018, 6)); // Cleric/Mobile
		_mercenaries.put(5214, new MercInfo(35019, 6)); // Wizard/Mobile
		// Teleporters - Innadril
		_mercenaries.put(5215, new MercInfo(35308, 6)); // Teleporter/Inner Castle, Guard
		_mercenaries.put(5218, new MercInfo(35309, 6)); // Teleporter/Outer Castle, Guardian
		_mercenaries.put(5219, new MercInfo(35310, 6)); // Teleporter/Inner Castle, Outer Castle
		// Greater/Elite Mercenaries - Innadril
		_mercenaries.put(6105, new MercInfo(35030, 6)); // Sword/Stationary
		_mercenaries.put(6106, new MercInfo(35031, 6)); // Spear/Stationary
		_mercenaries.put(6107, new MercInfo(35032, 6)); // Bow/Stationary
		_mercenaries.put(6108, new MercInfo(35033, 6)); // Cleric/Stationary
		_mercenaries.put(6109, new MercInfo(35034, 6)); // Wizard/Stationary
		_mercenaries.put(6110, new MercInfo(35035, 6)); // Sword/Mobile
		_mercenaries.put(6111, new MercInfo(35036, 6)); // Spear/Mobile
		_mercenaries.put(6112, new MercInfo(35037, 6)); // Bow/Mobile
		_mercenaries.put(6113, new MercInfo(35038, 6)); // Cleric/Mobile
		_mercenaries.put(6114, new MercInfo(35039, 6)); // Wizard/Mobile
		// Dawn Mercenaries - Innadril
		_mercenaries.put(6165, new MercInfo(35020, 6)); // Sword/Stationary
		_mercenaries.put(6166, new MercInfo(35021, 6)); // Spear/Stationary
		_mercenaries.put(6167, new MercInfo(35022, 6)); // Bow/Stationary
		_mercenaries.put(6168, new MercInfo(35023, 6)); // Cleric/Stationary
		_mercenaries.put(6169, new MercInfo(35024, 6)); // Wizard/Stationary
		_mercenaries.put(6170, new MercInfo(35025, 6)); // Sword/Mobile
		_mercenaries.put(6171, new MercInfo(35026, 6)); // Spear/Mobile
		_mercenaries.put(6172, new MercInfo(35027, 6)); // Bow/Mobile
		_mercenaries.put(6173, new MercInfo(35028, 6)); // Cleric/Mobile
		_mercenaries.put(6174, new MercInfo(35029, 6)); // Wizard/Mobile
		// Greater Recruits - Innadril
		_mercenaries.put(6225, new MercInfo(35040, 6)); // Sword/Stationary
		_mercenaries.put(6226, new MercInfo(35041, 6)); // Spear/Stationary
		_mercenaries.put(6227, new MercInfo(35042, 6)); // Bow/Stationary
		_mercenaries.put(6228, new MercInfo(35043, 6)); // Cleric/Stationary
		_mercenaries.put(6229, new MercInfo(35044, 6)); // Wizard/Stationary
		_mercenaries.put(6230, new MercInfo(35045, 6)); // Sword/Mobile
		_mercenaries.put(6231, new MercInfo(35046, 6)); // Spear/Mobile
		_mercenaries.put(6232, new MercInfo(35047, 6)); // Bow/Mobile
		_mercenaries.put(6233, new MercInfo(35048, 6)); // Cleric/Mobile
		_mercenaries.put(6234, new MercInfo(35049, 6)); // Wizard/Mobile
		// Normal Recruits - Innadril
		_mercenaries.put(6285, new MercInfo(35050, 6)); // Sword/Stationary
		_mercenaries.put(6286, new MercInfo(35051, 6)); // Spear/Stationary
		_mercenaries.put(6287, new MercInfo(35052, 6)); // Bow/Stationary
		_mercenaries.put(6288, new MercInfo(35053, 6)); // Cleric/Stationary
		_mercenaries.put(6289, new MercInfo(35054, 6)); // Wizard/Stationary
		_mercenaries.put(6290, new MercInfo(35055, 6)); // Sword/Mobile
		_mercenaries.put(6291, new MercInfo(35056, 6)); // Spear/Mobile
		_mercenaries.put(6292, new MercInfo(35057, 6)); // Bow/Mobile
		_mercenaries.put(6293, new MercInfo(35058, 6)); // Cleric/Mobile
		_mercenaries.put(6294, new MercInfo(35059, 6)); // Wizard/Mobile
		// Greater/Elite Dawn Mercenaries - Innadril
		_mercenaries.put(6305, new MercInfo(35060, 6)); // Sword/Mobile
		_mercenaries.put(6306, new MercInfo(35061, 6)); // Wizard/Mobile
		
		// Normal Mercenaries - Goddard
		_mercenaries.put(6779, new MercInfo(35010, 7)); // Sword/Stationary
		_mercenaries.put(6780, new MercInfo(35011, 7)); // Spear/Stationary
		_mercenaries.put(6781, new MercInfo(35012, 7)); // Bow/Stationary
		_mercenaries.put(6782, new MercInfo(35013, 7)); // Cleric/Stationary
		_mercenaries.put(6783, new MercInfo(35014, 7)); // Wizard/Stationary
		_mercenaries.put(6784, new MercInfo(35015, 7)); // Sword/Mobile
		_mercenaries.put(6785, new MercInfo(35016, 7)); // Spear/Mobile
		_mercenaries.put(6786, new MercInfo(35017, 7)); // Bow/Mobile
		_mercenaries.put(6787, new MercInfo(35018, 7)); // Cleric/Mobile
		_mercenaries.put(6788, new MercInfo(35019, 7)); // Wizard/Mobile
		// Teleporters - Goddard
		_mercenaries.put(6789, new MercInfo(35352, 7)); // Teleporter
		_mercenaries.put(6790, new MercInfo(35353, 7)); // Teleporter
		_mercenaries.put(6791, new MercInfo(35354, 7)); // Teleporter
		// Greater/Elite Mercenaries - Goddard
		_mercenaries.put(6792, new MercInfo(35030, 7)); // Sword/Stationary
		_mercenaries.put(6793, new MercInfo(35031, 7)); // Spear/Stationary
		_mercenaries.put(6794, new MercInfo(35032, 7)); // Bow/Stationary
		_mercenaries.put(6795, new MercInfo(35033, 7)); // Cleric/Stationary
		_mercenaries.put(6796, new MercInfo(35034, 7)); // Wizard/Stationary
		_mercenaries.put(6797, new MercInfo(35035, 7)); // Sword/Mobile
		_mercenaries.put(6798, new MercInfo(35036, 7)); // Spear/Mobile
		_mercenaries.put(6799, new MercInfo(35037, 7)); // Bow/Mobile
		_mercenaries.put(6800, new MercInfo(35038, 7)); // Cleric/Mobile
		_mercenaries.put(6801, new MercInfo(35039, 7)); // Wizard/Mobile
		// Dawn Mercenaries - Goddard
		_mercenaries.put(6802, new MercInfo(35020, 7)); // Sword/Stationary
		_mercenaries.put(6803, new MercInfo(35021, 7)); // Spear/Stationary
		_mercenaries.put(6804, new MercInfo(35022, 7)); // Bow/Stationary
		_mercenaries.put(6805, new MercInfo(35023, 7)); // Cleric/Stationary
		_mercenaries.put(6806, new MercInfo(35024, 7)); // Wizard/Stationary
		_mercenaries.put(6807, new MercInfo(35025, 7)); // Sword/Mobile
		_mercenaries.put(6808, new MercInfo(35026, 7)); // Spear/Mobile
		_mercenaries.put(6809, new MercInfo(35027, 7)); // Bow/Mobile
		_mercenaries.put(6810, new MercInfo(35028, 7)); // Cleric/Mobile
		_mercenaries.put(6811, new MercInfo(35029, 7)); // Wizard/Mobile
		// Greater Recruits - Goddard
		_mercenaries.put(6812, new MercInfo(35040, 7)); // Sword/Stationary
		_mercenaries.put(6813, new MercInfo(35041, 7)); // Spear/Stationary
		_mercenaries.put(6814, new MercInfo(35042, 7)); // Bow/Stationary
		_mercenaries.put(6815, new MercInfo(35043, 7)); // Cleric/Stationary
		_mercenaries.put(6816, new MercInfo(35044, 7)); // Wizard/Stationary
		_mercenaries.put(6817, new MercInfo(35045, 7)); // Sword/Mobile
		_mercenaries.put(6818, new MercInfo(35046, 7)); // Spear/Mobile
		_mercenaries.put(6819, new MercInfo(35047, 7)); // Bow/Mobile
		_mercenaries.put(6820, new MercInfo(35048, 7)); // Cleric/Mobile
		_mercenaries.put(6821, new MercInfo(35049, 7)); // Wizard/Mobile
		// Normal Recruits - Goddard
		_mercenaries.put(6822, new MercInfo(35050, 7)); // Sword/Stationary
		_mercenaries.put(6823, new MercInfo(35051, 7)); // Spear/Stationary
		_mercenaries.put(6824, new MercInfo(35052, 7)); // Bow/Stationary
		_mercenaries.put(6825, new MercInfo(35053, 7)); // Cleric/Stationary
		_mercenaries.put(6826, new MercInfo(35054, 7)); // Wizard/Stationary
		_mercenaries.put(6827, new MercInfo(35055, 7)); // Sword/Mobile
		_mercenaries.put(6828, new MercInfo(35056, 7)); // Spear/Mobile
		_mercenaries.put(6829, new MercInfo(35057, 7)); // Bow/Mobile
		_mercenaries.put(6830, new MercInfo(35058, 7)); // Cleric/Mobile
		_mercenaries.put(6831, new MercInfo(35059, 7)); // Wizard/Mobile
		// Greater/Elite Dawn Mercenaries - Goddard
		_mercenaries.put(6832, new MercInfo(35060, 7)); // Sword/Mobile
		_mercenaries.put(6833, new MercInfo(35061, 7)); // Wizard/Mobile
		
		// Normal Mercenaries - Rune
		_mercenaries.put(7973, new MercInfo(35010, 8)); // Sword/Stationary
		_mercenaries.put(7974, new MercInfo(35011, 8)); // Spear/Stationary
		_mercenaries.put(7975, new MercInfo(35012, 8)); // Bow/Stationary
		_mercenaries.put(7976, new MercInfo(35013, 8)); // Cleric/Stationary
		_mercenaries.put(7977, new MercInfo(35014, 8)); // Wizard/Stationary
		_mercenaries.put(7978, new MercInfo(35015, 8)); // Sword/Mobile
		_mercenaries.put(7979, new MercInfo(35016, 8)); // Spear/Mobile
		_mercenaries.put(7980, new MercInfo(35017, 8)); // Bow/Mobile
		_mercenaries.put(7981, new MercInfo(35018, 8)); // Cleric/Mobile
		_mercenaries.put(7982, new MercInfo(35019, 8)); // Wizard/Mobile
		// Teleporters - Rune
		_mercenaries.put(7983, new MercInfo(35497, 8)); // Teleporter
		_mercenaries.put(7984, new MercInfo(35498, 8)); // Teleporter
		_mercenaries.put(7985, new MercInfo(35499, 8)); // Teleporter
		_mercenaries.put(7986, new MercInfo(35500, 8)); // Teleporter
		_mercenaries.put(7987, new MercInfo(35501, 8)); // Teleporter
		// Greater/Elite Mercenaries - Rune
		_mercenaries.put(7988, new MercInfo(35030, 8)); // Sword/Stationary
		_mercenaries.put(7989, new MercInfo(35031, 8)); // Spear/Stationary
		_mercenaries.put(7990, new MercInfo(35032, 8)); // Bow/Stationary
		_mercenaries.put(7991, new MercInfo(35033, 8)); // Cleric/Stationary
		_mercenaries.put(7992, new MercInfo(35034, 8)); // Wizard/Stationary
		_mercenaries.put(7993, new MercInfo(35035, 8)); // Sword/Mobile
		_mercenaries.put(7994, new MercInfo(35036, 8)); // Spear/Mobile
		_mercenaries.put(7995, new MercInfo(35037, 8)); // Bow/Mobile
		_mercenaries.put(7996, new MercInfo(35038, 8)); // Cleric/Mobile
		_mercenaries.put(7997, new MercInfo(35039, 8)); // Wizard/Mobile
		// Dawn Mercenaries - Rune
		_mercenaries.put(7998, new MercInfo(35020, 8)); // Sword/Stationary
		_mercenaries.put(7999, new MercInfo(35021, 8)); // Spear/Stationary
		_mercenaries.put(8000, new MercInfo(35022, 8)); // Bow/Stationary
		_mercenaries.put(8001, new MercInfo(35023, 8)); // Cleric/Stationary
		_mercenaries.put(8002, new MercInfo(35024, 8)); // Wizard/Stationary
		_mercenaries.put(8003, new MercInfo(35025, 8)); // Sword/Mobile
		_mercenaries.put(8004, new MercInfo(35026, 8)); // Spear/Mobile
		_mercenaries.put(8005, new MercInfo(35027, 8)); // Bow/Mobile
		_mercenaries.put(8006, new MercInfo(35028, 8)); // Cleric/Mobile
		_mercenaries.put(8007, new MercInfo(35029, 8)); // Wizard/Mobile
		// Greater Recruits - Rune
		_mercenaries.put(8008, new MercInfo(35040, 8)); // Sword/Stationary
		_mercenaries.put(8009, new MercInfo(35041, 8)); // Spear/Stationary
		_mercenaries.put(8010, new MercInfo(35042, 8)); // Bow/Stationary
		_mercenaries.put(8011, new MercInfo(35043, 8)); // Cleric/Stationary
		_mercenaries.put(8012, new MercInfo(35044, 8)); // Wizard/Stationary
		_mercenaries.put(8013, new MercInfo(35045, 8)); // Sword/Mobile
		_mercenaries.put(8014, new MercInfo(35046, 8)); // Spear/Mobile
		_mercenaries.put(8015, new MercInfo(35047, 8)); // Bow/Mobile
		_mercenaries.put(8016, new MercInfo(35048, 8)); // Cleric/Mobile
		_mercenaries.put(8017, new MercInfo(35049, 8)); // Wizard/Mobile
		// Normal Recruits - Rune
		_mercenaries.put(8018, new MercInfo(35050, 8)); // Sword/Stationary
		_mercenaries.put(8019, new MercInfo(35051, 8)); // Spear/Stationary
		_mercenaries.put(8020, new MercInfo(35052, 8)); // Bow/Stationary
		_mercenaries.put(8021, new MercInfo(35053, 8)); // Cleric/Stationary
		_mercenaries.put(8022, new MercInfo(35054, 8)); // Wizard/Stationary
		_mercenaries.put(8023, new MercInfo(35055, 8)); // Sword/Mobile
		_mercenaries.put(8024, new MercInfo(35056, 8)); // Spear/Mobile
		_mercenaries.put(8025, new MercInfo(35057, 8)); // Bow/Mobile
		_mercenaries.put(8026, new MercInfo(35058, 8)); // Cleric/Mobile
		_mercenaries.put(8027, new MercInfo(35059, 8)); // Wizard/Mobile
		// Greater/Elite Dawn Mercenaries - Rune
		_mercenaries.put(8028, new MercInfo(35060, 8)); // Sword/Mobile
		_mercenaries.put(8029, new MercInfo(35061, 8)); // Wizard/Mobile
		
		// Normal Mercenaries - Schuttgart
		_mercenaries.put(7918, new MercInfo(35010, 9)); // Sword/Stationary
		_mercenaries.put(7919, new MercInfo(35011, 9)); // Spear/Stationary
		_mercenaries.put(7920, new MercInfo(35012, 9)); // Bow/Stationary
		_mercenaries.put(7921, new MercInfo(35013, 9)); // Cleric/Stationary
		_mercenaries.put(7922, new MercInfo(35014, 9)); // Wizard/Stationary
		_mercenaries.put(7923, new MercInfo(35015, 9)); // Sword/Mobile
		_mercenaries.put(7924, new MercInfo(35016, 9)); // Spear/Mobile
		_mercenaries.put(7925, new MercInfo(35017, 9)); // Bow/Mobile
		_mercenaries.put(7926, new MercInfo(35018, 9)); // Cleric/Mobile
		_mercenaries.put(7927, new MercInfo(35019, 9)); // Wizard/Mobile
		// Teleporters - Schuttgart
		_mercenaries.put(7928, new MercInfo(35544, 9)); // Teleporter
		_mercenaries.put(7929, new MercInfo(35545, 9)); // Teleporter
		_mercenaries.put(7930, new MercInfo(35546, 9)); // Teleporter
		// Greater/Elite Mercenaries - Schuttgart
		_mercenaries.put(7931, new MercInfo(35030, 9)); // Sword/Stationary
		_mercenaries.put(7932, new MercInfo(35031, 9)); // Spear/Stationary
		_mercenaries.put(7933, new MercInfo(35032, 9)); // Bow/Stationary
		_mercenaries.put(7934, new MercInfo(35033, 9)); // Cleric/Stationary
		_mercenaries.put(7935, new MercInfo(35034, 9)); // Wizard/Stationary
		_mercenaries.put(7936, new MercInfo(35035, 9)); // Sword/Mobile
		_mercenaries.put(7937, new MercInfo(35036, 9)); // Spear/Mobile
		_mercenaries.put(7938, new MercInfo(35037, 9)); // Bow/Mobile
		_mercenaries.put(7939, new MercInfo(35038, 9)); // Cleric/Mobile
		_mercenaries.put(7940, new MercInfo(35039, 9)); // Wizard/Mobile
		// Dawn Mercenaries - Schuttgart
		_mercenaries.put(7941, new MercInfo(35020, 9)); // Sword/Stationary
		_mercenaries.put(7942, new MercInfo(35021, 9)); // Spear/Stationary
		_mercenaries.put(7943, new MercInfo(35022, 9)); // Bow/Stationary
		_mercenaries.put(7944, new MercInfo(35023, 9)); // Cleric/Stationary
		_mercenaries.put(7945, new MercInfo(35024, 9)); // Wizard/Stationary
		_mercenaries.put(7946, new MercInfo(35025, 9)); // Sword/Mobile
		_mercenaries.put(7947, new MercInfo(35026, 9)); // Spear/Mobile
		_mercenaries.put(7948, new MercInfo(35027, 9)); // Bow/Mobile
		_mercenaries.put(7949, new MercInfo(35028, 9)); // Cleric/Mobile
		_mercenaries.put(7950, new MercInfo(35029, 9)); // Wizard/Mobile
		// Greater Recruits - Schuttgart
		_mercenaries.put(7951, new MercInfo(35040, 9)); // Sword/Stationary
		_mercenaries.put(7952, new MercInfo(35041, 9)); // Spear/Stationary
		_mercenaries.put(7953, new MercInfo(35042, 9)); // Bow/Stationary
		_mercenaries.put(7954, new MercInfo(35043, 9)); // Cleric/Stationary
		_mercenaries.put(7955, new MercInfo(35044, 9)); // Wizard/Stationary
		_mercenaries.put(7956, new MercInfo(35045, 9)); // Sword/Mobile
		_mercenaries.put(7957, new MercInfo(35046, 9)); // Spear/Mobile
		_mercenaries.put(7958, new MercInfo(35047, 9)); // Bow/Mobile
		_mercenaries.put(7959, new MercInfo(35048, 9)); // Cleric/Mobile
		_mercenaries.put(7960, new MercInfo(35049, 9)); // Wizard/Mobile
		// Normal Recruits - Schuttgart
		_mercenaries.put(7961, new MercInfo(35050, 9)); // Sword/Stationary
		_mercenaries.put(7962, new MercInfo(35051, 9)); // Spear/Stationary
		_mercenaries.put(7963, new MercInfo(35052, 9)); // Bow/Stationary
		_mercenaries.put(7964, new MercInfo(35053, 9)); // Cleric/Stationary
		_mercenaries.put(7965, new MercInfo(35054, 9)); // Wizard/Stationary
		_mercenaries.put(7966, new MercInfo(35055, 9)); // Sword/Mobile
		_mercenaries.put(7967, new MercInfo(35056, 9)); // Spear/Mobile
		_mercenaries.put(7968, new MercInfo(35057, 9)); // Bow/Mobile
		_mercenaries.put(7969, new MercInfo(35058, 9)); // Cleric/Mobile
		_mercenaries.put(7970, new MercInfo(35059, 9)); // Wizard/Mobile
		// Greater/Elite Dawn Mercenaries - Schuttgart
		_mercenaries.put(7971, new MercInfo(35060, 9)); // Sword/Mobile
		_mercenaries.put(7972, new MercInfo(35061, 9)); // Wizard/Mobile
		
		// Someone forgot to finish removing backwards compatibility in MercTicket
		// So we need an int[] array, and we need it ready
		Set<Integer> ids = _mercenaries.keySet();
		_handlerIds = new int[ids.size()];
		Iterator<Integer> it = ids.iterator();
		for (int i = 0; i < _handlerIds.length; i++)
			_handlerIds[i] = it.next();
	}
	
	/**
	 * Fills the hired mercenary position map:
	 * <LI>Load the positions from the database</LI>
	 * <LI>Filter positions against current config & possible exploits</LI>
	 * <LI>Create an item that represents the mercenary</LI>
	 * <LI>Spawn the item at the position</LI>
	 */
	private final void fillPositions()
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement ps = con.prepareStatement(LOAD_POSITIONS);
			ResultSet rs = ps.executeQuery();
			while (rs.next())
			{
				int item = rs.getInt("item_template_id");
				int x = rs.getInt("x");
				int y = rs.getInt("y");
				int z = rs.getInt("z");
				Castle c = CastleManager.getInstance().getCastle(x, y, z);
				if (c == null)
				{
					_log.warn("Mercenary at " + x + ";" + y + ";" + z + " isn't assigned to any castle, removed.");
					continue;
				}
				List<L2ItemInstance> posts = getPositions(c.getCastleId());
				if (posts.size() == CASTLE_HIRE_LIMIT[c.getCastleId() - 1])
				{
					_log.warn("Mercenary at " + x + ";" + y + ";" + z
							+ " exceeds the castle's hireling limit, removed.");
					continue;
				}
				MercInfo mi = _mercenaries.get(item);
				if (mi == null)
				{
					_log.warn("Unknown mercenary ticket " + item + ", mercenary post removed.");
					continue;
				}
				else if (mi.getCastleId() != c.getCastleId())
				{
					_log.warn("Mercenary at " + x + ";" + y + ";" + z + " is assigned to the wrong castle, removed.");
					continue;
				}
				spawnTicket(item, x, y, z, rs.getInt("heading"), false);
			}
		}
		catch (Exception e)
		{
			_log.error("Could not load castle mercenaries!", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	/**
	 * Gets the currently assigned mercenary positions.<BR>
	 * <B><U>Do not use this method to clear() the positions without
	 * explicitly updating the database!</U></B>
	 * @param castle Castle's ID
	 * @return mercenary position list
	 */
	public final List<L2ItemInstance> getPositions(int castle)
	{
		List<L2ItemInstance> posts = _positions.get(castle);
		if (posts == null)
		{
			posts = new ArrayList<L2ItemInstance>();
			_positions.put(castle, posts);
		}
		return posts;
	}
	
	/**
	 * Gets the currently assigned mercenary positions.
	 * @param castle Castle's ID
	 * @return mercenary position list
	 */
	public final int getTypeLimit(int item)
	{
		Integer i = _typeLimit.get(item);
		if (i == null)
			return CASTLE_TYPE_LIMIT[_mercenaries.get(item).getCastleArrayId()];
		else
			return i.intValue();
	}
	
	/**
	 * Counts how many specific mercenaries are hired already.
	 * @param castle castle ID
	 * @param item mercenary posting ticket item ID
	 * @return hireling count
	 */
	public final int getTypeHired(int castle, int item)
	{
		List<L2ItemInstance> posts = getPositions(castle);
		L2ItemInstance[] tickets = posts.toArray(new L2ItemInstance[posts.size()]);
		int total = 0;
		for (L2ItemInstance tick : tickets)
			if (tick.getItemId() == item)
				total++;
		return total;
	}
	
	/**
	 * Calculates the distance between the player's position and all
	 * hireling positions and determines if the player's position
	 * isn't too close to another hireling's position.
	 * @param x position's coordinate X
	 * @param y position's coordinate Y
	 * @param z position's coordinate Z
	 * @param castle castle ID
	 * @return is it fine to position a mercenary
	 */
	private final boolean isDistanceValid(int x, int y, int z, int castle)
	{
		List<L2ItemInstance> posts = getPositions(castle);
		L2ItemInstance[] tickets = posts.toArray(new L2ItemInstance[posts.size()]);
		for (L2ItemInstance item : tickets)
		{
			double dx = x - item.getX();
			double dy = y - item.getY();
			double dz = z - item.getZ();
			
			if ((dx * dx + dy * dy + dz * dz) < 25 * 25)
				return false;
		}
		return true;
	}
	
	/**
	 * Called when a player double-clicks (or tries to drop) a mercenary posting ticket.
	 * This means a confirm dialog is sent to the player, so we need to store main data.
	 * @param player The castle owning clan member
	 * @param merc The mercenary posting ticket
	 */
	public final void reqPosition(final L2Player player, final L2ItemInstance merc)
	{
		if (player == null || merc == null)
			return;
		
		final ConfirmDlg dlg = new ConfirmDlg(SystemMessageId.PLACE_S1_CURRENT_LOCATION_DIRECTION);
		dlg.addItemName(merc);
		dlg.addAnswerHandler(new AnswerHandler() {
			@Override
			public void handle(boolean answer)
			{
				if (answer)
					addPosition(player, merc.getObjectId());
			}
		});
		
		player.sendPacket(dlg);
	}
	
	/**
	 * Adds a new mercenary position, that is: drops the ticket from the
	 * player's inventory (on player's position), temporarily spawns the
	 * mercenary npc (just to show) and adds the position to castle.<BR>
	 * However, that is done only if all following conditions are met:
	 * <LI>Player has requested a position (by using or dropping the ticket)</LI>
	 * <LI>The ticket is still in player's inventory</LI>
	 * <LI>Player is on castle's ground</LI>
	 * <LI>Player is positioning the correct mercenary for this castle</LI>
	 * <LI>Player has the privilege to position mercenaries</LI>
	 * <LI>It is not siege nor SSQ competition period</LI>
	 * <LI>This castle wasn't already sieged during the current validation period</LI>
	 * <LI>Mercenary ticket's limit (in this castle) isn't exceeded</LI>
	 * <LI>Castle's mercenary limit isn't exceeded</LI>
	 * <LI>Player is far enough from other mercenary positions</LI>
	 * <LI>A recruit is positioned <I>(when Dusk owns Seal of Strife)</I></LI>
	 * <LI>A non-dawn mercenary is positioned <I>(when Seal of Strife is unclaimed)</I></LI>
	 * @param player which confirmed mercenary positioning
	 */
	public final void addPosition(L2Player player, Integer itemObjId)
	{
		L2ItemInstance ticket = player.getInventory().getItemByObjectId(itemObjId);
		if (ticket == null)
			return;
		if (!Config.ALT_SPAWN_SIEGE_GUARD)
		{
			player.sendPacket(SystemMessageId.TRY_AGAIN_LATER);
			return;
		}
		MercInfo mi = _mercenaries.get(ticket.getItemId());
		// The item handler guarantees it's a posting ticket
		// By creating _handlerIds we guarantee we know all IDs that are handled
		if (mi == null)
		{
			_log.error("A known mercenary is unknown? Item ID: " + ticket.getItemId());
			player.sendPacket(new SystemMessage(SystemMessageId.S1_DOES_NOT_EXIST).addItemName(ticket));
			if (player.isGM())
				player.sendMessage("The mercenary system is messed up. Please consult @ l2jfree.com");
			return;
		}
		// In-case of player running around in very short bursts
		int x = player.getX(), y = player.getY(), z = player.getZ();
		Castle c = CastleManager.getInstance().getCastle(player);
		if (c == null)
		{
			player.sendPacket(SystemMessageId.MERCENARIES_CANNOT_BE_POSITIONED_HERE);
			return;
		}
		else if (c.getCastleId() != mi.getCastleId())
			return;
		else if (!L2Clan.checkPrivileges(player, L2Clan.CP_CS_MERCENARIES))
		{
			player.sendPacket(SystemMessageId.YOU_DO_NOT_HAVE_AUTHORITY_TO_POSITION_MERCENARIES);
			return;
		}
		else if (c.getSiege().getIsInProgress())
		{
			player.sendPacket(SystemMessageId.CANNOT_POSITION_MERCS_DURING_SIEGE);
			return;
		}
		else if (!SevenSigns.getInstance().isSealValidationPeriod())
		{
			player.sendPacket(SystemMessageId.MERC_CAN_BE_ASSIGNED);
			return;
		}
		else if (c.getSiege().getSiegeDate().getTimeInMillis() > System.currentTimeMillis() + WEEK)
		{
			player.sendPacket(SystemMessageId.MERC_CAN_BE_ASSIGNED);
			return;
		}
		else if (getTypeHired(c.getCastleId(), ticket.getItemId()) >= getTypeLimit(ticket.getItemId()))
		{
			player.sendPacket(SystemMessageId.THIS_MERCENARY_CANNOT_BE_POSITIONED_ANYMORE);
			return;
		}
		else if (getPositions(c.getCastleId()).size() >= CASTLE_HIRE_LIMIT[mi.getCastleArrayId()])
		{
			player.sendPacket(SystemMessageId.THIS_MERCENARY_CANNOT_BE_POSITIONED_ANYMORE);
			return;
		}
		else if (!isDistanceValid(x, y, z, c.getCastleId()))
		{
			player.sendPacket(SystemMessageId.POSITIONING_CANNOT_BE_DONE_BECAUSE_DISTANCE_BETWEEN_MERCENARIES_TOO_SHORT);
			return;
		}
		switch (SevenSigns.getInstance().getSealOwner(SevenSigns.SEAL_STRIFE))
		{
			case SevenSigns.CABAL_NULL:
				// Nowadays you can't have recruits (read the ticket description)
				if (mi.isDawnMercenary() || mi.isRecruit())
				{
					player.sendPacket(SystemMessageId.MERC_CANT_BE_ASSIGNED_USING_STRIFE);
					return;
				}
				break;
			case SevenSigns.CABAL_DUSK:
				if (!mi.isRecruit())
				{
					player.sendPacket(SystemMessageId.MERC_CANT_BE_ASSIGNED_USING_STRIFE);
					return;
				}
				break;
		}
		if (!player.destroyItem("Mercenary posting", ticket, 1, ticket, false))
			return;
		
		spawnTicket(ticket.getItemId(), x, y, z, player.getHeading(), true);
		save(false, ticket.getItemId(), x, y, z, player.getHeading());
	}
	
	/**
	 * Assuming the ticket has been already removed from player's inventory,
	 * a new item instance is created and spawned on a given location (which by
	 * default is the player's location) and added to {@link #_positions}.<BR>
	 * If this method was called during server startup, <code>npc</code> will be
	 * false, that means no NPC will be spawned. If <code>npc</code> is true, a
	 * NPC representing the mercenary ticket will be temporarily spawned after
	 * positioning.
	 * @param item Mercenary ticket item ID
	 * @param x Mercenary position's coordinate X
	 * @param y Mercenary position's coordinate Y
	 * @param z Mercenary position's coordinate Z
	 * @param heading Mercenary position's heading
	 * @param npc Spawn the mercenary NPC temporarily?
	 */
	private final void spawnTicket(int item, int x, int y, int z, int heading, boolean npc)
	{
		MercInfo mi = _mercenaries.get(item);
		L2ItemInstance post = new L2ItemInstance(IdFactory.getInstance().getNextId(), item);
		post.setLocation(L2ItemInstance.ItemLocation.VOID);
		post.setHeading(heading);
		post.dropMe(null, x, y, z);
		post.setDropTime(0); // must be ignored by the timed drop remover
		L2World.getInstance().storeObject(post);
		getPositions(mi.getCastleId()).add(post);
		if (npc)
		{
			L2NpcTemplate temp = NpcTable.getInstance().getTemplate(mi.getNpcId());
			if (temp == null)
			{
				_log.warn("Missing mercenary NPC: " + mi.getNpcId());
				return;
			}
			L2Npc cha = null;
			if (temp.isAssignableTo(L2SiegeTeleporterInstance.class))
				cha = new L2SiegeTeleporterInstance(IdFactory.getInstance().getNextId(), temp);
			else
				cha = new L2SiegeGuardInstance(IdFactory.getInstance().getNextId(), temp);
			final L2Npc merc = cha;
			merc.getStatus().setCurrentHpMp(merc.getMaxHp(), merc.getMaxMp());
			merc.setDecayed(false);
			merc.spawnMe(x, y, (z + 20));
			AutoChatManager.getInstance().registerChat(merc, MercTicketManager.MESSAGES, 0);
			ThreadPoolManager.getInstance().scheduleGeneral(new Runnable() {
				@Override
				public void run()
				{
					merc.deleteMe();
				}
			}, 3000);
		}
	}
	
	/**
	 * Returns this manager's opinion about a player trying to pickup an item.<BR>
	 * Informational (SM) packets are sent if check returns false.
	 * @param player a player
	 * @param ticket an item
	 * @return allow player to pick up this item
	 */
	public final boolean canPickUp(L2Player player, L2ItemInstance ticket)
	{
		// not a mercenary posting ticket
		if (ticket == null || !isTicket(ticket.getItemId()))
			return true;
		
		MercInfo mi = _mercenaries.get(ticket.getItemId());
		L2Clan clan = player.getClan();
		if (clan == null || clan.getHasCastle() != mi.getCastleId())
		{
			player.sendPacket(SystemMessageId.THIS_IS_NOT_A_MERCENARY_OF_A_CASTLE_THAT_YOU_OWN_AND_SO_CANNOT_CANCEL_POSITIONING);
			return false;
		}
		else if (!L2Clan.checkPrivileges(player, L2Clan.CP_CS_MERCENARIES))
		{
			player.sendPacket(SystemMessageId.YOU_DO_NOT_HAVE_AUTHORITY_TO_CANCEL_MERCENARY_POSITIONING);
			return false;
		}
		else if (CastleManager.getInstance().getCastleById(mi.getCastleId()).getSiege().getIsInProgress())
		{
			// too late
			return false;
		}
		else
			return true;
	}
	
	/**
	 * This method should only be called from {@link L2ItemInstance#pickupMe(L2Creature)}
	 * without any additional checks.<BR>
	 * The item is validated if it is a mercenary ticket and then the position is cleared
	 * from {@link #_positions} and removed from the database.
	 * @param ticket
	 */
	public final void remPosition(L2ItemInstance ticket)
	{
		if (!isTicket(ticket.getItemId()))
			return;
		MercInfo mi = _mercenaries.get(ticket.getItemId());
		getPositions(mi.getCastleId()).remove(ticket);
		save(true, ticket.getX(), ticket.getY(), ticket.getZ());
	}
	
	/**
	 * Saves a mercenary position to the database. Saving will be performed
	 * according to the value of {@link Config#MERCENARY_SAVING_DELAY}.
	 * @param delete true to remove the position, false to add a new position
	 * @param ticket when removing the position pass x, y, z (in that order);
	 * when adding a position, pass itemID, x, y, z, heading (in that order)
	 */
	private final void save(boolean delete, int... ticket)
	{
		if (Config.MERCENARY_SAVING_DELAY == 0)
		{
			Connection con = null;
			try
			{
				con = L2DatabaseFactory.getInstance().getConnection();
				PreparedStatement ps;
				if (delete)
				{
					ps = con.prepareStatement(REMOVE_POSITION);
					ps.setInt(1, ticket[0]);
					ps.setInt(2, ticket[1]);
					ps.setInt(3, ticket[2]);
				}
				else
				{
					ps = con.prepareStatement(ADD_POSITION);
					ps.setInt(1, ticket[0]);
					ps.setInt(2, ticket[1]);
					ps.setInt(3, ticket[2]);
					ps.setInt(4, ticket[3]);
					ps.setInt(5, ticket[4]);
				}
				ps.executeUpdate();
				ps.close();
			}
			catch (Exception e)
			{
				_log.error("Could not update mercenary position!", e);
			}
			finally
			{
				L2DatabaseFactory.close(con);
			}
		}
		else if (_update == null)
			ThreadPoolManager.getInstance().scheduleGeneral(new PostSaver(), Config.MERCENARY_SAVING_DELAY);
	}
	
	/**
	 * Removes all mercenary positions from the database and then saves
	 * each position currently in {@link #_positions} to the database.
	 */
	public final void saveAll()
	{
		_update = null;
		// The old positions go and the current ones are stored, all together
		WorldTransaction.run("Saving the mercenary positions", () -> {
			Connection con = null;
			try
			{
				con = L2DatabaseFactory.getInstance().getConnection();
				PreparedStatement ps = con.prepareStatement(CLEAN_POSITIONS);
				ps.executeUpdate();
				ps.close();
				for (List<L2ItemInstance> posts : _positions.values())
				{
					L2ItemInstance[] pos = posts.toArray(new L2ItemInstance[posts.size()]);
					for (L2ItemInstance post : pos)
					{
						ps = con.prepareStatement(ADD_POSITION);
						ps.setInt(1, post.getItemId());
						ps.setInt(2, post.getX());
						ps.setInt(3, post.getY());
						ps.setInt(4, post.getZ());
						ps.setInt(5, post.getHeading());
						ps.executeUpdate();
						ps.close();
					}
				}
			}
			catch (SQLException e)
			{
				throw new IllegalStateException("Could not save mercenary positions!", e);
			}
			finally
			{
				L2DatabaseFactory.close(con);
			}
		});
	}
	
	/**
	 * Builds the mercenary spawns for a <CODE>SiegeGuardManager</CODE>.
	 * <U>Removes all tickets from L2World and clears the position list.</U>
	 * @param sgm
	 */
	public final void buildSpawns(SiegeGuardManager sgm)
	{
		List<L2ItemInstance> posts = getPositions(sgm.getCastle().getCastleId());
		L2ItemInstance[] arr = posts.toArray(new L2ItemInstance[posts.size()]);
		for (L2ItemInstance pos : arr)
		{
			MercInfo mi = _mercenaries.get(pos.getItemId());
			sgm.addMercenary(mi.getNpcId(), pos.getX(), pos.getY(), pos.getZ(), pos.getHeading());
			L2World.getInstance().removeVisibleObject(pos, pos.getWorldRegion());
			L2World.getInstance().removeObject(pos);
			save(true, pos.getX(), pos.getY(), pos.getZ());
		}
		posts.clear();
	}
	
	/**
	 * Checks if this item ID is mapped.
	 * @param item Item ID
	 * @return is this item a mercenary ticket
	 */
	public final boolean isTicket(Integer item)
	{
		return _mercenaries.containsKey(item);
	}
	
	/**
	 * When {@link Config#MERCENARY_SAVING_DELAY} is above 0, this is used
	 * to determine if saving is necessary at this very moment (called when
	 * shutting down server by default). As the name says, if cancels the
	 * update task.
	 * @return whether there was a scheduled save task
	 */
	public final boolean stopSaveTask()
	{
		if (_update != null)
			_update.cancel(true);
		return _update != null;
	}
	
	/**
	 * Necessary to provide IDs for the ItemHandler.
	 * @return mercenary posting ticket item ID array
	 */
	public final int[] getItemIds()
	{
		return _handlerIds;
	}
	
	/**
	 * Knowing that the item ID is mapped, all we need is the NPC ID.
	 * However, I've also included the castle ID for easy exploit protection.
	 * @author savormix
	 */
	private final class MercInfo
	{
		private final int npc;
		private final int castle;
		
		private MercInfo(int npc, int castle)
		{
			this.npc = npc;
			this.castle = castle;
		}
		
		private final int getNpcId()
		{
			return npc;
		}
		
		private final int getCastleId()
		{
			return castle;
		}
		
		private final int getCastleArrayId()
		{
			return getCastleId() - 1;
		}
		
		/** @return whether this is a raw recruit that can be hired only if Dusk owns Strife */
		private final boolean isRecruit()
		{
			return getNpcId() >= RECRUIT_MIN && getNpcId() <= RECRUIT_MAX;
		}
		
		/** @return whether this is a mercenary that can be hired only if Dawn owns Strife */
		private final boolean isDawnMercenary()
		{
			return ((getNpcId() >= DAWN_MERCENARY_MIN && getNpcId() <= DAWN_MERCENARY_MAX) || (getNpcId() >= DAWN_MERCENARY_ELITE_MIN && getNpcId() <= DAWN_MERCENARY_ELITE_MAX));
		}
	}
	
	/**
	 * This task just calls {@link MercTicketManager#saveAll()}.<BR><BR>
	 * You may wonder about the name, because mercenaries don't really have
	 * much to do with post and envelopes, however we have <B>mercenary
	 * <U>posting</U> tickets</B> so I assume "a post" is legal in english
	 * as well.
	 * @author savormix
	 */
	private final class PostSaver implements Runnable
	{
		@Override
		public void run()
		{
			saveAll();
		}
	}
	
	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder
	{
		protected static final MercTicketManager _instance = new MercTicketManager();
	}
}
