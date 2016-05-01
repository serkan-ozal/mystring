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

package tr.com.serkanozal.mysafe;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import tr.com.serkanozal.mystring.MyStringFactory;
import tr.com.serkanozal.mystring.MyStringService;
import tr.com.serkanozal.mystring.api.MyString;

public class MyStringDemo {

    private static final int STR_LENGTH = 1000000;
    private static final int STR_COUNT = 100;
    
    public static void main(String[] args) {
        runDemo();
    }
    
    private static void runDemo() {
        /*
         ========================== NOTE ==========================
         | There is a problem on JDK-8 with C1 level 3 compiler   |
         | which sometimes causes "ClassCastException" while      |   
         | casting "MyStringTemplate" to "String".                 |
         |                                                        |
         | As a workaround, you may try with :                    |
         |      - -XX:CompilationPolicyChoice=2                   |
         |      - -XX:CompileCommand=exclude,java/lang/String*.*  |
         ==========================================================
         */
        
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        
        MyStringFactory offHeapMyStrFactory = MyStringService.getOffHeapMyStringFactory();
        
        List<String> strList = new ArrayList<String>(STR_COUNT);
        Map<Integer, String> strMap = new HashMap<Integer, String>();
        
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < STR_LENGTH; i++) {
            sb.append((char) ('A' + (i % 32)));
        }
        String bigStr = sb.toString();

        System.out.println("[BEFORE STRING CREATION] Heap Memory Usage: " + memory.getHeapMemoryUsage().getUsed());

        for (int i = 0; i < STR_COUNT; i++) {
            //String str = offHeapMyStrFactory.create(new char[1024 * 1024 * 5]);
            //String str = new String(new char[1024 * 1024 * 5]);

            String str = offHeapMyStrFactory.create(new String(bigStr + i));
            //String str = strList.add(new String(bigStr + i));
            
            strList.add(str);
            strMap.put(i, str);
        }  

        for (int i = 0; i < 3; i++) {
            System.gc();
        }

        System.out.println("[AFTER  STRING CREATION] Heap Memory Usage: " + memory.getHeapMemoryUsage().getUsed());

        /*
        long totalStringLength = 0L;
        long totalStorageSize = 0L;
        for (Map.Entry<Integer, String> entry : strMap.entrySet()) {
            String str = entry.getValue();
            MyString myStr = MyStringService.getMyStringOrNull(str);
            totalStringLength += myStr.length();
            totalStorageSize += myStr.getStorageSize();
        }
        
        System.out.println("Total string count  : " + STR_COUNT);
        System.out.println("Total string length : " + totalStringLength);
        System.out.println("Total storage size  : " + totalStorageSize);
        */
        
        for (String str : strList) {
            MyString myStr = MyStringService.getMyStringOrNull(str);
            if (myStr != null) {
                myStr.destroy();
            }
        }  
    }

}
