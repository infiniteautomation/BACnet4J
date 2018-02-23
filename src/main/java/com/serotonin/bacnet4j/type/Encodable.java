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
package com.serotonin.bacnet4j.type;

import static com.serotonin.bacnet4j.util.BACnetUtils.toInt;
import static com.serotonin.bacnet4j.util.BACnetUtils.toLong;

import java.lang.reflect.InvocationTargetException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.serotonin.bacnet4j.exception.BACnetErrorException;
import com.serotonin.bacnet4j.exception.BACnetException;
import com.serotonin.bacnet4j.exception.ReflectionException;
import com.serotonin.bacnet4j.obj.ObjectProperties;
import com.serotonin.bacnet4j.obj.ObjectPropertyTypeDefinition;
import com.serotonin.bacnet4j.obj.PropertyTypeDefinition;
import com.serotonin.bacnet4j.type.constructed.BACnetArray;
import com.serotonin.bacnet4j.type.constructed.Choice;
import com.serotonin.bacnet4j.type.constructed.ChoiceOptions;
import com.serotonin.bacnet4j.type.constructed.PriorityArray;
import com.serotonin.bacnet4j.type.constructed.PriorityValue;
import com.serotonin.bacnet4j.type.constructed.SequenceOf;
import com.serotonin.bacnet4j.type.enumerated.ErrorClass;
import com.serotonin.bacnet4j.type.enumerated.ErrorCode;
import com.serotonin.bacnet4j.type.enumerated.ObjectType;
import com.serotonin.bacnet4j.type.enumerated.PropertyIdentifier;
import com.serotonin.bacnet4j.type.primitive.Null;
import com.serotonin.bacnet4j.type.primitive.Primitive;
import com.serotonin.bacnet4j.type.primitive.UnsignedInteger;
import com.serotonin.bacnet4j.util.sero.ByteQueue;

abstract public class Encodable {
    static final Logger LOG = LoggerFactory.getLogger(Encodable.class);

    abstract public void write(ByteQueue queue);

    abstract public void write(ByteQueue queue, int contextId);

    @Override
    public String toString() {
        return "Encodable(" + getClass().getName() + ")";
    }

    protected static void popTagData(final ByteQueue queue, final TagData tagData) {
        peekTagData(queue, tagData);
        queue.pop(tagData.tagLength);
    }

    protected static void peekTagData(final ByteQueue queue, final TagData tagData) {
        int peekIndex = 0;
        final byte b = queue.peek(peekIndex++);
        tagData.tagNumber = (b & 0xff) >> 4;
        tagData.contextSpecific = (b & 8) == 8;
        tagData.length = b & 7;

        if (tagData.tagNumber == 0xf)
            // Extended tag.
            tagData.tagNumber = toInt(queue.peek(peekIndex++));

        if (tagData.length == 5) {
            tagData.length = toInt(queue.peek(peekIndex++));
            if (tagData.length == 254)
                tagData.length = toInt(queue.peek(peekIndex++)) << 8 | toInt(queue.peek(peekIndex++));
            else if (tagData.length == 255)
                tagData.length = toLong(queue.peek(peekIndex++)) << 24 | toLong(queue.peek(peekIndex++)) << 16
                        | toLong(queue.peek(peekIndex++)) << 8 | toLong(queue.peek(peekIndex++));
        }

        tagData.tagLength = peekIndex;
    }

    protected static boolean isContextTag(final ByteQueue queue) {
        if (queue.size() == 0)
            return false;
        return (queue.peek(0) & 8) == 8;
    }

    protected static int peekTagNumber(final ByteQueue queue) {
        if (queue.size() == 0)
            return -1;

        // Take a peek at the tag number.
        int tagNumber = toInt(queue.peek(0)) >> 4;
        if (tagNumber == 15)
            tagNumber = toInt(queue.peek(1));
        return tagNumber;
    }

    protected static int getTagLength(final ByteQueue queue) {
        if (queue.size() == 0)
            return -1;

        // Take a peek at the tag number.
        final int tagNumber = toInt(queue.peek(0)) >> 4;
        if (tagNumber == 15)
            return 2;
        return 1;
    }

    //
    // Write context tags for base types.
    protected void writeContextTag(final ByteQueue queue, final int contextId, final boolean start) {
        if (contextId < 0 || contextId > 254)
            throw new RuntimeException("Invalid context id: " + contextId);

        if (contextId <= 14)
            queue.push(contextId << 4 | (start ? 0xe : 0xf));
        else {
            queue.push(start ? 0xfe : 0xff);
            queue.push(contextId);
        }
    }

    //
    // Read start tags.
    protected static int readStart(final ByteQueue queue) {
        if (queue.size() == 0)
            return -1;

        final int b = toInt(queue.peek(0));
        if ((b & 0xf) != 0xe)
            return -1;
        if ((b & 0xf0) == 0xf0)
            return toInt(queue.peek(1));
        return b >> 4;
    }

    protected static int popStart(final ByteQueue queue) {
        final int contextId = readStart(queue);
        if (contextId != -1) {
            queue.pop();
            if (contextId > 14)
                queue.pop();
        }
        return contextId;
    }

    protected static void popStart(final ByteQueue queue, final int contextId) throws BACnetErrorException {
        if (popStart(queue) != contextId)
            throw new BACnetErrorException(ErrorClass.property, ErrorCode.missingRequiredParameter);
    }

    //
    // Read end tags.
    protected static int readEnd(final ByteQueue queue) {
        if (queue.size() == 0)
            return -1;
        final int b = toInt(queue.peek(0));
        if ((b & 0xf) != 0xf)
            return -1;
        if ((b & 0xf0) == 0xf0)
            return toInt(queue.peek(1));
        return b >> 4;
    }

    protected static void popEnd(final ByteQueue queue, final int contextId) throws BACnetErrorException {
        if (readEnd(queue) != contextId)
            throw new BACnetErrorException(ErrorClass.property, ErrorCode.missingRequiredParameter);
        queue.pop();
        if (contextId > 14)
            queue.pop();
    }

    /**
     * Check if the tag number at the beginning of the queue matches the given context id.
     */
    private static boolean matchContextId(final ByteQueue queue, final int contextId) {
        return peekTagNumber(queue) == contextId;
    }

    /**
     * Check if the tag at the beginning of the queue is a start tag that matches given context id.
     */
    protected static boolean matchStartTag(final ByteQueue queue, final int contextId) {
        return matchContextId(queue, contextId) && (queue.peek(0) & 0xf) == 0xe;
    }

    /**
     * Check if the tag at the beginning of the queue is an end tag that matches given context id.
     */
    protected static boolean matchEndTag(final ByteQueue queue, final int contextId) {
        return matchContextId(queue, contextId) && (queue.peek(0) & 0xf) == 0xf;
    }

    /**
     * Check if the tag at the beginning of the queue is NOT an end tag, but that it matches given context id.
     */
    protected static boolean matchNonEndTag(final ByteQueue queue, final int contextId) {
        return matchContextId(queue, contextId) && (queue.peek(0) & 0xf) != 0xf;
    }

    //
    // Reading
    //

    @SuppressWarnings("unchecked")
    public static <T extends Encodable> T read(final ByteQueue queue, final Class<T> clazz) throws BACnetException {
        if (clazz == Primitive.class)
            return (T) Primitive.createPrimitive(queue);

        try {
            return clazz.getConstructor(new Class[] { ByteQueue.class }).newInstance(new Object[] { queue });
        } catch (final NoSuchMethodException e) {
            throw new BACnetException(e);
        } catch (final InvocationTargetException e) {
            // Check if there is a wrapped BACnet exception
            if (e.getCause() instanceof BACnetException)
                throw (BACnetException) e.getCause();
            throw new ReflectionException(e);
        } catch (final Exception e) {
            throw new BACnetException(e);
        }
    }

    //
    // Read and write with context id.
    public static <T extends Encodable> T read(final ByteQueue queue, final Class<T> clazz, final int contextId)
            throws BACnetException {
        if (!matchNonEndTag(queue, contextId))
            throw new BACnetErrorException(ErrorClass.property, ErrorCode.missingRequiredParameter);

        if (Primitive.class.isAssignableFrom(clazz)) {
            return read(queue, clazz);
        }
        return readWrapped(queue, clazz, contextId);
    }

    protected static <T extends Encodable> T readOptional(final ByteQueue queue, final Class<T> clazz,
            final int contextId) throws BACnetException {
        if (!matchNonEndTag(queue, contextId))
            return null;
        return read(queue, clazz, contextId);
    }

    //
    // Read choices
    protected static Choice readChoice(final ByteQueue queue, final ChoiceOptions choiceOptions)
            throws BACnetException {
        return new Choice(queue, choiceOptions);
    }

    protected static Choice readChoice(final ByteQueue queue, final ChoiceOptions choiceOptions, final int contextId)
            throws BACnetException {
        popStart(queue, contextId);
        try {
            return readChoice(queue, choiceOptions);
        } finally {
            popEnd(queue, contextId);
        }
    }

    protected static Choice readOptionalChoice(final ByteQueue queue, final ChoiceOptions choiceOptions)
            throws BACnetException {
        if (peekTagNumber(queue) == -1)
            return null;
        return readChoice(queue, choiceOptions);
    }

    protected static Choice readOptionalChoice(final ByteQueue queue, final ChoiceOptions choiceOptions,
            final int contextId) throws BACnetException {
        if (!matchNonEndTag(queue, contextId))
            return null;
        return readChoice(queue, choiceOptions, contextId);
    }

    //
    // Read lists
    public static <T extends Encodable> SequenceOf<T> readSequenceOf(final ByteQueue queue, final Class<T> clazz)
            throws BACnetException {
        return new SequenceOf<>(queue, clazz);
    }

    protected static <T extends Encodable> SequenceOf<T> readSequenceOf(final ByteQueue queue, final int count,
            final Class<T> clazz) throws BACnetException {
        return new SequenceOf<>(queue, count, clazz);
    }

    protected static <T extends Encodable> SequenceOf<T> readSequenceOf(final ByteQueue queue, final Class<T> clazz,
            final int contextId) throws BACnetException {
        popStart(queue, contextId);
        final SequenceOf<T> result = new SequenceOf<>(queue, clazz, contextId);
        popEnd(queue, contextId);
        return result;
    }

    protected static <T extends Encodable> T readSequenceType(final ByteQueue queue, final Class<T> clazz,
            final int contextId) throws BACnetException {
        popStart(queue, contextId);
        T result;
        try {
            result = clazz.getConstructor(new Class[] { ByteQueue.class, Integer.TYPE })
                    .newInstance(new Object[] { queue, contextId });
        } catch (final Exception e) {
            throw new BACnetException(e);
        }
        popEnd(queue, contextId);
        return result;
    }

    protected static SequenceOf<Choice> readSequenceOfChoice(final ByteQueue queue, final ChoiceOptions choiceOptions,
            final int contextId) throws BACnetException {
        popStart(queue, contextId);
        final SequenceOf<Choice> result = new SequenceOf<>();
        while (readEnd(queue) != contextId)
            result.add(new Choice(queue, choiceOptions));
        popEnd(queue, contextId);
        return result;
    }

    protected static <T extends Encodable> SequenceOf<T> readOptionalSequenceOf(final ByteQueue queue,
            final Class<T> clazz, final int contextId) throws BACnetException {
        if (readStart(queue) != contextId)
            return null;
        return readSequenceOf(queue, clazz, contextId);
    }

    protected static <T extends Encodable> BACnetArray<T> readArray(final ByteQueue queue, final Class<T> clazz,
            final int contextId) throws BACnetException {
        popStart(queue, contextId);
        final BACnetArray<T> result = new BACnetArray<>(queue, clazz, contextId);
        popEnd(queue, contextId);
        return result;
    }

    protected static Encodable readANY(final ByteQueue queue, final ObjectType objectType,
            final PropertyIdentifier propertyIdentifier, final UnsignedInteger propertyArrayIndex, final int contextId)
            throws BACnetException {
        // A property array index of 0 indicates a request for the length of an array.
        if (propertyArrayIndex != null && propertyArrayIndex.intValue() == 0)
            return readWrapped(queue, UnsignedInteger.class, contextId);

        if (!matchNonEndTag(queue, contextId))
            throw new BACnetErrorException(ErrorClass.property, ErrorCode.invalidDataType);

        // Get the definition for the property, if there is one.
        final PropertyTypeDefinition def = getPropertyTypeDefinition(objectType, propertyIdentifier);

        if (def == null) {
            // We don't know what this is. Check if it's a primitive.
            final Primitive p = Primitive.createPrimitive(queue, contextId);
            if (p != null)
                return p;

            // If it's not a primitive, return an ambiguous value.
            return new AmbiguousValue(queue, contextId);
        }

        if (ObjectProperties.isCommandable(objectType, propertyIdentifier)) {
            // If the object is commandable, it could be set to Null, so we need to treat it as ambiguous.
            final AmbiguousValue amb = new AmbiguousValue(queue, contextId);

            if (amb.isNull())
                return Null.instance;

            // Try converting to the definition value.
            return amb.convertTo(def.getClazz());
        }

        if (propertyArrayIndex == null && def.isCollection()) {
            return readSequenceOf(queue, def.getClazz(), contextId);
        }

        if (propertyArrayIndex != null && def.getClazz() == PriorityArray.class) {
            // An element of a priority array.
            return readWrapped(queue, PriorityValue.class, contextId);
        }

        return readWrapped(queue, def.getClazz(), contextId);
    }

    protected static Encodable readOptionalANY(final ByteQueue queue, final ObjectType objectType,
            final PropertyIdentifier propertyIdentifier, final int contextId) throws BACnetException {
        if (readStart(queue) != contextId)
            return null;
        return readANY(queue, objectType, propertyIdentifier, null, contextId);
    }

    protected static SequenceOf<? extends Encodable> readSequenceOfANY(final ByteQueue queue,
            final ObjectType objectType, final PropertyIdentifier propertyIdentifier, final int contextId)
            throws BACnetException {
        final PropertyTypeDefinition def = getPropertyTypeDefinition(objectType, propertyIdentifier);
        if (def == null)
            return readSequenceOf(queue, AmbiguousValue.class, contextId);
        return readSequenceOf(queue, def.getClazz(), contextId);
    }

    private static PropertyTypeDefinition getPropertyTypeDefinition(final ObjectType objectType,
            final PropertyIdentifier propertyIdentifier) {
        if (objectType != null && propertyIdentifier != null) {
            final ObjectPropertyTypeDefinition def = ObjectProperties.getObjectPropertyTypeDefinition(objectType,
                    propertyIdentifier);
            if (def != null) {
                return def.getPropertyTypeDefinition();
            }
        }

        // Check if the pid has only one type
        if (propertyIdentifier != null) {
            return ObjectProperties.getPropertyTypeDefinition(propertyIdentifier);
        }

        return null;
    }

    // Read vendor-specific
    protected static EncodedValue readEncodedValue(final ByteQueue queue, final int contextId) throws BACnetException {
        if (readStart(queue) != contextId)
            return null;
        return new EncodedValue(queue, contextId);
    }

    private static <T extends Encodable> T readWrapped(final ByteQueue queue, final Class<T> clazz, final int contextId)
            throws BACnetException {
        popStart(queue, contextId);
        final T result = read(queue, clazz);
        popEnd(queue, contextId);
        return result;
    }

    //
    // Writing
    //

    public static void write(final ByteQueue queue, final Encodable type) {
        type.write(queue);
    }

    public static void write(final ByteQueue queue, final Encodable type, final int contextId) {
        type.write(queue, contextId);
    }

    //
    // Optional read and write.
    protected static void writeOptional(final ByteQueue queue, final Encodable type) {
        if (type == null)
            return;
        write(queue, type);
    }

    protected static void writeOptional(final ByteQueue queue, final Encodable type, final int contextId) {
        if (type == null)
            return;
        write(queue, type, contextId);
    }

    // Read and write encodable
    protected static void writeANY(final ByteQueue queue, final Encodable type, final int contextId) {
        if (Primitive.class.isAssignableFrom(type.getClass()))
            ((Primitive) type).writeWithContextTag(queue, contextId);
        else
            type.write(queue, contextId);
    }
}
