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
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.communitybbs.Manager.PostBBSManager;

/**
 * @author Maktakien
 *
 */
public class Post
{
	private final static Logger _log = LoggerFactory.getLogger(Post.class);
	
	public class CPost
	{
		public int postId;
		public String postOwner;
		public int postOwnerId;
		public long postDate;
		public int postTopicId;
		public int postForumId;
		public String postTxt;
	}
	
	private final List<CPost> _post;
	
	/**
	 * @param restore
	 * @param t
	 */
	//public enum ConstructorType {REPLY, CREATE };
	public Post(String _PostOwner, int _PostOwnerID, long date, int tid, int _PostForumID, String txt)
	{
		_post = new ArrayList<CPost>();
		CPost cp = new CPost();
		cp.postId = 0;
		cp.postOwner = _PostOwner;
		cp.postOwnerId = _PostOwnerID;
		cp.postDate = date;
		cp.postTopicId = tid;
		cp.postForumId = _PostForumID;
		cp.postTxt = txt;
		_post.add(cp);
		insertindb(cp);
		
	}
	
	public void insertindb(CPost cp)
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement =
					con.prepareStatement("INSERT INTO forum_post (post_number, author_name, author_player_id, posted_at, topic_number, forum_id, body) VALUES (?,?,?,?,?,?,?)");
			statement.setInt(1, cp.postId);
			statement.setString(2, cp.postOwner);
			if (cp.postOwnerId != 0)
				statement.setInt(3, cp.postOwnerId);
			else
				statement.setNull(3, Types.INTEGER);
			statement.setTimestamp(4, new Timestamp(cp.postDate));
			statement.setInt(5, cp.postTopicId);
			statement.setInt(6, cp.postForumId);
			statement.setString(7, cp.postTxt);
			statement.execute();
			statement.close();
		}
		catch (Exception e)
		{
			_log.warn("error while saving new Post to db ", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	public Post(Topic t)
	{
		_post = new ArrayList<CPost>();
		load(t);
	}
	
	public CPost getCPost(int id)
	{
		int i = 0;
		for (CPost cp : _post)
		{
			if (i++ == id)
			{
				return cp;
			}
		}
		return null;
	}
	
	public void deleteme(Topic t)
	{
		PostBBSManager.getInstance().delPostByTopic(t);
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement =
					con.prepareStatement("DELETE FROM forum_post WHERE forum_id = ? AND topic_number = ?");
			statement.setInt(1, t.getForumID());
			statement.setInt(2, t.getID());
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
	 * @param t
	 */
	private void load(Topic t)
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement =
					con.prepareStatement("SELECT post_number, author_name, author_player_id, posted_at, topic_number, forum_id, body FROM forum_post WHERE forum_id = ? AND topic_number = ? ORDER BY post_number ASC");
			statement.setInt(1, t.getForumID());
			statement.setInt(2, t.getID());
			ResultSet result = statement.executeQuery();
			while (result.next())
			{
				CPost cp = new CPost();
				cp.postId = result.getInt("post_number");
				cp.postOwner = result.getString("author_name");
				cp.postOwnerId = result.getInt("author_player_id");
				cp.postDate = result.getTimestamp("posted_at").getTime();
				cp.postTopicId = result.getInt("topic_number");
				cp.postForumId = result.getInt("forum_id");
				cp.postTxt = result.getString("body");
				_post.add(cp);
			}
			result.close();
			statement.close();
		}
		catch (Exception e)
		{
			_log.warn("data error on Post " + t.getForumID() + "/" + t.getID() + " : ", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	/**
	 * @param i
	 */
	public void updatetxt(int i)
	{
		Connection con = null;
		try
		{
			CPost cp = getCPost(i);
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement =
					con.prepareStatement("UPDATE forum_post SET body = ? WHERE post_number = ? AND topic_number = ? AND forum_id = ?");
			statement.setString(1, cp.postTxt);
			statement.setInt(2, cp.postId);
			statement.setInt(3, cp.postTopicId);
			statement.setInt(4, cp.postForumId);
			statement.execute();
			statement.close();
		}
		catch (Exception e)
		{
			_log.warn("error while saving new Post to db ", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
}
