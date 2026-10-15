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
package com.l2jfree.gameserver.network;

import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayDeque;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.Channel;

import com.l2jfree.Config;
import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.LoginLink;
import com.l2jfree.contract.SessionKey;
import com.l2jfree.gameserver.ThreadPoolManager;
import com.l2jfree.gameserver.datatables.CharNameTable;
import com.l2jfree.gameserver.datatables.ClanTable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.model.CharSelectInfoPackage;
import com.l2jfree.gameserver.model.clan.L2Clan;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;
import com.l2jfree.gameserver.network.packets.server.LeaveWorld;
import com.l2jfree.gameserver.network.packets.server.ServerClose;
import com.l2jfree.gameserver.threadmanager.FIFORunnableQueue;
import com.l2jfree.network.NetworkServer;
import com.l2jfree.tools.security.BlowFishKeygen;
import com.l2jfree.tools.security.GameCrypt;
import com.l2jfree.util.concurrent.RunnableStatsManager;

/**
 * Represents a client connected on Game Server
 * 
 * @author KenM
 */
public final class L2Client extends com.l2jfree.network.Connection<L2Client, L2ClientPacket, L2ServerPacket>
{
	private static final Logger _log = LoggerFactory.getLogger(L2Client.class);
	
	/**
	 * @author KenM
	 */
	public static enum GameClientState
	{
		CONNECTED, // client has just connected
		AUTHED, // client has authed but doesnt has character attached to it yet
		IN_GAME; // client has selected a char and is in game
	}
	
	private GameClientState _state = GameClientState.CONNECTED;
	private String _accountName;
	private SessionKey _sessionId;
	private L2Player _activeChar;
	private boolean _isAuthedGG;
	private int[] _charSlotMapping;
	private GameCrypt _crypt;
	private volatile boolean _disconnected;
	private String _hostAddress;
	private boolean _protocol;
	
	public L2Client(NetworkServer<L2Client, L2ClientPacket, L2ServerPacket> networkServer, Channel channel)
	{
		super(networkServer, channel);
	}
	
	private GameCrypt getCrypt()
	{
		if (_crypt == null)
			_crypt = new GameCrypt();
		
		return _crypt;
	}
	
	public byte[] enableCrypt()
	{
		byte[] key = BlowFishKeygen.getRandomKey();
		getCrypt().setKey(key);
		return key;
	}
	
	public GameClientState getState()
	{
		return _state;
	}
	
	public void setState(GameClientState pState)
	{
		_state = pState;
	}
	
	@Override
	public boolean decrypt(ByteBuffer buf, int size)
	{
		getCrypt().decrypt(buf.array(), buf.position(), size);
		return true;
	}
	
	@Override
	public boolean encrypt(final ByteBuffer buf, final int size)
	{
		getCrypt().encrypt(buf.array(), buf.position(), size);
		buf.position(buf.position() + size);
		return true;
	}
	
	public L2Player getActiveChar()
	{
		return _activeChar;
	}
	
	public void setActiveChar(L2Player pActiveChar)
	{
		_activeChar = pActiveChar;
	}
	
	public void setGameGuardOk(boolean val)
	{
		_isAuthedGG = val;
	}
	
	public boolean isAuthedGG()
	{
		return _isAuthedGG;
	}
	
	public void setAccountName(String pAccountName)
	{
		_accountName = pAccountName;
	}
	
	public String getAccountName()
	{
		return _accountName;
	}
	
	public void setSessionId(SessionKey sk)
	{
		_sessionId = sk;
	}
	
	public SessionKey getSessionId()
	{
		return _sessionId;
	}
	
	/**
	 * Method to handle character deletion
	 * 
	 * @return a byte:
	 *         <li>-1: Error: No char was found for such charslot, caught exception, etc...
	 *         <li>0: character is not member of any clan, proceed with deletion
	 *         <li>1: character is member of a clan, but not clan leader
	 *         <li>2: character is clan leader
	 */
	public byte markToDeleteChar(int charslot)
	{
		int objid = getObjectIdForSlot(charslot);
		
		if (objid < 0)
			return -1;
		
		byte result = -1;
		
		Connection con = null;
		
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement = con.prepareStatement("SELECT clan_id FROM player WHERE id=?");
			statement.setInt(1, objid);
			ResultSet rs = statement.executeQuery();
			byte answer = -1;
			if (rs.next())
			{
				int clanId = rs.getInt(1);
				if (clanId != 0)
				{
					L2Clan clan = ClanTable.getInstance().getClan(clanId);
					
					if (clan == null)
						answer = 0; // jeezes!
					else if (clan.getLeaderId() == objid)
						answer = 2;
					else
						answer = 1;
				}
				else
					answer = 0;
				
				// Setting delete time
				if (answer == 0)
				{
					if (Config.DELETE_DAYS == 0)
					{
						deleteCharByObjId(objid);
					}
					else
					{
						statement = con.prepareStatement("UPDATE player SET delete_at=? WHERE id=?");
						statement.setTimestamp(1, new Timestamp(System.currentTimeMillis() + Config.DELETE_DAYS * 86400000L)); // 24*60*60*1000 = 86400000
						statement.setInt(2, objid);
						statement.execute();
						statement.close();
					}
				}
			}
			result = answer;
			rs.close();
			statement.close();
		}
		catch (Exception e)
		{
			_log.error("Error updating delete time of character.", e);
			result = -1;
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
		
		return result;
	}
	
	public void markRestoredChar(int charslot) throws Exception
	{
		// have to make sure active character must be nulled
		/*
		 * if (getActiveChar() != null) { saveCharToDisk (getActiveChar()); if
		 * (_log.isDebugEnabled()) _log.debug("active Char saved"); _activeChar
		 * = null; }
		 */
		
		int objid = getObjectIdForSlot(charslot);
		if (objid < 0)
			return;
		
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement = con.prepareStatement("UPDATE player SET delete_at=NULL WHERE id=?");
			statement.setInt(1, objid);
			statement.execute();
			statement.close();
		}
		catch (Exception e)
		{
			_log.error("Error restoring character.", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	public static void deleteCharByObjId(int objid)
	{
		if (objid < 0)
			return;
		
		Connection con = null;
		
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement;
			
			// The tables that belong to the player (items, skills, shortcuts, ...) cascade
			statement = con.prepareStatement("DELETE FROM player WHERE id=?");
			statement.setInt(1, objid);
			statement.execute();
			statement.close();
			
			CharNameTable.getInstance().remove(objid);
		}
		catch (Exception e)
		{
			_log.error("Error deleting character.", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	public L2Player loadCharFromDisk(int charslot)
	{
		return L2Player.load(getObjectIdForSlot(charslot));
	}
	
	/**
	 * @param chars
	 */
	public void setCharSelection(CharSelectInfoPackage[] chars)
	{
		_charSlotMapping = new int[chars.length];
		
		int i = 0;
		for (CharSelectInfoPackage element : chars)
			_charSlotMapping[i++] = element.getObjectId();
	}
	
	/**
	 * @param charslot
	 * @return
	 */
	private int getObjectIdForSlot(int charslot)
	{
		if (_charSlotMapping == null || charslot < 0 || charslot >= _charSlotMapping.length)
		{
			_log.warn(toString() + " tried to delete Character in slot " + charslot
					+ " but no characters exits at that slot.");
			return -1;
		}
		
		return _charSlotMapping[charslot];
	}
	
	@Override
	public String toString()
	{
		StringBuilder tb = new StringBuilder();
		
		tb.append("[State: ").append(getState());
		
		String host = getHostAddress();
		if (host != null)
			tb.append(" | IP: ").append(String.format("%-15s", host));
		
		String account = getAccountName();
		if (account != null)
			tb.append(" | Account: ").append(String.format("%-15s", account));
		
		L2Player player = getActiveChar();
		if (player != null)
			tb.append(" | Character: ").append(String.format("%-15s", player.getName()));
		
		tb.append("]");
		
		return tb.toString();
	}
	
	public boolean isProtocolOk()
	{
		return _protocol;
	}
	
	public void setProtocolOk(boolean b)
	{
		_protocol = b;
	}
	
	public String getHostAddress()
	{
		if (_hostAddress == null || _hostAddress.isEmpty())
			return getInetAddress().getHostAddress();
		
		return _hostAddress;
	}
	
	public void setHostAddress(String hostAddress)
	{
		_hostAddress = hostAddress;
	}
	
	boolean isDisconnected()
	{
		return _disconnected;
	}
	
	void setDisconnected()
	{
		LoginLink.getInstance().sendLogout(getAccountName(), this);
		
		_disconnected = true;
	}
	
	void execute(L2ClientPacket rp)
	{
		getPacketQueue().execute(rp);
	}
	
	private volatile FIFORunnableQueue<Runnable> _packetQueue;
	
	public FIFORunnableQueue<Runnable> getPacketQueue()
	{
		FIFORunnableQueue<Runnable> queue = _packetQueue;
		if (queue == null)
		{
			synchronized (this)
			{
				queue = _packetQueue;
				if (queue == null)
					_packetQueue = queue = new FIFORunnableQueue<Runnable>() {};
			}
		}
		
		return queue;
	}
	
	private final class ServerPacketQueue extends ArrayDeque<L2ServerPacket> implements Runnable
	{
		private static final long serialVersionUID = 6715576112277597425L;
		
		public ServerPacketQueue()
		{
			ThreadPoolManager.getInstance().schedule(this, Config.ENTERWORLD_TICK);
		}
		
		@Override
		public void run()
		{
			synchronized (L2Client.this)
			{
				if (_serverPacketQueue != this)
					return;
				
				if (isEmpty())
				{
					_serverPacketQueue = null;
					return;
				}
				
				int pn = size();
				if (pn > Config.ENTERWORLD_PPT)
					pn = Config.ENTERWORLD_PPT;
				
				for (int i = 0; i < pn; i++)
				{
					try
					{
						sendPacketImpl(removeFirst());
					}
					catch (Exception e)
					{
						_log.error("GDQ noob error (report in forum!) i=" + i + ", pn=" + pn, e);
					}
				}
				
				ThreadPoolManager.getInstance().schedule(this, Config.ENTERWORLD_TICK);
			}
		}
	}
	
	private ServerPacketQueue _serverPacketQueue;
	
	@Override
	public void sendPacket(L2ServerPacket sp)
	{
		if (_serverPacketQueue != null)
		{
			synchronized (this)
			{
				if (_serverPacketQueue != null)
				{
					_serverPacketQueue.add(sp);
					return;
				}
			}
		}
		
		sendPacketImpl(sp);
	}
	
	public void initServerPacketQueue()
	{
		if (!Config.ENTERWORLD_QUEUING)
			return;
		
		if (_serverPacketQueue == null)
		{
			synchronized (this)
			{
				if (_serverPacketQueue == null)
				{
					_serverPacketQueue = new ServerPacketQueue();
				}
			}
		}
	}
	
	/**
	 * {@link RunnableStatsManager} used here mostly for counting, since constructors - usually the longest parts - are
	 * excluded.
	 */
	private void sendPacketImpl(L2ServerPacket sp)
	{
		final long begin = System.nanoTime();
		final L2Player activeChar = getActiveChar();
		
		try
		{
			if (isDisconnected())
				return;
			
			if (!sp.canBeSentTo(this, activeChar))
				return;
			
			sp.prepareToSend(this, activeChar);
			
			super.sendPacket(sp);
			
			sp.packetSent(this, activeChar);
		}
		finally
		{
			RunnableStatsManager.handleStats(sp.getClass(), "runImpl()", System.nanoTime() - begin);
		}
	}
	
	void close(boolean toLoginScreen)
	{
		super.close(toLoginScreen ? ServerClose.STATIC_PACKET : LeaveWorld.STATIC_PACKET);
		
		setDisconnected();
	}
	
	@Override
	public void close(L2ServerPacket sp)
	{
		new Disconnection(this).defaultSequence(false);
	}
	
	@Override
	public void closeNow()
	{
		new Disconnection(this).defaultSequence(false);
	}
	
	@Override
	protected L2ServerPacket getDefaultClosePacket()
	{
		return LeaveWorld.STATIC_PACKET;
	}
	
	@Override
	protected void onDisconnection()
	{
		new Disconnection(this).onDisconnection();
		
		setDisconnected();
	}
	
	@Override
	protected void onForcedDisconnection()
	{
		if (_log.isDebugEnabled())
			_log.info("Client " + toString() + " disconnected abnormally.");
	}
	
	@Override
	protected String getUID()
	{
		return getAccountName();
	}
}
