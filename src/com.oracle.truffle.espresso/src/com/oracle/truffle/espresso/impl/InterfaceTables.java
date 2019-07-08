/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Signature;

/**
 * 3 pass interface table constructor helper:
 *
 * - First pass collects miranda methods and constructs an intermediate itable whose job is to
 * locate where to fetch the needed methods for the second pass.
 *
 * - Second pass is performed after constructing the virtual table (which itself is done after first
 * pass). Its goal is to find and insert in the vtable and the miranda methods the maximally
 * specific methods.
 *
 * - Third pass is performed just after second. Using the now correct vtable and mirandas, perform a
 * simple mapping from the helper table to the final itable.
 */
class InterfaceTables {

    static private final Comparator<TableData> SORTER = new Comparator<TableData>() {
        @Override
        public int compare(TableData o1, TableData o2) {
            return Integer.compare(o1.klass.getID(), o2.klass.getID());
        }
    };

    static private final Entry[][] EMPTY_ENTRY_DUAL_ARRAY = new Entry[0][];
    static private final Method[][] EMPTY_METHOD_DUAL_ARRAY = new Method[0][];

    private final ObjectKlass thisKlass;
    private final String thisRuntimePackage;
    private final ObjectKlass superKlass;
    private final ObjectKlass[] superInterfaces;
    private final ArrayList<Entry[]> tmpTables = new ArrayList<>();
    private final ArrayList<Klass> tmpKlassTable = new ArrayList<>();
    private final ArrayList<Method> mirandas = new ArrayList<>();

    private enum Location {
        SUPERVTABLE,
        DECLARED,
        MIRANDAS
    }

    static class CreationResult {
        Entry[][] tables;
        Klass[] klassTable;
        Method[] mirandas;

        public CreationResult(Entry[][] tables, Klass[] klassTable, Method[] mirandas) {
            TableData[] data = new TableData[klassTable.length];
            for (int i = 0; i < data.length; i++) {
                data[i] = new TableData(klassTable[i], tables[i]);
            }
            Arrays.sort(data, SORTER);
            for (int i = 0; i < data.length; i++) {
                tables[i] = data[i].table;
                klassTable[i] = data[i].klass;
            }
            this.tables = tables;
            this.klassTable = klassTable;
            this.mirandas = mirandas;
        }
    }

    static class TableData {
        Klass klass;
        Entry[] table;

        public TableData(Klass klass, Entry[] table) {
            this.klass = klass;
            this.table = table;
        }
    }

    static class Entry {
        Location loc;
        int index;

        public Entry(Location loc, int index) {
            this.loc = loc;
            this.index = index;
        }
    }

    private InterfaceTables(ObjectKlass thisKlass, ObjectKlass superKlass, ObjectKlass[] superInterfaces) {
        this.thisKlass = thisKlass;
        this.superKlass = superKlass;
        this.superInterfaces = superInterfaces;
        this.thisRuntimePackage = thisKlass.getRuntimePackage();
    }

    /**
     * Constructs the complete list of interfaces an interface needs to implement. Also initializes
     * itable indexes.
     * 
     * @param thisInterfKlass The interface in question
     * @param declared The declared methods of the interface.
     * @return the requested klass array
     */
    public static Klass[] getiKlassTable(ObjectKlass thisInterfKlass, Method[] declared) {
        ArrayList<Klass> tmpKlassTable = new ArrayList<>();
        for (int i = 0; i < declared.length; i++) {
            declared[i].setITableIndex(i);
        }
        tmpKlassTable.add(thisInterfKlass);
        for (ObjectKlass interf : thisInterfKlass.getSuperInterfaces()) {
            for (Klass supInterf : interf.getiKlassTable()) {
                if (canInsert(supInterf, tmpKlassTable)) {
                    tmpKlassTable.add(supInterf);
                }
            }
        }
        return tmpKlassTable.toArray(Klass.EMPTY_ARRAY);
    }

    // @formatter:off
    // checkstyle: stop
    /**
     * Performs the first step of itable creation.
     *
     * @param thisKlass the Klass for which the table is constructed
     * @param superKlass the super class of thisKlass
     * @param superInterfaces the superInterfaces of thisKlass
     * @return a 3-uple containing: <p>
     *      - An intermediate helper for the itable.
     *        Each entry of the helper table contains information of where to find the method that will be put in its place<p>
     *      - An array containing all directly and indirectly implemented interfaces<p>
     *      - An array of implicitly declared methods (aka, mirandas). This most notably contains default methods.<p>
     */
    // checkstyle: resume
    // @formatter:on
    public static CreationResult create(ObjectKlass thisKlass, ObjectKlass superKlass, ObjectKlass[] superInterfaces) {
        return new InterfaceTables(thisKlass, superKlass, superInterfaces).create();
    }

    /**
     * Performs second and third step of itable creation.
     * 
     * @param thisKlass the klass for which we are creating an itable
     * @param tables The helper table obtained from first step
     * @param iklassTable the interfaces directly and indirectly implemented by thisKlass
     * @return the final itable
     */
    public static Method[][] fixTables(ObjectKlass thisKlass, Entry[][] tables, Klass[] iklassTable) {
        ArrayList<Method[]> tmpTables = new ArrayList<>();
        Method[] vtable = thisKlass.getVTable();
        Method[] mirandas = thisKlass.getMirandaMethods();

        // Second step
        for (int i = 0; i < iklassTable.length; i++) {
            fixVTable(tables[i], vtable, mirandas, thisKlass.getDeclaredMethods(), iklassTable[i].getDeclaredMethods());
        }
        // Third step
        for (Entry[] entries : tables) {
            tmpTables.add(getITable(entries, vtable, mirandas, thisKlass.getDeclaredMethods()));
        }
        return tmpTables.toArray(EMPTY_METHOD_DUAL_ARRAY);
    }

    // Actual implementations

    private CreationResult create() {
        for (ObjectKlass interf : superInterfaces) {
            fillMirandas(interf);
            for (Klass supInterf : interf.getiKlassTable()) {
                fillMirandas(supInterf);
            }
        }
        // At this point, no more mirandas should be created.
        if (superKlass != null) {
            for (Klass superKlassInterf : superKlass.getiKlassTable()) {
                fillMirandas(superKlassInterf);
            }
        }

        return new CreationResult(tmpTables.toArray(EMPTY_ENTRY_DUAL_ARRAY), tmpKlassTable.toArray(Klass.EMPTY_ARRAY), mirandas.toArray(Method.EMPTY_ARRAY));
    }

    private void fillMirandas(Klass interf) {
        if (canInsert(interf, tmpKlassTable)) {
            Method[] interfMethods = interf.getDeclaredMethods();
            Entry[] res = new Entry[interfMethods.length];
            for (int i = 0; i < res.length; i++) {
                Method im = interfMethods[i];
                Symbol<Name> mname = im.getName();
                Symbol<Signature> sig = im.getRawSignature();
                res[i] = lookupLocation(im, mname, sig);
            }
            tmpTables.add(res);
            tmpKlassTable.add(interf);
        }
    }

    private static void fixVTable(Entry[] table, Method[] vtable, Method[] mirandas, Method[] declared, Method[] interfMethods) {
        for (int i = 0; i < table.length; i++) {
            Entry entry = table[i];
            int index = entry.index;
            Method virtualMethod;
            switch (entry.loc) {
                case SUPERVTABLE:
                    virtualMethod = vtable[index];
                    break;
                case MIRANDAS:
                    virtualMethod = mirandas[index];
                    break;
                default:
                    virtualMethod = declared[index];
                    break;
            }
            Method interfMethod = interfMethods[i];
            if (!virtualMethod.hasCode() && interfMethod.hasCode()) {
                // Abstract method vs. default method: take default and shortcut default conflict
                // checking.
                updateEntry(vtable, mirandas, entry, index, virtualMethod, interfMethod);
            } else if (checkDefaultConflict(virtualMethod, interfMethod)) {
                Method result = resolveMaximallySpecific(virtualMethod, interfMethod);
                if (result != virtualMethod) {
                    updateEntry(vtable, mirandas, entry, index, virtualMethod, result);
                }
            }
        }
    }

    private static void updateEntry(Method[] vtable, Method[] mirandas, Entry entry, int index, Method virtualMethod, Method toPut) {
        switch (entry.loc) {
            case SUPERVTABLE:
                vtable[index] = toPut;
                toPut.setVTableIndex(index);
                break;
            case DECLARED:
                vtable[virtualMethod.getVTableIndex()] = toPut;
                toPut.setVTableIndex(virtualMethod.getVTableIndex());
                break;
            case MIRANDAS:
                Method newMiranda = new Method(toPut);
                int vtableIndex = virtualMethod.getVTableIndex();
                vtable[vtableIndex] = newMiranda;
                mirandas[index] = newMiranda;
                newMiranda.setVTableIndex(vtableIndex);
                break;
        }
    }

    private static Method[] getITable(Entry[] entries, Method[] vtable, Method[] mirandas, Method[] declared) {
        int pos = 0;
        Method[] res = new Method[entries.length];
        for (Entry entry : entries) {
            switch (entry.loc) {
                case SUPERVTABLE:
                    res[pos] = new Method(vtable[entry.index]);
                    break;
                case DECLARED:
                    res[pos] = new Method(declared[entry.index]);
                    break;
                case MIRANDAS:
                    res[pos] = new Method(mirandas[entry.index]);
                    break;
            }
            res[pos].setITableIndex(pos);
            pos++;
        }
        return res;
    }

    // lookup helpers

    private Entry lookupLocation(Method im, Symbol<Name> mname, Symbol<Signature> sig) {
        Method m = null;
        if (superKlass != null) {
            m = superKlass.lookupVirtualMethod(mname, sig, thisRuntimePackage);
        }
        if (m != null) {
            return new Entry(Location.SUPERVTABLE, m.getVTableIndex());
        }
        int index = getDeclaredMethodIndex(thisKlass.getDeclaredMethods(), mname, sig);
        if (index != -1) {
            return new Entry(Location.DECLARED, index);
        }
        index = lookupMirandas(mname, sig);
        if (index != -1) {
            return new Entry(Location.MIRANDAS, index);
        }
        // This case should only happen during exploration of direct
        // superInterfaces and their interfaces
        mirandas.add(new Method(im)); // Proxy
        return new Entry(Location.MIRANDAS, mirandas.size() - 1);

    }

    private static int getDeclaredMethodIndex(Method[] declaredMethod, Symbol<Name> mname, Symbol<Signature> sig) {
        for (int i = 0; i < declaredMethod.length; i++) {
            Method m = declaredMethod[i];
            if (mname == m.getName() && sig == m.getRawSignature()) {
                return i;
            }
        }
        return -1;
    }

    private int lookupMirandas(Symbol<Name> mname, Symbol<Signature> sig) {
        int pos = 0;
        for (Method m : mirandas) {
            if (m.getName() == mname && sig == m.getRawSignature()) {
                return pos;
            }
            pos++;
        }
        return -1;
    }

    // helper checks

    private static boolean checkDefaultConflict(Method m1, Method m2) {
        return m1.getDeclaringKlass() != m2.getDeclaringKlass() && m1.isDefault() && m2.isDefault();
    }

    private static Method resolveMaximallySpecific(Method m1, Method m2) {
        Klass k1 = m1.getDeclaringKlass();
        Klass k2 = m2.getDeclaringKlass();
        if (k1.isAssignableFrom(k2)) {
            return m2;
        } else if (k2.isAssignableFrom(k1)) {
            return m1;
        } else {
            // JVM specs:
            // Can *declare* ambiguous default method (in bytecodes only, javac wouldn't compile
            // it). (5.4.3.3.)
            //
            // But if you try to *use* them, specs dictate to fail. (6.5.invoke{virtual,interface})
            Method m = new Method(m2);
            m.setPoisonPill();
            return m;
        }
    }

    private static boolean canInsert(Klass interf, ArrayList<Klass> tmpKlassTable) {
        for (Klass k : tmpKlassTable) {
            if (k == interf)
                return false;
        }
        return true;
    }
}