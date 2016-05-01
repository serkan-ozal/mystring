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

import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.CtMethod;
import sun.misc.Unsafe;
import tr.com.serkanozal.jillegal.agent.JillegalAgent;
import tr.com.serkanozal.mystring.api.MyString;
import tr.com.serkanozal.mystring.api.MyStringProcessor;
import tr.com.serkanozal.mystring.api.WhereAmI;
import tr.com.serkanozal.mystring.jvm.Field;
import tr.com.serkanozal.mystring.jvm.JVM;
import tr.com.serkanozal.mystring.jvm.Type;
import tr.com.serkanozal.mystring.offheap.OffHeapMyStringProcessor;
import tr.com.serkanozal.mystring.util.JvmUtil;

public class MyStringService {

    public static final int MODIFIER_FLAGS_OFFSET_IN_CLASS_DEFINITION_FOR_32_BIT_JVM = 80; 
    public static final int MODIFIER_FLAGS_OFFSET_IN_CLASS_DEFINITION_FOR_64_BIT_JVM = 152;
    
    public static final int ACCESS_FLAGS_OFFSET_IN_CLASS_DEFINITION_FOR_32_BIT_JVM = 84; 
    public static final int ACCESS_FLAGS_OFFSET_IN_CLASS_DEFINITION_FOR_64_BIT_JVM = 156;
    
    private static final Logger LOGGER = Logger.getLogger(MyStringService.class.getName());
    private static final boolean ACTIVE;
    private static final Map<String, MyStringFactory> MY_STR_FACTORY_MAP = 
            new HashMap<String, MyStringFactory>();
    private static final Unsafe UNSAFE;
    private static final Instrumentation INSTRUMENTATION;
    private static final ClassPool CLASS_POOL;
    private static final String OFFHEAP_MYSTRING_FACTORY_ID = "OffHeapMyStringProcessor";
    
    static {
        Instrumentation inst = null;
        ClassPool cp = null;
        Unsafe u = null;
        boolean initialized = false;
        
        try {
            JillegalAgent.init();

            inst = JillegalAgent.getInstrumentation();
            cp = ClassPool.getDefault();
            u = JvmUtil.getUnsafe();
            
            initialized = true;
        } catch (Throwable t) {
            LOGGER.log(Level.SEVERE, "Unable to initialize MyStringService!", t);
        }
        
        INSTRUMENTATION = inst;
        CLASS_POOL = cp;
        UNSAFE = u;
        
        boolean act = false;
        try {
            if (initialized) {
                if (JvmUtil.isJavaVersionSupported()) {
                    CtClass ctStringClass = instrumentStringClass();
    
                    defineMyStringTemplate(ctStringClass);
                
                    registerDefaultMyStringFactories();
                    
                    act = true;
                } else {
                    LOGGER.log(Level.WARNING, "Java version is not supported!" + 
                               " Only Java 6/7/8 supported but current platform is " + 
                               JvmUtil.JAVA_VERSION);
                }
            } else {
                LOGGER.log(Level.WARNING, 
                           "MyStringService couldn't be initialized. So MyString is not enabled.");
            }
        } catch (Throwable t) {
            LOGGER.log(Level.SEVERE, "Unable to perform required instrumentations for MyString!", t);
        }
        ACTIVE = act;
    }

    private static CtClass instrumentStringClass() {
        try {
            INSTRUMENTATION.appendToBootstrapClassLoaderSearch(new JarFile(WhereAmI.FROM));

            CtClass ctStringClass = CLASS_POOL.get(String.class.getName());
            for (CtMethod cm : ctStringClass.getDeclaredMethods()) {
                int modifiers = cm.getModifiers(); 
                CtClass[] paramTypes = cm.getParameterTypes();
                CtClass returnType = cm.getReturnType();
                if ("compareTo".equals(cm.getName()) && !paramTypes[0].equals(ctStringClass)) {
                    continue;
                }
                if (javassist.Modifier.isPublic(modifiers) 
                        && !javassist.Modifier.isStatic(modifiers) 
                        && !javassist.Modifier.isNative(modifiers)) {
                    StringBuilder params = new StringBuilder();
                    for (int i = 0; i < paramTypes.length; i++) {
                        if (i != 0) {
                            params.append(", ");
                        }
                        params.append("$" + (i + 1));
                    }
                    String delegatedCall =
                            "((tr.com.serkanozal.mystring.api.MyString) this)." +
                                    cm.getName() + "(" + params.toString() + ");";
                    if (returnType != null && returnType != CtClass.voidType) {
                        cm.insertBefore(
                            "{ " + 
                                "if (value == null) " + 
                                "{" + 
                                    "return " + delegatedCall +
                                "}" +    
                            "}");
                    } else {
                        cm.insertBefore(
                            "{ " + 
                                "if (value == null) " + 
                                "{" + 
                                    delegatedCall +
                                    "return;" +
                                "}" +
                            "}");
                    }
                }  
            }
            INSTRUMENTATION.redefineClasses(new ClassDefinition(String.class, ctStringClass.toBytecode()));

            makeStringClassNonFinal();
            
            return ctStringClass;
        } catch (Throwable t) {
            throw new RuntimeException("Unable to instrument 'java.lang.String'!", t);
        }
    }
    
    private static void makeStringClassNonFinal() {
        boolean done = false;
        try {
            JVM jvm = new JVM();
            
            Type sysDictionaryType = jvm.type("SystemDictionary");
            Field stringKlassField = sysDictionaryType.field("_well_known_klasses[SystemDictionary::String_klass_knum]");
            long stringKlassAddress = jvm.getAddress(stringKlassField.offset);

            Type klassType = jvm.type("Klass");
            Field modifierFlagsField = klassType.field("_modifier_flags");
            long modifierFlagsAddress = stringKlassAddress + modifierFlagsField.offset;
            Field accessFlagsField = klassType.field("_access_flags");
            long accessFlagsAddress = stringKlassAddress + accessFlagsField.offset;
            
            int modifierFlags = UNSAFE.getInt(modifierFlagsAddress);
            UNSAFE.putInt(modifierFlagsAddress, modifierFlags & 0xFFFFFFEF);
            
            int accessFlags = UNSAFE.getInt(accessFlagsAddress);
            UNSAFE.putInt(accessFlagsAddress, accessFlags & 0xFFFFFFEF);
            
            done = true;
        } catch (Throwable t) {
            LOGGER.log(Level.SEVERE, "Unable to modify String class by getting JVM internal offsets!", t);
        }
        if (!done) {
            LOGGER.log(Level.WARNING, "Predefined offsets will be used to modify String class ...");
            
            int modifierFlagsOffset;
            int accessFlagsOffset;
            
            int addressSize = JvmUtil.getAddressSize();
            if (addressSize == JvmUtil.SIZE_32_BIT) {
                modifierFlagsOffset = MODIFIER_FLAGS_OFFSET_IN_CLASS_DEFINITION_FOR_32_BIT_JVM;
                accessFlagsOffset = ACCESS_FLAGS_OFFSET_IN_CLASS_DEFINITION_FOR_32_BIT_JVM;
            } else if (addressSize == JvmUtil.SIZE_64_BIT) {
                modifierFlagsOffset = MODIFIER_FLAGS_OFFSET_IN_CLASS_DEFINITION_FOR_64_BIT_JVM;
                accessFlagsOffset = ACCESS_FLAGS_OFFSET_IN_CLASS_DEFINITION_FOR_64_BIT_JVM;
            } else {
                throw new IllegalStateException("Unsupported address size: " + addressSize);
            }
            
            long addressOfStrClass = JvmUtil.addressOfClass(String.class);
            
            int modifierFlags = UNSAFE.getInt(addressOfStrClass + modifierFlagsOffset);
            UNSAFE.putInt(addressOfStrClass + modifierFlagsOffset, modifierFlags & 0xFFFFFFEF);
            
            int accessFlags = UNSAFE.getInt(addressOfStrClass + accessFlagsOffset);
            UNSAFE.putInt(addressOfStrClass + accessFlagsOffset, accessFlags & 0xFFFFFFEF);
        }    
    }
    
    private static void defineMyStringTemplate(CtClass ctStringClass) {
        try {
            CtClass ctMyStringTemplateClass = CLASS_POOL.get("tr.com.serkanozal.mystring.MyStringTemplate");
            ctMyStringTemplateClass.setSuperclass(ctStringClass);
            byte[] byteCode = ctMyStringTemplateClass.toBytecode();
            UNSAFE.defineClass(ctMyStringTemplateClass.getName(), byteCode, 
                               0, byteCode.length, 
                               MyStringService.class.getClassLoader(), null);
        } catch (Throwable t) {
            throw new RuntimeException("Unable to define MyStringTemplate!", t);
        }
    }
    
    private static void registerDefaultMyStringFactories() {
        registerMyStringFactoryInternal(OFFHEAP_MYSTRING_FACTORY_ID, new OffHeapMyStringProcessor());
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static MyStringFactory createMyStringFactory(MyStringProcessor myStrProcessor) {
        try {
            CtClass ctMyStrFactoryClass = 
                    CLASS_POOL.makeClass("tr.com.serkanozal.mystring.DefaultMyStringFactory");
            ctMyStrFactoryClass.addInterface(CLASS_POOL.get(MyStringFactory.class.getName()));
            
            
            CtClass myStrProcessorClass = CLASS_POOL.get(MyStringProcessor.class.getName());
            
            ////////////////////////////////////////////////////////////////////////////////////

            CtField ctMyStrProcessorField = new CtField(myStrProcessorClass, "myStrProcessor", ctMyStrFactoryClass);
            ctMyStrFactoryClass.addField(ctMyStrProcessorField);
            
            ////////////////////////////////////////////////////////////////////////////////////
            
            CtConstructor ctMyStrFactoryConstructor = 
                    new CtConstructor(new CtClass[] { myStrProcessorClass }, ctMyStrFactoryClass);
            ctMyStrFactoryConstructor.setBody(
                    "{" + 
                        "this.myStrProcessor = $1;" +
                    "}");
            ctMyStrFactoryClass.addConstructor(ctMyStrFactoryConstructor);
            
            ////////////////////////////////////////////////////////////////////////////////////
            
            CtMethod ctCreateMethod1 = 
                    new CtMethod(CLASS_POOL.get(String.class.getName()), 
                                 "create", 
                                 new CtClass[] { CLASS_POOL.get(String.class.getName()) }, 
                                 ctMyStrFactoryClass);
            ctCreateMethod1.setBody(
                    "{" + 
                        "return new tr.com.serkanozal.mystring.MyStringTemplate(myStrProcessor, $1);" + 
                    "}");
            ctMyStrFactoryClass.addMethod(ctCreateMethod1);
            
            ////////////////////////////////////////////////////////////////////////////////////
            
            CtMethod ctCreateMethod2 = 
                    new CtMethod(CLASS_POOL.get(String.class.getName()), 
                                 "create", 
                                 new CtClass[] { CLASS_POOL.get(char[].class.getName()) }, 
                                 ctMyStrFactoryClass);
            ctCreateMethod2.setBody(
                    "{" + 
                        "return new tr.com.serkanozal.mystring.MyStringTemplate(myStrProcessor, $1);" + 
                    "}");
            ctMyStrFactoryClass.addMethod(ctCreateMethod2);
            
            ////////////////////////////////////////////////////////////////////////////////////
            
            CtMethod ctCreateMethod3 = 
                    new CtMethod(CLASS_POOL.get(String.class.getName()), 
                                 "create", 
                                 new CtClass[] { 
                                    CLASS_POOL.get(char[].class.getName()), 
                                    CLASS_POOL.get(int.class.getName()), 
                                    CLASS_POOL.get(int.class.getName()) }, 
                                 ctMyStrFactoryClass);
            ctCreateMethod3.setBody(
                    "{" + 
                        "return new tr.com.serkanozal.mystring.MyStringTemplate(myStrProcessor, $1, $2, $3);" + 
                    "}");
            ctMyStrFactoryClass.addMethod(ctCreateMethod3);
            
            ////////////////////////////////////////////////////////////////////////////////////
            
            CtMethod ctCreateMethod4 = 
                    new CtMethod(CLASS_POOL.get(String.class.getName()), 
                                 "create", 
                                 new CtClass[] { 
                                    CLASS_POOL.get(byte[].class.getName()), 
                                    CLASS_POOL.get(int.class.getName()) }, 
                                 ctMyStrFactoryClass);
            ctCreateMethod4.setBody(
                    "{" + 
                        "return new tr.com.serkanozal.mystring.MyStringTemplate(myStrProcessor, $1, $2);" + 
                    "}");
            ctMyStrFactoryClass.addMethod(ctCreateMethod4);
            
            ////////////////////////////////////////////////////////////////////////////////////
            
            CtMethod ctCreateMethod5 = 
                    new CtMethod(CLASS_POOL.get(String.class.getName()), 
                                 "create", 
                                 new CtClass[] { 
                                    CLASS_POOL.get(byte[].class.getName()), 
                                    CLASS_POOL.get(int.class.getName()),
                                    CLASS_POOL.get(int.class.getName()),
                                    CLASS_POOL.get(int.class.getName()) }, 
                                 ctMyStrFactoryClass);
            ctCreateMethod5.setBody(
                    "{" + 
                        "return new tr.com.serkanozal.mystring.MyStringTemplate(myStrProcessor, $1, $2, $3, $4);" + 
                    "}");
            ctMyStrFactoryClass.addMethod(ctCreateMethod5);

            ////////////////////////////////////////////////////////////////////////////////////
            
            byte[] byteCodeOfMyStrFactoryClass = ctMyStrFactoryClass.toBytecode();
            Class<? extends MyStringFactory> myStrFactoryClass = (Class<? extends MyStringFactory>)
                UNSAFE.defineClass(ctMyStrFactoryClass.getName(), 
                                   byteCodeOfMyStrFactoryClass, 
                                   0, byteCodeOfMyStrFactoryClass.length,
                                   MyStringService.class.getClassLoader(), null);
            Constructor myStrFactoryConstructor = myStrFactoryClass.getConstructor(MyStringProcessor.class);
            return (MyStringFactory) myStrFactoryConstructor.newInstance(myStrProcessor);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }
    
    private static void checkActive() {
        if (!ACTIVE) {
            throw new IllegalStateException("MyString support is not active!");
        }
    }
    
    public static MyStringFactory getMyStringFactory(String id) {
        checkActive();
        
        synchronized (MY_STR_FACTORY_MAP) {
            return MY_STR_FACTORY_MAP.get(id);
        }
    }
    
    @SuppressWarnings("rawtypes")
    public static void registerMyStringFactoryInternal(String id, MyStringProcessor myStrProcessor) {
        synchronized (MY_STR_FACTORY_MAP) {
            MyStringFactory myStrFactory = MY_STR_FACTORY_MAP.get(id);
            if (myStrFactory == null) {
                myStrFactory = createMyStringFactory(myStrProcessor);
                MY_STR_FACTORY_MAP.put(id, myStrFactory);
            } else {
                throw new IllegalArgumentException("There is already registered factory with id " + id);
            }
        }
    }
    
    @SuppressWarnings("rawtypes")
    public static void registerMyStringFactory(String id, MyStringProcessor myStrProcessor) {
        checkActive();
        
        registerMyStringFactoryInternal(id, myStrProcessor);
    }
    
    public static MyStringFactory deregisterMyStringFactory(String id) {
        checkActive();
        
        synchronized (MY_STR_FACTORY_MAP) {
            return MY_STR_FACTORY_MAP.get(id);
        }
    }
    
    public static MyStringFactory getOffHeapMyStringFactory() {
        checkActive();
        
        return getMyStringFactory(OFFHEAP_MYSTRING_FACTORY_ID);
    }
    
    public static MyString getMyStringOrNull(String str) {
        checkActive();
        
        Object strObj = str;
        if (strObj instanceof MyString) {
            return (MyString) strObj;
        } else {
            return null;
        }
    }
    
}
