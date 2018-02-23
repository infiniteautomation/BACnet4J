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
package com.serotonin.bacnet4j.type.constructed;

import com.serotonin.bacnet4j.exception.BACnetException;
import com.serotonin.bacnet4j.type.primitive.OctetString;
import com.serotonin.bacnet4j.type.primitive.Unsigned16;
import com.serotonin.bacnet4j.type.primitive.UnsignedInteger;
import com.serotonin.bacnet4j.util.sero.ByteQueue;

public class Address extends BaseType {
    public static final int LOCAL_NETWORK = 0;
    public static final int ALL_NETWORKS = 0xFFFF;
    public static final Address GLOBAL = new Address(new Unsigned16(ALL_NETWORKS), null);

    // 父类是UnsignedInteger, Unsigned16的构造函数只做了范围检测, 当networkNumber大于0xFFFF就会抛出IllegalArgumentException
    // networkNumber在创建IpNetwork的时候可以指定, 不指定默认为0
    private final Unsigned16 networkNumber;
    private final OctetString macAddress; // 内部存放着一个byte[], 通常前4个byte是IP地址, 后2个byte是port

    public Address(final byte[] macAddress) {
        this(new Unsigned16(LOCAL_NETWORK), new OctetString(macAddress));
    }

    public Address(final int networkNumber, final byte[] macAddress) {
        this(new Unsigned16(networkNumber), new OctetString(macAddress));
    }

    public Address(final OctetString macAddress) {
        this(new Unsigned16(LOCAL_NETWORK), macAddress);
    }

    public Address(final int networkNumber, final OctetString macAddress) {
        this(new Unsigned16(networkNumber), macAddress);
    }

    public Address(final Unsigned16 networkNumber, final OctetString macAddress) {
        this.networkNumber = networkNumber;
        this.macAddress = macAddress;
    }

    @Override
    public void write(final ByteQueue queue) {
        write(queue, networkNumber);
        write(queue, macAddress);
    }

    public Address(final ByteQueue queue) throws BACnetException {
        networkNumber = read(queue, Unsigned16.class);
        macAddress = read(queue, OctetString.class);
    }

    public OctetString getMacAddress() {
        return macAddress;
    }

    public UnsignedInteger getNetworkNumber() {
        return networkNumber;
    }

    public boolean isGlobal() {
        return networkNumber.intValue() == 0xFFFF;
    }

    //
    //
    // General convenience
    //
    public String getDescription() {
        final StringBuilder sb = new StringBuilder();
        sb.append(macAddress.getDescription());
        if (networkNumber.intValue() != 0)
            sb.append('(').append(networkNumber).append(')');
        return sb.toString();
    }

    @Override
    public String toString() {
        return "Address [networkNumber=" + networkNumber + ", macAddress=" + macAddress + "]";
    }

    @Override
    public int hashCode() {
        final int PRIME = 31;
        int result = 1;
        result = PRIME * result + (macAddress == null ? 0 : macAddress.hashCode());
        result = PRIME * result + (networkNumber == null ? 0 : networkNumber.hashCode());
        return result;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final Address other = (Address) obj;
        if (macAddress == null) {
            if (other.macAddress != null)
                return false;
        } else if (!macAddress.equals(other.macAddress))
            return false;
        if (networkNumber == null) {
            if (other.networkNumber != null)
                return false;
        } else if (!networkNumber.equals(other.networkNumber))
            return false;
        return true;
    }
}
