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
package com.l2jfree.gameserver.model.skills.effects.impl;

import com.l2jfree.gameserver.model.skills.Env;
import com.l2jfree.gameserver.model.skills.effects.L2Effect;
import com.l2jfree.gameserver.model.skills.effects.templates.EffectTemplate;

public class EffectCounter extends EffectBuff
{
	private int _ecount = 0;
	
	public EffectCounter(Env env, EffectTemplate template)
	{
		super(env, template);
	}
	
	@Override
	protected boolean onStart()
	{
		for (L2Effect e : getEffected().getAllEffects())
		{
			if (e instanceof EffectCounter)
			{
				// get current count of effects
				_ecount = ((EffectCounter)e).getEffectCount();
				// increase
				_ecount++;
				// set
				((EffectCounter)e).setEffectCount(_ecount);
			}
		}
		
		return true;
	}
	
	/* returns the count of running effects*/
	public int getEffectCount()
	{
		return _ecount;
	}
	
	public void setEffectCount(int ecount)
	{
		_ecount = ecount;
	}
}
