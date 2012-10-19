package edu.fhda.luminis.gcf.mowa2010;

/**
 * A simple set of functions that enables URL escaping text string without adding any bulky dependencies.
 * @author fmucar, Stack Overflow, http://stackoverflow.com/questions/724043/http-url-address-encoding-in-java
 */
public class URLEncoder {

    /**
     * Escape special (unsafe) characters in a Java String.
     * @param input The text to be escaped
     * @return A Java String with special (unsafe) characters escaped
     */
    public static String encode(String input) {
        StringBuilder resultStr = new StringBuilder();
        for (char ch : input.toCharArray()) {
            if (isUnsafe(ch)) {
                resultStr.append('%');
                resultStr.append(toHex(ch / 16));
                resultStr.append(toHex(ch % 16));
            } else {
                resultStr.append(ch);
            }
        }
        return resultStr.toString();
    }

    private static char toHex(int ch) {
        return (char) (ch < 10 ? '0' + ch : 'A' + ch - 10);
    }

    private static boolean isUnsafe(char ch) {
        if (ch > 128 || ch < 0)
            return true;
        return " %$&+,/:;=?@<>#%".indexOf(ch) >= 0;
    }

}
