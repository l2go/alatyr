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
package com.l2jfree.gameserver.util;

/**
 * @author luisantonioa
 */

import java.io.File;
import java.text.NumberFormat;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.python.core.Py;
import org.python.core.PyModule;
import org.python.core.PySystemState;
import org.python.core.imp;
import org.python.util.InteractiveConsole;

import com.l2jfree.Config;
import com.l2jfree.L2AutoInitialization;
import com.l2jfree.gameserver.ThreadPoolManager;
import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Object;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.position.ObjectPosition;
import com.l2jfree.lang.L2Math;
import com.l2jfree.lang.L2Thread;
import com.l2jfree.tools.util.CustomFileNameFilter;
import com.l2jfree.util.ValueSortMap;

/**
 * General Utility functions related to Gameserver
 */
public final class Util
{
	
	// some sys info utils
	public static int getAvailableProcessors()
	{
		Runtime rt = Runtime.getRuntime();
		return rt.availableProcessors();
	}
	
	public static String getOSName()
	{
		return System.getProperty("os.name");
	}
	
	public static String getOSVersion()
	{
		return System.getProperty("os.version");
	}
	
	public static String getOSArch()
	{
		return System.getProperty("os.arch");
	}
	
	public static String[] getMemUsage()
	{
		return L2Thread.getMemoryUsageStatistics();
	}
	
	public static void JythonShell()
	{
		InteractiveConsole interp = null;
		try
		{
			String interpClass =
					PySystemState.registry.getProperty("python.console", "org.python.util.InteractiveConsole");
			interp = (InteractiveConsole)Class.forName(interpClass).newInstance();
		}
		catch (Exception e)
		{
			interp = new InteractiveConsole();
		}
		PyModule mod = imp.addModule("__main__");
		interp.setLocals(mod.__dict__);
		try
		{
			interp.interact();
		}
		catch (RuntimeException e)
		{
			Py.printException(e);
		}
		interp.cleanup();
	}
	
	public static void handleIllegalPlayerAction(L2Player actor, String message)
	{
		handleIllegalPlayerAction(actor, message, Config.DEFAULT_PUNISH);
	}
	
	public static void handleIllegalPlayerAction(L2Player actor, String message, int punishment)
	{
		actor.setIllegalWaiting(true);
		ThreadPoolManager.getInstance().scheduleGeneral(new IllegalPlayerAction(actor, message, punishment), 5000);
	}
	
	public static String getRelativePath(File base, File file)
	{
		return file.toURI().getPath().substring(base.toURI().getPath().length());
	}
	
	/** Return degree value of object 2 to the horizontal line with object 1 being the origin */
	public final static double calculateAngleFrom(L2Object obj1, L2Object obj2)
	{
		final ObjectPosition pos1 = obj1.getPosition();
		final ObjectPosition pos2 = obj2.getPosition();
		
		return calculateAngleFrom(pos1.getX(), pos1.getY(), pos2.getX(), pos2.getY());
	}
	
	/** Return degree value of object 2 to the horizontal line with object 1 being the origin */
	public final static double calculateAngleFrom(int obj1X, int obj1Y, int obj2X, int obj2Y)
	{
		double angleTarget = Math.toDegrees(Math.atan2(obj2Y - obj1Y, obj2X - obj1X));
		
		return getValidDegree(angleTarget);
	}
	
	public final static double convertHeadingToDegree(int clientHeading)
	{
		double degree = clientHeading / 182.044444444;
		
		return getValidDegree(degree);
	}
	
	public final static int convertDegreeToClientHeading(double degree)
	{
		return (int)getValidHeading(degree * 182.044444444);
	}
	
	public final static int calculateHeadingFrom(L2Object obj1, L2Object obj2)
	{
		final ObjectPosition pos1 = obj1.getPosition();
		final ObjectPosition pos2 = obj2.getPosition();
		
		return calculateHeadingFrom(pos1.getX(), pos1.getY(), pos2.getX(), pos2.getY());
	}
	
	public final static int calculateHeadingFrom(int obj1X, int obj1Y, int obj2X, int obj2Y)
	{
		double angleTarget = Math.toDegrees(Math.atan2(obj2Y - obj1Y, obj2X - obj1X));
		
		return (int)getValidHeading(angleTarget * 182.044444444);
	}
	
	public final static int calculateHeadingFrom(double dx, double dy)
	{
		double angleTarget = Math.toDegrees(Math.atan2(dy, dx));
		
		return (int)getValidHeading(angleTarget * 182.044444444);
	}
	
	/**
	 * @param heading (n * 65536 + k)
	 * @return k [0, 65536[
	 */
	public static double getValidHeading(double heading)
	{
		while (heading < 0.)
			heading += 65536.;
		
		while (heading >= 65536.)
			heading -= 65536.;
		
		return heading;
	}
	
	/**
	 * @param degree (n * 360 + k)
	 * @return k [0, 360[
	 */
	public static double getValidDegree(double degree)
	{
		while (degree < 0.)
			degree += 360.;
		
		while (degree >= 360.)
			degree -= 360.;
		
		return degree;
	}
	
	/**
	 * @return angle difference [0, 180]
	 */
	public static double getAngleDifference(L2Object obj, L2Object src)
	{
		double diff = Util.calculateAngleFrom(src, obj) - Util.convertHeadingToDegree(src.getHeading());
		
		while (diff > +180)
			diff -= 360;
		
		while (diff < -180)
			diff += 360;
		
		return Math.abs(diff);
	}
	
	public static boolean isInAngle(L2Object obj, L2Object src, double degree)
	{
		return Util.getAngleDifference(obj, src) <= degree;
	}
	
	public static enum Direction
	{
		FRONT,
		SIDE,
		BACK;
		
		public static Direction getDirection(L2Object obj, L2Object src)
		{
			double angleDifference = Util.getAngleDifference(obj, src);
			
			if (angleDifference <= 60)
				return FRONT;
			
			if (angleDifference <= 120)
				return SIDE;
			
			return BACK;
		}
	}
	
	public final static double calculateDistance(int x1, int y1, int z1, int x2, int y2, int z2, boolean includeZAxis)
	{
		if (includeZAxis)
			return L2Math.calculateDistance(x1, y1, z1, x2, y2, z2);
		else
			return L2Math.calculateDistance(x1, y1, x2, y2);
	}
	
	public final static double calculateDistance(L2Object obj1, L2Object obj2, boolean includeZAxis)
	{
		if (obj1 == null || obj2 == null)
			return Double.MAX_VALUE;
		
		final ObjectPosition pos1 = obj1.getPosition();
		final ObjectPosition pos2 = obj2.getPosition();
		
		return calculateDistance(pos1.getX(), pos1.getY(), pos1.getZ(), pos2.getX(), pos2.getY(), pos2.getZ(),
				includeZAxis);
	}
	
	/**
	 * Capitalizes the first letter of a string, and returns the result.<BR>
	 * (Based on ucfirst() function of PHP)
	 * 
	 * @param String str
	 * @return String containing the modified string.
	 */
	public static String capitalizeFirst(String str)
	{
		str = str.trim();
		
		if (str.length() > 0 && Character.isLetter(str.charAt(0)))
			return str.substring(0, 1).toUpperCase() + str.substring(1);
		
		return str;
	}
	
	/**
	 * Capitalizes the first letter of every "word" in a string.<BR>
	 * (Based on ucwords() function of PHP)
	 * 
	 * @param String str
	 * @return String containing the modified string.
	 */
	public static String capitalizeWords(String str)
	{
		char[] charArray = str.toCharArray();
		String result = "";
		
		// Capitalize the first letter in the given string!
		charArray[0] = Character.toUpperCase(charArray[0]);
		
		for (int i = 0; i < charArray.length; i++)
		{
			if (Character.isWhitespace(charArray[i]))
				charArray[i + 1] = Character.toUpperCase(charArray[i + 1]);
			
			result += Character.toString(charArray[i]);
		}
		
		return result;
	}
	
	public static String reverseColor(String color)
	{
		char[] ch1 = color.toCharArray();
		char[] ch2 = new char[6];
		ch2[0] = ch1[4];
		ch2[1] = ch1[5];
		ch2[2] = ch1[2];
		ch2[3] = ch1[3];
		ch2[4] = ch1[0];
		ch2[5] = ch1[1];
		return new String(ch2);
	}
	
	/**
	 * Checks if object is within range, adding collisionRadius
	 */
	public static boolean checkIfInRange(int range, L2Object obj1, L2Object obj2, boolean includeZAxis)
	{
		if (!L2Object.isSameInstance(obj1, obj2))
			return false;
		
		if (range == -1)
			return true; // not limited
			
		if (obj1 instanceof L2Creature)
			range += ((L2Creature)obj1).getTemplate().getCollisionRadius();
		
		if (obj2 instanceof L2Creature)
			range += ((L2Creature)obj2).getTemplate().getCollisionRadius();
		
		final ObjectPosition pos1 = obj1.getPosition();
		final ObjectPosition pos2 = obj2.getPosition();
		
		if (includeZAxis)
			return L2Math.isDistanceLessThan(pos1.getX(), pos1.getY(), pos1.getZ(), pos2.getX(), pos2.getY(),
					pos2.getZ(), range);
		else
			return L2Math.isDistanceLessThan(pos1.getX(), pos1.getY(), pos2.getX(), pos2.getY(), range);
	}
	
	/*
	 *  Checks if object is within short (sqrt(int.max_value)) radius,
	 *  not using collisionRadius. Faster calculation than checkIfInRange
	 *  if distance is short and collisionRadius isn't needed.
	 *  Not for long distance checks (potential teleports, far away castles etc)
	 */
	public static boolean checkIfInShortRadius(int radius, L2Object obj1, L2Object obj2, boolean includeZAxis)
	{
		if (!L2Object.isSameInstance(obj1, obj2))
			return false;
		
		if (radius == -1)
			return true; // not limited
			
		final ObjectPosition pos1 = obj1.getPosition();
		final ObjectPosition pos2 = obj2.getPosition();
		
		if (includeZAxis)
			return L2Math.isDistanceLessThan(pos1.getX(), pos1.getY(), pos1.getZ(), pos2.getX(), pos2.getY(),
					pos2.getZ(), radius);
		else
			return L2Math.isDistanceLessThan(pos1.getX(), pos1.getY(), pos2.getX(), pos2.getY(), radius);
	}
	
	/**
	 * Returns a delimited string for an given array of string elements.<BR>
	 * (Based on implode() in PHP)
	 * 
	 * @param String[] strArray
	 * @param String strDelim
	 * @return String implodedString
	 */
	public static String implodeString(String[] strArray, String strDelim)
	{
		String result = "";
		
		for (String strValue : strArray)
			result += strValue + strDelim;
		
		return result;
	}
	
	/**
	 * Returns a delimited string for an given collection of string elements.<BR>
	 * (Based on implode() in PHP)
	 * 
	 * @param Collection&lt;String&gt; strCollection
	 * @param String strDelim
	 * @return String implodedString
	 */
	public static String implodeString(Collection<String> strCollection, String strDelim)
	{
		return implodeString(strCollection.toArray(new String[strCollection.size()]), strDelim);
	}
	
	/**
	 * Returns the rounded value of val to specified number of digits after the decimal point.<BR>
	 * (Based on round() in PHP)
	 * 
	 * @param float val
	 * @param int numPlaces
	 * @return float roundedVal
	 */
	public static float roundTo(float val, int numPlaces)
	{
		if (numPlaces <= 1)
			return Math.round(val);
		
		float exponent = L2Math.pow(10, numPlaces);
		
		return (Math.round(val * exponent) / exponent);
	}
	
	public static WorldFiles.Listed listDatapack(String dirname, String extention)
	{
		return WorldFiles.list(new File(Config.DATAPACK_ROOT, "data/" + dirname), new CustomFileNameFilter(extention));
	}
	
	public static File[] getDatapackFiles(String dirname, String extention)
	{
		return listDatapack(dirname, extention).files();
	}
	
	public static boolean isAlphaNumeric(String text)
	{
		if (text == null)
			return false;
		boolean result = true;
		char[] chars = text.toCharArray();
		for (int i = 0; i < chars.length; i++)
		{
			if (!Character.isLetterOrDigit(chars[i]))
			{
				result = false;
				break;
			}
		}
		return result;
	}
	
	public static int reverseRGBChanels(int rgbColor)
	{
		return (Integer.reverseBytes(rgbColor) >> 8) & 0xFFFFFF;
	}
	
	/**
	 * Returns a number formatted with "," delimiter
	 * 
	 * @param value
	 * @return String formatted number
	 */
	public static String formatNumber(long value)
	{
		return NumberFormat.getInstance(Locale.ENGLISH).format(value);
	}
	
	/**
	 * @param s
	 */
	public static void printSection(String s)
	{
		s = "={ " + s + " }";
		
		while (s.length() < 160)
			s = "-" + s;
		
		L2AutoInitialization.out.println(s);
	}
	
	public static Map<Integer, Integer> sortMap(Map<Integer, Integer> map, boolean asc)
	{
		ValueSortMap vsm = new ValueSortMap();
		return vsm.sortThis(map, asc);
	}
	
	public static int[] toIntArray(String string)
	{
		return toIntArray(string, ",");
	}
	
	public static float[] toFloatArray(String string)
	{
		return toFloatArray(string, ",");
	}
	
	public static boolean[] toBooleanArray(String string)
	{
		return toBooleanArray(string, ",");
	}
	
	public static int[] toIntArray(String string, String delimiter)
	{
		String[] strings = StringUtils.split(string, delimiter);
		int[] ints = new int[strings.length];
		for (int i = 0; i < strings.length; i++)
		{
			ints[i] = Integer.parseInt(strings[i]);
		}
		return ints;
	}
	
	public static float[] toFloatArray(String string, String delimiter)
	{
		String[] strings = StringUtils.split(string, delimiter);
		float[] floats = new float[strings.length];
		for (int i = 0; i < strings.length; i++)
		{
			floats[i] = Float.parseFloat(strings[i]);
		}
		return floats;
	}
	
	public static boolean[] toBooleanArray(String string, String delimiter)
	{
		String[] strings = StringUtils.split(string, delimiter);
		boolean[] bools = new boolean[strings.length];
		for (int i = 0; i < strings.length; i++)
		{
			bools[i] = Boolean.parseBoolean(strings[i]);
		}
		return bools;
	}
	
	/**
	 * Return amount of adena formatted with "," delimiter
	 * 
	 * @param amount
	 * @return String formatted adena amount
	 */
	public static String formatAdena(long amount)
	{
		return formatNumber(amount);
	}
}
