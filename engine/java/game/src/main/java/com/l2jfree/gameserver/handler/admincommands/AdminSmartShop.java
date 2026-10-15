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
package com.l2jfree.gameserver.handler.admincommands;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.datatables.ItemTable;
import com.l2jfree.gameserver.datatables.NpcTable;
import com.l2jfree.gameserver.datatables.SkillTable;
import com.l2jfree.gameserver.datatables.SkillTreeTable;
import com.l2jfree.gameserver.datatables.SpawnTable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.base.ClassId;
import com.l2jfree.gameserver.handler.IAdminCommandHandler;
import com.l2jfree.gameserver.instancemanager.QuestManager;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.items.templates.L2Armor;
import com.l2jfree.gameserver.model.items.templates.L2Equip;
import com.l2jfree.gameserver.model.items.templates.L2EtcItem;
import com.l2jfree.gameserver.model.items.templates.L2EtcItemType;
import com.l2jfree.gameserver.model.items.templates.L2Item;
import com.l2jfree.gameserver.model.items.templates.L2Weapon;
import com.l2jfree.gameserver.model.quest.Quest;
import com.l2jfree.gameserver.model.skills.Env;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.skills.funcs.Func;
import com.l2jfree.gameserver.model.skills.learn.L2SkillLearn;
import com.l2jfree.gameserver.model.world.spawn.L2Spawn;
import com.l2jfree.gameserver.network.Disconnection;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.NpcHtmlMessage;
import com.l2jfree.gameserver.network.packets.server.StatusUpdate;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;

/**
 * Smart GM Shop that can give you all available items
 * @author Darki699
 * @version $Revision: 1.2.2.1.2.4 $ $Date: 2005/04/11 10:05:56 $
 */
public class AdminSmartShop implements IAdminCommandHandler
{
	private final static Logger _log = LoggerFactory.getLogger(AdminSmartShop.class);
	
	private static final String[] ADMIN_COMMANDS = { "admin_smartshop" };
	
	private static List<Integer> smartList;
	private static List<Boolean> questList;
	private static List<Integer> gradeList;
	private static List<L2Skill> skillsWithItems;
	private static int itemsStart = 0, itemsEnd = -1, weapnStart = -1, weapnEnd = -1, armorStart = -1, armorEnd = -1;
	
	// Any number will do as long as it can be divided by 3, for viewing sake =P
	private static int _itemsPerView = 9;
	
	@Override
	public boolean useAdminCommand(String command, L2Player activeChar)
	{
		if (!command.startsWith("admin_smartshop"))
			return false;
		
		//runs once to initialize the static queues
		init();
		
		if (command.equals("admin_smartshop"))
		{
			showSmartShop(activeChar, "", "", "Welcome to Smart Shop");
		}
		
		String[] param = command.split(" &&");
		
		if (param.length > 1 && param[1] != null)
		{
			String newMark = "";
			if (param.length > 4 && param[4] != null)
			{
				newMark += param[2]; /*opCommand*/
				newMark +=
						parseParam(/*opCommand*/param[2], /*user 1st values*/param[3],/*user 2nd values*/param[4],
								activeChar);
			}
			
			else if (param.length > 3 && param[3] != null)
			{
				newMark += param[2];
				newMark += parseParam(/*opCommand*/param[2], /*user values*/param[3], activeChar);
			}
			
			else if (param.length > 2 && param[2] != null)
			{
				newMark += param[2];
			}
			
			param[1] = calculateMarks(param[1], newMark);
		}
		
		//for (String s : param)
		//System.out.println(s);
		
		if (param.length > 5)
		{
			activeChar.sendMessage("Wrong Usage: //smartshop_help");
			activeChar.sendMessage("Wrong Usage: //smartshop_?");
			return false;
		}
		
		String mark = (param.length > 1) ? param[1] : "", title = getTitle(mark), message = getMessage(mark);
		
		mark = parseItemMarks(mark, param);
		
		showSmartShop(activeChar, mark, message, title);
		
		return true;
		
	}
	
	@Override
	public String[] getAdminCommandList()
	{
		return ADMIN_COMMANDS;
	}
	
	/**
	 * Init should be called only once, and loads the item data
	 * to the queues.
	 */
	private void init()
	{
		if (smartList == null)
		{
			
			smartList = new ArrayList<Integer>();
			questList = new ArrayList<Boolean>();
			gradeList = new ArrayList<Integer>();
		}
		
		if (smartList.isEmpty())
		{
			itemsStart = 0;
			itemsEnd = -1;
			weapnStart = -1;
			weapnEnd = -1;
			armorStart = -1;
			armorEnd = -1;
			
			smartList.clear();
			questList.clear();
			gradeList.clear();
			
			getAllItems();
		}
		
	}
	
	/**
	 * Controls the smart shop's AI
	 * @param activeChar - The L2Player GM using the shop
	 */
	private void showSmartShop(L2Player activeChar, String marks, String message, String title)
	{
		
		if (marks.contains("_get="))
		{
			showItemScreen(marks, activeChar);
			return;
		}
		
		showScreen(getInitialWindow(marks), activeChar, message, title);
		
	}
	
	private void showItemScreen(String marks, L2Player actor)
	{
		
		int itemId = valueOfMark("_get=", marks);
		String itemName = ItemTable.getInstance().getTemplate(itemId).getName();
		
		NpcHtmlMessage adminReply = new NpcHtmlMessage(5);
		StringBuilder replyMSG = new StringBuilder("<html><title> " + itemName + "(Id " + itemId + ") </title><body>");
		
		String text =
				"<center><font color=\"LEVEL\">[Smart Shop by Darki699]</font></center><br><br>"
						+ "<center><edit var=\"input1\" width=\"125\"></center><br1>" + "<table><tr>"
						+ "<td width=\"100\"><button value=\"Create\" action=\"bypass -h admin_smartshop &&_buyItem="
						+ itemId
						+ " _get="
						+ itemId
						+ " &&_get="
						+ itemId
						+ " && $input1 && $input2\" width=90 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>"
						+ "<td width=\"100\"><button value=\"Edit\" action=\"bypass -h admin_smartshop &&_get="
						+ itemId
						+ " &&_editItem="
						+ itemId
						+ " && $input1 && $input2\" width=90 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>"
						+ "<td width=\"100\"><button value=\"Detail\" action=\"bypass -h admin_smartshop &&_get="
						+ itemId
						+ " &&_detailItem="
						+ itemId
						+ " && $input1 && $input2\" width=90 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>"
						+ "</tr><tr>"
						+ "<td width=\"100\"><button value=\"Quests\" action=\"bypass -h admin_smartshop &&_get="
						+ itemId
						+ " &&_getQuestItem="
						+ itemId
						+ " && $input1 && $input2\" width=90 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>"
						+ "<td width=\"100\"><button value=\"Skills\" action=\"bypass -h admin_smartshop &&_get="
						+ itemId
						+ " &&_getSkillItem="
						+ itemId
						+ " && $input1 && $input2\" width=90 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>"
						+ "<td width=\"100\"><button value=\"Buylist\" action=\"bypass -h admin_smartshop &&_get="
						+ itemId
						+ " &&_getBuyListItem="
						+ itemId
						+ " && $input1 && $input2\" width=90 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>"
						+ "</tr></table><br><center>"
						+ "<button value=\"-main-\" action=\"bypass -h admin_smartshop\" width=90 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></center><br>"
						+ itemText(marks, itemId, actor);
		replyMSG.append(text);
		
		replyMSG.append("</body></html>");
		adminReply.setHtml(replyMSG.toString());
		actor.sendPacket(adminReply);
		
	}
	
	/**
	 * Returns the input data of the requested item command.
	 * @param mark String of the cmd requested
	 * @return the String text ready to be outputed in html.
	 */
	private String itemText(String mark, int itemId, L2Player actor)
	{
		// "_getSkillItem=" , "_editItem=" , "_detailItem=" , "_getQuestItem=" , "_getBuyListItem="
		
		if (mark.contains("_getSkillItem="))
		{
			return getSkillItemText(itemId, actor);
		}
		
		else if (mark.contains("_editItem="))
		{
			return editItemText(itemId);
		}
		
		else if (mark.contains("_detailItem="))
		{
			return getDetailItemText(itemId);
		}
		
		else if (mark.contains("_getQuestItem="))
		{
			return questItemText(itemId);
		}
		
		else if (mark.contains("_getBuyListItem="))
		{
			return buylistItemText(itemId);
		}
		
		else if (mark.contains("_buyItem="))
		{
			String caution = "";
			try
			{
				caution =
						(ItemTable.getInstance().getTemplate(itemId).isStackable()) ? ""
								: "<center><font color=\"FF0000\">CAUTION!!!</font> Item is not stackable!</center>";
			}
			catch (Exception e)
			{
			}
			return "<br><center><font color=\"00FF00\">Create</font> - How many would you like?<br></center>" + caution;
		}
		return "<center>-- Choose an option from the menu --</center>";
	}
	
	/**
	 * Shows the screen to the gm using this shop.
	 * @param text - String html text
	 * @param activeChar - L2Player of the GM
	 * @param heading - String title of the shop
	 */
	private void showScreen(String text, L2Player activeChar, String message, String heading)
	{
		
		NpcHtmlMessage adminReply = new NpcHtmlMessage(5);
		StringBuilder replyMSG = new StringBuilder("<html><title> " + heading + " </title><body>");
		replyMSG.append(message + "<br1>");
		replyMSG.append(text);
		replyMSG.append("</body></html>");
		adminReply.setHtml(replyMSG.toString());
		activeChar.sendPacket(adminReply);
		
	}
	
	private String getInitialWindow(String marks)
	{
		
		String text =
				"<center><font color=\"LEVEL\">[Smart Shop by Darki699]</font></center><br><br>"
						+ "<table><tr><td width=\"125\"><font color =\"LEVEL\"><center>Value 1</center></font></td><td width=\"125\"><font color =\"LEVEL\"><center>Value 2</center></font></td></tr></table>"
						+ "<table><tr><td><edit var=\"input1\" width=\"125\"></td><td><edit var=\"input2\" width=\"125\"></td></tr></table>"
						+ "<table><tr>"
						+ "<td width=\"100\"><button value=\"Weapon\" action=\"bypass -h admin_smartshop &&"
						+ marks
						+ " &&_weapn && $input1 && $input2\" width=90 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>"
						+ "<td width=\"100\"><button value=\"Items\"  action=\"bypass -h admin_smartshop &&"
						+ marks
						+ " &&_items && $input1 && $input2\" width=90 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>"
						+ "<td width=\"100\"><button value=\"Armor\"  action=\"bypass -h admin_smartshop &&"
						+ marks
						+ " &&_armor && $input1 && $input2\" width=90 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>"
						+ "</tr><tr>"
						+ "<td width=\"100\"><button value=\"Quest\"  action=\"bypass -h admin_smartshop &&"
						+ marks
						+ " &&_quest && $input1 && $input2\" width=90 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>"
						+ "<td width=\"100\"><button value=\"View#\"  action=\"bypass -h admin_smartshop &&"
						+ marks
						+ " &&_view && $input1 && $input2\" width=90 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>"
						+ "<td width=\"100\"><button value=\"Help\"   action=\"bypass -h admin_smartshop &&"
						+ marks
						+ " &&_help && $input1 && $input2\" width=90 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>"
						+ "</tr><tr>"
						+ "<td width=\"100\"><button value=\"Item Id\"  action=\"bypass -h admin_smartshop &&"
						+ marks
						+ " &&_ids && $input1 && $input2\" width=90 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>"
						+ "<td width=\"100\"><button value=\"Equipped\" action=\"bypass -h admin_smartshop &&"
						+ marks
						+ " &&_equip && $input1 && $input2\" width=90 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>"
						+ "<td width=\"100\"><button value=\"Grade\"    action=\"bypass -h admin_smartshop &&"
						+ marks
						+ " &&_grade && $input1 && $input2\" width=90 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>"
						+ "</tr></table><br1>"
						+
						// Now the item list the character needs to see:
						showMarkItems(marks)
						+
						// <- Back // Main screen // Next -> buttons
						"<br1><table><tr>"
						+ "<td width=\"100\"><button value=\"<-back\" action=\"bypass -h admin_smartshop &&"
						+ marks
						+ " &&_back && $input1 && $input2\" width=90 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>"
						+ "<td width=\"100\"><button value=\"-main-\" action=\"bypass -h admin_smartshop\" width=90 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>"
						+ "<td width=\"100\"><button value=\"next->\" action=\"bypass -h admin_smartshop &&"
						+ marks
						+ " &&_next && $input1 && $input2\" width=90 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>"
						+ "</tr></table>";
		
		return text;
		
	}
	
	/**
	 * Retreives all the items from the DB SQL, and sets them in the queues
	 * armors, weapons, etcitems, and smartList which contains all items.
	 */
	private void getAllItems()
	{
		String[] SQL_ITEM_SELECTS =
				{ "SELECT id FROM etc_item_template ORDER BY id", "SELECT id FROM armor_template ORDER BY id",
						"SELECT id FROM weapon_template ORDER BY id" };
		
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			int index = 0;
			for (String selectQuery : SQL_ITEM_SELECTS)
			{
				PreparedStatement statement = con.prepareStatement(selectQuery);
				ResultSet rset = statement.executeQuery();
				
				while (rset.next())
				{
					Integer i = rset.getInt("id");
					smartList.add(i);
					index++;
					
					if (selectQuery.contains("FROM armor_template") && (armorStart == -1 || itemsEnd == -1))
					{
						armorStart = index;
						itemsEnd = index - 1;
					}
					else if (selectQuery.contains("FROM weapon_template") && (armorEnd == -1 || weapnStart == -1))
					{
						weapnStart = index;
						armorEnd = index - 1;
					}
				}
				rset.close();
				statement.close();
				
			}
			
			weapnEnd = index - 1;
		}
		catch (Exception e)
		{
			_log.warn("data error on item: ", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
		
		for (Integer x : smartList)
		{
			
			try
			{
				L2Item item = ItemTable.getInstance().getTemplate(x);
				
				if (item == null)
				{
					questList.add(false);
					gradeList.add(L2Item.CRYSTAL_NONE);
				}
				else
				{
					gradeList.add(item.getCrystalType());
					
					if (item.getType2() == 3)
						questList.add(true);
					
					else
						questList.add(false);
				}
			}
			catch (Exception e)
			{
				questList.add(false);
				gradeList.add(L2Item.CRYSTAL_NONE);
			}
			
		}
		
	}
	
	private String showMarkItems(String marks)
	{
		String text = "<table>";
		
		//Start showing items from i:
		int i = getStartIndex(marks);
		
		int[] itemId = getMarkedItems(marks, i);
		int view = valueOfMark("_view", marks);
		int maxId = Math.min((view > 0) ? view : _itemsPerView, itemId.length);
		text += "";
		
		for (int x = 0; x < maxId; x += 3)
		{
			String itemName1 = "", itemName2 = "", itemName3 = "";
			
			try
			{
				itemName1 =
						(ItemTable.getInstance().getTemplate(itemId[x]) == null)
								? "<font color = \"FF0011\">(unknown)</font>" : ItemTable.getInstance()
										.getTemplate(itemId[x]).getName();
			}
			catch (Exception e)
			{
				itemName1 = "<font color = \"FF0000\">(not found)</font>";
			}
			
			try
			{
				itemName2 =
						(ItemTable.getInstance().getTemplate(itemId[x + 1]) == null)
								? "<font color = \"FF0011\">(unknown)</font>" : ItemTable.getInstance()
										.getTemplate(itemId[x + 1]).getName();
			}
			catch (Exception e)
			{
				itemName2 = "<font color = \"FF0000\">(not found)</font>";
			}
			
			try
			{
				itemName3 =
						(ItemTable.getInstance().getTemplate(itemId[x + 2]) == null)
								? "<font color = \"FF0011\">(unknown)</font>" : (ItemTable.getInstance().getTemplate(
										itemId[x + 2]).getName());
			}
			catch (Exception e)
			{
				itemName3 = "<font color = \"FF0000\">(not found)</font>";
			}
			
			if (x + 1 == maxId)
			{
				itemName3 = "";
				itemName2 = "";
			}
			else if (x + 2 == maxId)
			{
				itemName3 = "";
			}
			
			if (marks.contains("_idsShow"))
			{
				itemName1 += "<br1>" + Integer.toString(itemId[x]);
				itemName2 += (!itemName2.isEmpty()) ? "<br1>" + Integer.toString(itemId[x + 1]) : "";
				itemName3 += (!itemName3.isEmpty()) ? "<br1>" + Integer.toString(itemId[x + 2]) : "";
			}
			else if (marks.contains("_idsOnlyShow"))
			{
				itemName1 =
						(itemName1.startsWith("<font color")) ? ("<font color = \"FF0000\"><br1>"
								+ Integer.toString(itemId[x]) + "<br1></font>") : ("<br1>"
								+ Integer.toString(itemId[x]) + "<br1>");
				itemName2 =
						(!itemName2.isEmpty()) ?
						
						(itemName2.startsWith("<font color")) ? ("<font color = \"FF0000\"><br1>"
								+ Integer.toString(itemId[x + 1]) + "<br1></font>") : ("<br1>"
								+ Integer.toString(itemId[x + 1]) + "<br1>") : "";
				
				itemName3 =
						(!itemName3.isEmpty()) ?
						
						(itemName3.startsWith("<font color")) ? ("<font color = \"FF0000\"><br1>"
								+ Integer.toString(itemId[x + 2]) + "<br1></font>") : ("<br1>"
								+ Integer.toString(itemId[x + 2]) + "<br1>") : "";
			}
			
			if (marks.contains("_gradeShow"))
			{
				
				itemName1 += ("<br1>Grade-" + getGradeString(gradeList.get(smartList.indexOf(itemId[x]))) + "<br1>");
				itemName2 +=
						(!itemName2.isEmpty()) ? ("<br1>Grade-"
								+ getGradeString(gradeList.get(smartList.indexOf(itemId[x + 1]))) + "<br1>") : "";
				itemName3 +=
						(!itemName3.isEmpty()) ? ("<br1>Grade-"
								+ getGradeString(gradeList.get(smartList.indexOf(itemId[x + 2]))) + "<br1>") : "";
				
			}
			
			text +=
					"<tr>" + "<td width=\"100\"><a action=\"bypass -h admin_smartshop &&" + marks + " &&_get="
							+ itemId[x] + " && $input1 && $input2\"><font color = \"11DD00\">" + itemName1
							+ "</font></a></td>";
			text +=
					(!itemName2.isEmpty()) ? "<td width=\"100\"><a action=\"bypass -h admin_smartshop &&" + marks
							+ " &&_get=" + itemId[x + 1] + " && $input1 && $input2\"><font color = \"00FF00\">"
							+ itemName2 + "</font></a></td>" : "<td width=\"100\"></td>";
			text +=
					(!itemName3.isEmpty()) ? "<td width=\"100\"><a action=\"bypass -h admin_smartshop &&" + marks
							+ " &&_get=" + itemId[x + 2] + " && $input1 && $input2\"><font color = \"00EE11\">"
							+ itemName3 + "</font></a></td>" : "<td width=\"100\"></td>";
			text += "</tr><br>";
		}
		
		return text + "</table>";
	}
	
	private int getStartIndex(String marks)
	{
		if (marks == null)
			return itemsStart;
		else if (marks.isEmpty())
			return 0;
		
		int index = itemsStart, endId = smartList.size(), start = itemsStart, view = valueOfMark("_view", marks);
		if (marks.contains("_items"))
		{
			endId = itemsEnd;
		}
		else if (marks.contains("_armor"))
		{
			start = armorStart;
			endId = armorEnd;
		}
		else if (marks.contains("_weapn"))
		{
			start = weapnStart;
			endId = weapnEnd;
		}
		
		if (marks.contains("_equip"))
		{
			start = armorStart;
			endId = weapnEnd;
		}
		
		index = start;
		
		if (marks.contains("_next"))
		{
			String pg = marks.substring(marks.lastIndexOf("_next=") + 6);
			String[] pgNum = pg.split(" ");
			if (pgNum.length > 1)
				pg = pgNum[0];
			index += (((view > 0) ? view : _itemsPerView) * Integer.valueOf(pg));
		}
		
		if (marks.contains("_back"))
		{
			String pg = marks.substring(marks.lastIndexOf("_back=") + 6);
			String[] pgNum = pg.split(" ");
			if (pgNum.length > 1)
				pg = pgNum[0];
			index = endId - (((view > 0) ? view : _itemsPerView) * Integer.valueOf(pg));
		}
		
		if (index < start)
			index = start;
		
		if (index > endId - ((view > 0) ? view : _itemsPerView))
			index = endId - ((view > 0) ? view : _itemsPerView);
		return index;
	}
	
	private int[] getMarkedItems(String marks, int i)
	{
		List<Integer> list = null;
		int view = valueOfMark("_view", marks);
		
		if (!marks.contains("_searchAll") && !marks.contains("_idList") && !marks.contains("_grade="))
			list = smartList.subList(i, i + ((view > 0) ? view : _itemsPerView));
		
		else if (marks.contains("_searchAll") || marks.contains("_idList") || marks.contains("_grade="))
		{
			int listSize =
					(marks.contains("_equip")) ? weapnEnd : (marks.contains("_armor")) ? armorEnd : (marks
							.contains("_weapn")) ? weapnEnd : (marks.contains("_items")) ? itemsEnd : smartList.size();
			
			list = getSearchItems(marks, smartList.subList(i, listSize));
		}
		
		if (marks.contains("_quest"))
			list = getQuestItems(list);
		
		if (list == null)
			return null;
		
		int[] returnList = new int[list.size()];
		
		for (Integer iter : list)
			returnList[list.indexOf(iter)] = iter;
		
		return returnList;
		
	}
	
	private List<Integer> getSearchItems(String marks, List<Integer> list)
	{
		if (list == null || list.size() == 0)
			return null;
		
		int view = valueOfMark("_view", marks), breakPoint = ((view > 0) ? view : _itemsPerView);
		
		List<Integer> newList = new ArrayList<Integer>();
		
		if (marks.contains("_next"))
		{
			int a = valueOfMark("_next", marks);
			
			if (a > 0)
				breakPoint += (a * ((view > 0) ? view : _itemsPerView));
		}
		
		for (Integer i : list)
		{
			i = correctItem(marks, i);
			
			if (i != null)
				newList.add(i);
			
			if (newList.size() > breakPoint)
				break;
		}
		
		return (newList.size() <= ((view > 0) ? view : _itemsPerView)) ? newList : newList.subList((newList.size() - 1)
				- ((view > 0) ? view : _itemsPerView), newList.size());
	}
	
	/**
	 * Receives a List and returns a smaller list containing only quest items.
	 * @param list the list of item ids
	 * @return a new list only with the quest item ids
	 */
	private List<Integer> getQuestItems(List<Integer> list)
	{
		List<Integer> newList = new ArrayList<Integer>();
		for (Integer i : list)
		{
			if (i != null && questList.get(smartList.indexOf(i)))
				newList.add(i);
		}
		
		return newList;
	}
	
	private Integer correctItem(String marks, Integer itemId)
	{
		
		if (itemId == null)
			return null;
		
		boolean returnItem = false;
		
		if (marks.contains("_grade="))
		{
			if (!gradeList.get(smartList.indexOf(itemId)).equals(getGradeValue(stringOfMark("_grade=", marks))))
				return null;
			
			returnItem = true;
		}
		
		if (marks.contains("_searchAll"))
		{
			try
			{
				returnItem = false;
				
				String itemName = ItemTable.getInstance().getTemplate(itemId).getName();
				String[] param = getSearchValues(marks, "_searchAll=");
				
				if (param != null)
					for (String s : param)
					{
						if (itemName.toLowerCase().contains(s.toLowerCase()))
						{
							returnItem = true;
						}
					}
				
			}
			catch (Exception e)
			{
				returnItem = false;
			}
			
		}
		
		if (marks.contains("_idList"))
		{
			try
			{
				returnItem = false;
				
				String[] param = getSearchValues(marks, "_idList=");
				
				if (param != null)
					for (String s : param)
					{
						if (itemId.equals(Integer.valueOf(s)))
						{
							returnItem = true;
						}
					}
			}
			catch (Exception e)
			{
				returnItem = false;
			}
		}
		
		return (returnItem) ? itemId : null;
	}
	
	private String getTitle(String mark)
	{
		String message = "";
		if (mark.isEmpty())
			return "Welcome to Smart Shop [by Darki699]";
		if (mark.contains("_quest") && !mark.contains("_equip") && !mark.contains("_items") && !mark.contains("_armor")
				&& !mark.contains("_weapn"))
			message += "Quest Items";
		else if (mark.contains("_quest"))
			message += "Quest ";
		if (mark.contains("_equip"))
			message += "Equippable Items";
		else if (mark.contains("_items"))
			message += "Item Search";
		else if (mark.contains("_weapn"))
			message += "Weapon Search";
		else if (mark.contains("_armor"))
			message += "Armor Search";
		
		if (mark.contains("_idsShow"))
		{
			message += (message.isEmpty()) ? "" : ", ";
			message += "Name&ID viewing";
		}
		else if (mark.contains("_idsOnlyShow"))
		{
			message += (message.isEmpty()) ? "" : ", ";
			message += "ID viewing";
		}
		
		if (mark.contains("_next="))
		{
			String pg = mark.substring(mark.lastIndexOf("_next=") + 6);
			String[] pgNum = pg.split(" ");
			if (pgNum.length > 1)
				pg = pgNum[0];
			message += (message.isEmpty()) ? "" : ", ";
			message += "Page " + (Integer.valueOf(pg) + 1);
		}
		else if (mark.contains("_back="))
		{
			String pg = mark.substring(mark.lastIndexOf("_back=") + 6);
			String[] pgNum = pg.split(" ");
			if (pgNum.length > 1)
				pg = pgNum[0];
			String pages = (Integer.valueOf(pg) > 1) ? " Pages" : " Page";
			message += (message.isEmpty()) ? "" : ", ";
			message += "Back " + pg + pages;
		}
		
		return (message.isEmpty()) ? "Welcome to Smart Shop [by Darki699]" : message;
	}
	
	private String getMessage(String mark)
	{
		if (mark == null || mark.isEmpty())
			return "<center>General Search for All items</center><br1>";
		
		String message = "";
		
		if (mark.contains("_equip"))
			message = "<font color = \"0000FF\"><center>Searching for All Equipped Items</center></font><br1>";
		
		else if (mark.contains("_armor"))
			message = "<font color = \"0000FF\"><center>Searching for Armors</center></font><br1>";
		
		else if (mark.contains("_weapn"))
			message = "<font color = \"0000FF\"><center>Searching for Weapons and Shields</center></font><br1>";
		
		else if (mark.contains("_items"))
		{
			message = "<font color = \"0000FF\"><center>Searching for Etc Items</center></font><br1>";
			
			if (mark.contains("_equip"))
				message += "<font color = \"FF0000\"><center>Warning! Etc Item not Equippable</center></font><br>";
		}
		
		if (mark.contains("_quest"))
		{
			message += "<center>Narrow search for Quest Items</center><br1>";
			
			if (mark.contains("_armor") || mark.contains("_weapn") || mark.contains("_equip"))
				message += "<font color = \"FF0000\"><center>Warning! Wrong Search Category!</center></font><br>";
		}
		
		if (mark.contains("_grade="))
		{
			message += "<center>Narrow search for Grade-" + stringOfMark("_grade=", mark) + " Items</center><br1>";
		}
		else if (mark.contains("_gradeShow"))
		{
			message += "<center>Added Grade of Items</center><br1>";
		}
		
		if (mark.contains("_idsShow"))
		{
			message += "<center>Showing items by Name & IDs</center><br1>";
		}
		else if (mark.contains("_idsOnlyShow"))
		{
			message += "<center>Showing item IDs only</center><br1>";
		}
		
		if (mark.contains("_searchAll"))
		{
			message += "<center>Searching Specific Names:</center><br1><center>";
			String[] param = getSearchValues(mark, "_searchAll");
			boolean add = false;
			for (String s : param)
			{
				if (add)
					message += " OR ";
				message += s;
				add = true;
			}
			message += "</center><br1>";
		}
		
		if (mark.contains("_idList"))
		{
			message += "<center>Searching Specific IDs:</center><br1><center>";
			String[] param = getSearchValues(mark, "_idList");
			boolean add = false;
			for (String s : param)
			{
				if (add)
					message += " OR ";
				message += s;
				add = true;
				
			}
			message += "</center><br1>";
			
			for (String s : param)
			{
				int itemid = Integer.valueOf(s);
				if (smartList.indexOf(itemid) == -1)
					message +=
							"<font color = \"FF0000\"><center>Warning! " + s + " does not exist!</center></font><br>";
				else if (mark.contains("_equip")
						&& (smartList.indexOf(itemid) < armorStart || smartList.indexOf(itemid) > weapnEnd))
					message +=
							"<font color = \"FF0000\"><center>Warning! " + s + " not equippable!</center></font><br>";
				else if (mark.contains("_armor")
						&& (smartList.indexOf(itemid) < armorStart || smartList.indexOf(itemid) > armorEnd))
					message += "<font color = \"FF0000\"><center>Warning! " + s + " not in Armors!</center></font><br>";
				else if (mark.contains("_weapn")
						&& (smartList.indexOf(itemid) < weapnStart || smartList.indexOf(itemid) > weapnEnd))
					message +=
							"<font color = \"FF0000\"><center>Warning! " + s + " not in Weapons!</center></font><br>";
				else if (mark.contains("_items")
						&& (smartList.indexOf(itemid) < itemsStart || smartList.indexOf(itemid) > itemsEnd))
					message +=
							"<font color = \"FF0000\"><center>Warning! " + s + " not in EtcItems!</center></font><br>";
				else if (mark.contains("_quest") && !questList.get(smartList.indexOf(itemid)))
					message +=
							"<font color = \"FF0000\"><center>Warning! " + s + " not a Quest Item!</center></font><br>";
			}
			
		}
		
		return message;
	}
	
	/**
	 * Runs parse functions on the marks collection and adds the newMark to them
	 * @param marks - String mark collection
	 * @param newMark - String new mark to add to the collection
	 * @return String new mark collection
	 */
	private String calculateMarks(String marks, String newMark)
	{
		if (newMark == null)
			newMark = "";
		
		if (marks == null)
			marks = "";
		
		if (marks.isEmpty() && newMark.isEmpty())
			return "";
		
		String newMarks = parseItemMarks(marks, newMark);
		if (!marks.equals(newMarks))
			return newMarks;
		
		marks = parseSearchValues(marks, newMark);
		
		newMarks = parseCategorySwitch(marks, newMark);
		if (!marks.equals(newMarks))
			return newMarks;
		
		newMarks = parseBackNext(marks, newMark);
		if (!marks.equals(newMarks))
		{
			
			if (newMarks.contains("_search") || newMarks.contains("_idList"))
				newMarks = removeMark(newMarks, "_back=");
			
			return newMarks;
		}
		
		newMarks = parseIdOption(marks, newMark);
		if (!marks.equals(newMarks))
			return newMarks;
		
		newMarks = parseEquipOption(marks, newMark);
		if (!marks.equals(newMarks))
			return newMarks;
		
		newMarks = parseGradeOption(marks, newMark);
		if (!marks.equals(newMarks))
			return newMarks;
		
		newMarks = parseQuestOption(marks, newMark);
		if (!marks.equals(newMarks))
			return newMarks;
		
		newMarks = parseViewOption(marks, newMark);
		if (!marks.equals(newMarks))
			return newMarks;
		
		return marks;
		
	}
	
	private String parseItemMarks(String marks, String newMark)
	{
		if (!newMark.contains("_get="))
			return marks;
		
		if (newMark.contains("-eraseItem") || newMark.contains("-editItem"))
		{
			marks = (newMark.contains("-eraseItem")) ? removeMark(marks, "_buyItem=") : marks;
			return marks;
		}
		
		return marks + " " + newMark;
	}
	
	/**
	 * Parse the search values for letters and numbers
	 * @param marks
	 * @param newMark
	 * @return
	 */
	private String parseSearchValues(String marks, String newMark)
	{
		if (!newMark.contains("_searchAll") && !newMark.contains("_idList"))
			return marks;
		
		String search = "", idList = "";
		
		if (marks.contains("_searchAll") || marks.contains("_idList"))
		{
			String[] param = marks.split(" ");
			for (String s : param)
			{
				
				if (s.contains("_searchAll=") && newMark.contains("_searchAll"))
				{
					search = "," + s.substring(11);
					marks = marks.replaceAll(s, "");
				}
				
				if (s.contains("_idList=") && newMark.contains("_idList"))
				{
					idList = "," + s.substring(8);
					marks = marks.replaceAll(s, "");
				}
				
			}
		}
		
		String[] param = newMark.split(" ");
		for (String s : param)
		{
			if (s.contains("_searchAll="))
			{
				marks += analyzeSearchString(s + search);
				marks = removeMark(marks, "_next=");
				marks = removeMark(marks, "_back=");
			}
			
			if (s.contains("_idList="))
			{
				marks += analyzeSearchString(s + idList);
				marks = removeMark(marks, "_next=");
				marks = removeMark(marks, "_back=");
			}
			
		}
		
		marks = rebuild(marks, " ");
		
		return marks;
	}
	
	/**
	 * Receives a search string, e.g. "value=val1,-val2,-val4,val4...",
	 * and returns an organized string e.g. "value=val1"
	 * @param search
	 * @return
	 */
	private String analyzeSearchString(String search)
	{
		if (search == null || search.isEmpty())
			return "";
		
		String[] param = search.split("=");
		
		if (param.length < 2)
			return "";
		
		if (param[1] == null || param[1].isEmpty())
			return "";
		
		String[] value = param[1].split(",");
		
		for (int y = 0; y < value.length; y++)
		{
			if (value[y].startsWith("+"))
				value[y] = value[y].substring(1);
		}
		
		for (String s : value)
		{
			if (s.startsWith("-"))
			{
				s = s.substring(1);
				for (int i = 0; i < value.length; i++)
				{
					if (s.toLowerCase().equals(value[i].toLowerCase())
							|| value[i].toLowerCase().equals("-" + s.toLowerCase()))
						value[i] = "";
				}
			}
		}
		boolean add = false;
		param[1] = "";
		for (int i = 0; i < value.length; i++)
		{
			if (value[i] != null && !value[i].isEmpty() && !value[i].startsWith("-"))
			{
				if (add)
					param[1] += ",";
				
				param[1] += value[i];
				add = true;
			}
		}
		
		if (param[1].isEmpty())
			return "";
		
		return " " + param[0] + "=" + param[1];
		
	}
	
	/**
	 * Changes back/next values for the mark collection:
	 * e.g. //smartshop _next=3 && _back && 4    ->    //smartshop _back=1
	 * 
	 * @param marks - the mark collection
	 * @param newMark - the new mark
	 * @return the new mark collection
	 */
	
	private String parseBackNext(String marks, String newMark)
	{
		if (!newMark.contains("_next") && !newMark.contains("_back"))
			return marks;
		
		String[] mark = marks.split(" ");
		
		// Opposite move
		if ((marks.contains("_back") && newMark.contains("_next"))
				|| (marks.contains("_next") && newMark.contains("_back")))
		{
			String lookFor = "";
			if (newMark.contains("_next"))
				lookFor = "_back=";
			else
				lookFor = "_next=";
			
			int changeVal = 1;
			try
			{
				changeVal = Integer.valueOf(newMark.substring(6));
			}
			catch (Exception e)
			{
			}
			
			for (int iter = mark.length - 1; iter > -1; iter--)
			{
				
				if (mark[iter].startsWith(lookFor))
				{
					int size = (Integer.valueOf(mark[iter].substring(6)) - changeVal);
					if (size > 0)
						mark[iter] = lookFor + size;
					else if (size == 0)
						mark[iter] = "";
					else if (size < 0)
					{
						mark[iter] = (mark[iter].contains("next")) ? ("_back=") : ("_next=");
						size = Math.abs(size);
						mark[iter] += getMaxMove(size, marks);
					}
					newMark = "";
					break;
				}
				
			}
		}
		
		// Same move
		else if ((marks.contains("_back") && newMark.contains("_back"))
				|| (marks.contains("_next") && newMark.contains("_next")))
		{
			String lookFor = "";
			if (newMark.contains("_next"))
				lookFor = "_next=";
			else
				lookFor = "_back=";
			
			int changeVal = 1;
			try
			{
				changeVal = Integer.valueOf(newMark.substring(6));
			}
			catch (Exception e)
			{
			}
			
			for (int iter = mark.length - 1; iter > -1; iter--)
			{
				
				if (mark[iter].startsWith(lookFor))
				{
					int size = (Integer.valueOf(mark[iter].substring(6)) + changeVal);
					size = getMaxMove(size, marks);
					mark[iter] = lookFor + size;
					newMark = "";
					break;
				}
				
			}
		}
		
		// New move
		else if (newMark.contains("_back") || newMark.contains("_next"))
		{
			String buildNewMark = (newMark.contains("_next")) ? "_next=" : "_back=";
			int changeVal = 1;
			try
			{
				changeVal = Integer.valueOf(newMark.substring(6));
				changeVal = getMaxMove(changeVal, marks);
			}
			catch (Exception e)
			{
			}
			newMark = buildNewMark + changeVal;
		}
		else
		{
			return marks;
		}
		
		if (marks.isEmpty())
			return newMark;
		
		marks = mark[0];
		for (int x = 1; x < mark.length; x++)
			marks += " " + mark[x];
		
		return marks + ((newMark.isEmpty()) ? "" : (" " + newMark));
	}
	
	/**
	 * Controls the category switch for items, armors and weapons.
	 * @param marks
	 * @param newMark
	 * @return
	 */
	private String parseCategorySwitch(String marks, String newMark)
	{
		if (!newMark.contains("_weapn") && !newMark.contains("_armor") && !newMark.contains("_items"))
			return marks;
		
		boolean move = false;
		
		if (marks.contains("_weapn") && (newMark.startsWith("_armor") || newMark.startsWith("_items"))
				|| marks.contains("_armor") && (newMark.startsWith("_weapn") || newMark.startsWith("_items"))
				|| marks.contains("_items") && (newMark.startsWith("_armor") || newMark.startsWith("_weapn")))
		{
			if (newMark.contains("_items") && marks.contains("_equip"))
			{
				marks = removeMark(marks, "_equip");
			}
			
			marks = marks.replaceAll("_weapn", newMark.substring(0, 6));
			marks = marks.replaceAll("_armor", newMark.substring(0, 6));
			marks = marks.replaceAll("_items", newMark.substring(0, 6));
			move = true;
		}
		else if ((!marks.contains("_weapn") && !marks.contains("_armor") && !marks.contains("_items"))
				&& (newMark.startsWith("_weapn") || newMark.startsWith("_armor") || newMark.startsWith("_items")))
		{
			
			if (newMark.contains("_items") && marks.contains("_equip"))
			{
				marks = removeMark(marks, "_equip");
			}
			
			marks += " " + newMark.substring(0, 6);
			move = true;
		}
		
		// When a category is switched, we reset the next & back pages option
		if (move && (marks.contains("_next") || marks.contains("_back")))
		{
			marks = removeMark(marks, "_next=");
			marks = removeMark(marks, "_back=");
		}
		
		return marks;
	}
	
	/**
	 * Parse the ID options given
	 * @param marks
	 * @param newMark
	 * @return
	 */
	private String parseIdOption(String marks, String newMark)
	{
		if (!newMark.contains("_ids"))
			return marks;
		
		// Id viewing on: both name and id will show
		if (newMark.startsWith("_ids") && !marks.contains("_idsShow") && !marks.contains("_idsOnlyShow"))
			marks = "_idsShow " + marks;
		// Id ONLY viewing: only Id will show
		else if (newMark.startsWith("_ids") && marks.contains("_idsShow"))
		{
			marks = removeMark(marks, "_idsShow");
			marks = "_idsOnlyShow " + marks;
		}
		// Id viewing off: only name will show
		else if (newMark.startsWith("_ids") && marks.contains("_idsOnlyShow"))
		{
			marks = removeMark(marks, "_idsOnlyShow");
		}
		
		return marks;
	}
	
	/**
	 * Parse the Grade options given to the items grade
	 * @param marks
	 * @param newMark
	 * @return
	 */
	private String parseGradeOption(String marks, String newMark)
	{
		if (!newMark.contains("_grade"))
			return marks;
		
		// Grade viewing on: both name and grade will show
		if (newMark.startsWith("_grade") && !marks.contains("_grade"))
		{
			String[] param = newMark.split(" ");
			if (param.length == 1)
			{
				marks = "_gradeShow " + marks;
			}
			else if (param.length > 1)
			{
				if (!getGrade(param[1]).isEmpty())
				{
					marks = "_grade=" + getGrade(param[1]) + " " + marks;
				}
				else
				{
					marks = "_gradeShow " + marks;
				}
			}
		}
		
		else if (newMark.startsWith("_grade") && marks.contains("_grade"))
		{
			String[] param = newMark.split(" ");
			if (param.length > 1)
			{
				
				marks = removeMark(marks, "_grade=");
				marks = removeMark(marks, "_next=");
				marks = removeMark(marks, "_back=");
				
				if (!getGrade(param[1]).isEmpty())
				{
					marks = "_grade=" + getGrade(param[1]) + " " + marks;
				}
			}
			else
			{
				marks = (marks.contains("_gradeShow")) ? removeMark(marks, "_gradeShow") : ("_gradeShow " + marks);
				marks = (marks.contains("_grade=")) ? removeMark(marks, "_grade=") : marks;
			}
		}
		
		return marks;
	}
	
	/**
	 * Parse the Quest toggle option search, will show only quest items
	 * @param marks String received list of current marks
	 * @param newMark String of the new mark
	 * @return return the String mark collection with the _quest mark changes made
	 */
	
	private String parseQuestOption(String marks, String newMark)
	{
		if (!newMark.contains("_quest"))
			return marks;
		
		if (newMark.startsWith("_quest") && !marks.contains("_quest"))
			marks = "_quest " + marks;
		else if (newMark.startsWith("_quest") && marks.contains("_quest"))
		{
			marks = removeMark(marks, "_quest");
		}
		
		return marks;
	}
	
	/**
	 * Parse the Equip options given, show equipped items on/off
	 * @param marks
	 * @param newMark
	 * @return
	 */
	private String parseEquipOption(String marks, String newMark)
	{
		
		if (!newMark.contains("_equip"))
			return marks;
		
		if (newMark.startsWith("_equip") && !marks.contains("_equip"))
			marks = "_equip " + marks;
		else if (newMark.startsWith("_equip") && marks.contains("_equip"))
		{
			marks = removeMark(marks, "_equip");
		}
		
		return marks;
	}
	
	/**
	 * Parse the View options given, show number of items
	 * @param marks
	 * @param newMark
	 * @return
	 */
	private String parseViewOption(String marks, String newMark)
	{
		
		if (!newMark.contains("_view"))
			return marks;
		
		if (newMark.startsWith("_view") && !marks.contains("_view"))
		{
			String[] param = newMark.split(" ");
			if (param.length > 1)
			{
				try
				{
					Integer i = Integer.valueOf(param[1]);
					if (i > 0 && i < 100)
						marks = "_view=" + i + " " + marks;
				}
				catch (Exception e)
				{
					return marks;
				}
				
			}
		}
		else if (newMark.startsWith("_view") && marks.contains("_view"))
		{
			marks = removeMark(marks, "_view=");
			marks = parseViewOption(marks, newMark);
		}
		
		return marks;
	}
	
	private int getMaxMove(int moveVal, String marks)
	{
		int pages = smartList.size();
		
		if (marks.contains("_armor"))
		{
			pages = (armorEnd - armorStart);
		}
		else if (marks.contains("_weapn"))
		{
			pages = weapnEnd - weapnStart;
		}
		else if (marks.contains("_items"))
		{
			pages = itemsEnd - itemsStart;
		}
		
		if (marks.contains("_equip"))
		{
			pages = weapnEnd - armorStart;
		}
		
		int view = valueOfMark("_view", marks);
		pages = (pages / ((view > 0) ? view : _itemsPerView));
		if (moveVal > pages)
			moveVal = pages;
		
		return moveVal;
	}
	
	/**
	 * @param opCommand
	 * @param first
	 * @param second
	 * @param actor
	 */
	String parseParam(String opCommand, String first, String second, L2Player actor)
	{
		if (first == null)
			first = "";
		if (second == null)
			second = "";
		
		//FIXME: WTF?!?!?!? Oo
		/*
		if (opCommand.contains("_armor") || opCommand.contains("_weapn") || opCommand.contains("_items"))
		{
			
		}
		*/
		
		return first;
	}
	
	String parseParam(String opCommand, String first, L2Player actor)
	{
		if (first == null)
			first = "";
		
		if (opCommand.contains("_get="))
		{
			try
			{
				int count = Integer.valueOf(first.substring(1));
				if (count > 0)
				{
					int itemId = valueOfMark("_get=", opCommand);
					if (ItemTable.getInstance().getTemplate(itemId) != null)
					{
						// Prevent hlApex users from abusing this...
						if (!actor.isGM())
						{
							new Disconnection(actor).defaultSequence(false);
						}
						
						if (!ItemTable.getInstance().getTemplate(itemId).isStackable() && count > 10)
						{
							actor.sendMessage("Item is not stackable, you may purchase only 10 at a time.");
							return "-eraseItem";
						}
						
						giveItem(actor, itemId, count);
					}
				}
				else
					return "-editItem";
			}
			catch (Exception e)
			{
				return "-eraseItem";
			}
			return "-editItem";
		}
		
		if (opCommand.contains("_grade"))
		{
			try
			{
				if (!getGrade(first.substring(1)).isEmpty())
					return first;
			}
			catch (Exception e)
			{
				return "";
			}
		}
		
		if (opCommand.contains("_back") || opCommand.contains("_next") || opCommand.contains("_view"))
		{
			return first;
		}
		
		if (opCommand.contains("_armor") || opCommand.contains("_weapn") || opCommand.contains("_items")
				|| opCommand.contains("_equip") || opCommand.contains("_ids"))
		{
			List<String> search = new ArrayList<String>();
			List<Integer> ids = new ArrayList<Integer>();
			String[] param = first.split(" ");
			for (String s : param)
			{
				if (s.isEmpty())
					continue;
				// Letters or Numbers?
				try
				{
					int value = Integer.valueOf(s);
					ids.add(value);
				}
				catch (Exception e)
				{
					search.add(s);
				}
			}
			first = "";
			if (!search.isEmpty())
			{
				first = " _searchAll=";
				String[] list = search.toArray(new String[search.size()]);
				for (int i = 0; i < list.length; i++)
					for (int j = 0; j < list.length; j++)
					{
						if (i != j && !list[i].isEmpty() && !list[j].isEmpty())
						{
							if (list[i].toLowerCase().contains(list[j].toLowerCase())
									|| list[i].toLowerCase().equals(list[j].toLowerCase()))
								list[j] = "";
							else if (list[j].toLowerCase().contains(list[i].toLowerCase()))
								list[i] = "";
						}
					}
				boolean add = false;
				for (int i = 0; i < list.length; i++)
				{
					if (list[i] != null && !list[i].isEmpty())
					{
						if (add)
							first += ",";
						
						first += list[i];
						add = true;
					}
				}
			}
			if (!ids.isEmpty())
			{
				first += " _idList=";
				Integer[] list = ids.toArray(new Integer[ids.size()]);
				for (int i = 0; i < list.length; i++)
					for (int j = 0; j < list.length; j++)
					{
						if (list[i] == null || list[j] == null)
							continue;
						
						else if (i != j && list[i] == list[j])
						{
							list[i] = null;
						}
					}
				boolean add = false;
				for (Integer finalElement : list)
				{
					if (finalElement != null)
					{
						if (add)
							first += ",";
						
						first += finalElement;
						add = true;
					}
				}
				
			}
			return first;
		}
		
		first = "";
		return first;
	}
	
	/**
	 * Parses a String by rebuilding it with only one parse space between values:
	 * e.g. if marks="a,,b,c" and parse="," then will return "a,b,c"
	 * @param marks
	 * @param parse
	 * @return
	 */
	private String rebuild(String marks, String parse)
	{
		String[] param = marks.split(parse);
		boolean add = false;
		marks = "";
		
		for (String s : param)
		{
			if (s != null && !s.isEmpty())
			{
				if (add)
					marks += parse;
				
				marks += s;
				add = true;
			}
		}
		
		return marks;
	}
	
	private String[] getSearchValues(String marks, String find)
	{
		try
		{
			String[] param = marks.split(" ");
			for (String s : param)
			{
				if (s.contains(find))
				{
					param = s.split("=");
					param = param[1].split(",");
					break;
				}
			}
			return param;
		}
		catch (Exception e)
		{
			return null;
		}
	}
	
	private String removeMark(String marks, String removeMark)
	{
		String[] param = marks.split(" ");
		for (int x = 0; x < param.length; x++)
		{
			if (param[x].startsWith(removeMark))
				param[x] = "";
		}
		
		marks = "";
		boolean add = false;
		for (int x = 0; x < param.length; x++)
		{
			if (param[x] != null && !param[x].isEmpty())
			{
				if (add)
					marks += " ";
				
				marks += param[x];
				add = true;
			}
			
		}
		
		return marks;
	}
	
	private int valueOfMark(String search, String marks)
	{
		String[] param = marks.split(" ");
		
		for (String s : param)
		{
			if (s.contains(search))
			{
				param = s.split("=");
				try
				{
					return Integer.valueOf(param[1]);
				}
				catch (Exception e)
				{
					return -1;
				}
			}
			
		}
		
		return -1;
	}
	
	private String stringOfMark(String search, String marks)
	{
		String[] param = marks.split(" ");
		
		for (String s : param)
		{
			if (s.contains(search))
			{
				param = s.split("=");
				try
				{
					return param[1];
				}
				catch (Exception e)
				{
					return "";
				}
			}
			
		}
		
		return "";
	}
	
	private int getGradeValue(String grade)
	{
		if (grade.startsWith("S"))
			return L2Item.CRYSTAL_S;
		else if (grade.equals("A"))
			return L2Item.CRYSTAL_A;
		else if (grade.equals("B"))
			return L2Item.CRYSTAL_B;
		else if (grade.equals("C"))
			return L2Item.CRYSTAL_C;
		else if (grade.equals("D"))
			return L2Item.CRYSTAL_D;
		else if (grade.equals("NONE"))
			return L2Item.CRYSTAL_NONE;
		return -1;
	}
	
	private String getGradeString(int grade)
	{
		if (grade >= L2Item.CRYSTAL_S)
			return "S";
		else if (grade == L2Item.CRYSTAL_A)
			return "A";
		else if (grade == L2Item.CRYSTAL_B)
			return "B";
		else if (grade == L2Item.CRYSTAL_C)
			return "C";
		else if (grade == L2Item.CRYSTAL_D)
			return "D";
		else if (grade == L2Item.CRYSTAL_NONE)
			return "NONE";
		
		return "";
	}
	
	private String getGrade(String grade)
	{
		grade = grade.toUpperCase();
		if (grade.startsWith("S") || grade.equals(L2Item.CRYSTAL_S))
			return "S";
		else if (grade.equals("A") || grade.equals(L2Item.CRYSTAL_A))
			return "A";
		else if (grade.equals("B") || grade.equals(L2Item.CRYSTAL_B))
			return "B";
		else if (grade.equals("C") || grade.equals(L2Item.CRYSTAL_C))
			return "C";
		else if (grade.equals("D") || grade.equals(L2Item.CRYSTAL_D))
			return "D";
		else if (grade.equals("N") || grade.equals("NO") || grade.equals("NONE") || grade.equals(L2Item.CRYSTAL_NONE))
			return "NONE";
		
		return "";
	}
	
	private void giveItem(L2Player pc, int itemId, int count)
	{
		
		L2ItemInstance item = ItemTable.getInstance().createItem("Smart Shop Admin Buy", itemId, count, pc);
		pc.addItem("Admin SmartShop Buy", item.getItemId(), count, pc, false, false);
		
		String itemName = item.getName();
		
		StatusUpdate su = new StatusUpdate(pc);
		su.addAttribute(StatusUpdate.CUR_LOAD, pc.getCurrentLoad());
		pc.sendPacket(su);
		
		SystemMessage sm;
		sm = new SystemMessage(SystemMessageId.EARNED_S1);
		sm.addString(count + " " + itemName + " from Smart Shop..");
		pc.sendPacket(sm);
		
	}
	
	private String parseItemMarks(String mark, String[] param)
	{
		if (param.length < 3)
			return mark;
		
		String newMark = param[2];
		String opItemCmds[] = { "_getSkillItem=", "_editItem=", "_detailItem=", "_getQuestItem=", "_getBuyListItem=" };
		
		for (String itemCmd : opItemCmds)
		{
			if (newMark.startsWith(itemCmd))
			{
				mark += " " + newMark;
			}
		}
		
		return mark;
	}
	
	private String getSkillItemText(int itemId, L2Player activeChar)
	{
		String message = "";
		if (skillsWithItems == null)
			skillsWithItems = new ArrayList<L2Skill>();
		if (skillsWithItems.isEmpty())
		{
			for (ClassId classId : ClassId.values())
			{
				try
				{
					for (L2SkillLearn skill : SkillTreeTable.getInstance().getAllowedSkills(classId))
					{
						try
						{
							L2Skill s = SkillTable.getInstance().getInfo(skill.getId(), skill.getLevel());
							if (s.getItemConsumeId() == 0)
								continue;
							boolean add = true;
							for (L2Skill temp : skillsWithItems)
							{
								if (temp.getId() == s.getId())
								{
									add = false;
									break;
								}
							}
							if (add)
								skillsWithItems.add(s);
						}
						catch (Exception e)
						{
							continue;
						}
					}
					
				}
				catch (Exception e)
				{
				}
			}
		}
		
		for (L2Skill s : skillsWithItems)
		{
			if (s.getItemConsumeId() == itemId)
			{
				message = (message.isEmpty()) ? "<br1><font color=\"00FF00\">Skills use this item:</font>" : message;
				message += "<br1>" + s.getName() + "(" + s.getId() + ") from level: " + s.getLevel();
			}
		}
		
		try
		{
			L2Item item = ItemTable.getInstance().getTemplate(itemId);
			L2Skill[] itemSkills = null;
			L2Skill[] enchantSkills = null;
			if (item instanceof L2Weapon)
			{
				itemSkills = ((L2Weapon)item).getSkills();
				enchantSkills = ((L2Weapon)item).getEnchant4Skills();
			}
			else if (item instanceof L2Armor)
			{
				itemSkills = ((L2Armor)item).getSkills();
			}
			
			if (itemSkills != null)
			{
				message += "<br1><font color=\"00FF00\">Instance Skill(s):</font>";
				for (L2Skill itemSkill : itemSkills)
					message +=
							"<br1>" + itemSkill.getName() + "(" + itemSkill.getId() + ") Level: "
									+ itemSkill.getLevel();
			}
			if (enchantSkills != null)
			{
				message += "<br1><font color=\"00FF00\">Enchant (+4 or more) Skill(s):</font>";
				for (L2Skill itemSkill : enchantSkills)
					message +=
							"<br1>" + itemSkill.getName() + "(" + itemSkill.getId() + ") Level: "
									+ itemSkill.getLevel();
			}
			
			if (item instanceof L2Equip)
			{
				Func[] funcs = ItemTable.getInstance().createDummyItem(itemId).getStatFuncs();
				if (funcs != null && funcs.length > 0)
				{
					message += "<br1><font color=\"00FF00\">Item Functions:</font>";
					
					for (Func func : funcs)
					{
						String param =
								func.getClass().toString().substring(5 + func.getClass().toString().lastIndexOf("."));
						message += "<br1>" + param + " ";
						if (func.stat != null)
						{
							message += func.stat.getValue() + " ";
							
							Env env = new Env();
							env.player = activeChar;
							env.target = activeChar;
							env.skill = null;
							env.value = 1;
							func.calcIfAllowed(env);
							env.value = (param.equals("Add") || param.equals("Sub")) ? (env.value - 1) : env.value;
							message += (param.equals("Enchant") && env.value == 1) ? "" : env.value;
						}
						
					}
				}
			}
		}
		catch (Exception e)
		{
		}
		
		return (message.isEmpty())
				? "<br1><center><font color=\"FF0000\">No Skills attached to this item.</font></center>" : message;
	}
	
	private String getDetailItemText(int itemId)
	{
		String message = "";
		
		try
		{
			L2Item item = ItemTable.getInstance().getTemplate(itemId);
			message +=
					(itemId != item.getItemDisplayId() && item.getItemDisplayId() > 0) ? ("<br1>Display Id: " + item
							.getItemDisplayId()) : "";
			String part = "", type = "";
			switch (item.getBodyPart())
			{
				case L2Item.SLOT_ALLDRESS:
					part = "All Dress";
					type = "Outfit";
					break;
				case L2Item.SLOT_BABYPET:
					part = "Baby Pet";
					type = "Pet Item";
					break;
				case L2Item.SLOT_BACK:
					part = "Back";
					type = "Outfit";
					break;
				case L2Item.SLOT_CHEST:
					part = "Chest";
					type = "Armor";
					break;
				case L2Item.SLOT_DECO:
					part = "Deco";
					type = "Accessory";
					break;
				case L2Item.SLOT_FEET:
					part = "Feet";
					type = "Armor";
					break;
				case L2Item.SLOT_FULL_ARMOR:
					part = "Full Armor";
					type = "Armor";
					break;
				case L2Item.SLOT_GLOVES:
					part = "Gloves";
					type = "Armor";
					break;
				case L2Item.SLOT_HAIR:
					part = "Hair Left";
					type = "Accessory";
					break;
				case L2Item.SLOT_HAIR2:
					part = "Hair Right";
					type = "Accessory";
					break;
				case L2Item.SLOT_HAIRALL:
					part = "Hair All";
					type = "Accessory";
					break;
				case L2Item.SLOT_HATCHLING:
					part = "Hatchling";
					type = "Pet Item";
					break;
				case L2Item.SLOT_HEAD:
					part = "Head";
					type = "Armor";
					break;
				case L2Item.SLOT_L_BRACELET:
					part = "Left Bracelet";
					type = "Armor";
					break;
				case L2Item.SLOT_L_EAR:
					part = "Left Ear";
					type = "Jewelry";
					break;
				case L2Item.SLOT_LEGS:
					part = "Legs";
					type = "Armor";
					break;
				case L2Item.SLOT_LR_EAR:
					part = "Ear(L/R)";
					type = "Jewelry";
					break;
				case L2Item.SLOT_L_FINGER:
					part = "Left Finger";
					type = "Jewelry";
					break;
				case L2Item.SLOT_L_HAND:
					part = "Left Hand";
					type = "Shield";
					break;
				case L2Item.SLOT_LR_FINGER:
					part = "Finger (L/R)";
					type = "Jewelry";
					break;
				case L2Item.SLOT_LR_HAND:
					part = "Both Hands";
					type = "Weapon";
					break;
				case L2Item.SLOT_NECK:
					part = "Neck";
					type = "Jewelry";
					break;
				case L2Item.SLOT_R_BRACELET:
					part = "Right Bracelet";
					type = "Armor";
					break;
				case L2Item.SLOT_R_EAR:
					part = "Right Ear";
					type = "Jewelry";
					break;
				case L2Item.SLOT_R_FINGER:
					part = "Right Finger";
					type = "Jewelry";
					break;
				case L2Item.SLOT_R_HAND:
					part = "Right Hand";
					type = "Weapon";
					break;
				case L2Item.SLOT_STRIDER:
					part = "Strider";
					type = "Pet Item";
					break;
				case L2Item.SLOT_UNDERWEAR:
					part = "Underwear";
					type = "Armor";
					break;
				case L2Item.SLOT_WOLF:
					part = "Wolf";
					type = "Pet Item";
					break;
				default:
					part = "None";
					type = "Not Known";
					break;
			}
			String grade = "";
			switch (item.getCrystalGrade())
			{
				case L2Item.CRYSTAL_S:
					grade = (item.getName().toLowerCase().contains("infinity")) ? "S Grade / Hero" : "S Grade";
					break;
				case L2Item.CRYSTAL_A:
					grade = "A Grade";
					break;
				case L2Item.CRYSTAL_B:
					grade = "B Grade";
					break;
				case L2Item.CRYSTAL_C:
					grade = "C Grade";
					break;
				case L2Item.CRYSTAL_D:
					grade = "D Grade";
					break;
				default:
					grade = "No Grade";
					break;
			}
			if (item.getName().toLowerCase().contains("dynasty"))
				grade = "S80 Grade / Dynasty";
			
			String material = "Unknown Material";
			switch (item.getMaterialType())
			{
				case L2Item.MATERIAL_ADAMANTAITE:
					material = "Adamantaite";
					break;
				case L2Item.MATERIAL_BLOOD_STEEL:
					material = "Blood Steel";
					break;
				case L2Item.MATERIAL_BONE:
					material = "Bone";
					break;
				case L2Item.MATERIAL_BRONZE:
					material = "Bronze";
					break;
				case L2Item.MATERIAL_CHRYSOLITE:
					material = "Chrysolite";
					break;
				case L2Item.MATERIAL_CLOTH:
					material = "Cloth";
					break;
				case L2Item.MATERIAL_CRYSTAL:
					material = "Crystal";
					break;
				case L2Item.MATERIAL_DAMASCUS:
					material = "Damascus";
					break;
				case L2Item.MATERIAL_DYESTUFF:
					material = "Dye Stuff";
					break;
				case L2Item.MATERIAL_FINE_STEEL:
					material = "Fine Steel";
					break;
				case L2Item.MATERIAL_GOLD:
					material = "Gold";
					break;
				case L2Item.MATERIAL_HORN:
					material = "Horn";
					break;
				case L2Item.MATERIAL_LEATHER:
					material = "Leather";
					break;
				case L2Item.MATERIAL_LIQUID:
					material = "Liquid";
					break;
				case L2Item.MATERIAL_MITHRIL:
					material = "Mithril";
					break;
				case L2Item.MATERIAL_ORIHARUKON:
					material = "Oriharukon";
					break;
				case L2Item.MATERIAL_PAPER:
					material = "Paper";
					break;
				case L2Item.MATERIAL_SCALE_OF_DRAGON:
					material = "Dragon Scale";
					break;
				case L2Item.MATERIAL_SEED:
					material = "Cob Web / Seed";
					break;
				case L2Item.MATERIAL_SILVER:
					material = "Silver";
					break;
				case L2Item.MATERIAL_STEEL:
					material = "Steel";
					break;
				case L2Item.MATERIAL_WOOD:
					material = "Wood";
					break;
			}
			
			message += "<br1>Defined Item Type: " + item.getItemType().toString();
			message += "<br1>Item Type by Slot: " + type;
			message += "<br1>Material: " + material;
			message += "<br1>Sellable: " + (item.isSellable());
			message +=
					(item.getReferencePrice() > 0) ? "<br1>Price: " + item.getReferencePrice()
							: "<br1>No Defined Price";
			message += (item.getWeight() > 0) ? "<br1>Weight: " + item.getWeight() : "<br1>No Weight Defined";
			message += "<br1>Consumable:  " + item.isConsumable();
			message += "<br1>Destroyable: " + item.isDestroyable();
			message += "<br1>Droppable:   " + item.isDropable();
			message +=
					(item.isEquipable()) ? "<br1>Equipped: " + item.getBodyPart() + " -> " + part
							: "<br1>Equippable: false";
			message +=
					(item.isForBabyPet()) ? "<br1> Baby Pet Item" : (item.isForHatchling() ? "<br1> Hatchling Pet Item"
							: (item.isForStrider() ? "<br1>Strider Pet Item" : (item.isForWolf() ? "<br1>Wolf Pet Item"
									: "")));
			message += "<br1>Grade: " + grade;
			message +=
					(item.isCrystallizable()) ? "<br1>Crystallizable: " + item.getCrystalCount() + " Crystals"
							: "<br1>Not Crystallizable";
			message += (item.isHeroItem()) ? "<br1>Hero Only Item" : "";
			message += (item.getDuration() > 0) ? "<br1>Duration: " + item.getDuration() : "";
			message += "<br1>Stackable: " + item.isStackable();
			message += "<br1>Tradeable: " + item.isTradeable();
			if (item instanceof L2Weapon)
			{
				message += "<br1><font color=\"00FF00\">Weapon Details:</font>";
				L2Weapon weapn = (L2Weapon)item;
				message += (weapn.getPDamage() > 0) ? "<br1>Physical Damage: " + weapn.getPDamage() : "";
				message += (weapn.getMDamage() > 0) ? "<br1>Magic Damage: " + weapn.getMDamage() : "";
				message +=
						(weapn.getAttackReuseDelay() > 0) ? "<br1>Attack Reuse Delay: " + weapn.getAttackReuseDelay()
								: "";
				message += (weapn.getAttackSpeed() > 0) ? "<br1>Attack Speed: " + weapn.getAttackSpeed() : "";
				message += (weapn.getAvoidModifier() > 0) ? "<br1>Avoid Modifier: " + weapn.getAvoidModifier() : "";
				message += (weapn.getCritical() > 0) ? "<br1>Critical Hit Rate: " + weapn.getCritical() : "";
				message += (weapn.getHitModifier() > 0) ? "<br1>Hit Modifier: " + weapn.getHitModifier() : "";
				message += (weapn.getMpConsume() > 0) ? "<br1>Consumes " + weapn.getMpConsume() + " MP" : "";
				message += (weapn.getRandomDamage() > 0) ? "<br1>Random Damage: " + weapn.getRandomDamage() : "";
				message += (weapn.getShieldDef() > 0) ? "<br1>Shield Defense: " + weapn.getShieldDef() : "";
				message +=
						(weapn.getShieldDefRate() > 0) ? "<br1>Shield Defense Rate: " + weapn.getShieldDefRate() : "";
				message +=
						(weapn.getSoulShotCount() > 0) ? "<br1>Soul Shots Consumed: " + weapn.getSoulShotCount()
								: "<br1>Does not consume Soul Shots.";
				message +=
						(weapn.getSpiritShotCount() > 0) ? "<br1>Spirit Shots Consumed: " + weapn.getSpiritShotCount()
								: "<br1>Does not consume Spirit Shots.";
				
				try
				{
					message +=
							(weapn.getChangeWeaponId() > 0)
									? ("<br1>Exchange for   <a action=\"bypass -h admin_smartshop &&_get="
											+ weapn.getChangeWeaponId() + " &&_detailItem=" + weapn.getChangeWeaponId()
											+ " && $input1 && $input2\"> "
											+ ItemTable.getInstance().getTemplate(weapn.getChangeWeaponId()).getName()
											+ " Id: " + weapn.getChangeWeaponId())
											+ " </a> " : "";
				}
				catch (Exception e)
				{
				}
			}
			if (item instanceof L2Armor)
			{
				message += "<br1><font color=\"00FF00\">Armor Details:</font>";
				L2Armor armor = (L2Armor)item;
				message += (armor.getPDef() > 0) ? "<br1>P.Def: " + armor.getPDef() : "<br1>No P.Def Defined";
				message += (armor.getMDef() > 0) ? "<br1>M.Def: " + armor.getMDef() : "<br1>No M.Def Defined";
				message += (armor.getHpBonus() > 0) ? "<br1>HP Bonus: " + armor.getHpBonus() : "";
				message += (armor.getMpBonus() > 0) ? "<br1>MP Bonus: " + armor.getMpBonus() : "";
			}
			
		}
		catch (Exception e)
		{
		}
		
		return (message.isEmpty())
				? "<br1><center><font color=\"FF0000\">Details for this item are not available.</font></center>"
				: message;
	}
	
	String questItemText(int itemId)
	{
		String message = "";
		
		try
		{
			L2Item item = ItemTable.getInstance().getTemplate(itemId);
			boolean questItem = (item.getItemType() == L2EtcItemType.QUEST);
			
			message += (item.getItemType() == L2EtcItemType.QUEST) ? "<br1>This is a QUEST item" : "";
			
			for (Quest quest : Quest.findAllEvents())
			{
				if (quest.getRegisteredItemIds() == null)
					continue;
				
				for (int id : quest.getRegisteredItemIds())
				{
					if (id == itemId)
					{
						message =
								(message.isEmpty()) ? "<br1><font color=\"00FF00\">Item found in Event:</font>"
										: message;
						message += "<br1>" + quest.getName();
						questItem = false;
					}
				}
			}
			
			boolean add = true;
			for (Quest quest : QuestManager.getInstance().getAllManagedScripts())
			{
				
				if (quest.getRegisteredItemIds() == null)
					continue;
				
				for (int id : quest.getRegisteredItemIds())
				{
					if (id == itemId)
					{
						message += (add) ? "<br1><font color=\"00FF00\">Item found in Quests:</font>" : "";
						add = false;
						message += "<br1>" + quest.getDescr() + ", Id: " + quest.getQuestIntId();
						questItem = false;
					}
				}
			}
			
			message += (questItem) ? ", but no quests contains this item." : "";
			
		}
		catch (Exception e)
		{
		}
		
		return (message.isEmpty())
				? "<br1><center><font color=\"FF0000\">Quests for this item are not available.</font></center>"
				: message;
	}
	
	private String buylistItemText(int itemId)
	{
		String message = "";
		try
		{
			List<Integer> list = getShopIds(itemId);
			if (list == null)
			{
				return "<br1><center><font color=\"FF0000\">No Buylists for this item.</font></center>";
			}
			if (list.isEmpty())
			{
				return "<br1><center><font color=\"FF0000\">No Buylists for this item.</font></center>";
			}
			
			list = getNpcIds(list);
			
			if (list == null)
			{
				return "<br1><center><font color=\"FF0000\">No Buylists for this item.</font></center>";
			}
			if (list.isEmpty())
			{
				return "<br1><center><font color=\"FF0000\">No Buylists for this item.</font></center>";
			}
			
			message =
					"<br1><font color=\"00FF00\">NPCs selling this item:</font><table><tr><td>NPC</td><td>ID</td><td>(X,Y,Z)</td></tr>";
			
			for (Integer npc : list)
			{
				if (npc == null)
					continue;
				
				try
				{
					int x = 0, y = 0, z = 0;
					for (L2Spawn spawn : SpawnTable.getInstance().getAllTemplates().values())
					{
						if (spawn.getLastSpawn() != null)
						{
							if (spawn.getLastSpawn().getNpcId() == npc)
							{
								x = spawn.getLastSpawn().getX();
								y = spawn.getLastSpawn().getY();
								z = spawn.getLastSpawn().getZ();
								break;
							}
						}
					}
					String coord =
							(x == 0 && y == 0 && z == 0) ? "Not Spawned" : ("(" + x + " , " + y + " , " + z + ")");
					message +=
							"<tr><td>" + NpcTable.getInstance().getTemplate(npc).getName() + "</td><td>" + npc
									+ "</td><td>" + coord + "</td></tr>";
				}
				catch (Exception e)
				{
				}
			}
			message += "</table>";
		}
		catch (Exception e)
		{
		}
		
		return (message.isEmpty()) ? "<br1><center><font color=\"FF0000\">No Buylists for this item.</font></center>"
				: message;
	}
	
	private List<Integer> getShopIds(int ItemId)
	{
		
		List<Integer> shopIds = new ArrayList<Integer>();
		String[] SQL_ITEM_SELECTS =
				{ "SELECT item_template_id, merchant_shop_id FROM custom_merchant_buylist",
						"SELECT item_template_id, merchant_shop_id FROM merchant_buylist" };
		
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			for (String selectQuery : SQL_ITEM_SELECTS)
			{
				PreparedStatement statement = con.prepareStatement(selectQuery);
				ResultSet rset = statement.executeQuery();
				while (rset.next())
				{
					int itemId = rset.getInt("item_template_id");
					Integer shopId = rset.getInt("merchant_shop_id");
					if (ItemId == itemId && !shopIds.contains(shopId))
					{
						shopIds.add(shopId);
					}
				}
				rset.close();
				statement.close();
				
			}
			
		}
		catch (Exception e)
		{
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
		
		return shopIds;
		
	}
	
	private List<Integer> getNpcIds(List<Integer> list)
	{
		
		List<Integer> NpcIds = new ArrayList<Integer>();
		String[] SQL_ITEM_SELECTS =
				{ "SELECT id, npc_template_id FROM custom_merchant_shop", "SELECT id, npc_template_id FROM merchant_shop" };
		
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			for (String selectQuery : SQL_ITEM_SELECTS)
			{
				PreparedStatement statement = con.prepareStatement(selectQuery);
				ResultSet rset = statement.executeQuery();
				while (rset.next())
				{
					Integer shopId = rset.getInt("id");
					// A GM shop has no NPC
					Integer npcId = rset.getInt("npc_template_id");
					for (Integer i : list)
					{
						if (i.equals(shopId) && !NpcIds.contains(npcId))
						{
							NpcIds.add(npcId);
						}
					}
				}
				rset.close();
				statement.close();
				
			}
			
		}
		catch (Exception e)
		{
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
		
		return NpcIds;
		
	}
	
	String editItemText(int itemId)
	{
		String message = "";
		try
		{
			// The columns of the three template tables that the editor shows
			String[] SQL_ITEM_SELECTS =
					{
							"id, name, is_crystallizable, item_type, weight, consume_type, material, crystal_type, shadow_mana, price, crystal_count, is_sellable, is_droppable, is_destroyable, is_tradable",
							
							"id, name, body_part, is_crystallizable, armor_type, weight,"
									+ " material, crystal_type, evasion_modifier, shadow_mana, physical_defense, magic_defense, mp_bonus,"
									+ " price, crystal_count, is_sellable, is_droppable, is_destroyable, is_tradable, item_skills",
							
							"id, name, body_part, is_crystallizable, weight, soulshot_count, spiritshot_count,"
									+ " material, crystal_type, physical_damage, random_damage, weapon_type, critical_rate, accuracy_modifier, evasion_modifier,"
									+ " shield_defense, shield_defense_rate, attack_speed, mp_consumption, magic_damage, shadow_mana, price, crystal_count,"
									+ " is_sellable, is_droppable, is_destroyable, is_tradable, item_skills, enchant4_skills, on_cast_skills,"
									+ " on_critical_skills, change_weapon_template_id" };
			
			L2Item item = ItemTable.getInstance().getTemplate(itemId);
			
			if (item instanceof L2Armor)
			{
				return editArmor(makeQuery("armor_template", SQL_ITEM_SELECTS[1]), (L2Armor)item);
			}
			else if (item instanceof L2Weapon)
			{
				return editWeapon(makeQuery("weapon_template", SQL_ITEM_SELECTS[2]), (L2Weapon)item);
			}
			else if (item instanceof L2EtcItem)
			{
				return editEtcItem(makeQuery("etc_item_template", SQL_ITEM_SELECTS[0]), (L2EtcItem)item);
			}
			
		}
		catch (Exception e)
		{
		}
		
		return (message.isEmpty()) ? "<center><font color=\"FF0000\">Invalid Edit Command</font></center>" : "";
	}
	
	/** The text the editor shows for a template table: the table and the columns of its rows. It is not executed. */
	private String makeQuery(String table, String columns)
	{
		return table + " (" + columns + ")";
	}
	
	/**
	 * @param s
	 * @param item
	 */
	String editWeapon(String s, L2Weapon item)
	{
		return s;
	}
	
	/**
	 * @param s
	 * @param item
	 */
	String editArmor(String s, L2Armor item)
	{
		return s;
	}
	
	/**
	 * @param s
	 * @param item
	 */
	String editEtcItem(String s, L2EtcItem item)
	{
		return s;
	}
}
