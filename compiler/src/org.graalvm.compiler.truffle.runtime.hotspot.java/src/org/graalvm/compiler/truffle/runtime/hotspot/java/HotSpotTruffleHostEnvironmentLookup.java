/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.runtime.hotspot.java;

import java.util.concurrent.atomic.AtomicReference;

import org.graalvm.compiler.serviceprovider.ServiceProvider;
import org.graalvm.compiler.truffle.compiler.host.TruffleHostEnvironment;

import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.meta.ResolvedJavaType;

@ServiceProvider(TruffleHostEnvironment.Lookup.class)
public final class HotSpotTruffleHostEnvironmentLookup implements TruffleHostEnvironment.Lookup {

    private static final AtomicReference<HotSpotTruffleRuntime> RUNTIME = new AtomicReference<>();
    private TruffleHostEnvironment environment;

    @Override
    public TruffleHostEnvironment lookup(ResolvedJavaType forType) {
        HotSpotTruffleRuntime runtime = RUNTIME.get();
        if (runtime == null) {
            // fast-path non truffle
            return null;
        }
        TruffleHostEnvironment env = this.environment;
        if (env != null && env.runtime() == runtime) {
            // fast path truffle
            return env;
        }
        // in an hotspot environment multiple compiler threads might lookup
        // the environment. Make sure we only create it once.
        synchronized (this) {
            env = this.environment;
            if (env != null && env.runtime() == runtime) {
                return env;
            }
            this.environment = env = new HotSpotTruffleHostEnvironment(runtime, HotSpotJVMCIRuntime.runtime().getHostJVMCIBackend().getMetaAccess());
        }
        return env;
    }

    static boolean registerRuntime(HotSpotTruffleRuntime runtime) {
        return RUNTIME.compareAndSet(null, runtime);
    }

}
