package com.serotonin.bacnet4j.type.primitive.encoding;

import static com.serotonin.bacnet4j.type.primitive.encoding.StandardCharacterEncodings.ISO_10646_UCS_2;

public class Ucs2CharacterEncoder extends AbstractCharacterEncoder {

    private static final String JAVA_CHARSET_NAME = "UTF-16";

    public Ucs2CharacterEncoder() {
        super(new CharacterEncoding(ISO_10646_UCS_2), JAVA_CHARSET_NAME);
    }
}
