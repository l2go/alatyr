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
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import com.l2jfree.Config;
import com.l2jfree.gameserver.util.WorldFiles;
import com.l2jfree.gameserver.model.L2SummonItem;

/**
 * @author FBIagent
 * @reworked by Michiru
 */
public class SummonItemsData
{
	private static final Logger _log = LoggerFactory.getLogger(SummonItemsData.class);
	
	private final Map<Integer, L2SummonItem> _summonitems;
	
	private int[] _summonItemIds;
	
	public static SummonItemsData getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private SummonItemsData()
	{
		_summonitems = new LinkedHashMap<Integer, L2SummonItem>();
		Document doc = null;
		File file = new File(Config.DATAPACK_ROOT, "data/summon_items.xml");
		if (WorldFiles.skip(_log, "summon items", file))
			return;
		
		try
		{
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setValidating(true);
			factory.setIgnoringComments(true);
			doc = factory.newDocumentBuilder().parse(file);
			
			int itemID = 0, npcID = 0;
			byte summonType = 0;
			Node a;
			for (Node n = doc.getFirstChild(); n != null; n = n.getNextSibling())
			{
				if ("list".equalsIgnoreCase(n.getNodeName()))
				{
					for (Node d = n.getFirstChild(); d != null; d = d.getNextSibling())
					{
						if ("item".equalsIgnoreCase(d.getNodeName()))
						{
							a = d.getAttributes().getNamedItem("id");
							if (a == null)
								throw new Exception("Error in summon item defenition!");
							itemID = Integer.parseInt(a.getNodeValue());
							
							for (Node e = d.getFirstChild(); e != null; e = e.getNextSibling())
							{
								if ("npcId".equalsIgnoreCase(e.getNodeName()))
								{
									a = e.getAttributes().getNamedItem("val");
									if (a == null)
										throw new Exception("Not defined npc id for summon item id=" + itemID + "!");
									npcID = Integer.parseInt(a.getNodeValue());
								}
								else if ("summonType".equalsIgnoreCase(e.getNodeName()))
								{
									a = e.getAttributes().getNamedItem("val");
									if (a == null)
										throw new Exception("Not defined summon type for summon item id=" + itemID
												+ "!");
									summonType = Byte.parseByte(a.getNodeValue());
								}
							}
							L2SummonItem summonitem = new L2SummonItem(itemID, npcID, summonType);
							_summonitems.put(itemID, summonitem);
						}
					}
				}
			}
			_summonItemIds = new int[_summonitems.size()];
			int i = 0;
			for (int itemId : _summonitems.keySet())
				_summonItemIds[i++] = itemId;
		}
		catch (IOException e)
		{
			_log.warn("SummonItemsData: Can not find " + file.getAbsolutePath() + " !", e);
		}
		catch (Exception e)
		{
			_log.warn("SummonItemsData: Error while parsing " + file.getAbsolutePath() + " !", e);
		}
		_log.info("SummonItemsData: Loaded " + _summonitems.size() + " Summon Items from " + file.getName());
	}
	
	public L2SummonItem getSummonItem(int itemId)
	{
		return _summonitems.get(itemId);
	}
	
	public int[] itemIDs()
	{
		return _summonItemIds;
	}
	
	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder
	{
		protected static final SummonItemsData _instance = new SummonItemsData();
	}
}
