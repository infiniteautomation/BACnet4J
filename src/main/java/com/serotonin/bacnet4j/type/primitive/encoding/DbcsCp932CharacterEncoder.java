package com.serotonin.bacnet4j.type.primitive.encoding;

import static com.serotonin.bacnet4j.type.primitive.encoding.StandardCharacterEncodings.IBM_MS_DBCS;

public class DbcsCp932CharacterEncoder extends AbstractDbcsCharacterEncoder {

    public static final int JAPANESE_CODEPAGE = 932;
    private static final String JAVA_CHARSET_NAME = "MS932";

    public DbcsCp932CharacterEncoder() {
        super(new CharacterEncoding(IBM_MS_DBCS, JAPANESE_CODEPAGE), JAVA_CHARSET_NAME);
    }

}
