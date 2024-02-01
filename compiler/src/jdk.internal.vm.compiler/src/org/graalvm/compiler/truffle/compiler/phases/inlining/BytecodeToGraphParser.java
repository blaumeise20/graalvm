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

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.TimerKey;
import org.graalvm.compiler.java.BytecodeParser;
import org.graalvm.compiler.java.FrameStateBuilder;
import org.graalvm.compiler.java.GraphBuilderPhase;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedGuardNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.PiArrayNode;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.StartNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.calc.IsNullNode;
import org.graalvm.compiler.nodes.calc.ObjectEqualsNode;
import org.graalvm.compiler.nodes.extended.BoxNode;
import org.graalvm.compiler.nodes.extended.UnboxNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.java.InstanceOfNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.java.LoadIndexedNode;
import org.graalvm.compiler.nodes.graphbuilderconf.IntrinsicContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.nodes.type.StampTool;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.truffle.compiler.KnownTruffleTypes;
import org.graalvm.compiler.truffle.compiler.TruffleTierContext;
import org.graalvm.compiler.truffle.compiler.nodes.TruffleAssumption;

import com.oracle.truffle.compiler.AlternativeBytecodeProxy;
import com.oracle.truffle.compiler.AlternativeBytecodeParameterCheckProxy;
import com.oracle.truffle.compiler.AlternativeBytecodeParameterProxy;

import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
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
        this.method = new FakeTruffleResolvedJavaMethod(bytecode, context.types(), context.getMetaAccess());
    }

    public static StructuredGraph parseFromBytecode(StructuredGraph graph, AlternativeBytecodeProxy bytecode, TruffleTierContext context) {
        return new BytecodeToGraphParser(graph, bytecode, context).build();
    }

    @SuppressWarnings("try")
    private StructuredGraph build() {
        StructuredGraph newGraph = new StructuredGraph.Builder(graph.getOptions(), debug, AllowAssumptions.ifTrue(true))
            .profileProvider(null)
            .trackNodeSourcePosition(false)
            .method(types.OptimizedCallTarget_profiledPERoot)
            .cancellable(graph.getCancellable())
            .build();

        StructuredGraph graphToBuild = newGraph;
        graphToBuild = graph;

        for (JavaConstant assumption : bytecode.getCurrentAssumptions()) {
            graphToBuild.getAssumptions().record(new TruffleAssumption(assumption));
        }

        CoreProviders providers = new Providers(context.getMetaAccess(), null, null, null, null, null, null, null, null, null, null, null, null); // TODO(blaumeise20): change providers
        try (DebugContext.Scope scope = debug.scope("buildGraph", graphToBuild); DebugCloseable a = BuildGraphTimer.start(debug)) {
            Plugins plugins = new Plugins(new InvocationPlugins());
            GraphBuilderConfiguration config = GraphBuilderConfiguration.getDefault(plugins);
            GraphBuilderPhase.Instance graphBuilderPhaseInstance = new BytecodeGraphBuilderPhase(providers, config);
            graphBuilderPhaseInstance.apply(graphToBuild);

            // canonicalizer.apply(graphToEncode, providers);
            // if (postParsingPhase != null) {
            //     postParsingPhase.apply(graphToEncode, providers);
            // }
        } catch (Throwable ex) {
            throw debug.handle(ex);
        }

        return graphToBuild;
    }

    private class BytecodeGraphBuilderPhase extends GraphBuilderPhase.Instance {

        protected BytecodeGraphBuilderPhase(CoreProviders providers, GraphBuilderConfiguration graphBuilderConfig) {
            super(providers, graphBuilderConfig, OptimisticOptimizations.NONE, null);
            System.out.println(graphBuilderConfig.eagerResolving());
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

        // @Override
        // protected void genInvokeVirtual(ResolvedJavaMethod resolvedTarget) {
        //     System.out.println(resolvedTarget);
        //     super.genInvokeVirtual(resolvedTarget);
        // }

        @Override
        protected void genGetField(ResolvedJavaField resolvedField, ValueNode receiver) {
            nullCheckWithField(receiver);
            super.genGetField(resolvedField, receiver);
        }

        @Override
        protected void genPutField(ResolvedJavaField resolvedField, ValueNode receiver, ValueNode value) {
            nullCheckWithField(receiver);
            super.genPutField(resolvedField, receiver, value);
        }

        // Static fields aren't really static in most language implementations.

        @Override
        protected void genGetStatic(int cpi, int opcode) {
            ResolvedJavaField field = (ResolvedJavaField) lookupField(cpi, opcode);
            ValueNode receiver = ConstantNode.forConstant(bytecode.getConstantPool().lookupStaticInstanceForField(cpi), getMetaAccess(), graph);

            ValueNode fieldRead = append(genLoadField(receiver, field));
            JavaKind fieldKind = field.getJavaKind();
            frameState.push(fieldKind, fieldRead);
        }

        @Override
        protected void genPutStatic(int cpi, int opcode) {
            ResolvedJavaField field = (ResolvedJavaField) lookupField(cpi, opcode);
            ValueNode receiver = ConstantNode.forConstant(bytecode.getConstantPool().lookupStaticInstanceForField(cpi), getMetaAccess(), graph);

            ValueNode value = frameState.pop(field.getJavaKind());
            genStoreField(receiver, field, value);
        }

        @Override
        protected void genLoadIndexed(JavaKind kind, ValueNode array, ValueNode index) {
            nullCheckWithField(array);
            super.genLoadIndexed(kind, array, index);
        }

        // @Override
        // protected void genLoadConstant(int cpi, int opcode) {
        //     System.out.println("cpi: " + cpi + ", opcode: " + opcode);
        //     super.genLoadConstant(cpi, opcode);
        // }

        private void nullCheckWithField(ValueNode receiver) {
            Class<?> objectClass = bytecode.getObjectClass();
            String nullCheckField = bytecode.getNullCheckField();
            ResolvedJavaField field;
            try {
                field = getMetaAccess().lookupJavaField(objectClass.getDeclaredField(nullCheckField));
            } catch (NoSuchFieldException e) {
                throw new IllegalStateException(e); // TODO(blaumeise20): other exception
            }

            ValueNode fieldValue = append(genLoadField(receiver, field));
            LogicNode isNull = add(new IsNullNode(fieldValue));
            append(new FixedGuardNode(isNull, DeoptimizationReason.TransferToInterpreter, DeoptimizationAction.InvalidateReprofile));
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
            // Prepare for parsing.
            startFrameState.storeLocal(0, JavaKind.Object, ConstantNode.forConstant(context.compilableConstant, getMetaAccess(), graph));
            StartNode startNode = graph.start();
            startNode.setStateAfter(startFrameState.create(bci(), startNode));
            graph.recordMethod(method);
            frameState = startFrameState;
            stream.setBCI(0);
            assert getParent() == null && !method.isSynchronized();

            MetaAccessProvider metaAccess = getMetaAccess();

            System.out.println(startFrameState.toString());
            System.out.println(bytecode.getFrameDescriptor());
            System.out.println(needsExplicitException());

            // --- LOAD ARGUMENTS ---

            // Before the graph can be built, we have to ensure that we correctly load all
            // parameters from the `Object[]` into local variables.
            lastInstr = startInstruction;
            ValueNode argArray = startFrameState.loadLocal(1, JavaKind.Object); // First parameter is 1, since 0 is the `this` object.
            AlternativeBytecodeParameterProxy[] parameters = bytecode.getParameters(); System.out.println("[" + Arrays.stream(parameters).map(b -> b.getType()).collect(Collectors.joining(", ")) + "]"); System.out.println(parameters.length);
            Class<?>[] argumentsProfile = bytecode.getArgumentsProfile();
            int receiver = Modifier.isStatic(bytecode.getModifiers()) ? 0 : 1;
            ValueNode[] args = new ValueNode[parameters.length + receiver];

            ParameterInfo[] parameterInfo = new ParameterInfo[parameters.length + receiver];
            if (receiver == 1) {
                Class<?> receiverClass = bytecode.getReceiverType();
                ResolvedJavaType receiverType = metaAccess.lookupJavaType(receiverClass);
                parameterInfo[0] = new ParameterInfo(receiverType, JavaKind.Object);
                applyFieldCheck(parameterInfo[0], receiverClass, bytecode.getReceiverCheck());
            }
            for (int i = receiver; i < parameterInfo.length; i++) {
                AlternativeBytecodeParameterProxy parameter = parameters[i - receiver];
                Class<?> expectedClass = parameter.getExpectedClass();
                ResolvedJavaType objectType = metaAccess.lookupJavaType(expectedClass);
                parameterInfo[i] = new ParameterInfo(objectType, parameter.getJavaKind());
                applyFieldCheck(parameterInfo[i], expectedClass, parameter.getArgumentCheck());
            }

            if (argumentsProfile != null) {
                // Packing the original first parameter through Pi nodes enables elimination of bound checks and other optimizations.
                argArray = graph.unique(new PiArrayNode(argArray, ConstantNode.forInt(argumentsProfile.length, graph), argArray.stamp(NodeView.DEFAULT)));
                argArray = graph.unique(new PiNode(argArray, new ObjectStamp(types.Object_Array, true, true, false, true)));

                assert args.length == argumentsProfile.length : "length mismatch";
                for (int i = 0; i < argumentsProfile.length; i++) {
                    if (argumentsProfile[i] != null) {
                        parameterInfo[i].profiled = metaAccess.lookupJavaType(argumentsProfile[i]);
                    }
                }
            }

            for (int i = 0; i < args.length; i++) {
                LoadIndexedNode loadNode = appendFixed(graph.add(new LoadIndexedNode(null, argArray, ConstantNode.forInt(i, graph), null, JavaKind.Object)));
                args[i] = loadNode;
            }

            for (int i = 0; i < args.length; i++) {
                ParameterInfo parameter = parameterInfo[i];
                ValueNode arg = args[i];

                if (parameter.profiled != null) {
                    assert parameter.objectType.isAssignableFrom(parameter.profiled);
                    // A type is only included in the profile when it is known to be exact and non null.
                    ObjectStamp stamp = new ObjectStamp(parameter.profiled, true, true, false, false);
                    arg = graph.unique(new PiNode(arg, stamp));
                    if (parameterInfo[i].javaKind != JavaKind.Object) {
                        arg = graph.unique(new BoxNode.TrustedBoxedValue(arg));
                    }
                }
                else {
                    // Do instance checks.
                    ObjectStamp stamp = new ObjectStamp(parameter.objectType, false, false, false, false);
                    LogicNode instanceOf = graph.unique(InstanceOfNode.createHelper(stamp, arg, null, null));
                    FixedGuardNode classCastGuard = appendFixed(graph.add(new FixedGuardNode(instanceOf, DeoptimizationReason.ClassCastException, DeoptimizationAction.InvalidateReprofile)));
                    arg = graph.unique(new PiNode(arg, stamp, classCastGuard));
                }

                if (parameter.checkField != null) {
                    ValueNode object = nullCheck(arg);
                    ValueNode fieldValue = appendFixed(graph.add((LoadFieldNode) genLoadField(object, parameter.checkField)));
                    LogicNode equals = graph.unique(new ObjectEqualsNode(fieldValue, ConstantNode.forConstant(parameter.checkValue, metaAccess, graph)));
                    appendFixed(graph.add(new FixedGuardNode(equals, DeoptimizationReason.TransferToInterpreter, DeoptimizationAction.InvalidateReprofile, true)));
                }

                if (parameter.javaKind != JavaKind.Object) {
                    // All parameters are packed into an Object, meaning that primitive types are boxed. Here we have to detect that and insert unboxing nodes.
                    // For a method like `myMethod(int a, Integer b, String c)` the parameters will be put into an array looking like `Object[] { Integer, Integer, String }`.
                    // Only the first parameter has to be unboxed though, since the second one is still intended to be boxed at this point and will possibly be unboxed later.
                    UnboxNode unbox = appendFixed(graph.add(new UnboxNode(arg, parameter.javaKind, metaAccess)));
                    arg = unbox;
                }

                args[i] = arg;
            }

            // --- INLINE METHOD ---

            // Bypass parser and directly inline the "child" method instead.
            boolean hasReturnValue = parseAndInlineCallee(BytecodeToGraphParser.this.method, args, null);

            // --- HANDLE RETURN ---

            JavaKind returnKind = BytecodeToGraphParser.this.method.getSignature().getReturnKind();
            ValueNode returnVal;
            if (returnKind == JavaKind.Void) {
                if (hasReturnValue) throw new IllegalStateException("should not have return value in void");

                returnVal = ConstantNode.forConstant(bytecode.getVoidObject(), getMetaAccess(), graph);
            }
            else {
                if (!hasReturnValue) throw new IllegalStateException("expected return value in non-void function");

                returnVal = frameState.pop(returnKind);

                // Box the return value if required.
                if (returnKind.isPrimitive()) {
                    ResolvedJavaType boxedType = metaAccess.lookupJavaType(returnKind.toBoxedJavaClass());
                    BoxNode box = graph.add(BoxNode.create(returnVal, boxedType, returnKind));
                    lastInstr.setNext(box);
                    lastInstr = box;
                    returnVal = box;
                }

            }
            genReturn(returnVal, JavaKind.Object);
        }

        private void applyFieldCheck(ParameterInfo parameter, Class<?> clazz, AlternativeBytecodeParameterCheckProxy check) {
            if (check == null) {
                return;
            }

            String field = check.getField();
            JavaConstant value = check.getValue();

            ResolvedJavaField checkField;
            try {
                checkField = getMetaAccess().lookupJavaField(clazz.getDeclaredField(field));
            } catch (NoSuchFieldException e) {
                throw new IllegalStateException(e); // TODO(blaumeise20): other exception
            }
            parameter.checkField = checkField;
            parameter.checkValue = value;
        }

        private <T extends FixedWithNextNode> T appendFixed(T node) {
            lastInstr.setNext(node);
            lastInstr = node;
            return node;
        }

        private ValueNode nullCheck(ValueNode node) {
            ObjectStamp maybeNullStamp = (ObjectStamp) node.stamp(NodeView.DEFAULT);
            if (maybeNullStamp.nonNull()) {
                return node;
            }

            LogicNode isNull = graph.unique(IsNullNode.create(node));
            FixedGuardNode guard = appendFixed(graph.add(new FixedGuardNode(isNull, DeoptimizationReason.NullCheckException, DeoptimizationAction.InvalidateReprofile)));
            return graph.unique(new PiNode(node, maybeNullStamp.asNonNull(), guard));
        }

    }

    private static class ParameterInfo {
        final ResolvedJavaType objectType;
        final JavaKind javaKind;
        ResolvedJavaType profiled = null;
        ResolvedJavaField checkField = null;
        JavaConstant checkValue = null;

        ParameterInfo(ResolvedJavaType objectType, JavaKind javaKind) {
            this.objectType = objectType;
            this.javaKind = javaKind;
        }
    }

}
