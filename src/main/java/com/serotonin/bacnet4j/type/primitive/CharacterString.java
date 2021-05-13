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
package com.serotonin.bacnet4j.type.primitive;

import static com.serotonin.bacnet4j.type.primitive.encoding.StandardCharacterEncodings.ANSI_X3_4;
import static com.serotonin.bacnet4j.type.primitive.encoding.StandardCharacterEncodings.CODE_PAGE_LATIN_1;
import static com.serotonin.bacnet4j.type.primitive.encoding.StandardCharacterEncodings.IBM_MS_DBCS;
import static com.serotonin.bacnet4j.type.primitive.encoding.StandardCharacterEncodings.NO_CODE_PAGE;

import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import com.serotonin.bacnet4j.exception.BACnetErrorException;
import com.serotonin.bacnet4j.exception.BACnetRuntimeException;
import com.serotonin.bacnet4j.type.enumerated.ErrorClass;
import com.serotonin.bacnet4j.type.enumerated.ErrorCode;
import com.serotonin.bacnet4j.type.primitive.encoding.CharacterEncoder;
import com.serotonin.bacnet4j.type.primitive.encoding.CharacterEncoding;
import com.serotonin.bacnet4j.util.sero.ByteQueue;

public class CharacterString extends Primitive {

    public static final byte TYPE_ID = 7;

    // load encoders before creating EMPTY
    private static final List<CharacterEncoder> characterEncoders = loadEncoders();

    public static final CharacterString EMPTY = new CharacterString("");

    private final CharacterEncoding encoding;
    private final CharacterEncoder encoder;
    private final String value;

    public CharacterString(final String value) {
        this(new CharacterEncoding(ANSI_X3_4), value);
    }

    /**
     * According to Oracle java documentation about Charset, the behavior of optional charsets may vary between java platform implementations.
     * This concerns ISO_10646_UCS_4 (UTF-32), IBM_MS_DBCS and JIS_C_6226.
     * @param encoding
     * @param value
     */
    public CharacterString(final byte encoding, final String value) {
        this(new CharacterEncoding(encoding, defaultCodingPage(encoding)), value);
    }

    public CharacterString(final CharacterEncoding encoding, final String value) {
        this.encoding = encoding;
        try {
            encoder = findEncoder(encoding);
        } catch (final BACnetErrorException e) {
            // This is an API constructor, so it doesn't need to throw checked exceptions. Convert to runtime.
            throw new BACnetRuntimeException(e);
        }
        this.value = value == null ? "" : value;
    }

    //
    // Reading and writing
    //
    public CharacterString(final ByteQueue queue) throws BACnetErrorException {
        final int length = (int) readTag(queue, TYPE_ID);

        encoding = createCharacterEncoding(queue);
        encoder = findEncoder(encoding);

        int headerLength = calcHeaderLength();

        final byte[] bytes = new byte[length - headerLength];
        queue.pop(bytes);

        value = encoder.decode(bytes);
    }

    public CharacterEncoding getEncoding() {
        return encoding;
    }

    public String getValue() {
        return value;
    }

    @Override
    public void writeImpl(final ByteQueue queue) {
        queue.push(encoding.getEncoding());
        queue.push(encoder.encode(value));
    }

    @Override
    protected long getLength() {
        return encoder.encode(value).length + 1;
    }

    @Override
    public byte getTypeId() {
        return TYPE_ID;
    }

    private int calcHeaderLength() {
        int headerLength = 1;
        if (encoding.getCodePage() != NO_CODE_PAGE) {
            headerLength += 2;
        }
        return headerLength;
    }

    private CharacterEncoding createCharacterEncoding(ByteQueue queue) {
        byte encodingValue = queue.pop();
        if (encodingValue != IBM_MS_DBCS) {
            return new CharacterEncoding(encodingValue, NO_CODE_PAGE);
        }
        //Decode the codePage
        int codePage = queue.popU2B();
        return new CharacterEncoding(encodingValue, codePage);
    }

    private static CharacterEncoder findEncoder(CharacterEncoding encoding) throws BACnetErrorException {
        return characterEncoders.stream()
                .filter(encoder -> encoder.isEncodingSupported(encoding))
                .findFirst()
                .orElseThrow(() -> new BACnetErrorException(
                        ErrorClass.property,
                        ErrorCode.characterSetNotSupported,
                        encoding.toString())
                );
    }

    private static int defaultCodingPage(byte encoding) {
        if (encoding == IBM_MS_DBCS) {
            return CODE_PAGE_LATIN_1;
        }
        return NO_CODE_PAGE;
    }

    private static List<CharacterEncoder> loadEncoders() {
        ServiceLoader<CharacterEncoder> loader = ServiceLoader.load(CharacterEncoder.class);
        return StreamSupport.stream(loader.spliterator(), false)
                .collect(Collectors.toList());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        CharacterString that = (CharacterString) o;
        return Objects.equals(encoding, that.encoding) &&
                Objects.equals(encoder, that.encoder) &&
                Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(encoding, encoder, value);
    }

    @Override
    public String toString() {
        return value;
    }
}
