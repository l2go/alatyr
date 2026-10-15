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

import java.math.BigDecimal;

/**
 * @author savormix
 *
 */
public final class Accounts extends AccountBean
{
	private static final long serialVersionUID = -3344760382246635879L;
	
	private Integer birthYear;
	private Integer birthMonth;
	private Integer birthDay;
	
	/** Default constructor */
	public Accounts()
	{
		super();
	}
	
	/**
	 * Simple constructor - self explanatory
	 * @param login
	 */
	public Accounts(String login)
	{
		super(login);
	}
	
	/**
	 * Full constructor - self explanatory
	 * @param login
	 * @param password
	 * @param lastactive
	 * @param accessLevel
	 * @param lastServerId
	 * @param birthYear
	 * @param birthMonth
	 * @param birthDay
	 * @param lastIp
	 */
	public Accounts(String login, String password, BigDecimal lastactive, Integer accessLevel, Integer lastServerId,
			Integer birthYear, Integer birthMonth, Integer birthDay, String lastIp)
	{
		super(login, password, lastactive, accessLevel, lastServerId, lastIp);
		this.birthYear = birthYear;
		this.birthMonth = birthMonth;
		this.birthDay = birthDay;
	}
	
	public final Integer getBirthYear()
	{
		return birthYear;
	}
	
	public final void setBirthYear(Integer birthYear)
	{
		this.birthYear = birthYear;
	}
	
	public final Integer getBirthMonth()
	{
		return birthMonth;
	}
	
	public final void setBirthMonth(Integer birthMonth)
	{
		this.birthMonth = birthMonth;
	}
	
	public final Integer getBirthDay()
	{
		return birthDay;
	}
	
	public final void setBirthDay(Integer birthDay)
	{
		this.birthDay = birthDay;
	}
}
