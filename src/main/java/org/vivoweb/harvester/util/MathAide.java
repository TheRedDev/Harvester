/*******************************************************************************
 * Copyright (c) 2010-2011 VIVO Harvester Team. For full list of contributors, please see the AUTHORS file provided.
 * All rights reserved.
 * This program and the accompanying materials are made available under the terms of the new BSD license which accompanies this distribution, and is available at http://www.opensource.org/licenses/bsd-license.html
 ******************************************************************************/
package org.vivoweb.harvester.util;

/**
 * Set of math helper methods
 * @author Christopher Haines chris@chrishaines.net
 */
public class MathAide {
	/**
	 * Find minimum of a set of ints
	 * @param d set of ints
	 * @return the index of the minimum in the set
	 */
	public static int minIntIndex(int... d) {
		if((d == null) || (d.length == 0)) {
			throw new IllegalArgumentException("d cannot be null");
		}
		int index = 0;
		for(int x = 0; x < d.length; x++) {
			if(d[x] < d[index]) {
				index = x;
			}
		}
		return index;
	}
	
	/**
	 * Find minimum of a set of doubles
	 * @param d set of doubles
	 * @return the index of the minimum in the set
	 */
	public static double minDoubleIndex(double... d) {
		if((d == null) || (d.length == 0)) {
			throw new IllegalArgumentException("d cannot be null");
		}
		int index = 0;
		for(int x = 0; x < d.length; x++) {
			if(d[x] < d[index]) {
				index = x;
			}
		}
		return index;
	}
	
	/**
	 * Find minimum of a set of floats
	 * @param d set of floats
	 * @return the index of the minimum in the set
	 */
	public static float minFloatIndex(float... d) {
		if((d == null) || (d.length == 0)) {
			throw new IllegalArgumentException("d cannot be null");
		}
		int index = 0;
		for(int x = 0; x < d.length; x++) {
			if(d[x] < d[index]) {
				index = x;
			}
		}
		return index;
	}
	
	/**
	 * Find minimum of a set of longs
	 * @param d set of longs
	 * @return the index of the minimum in the set
	 */
	public static long minLongIndex(long... d) {
		if((d == null) || (d.length == 0)) {
			throw new IllegalArgumentException("d cannot be null");
		}
		int index = 0;
		for(int x = 0; x < d.length; x++) {
			if(d[x] < d[index]) {
				index = x;
			}
		}
		return index;
	}
	
	/**
	 * NVL test function
	 * @param a parameter to test and return if not null
	 * @param b parameter to return otherwise
	 * @return A if A is not null, B otherwise 
	 */
	public static <T> T nvl(T a, T b) {
		return (a == null)?b:a;
	}
	
	/**
	 * NVL2 test function
	 * @param a parameter to test
	 * @param b parameter to return if A is not null
	 * @param c parameter to return otherwise
	 * @return B if A is not null, C otherwise 
	 */
	public static <T> T nvl2(Object a, T b, T c) {
		return (a == null)?c:b;
	}
	
	/**
	 * NVL2 test function
	 * @param a parameter to test
	 * @param b parameter to return if A is not null
	 * @return B if A is not null, null otherwise 
	 */
	public static <T> T nvl2(Object a, T b) {
		return (a == null)?null:b;
	}
}
