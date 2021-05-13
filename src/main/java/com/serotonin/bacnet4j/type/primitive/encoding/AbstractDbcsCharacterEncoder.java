package com.serotonin.bacnet4j.type.primitive.encoding;

import java.io.UnsupportedEncodingException;

import com.serotonin.bacnet4j.exception.BACnetRuntimeException;

public abstract class AbstractDbcsCharacterEncoder extends AbstractCharacterEncoder {
 
    private final CharacterEncoding characterEncoding;
    private final String javaCharsetName;

    public AbstractDbcsCharacterEncoder(CharacterEncoding characterEncoding, String javaCharsetName) {
        super(characterEncoding, javaCharsetName);
        this.characterEncoding = characterEncoding;
        this.javaCharsetName = javaCharsetName;
    }

    @Override
    public byte[] encode(String value) {
        try {
            byte[] bytes = value.getBytes(javaCharsetName);
            //Add the codePage
            byte[] result = new byte[2 + bytes.length];
            int codePage = characterEncoding.getCodePage();
            result[0] = (byte) (codePage >> 8);
            result[1] = (byte) codePage;
            System.arraycopy(bytes, 0, result, 2, bytes.length);
            return result;
        } catch (final UnsupportedEncodingException e) {
            // Should never happen
            throw new BACnetRuntimeException(e);
        }
    }
}
