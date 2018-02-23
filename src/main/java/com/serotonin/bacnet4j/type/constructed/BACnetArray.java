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

import java.util.ArrayList;
import java.util.List;

import com.serotonin.bacnet4j.exception.BACnetException;
import com.serotonin.bacnet4j.exception.BACnetRuntimeException;
import com.serotonin.bacnet4j.type.Encodable;
import com.serotonin.bacnet4j.util.sero.ByteQueue;

public class BACnetArray<E extends Encodable> extends SequenceOf<E> {
    public BACnetArray(final int size, final E defaultValue) {
        super(newList(size, defaultValue));
    }

    private static final <T extends Encodable> List<T> newList(final int size, final T defaultValue) {
        final List<T> list = new ArrayList<>();
        for (int i = 0; i < size; i++)
            list.add(defaultValue);
        return list;
    }

    @SafeVarargs
    public BACnetArray(final E... values) {
        super(values);
    }

    public BACnetArray(final List<E> values) {
        super(values);
    }

    public BACnetArray(final BACnetArray<E> that) {
        super(that.values);
    }

    public BACnetArray(final ByteQueue queue, final int count, final Class<E> clazz) throws BACnetException {
        super(queue, count, clazz);
    }

    public BACnetArray(final ByteQueue queue, final Class<E> clazz, final int contextId) throws BACnetException {
        super(queue, clazz, contextId);
    }

    public BACnetArray<E> putBase1(final int indexBase1, final E value) {
        setBase1(indexBase1, value);
        return this;
    }

    @Override
    public void setBase1(final int indexBase1, final E value) {
        values.set(indexBase1 - 1, value);
    }

    @Override
    public void add(final E value) {
        throw new BACnetRuntimeException("Illegal operation");
    }

    @Override
    public Encodable remove(final int indexBase1) {
        throw new BACnetRuntimeException("Illegal operation");
    }

    @Override
    public void remove(final E value) {
        throw new BACnetRuntimeException("Illegal operation");
    }

    @Override
    public void removeAll(final E value) {
        throw new BACnetRuntimeException("Illegal operation");
    }
}
