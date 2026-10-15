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
package com.l2jfree.gameserver.model.entity;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.sql.Types;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.idfactory.IdFactory;

/**
 * @author evill33t
 * 
 */
public class Couple
{
	private static final Logger _log = LoggerFactory.getLogger(Couple.class);
	
	// =========================================================
	// Data Field
	private int _id = 0;
	private int _player1Id = 0;
	private int _player2Id = 0;
	private boolean _maried = false;
	private long _affiancedDate;
	private long _weddingDate;
	
	// =========================================================
	// Constructor
	public Couple(int coupleId)
	{
		_id = coupleId;
		
		Connection con = null;
		try
		{
			PreparedStatement statement;
			ResultSet rs;
			
			con = L2DatabaseFactory.getInstance().getConnection(con);
			
			statement = con.prepareStatement("SELECT player1_id, player2_id, is_married, engaged_at, married_at FROM couple WHERE id = ?");
			statement.setInt(1, _id);
			rs = statement.executeQuery();
			
			while (rs.next())
			{
				_player1Id = rs.getInt("player1_id");
				_player2Id = rs.getInt("player2_id");
				_maried = rs.getBoolean("is_married");
				
				_affiancedDate = rs.getTimestamp("engaged_at").getTime();
				// NULL while only engaged: the wedding date is then the engagement date, as it was in memory before
				Timestamp marriedAt = rs.getTimestamp("married_at");
				_weddingDate = marriedAt == null ? _affiancedDate : marriedAt.getTime();
			}
			statement.close();
		}
		catch (Exception e)
		{
			_log.error("Exception: Couple.load(): " + e.getMessage(), e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	public Couple(L2Player player1, L2Player player2)
	{
		int _tempPlayer1Id = player1.getObjectId();
		int _tempPlayer2Id = player2.getObjectId();
		
		_player1Id = _tempPlayer1Id;
		_player2Id = _tempPlayer2Id;
		
		_affiancedDate = System.currentTimeMillis();
		_weddingDate = System.currentTimeMillis();
		
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement;
			_id = IdFactory.getInstance().getNextId();
			statement =
					con.prepareStatement("INSERT INTO couple (id, player1_id, player2_id, is_married, engaged_at, married_at) VALUES (?, ?, ?, ?, ?, ?)");
			statement.setInt(1, _id);
			statement.setInt(2, _player1Id);
			statement.setInt(3, _player2Id);
			statement.setBoolean(4, false);
			statement.setTimestamp(5, new Timestamp(_affiancedDate));
			statement.setNull(6, Types.TIMESTAMP_WITH_TIMEZONE); // NULL while only engaged
			statement.execute();
			statement.close();
		}
		catch (Exception e)
		{
			_log.error("", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	public void marry()
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement;
			
			statement = con.prepareStatement("UPDATE couple SET is_married = ?, married_at = ? WHERE id = ?");
			statement.setBoolean(1, true);
			_weddingDate = System.currentTimeMillis();
			statement.setTimestamp(2, new Timestamp(_weddingDate));
			statement.setInt(3, _id);
			statement.execute();
			statement.close();
			_maried = true;
		}
		catch (Exception e)
		{
			_log.error("", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	public void divorce()
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement;
			
			statement = con.prepareStatement("DELETE FROM couple WHERE id=?");
			statement.setInt(1, _id);
			statement.execute();
			statement.close();
		}
		catch (Exception e)
		{
			_log.error("Exception: Couple.divorce(): " + e.getMessage(), e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	public final int getId()
	{
		return _id;
	}
	
	public final int getPlayer1Id()
	{
		return _player1Id;
	}
	
	public final int getPlayer2Id()
	{
		return _player2Id;
	}
	
	public final boolean getMaried()
	{
		return _maried;
	}
	
	public final long getAffiancedDate()
	{
		return _affiancedDate;
	}
	
	public final long getWeddingDate()
	{
		return _weddingDate;
	}
}
