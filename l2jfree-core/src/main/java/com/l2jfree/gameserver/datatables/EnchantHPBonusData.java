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

import java.io.File;
import java.util.StringTokenizer;

import javax.xml.parsers.DocumentBuilderFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import com.l2jfree.Config;
import com.l2jfree.gameserver.util.WorldFiles;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.items.templates.L2Armor;
import com.l2jfree.gameserver.model.items.templates.L2Equip;
import com.l2jfree.gameserver.model.items.templates.L2Item;
import com.l2jfree.gameserver.model.items.templates.L2Weapon;
import com.l2jfree.gameserver.model.skills.Stats;
import com.l2jfree.gameserver.model.skills.funcs.FuncTemplate;
import com.l2jfree.util.LookupTable;

/**
 * @author MrPoke
 */
public final class EnchantHPBonusData
{
	private static final Logger _log = LoggerFactory.getLogger(EnchantHPBonusData.class);
	
	public static EnchantHPBonusData getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private final LookupTable<Integer[]> _singleArmorHPBonus = new LookupTable<Integer[]>();
	private final LookupTable<Integer[]> _fullArmorHPBonus = new LookupTable<Integer[]>();
	
	private EnchantHPBonusData()
	{
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setValidating(true);
		factory.setIgnoringComments(true);
		File file = new File(Config.DATAPACK_ROOT, "data/enchantHPBonus.xml");
		if (WorldFiles.skip(_log, "enchant HP bonus", file))
			return;
		Document doc = null;
		
		try
		{
			doc = factory.newDocumentBuilder().parse(file);
		}
		catch (Exception e)
		{
			_log.warn("", e);
			return;
		}
		
		for (Node n = doc.getFirstChild(); n != null; n = n.getNextSibling())
		{
			if ("list".equalsIgnoreCase(n.getNodeName()))
			{
				for (Node d = n.getFirstChild(); d != null; d = d.getNextSibling())
				{
					if ("enchantHP".equalsIgnoreCase(d.getNodeName()))
					{
						NamedNodeMap attrs = d.getAttributes();
						Node att;
						boolean fullArmor;
						
						att = attrs.getNamedItem("grade");
						if (att == null)
						{
							_log.warn("[EnchantHPBonusData] Missing grade, skipping");
							continue;
						}
						
						int grade = Integer.parseInt(att.getNodeValue());
						
						att = attrs.getNamedItem("fullArmor");
						if (att == null)
						{
							_log.warn("[EnchantHPBonusData] Missing fullArmor, skipping");
							continue;
						}
						fullArmor = Boolean.valueOf(att.getNodeValue());
						
						att = attrs.getNamedItem("values");
						if (att == null)
						{
							_log.warn("[EnchantHPBonusData] Missing bonus id: " + grade + ", skipping");
							continue;
						}
						StringTokenizer st = new StringTokenizer(att.getNodeValue(), ",");
						int tokenCount = st.countTokens();
						Integer[] bonus = new Integer[tokenCount];
						for (int i = 0; i < tokenCount; i++)
						{
							Integer value = Integer.decode(st.nextToken().trim());
							if (value == null)
							{
								_log.warn("[EnchantHPBonusData] Bad Hp value!! grade: " + grade + " FullArmor? "
										+ fullArmor + " token: " + i);
								value = 0;
							}
							bonus[i] = value;
						}
						if (fullArmor)
							_fullArmorHPBonus.set(grade, bonus);
						else
							_singleArmorHPBonus.set(grade, bonus);
					}
				}
			}
		}
		
		if (_fullArmorHPBonus.isEmpty() && _singleArmorHPBonus.isEmpty())
			return;
		
		int count = 0;
		
		for (L2Item item0 : ItemTable.getInstance().getAllTemplates())
		{
			if (!(item0 instanceof L2Equip))
				continue;
			
			final L2Equip item = (L2Equip)item0;
			
			if (item.getCrystalType() == L2Item.CRYSTAL_NONE)
				continue;
			
			boolean shouldAdd = false;
			
			// normally for armors
			if (item instanceof L2Armor)
			{
				switch (item.getBodyPart())
				{
					case L2Item.SLOT_CHEST:
					case L2Item.SLOT_FEET:
					case L2Item.SLOT_GLOVES:
					case L2Item.SLOT_HEAD:
					case L2Item.SLOT_LEGS:
					case L2Item.SLOT_BACK:
					case L2Item.SLOT_FULL_ARMOR:
					case L2Item.SLOT_UNDERWEAR:
					case L2Item.SLOT_L_HAND:
						shouldAdd = true;
						break;
				}
			}
			// shields in the weapons table
			else if (item instanceof L2Weapon)
			{
				switch (item.getBodyPart())
				{
					case L2Item.SLOT_L_HAND:
						shouldAdd = true;
						break;
				}
			}
			
			if (shouldAdd)
			{
				count++;
				item.attach(new FuncTemplate(null, "EnchantHp", Stats.MAX_HP, 0x60, 0));
			}
		}
		
		_log.info("Enchant HP Bonus registered for " + count + " items!");
	}
	
	public final int getHPBonus(L2ItemInstance item)
	{
		final Integer[] values;
		
		if (item.getItem().getBodyPart() == L2Item.SLOT_FULL_ARMOR)
			values = _fullArmorHPBonus.get(item.getItem().getItemGradeSPlus());
		else
			values = _singleArmorHPBonus.get(item.getItem().getItemGradeSPlus());
		
		if (values == null || values.length == 0)
			return 0;
		
		return values[Math.min(item.getEnchantLevel(), values.length) - 1];
	}
	
	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder
	{
		protected static final EnchantHPBonusData _instance = new EnchantHPBonusData();
	}
}
