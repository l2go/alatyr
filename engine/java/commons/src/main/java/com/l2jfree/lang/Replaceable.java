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
package com.l2jfree.lang;

import java.util.regex.Matcher;

/**
 * @author NB4L1
 */
public abstract class Replaceable implements CharSequence
{
	protected abstract CharSequence getInnerCharSequence();
	
	@Override
	public final char charAt(int index)
	{
		return getInnerCharSequence().charAt(index);
	}
	
	@Override
	public final int length()
	{
		return getInnerCharSequence().length();
	}
	
	@Override
	public final CharSequence subSequence(int start, int end)
	{
		return getInnerCharSequence().subSequence(start, end);
	}
	
	public final String substring(int start, int end)
	{
		return subSequence(start, end).toString();
	}
	
	public abstract void replace(String target, String replacement);
	
	public final void replace(String pattern, long value)
	{
		replace(pattern, String.valueOf(value));
	}
	
	public final void replace(String pattern, double value)
	{
		replace(pattern, String.valueOf(value));
	}
	
	public final void replace(String pattern, Object value)
	{
		replace(pattern, String.valueOf(value));
	}
	
	public abstract int indexOf(String str, int fromIndex);
	
	static void replace(StringBuilder sb, String target, String replacement)
	{
		for (int index = 0; (index = sb.indexOf(target, index)) != -1; index += replacement.length())
			sb.replace(index, index + target.length(), replacement);
	}
	
	@Override
	public final String toString()
	{
		return getInnerCharSequence().toString();
	}
	
	public static final class StringBuilderReplaceable extends Replaceable
	{
		private final StringBuilder _stringBuilder;
		
		public StringBuilderReplaceable(StringBuilder stringBuilder)
		{
			_stringBuilder = stringBuilder;
		}
		
		@Override
		protected CharSequence getInnerCharSequence()
		{
			return _stringBuilder;
		}
		
		@Override
		public void replace(String target, String replacement)
		{
			replace(_stringBuilder, target, quoteReplacement(replacement));
		}
		
		@Override
		public int indexOf(String str, int fromIndex)
		{
			return _stringBuilder.indexOf(str, fromIndex);
		}
	}
	
	public static final class StringReplaceable extends Replaceable
	{
		private String _string;
		
		public StringReplaceable(String string)
		{
			_string = string;
		}
		
		@Override
		protected CharSequence getInnerCharSequence()
		{
			return _string;
		}
		
		@Override
		public void replace(String target, String replacement)
		{
			replacement = quoteReplacement(replacement);
			
			_string = _string.replace(target, replacement);
		}
		
		@Override
		public int indexOf(String str, int fromIndex)
		{
			return _string.indexOf(str, fromIndex);
		}
	}
	
	public static final class MixedReplaceable extends Replaceable
	{
		private final String _string;
		private StringBuilder _builder;
		
		public MixedReplaceable(String string)
		{
			_string = string;
		}
		
		@Override
		protected CharSequence getInnerCharSequence()
		{
			if (_builder == null)
				return _string;
			
			return _builder;
		}
		
		@Override
		public void replace(String target, String replacement)
		{
			if (_builder == null)
			{
				if (!_string.contains(target))
					return;
				
				_builder = new StringBuilder(_string);
			}
			
			replace(_builder, target, quoteReplacement(replacement));
		}
		
		@Override
		public int indexOf(String str, int fromIndex)
		{
			if (_builder == null)
				return _string.indexOf(str, fromIndex);
			
			return _builder.indexOf(str, fromIndex);
		}
	}
	
	public static Replaceable valueOf(CharSequence charSequence)
	{
		// StringBuilder
		if (charSequence instanceof StringBuilder)
			return valueOf((StringBuilder)charSequence);
		
		// String, CharSequence
		return new MixedReplaceable(charSequence.toString());
	}
	
	// StringBuilder
	public static Replaceable valueOf(StringBuilder stringBuilder)
	{
		return new StringBuilderReplaceable(stringBuilder);
	}
	
	/**
	 * Inverse of {@link Matcher#quoteReplacement(String)}.
	 * 
	 * @param s
	 * @return
	 */
	public static String quoteReplacement(String s)
	{
		if (s.indexOf('\\') == -1)
			return s;
		
		final StringBuilder sb = new StringBuilder(s.length());
		
		for (int i = 0; i < s.length(); i++)
		{
			final char c = s.charAt(i);
			
			if (c == '\\' && i + 1 < s.length())
			{
				sb.append(s.charAt(i + 1));
				i++;
			}
			else
			{
				sb.append(c);
			}
		}
		
		return sb.toString();
	}
}
