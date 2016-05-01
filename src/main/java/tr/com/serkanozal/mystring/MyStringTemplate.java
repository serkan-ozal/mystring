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

package tr.com.serkanozal.mystring;

import java.io.ObjectStreamField;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import sun.misc.Unsafe;
import tr.com.serkanozal.mystring.api.MyString;
import tr.com.serkanozal.mystring.api.MyStringProcessor;
import tr.com.serkanozal.mystring.util.JvmUtil;

@SuppressWarnings({ "deprecation", "rawtypes", "unchecked" })
public final class MyStringTemplate
        implements Serializable, Comparable<String>, CharSequence, MyString {
    
    private static final Unsafe UNSAFE = JvmUtil.getUnsafe();

    private static final long VALUE_FIELD_OFFSET;
    private static final long HASH_FIELD_OFFSET;

    static {
        try {
            VALUE_FIELD_OFFSET = UNSAFE.fieldOffset(String.class.getDeclaredField("value"));
            HASH_FIELD_OFFSET = UNSAFE.fieldOffset(String.class.getDeclaredField("hash"));
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }
    
    /** The number of stored characters */
    private int length;
    
    /** Cache the hash code for the string */
    private int hash; // Default to 0
    
    /** Id of the storage */
    private long storageId;
    
    /** Base object for the storage */
    private Object storageBase;
    
    /** Size of the storage */
    private long storageSize;
    
    /** Pluggable processor for allocating storage and reading/writing char from/to storage */
    private final MyStringProcessor myStrProcessor;

    /** use serialVersionUID from JDK 1.0.2 for interoperability */
    private static final long serialVersionUID = -6849794470754667710L;

    /**
     * Class String is special cased within the Serialization Stream Protocol.
     *
     * A String instance is written into an ObjectOutputStream according to
     * <a href="{@docRoot}/../platform/serialization/spec/output.html">
     * Object Serialization Specification, Section 6.2, "Stream Elements"</a>
     */
    private static final ObjectStreamField[] serialPersistentFields =
        new ObjectStreamField[0];

    public MyStringTemplate(MyStringProcessor myStrProcessor, String original) {
        this.myStrProcessor = myStrProcessor;
        createStorage(original);
        this.hash = getHash(original);
        this.length = original.length();
        init();
    }

    public MyStringTemplate(MyStringProcessor myStrProcessor, char value[]) {
        this.myStrProcessor = myStrProcessor;
        createStorage(value);
        this.length = value.length;
        init();
    }

    public MyStringTemplate(MyStringProcessor myStrProcessor, char value[], int offset, int count) {
        if (offset < 0) {
            throw new StringIndexOutOfBoundsException(offset);
        }
        if (count < 0) {
            throw new StringIndexOutOfBoundsException(count);
        }
        // Note: offset or count might be near -1>>>1.
        if (offset > value.length - count) {
            throw new StringIndexOutOfBoundsException(offset + count);
        }
        this.myStrProcessor = myStrProcessor;
        createStorage(value, offset, count);
        this.length = count;
        init();
    }

    public MyStringTemplate(MyStringProcessor myStrProcessor, byte ascii[], int hibyte) {
        this(myStrProcessor, ascii, hibyte, 0, ascii.length);
    }

    public MyStringTemplate(MyStringProcessor myStrProcessor, byte ascii[], int hibyte, int offset, int count) {
        checkBounds(ascii, offset, count);
        createStorage(count);
        this.myStrProcessor = myStrProcessor;
        this.length = count;
        if (hibyte == 0) {
            for (int i = count; i-- > 0;) {
                writeValue(i, (char)(ascii[i + offset] & 0xff));
            }
        } else {
            hibyte <<= 8;
            for (int i = count; i-- > 0;) {
                writeValue(i,  (char)(hibyte | (ascii[i + offset] & 0xff)));
            }
        }
        init();
    }

    ///////////////////////////////////////////////////////////////////////////
    
    private boolean isMyString(String str) {
        return ((Object) str) instanceof MyStringTemplate;
    }
    
    private char[] getValue(String str) {
        return (char[]) UNSAFE.getObject(str, VALUE_FIELD_OFFSET);
    }
    
    private int getLength(String str) {
        return str.length();
    }
    
    private int getHash(String str) {
        return UNSAFE.getInt(str, HASH_FIELD_OFFSET);
    }
    
    private void createStorage(long size) {
        storageId = myStrProcessor.createStorageId(size);
        storageBase = myStrProcessor.createStorageBase(storageId, size);
        storageSize = myStrProcessor.getStorageSize(storageId, size);
    }
    
    private void createStorage(char[] value, int offset, int length) {
        storageId = myStrProcessor.createStorageId(value, offset, length);
        storageBase = myStrProcessor.createStorageBase(storageId, value, offset, length);
        storageSize = myStrProcessor.getStorageSize(storageId, value, offset, length);
    }
    
    private void createStorage(char[] value) {
        createStorage(value, 0, value.length);
    }
    
    private void createStorage(MyStringTemplate myStr) {
        storageId = myStrProcessor.createStorageId(myStr);
        storageBase = myStrProcessor.createStorageBase(storageId, myStr);
        storageSize = myStrProcessor.getStorageSize(storageId, myStr);
    }

    private void createStorage(String str) {
        boolean isMyString = isMyString(str);
        if (isMyString) {
            MyStringTemplate myStr = (MyStringTemplate) ((Object) str);
            createStorage(myStr);
        } else {
            createStorage(getValue(str));
        }    
    }

    private char readValue(int index) {
        return myStrProcessor.readValue(index, storageBase, index);
    }
    
    private char readValue(int index, Object str, char[] value, boolean isMyString) {
        if (isMyString) {
            return ((MyStringTemplate) str).readValue(index);
        } else {
            return value[index];
        }    
    }

    private void writeValue(int index, char c) {
        myStrProcessor.writeValue(c, storageBase, index, c);
    }
    
    @SuppressWarnings("unused")
    private void writeValue(int index, char c, Object str, char[] value, boolean isMyString) {
        if (isMyString) {
            ((MyStringTemplate) str).writeValue(index, c);
        } else {
            value[index] = c;
        }    
    }

    private void copyValue(int srcBegin, char[] dst, int dstBegin, int len) {
        myStrProcessor.copyValue(storageId, storageBase, srcBegin, dst, dstBegin, len);
    }
    
    private void checkBounds(byte[] bytes, int offset, int length) {
        if (length < 0) {
            throw new StringIndexOutOfBoundsException(length);
        }    
        if (offset < 0) {
            throw new StringIndexOutOfBoundsException(offset);
        }    
        if (offset > bytes.length - length) {
            throw new StringIndexOutOfBoundsException(offset + length);
        }    
    }

    private byte[] encode(int off, int len) {
        throw new UnsupportedOperationException();
    }
    
    private byte[] encode(String charsetName, int off, int len) {
        throw new UnsupportedOperationException();
    }
    
    private byte[] encode(Charset cs, int off, int len) {
        throw new UnsupportedOperationException();
    }
    
    private void init() {
        UNSAFE.putObject(this, VALUE_FIELD_OFFSET, null);
    }

    @Override
    public int length() {
        return length;
    }

    @Override
    public boolean isEmpty() {
        return length == 0;
    }

    @Override
    public char charAt(int index) {
        if ((index < 0) || (index >= length)) {
            throw new StringIndexOutOfBoundsException(index);
        }
        return readValue(index);
    }
    
    private int codePointAtImpl(int index, int limit) {
        char c1 = readValue(index++);
        if (Character.isHighSurrogate(c1)) {
            if (index < limit) {
                char c2 = readValue(index);
                if (Character.isLowSurrogate(c2)) {
                    return Character.toCodePoint(c1, c2);
                }
            }
        }
        return c1;
    }

    @Override
    public int codePointAt(int index) {
        if ((index < 0) || (index >= length)) {
            throw new StringIndexOutOfBoundsException(index);
        }
        return codePointAtImpl(index, length);
    }
    
    private int codePointBeforeImpl(int index, int start) {
        char c2 = readValue(--index);
        if (Character.isLowSurrogate(c2)) {
            if (index > start) {
                char c1 = readValue(--index);
                if (Character.isHighSurrogate(c1)) {
                    return Character.toCodePoint(c1, c2);
                }
            }
        }
        return c2;
    }

    @Override
    public int codePointBefore(int index) {
        int i = index - 1;
        if ((i < 0) || (i >= length)) {
            throw new StringIndexOutOfBoundsException(index);
        }
        return codePointBeforeImpl(index, 0);
    }
    
    private int codePointCountImpl(int offset, int count) {
        int endIndex = offset + count;
        int n = count;
        for (int i = offset; i < endIndex;) {
            if (Character.isHighSurrogate(readValue(i++)) && i < endIndex
                    && Character.isLowSurrogate(readValue(i))) {
                n--;
                i++;
            }
        }
        return n;
    }

    @Override
    public int codePointCount(int beginIndex, int endIndex) {
        if (beginIndex < 0 || endIndex > length || beginIndex > endIndex) {
            throw new IndexOutOfBoundsException();
        }
        return codePointCountImpl(beginIndex, endIndex - beginIndex);
    }
    
    private int offsetByCodePointsImpl(int start, int count, int index,
            int codePointOffset) {
        int x = index;
        if (codePointOffset >= 0) {
            int limit = start + count;
            int i;
            for (i = 0; x < limit && i < codePointOffset; i++) {
                if (Character.isHighSurrogate(readValue(x++)) && x < limit
                        && Character.isLowSurrogate(readValue(x))) {
                    x++;
                }
            }
            if (i < codePointOffset) {
                throw new IndexOutOfBoundsException();
            }
        } else {
            int i;
            for (i = codePointOffset; x > start && i < 0; i++) {
                if (Character.isLowSurrogate(readValue(--x)) && x > start
                        && Character.isHighSurrogate(readValue(x - 1))) {
                    x--;
                }
            }
            if (i < 0) {
                throw new IndexOutOfBoundsException();
            }
        }
        return x;
    }

    @Override
    public int offsetByCodePoints(int index, int codePointOffset) {
        if (index < 0 || index > length) {
            throw new IndexOutOfBoundsException();
        }
        return offsetByCodePointsImpl(0, length, index, codePointOffset);
    }

    @Override
    public void getChars(int srcBegin, int srcEnd, char dst[], int dstBegin) {
        if (srcBegin < 0) {
            throw new StringIndexOutOfBoundsException(srcBegin);
        }
        if (srcEnd > length) {
            throw new StringIndexOutOfBoundsException(srcEnd);
        }
        if (srcBegin > srcEnd) {
            throw new StringIndexOutOfBoundsException(srcEnd - srcBegin);
        }
        copyValue(srcBegin, dst, dstBegin, srcEnd - srcBegin);
    }

    @Override
    public void getBytes(int srcBegin, int srcEnd, byte dst[], int dstBegin) {
        if (srcBegin < 0) {
            throw new StringIndexOutOfBoundsException(srcBegin);
        }
        if (srcEnd > length) {
            throw new StringIndexOutOfBoundsException(srcEnd);
        }
        if (srcBegin > srcEnd) {
            throw new StringIndexOutOfBoundsException(srcEnd - srcBegin);
        }

        int j = dstBegin;
        int n = srcEnd;
        int i = srcBegin;

        while (i < n) {
            dst[j++] = (byte) readValue(i++);
        }
    }

    @Override
    public byte[] getBytes(String charsetName)
            throws UnsupportedEncodingException {
        if (charsetName == null) {
            throw new NullPointerException("charsetName");
        }
        return encode(charsetName, 0, length);
    }

    @Override
    public byte[] getBytes(Charset charset) {
        if (charset == null) {
            throw new NullPointerException("charset");
        }
        return encode(charset, 0, length);
    }

    @Override
    public byte[] getBytes() {
        return encode(0, length);
    }

    @Override
    public boolean equals(Object anObject) {
        if (this == anObject) {
            return true;
        }
        if (anObject instanceof String) {
            String anotherString = (String)anObject;
            int n = length;
            boolean isMyString = anObject instanceof MyStringTemplate;
            char[] anotherValue = getValue(anotherString);
            int anotherLength = anotherValue != null ? anotherValue.length : 0;
            if (n == anotherLength) {
                int i = 0;
                while (n-- != 0) {
                    if (readValue(i) != readValue(i, anObject, anotherValue, isMyString)) {
                        return false;
                    }    
                    i++;
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean contentEquals(StringBuffer sb) {
        return contentEquals((CharSequence) sb);
    }

    @Override
    public boolean contentEquals(CharSequence cs) {
        // Argument is a String
        if (cs instanceof String) {
            return equals(cs);
        }
        // Argument is a generic CharSequence
        int n = length;
        if (n != cs.length()) {
            return false;
        }
        for (int i = 0; i < n; i++) {
            if (readValue(i) != cs.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean equalsIgnoreCase(String anotherString) {
        return (this == (Object) anotherString) ? true
                : (anotherString != null)
                && (getLength(anotherString) == length)
                && regionMatches(true, 0, anotherString, 0, length);
    }

    @Override
    public int compareTo(String anotherString) {
        int len1 = length;
        int len2 = getLength(anotherString);
        int lim = Math.min(len1, len2);
        boolean isMyString = isMyString(anotherString);
        char[] anotherValue = getValue(anotherString);

        int k = 0;
        while (k < lim) {
            char c1 = readValue(k);
            char c2 = readValue(k, anotherString, anotherValue, isMyString);
            if (c1 != c2) {
                return c1 - c2;
            }
            k++;
        }
        return len1 - len2;
    }

    public static final CaseInsensitiveComparator CASE_INSENSITIVE_ORDER = null; //new CaseInsensitiveComparator();
   
    private static class CaseInsensitiveComparator
            implements Serializable {
        
        // use serialVersionUID from JDK 1.2.2 for interoperability
        private static final long serialVersionUID = 8575799808933029326L;

        public int compare(MyStringTemplate s1, String s2) {
            int n1 = s1.length();
            int n2 = s2.length();
            int min = Math.min(n1, n2);
            for (int i = 0; i < min; i++) {
                char c1 = s1.charAt(i);
                char c2 = s2.charAt(i);
                if (c1 != c2) {
                    c1 = Character.toUpperCase(c1);
                    c2 = Character.toUpperCase(c2);
                    if (c1 != c2) {
                        c1 = Character.toLowerCase(c1);
                        c2 = Character.toLowerCase(c2);
                        if (c1 != c2) {
                            // No overflow because of numeric promotion
                            return c1 - c2;
                        }
                    }
                }
            }
            return n1 - n2;
        }

        /** Replaces the de-serialized object. */
        private Object readResolve() { return CASE_INSENSITIVE_ORDER; }
    }

    @Override
    public int compareToIgnoreCase(String str) {
        return CASE_INSENSITIVE_ORDER.compare(this, str);
    }

    @Override
    public boolean regionMatches(int toffset, String other, int ooffset, int len) {
        int to = toffset;
        boolean isMyString = isMyString(other);
        char[] otherValue = getValue(other);
        int otherLength = other.length();
        int po = ooffset;
        // Note: toffset, ooffset, or len might be near -1>>>1.
        if ((ooffset < 0) || (toffset < 0)
                || (toffset > (long) length - len)
                || (ooffset > (long) otherLength - len)) {
            return false;
        }
        while (len-- > 0) {
            if (readValue(to++) != readValue(po++, other, otherValue, isMyString)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean regionMatches(boolean ignoreCase, int toffset,
            String other, int ooffset, int len) {
        int to = toffset;
        boolean isMyString = isMyString(other);
        char[] otherValue = getValue(other);
        int otherLength = other.length();
        int po = ooffset;
        // Note: toffset, ooffset, or len might be near -1>>>1.
        if ((ooffset < 0) || (toffset < 0)
                || (toffset > (long) length - len)
                || (ooffset > (long) otherLength - len)) {
            return false;
        }
        while (len-- > 0) {
            char c1 = readValue(to++);
            char c2 = readValue(po++, other, otherValue, isMyString);
            if (c1 == c2) {
                continue;
            }
            if (ignoreCase) {
                // If characters don't match but case may be ignored,
                // try converting both characters to uppercase.
                // If the results match, then the comparison scan should
                // continue.
                char u1 = Character.toUpperCase(c1);
                char u2 = Character.toUpperCase(c2);
                if (u1 == u2) {
                    continue;
                }
                // Unfortunately, conversion to uppercase does not work properly
                // for the Georgian alphabet, which has strange rules about case
                // conversion.  So we need to make one last check before
                // exiting.
                if (Character.toLowerCase(u1) == Character.toLowerCase(u2)) {
                    continue;
                }
            }
            return false;
        }
        return true;
    }

    @Override
    public boolean startsWith(String prefix, int toffset) {
        int to = toffset;
        boolean isMyString = isMyString(prefix);
        char[] prefixValue = getValue(prefix);
        int prefixLength = getLength(prefix);
        int po = 0;
        int pc = prefixLength;
        // Note: toffset might be near -1>>>1.
        if ((toffset < 0) || (toffset > length - pc)) {
            return false;
        }
        while (--pc >= 0) {
            if (readValue(to++) != readValue(po++, prefix, prefixValue, isMyString)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean startsWith(String prefix) {
        return startsWith(prefix, 0);
    }

    @Override
    public boolean endsWith(String suffix) {
        return startsWith(suffix, length - getLength(suffix));
    }

    @Override
    public int hashCode() {
        int h = hash;
        if (h == 0 && length > 0) {
            for (int i = 0; i < length; i++) {
                h = 31 * h + readValue(i);
            }
            hash = h;
        }
        return h;
    }

    @Override
    public int indexOf(int ch) {
        return indexOf(ch, 0);
    }

    @Override
    public int indexOf(int ch, int fromIndex) {
        final int max = length;
        if (fromIndex < 0) {
            fromIndex = 0;
        } else if (fromIndex >= max) {
            // Note: fromIndex might be near -1>>>1.
            return -1;
        }

        if (ch < Character.MIN_SUPPLEMENTARY_CODE_POINT) {
            // handle most cases here (ch is a BMP code point or a
            // negative value (invalid code point))
            for (int i = fromIndex; i < max; i++) {
                if (readValue(i) == ch) {
                    return i;
                }
            }
            return -1;
        } else {
            return indexOfSupplementary(ch, fromIndex);
        }
    }
    
    private char highSurrogate(int codePoint) {
        return (char) ((codePoint >>> 10)
                + (Character.MIN_HIGH_SURROGATE - (Character.MIN_SUPPLEMENTARY_CODE_POINT >>> 10)));
    }
    
    private char lowSurrogate(int codePoint) {
        return (char) ((codePoint & 0x3ff) + Character.MIN_LOW_SURROGATE);
    }

    private int indexOfSupplementary(int ch, int fromIndex) {
        if (Character.isValidCodePoint(ch)) {
            final char hi = highSurrogate(ch);
            final char lo = lowSurrogate(ch);
            final int max = length - 1;
            for (int i = fromIndex; i < max; i++) {
                if (readValue(i) == hi && readValue(i + 1) == lo) {
                    return i;
                }
            }
        }
        return -1;
    }

    @Override
    public int lastIndexOf(int ch) {
        return lastIndexOf(ch, length - 1);
    }

    @Override
    public int lastIndexOf(int ch, int fromIndex) {
        if (ch < Character.MIN_SUPPLEMENTARY_CODE_POINT) {
            // handle most cases here (ch is a BMP code point or a
            // negative value (invalid code point))
            int i = Math.min(fromIndex, length - 1);
            for (; i >= 0; i--) {
                if (readValue(i) == ch) {
                    return i;
                }
            }
            return -1;
        } else {
            return lastIndexOfSupplementary(ch, fromIndex);
        }
    }

    private int lastIndexOfSupplementary(int ch, int fromIndex) {
        if (Character.isValidCodePoint(ch)) {
            char hi = highSurrogate(ch);
            char lo = lowSurrogate(ch);
            int i = Math.min(fromIndex, length - 2);
            for (; i >= 0; i--) {
                if (readValue(i) == hi && readValue(i + 1) == lo) {
                    return i;
                }
            }
        }
        return -1;
    }

    @Override
    public int indexOf(String str) {
        return indexOf(str, 0);
    }

    @Override
    public int indexOf(String str, int fromIndex) {
        return indexOf(0, length, str, 0, getLength(str), fromIndex);
    }

    private int indexOf(int sourceOffset, int sourceCount,
                        String target, int targetOffset, int targetCount,
                        int fromIndex) {
        if (fromIndex >= sourceCount) {
            return (targetCount == 0 ? sourceCount : -1);
        }
        if (fromIndex < 0) {
            fromIndex = 0;
        }
        if (targetCount == 0) {
            return fromIndex;
        }

        boolean isMyString = isMyString(target);
        char[] targetValue = getValue(target);
        char first = readValue(targetOffset, target, targetValue, isMyString);
        int max = sourceOffset + (sourceCount - targetCount);

        for (int i = sourceOffset + fromIndex; i <= max; i++) {
            /* Look for first character. */
            if (readValue(i) != first) {
                while (++i <= max && readValue(i) != first);
            }

            /* Found first character, now look at the rest of v2 */
            if (i <= max) {
                int j = i + 1;
                int end = j + targetCount - 1;
                for (int k = targetOffset + 1; j < end && 
                        readValue(j) == readValue(k, target, targetValue, isMyString); j++, k++);

                if (j == end) {
                    /* Found whole string. */
                    return i - sourceOffset;
                }
            }
        }
        return -1;
    }

    @Override
    public int lastIndexOf(String str) {
        return lastIndexOf(str, length);
    }

    @Override
    public int lastIndexOf(String str, int fromIndex) {
        return lastIndexOf(0, length,
                           str, 0, getLength(str), 
                           fromIndex);
    }

    private int lastIndexOf(int sourceOffset, int sourceCount,
                            String target, int targetOffset, int targetCount,
                            int fromIndex) {
        /*
         * Check arguments; return immediately where possible. For
         * consistency, don't check for null str.
         */
        int rightIndex = sourceCount - targetCount;
        if (fromIndex < 0) {
            return -1;
        }
        if (fromIndex > rightIndex) {
            fromIndex = rightIndex;
        }
        /* Empty string always matches. */
        if (targetCount == 0) {
            return fromIndex;
        }

        boolean isMyString = isMyString(target);
        char[] targetValue = getValue(target);
        
        int strLastIndex = targetOffset + targetCount - 1;
        char strLastChar = readValue(strLastIndex, target, targetValue, isMyString);
        int min = sourceOffset + targetCount - 1;
        int i = min + fromIndex;

    startSearchForLastChar:
        while (true) {
            while (i >= min && readValue(i) != strLastChar) {
                i--;
            }
            if (i < min) {
                return -1;
            }
            int j = i - 1;
            int start = j - (targetCount - 1);
            int k = strLastIndex - 1;

            while (j > start) {
                if (readValue(j--) != readValue(k--, target, targetValue, isMyString)) {
                    i--;
                    continue startSearchForLastChar;
                }
            }
            return start - sourceOffset + 1;
        }
    }

    @Override
    public String substring(int beginIndex) {
        if (beginIndex < 0) {
            throw new StringIndexOutOfBoundsException(beginIndex);
        }
        int subLen = length - beginIndex;
        if (subLen < 0) {
            throw new StringIndexOutOfBoundsException(subLen);
        }
        return (beginIndex == 0) 
                    ? this.toString() 
                    : new String(toCharArray(), beginIndex, subLen);
    }

    @Override
    public String substring(int beginIndex, int endIndex) {
        if (beginIndex < 0) {
            throw new StringIndexOutOfBoundsException(beginIndex);
        }
        if (endIndex > length) {
            throw new StringIndexOutOfBoundsException(endIndex);
        }
        int subLen = endIndex - beginIndex;
        if (subLen < 0) {
            throw new StringIndexOutOfBoundsException(subLen);
        }
        return ((beginIndex == 0) && (endIndex == length)) 
                ? this.toString()
                : new String(toCharArray(), beginIndex, subLen);
    }

    @Override
    public CharSequence subSequence(int beginIndex, int endIndex) {
        return this.substring(beginIndex, endIndex);
    }

    @Override
    public String concat(String str) {
        int otherLen = str.length();
        if (otherLen == 0) {
            return this.toString();
        }
        int len = length;
        char buf[] = new char[len + otherLen];
        getChars(0, len, buf, 0);
        str.getChars(0, otherLen, buf, len);
        return new String(buf);
    }

    @Override
    public String replace(char oldChar, char newChar) {
        if (oldChar != newChar) {
            int len = length;
            int i = -1;

            while (++i < len) {
                if (readValue(i) == oldChar) {
                    break;
                }
            }
            if (i < len) {
                char buf[] = new char[len];
                for (int j = 0; j < i; j++) {
                    buf[j] = readValue(j);
                }
                while (i < len) {
                    char c = readValue(i);
                    buf[i] = (c == oldChar) ? newChar : c;
                    i++;
                }
                return new String(buf);
            }
        }
        return this.toString();
    }

    @Override
    public boolean matches(String regex) {
        return Pattern.matches(regex, this);
    }

    @Override
    public boolean contains(CharSequence s) {
        return indexOf(s.toString()) > -1;
    }

    @Override
    public String replaceFirst(String regex, String replacement) {
        return Pattern
                .compile(regex)
                .matcher(this)
                .replaceFirst(replacement);
    }

    @Override
    public String replaceAll(String regex, String replacement) {
        return Pattern
                .compile(regex)
                .matcher(this)
                .replaceAll(replacement);
    }

    @Override
    public String replace(CharSequence target, CharSequence replacement) {
        return Pattern
                .compile(target.toString(), Pattern.LITERAL)
                .matcher(this)
                .replaceAll(Matcher.quoteReplacement(replacement.toString()));
    }

    @Override
    public String[] split(String regex, int limit) {
        char ch = 0;
        if (((getLength(regex) == 1 &&
             ".$|()[{^?*+\\".indexOf(ch = regex.charAt(0)) == -1) ||
             (regex.length() == 2 &&
              regex.charAt(0) == '\\' &&
              (((ch = regex.charAt(1))-'0')|('9'-ch)) < 0 &&
              ((ch-'a')|('z'-ch)) < 0 &&
              ((ch-'A')|('Z'-ch)) < 0)) &&
            (ch < Character.MIN_HIGH_SURROGATE ||
             ch > Character.MAX_LOW_SURROGATE))
        {
            int off = 0;
            int next = 0;
            boolean limited = limit > 0;
            ArrayList<String> list = new ArrayList<String>();
            while ((next = indexOf(ch, off)) != -1) {
                if (!limited || list.size() < limit - 1) {
                    list.add(substring(off, next));
                    off = next + 1;
                } else {    // last one
                    //assert (list.size() == limit - 1);
                    list.add(substring(off, length));
                    off = length;
                    break;
                }
            }
            // If no match was found, return this
            if (off == 0) {
                return new String[]{this.toString()};
            }
            // Add remaining segment
            if (!limited || list.size() < limit)
                list.add(substring(off, length));

            // Construct result
            int resultSize = list.size();
            if (limit == 0) {
                while (resultSize > 0 && list.get(resultSize - 1).length() == 0) {
                    resultSize--;
                }
            }
            String[] result = new String[resultSize];
            return list.subList(0, resultSize).toArray(result);
        }
        return Pattern.compile(regex).split(this, limit);
    }

    @Override
    public String[] split(String regex) {
        return split(regex, 0);
    }

    @Override
    public String toLowerCase(Locale locale) {
        if (locale == null) {
            throw new NullPointerException();
        }

        int firstUpper;
        final int len = length;

        /* Now check if there are any characters that need to be changed. */
        scan: {
            for (firstUpper = 0 ; firstUpper < len; ) {
                char c = readValue(firstUpper);
                if ((c >= Character.MIN_HIGH_SURROGATE)
                        && (c <= Character.MAX_HIGH_SURROGATE)) {
                    int supplChar = codePointAt(firstUpper);
                    if (supplChar != Character.toLowerCase(supplChar)) {
                        break scan;
                    }
                    firstUpper += Character.charCount(supplChar);
                } else {
                    if (c != Character.toLowerCase(c)) {
                        break scan;
                    }
                    firstUpper++;
                }
            }
            return this.toString();
        }

        char[] result = new char[len];
        int resultOffset = 0;  /* result may grow, so i+resultOffset
                                * is the write location in result */

        /* Just copy the first few lowerCase characters. */
        copyValue(0, result, 0, firstUpper);

        String lang = locale.getLanguage();
        boolean localeDependent =
                (lang == "tr" || lang == "az" || lang == "lt");
        char[] lowerCharArray;
        int lowerChar;
        int srcChar;
        int srcCount;
        for (int i = firstUpper; i < len; i += srcCount) {
            srcChar = (int) readValue(i);
            if ((char)srcChar >= Character.MIN_HIGH_SURROGATE
                    && (char)srcChar <= Character.MAX_HIGH_SURROGATE) {
                srcChar = codePointAt(i);
                srcCount = Character.charCount(srcChar);
            } else {
                srcCount = 1;
            }
            if (localeDependent ||
                srcChar == '\u03A3' || // GREEK CAPITAL LETTER SIGMA
                srcChar == '\u0130') { // LATIN CAPITAL LETTER I WITH DOT ABOVE
                lowerChar = MyStringUtil.toLowerCaseEx(this.toString(), i, locale);
            } else {
                lowerChar = Character.toLowerCase(srcChar);
            }
            if ((lowerChar == MyStringUtil.ERROR)
                    || (lowerChar >= Character.MIN_SUPPLEMENTARY_CODE_POINT)) {
                if (lowerChar == MyStringUtil.ERROR) {
                    lowerCharArray =
                            MyStringUtil.toLowerCaseCharArray(this.toString(), i, locale);
                } else if (srcCount == 2) {
                    resultOffset += Character.toChars(lowerChar, result, i + resultOffset) - srcCount;
                    continue;
                } else {
                    lowerCharArray = Character.toChars(lowerChar);
                }

                /* Grow result if needed */
                int mapLen = lowerCharArray.length;
                if (mapLen > srcCount) {
                    char[] result2 = new char[result.length + mapLen - srcCount];
                    System.arraycopy(result, 0, result2, 0, i + resultOffset);
                    result = result2;
                }
                for (int x = 0; x < mapLen; ++x) {
                    result[i + resultOffset + x] = lowerCharArray[x];
                }
                resultOffset += (mapLen - srcCount);
            } else {
                result[i + resultOffset] = (char)lowerChar;
            }
        }
        return new String(result, 0, len + resultOffset);
    }

    @Override
    public String toLowerCase() {
        return toLowerCase(Locale.getDefault());
    }

    @Override
    public String toUpperCase(Locale locale) {
        if (locale == null) {
            throw new NullPointerException();
        }

        int firstLower;
        final int len = length;

        /* Now check if there are any characters that need to be changed. */
        scan: {
            for (firstLower = 0 ; firstLower < len; ) {
                int c = (int) readValue(firstLower);
                int srcCount;
                if ((c >= Character.MIN_HIGH_SURROGATE)
                        && (c <= Character.MAX_HIGH_SURROGATE)) {
                    c = codePointAt(firstLower);
                    srcCount = Character.charCount(c);
                } else {
                    srcCount = 1;
                }
                int upperCaseChar = MyStringUtil.toUpperCaseEx(c);
                if ((upperCaseChar == MyStringUtil.ERROR)
                        || (c != upperCaseChar)) {
                    break scan;
                }
                firstLower += srcCount;
            }
            return this.toString();
        }

        /* result may grow, so i+resultOffset is the write location in result */
        int resultOffset = 0;
        char[] result = new char[len]; /* may grow */

        /* Just copy the first few upperCase characters. */
        copyValue(0, result, 0, firstLower);

        String lang = locale.getLanguage();
        boolean localeDependent =
                (lang == "tr" || lang == "az" || lang == "lt");
        char[] upperCharArray;
        int upperChar;
        int srcChar;
        int srcCount;
        for (int i = firstLower; i < len; i += srcCount) {
            srcChar = (int) readValue(i);
            if ((char)srcChar >= Character.MIN_HIGH_SURROGATE &&
                (char)srcChar <= Character.MAX_HIGH_SURROGATE) {
                srcChar = codePointAt(i);
                srcCount = Character.charCount(srcChar);
            } else {
                srcCount = 1;
            }
            if (localeDependent) {
                upperChar = MyStringUtil.toUpperCaseEx(this.toString(), i, locale);
            } else {
                upperChar = MyStringUtil.toUpperCaseEx(srcChar);
            }
            if ((upperChar == MyStringUtil.ERROR)
                    || (upperChar >= Character.MIN_SUPPLEMENTARY_CODE_POINT)) {
                if (upperChar == MyStringUtil.ERROR) {
                    if (localeDependent) {
                        upperCharArray =
                                MyStringUtil.toUpperCaseCharArray(this.toString(), i, locale);
                    } else {
                        upperCharArray = MyStringUtil.toUpperCaseCharArray(srcChar);
                    }
                } else if (srcCount == 2) {
                    resultOffset += Character.toChars(upperChar, result, i + resultOffset) - srcCount;
                    continue;
                } else {
                    upperCharArray = Character.toChars(upperChar);
                }

                /* Grow result if needed */
                int mapLen = upperCharArray.length;
                if (mapLen > srcCount) {
                    char[] result2 = new char[result.length + mapLen - srcCount];
                    System.arraycopy(result, 0, result2, 0, i + resultOffset);
                    result = result2;
                }
                for (int x = 0; x < mapLen; ++x) {
                    result[i + resultOffset + x] = upperCharArray[x];
                }
                resultOffset += (mapLen - srcCount);
            } else {
                result[i + resultOffset] = (char)upperChar;
            }
        }
        return new String(result, 0, len + resultOffset);
    }

    @Override
    public String toUpperCase() {
        return toUpperCase(Locale.getDefault());
    }

    @Override
    public String trim() {
        int len = length;
        int st = 0;

        while ((st < len) && (readValue(st) <= ' ')) {
            st++;
        }
        while ((st < len) && (readValue(len - 1) <= ' ')) {
            len--;
        }
        return ((st > 0) || (len < length)) ? substring(st, len) : this.toString();
    }

    @Override
    public char[] toCharArray() {
        char result[] = new char[length];
        getChars(0, length, result, 0);
        return result;
    }
    
    @Override
    public String intern() {
        return (String) ((Object) this);
    }

    @Override
    public String toString() {
        return (String) ((Object) this);
        //return new String(toCharArray());
    }

    @Override
    public <S> MyStringProcessor<S> getMyStringProcessor() {
        return myStrProcessor;
    }

    @Override
    public long getStorageId() {
        return storageId;
    }
    
    @Override
    public <T> T getStorageBase() {
        return (T) storageBase;
    }

    @Override
    public long getStorageSize() {
        return storageSize;
    }
    
    // Note that this method is not thread-safe
    @Override
    public void destroy() {
        if (storageSize != INVALID_STORAGE_SIZE) {
            myStrProcessor.destroy(storageId, storageBase);
            storageSize = INVALID_STORAGE_SIZE;
            storageId = INVALID_STORAGE_ID;
            storageBase = INVALID_STORAGE_BASE;
        }    
    }

    @Override
    public IntStream chars() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public IntStream codePoints() {
        // TODO Auto-generated method stub
        return null;
    }

}
