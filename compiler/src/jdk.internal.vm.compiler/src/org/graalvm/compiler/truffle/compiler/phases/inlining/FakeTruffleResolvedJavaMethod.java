/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.compiler.phases.inlining;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import org.graalvm.compiler.truffle.compiler.KnownTruffleTypes;

import com.oracle.truffle.compiler.AlternativeBytecodeParameterProxy;
import com.oracle.truffle.compiler.AlternativeBytecodeProxy;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.ExceptionHandler;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.LineNumberTable;
import jdk.vm.ci.meta.LocalVariableTable;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;
import jdk.vm.ci.meta.SpeculationLog;

/**
 * TODO(blaumeise20): documentation
 */
public class FakeTruffleResolvedJavaMethod implements ResolvedJavaMethod {

    private final AlternativeBytecodeProxy bytecode;
    private final AlternativeBytecodeConstantPool constantPool;
    private final KnownTruffleTypes types;

    public FakeTruffleResolvedJavaMethod(AlternativeBytecodeProxy bytecode, KnownTruffleTypes types, MetaAccessProvider metaAccess) {
        this.bytecode = bytecode;
        this.constantPool = new AlternativeBytecodeConstantPool(bytecode.getConstantPool(), types, metaAccess);
        this.types = types;
    }

    @Override
    public String getName() {
        return bytecode.getMethodName();
    }

    @Override
    public Signature getSignature() {
        AlternativeBytecodeParameterProxy[] parameters = bytecode.getParameters();
        return new Signature() {

            @Override
            public int getParameterCount(boolean receiver) {
                int addForReceiver = receiver ? 1 : 0;
                return parameters.length + addForReceiver;
            }

            @Override
            public JavaType getParameterType(int index, ResolvedJavaType accessingClass) {
                System.out.println("stack contains dump: " + java.util.Arrays.asList((Thread.currentThread().getStackTrace())).stream().anyMatch(ste -> ste.getClassName().contains("Dump")));
                return types.java_lang_Object;
            }

            @Override
            public JavaKind getParameterKind(int index) {
                return parameters[index].getJavaKind();
            }

            @Override
            public JavaType getReturnType(ResolvedJavaType accessingClass) {
                System.out.println("stack contains dump: " + java.util.Arrays.asList((Thread.currentThread().getStackTrace())).stream().anyMatch(ste -> ste.getClassName().contains("Dump")));
                return types.java_lang_Object;
            }

            @Override
            public JavaKind getReturnKind() {
                return bytecode.getReturnKind();
            }

        };
    }

    @Override
    public int getModifiers() {
        return bytecode.getModifiers();
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getAnnotation'");
    }

    @Override
    public Annotation[] getAnnotations() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getAnnotations'");
    }

    @Override
    public Annotation[] getDeclaredAnnotations() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getDeclaredAnnotations'");
    }

    @Override
    public byte[] getCode() {
        return bytecode.getCode();
    }

    @Override
    public int getCodeSize() {
        return getCode().length;
    }

    @Override
    public ResolvedJavaType getDeclaringClass() {
        return types.OptimizedCallTarget;
    }

    @Override
    public int getMaxLocals() {
        return bytecode.getMaxLocals();
    }

    @Override
    public int getMaxStackSize() {
        return bytecode.getMaxStack();
    }

    @Override
    public boolean isSynthetic() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'isSynthetic'");
    }

    @Override
    public boolean isVarArgs() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'isVarArgs'");
    }

    @Override
    public boolean isBridge() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'isBridge'");
    }

    @Override
    public boolean isDefault() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'isDefault'");
    }

    @Override
    public boolean isClassInitializer() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'isClassInitializer'");
    }

    @Override
    public boolean isConstructor() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'isConstructor'");
    }

    @Override
    public boolean canBeStaticallyBound() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'canBeStaticallyBound'");
    }

    @Override
    public ExceptionHandler[] getExceptionHandlers() {
        return new ExceptionHandler[] {}; // TODO
    }

    @Override
    public StackTraceElement asStackTraceElement(int bci) {
        return new StackTraceElement(getDeclaringClass().getName(), getName(), "somewhere", 123); // TODO
    }

    @Override
    public ProfilingInfo getProfilingInfo(boolean includeNormal, boolean includeOSR) {
        return null; // TODO
    }

    @Override
    public void reprofile() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'reprofile'");
    }

    @Override
    public ConstantPool getConstantPool() {
        return constantPool;
    }

    @Override
    public Annotation[][] getParameterAnnotations() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getParameterAnnotations'");
    }

    @Override
    public Type[] getGenericParameterTypes() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getGenericParameterTypes'");
    }

    @Override
    public boolean canBeInlined() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'canBeInlined'");
    }

    @Override
    public boolean hasNeverInlineDirective() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'hasNeverInlineDirective'");
    }

    @Override
    public boolean shouldBeInlined() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'shouldBeInlined'");
    }

    @Override
    public LineNumberTable getLineNumberTable() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getLineNumberTable'");
    }

    @Override
    public LocalVariableTable getLocalVariableTable() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getLocalVariableTable'");
    }

    @Override
    public Constant getEncoding() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getEncoding'");
    }

    @Override
    public boolean isInVirtualMethodTable(ResolvedJavaType resolved) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'isInVirtualMethodTable'");
    }

    @Override
    public SpeculationLog getSpeculationLog() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getSpeculationLog'");
    }

}
