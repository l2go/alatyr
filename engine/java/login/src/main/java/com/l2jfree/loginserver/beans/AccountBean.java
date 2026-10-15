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

import java.io.Serializable;
import java.math.BigDecimal;
import java.security.MessageDigest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.loginserver.security.PasswordHasher;
import com.l2jfree.tools.codec.Base64;

/**
 * A class for convenient table entry management.<BR>
 * Use instead of Accounts.class only if you want L2J table compatibility.<BR>
 * It doesn't affect cross-serverpack compatibility at all.
 * @author savormix
 */
public class AccountBean implements Serializable
{
	private static final Logger _log = LoggerFactory.getLogger(AccountBean.class);
	private static final long serialVersionUID = 4402116860273590029L;
	
	private String login;
	private String password;
	private BigDecimal lastactive;
	private Integer accessLevel;
	private Integer lastServerId;
	private String lastIp;
	
	/** Default constructor? */
	public AccountBean()
	{
	}
	
	/**
	 * Simple constructor - self explanatory
	 * @param login
	 */
	public AccountBean(String login)
	{
		this.login = login;
	}
	
	/**
	 * Full constructor - self explanatory
	 * @param login
	 * @param password
	 * @param lastactive
	 * @param accessLevel
	 * @param lastServerId
	 */
	public AccountBean(String login, String password, BigDecimal lastactive, Integer accessLevel, Integer lastServerId,
			String lastIp)
	{
		this(login);
		this.password = password;
		this.lastactive = lastactive;
		this.accessLevel = accessLevel;
		this.lastServerId = lastServerId;
		this.lastIp = lastIp;
	}
	
	public final String getLogin()
	{
		return login;
	}
	
	public final void setLogin(String login)
	{
		this.login = login;
	}
	
	public final String getPassword()
	{
		return password;
	}
	
	public final void setPassword(String password)
	{
		this.password = password;
	}
	
	public final void setPlainPassword(String password)
	{
		this.password = PasswordHasher.hash(password);
	}
	
	public final BigDecimal getLastactive()
	{
		return lastactive;
	}
	
	public final void setLastactive(BigDecimal lastactive)
	{
		this.lastactive = lastactive;
	}
	
	public final Integer getAccessLevel()
	{
		return accessLevel;
	}
	
	public final void setAccessLevel(Integer accessLevel)
	{
		this.accessLevel = accessLevel;
	}
	
	public final Integer getLastServerId()
	{
		return lastServerId;
	}
	
	public final void setLastServerId(Integer lastServerId)
	{
		this.lastServerId = lastServerId;
	}
	
	public final String getLastIp()
	{
		return lastIp;
	}
	
	public final void setLastIp(String lastIp)
	{
		this.lastIp = lastIp;
	}
}
