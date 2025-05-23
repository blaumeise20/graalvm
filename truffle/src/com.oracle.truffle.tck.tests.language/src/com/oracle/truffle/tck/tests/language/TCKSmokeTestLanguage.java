/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.tck.tests.language;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.tck.tests.language.TCKSmokeTestLanguage.TCKSmokeTestLanguageContext;
import sun.misc.Unsafe;

import java.io.Closeable;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ServiceLoader;

@Registration(id = TCKSmokeTestLanguage.ID, name = TCKSmokeTestLanguage.ID, characterMimeTypes = TCKSmokeTestLanguage.MIME)
final class TCKSmokeTestLanguage extends TruffleLanguage<TCKSmokeTestLanguageContext> {

    static final String ID = "TCKSmokeTestLanguage";
    static final String MIME = "text/x-TCKSmokeTestLanguage";

    TCKSmokeTestLanguage() {
    }

    @Override
    protected TCKSmokeTestLanguageContext createContext(Env env) {
        return new TCKSmokeTestLanguageContext(env);
    }

    @Override
    protected CallTarget parse(ParsingRequest request) {
        try {
            Thread thread = new Thread(() -> {
            });
            URL url = URI.create("http://localhost").toURL();
            RootNode root = new RootNodeImpl(this,
                            new PrivilegedCallNode(thread, url),
                            new UnsafeCallNode(),
                            new AllowedURLNode(),
                            new DeniedURLNode());
            return root.getCallTarget();
        } catch (MalformedURLException urlException) {
            throw new AssertionError(urlException);
        }
    }

    static final class TCKSmokeTestLanguageContext {
        final Env env;

        TCKSmokeTestLanguageContext(Env env) {
            this.env = env;
        }
    }

    private static final class RootNodeImpl extends RootNode {

        private @Children BaseNode[] children;

        RootNodeImpl(TCKSmokeTestLanguage language, BaseNode... children) {
            super(language);
            this.children = children;
        }

        @Override
        @ExplodeLoop
        public Object execute(VirtualFrame frame) {
            for (BaseNode child : children) {
                child.execute(frame);
            }
            return true;
        }
    }

    private abstract static class BaseNode extends Node {
        abstract void execute(VirtualFrame frame);
    }

    private static final class PrivilegedCallNode extends BaseNode {

        private static final Method READ_FILE_METHOD;
        private static final Constructor<? extends Closeable> FILE_INPUT_STREAM_CONSTRUCTOR;
        private static final MethodHandle READ_FILE_HANDLE;

        static {
            try {
                READ_FILE_METHOD = Files.class.getMethod("readAllBytes", Path.class);
                FILE_INPUT_STREAM_CONSTRUCTOR = FileInputStream.class.getConstructor(String.class);
                MethodType methodSignature = MethodType.methodType(byte[].class, Path.class);
                READ_FILE_HANDLE = MethodHandles.lookup().findStatic(Files.class, "readAllBytes", methodSignature);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        }

        private final Thread otherThread;
        private final URL url;

        PrivilegedCallNode(Thread thread, URL url) {
            this.otherThread = thread;
            this.url = url;
        }

        @Override
        void execute(VirtualFrame frame) {
            doPrivilegedCall();
            doBehindBoundaryPrivilegedCall();
            doInterrupt();
            doPolymorphicCall();
            callMethodReflectively();
            callConstructorReflectively();
            callMethodHandle();
            testService();
        }

        @SuppressWarnings("deprecation" /* JEP-411 */)
        void doPrivilegedCall() {
            otherThread.checkAccess();
        }

        @TruffleBoundary
        static void doBehindBoundaryPrivilegedCall() {
            Thread.currentThread().setName("Thread-2");
        }

        @TruffleBoundary
        void doInterrupt() {
            if (this.otherThread != null) {
                this.otherThread.interrupt();
            }
            Thread.currentThread().interrupt();
        }

        @TruffleBoundary
        void doPolymorphicCall() {
            try {
                InputStream in = url.openStream();
                in.close();
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
        }

        @TruffleBoundary
        static void callMethodReflectively() {
            try {
                READ_FILE_METHOD.invoke(null, Path.of("test"));
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        }

        @TruffleBoundary
        static void callConstructorReflectively() {
            try {
                Closeable closeable = FILE_INPUT_STREAM_CONSTRUCTOR.newInstance("test");
                closeable.close();
            } catch (ReflectiveOperationException | IOException e) {
                throw new AssertionError(e);
            }
        }

        @TruffleBoundary
        static void callMethodHandle() {
            try {
                READ_FILE_HANDLE.invoke(Path.of("test"));
            } catch (Throwable t) {
                throw new AssertionError(t);
            }
        }

        @TruffleBoundary
        static void testService() {
            ServiceLoader.load(Service.class).iterator().next().execute();
        }
    }

    private static final class UnsafeCallNode extends BaseNode {
        private static final Unsafe UNSAFE;
        static {
            try {
                Field f = Unsafe.class.getDeclaredField("theUnsafe");
                f.setAccessible(true);
                UNSAFE = (Unsafe) f.get(null);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        }

        @SuppressWarnings("deprecation"/* JDK-8277863 */)
        private static long getFieldOffset(Field filed) {
            return UNSAFE.objectFieldOffset(filed);
        }

        @Override
        void execute(VirtualFrame frame) {
            doUnsafeAccess();
            doBehindBoundaryUnsafeAccess();
        }

        @TruffleBoundary
        static long getFieldOffset(Class<?> clazz, String value) {
            try {
                return getFieldOffset(clazz.getDeclaredField(value));
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        }

        static void doUnsafeAccess() {
            int i = 42;
            int result = UNSAFE.getInt(i, getFieldOffset(Integer.class, "value"));
            assert i == result;
        }

        @TruffleBoundary
        static void doBehindBoundaryUnsafeAccess() {
            int i = 23;
            int result = UNSAFE.getInt(i, getFieldOffset(Integer.class, "value"));
            assert i == result;
        }
    }

    private static final class AllowedURLNode extends BaseNode {

        private final URI currentWorkingDirectory;

        AllowedURLNode() {
            this.currentWorkingDirectory = Path.of("").toAbsolutePath().toUri();
        }

        @Override
        void execute(VirtualFrame frame) {
            doURLOf();
        }

        @TruffleBoundary
        private void doURLOf() {
            try {
                URL.of(currentWorkingDirectory, null);
            } catch (MalformedURLException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }
    }

    private static final class DeniedURLNode extends BaseNode {

        private final URLStreamHandler handler;
        private final URI currentWorkingDirectory;

        DeniedURLNode() {
            this.handler = new MockURLStreamHandler();
            this.currentWorkingDirectory = Path.of("").toAbsolutePath().toUri();
        }

        @Override
        void execute(VirtualFrame frame) {
            doURLOf();
        }

        @TruffleBoundary
        private void doURLOf() {
            try {
                URL.of(currentWorkingDirectory, handler);
            } catch (MalformedURLException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        private static final class MockURLStreamHandler extends URLStreamHandler {

            @Override
            protected URLConnection openConnection(URL u) {
                throw new UnsupportedOperationException();
            }
        }
    }
}
