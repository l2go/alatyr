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

import java.net.InetAddress;

public class FailedLoginAttempt
{
	// private InetAddress _ipAddress;
	private int _count;
	
	private long _lastAttempTime;
	
	private String _lastPassword;
	
	public FailedLoginAttempt(InetAddress address, String lastPassword)
	{
		// _ipAddress = address;
		_count = 1;
		_lastAttempTime = System.currentTimeMillis();
		_lastPassword = lastPassword;
	}
	
	public synchronized void increaseCounter(String password)
	{
		if (!_lastPassword.equals(password))
		{
			// check if theres a long time since last wrong try
			if (System.currentTimeMillis() - _lastAttempTime < 300 * 1000)
			{
				_count++;
			}
			else
			{
				// restart the status
				_count = 1;
				
			}
			_lastPassword = password;
			_lastAttempTime = System.currentTimeMillis();
		}
		else
		// trying the same password is not brute force
		{
			_lastAttempTime = System.currentTimeMillis();
		}
	}
	
	public synchronized int getCount()
	{
		return _count;
	}
}
