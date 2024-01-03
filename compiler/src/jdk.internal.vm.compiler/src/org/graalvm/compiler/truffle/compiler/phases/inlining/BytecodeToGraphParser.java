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

import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.TimerKey;
import org.graalvm.compiler.java.BytecodeParser;
import org.graalvm.compiler.java.FrameStateBuilder;
import org.graalvm.compiler.java.GraphBuilderPhase;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.PiArrayNode;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.StartNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.extended.BoxNode;
import org.graalvm.compiler.nodes.extended.UnboxNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.java.LoadIndexedNode;
import org.graalvm.compiler.nodes.graphbuilderconf.IntrinsicContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.truffle.compiler.KnownTruffleTypes;
import org.graalvm.compiler.truffle.compiler.TruffleTierContext;

import com.oracle.truffle.compiler.AlternativeBytecodeProxy;
import com.oracle.truffle.compiler.AlternativeBytecodeParameterProxy;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * TODO(blaumeise20): document
 */
public class BytecodeToGraphParser {

    private static final TimerKey BuildGraphTimer = DebugContext.timer("BytecodeToGraph-GraphBuilding");

    private final StructuredGraph graph;
    private final DebugContext debug;
    private final AlternativeBytecodeProxy bytecode;
    private final TruffleTierContext context;
    private final KnownTruffleTypes types;
    private final FakeTruffleResolvedJavaMethod method;

    private BytecodeToGraphParser(StructuredGraph graph, AlternativeBytecodeProxy bytecode, TruffleTierContext context) {
        this.graph = graph;
        this.debug = graph.getDebug();
        this.bytecode = bytecode;
        this.context = context;
        this.types = context.types();
        this.method = new FakeTruffleResolvedJavaMethod(bytecode, context.types());
    }

    public static StructuredGraph parseFromBytecode(StructuredGraph graph, AlternativeBytecodeProxy bytecode, TruffleTierContext context) {
        return new BytecodeToGraphParser(graph, bytecode, context).build();
    }

    @SuppressWarnings("try")
    private StructuredGraph build() {
        // @formatter:off
        StructuredGraph newGraph = new StructuredGraph.Builder(graph.getOptions(), debug, AllowAssumptions.ifTrue(false)).
                profileProvider(null).
                trackNodeSourcePosition(false).
                method(types.OptimizedCallTarget_profiledPERoot).
                cancellable(graph.getCancellable()).
                build();
        // @formatter:on

        // TODO(blaumeise20): comment

        // TODO(blaumeise20): change providers
        CoreProviders providers = new Providers(context.getMetaAccess(), null, null, null, null, null, null, null, null, null, null, null, null);
        try (DebugContext.Scope scope = debug.scope("buildGraph", newGraph); DebugCloseable a = BuildGraphTimer.start(debug)) {
            Plugins plugins = new Plugins(new InvocationPlugins());
            GraphBuilderConfiguration config = GraphBuilderConfiguration.getDefault(plugins);
            GraphBuilderPhase.Instance graphBuilderPhaseInstance = new BytecodeGraphBuilderPhase(providers, config);
            graphBuilderPhaseInstance.apply(newGraph);
            // canonicalizer.apply(graphToEncode, providers);
            // if (postParsingPhase != null) {
            //     postParsingPhase.apply(graphToEncode, providers);
            // }
        } catch (Throwable ex) {
            throw debug.handle(ex);
        }

        return newGraph;
    }

    private class BytecodeGraphBuilderPhase extends GraphBuilderPhase.Instance {

        protected BytecodeGraphBuilderPhase(CoreProviders providers, GraphBuilderConfiguration graphBuilderConfig) {
            super(providers, graphBuilderConfig, OptimisticOptimizations.NONE, null);
        }

        @Override
        @SuppressWarnings("hiding")
        protected BytecodeParser createBytecodeParser(StructuredGraph graph, BytecodeParser parent, ResolvedJavaMethod method, int entryBCI, IntrinsicContext intrinsicContext) {
            if (method instanceof FakeTruffleResolvedJavaMethod) {
                return new FakeTruffleMethodParser(this, graph, parent, method, entryBCI, intrinsicContext);
            }
            else {
                return new ProfiledPERootParser(this, graph, parent, method, entryBCI, intrinsicContext);
            }
        }

    }

    /**
     * TODO(blaumeise20): document
     */
    private class FakeTruffleMethodParser extends BytecodeParser {
        protected FakeTruffleMethodParser(GraphBuilderPhase.Instance graphBuilderInstance, StructuredGraph graph, BytecodeParser parent, ResolvedJavaMethod method,
                        int entryBCI, IntrinsicContext intrinsicContext) {
            super(graphBuilderInstance, graph, parent, method,
                    entryBCI, intrinsicContext);
        }

        @Override
        protected void genReturn(ValueNode returnVal, JavaKind returnKind) {
            ValueNode value;

            // Box the return value if required.
            if (returnKind.isPrimitive()) {
                ResolvedJavaType boxedType = getMetaAccess().lookupJavaType(returnKind.toBoxedJavaClass());
                BoxNode box = graph.add(BoxNode.create(returnVal, boxedType, returnKind));
                lastInstr.setNext(box);
                lastInstr = box;
                value = box;
            }
            else {
                value = returnVal;
            }

            super.genReturn(value, JavaKind.Object);
        }
    }

    /**
     * A customized bytecode parser made for parsing a faked Truffle method.
     */
    private class ProfiledPERootParser extends BytecodeParser {

        protected ProfiledPERootParser(GraphBuilderPhase.Instance graphBuilderInstance, StructuredGraph graph, BytecodeParser parent, ResolvedJavaMethod method,
                        int entryBCI, IntrinsicContext intrinsicContext) {
            super(graphBuilderInstance, graph, parent, method,
                    entryBCI, intrinsicContext);
        }

        @Override
        protected void build(FixedWithNextNode startInstruction, FrameStateBuilder startFrameState) {
            // Before the graph can be built, we have to ensure that we correctly load all
            // parameters from the `Object[]` into local variables.

            System.out.println(startFrameState.toString());
            System.out.println(bytecode.getFrameDescriptor());

            FixedWithNextNode cur = startInstruction;
            ValueNode rawArgs = startFrameState.loadLocal(1, JavaKind.Object); // First parameter is 1, since 0 is the `this` object.
            AlternativeBytecodeParameterProxy[] parameters = bytecode.getParameters();
            ValueNode[] parameterNodes = new ValueNode[parameters.length];
            // Packing the original first parameter through Pi nodes enables elimination of bound checks and other optimizations.
            ValueNode args = graph.unique(new PiArrayNode(rawArgs, ConstantNode.forInt(parameters.length, graph), rawArgs.stamp(NodeView.DEFAULT)));
            args = graph.unique(new PiNode(args, new ObjectStamp(types.Object_Array, true, true, false, true)));
            for (int i = 0; i < parameters.length; i++) {
                LoadIndexedNode loadNode = graph.add(new LoadIndexedNode(null, args, ConstantNode.forInt(i, graph), null, JavaKind.Object));
                cur.setNext(loadNode);
                cur = loadNode;
                parameterNodes[i] = loadNode;
            }
            for (int i = 0; i < parameters.length; i++) {
                // All parameters are packed into an Object, meaning that primitive types are boxed. Here we have to detect that and insert unboxing nodes.
                // For a method like `myMethod(int a, Integer b, String c)` the parameters will be put into an array looking like `Object[] { Integer, Integer, String }`.
                // Only the first parameter has to be unboxed though, since the second one is still intended to be boxed at this point and will possibly be unboxed later.
                String type = parameters[i].getType();
                if (!type.startsWith("L") && !type.startsWith("[")) {
                    cur = genUnbox(cur, parameters[i], parameterNodes[i]);
                    parameterNodes[i] = cur;
                }
            }

            System.out.println(parameters.length);

            // Bypass parser and directly inline the "child" method instead.
            graph.recordMethod(method);
            lastInstr = cur;
            frameState = startFrameState;
            stream.setBCI(0);
            assert getParent() == null && !method.isSynchronized();

            StartNode startNode = graph.start();
            startNode.setStateAfter(frameState.create(bci(), startNode));

            FixedWithNextNode oldLast = cur;

            parseAndInlineCallee(BytecodeToGraphParser.this.method, parameterNodes, null);

            System.out.println(oldLast.next());

            genReturn(frameState.pop(JavaKind.Object), JavaKind.Object);

            // super.build(cur, startFrameState);
        }

        @SuppressWarnings("hiding")
        private FixedWithNextNode genUnbox(FixedWithNextNode cur, AlternativeBytecodeParameterProxy parameter, ValueNode parameterNode) {
            JavaKind kind;
            switch (parameter.getType().charAt(0)) {
                case 'Z': kind = JavaKind.Boolean; break;
                case 'B': kind = JavaKind.Byte; break;
                case 'S': kind = JavaKind.Short; break;
                case 'C': kind = JavaKind.Char; break;
                case 'I': kind = JavaKind.Int; break;
                case 'F': kind = JavaKind.Float; break;
                case 'J': kind = JavaKind.Long; break;
                case 'D': kind = JavaKind.Double; break;
                default:
                    throw new IllegalStateException("should have never reached here, method call has to be guarded");
            }

            ResolvedJavaType boxedType = getMetaAccess().lookupJavaType(kind.toBoxedJavaClass());
            ObjectStamp stamp = new ObjectStamp(boxedType, true, true, false, false);
            ValueNode pi = graph.unique(new PiNode(parameterNode, stamp));
            ValueNode box = graph.unique(new BoxNode.TrustedBoxedValue(pi));
            UnboxNode unbox = graph.add(new UnboxNode(box, kind, getMetaAccess()));
            cur.setNext(unbox);
            return unbox;
        }

    }

}
