package com.serotonin.bacnet4j.type.primitive.encoding;

import java.io.UnsupportedEncodingException;

import com.serotonin.bacnet4j.exception.BACnetRuntimeException;

public abstract class AbstractCharacterEncoder implements CharacterEncoder {

    private final CharacterEncoding characterEncoding;
    private final String javaCharsetName;

    public AbstractCharacterEncoder(CharacterEncoding characterEncoding, String javaCharsetName) {
        this.characterEncoding = characterEncoding;
        this.javaCharsetName = javaCharsetName;
    }

    @Override
    public boolean isEncodingSupported(CharacterEncoding encoding) {
        return characterEncoding.equals(encoding);
    }

    @Override
    public byte[] encode(String value) {
        try {
            return value.getBytes(javaCharsetName);
        } catch (final UnsupportedEncodingException e) {
            // Should never happen
            throw new BACnetRuntimeException(e);
        }
    }

    @Override
    public String decode(byte[] bytes) {
        try {
            return new String(bytes, javaCharsetName);
        } catch (final UnsupportedEncodingException e) {
            // Should never happen
            throw new BACnetRuntimeException(e);
        }
    }
}
