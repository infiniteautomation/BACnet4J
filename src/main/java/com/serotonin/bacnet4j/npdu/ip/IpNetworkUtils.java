/*
 * ============================================================================
 * GNU General Public License
 * ============================================================================
 *
 * Copyright (C) 2015 Infinite Automation Software. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * When signing a commercial license with Infinite Automation Software,
 * the following extension to GPL is made. A special exception to the GPL is
 * included to allow you to distribute a combined work that includes BAcnet4J
 * without being obliged to provide the source code for any proprietary components.
 *
 * See www.infiniteautomation.com for commercial license options.
 *
 * @author Matthew Lohbihler
 */
package com.serotonin.bacnet4j.npdu.ip;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.serotonin.bacnet4j.type.constructed.Address;
import com.serotonin.bacnet4j.type.primitive.OctetString;
import com.serotonin.bacnet4j.util.BACnetUtils;
import com.serotonin.bacnet4j.util.sero.ByteQueue;
import com.serotonin.bacnet4j.util.sero.IpAddressUtils;

public class IpNetworkUtils {
    public static OctetString toOctetString(final String dottedString) {
        final String s = dottedString.trim();
        final int colon = s.indexOf(":");
        if (colon == -1)
            throw new IllegalArgumentException("Dotted string missing port number. Expected x.x.x.x:port");

        final byte[] ip = BACnetUtils.dottedStringToBytes(s.substring(0, colon));
        final int port = Integer.parseInt(s.substring(colon + 1));
        return toOctetString(ip, port);
    }

    public static OctetString toOctetString(final byte[] ipAddress, final int port) {
        return new OctetString(toBytes(ipAddress, port));
    }

    public static OctetString toOctetString(final String dotted, final int port) {
        return toOctetString(BACnetUtils.dottedStringToBytes(dotted), port);
    }

    public static OctetString toOctetString(final ByteQueue queue) {
        final byte[] b = new byte[6];
        queue.pop(b);
        return new OctetString(b);
    }

    public static OctetString toOctetString(final InetSocketAddress addr) {
        return toOctetString(addr.getAddress().getAddress(), addr.getPort());
    }

    private static byte[] toBytes(final byte[] ipAddress, final int port) {
        if (ipAddress.length != 4)
            throw new IllegalArgumentException("IP address must have 4 parts, not " + ipAddress.length);

        final byte[] b = new byte[6];
        System.arraycopy(ipAddress, 0, b, 0, ipAddress.length);
        b[ipAddress.length] = (byte) (port >> 8);
        b[ipAddress.length + 1] = (byte) port;
        return b;
    }

    public static InetAddress getInetAddress(final OctetString mac) {
        try {
            return InetAddress.getByAddress(getIpBytes(mac));
        } catch (final UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    public static InetSocketAddress getInetSocketAddress(final OctetString mac) {
        return InetAddrCache.get(getInetAddress(mac), getPort(mac));
    }

    public static int getPort(final OctetString mac) {
        if (mac.getLength() != 6)
            throw new IllegalArgumentException("Not an I/P mac");
        return (mac.getBytes()[4] & 0xff) << 8 | mac.getBytes()[5] & 0xff;
    }

    public static String toIpString(final OctetString mac) {
        return IpAddressUtils.toIpString(getIpBytes(mac));
    }

    public static String toIpPortString(final OctetString mac) {
        return toIpString(mac) + ":" + getPort(mac);
    }

    public static byte[] getIpBytes(final OctetString mac) {
        if (mac.getLength() != 6)
            throw new IllegalArgumentException("Not an I/P mac");
        final byte[] b = new byte[4];
        System.arraycopy(mac.getBytes(), 0, b, 0, 4);
        return b;
    }

    public static String toString(final OctetString mac) {
        return toIpPortString(mac);
    }

    public static Address toAddress(final byte[] ipAddress, final int port) {
        return toAddress(Address.LOCAL_NETWORK, ipAddress, port);
    }

    /**
     * Convenience constructor for IP addresses remote to this network.
     *
     * @param network
     * @param ipAddress
     * @param port
     */
    public static Address toAddress(final int networkNumber, final byte[] ipAddress, final int port) {
        final byte[] ipMacAddress = new byte[ipAddress.length + 2];
        System.arraycopy(ipAddress, 0, ipMacAddress, 0, ipAddress.length);
        ipMacAddress[ipAddress.length] = (byte) (port >> 8);
        ipMacAddress[ipAddress.length + 1] = (byte) port;
        return new Address(networkNumber, new OctetString(ipMacAddress));
    }

    public static Address toAddress(final String host, final int port) {
        return toAddress(Address.LOCAL_NETWORK, host, port);
    }

    public static Address toAddress(final int networkNumber, final String host, final int port) {
        return toAddress(networkNumber, InetAddrCache.get(host, port));
    }

    public static Address toAddress(final InetSocketAddress addr) {
        return toAddress(Address.LOCAL_NETWORK, addr.getAddress().getAddress(), addr.getPort());
    }

    public static Address toAddress(final int networkNumber, final InetSocketAddress addr) {
        return toAddress(networkNumber, addr.getAddress().getAddress(), addr.getPort());
    }

    public static List<InterfaceAddress> getLocalInterfaceAddresses() {
        try {
            final List<InterfaceAddress> result = new ArrayList<>();
            for (final NetworkInterface iface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                for (final InterfaceAddress addr : iface.getInterfaceAddresses()) {
                    if (!addr.getAddress().isLoopbackAddress() && addr.getAddress().isSiteLocalAddress())
                        result.add(addr);
                }
            }
            return result;
        } catch (final Exception e) {
            // Should never happen, so just wrap in a RuntimeException
            throw new RuntimeException(e);
        }
    }

    public static long bytesToLong(final byte[] ba) {
        return (ba[0] & 0xffL) << 24 | (ba[1] & 0xffL) << 16 | (ba[2] & 0xffL) << 8 | ba[3] & 0xffL;
    }

    public static long createMask(final int length // 24) {
        long l = 0; // long = 8 byte = 64 bit
        int shift = 31;
        for (int i = 0; i < length; i++) {
            l |= 1L << shift--;
        }
        return l; // 高32位为0，低32位为11111111 11111111 11111111 00000000
    }

    public static long toBroadcast(final long ipaddr, final long subnet) {
        return 0xFFFFFFFFL ^ subnet | ipaddr;
    }

    public static String toIpAddrString(final long addr // 高32位为0，低32位为11111111 11111111 11111111 00000000) {
        final StringBuilder sb = new StringBuilder();
        sb.append(addr >> 24 & 0xFF).append('.'); // addr >> 24 = 00000000 00000000 00000000 11111111, & 0xFF = 11111111 = 255
        sb.append(addr >> 16 & 0xFF).append('.'); // addr >> 16 = 00000000 00000000 11111111 11111111, 最后结果跟上面一样
        sb.append(addr >> 8 & 0xFF).append('.');  // addr >> 8  = 00000000 11111111 11111111 11111111, 最后结果跟上面一样
        sb.append(addr & 0xFF); // 11111111 11111111 11111111 00000000 & 00000000 00000000 00000000 11111111 = 0
        return sb.toString(); // "255.255.255.0"
    }
}
