/*******************************************************************************
 * Copyright (c) 2010-2011 VIVO Harvester Team. For full list of contributors, please see the AUTHORS file provided.
 * All rights reserved.
 * This program and the accompanying materials are made available under the terms of the new BSD license which accompanies this distribution, and is available at http://www.opensource.org/licenses/bsd-license.html
 ******************************************************************************/
package org.vivoweb.harvester.score.algorithm;

import java.util.HashMap;
import java.util.Map;

/**
 * Calculates the difference of two strings and accounts for typos
 * @author Christopher Haines chris@chrishaines.net
 */
public class NormalizedTypoDifference extends NormalizedDamerauLevenshteinDifference {
	/**
	 * The amount to decost due to type closeness
	 */
	private float decost = 0f;
	
	/**
	 * Default Constructor
	 */
	public NormalizedTypoDifference() {
		this.keyWeights = USEngKeyboard;
	}
	
	/**
	 * Default Constructor
	 * @param keyWeights the key weights
	 */
	public NormalizedTypoDifference(Map<Character, Map<Character, Float>> keyWeights) {
		this.keyWeights = keyWeights;
	}
	
	/**
	 * Reduction for things only 1 key away
	 */
	public static final float reduce1Weight = .5f;
	/**
	 * Reduction for things only 1 key away and shifted (for numbers)
	 */
	public static final float reduce1WeightShift = .3f;
	/**
	 * The Current Keyboard Proximity Map
	 */
	private Map<Character, Map<Character, Float>> keyWeights;
	/**
	 * US English Standard Keyboard Proximity Map
	 */
	public static final Map<Character, Map<Character, Float>> USEngKeyboard;
	static {
		Map<Character, String> reduce1Map = new HashMap<Character, String>();
		Map<Character, String> reduce1ShiftMap = new HashMap<Character, String>();
		
		reduce1Map.put(Character.valueOf('`'), "1q");
		reduce1ShiftMap.put(Character.valueOf('`'), "!Q");
		reduce1Map.put(Character.valueOf('~'), "!Q");
		reduce1ShiftMap.put(Character.valueOf('~'), "1q");
		
		reduce1Map.put(Character.valueOf('1'), "2wq`");
		reduce1ShiftMap.put(Character.valueOf('1'), "@WQ~");
		reduce1Map.put(Character.valueOf('!'), "@WQ~");
		reduce1ShiftMap.put(Character.valueOf('!'), "2wq`");
		
		reduce1Map.put(Character.valueOf('2'), "3ewq1");
		reduce1ShiftMap.put(Character.valueOf('2'), "#EWQ!");
		reduce1Map.put(Character.valueOf('@'), "#EWQ!");
		reduce1ShiftMap.put(Character.valueOf('@'), "3ewq1");
		
		reduce1Map.put(Character.valueOf('3'), "4rew2");
		reduce1ShiftMap.put(Character.valueOf('3'), "$REW@");
		reduce1Map.put(Character.valueOf('#'), "$REW@");
		reduce1ShiftMap.put(Character.valueOf('#'), "4rew2");
		
		reduce1Map.put(Character.valueOf('4'), "5tre3");
		reduce1ShiftMap.put(Character.valueOf('4'), "%TRE#");
		reduce1Map.put(Character.valueOf('$'), "%TRE#");
		reduce1ShiftMap.put(Character.valueOf('$'), "5tre3");
		
		reduce1Map.put(Character.valueOf('5'), "6ytr4");
		reduce1ShiftMap.put(Character.valueOf('5'), "^YTR$");
		reduce1Map.put(Character.valueOf('%'), "^YTR$");
		reduce1ShiftMap.put(Character.valueOf('%'), "6ytr4");
		
		reduce1Map.put(Character.valueOf('6'), "7uyt5");
		reduce1ShiftMap.put(Character.valueOf('6'), "&UYT%");
		reduce1Map.put(Character.valueOf('^'), "&UYT%");
		reduce1ShiftMap.put(Character.valueOf('^'), "7uyt5");
		
		reduce1Map.put(Character.valueOf('7'), "8iuy6");
		reduce1ShiftMap.put(Character.valueOf('7'), "*IUY^");
		reduce1Map.put(Character.valueOf('&'), "*IUY^");
		reduce1ShiftMap.put(Character.valueOf('&'), "8iuy6");
		
		reduce1Map.put(Character.valueOf('8'), "9oiu7");
		reduce1ShiftMap.put(Character.valueOf('8'), "(OIU&");
		reduce1Map.put(Character.valueOf('*'), "(OIU&");
		reduce1ShiftMap.put(Character.valueOf('*'), "9oiu7");
		
		reduce1Map.put(Character.valueOf('9'), "0poi8");
		reduce1ShiftMap.put(Character.valueOf('9'), ")POI*");
		reduce1Map.put(Character.valueOf('('), ")POI*");
		reduce1ShiftMap.put(Character.valueOf('('), "0poi8");
		
		reduce1Map.put(Character.valueOf('0'), "-[po9");
		reduce1ShiftMap.put(Character.valueOf('0'), "_{PO(");
		reduce1Map.put(Character.valueOf(')'), "_{PO(");
		reduce1ShiftMap.put(Character.valueOf(')'), "-[po9");
		
		reduce1Map.put(Character.valueOf('-'), "=][p0");
		reduce1ShiftMap.put(Character.valueOf('-'), "+}{P)");
		reduce1Map.put(Character.valueOf('_'), "+}{P)");
		reduce1ShiftMap.put(Character.valueOf('_'), "=][p0");
		
		reduce1Map.put(Character.valueOf('='), "][-");
		reduce1ShiftMap.put(Character.valueOf('='), "}{_");
		reduce1Map.put(Character.valueOf('+'), "}{_");
		reduce1ShiftMap.put(Character.valueOf('+'), "][-");
		
		reduce1Map.put(Character.valueOf('q'), "wsa12");
		reduce1ShiftMap.put(Character.valueOf('q'), "WSA!@");
		
		reduce1Map.put(Character.valueOf('w'), "qasde321");
		reduce1ShiftMap.put(Character.valueOf('w'), "QASDE#@!");
		
		reduce1Map.put(Character.valueOf('e'), "rfdsw234");
		reduce1ShiftMap.put(Character.valueOf('e'), "RFDSW@#$");
		
		reduce1Map.put(Character.valueOf('r'), "tgfde345");
		reduce1ShiftMap.put(Character.valueOf('r'), "TGFDE#$%");
		
		reduce1Map.put(Character.valueOf('t'), "yhgfr456");
		reduce1ShiftMap.put(Character.valueOf('t'), "YHGFR$%^");
		
		reduce1Map.put(Character.valueOf('y'), "ujhgt567");
		reduce1ShiftMap.put(Character.valueOf('y'), "UJHGT%^&");
		
		reduce1Map.put(Character.valueOf('u'), "ikjhy678");
		reduce1ShiftMap.put(Character.valueOf('u'), "IKJHY^&*");
		
		reduce1Map.put(Character.valueOf('i'), "olkju789");
		reduce1ShiftMap.put(Character.valueOf('i'), "OLKJU&*(");
		
		reduce1Map.put(Character.valueOf('o'), "p;lki890");
		reduce1ShiftMap.put(Character.valueOf('o'), "P:LKI*()");
		
		reduce1Map.put(Character.valueOf('p'), "[';lo90-");
		reduce1ShiftMap.put(Character.valueOf('p'), "{\":LO()_");
		
		reduce1Map.put(Character.valueOf('['), "]';p0-=");
		reduce1ShiftMap.put(Character.valueOf('['), "}\":P)_+");
		reduce1Map.put(Character.valueOf('{'), "}\":P)_+");
		reduce1ShiftMap.put(Character.valueOf('{'), "]';p0-=");
		
		reduce1Map.put(Character.valueOf(']'), "\'[-=");
		reduce1ShiftMap.put(Character.valueOf(']'), "|\"{_+");
		reduce1Map.put(Character.valueOf('}'), "|\"{+");
		reduce1ShiftMap.put(Character.valueOf('}'), "\'[=");
		
		reduce1Map.put(Character.valueOf('\''), "]=");
		reduce1ShiftMap.put(Character.valueOf('\''), "}+");
		reduce1Map.put(Character.valueOf('|'), "}+");
		reduce1ShiftMap.put(Character.valueOf('|'), "]=");
		
		reduce1Map.put(Character.valueOf('/'), ".;'");
		reduce1ShiftMap.put(Character.valueOf('/'), "?>:\"");
		reduce1Map.put(Character.valueOf('?'), ">:\"");
		reduce1ShiftMap.put(Character.valueOf('?'), ".;'");
		
		reduce1Map.put(Character.valueOf('.'), ",l;/");
		reduce1ShiftMap.put(Character.valueOf('.'), "<L:?");
		reduce1Map.put(Character.valueOf('>'), "<L:?");
		reduce1ShiftMap.put(Character.valueOf('>'), ",l;/");
		
		reduce1Map.put(Character.valueOf(','), "mkl.");
		reduce1ShiftMap.put(Character.valueOf(','), "MKL>");
		reduce1Map.put(Character.valueOf('<'), "MKL>");
		reduce1ShiftMap.put(Character.valueOf('<'), "mkl.");
		
		reduce1Map.put(Character.valueOf('m'), "njk,");
		reduce1ShiftMap.put(Character.valueOf('m'), "NJK<");
		
		reduce1Map.put(Character.valueOf('n'), "bhjm");
		reduce1ShiftMap.put(Character.valueOf('n'), "BHJM");
		
		reduce1Map.put(Character.valueOf('b'), "vghn");
		reduce1ShiftMap.put(Character.valueOf('b'), "VGHN");
		
		reduce1Map.put(Character.valueOf('v'), "cfgb");
		reduce1ShiftMap.put(Character.valueOf('v'), "CFGB");
		
		reduce1Map.put(Character.valueOf('c'), "xdfv");
		reduce1ShiftMap.put(Character.valueOf('c'), "XDFV");
		
		reduce1Map.put(Character.valueOf('x'), "zsdc");
		reduce1ShiftMap.put(Character.valueOf('x'), "ZSDC");
		
		reduce1Map.put(Character.valueOf('z'), "asx");
		reduce1ShiftMap.put(Character.valueOf('z'), "ASX");
		
		reduce1Map.put(Character.valueOf('a'), "qwsxz");
		reduce1ShiftMap.put(Character.valueOf('a'), "QWSXZ");
		
		reduce1Map.put(Character.valueOf('s'), "qwedcxza");
		reduce1ShiftMap.put(Character.valueOf('s'), "QWEDCXZA");
		
		reduce1Map.put(Character.valueOf('d'), "werfvcxs");
		reduce1ShiftMap.put(Character.valueOf('d'), "WERFVCXS");
		
		reduce1Map.put(Character.valueOf('f'), "ertgbvcd");
		reduce1ShiftMap.put(Character.valueOf('f'), "ERTGBVCD");
		
		reduce1Map.put(Character.valueOf('g'), "rtyhnbvf");
		reduce1ShiftMap.put(Character.valueOf('g'), "RTYHNBVF");
		
		reduce1Map.put(Character.valueOf('h'), "tyujmnbg");
		reduce1ShiftMap.put(Character.valueOf('h'), "TYUJMNBG");
		
		reduce1Map.put(Character.valueOf('j'), "yuik,mnh");
		reduce1ShiftMap.put(Character.valueOf('j'), "YUIK<MNH");
		
		reduce1Map.put(Character.valueOf('k'), "uiol.,mj");
		reduce1ShiftMap.put(Character.valueOf('k'), "UIOL><MJ");
		
		reduce1Map.put(Character.valueOf('l'), "iop;/.,k");
		reduce1ShiftMap.put(Character.valueOf('l'), "IOP:?><K");
		
		reduce1Map.put(Character.valueOf(';'), "op['/.l");
		reduce1ShiftMap.put(Character.valueOf(';'), "OP{\"?>L");
		reduce1Map.put(Character.valueOf(':'), "OP{\"?>L");
		reduce1ShiftMap.put(Character.valueOf(':'), "op['/.l");
		
		Map<Character, Map<Character, Float>> tmp = new HashMap<Character, Map<Character, Float>>();
		for(Character c : reduce1Map.keySet()) {
			// handle uppercase variants
			Character d = Character.valueOf(Character.toUpperCase(c.charValue()));
			if(!tmp.containsKey(c)) {
				tmp.put(c, new HashMap<Character, Float>());
			}
			if(!tmp.containsKey(d)) {
				tmp.put(d, new HashMap<Character, Float>());
			}
			for(char x : reduce1Map.get(c).toCharArray()) {
				tmp.get(d).put(Character.valueOf(x), Float.valueOf(reduce1WeightShift));
				tmp.get(c).put(Character.valueOf(x), Float.valueOf(reduce1Weight));
			}
		}
		for(Character c : reduce1ShiftMap.keySet()) {
			// handle uppercase variants
			Character d = Character.valueOf(Character.toUpperCase(c.charValue()));
			if(!tmp.containsKey(c)) {
				tmp.put(c, new HashMap<Character, Float>());
			}
			if(!tmp.containsKey(d)) {
				tmp.put(d, new HashMap<Character, Float>());
			}
			for(char x : reduce1Map.get(c).toCharArray()) {
				tmp.get(c).put(Character.valueOf(x), Float.valueOf(reduce1WeightShift));
				tmp.get(d).put(Character.valueOf(x), Float.valueOf(reduce1Weight));
			}
		}
		USEngKeyboard = tmp;
	}
	
	/**
	 * Set the key weight mapping to use
	 * @param keyWeights the key weights
	 */
	public void setKeyWeights(Map<Character, Map<Character, Float>> keyWeights) {
		this.keyWeights = keyWeights;
	}
	
	@Override
	protected void distAugment(int editTypeIndex, char si, char bj) {
		super.distAugment(editTypeIndex, si, bj);
		if(editTypeIndex == 2) {
			try {
				this.decost += this.keyWeights.get(Character.valueOf(si)).get(Character.valueOf(bj)).floatValue();
			} catch(NullPointerException e) {
				// mapping must for small[i] to big[j] must not exist - ignore
			}
		}
	}
	
	@Override
	protected void resetAugment() {
		super.resetAugment();
		this.decost = 0f;
	}
	
	@Override
	protected float getAugment() {
		return (0 - this.decost) + super.getAugment();
	}
}
