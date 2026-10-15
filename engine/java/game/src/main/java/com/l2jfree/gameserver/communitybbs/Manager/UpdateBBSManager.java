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
package com.l2jfree.gameserver.communitybbs.Manager;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.gameobjects.L2Player;

/**
 * @author evill33t
 * 
 */
public class UpdateBBSManager extends BaseBBSManager
{
	private final static Logger _log = LoggerFactory.getLogger(UpdateBBSManager.class);
	
	public class UpdateItem
	{
		public Integer id;
		public String author;
		public String introduction;
		public String text;
		public String udate;
	}
	
	private List<UpdateItem> getChangeLog()
	{
		List<UpdateItem> _items = new ArrayList<UpdateItem>();
		java.sql.Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement = con.prepareStatement("SELECT id, published_on, introduction, body, author FROM changelog_entry ORDER BY id DESC");
			ResultSet result = statement.executeQuery();
			while (result.next())
			{
				UpdateItem it = new UpdateItem();
				it.id = (int) result.getLong("id");
				it.udate = String.valueOf(result.getObject("published_on", LocalDate.class));
				it.introduction = result.getString("introduction");
				it.text = result.getString("body");
				it.author = result.getString("author");
				_items.add(it);
			}
			result.close();
			statement.close();
		}
		catch (Exception e)
		{
			_log.warn("couldnt load changelog", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
		return _items;
	}
	
	private UpdateItem getDetails(int id)
	{
		UpdateItem rit = new UpdateItem();
		for (UpdateItem temp : getChangeLog())
		{
			rit = temp;
			if (temp.id == id)
				break;
		}
		return rit;
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.l2jfree.gameserver.communitybbs.Manager.BaseBBSManager#parsecmd(java
	 * .lang.String, com.l2jfree.gameserver.gameobjects.instance.L2Player)
	 */
	@Override
	public void parsecmd(String command, L2Player activeChar)
	{
		if (command.equals("_bbsupdate_notes"))
		{
			final StringBuilder tb = new StringBuilder();
			tb.append("<html><body><br>");
			
			tb.append("<table border=0 cellspacing=0 cellpadding=2 bgcolor=808080 width=770>");
			tb.append("<tr>");
			tb.append("<td FIXWIDTH=5></td>");
			tb.append("<td FIXWIDTH=150 align=center>Author</td>");
			tb.append("<td FIXWIDTH=460 align=left>Title</td>");
			tb.append("<td FIXWIDTH=150 align=center>Authoring Date</td>");
			tb.append("<td FIXWIDTH=5></td>");
			tb.append("</tr></table>");
			tb.append("<table border=0 cellspacing=0 cellpadding=2 width=770>");
			for (UpdateItem temp : getChangeLog())
			{
				tb.append("<tr>");
				tb.append("<td FIXWIDTH=5></td><td FIXWIDTH=150>").append(temp.author)
						.append("</td><td FIXWIDTH=460><a action=\"bypass _bbsupdate_details;").append(temp.id)
						.append("\">").append(temp.introduction).append("</a></td><td FIXWIDTH=150>")
						.append(temp.udate).append("</td><td FIXWIDTH=5></td>");
				tb.append("</tr>");
			}
			tb.append("</table>");
			tb.append("</body></html>");
			separateAndSend(tb, activeChar);
		}
		else if (command.startsWith("_bbsupdate_details"))
		{
			StringTokenizer st = new StringTokenizer(command, ";");
			st.nextToken();
			int id = Integer.parseInt(st.nextToken());
			UpdateItem it = getDetails(id);
			final StringBuilder tb = new StringBuilder();
			tb.append("<html><body><table border=0 cellspacing=0 cellpadding=2 width=770><tr><td FIXWIDTH=5></td><td><br>");
			tb.append(it.udate).append(" ").append(it.introduction).append("<br><br>");
			tb.append(it.text);
			tb.append("</td><td FIXWIDTH=5></td></tr></table>  <a action=\"bypass _bbsupdate_notes\">Back to Changelog</a><br></body></html>");
			separateAndSend(tb, activeChar);
		}
		else
		{
			notImplementedYet(activeChar, command);
		}
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.l2jfree.gameserver.communitybbs.Manager.BaseBBSManager#parsewrite
	 * (java.lang.String, java.lang.String, java.lang.String, java.lang.String,
	 * java.lang.String,
	 * com.l2jfree.gameserver.gameobjects.instance.L2Player)
	 */
	@Override
	public void parsewrite(String ar1, String ar2, String ar3, String ar4, String ar5, L2Player activeChar)
	{
		// TODO Auto-generated method stub
	}
	
	private static UpdateBBSManager _instance = new UpdateBBSManager();
	
	/**
	 * @return
	 */
	public static UpdateBBSManager getInstance()
	{
		return _instance;
	}
	
}
