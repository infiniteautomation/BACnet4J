package com.serotonin.bacnet4j.type.primitive.encoding;

import static com.serotonin.bacnet4j.type.primitive.encoding.StandardCharacterEncodings.ANSI_X3_4;

public class AnsiCharacterEncoder extends AbstractCharacterEncoder {

    private static final String JAVA_CHARSET_NAME = "UTF-8";

    public AnsiCharacterEncoder() {
        super(new CharacterEncoding(ANSI_X3_4), JAVA_CHARSET_NAME);
    }
}
