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

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Well-formed language tags. This is not a membership check against a language registry.
 */
final class LanguageTag
{
	private static final Set<String> GRANDFATHERED = Set.of("art-lojban", "cel-gaulish", "en-gb-oed", "i-ami", "i-bnn",
			"i-default", "i-enochian", "i-hak", "i-klingon", "i-lux", "i-mingo", "i-navajo", "i-pwn", "i-tao", "i-tay",
			"i-tsu", "no-bok", "no-nyn", "sgn-be-fr", "sgn-be-nl", "sgn-ch-de", "zh-guoyu", "zh-hakka", "zh-min",
			"zh-min-nan", "zh-xiang");
	
	private LanguageTag()
	{
	}
	
	static boolean valid(byte[] raw)
	{
		String tag = new String(raw, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
		if (GRANDFATHERED.contains(tag))
			return true;
		
		String[] parts = tag.split("-", -1);
		if (parts[0].equals("x"))
			return privateUse(parts, 1);
		
		if (parts[0].length() < 2 || parts[0].length() > 8 || !alpha(parts[0]))
			return false;
		
		int i = 1;
		if (parts[0].length() <= 3)
		{
			for (int n = 0; n < 3 && i < parts.length && parts[i].length() == 3 && alpha(parts[i]); n++)
				i++;
		}
		if (i < parts.length && parts[i].length() == 4 && alpha(parts[i]))
			i++;
		
		if (i < parts.length && (parts[i].length() == 2 && alpha(parts[i]) || parts[i].length() == 3 && numeric(parts[i])))
			i++;
		
		Map<String, Boolean> variants = new HashMap<>();
		while (i < parts.length)
		{
			String part = parts[i];
			boolean variant = part.length() >= 5 && part.length() <= 8 && alnum(part)
					|| part.length() == 4 && part.charAt(0) >= '0' && part.charAt(0) <= '9' && alnum(part);
			if (!variant)
				break;
			
			if (variants.containsKey(part))
				return false;
			
			variants.put(part, Boolean.TRUE);
			i++;
		}
		Map<String, Boolean> extensions = new HashMap<>();
		while (i < parts.length)
		{
			String part = parts[i];
			if (part.equals("x"))
				return privateUse(parts, i + 1);
			
			if (part.length() != 1 || !alnum(part) || extensions.containsKey(part))
				return false;
			
			extensions.put(part, Boolean.TRUE);
			i++;
			int start = i;
			while (i < parts.length && parts[i].length() >= 2 && parts[i].length() <= 8 && alnum(parts[i]))
				i++;
			
			if (i == start)
				return false;
		}
		return true;
	}
	
	private static boolean privateUse(String[] parts, int index)
	{
		if (index >= parts.length)
			return false;
		
		for (int i = index; i < parts.length; i++)
		{
			if (parts[i].length() > 8 || !alnum(parts[i]))
				return false;
		}
		return true;
	}
	
	private static boolean alpha(String part)
	{
		if (part.isEmpty())
			return false;
		
		for (int i = 0; i < part.length(); i++)
		{
			char c = part.charAt(i);
			if (c < 'a' || c > 'z')
				return false;
		}
		return true;
	}
	
	private static boolean numeric(String part)
	{
		if (part.isEmpty())
			return false;
		
		for (int i = 0; i < part.length(); i++)
		{
			char c = part.charAt(i);
			if (c < '0' || c > '9')
				return false;
		}
		return true;
	}
	
	private static boolean alnum(String part)
	{
		if (part.isEmpty())
			return false;
		
		for (int i = 0; i < part.length(); i++)
		{
			char c = part.charAt(i);
			if (!(c >= 'a' && c <= 'z' || c >= '0' && c <= '9'))
				return false;
		}
		return true;
	}
}
