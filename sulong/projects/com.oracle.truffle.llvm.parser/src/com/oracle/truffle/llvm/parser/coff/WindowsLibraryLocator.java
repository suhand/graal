/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.parser.coff;

import java.nio.file.Path;
import java.nio.file.Paths;

import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.runtime.DefaultLibraryLocator;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LibraryLocator;

public final class WindowsLibraryLocator extends LibraryLocator {
    Path libraryPath;

    public WindowsLibraryLocator(Source source) {
        libraryPath = source != null ? Paths.get(source.getPath()).getParent() : null;
    }

    @Override
    public Object locateLibrary(LLVMContext context, String lib, Object reason) {
        TruffleFile file;
        Path libPath = Paths.get(lib);

        if (libPath.isAbsolute()) {
            return DefaultLibraryLocator.locateAbsolute(context, libPath);
        }

        // first try in the same directory
        if (libraryPath != null) {
            file = DefaultLibraryLocator.locateAbsolute(context, libraryPath.resolve(libPath));
            if (file != null) {
                return file;
            }
        }

        // then try the current directory
        Path path = Paths.get(context.getEnv().getCurrentWorkingDirectory().getPath());
        file = DefaultLibraryLocator.locateAbsolute(context, path.resolve(libPath));
        if (file != null) {
            return file;
        }

        // finally try the global directory
        return DefaultLibraryLocator.locateGlobal(context, lib);
    }
}
