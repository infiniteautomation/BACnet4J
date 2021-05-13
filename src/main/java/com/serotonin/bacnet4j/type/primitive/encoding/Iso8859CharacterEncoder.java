package com.serotonin.bacnet4j.type.primitive.encoding;

import static com.serotonin.bacnet4j.type.primitive.encoding.StandardCharacterEncodings.ISO_8859_1;

public class Iso8859CharacterEncoder extends AbstractCharacterEncoder {

    private static final String JAVA_CHARSET_NAME = "ISO-8859-1";

    public Iso8859CharacterEncoder() {
        super(new CharacterEncoding(ISO_8859_1), JAVA_CHARSET_NAME);
    }
}
