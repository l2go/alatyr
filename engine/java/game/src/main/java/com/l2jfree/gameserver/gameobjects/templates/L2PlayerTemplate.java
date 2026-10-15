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
package com.l2jfree.gameserver.gameobjects.templates;

import java.util.ArrayList;
import java.util.List;

import com.l2jfree.Config;
import com.l2jfree.gameserver.gameobjects.base.ClassId;
import com.l2jfree.gameserver.gameobjects.base.Race;
import com.l2jfree.gameserver.model.Location;
import com.l2jfree.gameserver.templates.StatsSet;
import com.l2jfree.tools.random.Rnd;

/**
 * Base template for all type of playable characters
 * Override {@link com.l2jfree.gameserver.templates.L2CreatureTemplate} to add some properties
 * specific to pc.
 *
 * <br/>
 * <br/>
 * <font color="red">
 * <b>Property don't change in the time, this is just a template, not the currents status
 * of characters !</b>
 * </font>
 */
public class L2PlayerTemplate extends L2CreatureTemplate
{
	private static final Location[][] START_POINTS = {
			// Human Fighter (0)
			new Location[] { new Location(-72662, 258431, -3104), new Location(-72963, 258034, -3104),
					new Location(-71436, 256718, -3104), new Location(-71081, 257082, -3104),
					new Location(-72682, 257559, -3104), new Location(-71958, 256935, -3104),
					new Location(-72352, 257936, -3104), new Location(-71616, 257328, -3104),
					new Location(-72320, 258816, -3104), new Location(-72432, 258656, -3104),
					new Location(-72256, 258672, -3104), new Location(-71936, 259232, -3104),
					new Location(-71792, 259408, -3104), new Location(-71760, 259264, -3104),
					new Location(-72064, 257344, -3104), new Location(-72304, 257552, -3104),
					new Location(-70880, 257360, -3104), new Location(-70736, 257520, -3104),
					new Location(-70896, 257536, -3104), new Location(-70400, 257936, -3104),
					new Location(-70432, 258112, -3104), new Location(-70240, 258112, -3104) },
			// Elf (1)
			new Location[] { new Location(43648, 40352, -3440), new Location(43424, 40224, -3440),
					new Location(43584, 40208, -3440), new Location(43360, 40368, -3440),
					new Location(43392, 40560, -3440), new Location(43584, 40560, -3440),
					new Location(43504, 40384, -3440), new Location(48720, 40000, -3440),
					new Location(48912, 39984, -3440), new Location(48832, 40144, -3440),
					new Location(48656, 40144, -3440), new Location(49011, 40128, -3440),
					new Location(48752, 40304, -3440), new Location(48928, 40288, -3440) },
			// Dark Elf (2)
			new Location[] { new Location(26716, 11680, -4224), new Location(26794, 11561, -4224),
					new Location(26672, 11440, -4224), new Location(27008, 11568, -4224),
					new Location(27104, 11408, -4224), new Location(26672, 10656, -4224),
					new Location(26768, 10512, -4224), new Location(26624, 10368, -4224),
					new Location(26912, 10400, -4224), new Location(26960, 10496, -4224) },
			// Orc (3)
			new Location[] { new Location(-56936, -112448, -679), new Location(-57281, -112427, -679),
					new Location(-56928, -112880, -679), new Location(-57248, -112864, -679),
					new Location(-58192, -113408, -679), new Location(-58256, -113856, -679),
					new Location(-57824, -113408, -679), new Location(-57824, -113744, -679),
					new Location(-57280, -114688, -679), new Location(-56880, -114752, -679),
					new Location(-57248, -114320, -679), new Location(-56896, -114320, -679),
					new Location(-56192, -113792, -679), new Location(-56448, -113792, -679),
					new Location(-56096, -113424, -679), new Location(-56432, -113456, -679) },
			// Dwarf (4)
			new Location[] { new Location(107520, -175808, -400), new Location(107824, -175776, -400),
					new Location(108336, -175536, -400), new Location(108592, -175232, -400),
					new Location(107024, -175440, -400), new Location(106880, -175056, -400),
					new Location(106848, -174704, -400), new Location(108256, -175152, -400),
					new Location(107632, -175376, -400), new Location(107200, -174800, -400) },
			// Kamael (5)
			new Location[] { new Location(-125464, 37776, 1176), new Location(-125517, 38267, 1176),
					new Location(-125533, 38114, 1142) } };
	private static final Location[] START_POINTS_HM = { new Location(-88832, 248256, -3570),
			new Location(-88944, 248208, -3570), new Location(-89040, 248128, -3570),
			new Location(-89200, 248000, -3570), new Location(-91008, 249248, -3570),
			new Location(-90848, 249360, -3570), new Location(-90912, 249312, -3570),
			new Location(-89696, 247664, -3570), new Location(-89755, 247606, -3570),
			new Location(-89840, 247536, -3570), new Location(-90378, 249698, -3570),
			new Location(-90256, 249792, -3570), new Location(-89968, 249936, -3570),
			new Location(-90096, 249856, -3570) };
	
	/** The Class object of the L2Player */
	private ClassId classId;
	
	private Race race;
	private String className;
	
	/* Not a single point
	private int				spawnX;
	private int				spawnY;
	private int				spawnZ;
	*/
	
	private int classBaseLevel;
	private float lvlHpAdd;
	private float lvlHpMod;
	private float lvlCpAdd;
	private float lvlCpMod;
	private float lvlMpAdd;
	private float lvlMpMod;
	
	// for female chars
	private final double fCollisionRadius;
	private final double fCollisionHeight;
	
	private final List<PlayerTemplateItem> _items = new ArrayList<PlayerTemplateItem>();
	
	public L2PlayerTemplate(StatsSet set)
	{
		super(set);
		classId = ClassId.values()[set.getInteger("classId")];
		race = Race.values()[set.getInteger("raceId")];
		className = set.getString("className");
		
		/*
		spawnX = set.getInteger("spawnX");
		spawnY = set.getInteger("spawnY");
		spawnZ = set.getInteger("spawnZ");
		*/
		
		classBaseLevel = set.getInteger("classBaseLevel");
		lvlHpAdd = set.getFloat("lvlHpAdd");
		lvlHpMod = set.getFloat("lvlHpMod");
		lvlCpAdd = set.getFloat("lvlCpAdd");
		lvlCpMod = set.getFloat("lvlCpMod");
		lvlMpAdd = set.getFloat("lvlMpAdd");
		lvlMpMod = set.getFloat("lvlMpMod");
		
		// Geometry
		fCollisionRadius = set.getDouble("fcollision_radius");
		fCollisionHeight = set.getDouble("fcollision_height");
	}
	
	/**
	 * Adds starter equipment
	 * @param i
	 */
	public void addItem(int itemId, int amount, boolean equipped)
	{
		_items.add(new PlayerTemplateItem(itemId, amount, equipped));
	}
	
	/**
	 *
	 * @return itemIds of all the starter equipment
	 */
	public List<PlayerTemplateItem> getItems()
	{
		return _items;
	}
	
	/**
	 * @return the classBaseLevel
	 */
	public int getClassBaseLevel()
	{
		return classBaseLevel;
	}
	
	/**
	 * @param classBaseLevel the classBaseLevel to set
	 */
	public void setClassBaseLevel(int _classBaseLevel)
	{
		classBaseLevel = _classBaseLevel;
	}
	
	/**
	 * @return the classId
	 */
	public ClassId getClassId()
	{
		return classId;
	}
	
	/**
	 * @param classId the classId to set
	 */
	public void setClassId(ClassId _classId)
	{
		classId = _classId;
	}
	
	/**
	 * @return the className
	 */
	public String getClassName()
	{
		return className;
	}
	
	/**
	 * @param className the className to set
	 */
	public void setClassName(String _className)
	{
		className = _className;
	}
	
	/**
	 * @return the lvlCpAdd
	 */
	public float getLvlCpAdd()
	{
		return lvlCpAdd;
	}
	
	/**
	 * @param lvlCpAdd the lvlCpAdd to set
	 */
	public void setLvlCpAdd(float _lvlCpAdd)
	{
		lvlCpAdd = _lvlCpAdd;
	}
	
	/**
	 * @return the lvlCpMod
	 */
	public float getLvlCpMod()
	{
		return lvlCpMod;
	}
	
	/**
	 * @param lvlCpMod the lvlCpMod to set
	 */
	public void setLvlCpMod(float _lvlCpMod)
	{
		lvlCpMod = _lvlCpMod;
	}
	
	/**
	 * @return the lvlHpAdd
	 */
	public float getLvlHpAdd()
	{
		return lvlHpAdd;
	}
	
	/**
	 * @param lvlHpAdd the lvlHpAdd to set
	 */
	public void setLvlHpAdd(float _lvlHpAdd)
	{
		lvlHpAdd = _lvlHpAdd;
	}
	
	/**
	 * @return the lvlHpMod
	 */
	public float getLvlHpMod()
	{
		return lvlHpMod;
	}
	
	/**
	 * @param lvlHpMod the lvlHpMod to set
	 */
	public void setLvlHpMod(float _lvlHpMod)
	{
		lvlHpMod = _lvlHpMod;
	}
	
	/**
	 * @return the lvlMpAdd
	 */
	public float getLvlMpAdd()
	{
		return lvlMpAdd;
	}
	
	/**
	 * @param lvlMpAdd the lvlMpAdd to set
	 */
	public void setLvlMpAdd(float _lvlMpAdd)
	{
		lvlMpAdd = _lvlMpAdd;
	}
	
	/**
	 * @return the lvlMpMod
	 */
	public float getLvlMpMod()
	{
		return lvlMpMod;
	}
	
	/**
	 * @param lvlMpMod the lvlMpMod to set
	 */
	public void setLvlMpMod(float _lvlMpMod)
	{
		lvlMpMod = _lvlMpMod;
	}
	
	/**
	 * @return the race
	 */
	public Race getRace()
	{
		return race;
	}
	
	/**
	 * @param race the race to set
	 */
	public void setRace(Race _race)
	{
		race = _race;
	}
	
	/**
	 * @return the collisionHeight
	 */
	public double getdCollisionHeight()
	{
		return collisionHeight;
	}
	
	/**
	 * @return the fCollisionHeight
	 */
	public double getFCollisionHeight()
	{
		return fCollisionHeight;
	}
	
	/**
	 * @return the collisionRadius
	 */
	public double getdCollisionRadius()
	{
		return collisionRadius;
	}
	
	/**
	 * @return the fCollisionRadius
	 */
	public double getFCollisionRadius()
	{
		return fCollisionRadius;
	}
	
	/**
	 * @return the spawnX
	 */
	/*
	public int getSpawnX()
	{
		return spawnX;
	}

	/**
	 * @param spawnX the spawnX to set
	 */
	/*
	public void setSpawnX(int _spawnX)
	{
		spawnX = _spawnX;
	}

	/**
	 * @return the spawnY
	 */
	/*
	public int getSpawnY()
	{
		return spawnY;
	}

	/**
	 * @param spawnY the spawnY to set
	 */
	/*
	public void setSpawnY(int _spawnY)
	{
		spawnY = _spawnY;
	}

	/**
	 * @return the spawnZ
	 */
	/*
	public int getSpawnZ()
	{
		return spawnZ;
	}

	/**
	 * @param spawnZ the spawnZ to set
	 */
	/*
	public void setSpawnZ(int _spawnZ)
	{
		spawnZ = _spawnZ;
	}
	*/
	
	public Location getStartingPosition()
	{
		if (getRace().equals(Race.Human) && getClassId().isMage())
			return START_POINTS_HM[Rnd.get(START_POINTS_HM.length)];
		Location[] pos = START_POINTS[getRace().ordinal()];
		return pos[Rnd.get(pos.length)];
	}
	
	public int getBaseFallSafeHeight(boolean female)
	{
		if (classId.getRace() == Race.Darkelf || classId.getRace() == Race.Elf)
		{
			return (classId.isMage()) ? ((female) ? 330 : 300) : ((female) ? 380 : 350);
		}
		
		else if (classId.getRace() == Race.Dwarf)
		{
			return ((female) ? 200 : 180);
		}
		
		else if (classId.getRace() == Race.Human)
		{
			return (classId.isMage()) ? ((female) ? 220 : 200) : ((female) ? 270 : 250);
		}
		
		else if (classId.getRace() == Race.Orc)
		{
			return (classId.isMage()) ? ((female) ? 280 : 250) : ((female) ? 220 : 200);
		}
		
		return Config.ALT_MINIMUM_FALL_HEIGHT;
		
		/**
		  	Dark Elf Fighter F 380
			Dark Elf Fighter M 350
			Dark Elf Mystic F 330
			Dark Elf Mystic M 300
			Dwarf Fighter F 200
			Dwarf Fighter M 180
			Elf Fighter F 380
			Elf Fighter M 350
			Elf Mystic F 330
			Elf Mystic M 300
			Human Fighter F 270
			Human Fighter M 250
			Human Mystic F 220
			Human Mystic M 200
			Orc Fighter F 220
			Orc Fighter M 200
			Orc Mystic F 280
			Orc Mystic M 250
		 */
	}
	
	public static final class PlayerTemplateItem
	{
		private final int _itemId;
		private final int _amount;
		private final boolean _equipped;
		
		/**
		 * @param amount
		 * @param itemId
		 */
		public PlayerTemplateItem(int itemId, int amount, boolean equipped)
		{
			_itemId = itemId;
			_amount = amount;
			_equipped = equipped;
		}
		
		/**
		 * @return Returns the itemId.
		 */
		public int getItemId()
		{
			return _itemId;
		}
		
		/**
		 * @return Returns the amount.
		 */
		public int getAmount()
		{
			return _amount;
		}
		
		/**
		 * @return Returns the if the item should be equipped after char creation.
		 */
		public boolean isEquipped()
		{
			return _equipped;
		}
	}
}
