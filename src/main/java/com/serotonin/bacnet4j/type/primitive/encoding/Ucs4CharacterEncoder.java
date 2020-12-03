package com.serotonin.bacnet4j.type.primitive.encoding;

import static com.serotonin.bacnet4j.type.primitive.encoding.StandardCharacterEncodings.ISO_10646_UCS_4;

public class Ucs4CharacterEncoder extends AbstractCharacterEncoder {

    private static final String JAVA_CHARSET_NAME = "UTF-32";

    public Ucs4CharacterEncoder() {
        super(new CharacterEncoding(ISO_10646_UCS_4), JAVA_CHARSET_NAME);
    }
}
