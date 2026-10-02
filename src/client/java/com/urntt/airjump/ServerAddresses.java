package com.urntt.airjump;

import com.google.common.net.HostAndPort;
import com.google.common.net.InetAddresses;
import com.google.common.net.InternetDomainName;
import java.net.IDN;
import java.util.Locale;
import java.util.OptionalInt;
import org.jspecify.annotations.Nullable;

/**
 * Matches server list entries against the address of the connected server.
 *
 * <p>Entries and addresses are parsed like vanilla's {@code ServerAddress}, but the host must also be a valid domain
 * name or IP address. Host names are compared case-insensitively after IDN conversion. A port is compared only when
 * the entry specifies one, so {@code mc.example.com} matches the server on any port.
 */
public final class ServerAddresses {
	private static final int DEFAULT_PORT = 25565;

	private ServerAddresses() {
	}

	/**
	 * Returns whether {@code entry} is a usable server list entry.
	 */
	public static boolean isValid(final String entry) {
		return parse(entry) != null;
	}

	/**
	 * Returns whether {@code entry} refers to the server at {@code address}, the address the player connected to.
	 */
	public static boolean matches(final String entry, final String address) {
		Parsed parsedEntry = parse(entry);
		Parsed parsedAddress = parse(address);
		if (parsedEntry == null || parsedAddress == null || !parsedEntry.host().equals(parsedAddress.host())) {
			return false;
		}
		return parsedEntry.port().isEmpty() || parsedEntry.port().getAsInt() == parsedAddress.port().orElse(DEFAULT_PORT);
	}

	private static @Nullable Parsed parse(final String text) {
		try {
			HostAndPort hostAndPort = HostAndPort.fromString(text.trim());
			String host = hostAndPort.getHost();
			if (host.isEmpty()) {
				return null;
			}
			String asciiHost = IDN.toASCII(host).toLowerCase(Locale.ROOT);
			if (!InetAddresses.isInetAddress(asciiHost) && !InternetDomainName.isValid(asciiHost)) {
				return null;
			}
			OptionalInt port = hostAndPort.hasPort() ? OptionalInt.of(hostAndPort.getPort()) : OptionalInt.empty();
			return new Parsed(asciiHost, port);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private record Parsed(String host, OptionalInt port) {
	}
}
