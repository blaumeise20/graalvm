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
package com.oracle.truffle.compiler;

import jdk.vm.ci.meta.JavaKind;

/**
 * TODO(blaumeise20): documentation
 */
public interface AlternativeBytecodeParameterProxy {

    String getType();

    default JavaKind getJavaKind() {
        JavaKind kind;
        switch (getType().charAt(0)) {
            case 'Z': kind = JavaKind.Boolean; break;
            case 'B': kind = JavaKind.Byte;    break;
            case 'S': kind = JavaKind.Short;   break;
            case 'C': kind = JavaKind.Char;    break;
            case 'I': kind = JavaKind.Int;     break;
            case 'F': kind = JavaKind.Float;   break;
            case 'J': kind = JavaKind.Long;    break;
            case 'D': kind = JavaKind.Double;  break;
            default : kind = JavaKind.Object;  break;
        }
        return kind;
    }

}
