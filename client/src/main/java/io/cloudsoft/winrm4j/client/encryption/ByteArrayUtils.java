package io.cloudsoft.winrm4j.client.encryption;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class ByteArrayUtils {

    public static String formatHexDump(byte[] array) {
        if (array==null) return "null";
        return formatHexDump(array, 0, array.length);
    }

    public static String formatHexDump(byte[] array, int offset, int length) {
        // from https://gist.github.com/jen20/906db194bd97c14d91df

        final int width = 32;

        StringBuilder builder = new StringBuilder();

        for (int rowOffset = offset; rowOffset < offset + length; rowOffset += width) {
            builder.append(String.format("%06d: ", rowOffset));

            for (int index = 0; index < width; index++) {
                if (rowOffset + index < array.length) {
                    builder.append(String.format("%02x", array[rowOffset + index]));
                } else {
                    builder.append("  ");
                }
                if (index % 4 == 3) builder.append(" ");
            }

            if (rowOffset < array.length) {
                int asciiWidth = Math.min(width, array.length - rowOffset);
                builder.append(" | ");
                for (int index = 0; index < width; index++) {
                    if (rowOffset + index < array.length) {
                        byte c = array[rowOffset + index];
                        builder.append( (c>=20 && c<127) ? (char)c : '.' );

                        if (index % 8 == 7) builder.append(" ");
                    }
                }
            }

            builder.append(String.format("%n"));
        }

        return builder.toString();
    }

    public static byte[] bytes(int ...ints) {
        byte[] result = new byte[ints.length];
        for (int x=0; x<result.length; x++) { result[x] = (byte) ints[x]; }
        return result;
    }


    public static byte[] getLittleEndianUnsignedInt(long x) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(4);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        byteBuffer.putInt( (int) (x & 0xFFFFFFFF) );
        return byteBuffer.array();
    }


    public static byte[] concat(byte[] ...sequences) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            for (byte[] s: sequences) {
                out.write(s);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static byte[] repeated(int count, int[] bytesI) {
        return repeated(count, bytes(bytesI));
    }
    public static byte[] repeated(int count, byte[] bytes) {
        byte[] result = new byte[bytes.length * count];
        for (int i=0; i<count; i++) {
            System.arraycopy(bytes, 0, result, i*bytes.length, bytes.length);
        }
        return result;
    }

    public static long readLittleEndianUnsignedInt(byte[] input, int offset) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(input);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        return Integer.toUnsignedLong(byteBuffer.getInt(offset));
    }
}
