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
package com.l2jfree.loginserver.manager;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.loginserver.beans.BanInfo;
import com.l2jfree.tools.network.SubNet;

/**
 * This class manage ban list
 * 
 * Ban list is stored in a file BAN_LIST before the startup. The BanManager load this file on startup and store for each ban
 * the ip and the expiration time (the date when this ban is finished). If the expiration is 0, the ban is eternal.
 * 
 */
public class BanManager
{
	private static final class SingletonHolder
	{
		private static final BanManager INSTANCE = new BanManager();
	}
	
	public static BanManager getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	private static final Logger _log = LoggerFactory.getLogger(BanManager.class);
	/** Banned ips */
	private final Map<SubNet, BanInfo> _bannedIps = new ConcurrentHashMap<SubNet, BanInfo>();
	private final Map<SubNet, BanInfo> _restrictedIps = new ConcurrentHashMap<SubNet, BanInfo>();
	
	public static String BAN_LIST = "config/banned_ip.cfg";
	private static final String ENCODING = "UTF-8";
	
	private BanManager()
	{
		load();
	}
	
	/**
	 * Load banned list
	 *
	 */
	public void load()
	{
		File file = new File(BAN_LIST);
		try
		{
			_bannedIps.clear();
			_restrictedIps.clear();
			// try to read banned list
			List<?> lines = FileUtils.readLines(file, ENCODING);
			
			for (int i = 0; i < lines.size(); i++)
			{
				String line = (String)lines.get(i);
				line = line.trim();
				if (line.length() > 0 && !line.startsWith("#"))
				{
					addBannedIP(line);
				}
			}
			_log.info("BanManager: Loaded.");
			_log.info(" - temporary banned IPs:" + getTempBanCount());
			_log.info(" - forever banned IPs:" + getEternalBanCount());
		}
		catch (IOException e)
		{
			if (!file.isFile())
				_log.info("BanManager: no banned addresses");
			else
				_log.warn("BanManager: Cannot read IP list: ", e);
		}
	}
	
	/**
	 * Store a ban IP in memory.
	 * Read a line, ignore comment and split it to get the IP and the expiration
	 * If no expiration was found, this is a eternal ban
	 * @param line
	 */
	private void addBannedIP(String line)
	{
		String[] parts;
		// split comments if any
		parts = line.split("#");
		
		// discard comments in the line, if any
		line = parts[0];
		
		parts = line.split(" ");
		
		String address = parts[0];
		
		long duration = 0;
		
		if (parts.length > 1)
		{
			try
			{
				duration = Long.parseLong(parts[1]);
			}
			catch (NumberFormatException e)
			{
				_log.warn("Skipped: Incorrect ban duration (" + parts[1] + ") on Line: " + line);
				return;
			}
		}
		
		if (duration == 0 || duration > System.currentTimeMillis())
		{
			if (address.contains("/"))
			{
				addBanForSubnet(address, duration);
			}
			else
			{
				try
				{
					addBanForAddress(address, duration);
				}
				catch (UnknownHostException e)
				{
					_log.warn("Skipped: Invalid address (" + parts[0] + ") on Line: " + line);
				}
			}
		}
	}
	
	/**
	 * Adds the address to the ban list of the login server, with the given duration.
	 * 
	 * @param address The Address to be banned.
	 * @param expiration Timestamp in milliseconds when this ban expires
	 * @throws UnknownHostException if the address is invalid.
	 */
	public void addBanForAddress(String address, long expiration) throws UnknownHostException
	{
		InetAddress netAddress = InetAddress.getByName(address);
		SubNet _net = new SubNet(netAddress.getHostAddress());
		if (expiration != 0)
			_bannedIps.put(_net, new BanInfo(_net, expiration));
		else
			_restrictedIps.put(_net, new BanInfo(_net, expiration));
	}
	
	/**
	 * Adds the network to the ban list of the login server, with the given duration.
	 * 
	 * @param net The Network to be banned.
	 * @param duration is milliseconds
	 */
	public void addBanForSubnet(String address, long duration)
	{
		SubNet _net = new SubNet(address);
		if (duration != 0)
			_bannedIps.put(_net, new BanInfo(_net, System.currentTimeMillis() + duration));
		else
			_restrictedIps.put(_net, new BanInfo(_net, 0));
	}
	
	/**
	 * Adds the address to the ban list of the login server, with the given duration.
	 * 
	 * @param address The Address to be banned.
	 * @param duration is milliseconds
	 */
	public void addBanForAddress(InetAddress address, long duration)
	{
		SubNet _net = new SubNet(address.getHostAddress());
		if (duration != 0)
			_bannedIps.put(_net, new BanInfo(_net, System.currentTimeMillis() + duration));
		else
			_restrictedIps.put(_net, new BanInfo(_net, 0));
	}
	
	public boolean isRestrictedAddress(InetAddress address)
	{
		for (Map.Entry<SubNet, BanInfo> _bannedIP : _restrictedIps.entrySet())
		{
			SubNet net = _bannedIP.getKey();
			
			if (net != null && net.isInSubnet(address.getHostAddress()))
			{
				BanInfo bi = _bannedIP.getValue();
				if (bi != null)
					return true;
			}
		}
		return false;
	}
	
	/**
	 * Check if an IP is banned
	 * 
	 * @param address
	 * @return true if IP is banned or false otherwise
	 */
	public boolean isBannedAddress(InetAddress address)
	{
		Entry<SubNet, BanInfo> bannedIP = getInfo(address);
		if (bannedIP != null)
		{
			if (bannedIP.getValue().hasExpired())
			{
				_bannedIps.remove(bannedIP.getKey());
				return false;
			}
			else
				return true;
		}
		return false;
	}
	
	public long getBanExpiry(InetAddress address)
	{
		try
		{
			return getBanData(address).getExpiry();
		}
		catch (NullPointerException npe)
		{
			return 0;
		}
	}
	
	private BanInfo getBanData(InetAddress address)
	{
		return getInfo(address).getValue();
	}
	
	public Entry<SubNet, BanInfo> getInfo(InetAddress address)
	{
		for (Map.Entry<SubNet, BanInfo> _bannedIP : _bannedIps.entrySet())
		{
			SubNet net = _bannedIP.getKey();
			if (net.isInSubnet(address.getHostAddress()))
				return _bannedIP;
		}
		return null;
	}
	
	/**
	 * get all banned IPs
	 * @return a map of banned IP
	 */
	public Map<SubNet, BanInfo> getBannedIps()
	{
		return _bannedIps;
	}
	
	/**
	 * Remove the specified address from the ban list
	 * @param address The address to be removed from the ban list
	 * @return true if the ban was removed, false if there was no ban for this IP
	 */
	public boolean removeBanForAddress(InetAddress address)
	{
		for (Map.Entry<SubNet, BanInfo> _bannedIP : _bannedIps.entrySet())
		{
			SubNet net = _bannedIP.getKey();
			
			if (net.isInSubnet(address.getHostAddress()) && (net.getMask() == 0xffffffff))
				return _bannedIps.remove(net) != null;
		}
		
		return false;
	}
	
	/**
	 * Remove the specified address from the ban list
	 * @param address The address to be removed from the ban list
	 * @return true if the ban was removed, false if there was no ban for this ip or the address was invalid.
	 */
	public boolean removeBanForAddress(String address)
	{
		try
		{
			return removeBanForAddress(InetAddress.getByName(address));
		}
		catch (UnknownHostException e)
		{
			return false;
		}
	}
	
	public int getEternalBanCount()
	{
		return _restrictedIps.size();
	}
	
	public int getTempBanCount()
	{
		return _bannedIps.size();
	}
}
