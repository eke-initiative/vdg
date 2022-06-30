package it.cnr.istc.stlab.lgu;

import com.google.common.escape.Escaper;
import com.google.common.escape.UnicodeEscaper;
import com.google.common.net.PercentEscaper;
import com.google.common.net.UrlEscapers;

public class URIUtils {

	static final int TRUE_LENGTH = 13;
	static final char[] STRING = { 'M', 'r', ' ', 'J', 'o', 'h', 'n', ' ', 'S', 'm', 'i', 't', 'h', ' ', ' ', ' ', ' ',
			' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ' };

	public static String URLify(String s) {
		return s.replaceAll("\\s", "_");
	}

	public static String getNamespace(String uri) {
		int prefixLastCharacter = uri.lastIndexOf('#');
		if (prefixLastCharacter < 0) {
			prefixLastCharacter = uri.lastIndexOf('/');
		}
		if (prefixLastCharacter < 0) {
			prefixLastCharacter = uri.lastIndexOf(':');
		}
		if (prefixLastCharacter < 0) {
			prefixLastCharacter = uri.length() - 1;
		}
		return uri.substring(0, prefixLastCharacter + 1);
	}

	public static String getID(final String uri) {
		for (int i = uri.length() - 1; i >= 0; i--) {
			if (uri.charAt(i) == '#' | uri.charAt(i) == '/' | uri.charAt(i) == ':') {
				return uri.substring(i + 1);
			}
		}
		return uri;
	}

	public static CharSequence getID(final CharSequence uri) {

		for (int i = uri.length() - 1; i >= 0; i--) {
			if (uri.charAt(i) == '#' | uri.charAt(i) == '/' | uri.charAt(i) == ':') {
				return uri.subSequence(i + 1, uri.length() - 1);
			}
		}
		return uri;
	}

	public static String normalizeURI(String uri) {
		return urlEscaper.escape(uri);
	}

	private static UnicodeEscaper basicEscaper = new PercentEscaper("_", false);
	private static Escaper urlEscaper = UrlEscapers.urlFragmentEscaper();

	public static String percentEncoding(final String string) {
		return basicEscaper.escape(string);
	}

	public static String percentEncodingID(final String uri) {
		return getNamespace(uri) + percentEncoding(getID(uri));

	}

}
