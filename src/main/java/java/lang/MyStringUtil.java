/*
 * Copyright (c) 1986-2016, Serkan OZAL, All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package java.lang;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.Locale;

public final class MyStringUtil {

    public static final int ERROR = Character.ERROR;
    
    private MyStringUtil() {
    }

    public static int toUpperCaseEx(int codePoint) {
        return Character.toUpperCaseEx(codePoint);
    }
    
    public static char[] toUpperCaseCharArray(int codePoint) {
        return Character.toUpperCaseCharArray(codePoint);
    }
    
    public static int toLowerCaseEx(String src, int index, Locale locale) {
        return ConditionalSpecialCasing.toLowerCaseEx(src, index, locale);
    }
    
    public static int toUpperCaseEx(String src, int index, Locale locale) {
        return ConditionalSpecialCasing.toUpperCaseEx(src, index, locale);
    }
    
    public static char[] toLowerCaseCharArray(String src, int index, Locale locale) {
        return ConditionalSpecialCasing.toLowerCaseCharArray(src, index, locale);
    }

    public static char[] toUpperCaseCharArray(String src, int index, Locale locale) {
        return ConditionalSpecialCasing.toUpperCaseCharArray(src, index, locale);
    }
    
    public static char[] decode(byte[] ba, int off, int len) {
        return StringCoding.decode(ba, off, len);
    }
    
    public static char[] decode(String charsetName, byte[] ba, int off, int len)
            throws UnsupportedEncodingException {
        return StringCoding.decode(charsetName, ba, off, len);
    }

    public static char[] decode(Charset cs, byte[] ba, int off, int len) {
        return StringCoding.decode(cs, ba, off, len);
    }
    
    public static byte[] encode(char[] ca, int off, int len) {
        return StringCoding.encode(ca, off, len);
    }
    
    public static byte[] encode(String charsetName, char[] ca, int off, int len)
            throws UnsupportedEncodingException {
        return StringCoding.encode(charsetName, ca, off, len);
    }

    public static byte[] encode(Charset cs, char[] ca, int off, int len) {
        return StringCoding.encode(cs, ca, off, len);
    }

}
