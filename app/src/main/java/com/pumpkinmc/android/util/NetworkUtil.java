package com.pumpkinmc.android.util;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.Enumeration;

/**
 * Finds the device's local (LAN) IPv4 address, e.g. the address a
 * Minecraft client on the same Wi-Fi network would use to connect
 * (since Pumpkin binds to 0.0.0.0, which is not itself connectable).
 */
public class NetworkUtil {

    /**
     * @return the device's LAN IPv4 address (e.g. "192.168.1.42"),
     *         or null if none could be found (no Wi-Fi/Ethernet connection).
     */
    public static String getLocalIpAddress() {
        try {
            for (NetworkInterface iface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!iface.isUp() || iface.isLoopback() || iface.isVirtual()) continue;

                for (InetAddress addr : Collections.list(iface.getInetAddresses())) {
                    if (addr.isLoopbackAddress()) continue;
                    if (addr instanceof Inet4Address) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
            // No network available or permission issue; fall through to null
        }
        return null;
    }
}
