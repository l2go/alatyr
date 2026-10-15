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
package com.l2jfree.gameserver.datatables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.gameobjects.instance.L2PetInstance;
import com.l2jfree.gameserver.model.L2PetData;

public class PetDataTable
{
	private final static Logger _log = LoggerFactory.getLogger(L2PetInstance.class);
	
	public final static int PET_WOLF_ID = 12077;
	
	public final static int HATCHLING_WIND_ID = 12311;
	public final static int HATCHLING_STAR_ID = 12312;
	public final static int HATCHLING_TWILIGHT_ID = 12313;
	
	public final static int STRIDER_WIND_ID = 12526;
	public final static int STRIDER_STAR_ID = 12527;
	public final static int STRIDER_TWILIGHT_ID = 12528;
	
	public final static int RED_STRIDER_WIND_ID = 16038;
	public final static int RED_STRIDER_STAR_ID = 16039;
	public final static int RED_STRIDER_TWILIGHT_ID = 16040;
	
	public final static int WYVERN_ID = 12621;
	
	public final static int BABY_BUFFALO_ID = 12780;
	public final static int BABY_KOOKABURRA_ID = 12781;
	public final static int BABY_COUGAR_ID = 12782;
	
	public final static int IMPROVED_BABY_BUFFALO_ID = 16034;
	public final static int IMPROVED_BABY_KOOKABURRA_ID = 16035;
	public final static int IMPROVED_BABY_COUGAR_ID = 16036;
	
	public final static int SIN_EATER_ID = 12564;
	
	public final static int BLACK_WOLF_ID = 16030;
	public final static int WGREAT_WOLF_ID = 16037;
	public final static int GREAT_WOLF_ID = 16025;
	public final static int FENRIR_WOLF_ID = 16041;
	public final static int WFENRIR_WOLF_ID = 16042;
	
	public final static int PURPLE_HORSE_ID = 13130;
	public final static int TAWNY_MANED_LION_ID = 13146;
	public final static int STEAM_SLEDGE = 13147;
	
	private static Map<Integer, Map<Integer, L2PetData>> petTable;
	
	public final static int[] EMPTY_INT = { 0 };
	
	public static PetDataTable getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private PetDataTable()
	{
		petTable = new LinkedHashMap<Integer, Map<Integer, L2PetData>>();
	}
	
	public void loadPetsData()
	{
		Connection con = null;
		
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement =
					con.prepareStatement("SELECT npc_template_id, level, max_exp, max_hp, max_mp, physical_attack, physical_defense, magic_attack, magic_defense, accuracy, evasion, critical_rate, run_speed, attack_speed, casting_speed, max_feed, feed_battle, feed_normal, max_load, hp_regeneration, mp_regeneration, owner_exp_share FROM pet_stat");
			ResultSet rset = statement.executeQuery();
			
			int petId, petLevel;
			
			while (rset.next())
			{
				petId = rset.getInt("npc_template_id");
				petLevel = rset.getInt("level");
				
				//build the petdata for this level
				L2PetData petData = new L2PetData();
				petData.setPetID(petId);
				petData.setPetLevel(petLevel);
				petData.setPetMaxExp(rset.getLong("max_exp"));
				petData.setPetMaxHP(rset.getInt("max_hp"));
				petData.setPetMaxMP(rset.getInt("max_mp"));
				petData.setPetPAtk(rset.getInt("physical_attack"));
				petData.setPetPDef(rset.getInt("physical_defense"));
				petData.setPetMAtk(rset.getInt("magic_attack"));
				petData.setPetMDef(rset.getInt("magic_defense"));
				petData.setPetAccuracy(rset.getInt("accuracy"));
				petData.setPetEvasion(rset.getInt("evasion"));
				petData.setPetCritical(rset.getInt("critical_rate"));
				petData.setPetSpeed(rset.getInt("run_speed"));
				petData.setPetAtkSpeed(rset.getInt("attack_speed"));
				petData.setPetCastSpeed(rset.getInt("casting_speed"));
				petData.setPetMaxFeed(rset.getInt("max_feed"));
				petData.setPetFeedNormal(rset.getInt("feed_normal"));
				petData.setPetFeedBattle(rset.getInt("feed_battle"));
				petData.setPetMaxLoad(rset.getInt("max_load"));
				petData.setPetRegenHP(rset.getInt("hp_regeneration"));
				petData.setPetRegenMP(rset.getInt("mp_regeneration"));
				petData.setOwnerExpTaken(rset.getFloat("owner_exp_share"));
				
				// if its the first data for this petid, we initialize its level Map
				if (!petTable.containsKey(petId))
					petTable.put(petId, new LinkedHashMap<Integer, L2PetData>());
				
				petTable.get(petId).put(petLevel, petData);
			}
			
			rset.close();
			statement.close();
		}
		catch (Exception e)
		{
			_log.warn("Could not load pets stats: ", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
		
		_log.info("PetDataTable: loaded " + petTable.size() + " pets.");
	}
	
	public void addPetData(L2PetData petData)
	{
		Map<Integer, L2PetData> h = petTable.get(petData.getPetID());
		
		if (h == null)
		{
			Map<Integer, L2PetData> statTable = new LinkedHashMap<Integer, L2PetData>();
			statTable.put(petData.getPetLevel(), petData);
			petTable.put(petData.getPetID(), statTable);
			return;
		}
		
		h.put(petData.getPetLevel(), petData);
	}
	
	public void addPetData(L2PetData[] petLevelsList)
	{
		for (L2PetData finalElement : petLevelsList)
			addPetData(finalElement);
	}
	
	public L2PetData getPetData(int petID, int petLevel)
	{
		try
		{
			return petTable.get(petID).get(petLevel);
		}
		catch (NullPointerException npe)
		{
			return null;
		}
	}
	
	public static int getPetIdByItemId(int itemId)
	{
		for (L2Pet pet : L2Pet.values())
			if (pet.getControlItemId() == itemId)
				return pet.getNpcId();
		return 0;
	}
	
	public static int getItemIdByPetId(int npcId)
	{
		for (L2Pet pet : L2Pet.values())
			if (pet.getNpcId() == npcId)
				return pet.getControlItemId();
		return 0;
	}
	
	public static int[] getFoodItemId(int npcId)
	{
		for (L2Pet pet : L2Pet.values())
			if (pet.getNpcId() == npcId)
				return pet.getFoodIds();
		return EMPTY_INT;
	}
	
	public static boolean isPet(int npcId)
	{
		for (L2Pet pet : L2Pet.values())
			if (pet.getNpcId() == npcId)
				return true;
		
		return false;
	}
	
	public static boolean isPetFood(int npcId, int itemId)
	{
		for (L2Pet pet : L2Pet.values())
			if (pet.getNpcId() == npcId)
				for (int id : pet.getFoodIds())
					if (id == itemId)
						return true;
		
		return false;
	}
	
	public static boolean isPetFood(int itemId)
	{
		for (L2Pet pet : L2Pet.values())
			for (int id : pet.getFoodIds())
				if (id == itemId)
					return true;
		
		return false;
	}
	
	public static boolean isPetItem(int itemId)
	{
		for (L2Pet pet : L2Pet.values())
			if (pet.getControlItemId() == itemId)
				return true;
		
		return false;
	}
	
	public static boolean isMountable(int npcId)
	{
		for (L2Pet pet : L2Pet.values())
			if (pet.getNpcId() == npcId)
				return pet.isMountable();
		return false;
	}
	
	public static boolean isWolf(int npcId)
	{
		return (PET_WOLF_ID == npcId);
	}
	
	public static boolean isHatchling(int npcId)
	{
		return (HATCHLING_WIND_ID == npcId || HATCHLING_STAR_ID == npcId || HATCHLING_TWILIGHT_ID == npcId);
	}
	
	public static boolean isStrider(int npcId)
	{
		switch (npcId)
		{
			case STRIDER_WIND_ID:
			case STRIDER_STAR_ID:
			case STRIDER_TWILIGHT_ID:
			case RED_STRIDER_WIND_ID:
			case RED_STRIDER_STAR_ID:
			case RED_STRIDER_TWILIGHT_ID:
				return true;
		}
		return false;
	}
	
	public static boolean isWyvern(int npcId)
	{
		return (WYVERN_ID == npcId);
	}
	
	public static boolean isEvolvedWolf(int npcId)
	{
		switch (npcId)
		{
			case GREAT_WOLF_ID:
			case WGREAT_WOLF_ID:
			case BLACK_WOLF_ID:
			case FENRIR_WOLF_ID:
			case WFENRIR_WOLF_ID:
				return true;
		}
		return false;
	}
	
	public static boolean isBaby(int npcId)
	{
		return (BABY_BUFFALO_ID == npcId || BABY_KOOKABURRA_ID == npcId || BABY_COUGAR_ID == npcId);
	}
	
	public static boolean isImprovedBaby(int npcId)
	{
		return (IMPROVED_BABY_BUFFALO_ID == npcId || IMPROVED_BABY_KOOKABURRA_ID == npcId || IMPROVED_BABY_COUGAR_ID == npcId);
	}
	
	public static boolean isSinEater(int npcId)
	{
		return npcId == SIN_EATER_ID;
	}
	
	public static boolean isTransformationPet(int npcId)
	{
		return (npcId == PURPLE_HORSE_ID || npcId == TAWNY_MANED_LION_ID || npcId == STEAM_SLEDGE);
	}
	
	/**
	 * This class describes basic pet info
	 * NPC template id, control item id, food item id and can be pet mounted
	 */
	private static enum L2Pet
	{
		WOLF(PET_WOLF_ID, 2375, new int[] { 2515 }, false),
		
		HATCHLING_WIND(HATCHLING_WIND_ID, 3500, new int[] { 4038 }, false),
		HATCHLING_STAR(HATCHLING_STAR_ID, 3501, new int[] { 4038 }, false),
		HATCHLING_TWILIGHT(HATCHLING_TWILIGHT_ID, 3502, new int[] { 4038 }, false),
		
		STRIDER_WIND(STRIDER_WIND_ID, 4422, new int[] { 5168, 5169 }, true),
		STRIDER_STAR(STRIDER_STAR_ID, 4423, new int[] { 5168, 5169 }, true),
		STRIDER_TWILIGHT(STRIDER_TWILIGHT_ID, 4424, new int[] { 5168, 5169 }, true),
		
		RED_STRIDER_WIND(RED_STRIDER_WIND_ID, 10308, new int[] { 5168, 5169 }, true),
		RED_STRIDER_STAR(RED_STRIDER_STAR_ID, 10309, new int[] { 5168, 5169 }, true),
		RED_STRIDER_TWILIGHT(RED_STRIDER_TWILIGHT_ID, 10310, new int[] { 5168, 5169 }, true),
		
		WYVERN(WYVERN_ID, 5249, new int[] { 6316 }, true),
		
		GREAT_WOLF(GREAT_WOLF_ID, 9882, new int[] { 9668 }, false),
		WGREAT_WOLF(WGREAT_WOLF_ID, 10307, new int[] { 9668 }, true),
		BLACK_WOLF(BLACK_WOLF_ID, 10163, new int[] { 9668 }, false),
		FENRIR_WOLF(FENRIR_WOLF_ID, 10426, new int[] { 9668 }, true),
		WFENRIR_WOLF(WFENRIR_WOLF_ID, 10611, new int[] { 9668 }, true),
		
		BABY_BUFFALO(BABY_BUFFALO_ID, 6648, new int[] { 7582 }, false),
		BABY_KOOKABURRA(BABY_KOOKABURRA_ID, 6649, new int[] { 7582 }, false),
		BABY_COUGAR(BABY_COUGAR_ID, 6650, new int[] { 7582 }, false),
		
		IMPROVED_BABY_BUFFALO(IMPROVED_BABY_BUFFALO_ID, 10311, new int[] { 10425 }, false),
		IMPROVED_BABY_KOOKABURRA(IMPROVED_BABY_KOOKABURRA_ID, 10312, new int[] { 10425 }, false),
		IMPROVED_BABY_COUGAR(IMPROVED_BABY_COUGAR_ID, 10313, new int[] { 10425 }, false),
		
		SIN_EATER(SIN_EATER_ID, 4425, new int[] { 2515 }, false);
		
		private final int _npcId;
		private final int _controlItemId;
		private final int[] _foodIds;
		private final boolean _isMountabe;
		
		private L2Pet(int npcId, int controlItemId, int[] foodIds, boolean isMountabe)
		{
			_npcId = npcId;
			_controlItemId = controlItemId;
			_foodIds = foodIds;
			_isMountabe = isMountabe;
		}
		
		public int getNpcId()
		{
			return _npcId;
		}
		
		public int getControlItemId()
		{
			return _controlItemId;
		}
		
		public int[] getFoodIds()
		{
			return _foodIds;
		}
		
		public boolean isMountable()
		{
			return _isMountabe;
		}
	}
	
	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder
	{
		protected static final PetDataTable _instance = new PetDataTable();
	}
}
