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
package com.l2jfree.gameserver.model.skills.funcs;

import java.lang.reflect.Constructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.gameserver.model.skills.Stats;
import com.l2jfree.gameserver.model.skills.conditions.Condition;

/**
 * @author mkizub
 */
public final class FuncTemplate
{
	private final static Logger _log = LoggerFactory.getLogger(FuncTemplate.class);
	
	private final Constructor<?> _constructor;
	
	public final Stats stat;
	public final int order;
	public final double lambda;
	public final Condition applayCond;
	
	public FuncTemplate(Condition pApplayCond, String pFunc, Stats pStat, int pOrder, double pLambda)
	{
		Class<?> clazz;
		try
		{
			final StringBuilder sb = new StringBuilder();
			sb.append(IFuncMarker.class.getPackage().getName());
			sb.append(".Func");
			sb.append(pFunc);
			clazz = Class.forName(sb.toString());
		}
		catch (Exception e)
		{
			throw new RuntimeException(e);
		}
		
		// Ugly fixes for DP errors
		switch (pStat)
		{
			case CRITICAL_DAMAGE_ADD:
			{
				if (clazz != FuncAdd.class && clazz != FuncSub.class)
				{
					throwException(pFunc, pStat, pOrder, pLambda);
					pStat = Stats.CRITICAL_DAMAGE;
				}
				break;
			}
			case CRITICAL_DAMAGE:
			{
				if (clazz == FuncAdd.class || clazz == FuncSub.class)
				{
					throwException(pFunc, pStat, pOrder, pLambda);
					pStat = Stats.CRITICAL_DAMAGE_ADD;
				}
				break;
			}
			case CRITICAL_RATE:
			{
				if (clazz == FuncMul.class)
				{
					//throwException(pFunc, pStat, pOrder, pLambda);
					clazz = FuncBaseMul.class;
					pLambda = (pLambda - 1.0);
				}
				else if (clazz == FuncDiv.class)
				{
					//throwException(pFunc, pStat, pOrder, pLambda);
					clazz = FuncBaseMul.class;
					pLambda = ((1.0 / pLambda) - 1.0);
				}
				break;
			}
			case MCRITICAL_RATE:
			{
				if (clazz == FuncMul.class)
				{
					//throwException(pFunc, pStat, pOrder, pLambda);
					clazz = FuncBaseMul.class;
					pLambda = (pLambda - 1.0);
				}
				else if (clazz == FuncDiv.class)
				{
					//throwException(pFunc, pStat, pOrder, pLambda);
					clazz = FuncBaseMul.class;
					pLambda = ((1.0 / pLambda) - 1.0);
				}
				break;
			}
		}
		
		if (pStat.isMultiplicativeResist())
		{
			if (clazz != FuncMul.class && clazz != FuncDiv.class)
				throwException(pFunc, pStat, pOrder, pLambda);
			
			if (pLambda > 2 || pLambda < 0)
				throwException(pFunc, pStat, pOrder, pLambda);
		}
		
		if (pStat.isAdditiveResist())
		{
			if (clazz != FuncAdd.class && clazz != FuncSub.class)
				throwException(pFunc, pStat, pOrder, pLambda);
			
			if ((int)pLambda != pLambda) // it wasn't an integer value
				throwException(pFunc, pStat, pOrder, pLambda);
		}
		
		if (clazz == FuncEnchant.class)
		{
			switch (pStat)
			{
				case MAGIC_DEFENCE:
				case POWER_DEFENCE:
				case SHIELD_DEFENCE:
				case MAGIC_ATTACK:
				case POWER_ATTACK:
					break;
				default:
					throwException(pFunc, pStat, pOrder, pLambda);
			}
		}
		else if (clazz == FuncBaseMul.class)
		{
			switch (pStat)
			{
				case CRITICAL_RATE:
				case MCRITICAL_RATE:
					break;
				default:
					throwException(pFunc, pStat, pOrder, pLambda);
			}
		}
		
		stat = pStat;
		order = pOrder;
		lambda = pLambda;
		applayCond = pApplayCond;
		
		try
		{
			_constructor =
					clazz.getConstructor(Stats.class, Integer.TYPE, FuncOwner.class, Double.TYPE, Condition.class);
		}
		catch (Exception e)
		{
			throw new RuntimeException(e);
		}
	}
	
	private void throwException(String pFunc, Stats pStat, int pOrder, double pLambda)
	{
		throw new IllegalStateException("<" + pFunc.toLowerCase() + " order=\"0x" + Integer.toHexString(pOrder)
				+ "\" stat=\"" + pStat.getValue() + "\" val=\"" + pLambda + "\"/>");
	}
	
	public Func getFunc(FuncOwner funcOwner)
	{
		try
		{
			return (Func)_constructor.newInstance(stat, order, funcOwner, lambda, applayCond);
		}
		catch (Exception e)
		{
			_log.warn("", e);
		}
		
		return null;
	}
}
