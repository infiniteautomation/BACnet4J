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

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.serotonin.bacnet4j.enums.MaxApduLength;
import com.serotonin.bacnet4j.exception.BACnetException;
import com.serotonin.bacnet4j.npdu.MessageValidationException;
import com.serotonin.bacnet4j.npdu.NPDU;
import com.serotonin.bacnet4j.npdu.Network;
import com.serotonin.bacnet4j.npdu.NetworkIdentifier;
import com.serotonin.bacnet4j.transport.Transport;
import com.serotonin.bacnet4j.type.constructed.Address;
import com.serotonin.bacnet4j.type.primitive.OctetString;
import com.serotonin.bacnet4j.util.BACnetUtils;
import com.serotonin.bacnet4j.util.sero.ByteQueue;

/**
 * Use IpNetworkBuilder to create.
 */
public class IpNetwork extends Network implements Runnable {
    static final Logger LOG = LoggerFactory.getLogger(IpNetwork.class);

    public static final byte BVLC_TYPE = (byte) 0x81;
    public static final int DEFAULT_PORT = 0xBAC0; // == 47808
    public static final String DEFAULT_BIND_IP = "0.0.0.0";

    private static final int MESSAGE_LENGTH = 2048;

    private final int port;
    private final String localBindAddressStr;
    private final String broadcastAddressStr;
    private final String subnetMaskStr;
    private final boolean reuseAddress;

    // BBMD support
    private List<BDTEntry> broadcastDistributionTable = new ArrayList<>();
    final List<FDTEntry> foreignDeviceTable = new CopyOnWriteArrayList<>();
    private ScheduledFuture<?> ftdMaintenance;

    // When acting as a foreign device...
    final Object foreignBBMDLock = new Object();
    InetSocketAddress foreignBBMD;
    private int foreignTTL;
    private int bbmdResponse;
    private ScheduledFuture<?> foreignRegistrationMaintenance;

    // Runtime
    private Thread thread;
    private DatagramSocket socket;
    private OctetString broadcastMAC;
    private InetSocketAddress localBindAddress;
    private byte[] subnetMask;
    private long bytesOut;
    private long bytesIn;

    /**
     * Use an IpNetworkBuilder to create instances.
     */
    IpNetwork(final int port, final String localBindAddress, final String broadcastAddress, final String subnetMask,
            final int localNetworkNumber, final boolean reuseAddress) {
        super(localNetworkNumber);
        this.port = port;
        this.localBindAddressStr = localBindAddress;
        this.broadcastAddressStr = broadcastAddress;
        this.subnetMaskStr = subnetMask;
        this.reuseAddress = reuseAddress;
    }

    @Override
    public NetworkIdentifier getNetworkIdentifier() {
        return new IpNetworkIdentifier(port, localBindAddressStr);
    }

    @Override
    public MaxApduLength getMaxApduLength() {
        return MaxApduLength.UP_TO_1476;
    }

    public int getPort() {
        return port;
    }

    public InetSocketAddress getLocalBindAddress() {
        return localBindAddress;
    }

    public String getBroadcastAddresss() {
        return broadcastAddressStr;
    }

    @Override
    public long getBytesOut() {
        return bytesOut;
    }

    @Override
    public long getBytesIn() {
        return bytesIn;
    }

    // For testing purposes.
    DatagramSocket getSocket() {
        return socket;
    }

    @Override
    public void initialize(final Transport transport) throws Exception {
        super.initialize(transport);
        // InetAddrCache中有个static Map<InetAddress, Map<Integer, InetSocketAddress>> socketCache
        // 根据localBindAddressStr创建InetAddress，一个InetAddress可以根据多个port创建多个InetSocketAddress
        localBindAddress = InetAddrCache.get(localBindAddressStr, port);

        if (reuseAddress) {
            socket = new DatagramSocket();
            socket.setReuseAddress(true);
            if (!socket.getReuseAddress())
                LOG.warn("reuseAddress was set, but not supported by the underlying platform");
            socket.bind(localBindAddress);
        } else
            socket = new DatagramSocket(localBindAddress);
        socket.setBroadcast(true);

        //        broadcastAddress = new Address(broadcastIp, port, new Network(0xffff, new byte[0]));
        broadcastMAC = IpNetworkUtils.toOctetString(broadcastAddressStr, port);// broadcastMAC里面有byte[6]，前4个byte是broadcastAddress，后2个byte是port
        subnetMask = BACnetUtils.dottedStringToBytes(subnetMaskStr);// 将"255.255.255.0"转换成byte[4]

        thread = new Thread(this, "BACnet4J IP socket listener");
        thread.start();
    }

    @Override
    public void terminate() {
        unregisterAsForeignDevice();
        if (socket != null)
            socket.close();
        if (ftdMaintenance != null)
            ftdMaintenance.cancel(false);
    }

    @Override
    protected OctetString getBroadcastMAC() {
        return broadcastMAC;
    }

    public Address getBroadcastAddress(final int port) {
        return IpNetworkUtils.toAddress(broadcastAddressStr, port);
    }

    /**
     * Attempts an initial registration of this device as a foreign device in a BBMD. This method blocks until the
     * response (ACK or NAK) is received, or a timeout occurs (no response).
     *
     * If the device is successfully registered (ACK), regular re-registration requests will be sent to maintain the
     * registration. If any of these requests fail, the local device's exception dispatcher will be notified.
     *
     * If the device is to be un-registered, the registerAsForeignDevice method should be called. This will be done
     * automatically if the local device is terminated.
     *
     * @param addr
     *            The address of the BBMD where our device wants to be registered
     * @param timeToLive
     *            The time until we are automatically removed out of the FDT
     * @throws BACnetException
     *             if a timeout occurs, a NAK is received, the device is already registered as a foreign device, or
     *             the request could not be sent.
     */
    public void registerAsForeignDevice(final InetSocketAddress addr, final int timeToLive) throws BACnetException {
        if (timeToLive < 1)
            throw new IllegalArgumentException("timeToLive cannot be less than 1");
        if (getTransport() == null || getTransport().getLocalDevice() == null)
            throw new IllegalArgumentException(
                    "Network must be used within a local device before foreign device registration can be performed.");

        synchronized (foreignBBMDLock) {
            if (foreignBBMD != null)
                throw new BACnetException("Already registered as a foreign device");

            foreignBBMD = addr;
            foreignTTL = timeToLive;

            try {
                sendForeignDeviceRegistration();
            } catch (final BACnetException e) {
                foreignBBMD = null;
                throw e;
            }

            // No exception occurred, so create a task that will re-register this device at appropriate intervals.
            // Including the grace period, this should result in rereg 1 minute before expiration.
            int interval = timeToLive - 30;
            // ... but since the ttl can be less than 30, we need to correct.
            if (interval < 15)
                // Minimum of 15s.
                interval = 15;
            foreignRegistrationMaintenance = getTransport().getLocalDevice().scheduleAtFixedRate(() -> {
                synchronized (foreignBBMDLock) {
                    // Check just in case...
                    if (foreignBBMD == null)
                        return;

                    try {
                        sendForeignDeviceRegistration();
                    } catch (final BACnetException e) {
                        getTransport().getLocalDevice().getExceptionDispatcher().fireReceivedException(e);
                    }
                }
            }, interval, interval, TimeUnit.SECONDS);
        }
    }

    /**
     * Requests that this device be un-registered at the location at which it was previously registered. Quietly
     * ignores the case where it is not already registered.
     */
    public void unregisterAsForeignDevice() {
        synchronized (foreignBBMDLock) {
            if (foreignBBMD != null) {
                try {
                    deleteForeignDeviceTableEntry(foreignBBMD, localBindAddress);
                } catch (final BACnetException e) {
                    LOG.info("Device de-registration failed", e);
                }
            }
            if (foreignRegistrationMaintenance != null)
                foreignRegistrationMaintenance.cancel(false);
            foreignBBMD = null;
            foreignRegistrationMaintenance = null;
        }
    }

    @Override
    public void sendNPDU(final Address recipient, final OctetString router, final ByteQueue npdu,
            final boolean broadcast, final boolean expectsReply) throws BACnetException {
        final ByteQueue queue = new ByteQueue();

        // BACnet virtual link layer detail
        queue.push(BVLC_TYPE);

        InetSocketAddress addr = foreignBBMD;
        if (addr != null && broadcast) {
            // Distribute-Broadcast-To-Network. This device is registered as a foreign device in a BBMD, so send the
            // message as a distribute broadcast to network.
            queue.push(9);
        } else {
            // Original-Unicast-NPDU, or Original-Broadcast-NPDU
            queue.push(broadcast ? 0xb : 0xa);

            final OctetString dest = getDestination(recipient, router);
            addr = IpNetworkUtils.getInetSocketAddress(dest);
        }

        queue.pushU2B(npdu.size() + 4);

        // Combine the queues
        queue.push(npdu);

        sendPacket(addr, queue.popAll());
    }

    private void sendPacket(final InetSocketAddress addr, final byte[] data) throws BACnetException {
        try {
            final DatagramPacket packet = new DatagramPacket(data, data.length, addr);
            socket.send(packet);
            bytesOut += data.length;
        } catch (final Exception e) {
            throw new BACnetException(e);
        }
    }

    //
    // For receiving
    @Override
    public void run() {
        final byte[] buffer = new byte[MESSAGE_LENGTH];
        final DatagramPacket p = new DatagramPacket(buffer, buffer.length);

        while (!socket.isClosed()) {
            try {
                socket.receive(p);

                bytesIn += p.getLength();
                // Create a new byte queue for the message, because the queue will probably be processed in the
                // transport thread.
                final ByteQueue queue = new ByteQueue(p.getData(), 0, p.getLength());
                final OctetString link = IpNetworkUtils.toOctetString(p.getAddress().getAddress(), p.getPort());

                handleIncomingData(queue, link);

                // Reset the packet.
                p.setData(buffer);
            } catch (@SuppressWarnings("unused") final IOException e) {
                // no op. This happens if the socket gets closed by the destroy method.
            }
        }
    }

    @Override
    protected NPDU handleIncomingDataImpl(final ByteQueue queue, final OctetString linkService) throws Exception {
        LOG.trace("Received request from {}", linkService);

        // Initial parsing of IP message.
        // BACnet/IP
        if (queue.pop() != BVLC_TYPE)
            throw new MessageValidationException("Protocol id is not BACnet/IP (0x81)");

        final byte function = queue.pop();

        final int length = BACnetUtils.popShort(queue);
        if (length != queue.size() + 4)
            throw new MessageValidationException(
                    "Length field does not match data: given=" + length + ", expected=" + (queue.size() + 4));

        NPDU npdu = null;
        if (function == 0x0) {
            final int result = BACnetUtils.popShort(queue);
            if (result == 0x30)
                LOG.error("Register-Foreign-Device failed!");
            else if (result == 0x50)
                LOG.error("Delete-Foreign-Device-Table-Entry failed!");
            else if (result == 0x60)
                LOG.error("Distribute-Broadcast-To-Network failed!");
            else if (result != 0)
                LOG.warn("Received unexpected BVLC result: " + result);

            // Response management.
            bbmdResponse = result;
            final InetSocketAddress addr = foreignBBMD;
            if (addr != null) {
                synchronized (addr) {
                    addr.notifyAll();
                }
            }
        } else if (function == 0x1)
            // Write-Broadcast-Distribution-Table
            writeBDT(queue, linkService);
        else if (function == 0x2)
            // Read-Broadcast-Distribution-Table
            readBDT(linkService);
        else if (function == 0x3)
            // Not implemented because this does not send Read-Broadcast-Distribution-Table requests, and so should
            // not receive any responses.
            throw new BACnetException("Read-Broadcast-Distribution-Table-Ack not implemented");
        else if (function == 0x4) {
            // Forwarded-NPDU.
            // If this is a BBMD, it could forward this message to its local broadcast address, meaning it could
            // receive its own forwards. Check if the link service is itself, and ignore if so.
            if (!linkService.equals(IpNetworkUtils.toOctetString(localBindAddress))) {
                forwardNPDU(queue, linkService);

                // Process the NPDU locally
                final byte[] address = new byte[6];
                queue.pop(address);
                final OctetString origin = new OctetString(address);
                npdu = parseNpduData(queue, origin);
            }
        } else if (function == 0x5)
            // Register-Foreign-Device
            registerForeignDevice(queue, linkService);
        else if (function == 0x6)
            // Read-Foreign-Device-Table
            readFDT(linkService);
        else if (function == 0x7)
            // Not implemented because this does not send Read-Foreign-Device-Table requests, and so should
            // not receive any responses.
            throw new BACnetException("Read-Foreign-Device-Table-Ack not implemented");
        else if (function == 0x8)
            // Delete-Foreign-Device-Table-Entry
            deleteFDT(queue, linkService);
        else if (function == 0x9) {
            // Distribute-Broadcast-To-Network
            final boolean ok = distributeBroadcastToNetwork(queue, linkService);
            if (ok)
                // Only process locally if the foreign device is valid.
                npdu = parseNpduData(queue, linkService);
        } else if (function == 0xa)
            // Original-Unicast-NPDU
            npdu = parseNpduData(queue, linkService);
        else if (function == 0xb) {
            // Original-Broadcast-NPDU
            originalBroadcast(queue, linkService);

            npdu = parseNpduData(queue, linkService);
        } else
            throw new MessageValidationException(
                    "Unhandled BVLC function type: 0x" + Integer.toHexString(function & 0xff));

        return npdu;
    }

    //
    //
    // Convenience methods
    //
    public Address getAddress(final InetAddress inetAddress) {
        try {
            return IpNetworkUtils.toAddress(getLocalNetworkNumber(), inetAddress.getAddress(), port);
        } catch (final Exception e) {
            // Should never happen, so just wrap in a RuntimeException
            throw new RuntimeException(e);
        }
    }

    public static InetAddress getDefaultLocalInetAddress() throws UnknownHostException, SocketException {
        for (final NetworkInterface iface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            for (final InetAddress addr : Collections.list(iface.getInetAddresses())) {
                if (!addr.isLoopbackAddress() && addr.isSiteLocalAddress())
                    return addr;
            }
        }

        return InetAddress.getLocalHost();
    }

    @Override
    public Address[] getAllLocalAddresses() {
        try {
            final ArrayList<Address> result = new ArrayList<>();
            for (final NetworkInterface iface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                for (final InetAddress addr : Collections.list(iface.getInetAddresses())) {
                    if (!addr.isLoopbackAddress() && addr.isSiteLocalAddress())
                        result.add(getAddress(addr));
                }
            }

            // Check if the configured local bind address is one of the addresses. If not, add it. This is
            // necessary if the local bind address is a loopback.
            final Address config = IpNetworkUtils.toAddress(localBindAddress);
            boolean found = false;
            for (final Address addr : result) {
                if (addr.equals(config)) {
                    found = true;
                    break;
                }
            }

            if (!found)
                result.add(config);

            return result.toArray(new Address[result.size()]);
        } catch (final Exception e) {
            // Should never happen, so just wrap in a RuntimeException
            throw new RuntimeException(e);
        }
    }

    @Override
    public Address getLoopbackAddress() {
        return IpNetworkUtils.toAddress("127.0.0.1", DEFAULT_PORT);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + (broadcastAddressStr == null ? 0 : broadcastAddressStr.hashCode());
        result = prime * result + (localBindAddressStr == null ? 0 : localBindAddressStr.hashCode());
        result = prime * result + port;
        return result;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        final IpNetwork other = (IpNetwork) obj;
        if (broadcastAddressStr == null) {
            if (other.broadcastAddressStr != null)
                return false;
        } else if (!broadcastAddressStr.equals(other.broadcastAddressStr))
            return false;
        if (localBindAddressStr == null) {
            if (other.localBindAddressStr != null)
                return false;
        } else if (!localBindAddressStr.equals(other.localBindAddressStr))
            return false;
        if (port != other.port)
            return false;
        return true;
    }

    //
    //
    // BBMD
    //
    static class BDTEntry {
        // The IP address of the other BBMD
        byte[] address;
        int port;

        // If messages are to be distributed on the remote IP subnet using directed broadcasts, the broadcast
        // distribution mask shall be identical to the subnet mask associated with the subnet, i.e., all 1's in the
        // network portion of the 4-octet IP address field and all 0's in the host portion. If messages are to be
        // distributed on the remote IP subnet by sending the message directly to the remote BBMD, the broadcast
        // distribution mask shall be all 1's. The broadcast distribution masks referring to the same IP subnet shall
        // be identical in each BDT.
        byte[] distributionMask;
    }

    static class FDTEntry {
        InetSocketAddress address;
        int timeToLive;
        long endTime;
    }

    private void writeBDT(final ByteQueue queue, final OctetString origin) throws BACnetException {
        final ByteQueue response = new ByteQueue();
        response.push(BVLC_TYPE);
        response.push(0); // Result
        response.pushU2B(6); // Length

        try {
            final List<BDTEntry> list = new ArrayList<>();

            while (queue.size() > 0) {
                final BDTEntry e = new BDTEntry();
                e.address = new byte[4];
                queue.pop(e.address);
                e.port = queue.popU2B();
                e.distributionMask = new byte[4];
                queue.pop(e.distributionMask);
                list.add(e);
            }

            // Successfully read. Replace the current BDT.
            broadcastDistributionTable = list;

            response.pushU2B(0); // Ok
        } catch (final Exception e) {
            LOG.error("BDT write failed", e);
            response.pushU2B(0x10); // NAK
        }

        sendPacket(IpNetworkUtils.getInetSocketAddress(origin), response.popAll());
    }

    private void readBDT(final OctetString origin) throws BACnetException {
        final ByteQueue response = new ByteQueue();
        response.push(BVLC_TYPE);

        try {
            final ByteQueue list = new ByteQueue();

            for (final BDTEntry e : broadcastDistributionTable) {
                list.push(e.address);
                list.pushU2B(e.port);
                list.push(e.distributionMask);
            }

            // Successfully written.
            response.push(3); // Read-Broadcast-Distribution-Table-Ack
            response.pushU2B(4 + list.size()); // Length
            response.push(list); // List
        } catch (final Exception e) {
            LOG.error("BDT read failed", e);
            response.push(0); // Result
            response.pushU2B(6); // Length
            response.pushU2B(0x20); // NAK
        }

        sendPacket(IpNetworkUtils.getInetSocketAddress(origin), response.popAll());
    }

    private void forwardNPDU(final ByteQueue partial, final OctetString origin) throws BACnetException {
        // Determine whether to the message should be broadcast locally.
        boolean doLocalBroadcast = !broadcastDistributionTable.isEmpty();

        if (doLocalBroadcast) {
            // 1) If the origin is on the same subnet, do not broadcast locally.
            boolean fromSameSubnet = true;
            for (int i = 0; i < 4; i++) {
                final int b1 = localBindAddress.getAddress().getAddress()[i] & subnetMask[i];
                final int b2 = origin.getBytes()[i] & subnetMask[i];
                if (b1 != b2) {
                    fromSameSubnet = false;
                    break;
                }
            }

            if (fromSameSubnet)
                doLocalBroadcast = false;
        }

        if (doLocalBroadcast) {
            // 2) If the mask of the BDT entry for this BBMD is not all 1s, do not broadcast locally.
            final byte[] myAddress = localBindAddress.getAddress().getAddress();

            // Find this BDT entry.
            BDTEntry thisEntry = null;
            for (final BDTEntry e : broadcastDistributionTable) {
                if (Arrays.equals(e.address, myAddress)) {
                    thisEntry = e;
                    break;
                }
            }

            if (thisEntry == null) {
                // Not found. This is a configuration problem. Don't broadcast.
                LOG.warn("Configuration error: could not find BDT entry for this instance.");
                doLocalBroadcast = false;
            } else {
                // We only need to check the last byte (the last bit in actually), because any zeros all need to
                // be on the right.
                if (thisEntry.distributionMask[3] != (byte) 255)
                    doLocalBroadcast = false;
            }
        }

        // Check if anything needs to be done.
        if (foreignDeviceTable.isEmpty() && !doLocalBroadcast)
            return;

        // The BVLC type, function and length were removed from this queue, so recreate.
        final ByteQueue fwd = new ByteQueue();
        fwd.push(BVLC_TYPE);
        fwd.push(4); // Forward
        fwd.pushU2B(4 + partial.size()); // Length
        fwd.push(partial);
        final byte[] toSend = fwd.popAll();

        if (doLocalBroadcast)
            sendPacket(InetAddrCache.get(broadcastAddressStr, port), toSend);

        // Forward to all foreign devices.
        for (final FDTEntry fd : foreignDeviceTable)
            sendPacket(fd.address, toSend);
    }

    private void originalBroadcast(final ByteQueue partial, final OctetString originStr) throws BACnetException {
        // Check if anything needs to be done.
        if (foreignDeviceTable.isEmpty() && broadcastDistributionTable.isEmpty())
            return;

        final ByteQueue fwd = new ByteQueue();
        fwd.push(BVLC_TYPE);
        fwd.push(4); // Forward
        fwd.pushU2B(10 + partial.size()); // Length
        fwd.push(originStr.getBytes()); // Origin
        fwd.push(partial);
        final byte[] toSend = fwd.popAll();

        try {
            final byte[] myAddress = localBindAddress.getAddress().getAddress();

            // Send to all subnets except own
            for (final BDTEntry e : broadcastDistributionTable) {
                if (Arrays.equals(e.address, myAddress))
                    continue;
                sendToBDT(e, toSend);
            }

            // Forward to all foreign devices.
            for (final FDTEntry fd : foreignDeviceTable)
                sendPacket(fd.address, toSend);
        } catch (final UnknownHostException e) {
            throw new BACnetException(e);
        }
    }

    private void registerForeignDevice(final ByteQueue queue, final OctetString originStr) throws BACnetException {
        final InetSocketAddress origin = IpNetworkUtils.getInetSocketAddress(originStr);
        LOG.debug("Recieved registerForeignDevice request from {}", origin);

        final ByteQueue response = new ByteQueue();
        response.push(BVLC_TYPE);
        response.push(0); // Response type
        response.pushU2B(6); // Length

        final int timeToLive = queue.popU2B();
        if (timeToLive < 1)
            response.pushU2B(0x30); // NAK
        else {
            // Check if the device is already in the list. If so, update its start time. Otherwise, add it.
            FDTEntry fd = null;
            synchronized (foreignDeviceTable) {
                for (final FDTEntry e : foreignDeviceTable) {
                    if (e.address.equals(origin)) {
                        fd = e;
                        break;
                    }
                }

                if (fd == null) {
                    // Add the FDT entry
                    fd = new FDTEntry();
                    fd.address = origin;
                    foreignDeviceTable.add(fd);

                    if (ftdMaintenance == null) {
                        // Add a job to expire foreign device registrations.
                        ftdMaintenance = getTransport().getLocalDevice().scheduleAtFixedRate(() -> {
                            final long now = getTransport().getLocalDevice().getClock().millis();

                            synchronized (foreignDeviceTable) {
                                final List<FDTEntry> toRemove = new ArrayList<>();

                                for (final FDTEntry e : foreignDeviceTable) {
                                    if (e.endTime < now) {
                                        LOG.debug("Removing expired foreign device: " + e);
                                        toRemove.add(e);
                                    }
                                }

                                if (!toRemove.isEmpty())
                                    foreignDeviceTable.removeAll(toRemove);
                            }
                        }, 10, 10, TimeUnit.SECONDS);
                    }
                }
            }

            fd.timeToLive = timeToLive;
            fd.endTime = getTransport().getLocalDevice().getClock().millis() + (timeToLive + 30) * 1000; // Adds a 30-second grace period, as per J.5.2.3

            response.pushU2B(0); // Success
        }

        sendPacket(origin, response.popAll());
    }

    private void readFDT(final OctetString origin) throws BACnetException {
        final ByteQueue response = new ByteQueue();
        response.push(BVLC_TYPE);

        final long now = getTransport().getLocalDevice().getClock().millis();
        try {
            final ByteQueue list = new ByteQueue();

            for (final FDTEntry e : foreignDeviceTable) {
                pushISA(list, e.address);
                list.pushU2B(e.timeToLive);

                int remaining = (int) (e.endTime - now) / 1000;
                if (remaining < 0)
                    // Hasn't yet been cleaned up.
                    remaining = 0;
                if (remaining > 65535)
                    remaining = 65535;

                list.pushU2B(remaining);
            }

            // Successfully written.
            response.push(7); // Read-Foreign-Device-Table-Ack
            response.pushU2B(4 + list.size()); // Length
            response.push(list); // List
        } catch (final Exception e) {
            LOG.error("FDT read failed", e);
            response.push(0); // Result
            response.pushU2B(6); // Length
            response.pushU2B(0x40); // NAK
        }

        sendPacket(IpNetworkUtils.getInetSocketAddress(origin), response.popAll());
    }

    private void deleteFDT(final ByteQueue queue, final OctetString origin) throws BACnetException {
        final byte[] addr = new byte[4];
        queue.pop(addr);
        final int port = queue.popU2B();

        final ByteQueue response = new ByteQueue();
        response.push(BVLC_TYPE);
        response.push(0); // Response type
        response.pushU2B(6); // Length

        synchronized (foreignDeviceTable) {
            FDTEntry toDelete = null;
            for (final FDTEntry fd : foreignDeviceTable) {
                if (Arrays.equals(fd.address.getAddress().getAddress(), addr) && fd.address.getPort() == port) {
                    toDelete = fd;
                    break;
                }
            }

            if (toDelete != null) {
                foreignDeviceTable.remove(toDelete);
                response.pushU2B(0); // Success
            } else
                response.pushU2B(0x50); // NAK
        }

        sendPacket(IpNetworkUtils.getInetSocketAddress(origin), response.popAll());
    }

    private boolean distributeBroadcastToNetwork(final ByteQueue queue, final OctetString originStr)
            throws BACnetException {
        final InetSocketAddress origin = IpNetworkUtils.getInetSocketAddress(originStr);

        // Find the foreign device.
        FDTEntry originFDT = null;
        for (final FDTEntry fd : foreignDeviceTable) {
            if (fd.address.equals(origin)) {
                originFDT = fd;
                break;
            }
        }

        final ByteQueue response = new ByteQueue();
        response.push(BVLC_TYPE);
        response.push(0); // Response type
        response.pushU2B(6); // Length

        // If the FDT was not found, send a NAK and return false.
        if (originFDT == null) {
            response.pushU2B(0x60); // NAK
            sendPacket(origin, response.popAll());
            return false;
        }

        // The FDT was found. Forward the message around.
        final ByteQueue fwd = new ByteQueue();
        fwd.push(BVLC_TYPE);
        fwd.push(4); // Forward
        fwd.pushU2B(10 + queue.size()); // Length
        fwd.push(originStr.getBytes()); // Origin
        fwd.push(queue);
        final byte[] toSend = fwd.popAll();

        // Send locally
        sendPacket(InetAddrCache.get(broadcastAddressStr, port), toSend);

        try {
            // Send to all BDTs except own
            final byte[] myAddress = localBindAddress.getAddress().getAddress();
            for (final BDTEntry e : broadcastDistributionTable) {
                if (Arrays.equals(e.address, myAddress))
                    continue;
                sendToBDT(e, toSend);
            }
        } catch (final UnknownHostException e1) {
            LOG.warn("Error forwarding to BDT", e1);
        }

        // Forward to all foreign devices except the origin.
        for (final FDTEntry fd : foreignDeviceTable) {
            if (fd != originFDT)
                sendPacket(fd.address, toSend);
        }

        response.pushU2B(0); // Success
        sendPacket(origin, response.popAll());
        return true;
    }

    private void sendToBDT(final BDTEntry e, final byte[] toSend) throws UnknownHostException, BACnetException {
        // J.4.5: The B/IP address to which the Forwarded-NPDU message is sent is formed by inverting the broadcast
        // distribution mask in the BDT entry and logically ORing it with the BBMD address of the same entry.
        final byte[] target = new byte[4];
        for (int i = 0; i < 4; i++)
            target[i] = (byte) (e.address[i] | ~e.distributionMask[i]);

        final InetSocketAddress to = InetAddrCache.get(InetAddress.getByAddress(target), e.port);
        sendPacket(to, toSend);
    }

    private static void pushISA(final ByteQueue queue, final InetSocketAddress isa) {
        queue.push(isa.getAddress().getAddress());
        queue.pushU2B(isa.getPort());
    }

    //
    //
    // Foreign device registration (of this local device in a foreign BBMD)
    //
    public void sendForeignDeviceRegistration() throws BACnetException {
        final InetSocketAddress addr = foreignBBMD;
        if (addr == null)
            return;

        final ByteQueue queue = new ByteQueue();
        queue.push(BVLC_TYPE);
        queue.push(0x05); // Register foreign device
        queue.pushU2B(6); // Length
        queue.pushU2B(foreignTTL); // TTL

        synchronized (addr) {
            bbmdResponse = -1;

            sendPacket(addr, queue.popAll());
            try {
                addr.wait(5000); // Wait up to 5 seconds.
            } catch (@SuppressWarnings("unused") final InterruptedException e) {
                // no op
            }

            if (bbmdResponse == -1)
                throw new BACnetException("Foreign registration timeout");
            if (bbmdResponse != 0)
                throw new BACnetException("NAK response: " + bbmdResponse);
        }
    }

    /**
     * Utility method that allows the de-registration of an arbitrary foreign device in a BBMD. If the intention is
     * to unregister this device, the unregisterAsForeignDevice method should be used instead.
     *
     * @param addr
     * @param fdtEntry
     * @throws BACnetException
     */
    public void deleteForeignDeviceTableEntry(final InetSocketAddress addr, final InetSocketAddress fdtEntry)
            throws BACnetException {
        final ByteQueue queue = new ByteQueue();
        queue.push(BVLC_TYPE);
        queue.push(0x08); // Delete foreign device table entry
        queue.pushU2B(0xA); // Length
        pushISA(queue, fdtEntry);
        sendPacket(addr, queue.popAll());
    }
}
