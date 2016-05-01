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

package tr.com.serkanozal.mystring.offheap;

import sun.misc.Unsafe;
import tr.com.serkanozal.mystring.api.MyString;
import tr.com.serkanozal.mystring.api.MyStringProcessor;
import tr.com.serkanozal.mystring.util.JvmUtil;

public class OffHeapMyStringProcessor implements MyStringProcessor<Void> {

    private static final Unsafe UNSAFE = JvmUtil.getUnsafe();
    
    private static final long CHAR_ARRAY_BASE_OFFSET;
    private static final long CHAR_ARRAY_INDEX_SCALE;

    static {
        try {
            CHAR_ARRAY_BASE_OFFSET = UNSAFE.arrayBaseOffset(char[].class);
            CHAR_ARRAY_INDEX_SCALE = UNSAFE.arrayIndexScale(char[].class);
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }
    
    @Override
    public long createStorageId(long size) {
        return UNSAFE.allocateMemory(size);
    }
    
    @Override
    public Void createStorageBase(long storageId, long size) {
        return null;
    }
    
    @Override
    public long getStorageSize(long storageId, long size) {
        return size;
    }
    
    @Override
    public long createStorageId(char[] value, int offet, int length) {
        long start = CHAR_ARRAY_BASE_OFFSET + (CHAR_ARRAY_INDEX_SCALE * offet);
        long size = CHAR_ARRAY_INDEX_SCALE * length;
        long storageAddress = UNSAFE.allocateMemory(size);
        UNSAFE.copyMemory(value, start, null, storageAddress, size);
        return storageAddress;
    }
    
    @Override
    public Void createStorageBase(long storageId, char[] value, int offet, int length) {
        return null;
    }
    
    @Override
    public long getStorageSize(long storageId, char[] value, int offet, int length) {
        return CHAR_ARRAY_INDEX_SCALE * length;
    }
    
    @SuppressWarnings("rawtypes")
    @Override
    public long createStorageId(MyString myStr) {
        MyStringProcessor myStrProcessor = myStr.getMyStringProcessor();
        if (myStrProcessor instanceof OffHeapMyStringProcessor) {
            long size = myStr.getStorageSize();
            long storageAddressSrc = myStr.getStorageId();
            long storageAddressDst = UNSAFE.allocateMemory(size);
            UNSAFE.copyMemory(storageAddressSrc, storageAddressDst, size); 
            return storageAddressDst;
        } else {
            char[] value = myStr.toCharArray();
            return createStorageId(value, 0, value.length);
        }
    }
    
    @Override
    public Void createStorageBase(long storageId, MyString myStr) {
        return null;
    }
    
    @Override
    public long getStorageSize(long storageId, MyString myStr) {
        return myStr.getStorageSize();
    }
    
    @Override
    public char readValue(long storageId, Void storageBase, int index) {
        return UNSAFE.getChar(storageId + index);
    }
    
    @Override
    public void writeValue(long storageId, Void storageBase, int index, char c) {
        UNSAFE.putChar(storageId + index, c);
    }
    
    @Override
    public void copyValue(long storageId, Void storageBase, int srcBegin, char[] dst, int dstBegin, int len) {
        UNSAFE.copyMemory(null, storageId + (srcBegin * CHAR_ARRAY_INDEX_SCALE), 
                          dst, CHAR_ARRAY_BASE_OFFSET + (dstBegin * CHAR_ARRAY_INDEX_SCALE), 
                          len * CHAR_ARRAY_INDEX_SCALE);
    }
    
    @Override
    public void destroy(long storageId, Void storageBase) {
        UNSAFE.freeMemory(storageId);
    }

}
