package org.vivoweb.harvester.util.xslt;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.WordUtils;

/**
 * @author jaf30
 */
public class AuthorTools {
	/**
	 * Default Constructor
	 */
	public AuthorTools() {}
	
	/**
	 * Normalize Author Name
	 * @param firstName the first name
	 * @param lastName the last name
	 * @param middleName the middle name
	 * @return normalized author name
	 */
	public static String normalizeAuthorName(String lastName, String firstName, String middleName) {
		String normLastName = new String();
		String normFirstName = new String();
		String normMiddleName = new String();
		
		if(StringUtils.isEmpty(lastName)) {
			return "";
		}
		normLastName = WordUtils.capitalize(lastName.toLowerCase());
		
		if(firstName != null && firstName.length() > 0) {
			normFirstName = WordUtils.capitalize(firstName).substring(0, 1);
		}
		
		if(middleName != null && middleName.length() > 0) {
			normMiddleName = WordUtils.capitalize(middleName).substring(0, 1);
		}
		return (normLastName + " " + normFirstName + normMiddleName).trim();
	}
}