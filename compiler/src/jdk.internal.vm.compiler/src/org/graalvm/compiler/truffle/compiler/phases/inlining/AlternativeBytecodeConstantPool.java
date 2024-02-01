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

import org.graalvm.compiler.truffle.compiler.KnownTruffleTypes;

import com.oracle.truffle.compiler.AlternativeBytecodeConstantPoolProxy;

import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaField;
import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.Signature;

/**
 * TODO(blaumeise20): documentation
 */
public class AlternativeBytecodeConstantPool implements ConstantPool {

    private final AlternativeBytecodeConstantPoolProxy constantPool;
    private final KnownTruffleTypes types;
    private final MetaAccessProvider metaAccess;

    AlternativeBytecodeConstantPool(AlternativeBytecodeConstantPoolProxy constantPool, KnownTruffleTypes types, MetaAccessProvider metaAccess) {
        this.constantPool = constantPool;
        this.types = types;
        this.metaAccess = metaAccess;
    }

    @Override
    public int length() {
        return constantPool.length();
    }

    @Override
    public void loadReferencedType(int cpi, int opcode) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'loadReferencedType'");
    }

    @Override
    public JavaType lookupReferencedType(int cpi, int opcode) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'lookupReferencedType'");
    }

    @Override
    public JavaField lookupField(int cpi, ResolvedJavaMethod method, int opcode) {
        return metaAccess.lookupJavaField(constantPool.lookupField(cpi));
    }

    @Override
    public JavaMethod lookupMethod(int cpi, int opcode, ResolvedJavaMethod caller) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'lookupMethod'");
    }

    @Override
    public JavaType lookupType(int cpi, int opcode) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'lookupType'");
    }

    @Override
    public String lookupUtf8(int cpi) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'lookupUtf8'");
    }

    @Override
    public Signature lookupSignature(int cpi) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'lookupSignature'");
    }

    @Override
    public Object lookupConstant(int cpi) {
        return constantPool.lookupConstant(cpi);
    }

    @Override
    public JavaConstant lookupAppendix(int cpi, int opcode) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'lookupAppendix'");
    }

}
