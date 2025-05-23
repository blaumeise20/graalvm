/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.flow;

import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.typestate.TypeState;
import com.oracle.graal.pointsto.util.AnalysisError;

import jdk.vm.ci.code.BytecodePosition;

/**
 * A pre-saturated flow that will signal the saturation to any future uses. The flow will only
 * propagate the saturation when it is enabled by its predicate.
 */
public class PreSaturatedTypeFlow extends TypeFlow<BytecodePosition> {

    public PreSaturatedTypeFlow(BytecodePosition bytecodePosition) {
        super(bytecodePosition, null);
    }

    private PreSaturatedTypeFlow(MethodFlowsGraph methodFlows, PreSaturatedTypeFlow original) {
        super(original, methodFlows);
    }

    @Override
    public TypeFlow<BytecodePosition> copy(PointsToAnalysis bb, MethodFlowsGraph methodFlows) {
        return new PreSaturatedTypeFlow(methodFlows, this);
    }

    @Override
    protected void onFlowEnabled(PointsToAnalysis bb) {
        super.onFlowEnabled(bb);
        this.onSaturated(bb);
    }

    @Override
    public boolean addState(PointsToAnalysis bb, TypeState add) {
        throw AnalysisError.shouldNotReachHere("PreSaturated flows don't have a state.");
    }

    @Override
    public String toString() {
        return "PreSaturatedTypeFlow<" + getStateDescription() + ">";
    }
}
