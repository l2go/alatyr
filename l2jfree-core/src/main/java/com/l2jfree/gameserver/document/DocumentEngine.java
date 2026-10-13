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
package com.l2jfree.gameserver.document;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.Config;
import com.l2jfree.gameserver.model.items.Item;
import com.l2jfree.gameserver.model.items.templates.L2EtcItem;
import com.l2jfree.gameserver.model.items.templates.L2EtcItemType;
import com.l2jfree.gameserver.model.items.templates.L2Item;
import com.l2jfree.gameserver.model.skills.L2Skill;

/**
 * @author mkizub
 */
public final class DocumentEngine
{
	private static final Logger _log = LoggerFactory.getLogger(DocumentEngine.class);
	
	private DocumentEngine()
	{
	}
	
	private static final FileFilter XML_FILTER = new FileFilter() {
		@Override
		public boolean accept(File f)
		{
			return f.getName().endsWith(".xml");
		}
	};
	
	private static File[] listFiles(String dirname, String part)
	{
		File dir = new File(Config.DATAPACK_ROOT, dirname);
		File[] files = dir.isDirectory() ? dir.listFiles(XML_FILTER) : null;
		if (files == null || files.length == 0)
		{
			_log.info("World has no " + part);
			return new File[0];
		}
		return files;
	}
	
	public static List<L2Skill> loadSkills()
	{
		final List<L2Skill> list = new ArrayList<L2Skill>();
		
		for (File file : listFiles("data/stats/skills", "skills"))
		{
			DocumentSkill doc = new DocumentSkill(file);
			doc.parse();
			list.addAll(doc.getSkills());
		}
		
		return list;
	}
	
	public static List<L2Item> loadArmors(Map<Integer, Item> armorData)
	{
		final List<L2Item> list = loadData(armorData, listFiles("data/stats/armor", "armor"));
		
		Set<Integer> xmlItem = new HashSet<Integer>();
		
		for (L2Item item : list)
			xmlItem.add(item.getItemId());
		
		for (Item item : armorData.values())
			if (!xmlItem.contains(item.id))
				_log.warn("SkillsEngine: Missing XML side for L2Armor - id: " + item.id);
		
		return list;
	}
	
	public static List<L2Item> loadWeapons(Map<Integer, Item> weaponData)
	{
		final List<L2Item> list = loadData(weaponData, listFiles("data/stats/weapon", "weapons"));
		
		Set<Integer> xmlItem = new HashSet<Integer>();
		
		for (L2Item item : list)
			xmlItem.add(item.getItemId());
		
		for (Item item : weaponData.values())
			if (!xmlItem.contains(item.id))
				_log.warn("SkillsEngine: Missing XML side for L2Weapon - id: " + item.id);
		
		return list;
	}
	
	public static List<L2Item> loadItems(Map<Integer, Item> itemData)
	{
		final List<L2Item> list = loadData(itemData, listFiles("data/stats/etcitem", "etc items"));
		
		Set<Integer> xmlItem = new HashSet<Integer>();
		
		for (L2Item item : list)
			xmlItem.add(item.getItemId());
		
		for (Item item : itemData.values())
		{
			if (!xmlItem.contains(item.id))
			{
				try
				{
					list.add(new L2EtcItem((L2EtcItemType)item.type, item.set));
				}
				catch (RuntimeException e)
				{
					_log.warn("Error while parsing item id " + item.id, e);
				}
			}
		}
		
		return list;
	}
	
	private static List<L2Item> loadData(Map<Integer, Item> itemData, File[] files)
	{
		final List<L2Item> list = new ArrayList<L2Item>();
		
		for (File f : files)
		{
			DocumentItem document = new DocumentItem(itemData, f);
			document.parse();
			list.addAll(document.getItemList());
		}
		
		return list;
	}
}
