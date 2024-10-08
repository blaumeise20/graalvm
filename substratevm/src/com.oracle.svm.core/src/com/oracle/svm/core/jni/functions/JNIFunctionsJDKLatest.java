/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.svm.core.jni.functions;

import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CEntryPoint.Publish;

import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.jni.JNIObjectHandles;
import com.oracle.svm.core.jni.functions.JNIFunctions.Support.JNIEnvEnterPrologue;
import com.oracle.svm.core.jni.functions.JNIFunctions.Support.JNIExceptionHandlerReturnMinusOne;
import com.oracle.svm.core.jni.functions.JNIFunctions.Support.ReturnMinusOneLong;
import com.oracle.svm.core.jni.headers.JNIEnvironment;
import com.oracle.svm.core.jni.headers.JNIObjectHandle;
import com.oracle.svm.core.util.Utf8;

/**
 * Implementations of the functions defined by the Java Native Interface only present in the latest
 * supported JDK.
 * 
 * @see JNIFunctions
 */
@SuppressWarnings("unused")
public final class JNIFunctionsJDKLatest {

    // Checkstyle: stop

    /*
     * jlong GetStringUTFLengthAsLong(JNIEnv *env, jstring string);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnMinusOne.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnMinusOneLong.class)
    static long GetStringUTFLengthAsLong(JNIEnvironment env, JNIObjectHandle hstr) {
        String str = JNIObjectHandles.getObject(hstr);
        return Utf8.utf8LengthAsLong(str);
    }
}
