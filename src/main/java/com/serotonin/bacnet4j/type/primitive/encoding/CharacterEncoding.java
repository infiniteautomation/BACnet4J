package com.serotonin.bacnet4j.type.primitive.encoding;

import static com.serotonin.bacnet4j.type.primitive.encoding.StandardCharacterEncodings.NO_CODE_PAGE;

import java.util.Objects;

public class CharacterEncoding {

    private final byte encoding;
    private final int codePage;

    public CharacterEncoding(byte encoding) {
        this(encoding, NO_CODE_PAGE);
    }

    public CharacterEncoding(byte encoding, int codePage) {
        this.encoding = encoding;
        this.codePage = codePage;
    }

    public byte getEncoding() {
        return encoding;
    }

    public int getCodePage() {
        return codePage;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        CharacterEncoding that = (CharacterEncoding) o;
        return encoding == that.encoding &&
                codePage == that.codePage;
    }

    @Override
    public int hashCode() {
        return Objects.hash(encoding, codePage);
    }

    @Override
    public String toString() {
        return "CharacterEncoding{" +
                "encoding=" + encoding +
                ", codePage=" + codePage +
                '}';
    }
}
