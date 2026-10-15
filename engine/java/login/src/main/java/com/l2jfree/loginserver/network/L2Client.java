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
package com.l2jfree.loginserver.network;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.interfaces.RSAPrivateKey;
import java.util.ArrayDeque;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.Channel;

import com.l2jfree.contract.SessionKey;
import com.l2jfree.loginserver.crypt.LoginCrypt;
import com.l2jfree.loginserver.manager.LoginManager;
import com.l2jfree.loginserver.network.packets.L2ClientPacket;
import com.l2jfree.loginserver.network.packets.L2ServerPacket;
import com.l2jfree.loginserver.network.packets.server.LoginFail;
import com.l2jfree.loginserver.network.packets.server.PlayFail;
import com.l2jfree.network.Connection;
import com.l2jfree.network.NetworkServer;
import com.l2jfree.tools.math.ScrambledKeyPair;
import com.l2jfree.tools.random.Rnd;

/**
 * Represents a client connected into the LoginServer
 * 
 * @author KenM
 */
public final class L2Client extends Connection<L2Client, L2ClientPacket, L2ServerPacket>
{
	private static final Logger _log = LoggerFactory.getLogger(L2Client.class);
	
	public static enum LoginClientState
	{
		CONNECTED,
		AUTHED_GG,
		AUTHED_LOGIN;
	}
	
	// the packet threads (virtual threads) and the I/O thread of the connection read and write these
	private volatile LoginClientState _state = LoginClientState.CONNECTED;
	
	// Crypt
	private LoginCrypt _loginCrypt;
	private final ScrambledKeyPair _scrambledPair;
	private final byte[] _blowfishKey;
	
	private volatile String _account;
	private int _accessLevel;
	private int _lastServerId;
	private int _age;
	private volatile SessionKey _sessionKey;
	private final int _sessionId = Rnd.nextInt(Integer.MAX_VALUE);
	private volatile boolean _joinedGS;
	
	/** Set when the connection is gone, so that a packet that finishes later can undo what it registered. */
	private volatile boolean _disconnected;
	
	/** The packets of this client wait here, so that they run one after the other, as the client sent them. */
	private final ArrayDeque<Runnable> _packets = new ArrayDeque<Runnable>();
	private boolean _packetsRunning;
	private final String _ip;
	
	private boolean _card;
	
	public L2Client(NetworkServer<L2Client, L2ClientPacket, L2ServerPacket> networkServer, Channel channel)
	{
		super(networkServer, channel);
		
		_ip = getInetAddress().getHostAddress();
		
		_scrambledPair = LoginManager.getInstance().getScrambledRSAKeyPair();
		_blowfishKey = LoginManager.getInstance().getBlowfishKey();
	}
	
	private LoginCrypt getLoginCrypt()
	{
		if (_loginCrypt == null)
		{
			_loginCrypt = new LoginCrypt();
			_loginCrypt.setKey(_blowfishKey);
		}
		
		return _loginCrypt;
	}
	
	public String getIp()
	{
		return _ip;
	}
	
	@Override
	public boolean decrypt(ByteBuffer buf, int size)
	{
		boolean ret = false;
		try
		{
			ret = getLoginCrypt().decrypt(buf.array(), buf.position(), size);
		}
		catch (IOException e)
		{
			e.printStackTrace();
			closeNow();
			return false;
		}
		
		if (!ret)
		{
			byte[] dump = new byte[size];
			System.arraycopy(buf.array(), buf.position(), dump, 0, size);
			_log.warn("Wrong checksum from client: " + toString());
			closeNow();
		}
		
		return ret;
	}
	
	@Override
	public boolean encrypt(ByteBuffer buf, int size)
	{
		final int offset = buf.position();
		try
		{
			size = getLoginCrypt().encrypt(buf.array(), offset, size);
		}
		catch (IOException e)
		{
			e.printStackTrace();
			return false;
		}
		
		buf.position(offset + size);
		return true;
	}
	
	public LoginClientState getState()
	{
		return _state;
	}
	
	public void setState(LoginClientState state)
	{
		_state = state;
	}
	
	public byte[] getBlowfishKey()
	{
		return _blowfishKey;
	}
	
	public byte[] getScrambledModulus()
	{
		return _scrambledPair.getScrambledModulus();
	}
	
	public RSAPrivateKey getRSAPrivateKey()
	{
		return (RSAPrivateKey)_scrambledPair.getPair().getPrivate();
	}
	
	public String getAccount()
	{
		return _account;
	}
	
	public void setAccount(String account)
	{
		_account = account;
	}
	
	public void setAccessLevel(int accessLevel)
	{
		_accessLevel = accessLevel;
	}
	
	public int getAccessLevel()
	{
		return _accessLevel;
	}
	
	public void setLastServerId(int lastServerId)
	{
		_lastServerId = lastServerId;
	}
	
	public int getLastServerId()
	{
		return _lastServerId;
	}
	
	public void setAge(int year, int month, int day)
	{
		Calendar dateOfBirth = new GregorianCalendar(year, month - 1, day);
		Calendar today = Calendar.getInstance();
		int age = today.get(Calendar.YEAR) - dateOfBirth.get(Calendar.YEAR);
		dateOfBirth.add(Calendar.YEAR, age);
		if (today.before(dateOfBirth))
			age--;
		
		_age = age;
	}
	
	public int getAge()
	{
		return _age;
	}
	
	public int getSessionId()
	{
		return _sessionId;
	}
	
	public void setSessionKey(SessionKey sessionKey)
	{
		_sessionKey = sessionKey;
	}
	
	public boolean hasJoinedGS()
	{
		return _joinedGS;
	}
	
	public void setJoinedGS(boolean val)
	{
		_joinedGS = val;
	}
	
	public SessionKey getSessionKey()
	{
		return _sessionKey;
	}
	
	/**
	 * @return true when the pair is the login-ok pair of the session key of this client
	 */
	public boolean hasLoginPair(int loginOk1, int loginOk2)
	{
		return _sessionKey != null && _sessionKey.loginOk1() == loginOk1 && _sessionKey.loginOk2() == loginOk2;
	}
	
	public void closeLogin(int reason)
	{
		close(new LoginFail(reason));
	}
	
	public void closeLoginGame(int reason)
	{
		close(new PlayFail(reason));
	}
	
	public void closeBanned()
	{
		close(new LoginFail(getAccessLevel(), true));
	}
	
	public void closeBanned(int timeLeft)
	{
		closeLogin(LoginFail.REASON_IP_RESTRICTED);
	}
	
	public boolean isCardAuthed()
	{
		return _card;
	}
	
	public void setCardAuthed(boolean card)
	{
		_card = card;
	}
	
	@Override
	protected L2ServerPacket getDefaultClosePacket()
	{
		return new LoginFail(LoginFail.REASON_ACCESS_FAILED);
	}
	
	/**
	 * Runs the packets of one client in the order they arrived. Each packet may wait on the database, so they run on
	 * the given executor, but never two of the same client at once.
	 */
	public void executeInOrder(Runnable packet, Executor executor)
	{
		synchronized (_packets)
		{
			_packets.add(packet);
			if (_packetsRunning)
				return;
			_packetsRunning = true;
		}
		
		executor.execute(this::runPackets);
	}
	
	private void runPackets()
	{
		for (;;)
		{
			Runnable packet;
			synchronized (_packets)
			{
				packet = _packets.poll();
				if (packet == null)
				{
					_packetsRunning = false;
					return;
				}
			}
			
			packet.run();
		}
	}
	
	public boolean isDisconnected()
	{
		return _disconnected;
	}
	
	@Override
	public void onDisconnection()
	{
		_disconnected = true;
		
		if (_log.isDebugEnabled())
			_log.info("onDisconnection: " + this);
		
		// If player was not on GS, don't forget to remove it from authed login on LS. Only the entry of this
		// connection: a newer connection of the same account owns its own.
		final String account = getAccount();
		if (account != null && !hasJoinedGS())
		{
			LoginManager.getInstance().removeAuthedLoginClient(account, this);
		}
	}
	
	@Override
	protected void onForcedDisconnection()
	{
		if (_log.isDebugEnabled())
			_log.info("onForcedDisconnection: " + this);
	}
	
	@Override
	public String toString()
	{
		StringBuilder tb = new StringBuilder();
		
		tb.append("[State: ").append(getState());
		
		String ip = getIp();
		if (ip != null)
			tb.append(" | IP: ").append(String.format("%-15s", ip));
		
		String account = getAccount();
		if (account != null)
			tb.append(" | Account: ").append(String.format("%-15s", account));
		
		tb.append("]");
		
		return tb.toString();
	}
	
	@Override
	protected String getUID()
	{
		return getAccount();
	}
}
