/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.graphio.parsing.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jdk.graal.compiler.graphio.parsing.BinaryReader.Method;

public class InputMethod extends Properties.Entity {
    private final Method method;
    private final String name;
    private final int bci;
    private final String shortName;
    private final List<InputMethod> inlined;
    private InputMethod parentMethod;
    private final Group group;
    private final List<InputBytecode> bytecodes;

    public Method getMethod() {
        return method;
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(name);
        result = result * 31 + bci;
        result = result * 31 + Objects.hashCode(shortName);
        result = result * 31 + Objects.hashCode(inlined);
        result = result * 31 + Objects.hashCode(bytecodes);
        result = result * 31 + Objects.hashCode(method);
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || (!(o instanceof InputMethod))) {
            return false;
        }

        final InputMethod im = (InputMethod) o;
        return Objects.equals(name, im.name) && bci == im.bci && Objects.equals(shortName, im.shortName) && Objects.equals(inlined, im.inlined) && Objects.equals(bytecodes, im.bytecodes) &&
                        Objects.equals(method, im.method);
    }

    public InputMethod(Group parent, String name, String shortName, int bci, Method method) {
        this.group = parent;
        this.name = name;
        this.bci = bci;
        this.shortName = shortName;
        inlined = new ArrayList<>();
        bytecodes = new ArrayList<>();
        if (method != null) {
            setBytecodes(method.code);
        }
        this.method = method;
    }

    public List<InputBytecode> getBytecodes() {
        return Collections.unmodifiableList(bytecodes);
    }

    public List<InputMethod> getInlined() {
        return Collections.unmodifiableList(inlined);
    }

    public void addInlined(InputMethod m) {
        // assert bci unique
        for (InputMethod m2 : inlined) {
            assert m2.getBci() != m.getBci();
        }

        inlined.add(m);
        assert m.parentMethod == null;
        m.parentMethod = this;

        for (InputBytecode bc : bytecodes) {
            if (bc.getBci() == m.getBci()) {
                bc.setInlined(m);
            }
        }
    }

    public Group getGroup() {
        return group;
    }

    public String getShortName() {
        return shortName;
    }

    private void setBytecodes(byte[] code) {
        if (code == null || code.length == 0) {
            return;
        }
        InputBytecode.parseCode(code, bytecodes, inlined);
    }

    public void setBytecodes(String text) {
        Pattern instruction = Pattern.compile("\\s*(\\d+)\\s*:?\\s*(\\w+)\\s*(.*)(?://(.*))?");
        String[] strings = text.split("\n");
        int oldBci = -1;
        for (String string : strings) {
            if (string.startsWith(" ")) {
                // indented lines are extra textual information
                continue;
            }
            String s = string.trim();
            if (!s.isEmpty()) {
                final Matcher matcher = instruction.matcher(s);
                if (matcher.matches()) {
                    String bciString = matcher.group(1);
                    String opcode = matcher.group(2);
                    String operands = matcher.group(3).trim();
                    String comment = matcher.group(4);
                    if (comment != null) {
                        comment = comment.trim();
                    }

                    int curBci = Integer.parseInt(bciString);

                    // assert correct order of bytecodes
                    assert curBci > oldBci;

                    InputBytecode bc = new InputBytecode(curBci, opcode, operands, comment);
                    bytecodes.add(bc);

                    for (InputMethod m : inlined) {
                        if (m.getBci() == curBci) {
                            bc.setInlined(m);
                            break;
                        }
                    }
                } else {
                    Logger.getLogger(getClass().getName()).log(Level.INFO, "Unparseable bytecode: " + s);
                }
            }
        }
    }

    public String getName() {
        return name;
    }

    public int getBci() {
        return bci;
    }
}
