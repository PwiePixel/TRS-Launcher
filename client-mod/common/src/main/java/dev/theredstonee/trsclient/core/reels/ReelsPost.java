package dev.theredstonee.trsclient.core.reels;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Ein Post im Feed (Gson-DTO, steht in {@code feed.json}). Die Oberfläche liest die Felder nur; geändert wird über das
 * {@link ReelsBackend} (Like, Kommentar).
 */
public final class ReelsPost {
	/** Höchstlänge des Texts zum Foto (Zeichen). */
	public static final int MAX_CAPTION = 80;
	/** Höchstlänge eines Namens (Zeichen). */
	public static final int MAX_AUTHOR = 32;

	/** {@code yyyyMMdd-HHmmss-<4 hex>} – zugleich der Dateiname des Fotos ohne Endung. */
	public String id;
	public String author;
	public String caption;
	/** Dateiname des Fotos im Ordner {@code photos} (nie ein Pfad). */
	public String photo;
	public int width;
	public int height;
	public boolean selfie;
	/** Hochkant aufgenommen (9:16) – sonst quer (16:9). */
	public boolean portrait;
	public boolean likedByMe;
	public int likes;
	/** Zeitpunkt des Postens (Millisekunden seit 1970). */
	public long createdAt;
	/** Womit das Foto aufgenommen wurde – null bei alten Posts und wenn nichts bekannt war. */
	public ReelsMeta meta;
	public List<ReelsComment> comments = new ArrayList<ReelsComment>();

	public ReelsPost() {
	}

	public ReelsPost copy() {
		ReelsPost p = new ReelsPost();
		p.id = id;
		p.author = author;
		p.caption = caption;
		p.photo = photo;
		p.width = width;
		p.height = height;
		p.selfie = selfie;
		p.portrait = portrait;
		p.likedByMe = likedByMe;
		p.likes = likes;
		p.createdAt = createdAt;
		p.meta = meta == null ? null : meta.copy();
		p.comments = new ArrayList<ReelsComment>();
		if (comments != null) {
			for (ReelsComment c : comments) if (c != null) p.comments.add(c.copy());
		}
		return p;
	}

	/** Neue ID zum Zeitpunkt {@code now}: {@code yyyyMMdd-HHmmss-<4 hex>} ({@code salt} macht sie eindeutig). */
	public static String newId(long now, TimeZone zone, int salt) {
		SimpleDateFormat f = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT);
		f.setTimeZone(zone != null ? zone : TimeZone.getTimeZone("UTC"));
		String hex = Integer.toHexString(salt & 0xFFFF);
		return f.format(new Date(now)) + "-" + "0000".substring(hex.length()) + hex;
	}

	/** Gültige ID (nur Ziffern, Kleinbuchstaben a–f und Bindestriche in der festen Form)? */
	public static boolean validId(String id) {
		if (id == null || id.length() != 20) return false;
		for (int i = 0; i < id.length(); i++) {
			char c = id.charAt(i);
			if (i == 8 || i == 15) {
				if (c != '-') return false;
			} else if (i < 15) {
				if (c < '0' || c > '9') return false;
			} else if (!(c >= '0' && c <= '9' || c >= 'a' && c <= 'f')) {
				return false;
			}
		}
		return true;
	}

	/** Dateiname eines Fotos: {@code <id>.png} – nichts anderes wird je geöffnet oder gelöscht. */
	public static boolean validPhotoName(String name) {
		return name != null && name.endsWith(".png") && validId(name.substring(0, name.length() - 4));
	}

	/**
	 * Text für Feed und Kommentare: Steuerzeichen und Zeilenumbrüche werden zu Leerzeichen, Ränder weg, höchstens
	 * {@code max} Zeichen (ohne ein Zeichenpaar zu zerschneiden). Ein halbes Zeichenpaar (ein Emoji aus der
	 * Zwischenablage, von dem eine Hälfte gelöscht oder abgeschnitten wurde) fällt weg: es ist kein Zeichen und ließe
	 * sich nicht als UTF-8 schreiben.
	 */
	public static String clean(String text, int max) {
		if (text == null) return "";
		StringBuilder b = new StringBuilder(Math.min(text.length(), max + 8));
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (Character.isHighSurrogate(c)) {
				// Nur als ganzes Paar.
				if (i + 1 < text.length() && Character.isLowSurrogate(text.charAt(i + 1))) b.append(c).append(text.charAt(++i));
				continue;
			}
			if (Character.isLowSurrogate(c)) continue;
			b.append(c < ' ' || c == 0x7F || c == '§' ? ' ' : c);
		}
		String s = b.toString().trim();
		if (s.length() > max) {
			int end = max;
			if (Character.isHighSurrogate(s.charAt(end - 1))) end--;
			s = s.substring(0, end).trim();
		}
		return s;
	}
}
