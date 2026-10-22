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
package com.l2jfree.gameserver.bridge.wire;

import java.util.Map;

/**
 * One field in a schema. A codec copies the spec before use.
 */
final class Spec
{
	Shape shape;
	boolean essential;
	boolean required;
	boolean nonzero;
	int bits;
	int maxBytes;
	long[] enums;
	Map<Integer, Spec> object;
	Spec element;
	
	Spec()
	{
	}
	
	static Spec required(Shape shape)
	{
		Spec spec = new Spec();
		spec.shape = shape;
		spec.essential = true;
		spec.required = true;
		return spec;
	}
	
	static Spec essential(Shape shape)
	{
		Spec spec = new Spec();
		spec.shape = shape;
		spec.essential = true;
		return spec;
	}
	
	static Spec optional(Shape shape)
	{
		Spec spec = new Spec();
		spec.shape = shape;
		return spec;
	}
	
	Spec copy()
	{
		Spec spec = new Spec();
		spec.shape = shape;
		spec.essential = essential;
		spec.required = required;
		spec.nonzero = nonzero;
		spec.bits = bits;
		spec.maxBytes = maxBytes;
		spec.enums = enums;
		spec.object = object;
		spec.element = element;
		return spec;
	}
}
