/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.l2jfree.gameserver.network.bridge;

import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.l2jfree.gameserver.gameobjects.L2Player;

/** Restricted display tokenizer. It neither executes commands nor fetches linked content. */
public final class BridgeHtml
{
	public record Window(String text, List<BridgeOption> options)
	{
		public Window { options = List.copyOf(options); }
		@Override public String toString() { return "Window[options=" + options.size() + "]"; }
	}

	private record Tag(String name, boolean closing, Map<String, String> attributes) {}

	private BridgeHtml() {}

	public static void describe(FactSink sink, L2Player receiver, int npcId, CharSequence content)
	{
		Window window = parse(content);
		var npc = receiver == null ? null : receiver.getLastFolkNPC();
		boolean spoken = npcId > 0 && npc != null && receiver.getTarget() == npc && npc.getObjectId() == npcId;
		if (!spoken)
		{
			sink.emit(BridgeFact.of(18, BridgeField.text(20, window.text(), 1),
					BridgeField.string(26, "world-message")));
			return;
		}
		var options = window.options().stream().map(BridgeOption::value).toList();
		sink.emit(BridgeFact.of(11, BridgeField.number(64, npcId), BridgeField.text(20, window.text(), 1),
				BridgeField.list(68, options), BridgeField.number(74, sink.now())));
	}

	public static Window parse(CharSequence content)
	{
		if (content == null || content.length() > 4096)
			throw new IllegalArgumentException("HTML size");
		String source = content.toString();
		requireUtf8(source);
		String html = source.replace("\r\n", "\n").replace('\r', '\n');
		var display = new Display();
		var options = new ArrayList<BridgeOption>();
		Display label = null;
		String action = null;
		int anchorOption = -1;
		boolean pre = false;
		String suppressed = null;
		int offset = 0;
		while (offset < html.length())
		{
			if (suppressed != null)
			{
				int close = -1;
				String marker = "</" + suppressed;
				for (int i = offset; i + marker.length() < html.length(); i++)
					if (asciiLower(html.substring(i, i + marker.length())).equals(marker)
							&& (html.charAt(i + marker.length()) == '>' || asciiSpace(html.charAt(i + marker.length()))))
					{
						close = i;
						break;
					}
				if (close < 0)
					break;
				offset = tagEnd(html, close + 1) + 1;
				suppressed = null;
				continue;
			}
			if (html.charAt(offset) != '<')
			{
				int end = html.indexOf('<', offset);
				if (end < 0)
					end = html.length();
				if (suppressed == null)
				{
					String text = entities(html.substring(offset, end));
					display.append(text, pre);
					if (label != null)
						label.append(text, pre);
				}
				offset = end;
				continue;
			}
			if (html.startsWith("<!--", offset))
			{
				int end = html.indexOf("-->", offset + 4);
				if (end < 0)
					throw new IllegalArgumentException("unclosed HTML comment");
				offset = end + 3;
				continue;
			}
			int end = tagEnd(html, offset + 1);
			Tag tag = tag(html.substring(offset + 1, end));
			offset = end + 1;
			if (tag.name().equals("script") || tag.name().equals("style"))
			{
				if (!tag.closing())
					suppressed = tag.name();
				continue;
			}
			if (tag.name().equals("pre"))
			{
				pre = !tag.closing();
				continue;
			}
			if (tag.name().equals("br") || tag.name().equals("br1")
					|| tag.closing() && (tag.name().equals("p") || tag.name().equals("tr")))
			{
				display.newline(pre);
				if (label != null)
					label.newline(pre);
			}
			if (tag.name().equals("a"))
			{
				if (tag.closing())
				{
					if (label != null)
					{
						if (action != null)
							options.set(anchorOption, new BridgeOption(anchorOption + 1, label.finish(), action));
						label = null;
						action = null;
						anchorOption = -1;
					}
				}
				else if (tag.attributes().containsKey("action"))
				{
					if (label != null)
						throw new IllegalArgumentException("nested action anchor");
					label = new Display();
					action = savedAction(tag.attributes().get("action"));
					if (action != null)
					{
						anchorOption = options.size();
						options.add(null);
					}
				}
			}
			if (tag.name().equals("button") && !tag.closing())
			{
				String value = tag.attributes().get("value");
				if (value != null)
					display.append(value, pre);
				String saved = savedAction(tag.attributes().get("action"));
				if (saved != null && value != null)
					options.add(new BridgeOption(options.size() + 1, value, saved));
			}
		}
		if (label != null)
			throw new IllegalArgumentException("unclosed action anchor");
		return new Window(display.finish(), options);
	}

	private static void requireUtf8(String value)
	{
		try
		{
			var encoded = StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
					.onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(value));
			if (encoded.remaining() > 4096)
				throw new IllegalArgumentException("HTML size");
		}
		catch (CharacterCodingException invalid)
		{
			throw new IllegalArgumentException("invalid HTML text", invalid);
		}
	}

	private static int tagEnd(String html, int offset)
	{
		char quote = 0;
		for (int i = offset; i < html.length(); i++)
		{
			char c = html.charAt(i);
			if (quote != 0)
			{
				if (c == quote)
					quote = 0;
			}
			else if (c == '\'' || c == '"')
				quote = c;
			else if (c == '>')
				return i;
		}
		throw new IllegalArgumentException("incomplete HTML tag");
	}

	private static Tag tag(String source)
	{
		int i = 0;
		while (i < source.length() && asciiSpace(source.charAt(i)))
			i++;
		boolean closing = i < source.length() && source.charAt(i) == '/';
		if (closing)
			i++;
		int start = i;
		while (i < source.length() && !asciiSpace(source.charAt(i)) && source.charAt(i) != '/')
			i++;
		String name = asciiLower(source.substring(start, i));
		var attributes = new HashMap<String, String>();
		while (i < source.length())
		{
			while (i < source.length() && asciiSpace(source.charAt(i)))
				i++;
			if (i == source.length() || source.charAt(i) == '/')
				break;
			start = i;
			while (i < source.length() && !asciiSpace(source.charAt(i)) && source.charAt(i) != '=')
				i++;
			String key = asciiLower(source.substring(start, i));
			while (i < source.length() && asciiSpace(source.charAt(i)))
				i++;
			String value = "";
			if (i < source.length() && source.charAt(i) == '=')
			{
				i++;
				while (i < source.length() && asciiSpace(source.charAt(i)))
					i++;
				if (i == source.length())
					throw new IllegalArgumentException("missing HTML attribute");
				char quote = source.charAt(i);
				if (quote == '\'' || quote == '"')
				{
					start = ++i;
					int end = source.indexOf(quote, i);
					if (end < 0)
						throw new IllegalArgumentException("unclosed HTML attribute");
					value = source.substring(start, end);
					i = end + 1;
				}
				else
				{
					start = i;
					while (i < source.length() && !asciiSpace(source.charAt(i)))
						i++;
					value = source.substring(start, i);
				}
			}
			if (!key.equals("action"))
				value = entities(value);
			if (attributes.putIfAbsent(key, value) != null && (name.equals("a") || name.equals("button")))
				throw new IllegalArgumentException("duplicate action attribute");
		}
		return new Tag(name, closing, Map.copyOf(attributes));
	}

	private static String savedAction(String value)
	{
		if (value == null)
			return null;
		if (entities(value).indexOf('\0') >= 0)
			throw new IllegalArgumentException("invalid action identifier");
		String prefix = "bypass -h";
		if (!value.startsWith(prefix) || value.length() == prefix.length()
				|| !asciiSpace(value.charAt(prefix.length())))
			return null;
		String action = value.substring(prefix.length()).trim();
		if (action.isEmpty() || entities(action).indexOf('$') >= 0)
			return null;
		requireUtf8(action);
		return action;
	}

	private static boolean asciiSpace(char c)
	{
		return c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f' || c == 11;
	}

	private static String asciiLower(String value)
	{
		var result = new StringBuilder(value.length());
		for (int i = 0; i < value.length(); i++)
		{
			char c = value.charAt(i);
			result.append(c >= 'A' && c <= 'Z' ? (char)(c + ('a' - 'A')) : c);
		}
		return result.toString();
	}

	private static String entities(String source)
	{
		var result = new StringBuilder();
		for (int i = 0; i < source.length(); i++)
		{
			char c = source.charAt(i);
			if (c != '&')
			{
				result.append(c);
				continue;
			}
			int end = source.indexOf(';', i + 1);
			if (end < 0)
			{
				result.append(c);
				continue;
			}
			String entity = source.substring(i + 1, end);
			String decoded = switch (entity)
			{
				case "amp" -> "&";
				case "lt" -> "<";
				case "gt" -> ">";
				case "quot" -> "\"";
				case "apos" -> "'";
				case "nbsp" -> "\u00a0";
				default -> numericEntity(entity);
			};
			if (decoded == null)
			{
				result.append(c);
				continue;
			}
			result.append(decoded);
			i = end;
		}
		return result.toString();
	}

	private static String numericEntity(String entity)
	{
		if (!entity.startsWith("#"))
			return null;
		try
		{
			boolean hex = entity.startsWith("#x") || entity.startsWith("#X");
			String digits = entity.substring(hex ? 2 : 1);
			if (digits.isEmpty())
				return null;
			for (int i = 0; i < digits.length(); i++)
				if (!(digits.charAt(i) >= '0' && digits.charAt(i) <= '9'
						|| hex && (digits.charAt(i) >= 'a' && digits.charAt(i) <= 'f'
								|| digits.charAt(i) >= 'A' && digits.charAt(i) <= 'F')))
					return null;
			int code = Integer.parseInt(digits, hex ? 16 : 10);
			if (!Character.isValidCodePoint(code) || code >= 0xd800 && code <= 0xdfff)
				return null;
			return new String(Character.toChars(code));
		}
		catch (NumberFormatException invalid) { return null; }
	}

	private static final class Display
	{
		private final StringBuilder text = new StringBuilder();
		private boolean pendingSpace;
		private boolean lastPre;

		void append(String value, boolean pre)
		{
			for (int i = 0; i < value.length(); i++)
			{
				char c = value.charAt(i);
				if (!pre && (Character.isWhitespace(c) || c == '\u00a0'))
				{
					pendingSpace = true;
					continue;
				}
				if (pendingSpace && text.length() > 0 && text.charAt(text.length() - 1) != '\n')
					text.append(' ');
				pendingSpace = false;
				text.append(c);
				lastPre = pre;
			}
		}

		void newline(boolean pre)
		{
			pendingSpace = false;
			if (!pre && !lastPre)
				while (text.length() > 0 && text.charAt(text.length() - 1) == ' ')
					text.setLength(text.length() - 1);
			text.append('\n');
			lastPre = pre;
		}

		String finish()
		{
			if (!lastPre)
				while (text.length() > 0 && (text.charAt(text.length() - 1) == ' ' || text.charAt(text.length() - 1) == '\n'))
					text.setLength(text.length() - 1);
			return text.toString();
		}
	}
}
