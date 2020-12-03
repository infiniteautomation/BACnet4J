package com.serotonin.bacnet4j.type.primitive.encoding;

import static com.serotonin.bacnet4j.type.primitive.encoding.StandardCharacterEncodings.IBM_MS_DBCS;

public class DbcsCp850CharacterEncoder extends AbstractDbcsCharacterEncoder {

    public static final int DOS_LATIN_1_CODEPAGE = 850;
    private static final String JAVA_ENCODING = "IBM850";

    public DbcsCp850CharacterEncoder() {
        super(new CharacterEncoding(IBM_MS_DBCS, DOS_LATIN_1_CODEPAGE), JAVA_ENCODING);
    }

}
