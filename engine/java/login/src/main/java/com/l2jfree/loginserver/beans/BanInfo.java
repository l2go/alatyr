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
package com.l2jfree.loginserver.beans;

import com.l2jfree.tools.network.SubNet;

/**
 * 
 * A class that represent the information for a ban
 *
 */
public class BanInfo
{
	/**
	 * The IP address banned
	 */
	private final SubNet _net;
	
	/**
	 * expiration of the ban : represent the difference, measured in milliseconds, between the expiration time of the ban and midnight, January 1, 1970 UTC.
	 */
	private final long _expiration;
	
	/**
	 * Constructor
	 * @param ipAddress
	 * @param expiration
	 */
	public BanInfo(SubNet net, long expiration)
	{
		_net = net;
		_expiration = expiration;
	}
	
	public SubNet getNet()
	{
		return _net;
	}
	
	/**
	 * check if the ban is eternal (equal to 0)
	 * @return true or false
	 */
	public boolean isBanEternal()
	{
		return _expiration == 0;
	}
	
	/**
	 * Check if ban expired : current time > _expiration
	 * @return true if ban is expired
	 */
	public boolean hasExpired()
	{
		return System.currentTimeMillis() > _expiration;
	}
	
	public long getExpiry()
	{
		return _expiration;
	}
}
