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
package com.l2jfree.smoke;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Prints one line per step and keeps the lines. A step starts with {@link #begin}; it ends with
 * {@link #ok} or, when it throws, with {@link #fail}.
 */
final class Reporter
{
	private final PrintStream _out;
	private final List<String> _lines = new ArrayList<>();
	private String _current = "start";

	Reporter(PrintStream out)
	{
		_out = out;
	}

	void begin(String step)
	{
		_current = step;
	}

	String current()
	{
		return _current;
	}

	void ok(String detail)
	{
		line("OK  " + _current + (detail == null || detail.isEmpty() ? "" : " (" + detail + ")"));
	}

	/** A line that is not a step: a remark about what the client did. */
	void info(String text)
	{
		line("    " + text);
	}

	void fail(String reason)
	{
		line("FAIL " + _current + ": " + reason);
	}

	void line(String text)
	{
		_lines.add(text);
		_out.println(text);
	}

	List<String> lines()
	{
		return List.copyOf(_lines);
	}
}
