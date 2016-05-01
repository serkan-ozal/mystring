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

package tr.com.serkanozal.mystring.util;

import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.RuntimeMBeanException;
import javax.management.openmbean.CompositeDataSupport;

import sun.management.VMManagement;
import sun.misc.Unsafe;
import tr.com.serkanozal.mystring.jvm.JVM;
import tr.com.serkanozal.mystring.jvm.Type;

/**
 * @author Serkan OZAL
 * 
 * OOP and Klass layout for JDK-6:
 *      @link http://hg.openjdk.java.net/jdk6/jdk6/hotspot/file/964754807fa6/src/share/vm/oops/oop.hpp
 *      @link http://hg.openjdk.java.net/jdk6/jdk6/hotspot/file/964754807fa6/src/share/vm/oops/klass.hpp
 * 
 * OOP and Klass layout for JDK-7:
 *      @link http://hg.openjdk.java.net/jdk7/jdk7/hotspot/file/9b0ca45cd756/src/share/vm/oops/oop.hpp
 *      @link http://hg.openjdk.java.net/jdk7/jdk7/hotspot/file/9b0ca45cd756/src/share/vm/oops/klass.hpp
 * 
 * OOP and Klass layout for JDK-8:
 *      @link http://hg.openjdk.java.net/jdk8/jdk8/hotspot/file/87ee5ee27509/src/share/vm/oops/oop.hpp
 *      @link http://hg.openjdk.java.net/jdk8/jdk8/hotspot/file/87ee5ee27509/src/share/vm/oops/klass.hpp
 * 
 * @link https://blogs.oracle.com/jrockit/entry/understanding_compressed_refer
 * @link https://wikis.oracle.com/display/HotSpotInternals/CompressedOops
 * 
 * Note: Use "-XX:-UseCompressedOops" for 64 bit JVM to disable CompressedOops
 */
public class JvmUtil {
	
	public static final String JAVA_1_6 = "1.6";
	public static final String JAVA_1_7 = "1.7";
	public static final String JAVA_1_8 = "1.8";
	
	public static final String JAVA_VERSION = System.getProperty("java.version");
	public static final String JAVA_SPEC_VERSION = System.getProperty("java.specification.version");
	public static final String JAVA_RUNTIME_VERSION = System.getProperty("java.runtime.version");
	public static final String JAVA_VENDOR = System.getProperty("java.vendor");
	public static final String JVM_VENDOR = System.getProperty("java.vm.vendor");
	public static final String JVM_VERSION = System.getProperty("java.vm.version");
	public static final String JVM_NAME = System.getProperty("java.vm.name");
	public static final String OS_ARCH = System.getProperty("os.arch");
	public static final String OS_NAME = System.getProperty("os.name");
	public static final String OS_VERSION = System.getProperty("os.version");
	
	public static final JavaVersionInfo JAVA_VERSION_INFO = findJavaVersionInfo();
	  
	public static final byte SIZE_32_BIT = 4;
    public static final byte SIZE_64_BIT = 8;
    public static final byte INVALID_ADDRESS = -1;
    
    public static final byte ADDRESSING_4_BYTE = 4;
    public static final byte ADDRESSING_8_BYTE = 8;
    public static final byte ADDRESSING_16_BYTE = 16;

    public static final int NR_BITS = Integer.valueOf(System.getProperty("sun.arch.data.model"));
    public static final int BYTE = 8;
    public static final int WORD = NR_BITS / BYTE;
    public static final int MIN_SIZE = 16; 
    
    public static final int ADDRESS_SHIFT_SIZE_FOR_BETWEEN_32GB_AND_64_GB = 3; 
    public static final int ADDRESS_SHIFT_SIZE_FOR_BIGGER_THAN_64_GB = 4; 
    
    public static final int OBJECT_HEADER_SIZE_32_BIT = 8; 
    public static final int OBJECT_HEADER_SIZE_64_BIT = 12; 
    
    public static final int CLASS_DEF_POINTER_OFFSET_IN_OBJECT_FOR_32_BIT = 4;
    public static final int CLASS_DEF_POINTER_OFFSET_IN_OBJECT_FOR_64_BIT = 8;
    
    public static final int CLASS_DEF_POINTER_OFFSET_IN_CLASS_32_BIT_FOR_JAVA_1_6 = 8; 
    public static final int CLASS_DEF_POINTER_OFFSET_IN_CLASS_64_BIT_WITH_COMPRESSED_REF_FOR_JAVA_1_6 = 12;
    public static final int CLASS_DEF_POINTER_OFFSET_IN_CLASS_64_BIT_WITHOUT_COMPRESSED_REF_FOR_JAVA_1_6 = 16;
    
    public static final int CLASS_DEF_POINTER_OFFSET_IN_CLASS_32_BIT_FOR_JAVA_1_7 = 80; 
    public static final int CLASS_DEF_POINTER_OFFSET_IN_CLASS_64_BIT_WITH_COMPRESSED_REF_FOR_JAVA_1_7 = 84;
    public static final int CLASS_DEF_POINTER_OFFSET_IN_CLASS_64_BIT_WITHOUT_COMPRESSED_REF_FOR_JAVA_1_7 = 160;
    
    public static final int CLASS_DEF_POINTER_OFFSET_IN_CLASS_32_BIT_FOR_JAVA_1_8 = 64; 
    public static final int CLASS_DEF_POINTER_OFFSET_IN_CLASS_64_BIT_WITH_COMPRESSED_REF_FOR_JAVA_1_8 = 72;
    public static final int CLASS_DEF_POINTER_OFFSET_IN_CLASS_64_BIT_WITHOUT_COMPRESSED_REF_FOR_JAVA_1_8 = 128;
    
    public static final int SIZE_FIELD_OFFSET_IN_CLASS_32_BIT = 12;
    public static final int SIZE_FIELD_OFFSET_IN_CLASS_64_BIT = 24;
    
    public static final int BOOLEAN_SIZE = 1;
    public static final int BYTE_SIZE = Byte.SIZE / BYTE;
    public static final int CHAR_SIZE = Character.SIZE / BYTE;
    public static final int SHORT_SIZE = Short.SIZE / BYTE;
    public static final int INT_SIZE = Integer.SIZE / BYTE;
    public static final int FLOAT_SIZE = Float.SIZE / BYTE;
    public static final int LONG_SIZE = Long.SIZE / BYTE;
    public static final int DOUBLE_SIZE = Double.SIZE / BYTE;
    
    private static final Logger logger = Logger.getLogger(JvmUtil.class.getName());
    
    private static VMOptions options;
    private static Unsafe unsafe;
    private static Object[] objArray;
    private static int addressSize;
    private static int headerSize;
    private static int arrayHeaderSize;
    private static long baseOffset;
    private static int indexScale;
	private static int classDefPointerOffsetInObject;
    private static int classDefPointerOffsetInClass;
    private static int sizeFieldOffsetOffsetInClass;
    
    private static JvmAwareUtil jvmAwareUtil;
    
    static {
    	init();
    }
	
	private static void init() {
        if (isJavaVersionSupported() == false) {
        	throw new AssertionError("Java version is not supported: " + JAVA_SPEC_VERSION); 
        }
		
		try {
            Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            unsafe = (Unsafe) unsafeField.get(null);
        } catch (NoSuchFieldException e) {
        	throw new RuntimeException("Unable to get unsafe", e);
        } catch (IllegalAccessException e) {
        	throw new RuntimeException("Unable to get unsafe", e);
        }

        objArray = new Object[1];
        
        int headerSize;
        try {
            long off1 = unsafe.objectFieldOffset(HeaderClass.class.getField("b1"));
            headerSize = (int) off1;
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("Unable to calculate header size", e);
        }

        JvmUtil.addressSize = unsafe.addressSize();
        JvmUtil.baseOffset = unsafe.arrayBaseOffset(Object[].class);
        JvmUtil.indexScale = unsafe.arrayIndexScale(Object[].class);
        JvmUtil.headerSize = headerSize;
        JvmUtil.arrayHeaderSize = headerSize + indexScale;
        JvmUtil.options = findOptions();

        switch (addressSize) {
            case SIZE_32_BIT:
            	JvmUtil.classDefPointerOffsetInObject = CLASS_DEF_POINTER_OFFSET_IN_OBJECT_FOR_32_BIT;
            	if (isJava_1_6()) {
            		JvmUtil.classDefPointerOffsetInClass = CLASS_DEF_POINTER_OFFSET_IN_CLASS_32_BIT_FOR_JAVA_1_6;
            	} else if (isJava_1_7()) {
            		JvmUtil.classDefPointerOffsetInClass = CLASS_DEF_POINTER_OFFSET_IN_CLASS_32_BIT_FOR_JAVA_1_7;
            	} else if (isJava_1_8()) {
                    JvmUtil.classDefPointerOffsetInClass = CLASS_DEF_POINTER_OFFSET_IN_CLASS_32_BIT_FOR_JAVA_1_8;
                }
            	JvmUtil.sizeFieldOffsetOffsetInClass = SIZE_FIELD_OFFSET_IN_CLASS_32_BIT;
            	jvmAwareUtil = new Address32BitJvmUtil();
                break;
            case SIZE_64_BIT:
            	JvmUtil.classDefPointerOffsetInObject = CLASS_DEF_POINTER_OFFSET_IN_OBJECT_FOR_64_BIT;
            	if (isJava_1_6()) {
            		if (options.compressedRef) {
            			JvmUtil.classDefPointerOffsetInClass = 
            					CLASS_DEF_POINTER_OFFSET_IN_CLASS_64_BIT_WITH_COMPRESSED_REF_FOR_JAVA_1_6;
            			jvmAwareUtil = new Address64BitWithCompressedOopsJvmUtil();
            		} else {
            			JvmUtil.classDefPointerOffsetInClass = 
            					CLASS_DEF_POINTER_OFFSET_IN_CLASS_64_BIT_WITHOUT_COMPRESSED_REF_FOR_JAVA_1_6;
            			jvmAwareUtil = new Address64BitWithoutCompressedOopsJvmUtil();
            		}
            	} else if (isJava_1_7()) {
            		if (options.compressedRef) {
            			JvmUtil.classDefPointerOffsetInClass = 
            					CLASS_DEF_POINTER_OFFSET_IN_CLASS_64_BIT_WITH_COMPRESSED_REF_FOR_JAVA_1_7;
            			jvmAwareUtil = new Address64BitWithCompressedOopsJvmUtil();
            		} else {
            			JvmUtil.classDefPointerOffsetInClass = 
            					CLASS_DEF_POINTER_OFFSET_IN_CLASS_64_BIT_WITHOUT_COMPRESSED_REF_FOR_JAVA_1_7;
            			jvmAwareUtil = new Address64BitWithoutCompressedOopsJvmUtil();
            		}
            	} else if (isJava_1_8()) {
                    if (options.compressedRef) {
                        JvmUtil.classDefPointerOffsetInClass = 
                                CLASS_DEF_POINTER_OFFSET_IN_CLASS_64_BIT_WITH_COMPRESSED_REF_FOR_JAVA_1_8;
                        jvmAwareUtil = new Address64BitWithCompressedOopsJvmUtil();
                    } else {
                        JvmUtil.classDefPointerOffsetInClass = 
                                CLASS_DEF_POINTER_OFFSET_IN_CLASS_64_BIT_WITHOUT_COMPRESSED_REF_FOR_JAVA_1_8;
                        jvmAwareUtil = new Address64BitWithoutCompressedOopsJvmUtil();
                    }
                } else {
            		throw new AssertionError("Java version is not supported: " + JAVA_SPEC_VERSION); 
            	}
            	JvmUtil.sizeFieldOffsetOffsetInClass = SIZE_FIELD_OFFSET_IN_CLASS_64_BIT;
                break;
            default:
            	throw new AssertionError("Unsupported address size: " + addressSize); 
        }        
    }
	
	public static Unsafe getUnsafe() {
		return unsafe;
	}
	
	private static JavaVersionInfo findJavaVersionInfo() {
		if (JAVA_SPEC_VERSION.equals(JAVA_1_6)) {
			return JavaVersionInfo.JAVA_VERSION_1_6;
		} else if (JAVA_SPEC_VERSION.equals(JAVA_1_7)) {
			return JavaVersionInfo.JAVA_VERSION_1_7;
		} else if (JAVA_SPEC_VERSION.equals(JAVA_1_8)) {
            return JavaVersionInfo.JAVA_VERSION_1_8;
        } else {
			throw new AssertionError("Java version is not supported: " + JAVA_SPEC_VERSION); 
		}
	}

	public static boolean isJava_1_6() {
		return JAVA_VERSION_INFO == JavaVersionInfo.JAVA_VERSION_1_6;
	}

	public static boolean isJava_1_7() {
		return JAVA_VERSION_INFO == JavaVersionInfo.JAVA_VERSION_1_7;
	}
	
	public static boolean isJava_1_8() {
        return JAVA_VERSION_INFO == JavaVersionInfo.JAVA_VERSION_1_8;
    }
	
	public static boolean isJavaVersionSupported() {
		return isJava_1_6() || isJava_1_7() || isJava_1_8();
	}
	
	public static VMOptions getOptions() {
		return options;
	}
	
	public static int getAddressSize() {
		return addressSize;
	}
	
	public static boolean isAddressSizeSupported() {
		return addressSize == SIZE_32_BIT || addressSize == SIZE_64_BIT;
	}
	
	public static int getHeaderSize() {
		return headerSize;
	}
	
	public static int getArrayHeaderSize() {
		return arrayHeaderSize;
	}
	
	public static long getBaseOffset() {
		return baseOffset;
	}
	
	public static int getIndexScale() {
		return indexScale;
	}

	public static int getClassDefPointerOffsetInClass() {
		return classDefPointerOffsetInClass;
	}
	
	public static int getClassDefPointerOffsetInObject() {
		return classDefPointerOffsetInObject;
	}
	
	public static int getSizeFieldOffsetOffsetInClass() {
		return sizeFieldOffsetOffsetInClass;
	}
	
	public static boolean isCompressedRef() {
		return options.compressedRef;
	}
	
	public static int getReferenceSize() {
		return options.referenceSize;
	}
	
	public static int getObjectAlignment() {
		return options.objectAlignment;
	}
	
	public static int getCompressedReferenceShift() {
		return options.compressRefShift;
	}
	
	public static String getVmName() {
		return options.name;
	}
	
    private static long normalize(int value) {
        if (value >= 0) {
            return value;
        }    
        else {
            return (~0L >>> 32) & value;
        }    
    }

    private interface JvmAwareUtil {
    	
    	long addressOf(Object obj);
    	long addressOfClass(Object o);
    	long addressOfClassInternal(Class<?> clazz);
    	
    }

    private static class Address32BitJvmUtil implements JvmAwareUtil {

		@Override
		public long addressOf(Object obj) {
			if (obj == null) {
	            return 0;
	        }
	        objArray[0] = obj;
	        return unsafe.getInt(objArray, baseOffset);
		}

		@SuppressWarnings("deprecation")
		@Override
		public long addressOfClass(Object o) {
			return normalize(unsafe.getInt(o, classDefPointerOffsetInObject));
		}

		@Override
		public long addressOfClassInternal(Class<?> clazz) {
			long addressOfClass = addressOf(clazz);
	    	return normalize(unsafe.getInt(addressOfClass + classDefPointerOffsetInClass));
		}

    }

    private static class Address64BitWithCompressedOopsJvmUtil implements JvmAwareUtil {

		@Override
		public long addressOf(Object obj) {
			if (obj == null) {
	            return 0;
	        }
	        objArray[0] = obj;
	        return JvmUtil.toNativeAddress(normalize(unsafe.getInt(objArray, baseOffset)));
		}

		@SuppressWarnings("deprecation")
		@Override
		public long addressOfClass(Object o) {
			return JvmUtil.toNativeAddress(normalize(unsafe.getInt(o, classDefPointerOffsetInObject)));
		}

		@Override
		public long addressOfClassInternal(Class<?> clazz) {
			long addressOfClass = addressOf(clazz);
			return JvmUtil.toNativeAddress(normalize(unsafe.getInt(addressOfClass + classDefPointerOffsetInClass)));
		}

    }
    
    private static class Address64BitWithoutCompressedOopsJvmUtil implements JvmAwareUtil {

		@Override
		public long addressOf(Object obj) {
			if (obj == null) {
	            return 0;
	        }
	        objArray[0] = obj;
	        return unsafe.getLong(objArray, baseOffset);
		}

		@SuppressWarnings("deprecation")
		@Override
		public long addressOfClass(Object o) {
			return unsafe.getLong(o, classDefPointerOffsetInObject); 
		}

		@Override
		public long addressOfClassInternal(Class<?> clazz) {
			long addressOfClass = addressOf(clazz);
			return unsafe.getLong(addressOfClass + classDefPointerOffsetInClass); 
		}

    }
    
    public synchronized static long addressOf(Object obj) {
    	return jvmAwareUtil.addressOf(obj);
    }

	public synchronized static long addressOfClass(Object o) {
    	return jvmAwareUtil.addressOfClass(o);
    }

    public synchronized static long addressOfClass(Class<?> clazz) {
    	return jvmAwareUtil.addressOfClassInternal(clazz);
    }

    public static long toNativeAddress(long address) {
    	return options.toNativeAddress(address);
    }
    
    public static long toJvmAddress(long address) {
    	return options.toJvmAddress(address);
    }

	public static String getProcessId() throws Exception {
        RuntimeMXBean mxbean = ManagementFactory.getRuntimeMXBean();
        Field jvmField = mxbean.getClass().getDeclaredField("jvm");

        jvmField.setAccessible(true);
        VMManagement management = (VMManagement) jvmField.get(mxbean);
        Method method = management.getClass().getDeclaredMethod("getProcessId");
        method.setAccessible(true);
        Integer processId = (Integer) method.invoke(management);

        return processId.toString();
    }

    public static void info() {
        System.out.println("JVM Name                   : " + JVM_NAME);
        System.out.println("JVM Version                : " + JVM_VERSION);
        System.out.println("JVM Vendor                 : " + JVM_VENDOR);
        System.out.println("Java Version               : " + JAVA_VERSION);
        System.out.println("Java Specification Version : " + JAVA_SPEC_VERSION);
        System.out.println("Java Runtime Version       : " + JAVA_RUNTIME_VERSION);
        System.out.println("Java Vendor                : " + JAVA_VENDOR);
        System.out.println("OS Architecture            : " + OS_ARCH);
        System.out.println("OS Name                    : " + OS_NAME);
        System.out.println("OS Version                 : " + OS_VERSION);
        System.out.println("Word Size                  : " + WORD + " byte");
        
        System.out.println("Running " + (addressSize * BYTE) + "-bit " + options.name + " VM.");
        if (options.compressedRef) {
        	System.out.println("Using compressed references with " + options.compressRefShift + "-bit shift.");
        }
        System.out.println("Objects are " + options.objectAlignment + " bytes aligned.");
        System.out.println();
    }

    private static VMOptions findOptions() {
        // Try Hotspot
        VMOptions hsOpts = getHotspotSpecifics();
        if (hsOpts != null) {
        	return hsOpts;
        }

        // Try JRockit
        VMOptions jrOpts = getJRockitSpecifics();
        if (jrOpts != null) {
        	return jrOpts;
        }
        
        /*
         * When running with CompressedOops on 64-bit platform, the address size
         * reported by Unsafe is still 8, while the real reference fields are 4 bytes long.
         * Try to guess the reference field size with this naive trick.
         */
        int oopSize;
        try {
            long off1 = unsafe.objectFieldOffset(CompressedOopsClass.class.getField("obj1"));
            long off2 = unsafe.objectFieldOffset(CompressedOopsClass.class.getField("obj2"));
            oopSize = (int) Math.abs(off2 - off1);
        } 
        catch (NoSuchFieldException e) {
            oopSize = -1;
        }

        if (oopSize != unsafe.addressSize()) {
        	switch (oopSize) {
	            case ADDRESSING_8_BYTE:
	            	return new VMOptions("Auto-detected", ADDRESS_SHIFT_SIZE_FOR_BETWEEN_32GB_AND_64_GB);
	            case ADDRESSING_16_BYTE:
	            	return new VMOptions("Auto-detected", ADDRESS_SHIFT_SIZE_FOR_BIGGER_THAN_64_GB);
	            default:
	            	throw new AssertionError("Unsupported address size for compressed reference shifting: " + oopSize); 
        	}    	
        }
        else {
            return new VMOptions("Auto-detected");
        }
    }
    
    private static VMOptions getHotspotSpecifics() {
        String name = System.getProperty("java.vm.name");
        if (!name.contains("HotSpot") && !name.contains("OpenJDK")) {
            return null;
        }

        try {
            MBeanServer server = ManagementFactory.getPlatformMBeanServer();

            try {
                ObjectName mbean = new ObjectName("com.sun.management:type=HotSpotDiagnostic");
                
                CompositeDataSupport compressedOopsValue = 
                		(CompositeDataSupport) server.invoke(mbean, "getVMOption", 
                				new Object[]{"UseCompressedOops"}, new String[]{"java.lang.String"});
                boolean compressedOops = Boolean.valueOf(compressedOopsValue.get("value").toString());
                
                boolean compressedKlass = false; 
                try {
                    CompositeDataSupport compressedKlassValue = 
                        (CompositeDataSupport) server.invoke(mbean, "getVMOption", 
                                new Object[]{"UseCompressedClassPointers"}, new String[]{"java.lang.String"});
                    compressedKlass = Boolean.valueOf(compressedKlassValue.get("value").toString());
                } catch (RuntimeMBeanException e) {
                }

                if (compressedOops) {
                    try {
                        JVM jvm = new JVM();
                        Type universe = jvm.type("Universe");
                        int compressedRefShift = jvm.getInt(universe.global("_narrow_oop._shift"));
                        int compressedKlassShift = -1;
                        try {
                            compressedKlassShift = jvm.getInt(universe.global("_narrow_klass._shift"));
                        } catch (NoSuchElementException e) {
                        }
                        if (compressedKlassShift == -1) {
                            return new VMOptions("HotSpot", compressedRefShift);
                        } else {
                            return new VMOptions("HotSpot", true, compressedRefShift, compressedKlass, compressedKlassShift);
                        }
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                    
                    // If compressed oops are enabled, then this option is also accessible
                    CompositeDataSupport alignmentValue = 
                            (CompositeDataSupport) server.invoke(mbean, "getVMOption", 
                                    new Object[]{"ObjectAlignmentInBytes"}, new String[]{"java.lang.String"});
                    
                    int align = Integer.valueOf(alignmentValue.get("value").toString());
                    return new VMOptions("HotSpot", log2p(align));
                } 
                else {
                    return new VMOptions("HotSpot");
                }

            } 
            catch (RuntimeMBeanException iae) {
                return new VMOptions("HotSpot");
            }
        } 
        catch (Exception e) {
        	logger.log(Level.SEVERE, "Failed to read HotSpot-specific configuration properly", e);
            return null;
        } 
    }

    private static VMOptions getJRockitSpecifics() {
        String name = System.getProperty("java.vm.name");
        if (!name.contains("JRockit")) {
            return null;
        }

        try {
            MBeanServer server = ManagementFactory.getPlatformMBeanServer();
            String str = (String) server.invoke(new ObjectName("oracle.jrockit.management:type=DiagnosticCommand"), "execute", new Object[]{"print_vm_state"}, new String[]{"java.lang.String"});
            String[] split = str.split("\n");
            for (String s : split) {
                if (s.contains("CompRefs")) {
                    Pattern pattern = Pattern.compile("(.*?)References are compressed, with heap base (.*?) and shift (.*?)\\.");
                    Matcher matcher = pattern.matcher(s);
                    if (matcher.matches()) {
                        return new VMOptions("JRockit", Integer.valueOf(matcher.group(3)));
                    } 
                    else {
                        return new VMOptions("JRockit");
                    }
                }
            }
            return null;
        } 
        catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to read JRockit-specific configuration properly", e);
            return null;
        }
    }
    
    @SuppressWarnings("unused")
	private static int align(int addr) {
        int align = options.objectAlignment;
        if ((addr % align) == 0) {
            return addr;
        } 
        else {
            return ((addr / align) + 1) * align;
        }
    }

    private static int log2p(int x) {
        int r = 0;
        while ((x >>= 1) != 0) {
            r++;
        }    
        return r;
    }
    
    private static int guessAlignment(int oopSize) {
        final int COUNT = 1000 * 1000;
        Object[] array = new Object[COUNT];
        long[] offsets = new long[COUNT];

        for (int c = 0; c < COUNT - 3; c += 3) {
            array[c + 0] = new MyObject1();
            array[c + 1] = new MyObject2();
            array[c + 1] = new MyObject3();
        }

        for (int c = 0; c < COUNT; c++) {
            offsets[c] = addressOfObject(array[c], oopSize);
        }

        Arrays.sort(offsets);

        List<Integer> sizes = new ArrayList<Integer>(COUNT);
        for (int c = 1; c < COUNT; c++) {
            sizes.add((int) (offsets[c] - offsets[c - 1]));
        }

        int min = -1;
        for (int s : sizes) {
            if (s <= 0) {
            	continue;
            }
            if (min == -1) {
                min = s;
            } 
            else {
                min = gcd(min, s);
            }
        }

        return min;
    }
    
    @SuppressWarnings("unused")
	private static long addressOfObject(Object o) {
    	return addressOfObject(options.referenceSize);
    }
    
    private static long addressOfObject(Object o, int oopSize) {
        Object[] array = new Object[]{o};

        long baseOffset = unsafe.arrayBaseOffset(Object[].class);
        long objectAddress;
        switch (oopSize) {
            case SIZE_32_BIT:
                objectAddress = unsafe.getInt(array, baseOffset);
                break;
            case SIZE_64_BIT:
                objectAddress = unsafe.getLong(array, baseOffset);
                break;
            default:
            	throw new AssertionError("Unsupported address size: " + oopSize); 
        }

        return objectAddress;
    }
    
    private static int gcd(int a, int b) {
        while (b > 0) {
            int temp = b;
            b = a % b;
            a = temp;
        }
        return a;
    }

    public static class VMOptions {
    	
        public final String name;
        public final boolean compressedRef;
        public final int compressRefShift;
        public final boolean compressedKlass;
        public final int compressKlassShift;
        public final int objectAlignment;
        public final int referenceSize;

        public VMOptions(String name) {
            this.name = name;
            this.referenceSize = unsafe.addressSize();
            this.objectAlignment = guessAlignment(this.referenceSize);
            this.compressedRef = false;
            this.compressRefShift = 0;
            this.compressedKlass = false;
            this.compressKlassShift = 0;
        }

        public VMOptions(String name, 
                         boolean compressedRef, int compressRefShift,
                         boolean compressedKlass, int compressKlassShift) {
            this.name = name;
            this.referenceSize = SIZE_32_BIT;
            this.objectAlignment = guessAlignment(this.referenceSize) << compressRefShift;
            this.compressedRef = compressedRef;
            this.compressRefShift = compressRefShift;
            this.compressedKlass = compressedKlass;
            this.compressKlassShift = compressKlassShift;
        }
        
        public VMOptions(String name, int shift) {
            this.name = name;
            this.referenceSize = SIZE_32_BIT;
            this.objectAlignment = guessAlignment(this.referenceSize) << shift;
            this.compressedRef = true;
            this.compressRefShift = shift;
            this.compressedKlass = true;
            this.compressKlassShift = shift;
        }


        public long toNativeAddress(long address) {
            if (compressedRef) {
                return address << compressRefShift;
            } 
            else {
                return address;
            }
        }
        
        public long toJvmAddress(long address) {
            if (compressedRef) {
                return address >> compressRefShift;
            } 
            else {
                return address;
            }
        }

    }
    
    @SuppressWarnings("unused")
    private static class CompressedOopsClass {
        
		public Object obj1;
        public Object obj2;
        
    }

    @SuppressWarnings("unused")
    private static class HeaderClass {
    	
        public boolean b1;
        
    }
    
    private static class MyObject1 {

    }

    @SuppressWarnings("unused")
    private static class MyObject2 {
    	
        private boolean b1;
        
    }

    @SuppressWarnings("unused")
    private static class MyObject3 {
    	
        private int i1;
        
    }
	
	public enum JavaVersionInfo {
		
		JAVA_VERSION_1_6(JAVA_1_6),
		JAVA_VERSION_1_7(JAVA_1_7),
		JAVA_VERSION_1_8(JAVA_1_8);
		
		String name;
		
		JavaVersionInfo(String name) {
			this.name = name;
		}
		
		public String getName() {
			return name;
		}
		
	}
	
	public static void dump(long address, long size) {
        dump(System.out, address, size);
    }
    
    public static void dump(PrintStream ps, long address, long size) {
        for (int i = 0; i < size; i++) {
            if (i % 16 == 0) {
                ps.print(String.format("[0x%04x]: ", i));
            }
            ps.print(String.format("%02x ", unsafe.getByte(address + i)));
            if ((i + 1) % 16 == 0) {
                ps.println();
            }
        }   
        ps.println();
    }
    
    public static void dump(Object obj, long size) {
        dump(System.out, obj, size);
    }
    
    public static void dump(PrintStream ps, Object obj, long size) {
        for (int i = 0; i < size; i++) {
            if (i % 16 == 0) {
                ps.print(String.format("[0x%04x]: ", i));
            }
            ps.print(String.format("%02x ", unsafe.getByte(obj, (long)i)));
            if ((i + 1) % 16 == 0) {
                ps.println();
            }
        }   
        ps.println();
    }

}
