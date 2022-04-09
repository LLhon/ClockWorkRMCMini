package com.palmbaby.lib_common.utils;

import java.nio.charset.Charset;
import java.util.Arrays;

/**
 * Created by LLhon on 2022/4/6 11:46.
 */
public class ByteUtil {

    private ByteUtil() {
    }

    private static char forDigit(int digit, int radix) {
        if (digit < radix && digit >= 0) {
            if (radix >= 2 && radix <= 36) {
                return digit < 10 ? (char)(48 + digit) : (char)(55 + digit);
            } else {
                return '\u0000';
            }
        } else {
            return '\u0000';
        }
    }

    public static String byte2Hex(byte num) {
        return value2Hex(num);
    }

    public static String short2Hex(short num) {
        return value2Hex(num);
    }

    public static String int2Hex(int num) {
        return value2Hex(num);
    }

    public static String long2Hex(long num) {
        return value2Hex(num);
    }

    public static String value2Hex(Number number) {
        byte[] bytes = null;
        if (number instanceof Byte) {
            bytes = long2bytes((long)number.byteValue(), 1);
        } else if (number instanceof Short) {
            bytes = long2bytes((long)number.shortValue(), 2);
        } else if (number instanceof Integer) {
            bytes = long2bytes((long)number.intValue(), 4);
        } else if (number instanceof Long) {
            bytes = long2bytes(number.longValue(), 8);
        }

        return bytes == null ? "00" : bytes2HexStr(bytes);
    }

    /**
     * 二进制转换十六进制
     * @param src
     * @return
     */
    public static String bytes2HexStr(byte[] src) {
        StringBuilder builder = new StringBuilder();
        if (src != null && src.length > 0) {
            char[] buffer = new char[2];

            for(int i = 0; i < src.length; ++i) {
                buffer[0] = forDigit(src[i] >>> 4 & 15, 16);
                buffer[1] = forDigit(src[i] & 15, 16);
                builder.append(buffer);
            }

            return builder.toString();
        } else {
            return "";
        }
    }

    /**
     * 二进制转换十六进制
     * @param src
     * @return
     */
    public static String bytes2HexStr2(byte[] src) {
        StringBuilder builder = new StringBuilder();

        for (byte b : src) {
            String hex = Integer.toHexString(b & 0xFF);
            if (hex.length() == 1) {
                hex = '0' + hex;
            }
            builder.append(hex);
        }

        return builder.toString().toUpperCase();
    }

    /**
     * 十六进制字符串转十进制
     * @param hex
     * @return
     */
    public static int hexStr2Algorism(String hex) {
        hex = hex.toUpperCase();
        int max = hex.length();
        int result = 0;
        for (int i = max; i > 0; i--) {
            char c = hex.charAt(i - 1);
            int algorism = 0;
            if (c >= '0' && c <= '9') {
                algorism = c - '0';
            } else {
                algorism = c - 55;
            }
            result += Math.pow(16, max - i) * algorism;
        }
        return result;
    }

    public static String bytes2HexStr(byte[] src, int dec, int length) {
        byte[] temp = new byte[length];
        System.arraycopy(src, dec, temp, 0, length);
        return bytes2HexStr(temp);
    }

    public static long hexStr2decimal(String hex) {
        return Long.parseLong(hex, 16);
    }

    public static String decimal2fitHex(long num) {
        String hex = Long.toHexString(num).toUpperCase();
        return hex.length() % 2 != 0 ? "0" + hex : hex.toUpperCase();
    }

    public static String decimal2fitHex(long num, int strLength) {
        String hexStr = decimal2fitHex(num);
        StringBuilder stringBuilder = new StringBuilder(hexStr);

        while(stringBuilder.length() < strLength) {
            stringBuilder.insert(0, '0');
        }

        return stringBuilder.toString();
    }

    public static String fitDecimalStr(int dicimal, int strLength) {
        StringBuilder builder = new StringBuilder(String.valueOf(dicimal));

        while(builder.length() < strLength) {
            builder.insert(0, "0");
        }

        return builder.toString();
    }

    public static String str2HexString(String str) {
        char[] chars = "0123456789ABCDEF".toCharArray();
        StringBuilder sb = new StringBuilder();
        byte[] bs = null;

        try {
            bs = str.getBytes(Charset.forName("utf8"));
        } catch (Exception var6) {
            var6.printStackTrace();
        }

        for(int i = 0; i < bs.length; ++i) {
            int bit = (bs[i] & 240) >> 4;
            sb.append(chars[bit]);
            bit = bs[i] & 15;
            sb.append(chars[bit]);
        }

        return sb.toString();
    }

    public static byte[] hexStr2bytes(String hex) {
        int len = hex.length() / 2;
        byte[] result = new byte[len];
        char[] achar = hex.toUpperCase().toCharArray();

        for(int i = 0; i < len; ++i) {
            int pos = i * 2;
            result[i] = (byte)(hexChar2byte(achar[pos]) << 4 | hexChar2byte(achar[pos + 1]));
        }

        return result;
    }

    public static byte[] long2bytes(long ori, int arrayAmount) {
        byte[] bytes = new byte[arrayAmount];

        for(int i = 0; i < arrayAmount; ++i) {
            bytes[i] = (byte)((int)(ori >>> (arrayAmount - i - 1) * 8 & 255L));
        }

        return bytes;
    }

    public static byte[] long2bytes(long ori, byte[] targetBytes, int offset, int arrayAmount) {
        for(int i = 0; i < arrayAmount; ++i) {
            targetBytes[offset + i] = (byte)((int)(ori >>> (arrayAmount - i - 1) * 8 & 255L));
        }

        return targetBytes;
    }

    public static long bytes2long(byte[] ori, int offset, int len) {
        long result = 0L;

        for(int i = 0; i < len; ++i) {
            result |= (255L & (long)ori[offset + i]) << (len - 1 - i) * 8;
        }

        return result;
    }

    public static long bytes2long(byte[] ori) {
        return bytes2long(ori, 0, ori.length);
    }

    private static int hexChar2byte(char c) {
        switch(c) {
            case '0':
                return 0;
            case '1':
                return 1;
            case '2':
                return 2;
            case '3':
                return 3;
            case '4':
                return 4;
            case '5':
                return 5;
            case '6':
                return 6;
            case '7':
                return 7;
            case '8':
                return 8;
            case '9':
                return 9;
            case ':':
            case ';':
            case '<':
            case '=':
            case '>':
            case '?':
            case '@':
            case 'G':
            case 'H':
            case 'I':
            case 'J':
            case 'K':
            case 'L':
            case 'M':
            case 'N':
            case 'O':
            case 'P':
            case 'Q':
            case 'R':
            case 'S':
            case 'T':
            case 'U':
            case 'V':
            case 'W':
            case 'X':
            case 'Y':
            case 'Z':
            case '[':
            case '\\':
            case ']':
            case '^':
            case '_':
            case '`':
            default:
                return -1;
            case 'A':
            case 'a':
                return 10;
            case 'B':
            case 'b':
                return 11;
            case 'C':
            case 'c':
                return 12;
            case 'D':
            case 'd':
                return 13;
            case 'E':
            case 'e':
                return 14;
            case 'F':
            case 'f':
                return 15;
        }
    }

    public static String toBinString(long value, int byteLen, boolean withDivider) {
        int bitLen = byteLen * 8;
        char[] chars = new char[bitLen];
        Arrays.fill(chars, '0');
        int charPos = bitLen;

        do {
            --charPos;
            if ((value & 1L) > 0L) {
                chars[charPos] = '1';
            }

            value >>>= 1;
        } while(value != 0L && charPos > 0);

        if (withDivider && byteLen > 1) {
            StringBuilder stringBuilder = new StringBuilder();
            boolean alreadyAppend = false;

            for(int i = 0; i < byteLen; ++i) {
                if (alreadyAppend) {
                    stringBuilder.append(' ');
                } else {
                    alreadyAppend = true;
                }

                stringBuilder.append(chars, i * 8, 8);
            }

            return stringBuilder.toString();
        } else {
            return new String(chars);
        }
    }

    public static String toBinString(long value, int byteLen) {
        return toBinString(value, byteLen, true);
    }

    public static byte getXOR(byte[] bytes, int offset, int len) {
        byte toDiff = 0;

        for(int i = 0; i < len; ++i) {
            toDiff ^= bytes[i + offset];
        }

        return toDiff;
    }

    public static int getBitFromLeft(byte[] bytes, int dataOffset, int bitPos) {
        int byteIndex = (bitPos - 1) / 8;
        int bitIndex = (bitPos - 1) % 8;
        return (bytes[dataOffset + byteIndex] & 1 << bitIndex) != 0 ? 1 : 0;
    }
}
