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
package com.l2jfree.gameserver.communitybbs.bb;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.communitybbs.Manager.ForumsBBSManager;
import com.l2jfree.gameserver.communitybbs.Manager.TopicBBSManager;

public class Forum
{
	//type
	public static final int ROOT = 0;
	public static final int NORMAL = 1;
	public static final int CLAN = 2;
	public static final int MEMO = 3;
	public static final int MAIL = 4;
	//perm
	public static final int INVISIBLE = 0;
	public static final int ALL = 1;
	public static final int CLANMEMBERONLY = 2;
	public static final int OWNERONLY = 3;
	
	private final static Logger _log = LoggerFactory.getLogger(Forum.class);
	
	private final List<Forum> _children;
	private final Map<Integer, Topic> _topic;
	private final int _forumId;
	private String _forumName;
	//private int _ForumParent;
	private int _forumType;
	private int _forumPerm;
	private final Forum _fParent;
	private int _ownerID;
	private boolean _loaded = false;
	
	/**
	 * @param Forumid
	 * @param FParent
	 */
	public Forum(int Forumid, Forum FParent)
	{
		_forumId = Forumid;
		_fParent = FParent;
		_children = new ArrayList<Forum>();
		_topic = new LinkedHashMap<Integer, Topic>();
		
		/*load();
		getChildren();	*/
	}
	
	/**
	 * @param name
	 * @param parent
	 * @param type
	 * @param perm
	 */
	public Forum(String name, Forum parent, int type, int perm, int OwnerID)
	{
		_forumName = name;
		_forumId = ForumsBBSManager.getInstance().getANewID();
		//_ForumParent = parent.getID();
		_forumType = type;
		_forumPerm = perm;
		_fParent = parent;
		_ownerID = OwnerID;
		_children = new ArrayList<Forum>();
		_topic = new LinkedHashMap<Integer, Topic>();
		parent._children.add(this);
		ForumsBBSManager.getInstance().addForum(this);
		_loaded = true;
	}
	
	/**
	 *
	 */
	private void load()
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement = con.prepareStatement("SELECT name, kind, access, COALESCE(owner_player_id, owner_clan_id) AS owner_id FROM forum WHERE id = ?");
			statement.setInt(1, _forumId);
			ResultSet result = statement.executeQuery();
			
			if (result.next())
			{
				_forumName = result.getString("name");
				//_ForumParent = result.getInt("forum_parent"));
				_forumType = result.getInt("kind");
				_forumPerm = result.getInt("access");
				_ownerID = result.getInt("owner_id");
			}
			result.close();
			statement.close();
		}
		catch (Exception e)
		{
			_log.warn("data error on Forum " + _forumId + " : ", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
		
		con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement =
					con.prepareStatement("SELECT forum_id, topic_number, name, created_at, author_name, author_player_id, kind FROM forum_topic WHERE forum_id = ? ORDER BY topic_number DESC");
			statement.setInt(1, _forumId);
			ResultSet result = statement.executeQuery();
			
			while (result.next())
			{
				Topic t =
						new Topic(Topic.ConstructorType.RESTORE, result.getInt("topic_number"),
								result.getInt("forum_id"), result.getString("name"),
								result.getTimestamp("created_at").getTime(), result.getString("author_name"),
								result.getInt("author_player_id"), result.getInt("kind"), 0);
				_topic.put(t.getID(), t);
				if (t.getID() > TopicBBSManager.getInstance().getMaxID(this))
				{
					TopicBBSManager.getInstance().setMaxID(t.getID(), this);
				}
			}
			result.close();
			statement.close();
		}
		catch (Exception e)
		{
			_log.warn("data error on Forum " + _forumId + " : ", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	/**
	 *
	 */
	private void getChildren()
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement = con.prepareStatement("SELECT id FROM forum WHERE parent_forum_id = ? ORDER BY id");
			statement.setInt(1, _forumId);
			ResultSet result = statement.executeQuery();
			
			while (result.next())
			{
				Forum f = new Forum(result.getInt("id"), this);
				_children.add(f);
				ForumsBBSManager.getInstance().addForum(f);
			}
			result.close();
			statement.close();
		}
		catch (Exception e)
		{
			_log.warn("data error on Forum (children): ", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	public int getTopicSize()
	{
		vload();
		return _topic.size();
	}
	
	public Topic getTopic(int j)
	{
		vload();
		return _topic.get(j);
	}
	
	public void addTopic(Topic t)
	{
		vload();
		_topic.put(t.getID(), t);
	}
	
	/**
	* @return
	*/
	public int getID()
	{
		return _forumId;
	}
	
	public String getName()
	{
		vload();
		return _forumName;
	}
	
	public int getType()
	{
		vload();
		return _forumType;
	}
	
	/**
	 * @param name
	 * @return
	 */
	public Forum getChildByName(String name)
	{
		vload();
		for (Forum f : _children)
		{
			if (f.getName().equals(name))
			{
				return f;
			}
		}
		return null;
	}
	
	/**
	 * @param id
	 */
	public void rmTopicByID(int id)
	{
		_topic.remove(id);
		
	}
	
	/**
	 *
	 */
	public void insertIntoDb()
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement =
					con.prepareStatement("INSERT INTO forum (id, name, parent_forum_id, kind, access, owner_player_id, owner_clan_id) VALUES (?,?,?,?,?,?,?)");
			statement.setInt(1, _forumId);
			statement.setString(2, _forumName);
			statement.setInt(3, _fParent.getID());
			statement.setInt(4, _forumType);
			statement.setInt(5, _forumPerm);
			// A memo or mail forum belongs to a player, a clan forum to a clan, other forums to nobody.
			if (_ownerID != 0 && (_forumType == MEMO || _forumType == MAIL))
				statement.setInt(6, _ownerID);
			else
				statement.setNull(6, Types.INTEGER);
			if (_ownerID != 0 && _forumType == CLAN)
				statement.setInt(7, _ownerID);
			else
				statement.setNull(7, Types.INTEGER);
			statement.execute();
			statement.close();
		}
		catch (Exception e)
		{
			_log.warn("error while saving new Forum to db ", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	/**
	 *
	 */
	public void vload()
	{
		if (!_loaded)
		{
			load();
			getChildren();
			_loaded = true;
		}
	}
	
	/**
	 * @return
	 */
	
}
