package com.serotonin.bacnet4j.type.primitive.encoding;

public interface CharacterEncoder {

    boolean isEncodingSupported(CharacterEncoding encoding);

    byte[] encode(String value);

    String decode(byte[] bytes);
}
