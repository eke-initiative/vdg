package it.cnr.istc.stlab.lgu;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import org.apache.commons.compress.utils.IOUtils;

public class Utils {

	public static String uncamelize(String s) {
		if (s == null) {
			return null;
		}

		if (s.length() == 0) {
			return "";
		}

		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < s.length() - 1; i++) {
			sb.append(s.charAt(i));
			if (Character.isLowerCase(s.charAt(i)) && Character.isUpperCase(s.charAt(i + 1))) {
				sb.append(' ');
			}
		}
		sb.append(s.charAt(s.length() - 1));
		return sb.toString();
	}

	public static void issueGET(String urlToGet, OutputStream os) {
		URL url;
		HttpURLConnection conn;
		try {
			url = new URL(urlToGet);
			conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("GET");
			IOUtils.copy(conn.getInputStream(), os);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}


	public static StringBuilder clean(final StringBuilder sb, Set<Character> toFilter) {
		IntStream.range(0, sb.length()).parallel().filter(i -> {
			return toFilter.contains(sb.charAt(i));
		}).forEach(i -> {
			sb.setCharAt(i, ' ');
		});

		return sb;
	}



	public static List<String> readPrefixesFromTSV(String path) throws IOException {
		BufferedReader br = new BufferedReader(new FileReader(new File(path)));
		List<String> result = new ArrayList<>();
		br.lines().forEach(line -> {
			String prefix = line.split("\t")[0];
			if (prefix.charAt(prefix.length() - 1) == '/') {
				result.add(prefix.subSequence(0, prefix.length() - 1).toString());
			} else {
				result.add(prefix);
			}
		});
		br.close();
		return result;
	}
}
