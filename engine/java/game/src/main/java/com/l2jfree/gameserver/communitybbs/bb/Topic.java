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
import java.sql.Timestamp;
import java.sql.Types;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.communitybbs.Manager.TopicBBSManager;

public class Topic
{
	
	private final static Logger _log = LoggerFactory.getLogger(Topic.class);
	
	public static final int MORMAL = 0;
	public static final int MEMO = 1;
	
	private final int _id;
	private final int _forumId;
	private final String _topicName;
	private final long _date;
	private final String _ownerName;
	private final int _ownerId;
	private final int _type;
	private final int _cReply;
	
	/**
	 * @param restaure
	 * @param i
	 * @param j
	 * @param string
	 * @param k
	 * @param string2
	 * @param l
	 * @param m
	 * @param n
	 */
	public Topic(ConstructorType ct, int id, int fid, String name, long date, String oname, int oid, int type,
			int Creply)
	{
		_id = id;
		_forumId = fid;
		_topicName = name;
		_date = date;
		_ownerName = oname;
		_ownerId = oid;
		_type = type;
		_cReply = Creply;
		TopicBBSManager.getInstance().addTopic(this);
		
		if (ct == ConstructorType.CREATE)
		{
			
			insertindb();
		}
	}
	
	/**
	 *
	 */
	public void insertindb()
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement =
					con.prepareStatement("INSERT INTO forum_topic (topic_number, forum_id, name, created_at, author_name, author_player_id, kind) VALUES (?,?,?,?,?,?,?)");
			statement.setInt(1, _id);
			statement.setInt(2, _forumId);
			statement.setString(3, _topicName);
			statement.setTimestamp(4, new Timestamp(_date));
			statement.setString(5, _ownerName);
			if (_ownerId != 0)
				statement.setInt(6, _ownerId);
			else
				statement.setNull(6, Types.INTEGER);
			statement.setInt(7, _type);
			statement.execute();
			statement.close();
			
		}
		catch (Exception e)
		{
			_log.warn("error while saving new Topic to db ", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	public enum ConstructorType
	{
		RESTORE,
		CREATE
	}
	
	/**
	 * @return
	 */
	public int getID()
	{
		return _id;
	}
	
	public int getForumID()
	{
		return _forumId;
	}
	
	/**
	 * @return
	 */
	public String getName()
	{
		// TODO Auto-generated method stub
		return _topicName;
	}
	
	public String getOwnerName()
	{
		// TODO Auto-generated method stub
		return _ownerName;
	}
	
	/**
	 *
	 */
	public void deleteme(Forum f)
	{
		TopicBBSManager.getInstance().delTopic(this);
		f.rmTopicByID(getID());
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement =
					con.prepareStatement("DELETE FROM forum_topic WHERE topic_number = ? AND forum_id = ?");
			statement.setInt(1, getID());
			statement.setInt(2, f.getID());
			statement.execute();
			statement.close();
		}
		catch (Exception e)
		{
			_log.error(e.getMessage(), e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	/**
	 * @return
	 */
	public long getDate()
	{
		return _date;
	}
}
