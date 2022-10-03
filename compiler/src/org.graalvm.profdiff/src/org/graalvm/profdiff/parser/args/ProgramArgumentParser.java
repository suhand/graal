/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.profdiff.parser.args;

import org.graalvm.profdiff.command.Command;

/**
 * Assembles and parses program arguments. This is the root parser of the program.
 */
public class ProgramArgumentParser extends ArgumentParser {
    /**
     * The name of the program.
     */
    private final String prog;

    /**
     * The description of the program.
     */
    private final String description;

    /**
     * Constructs an argument parser.
     *
     * @param prog the name of the program
     * @param description the description of the program
     */
    public ProgramArgumentParser(String prog, String description) {
        this.prog = prog;
        this.description = description;
    }

    /**
     * Gets the name of the program.
     */
    public String getProg() {
        return prog;
    }

    /**
     * Formats a help message for the program. Includes a usage string, the description of the
     * program, and a listing of option/positional/command arguments.
     *
     * @return a help message for the program
     */
    public String formatHelp() {
        StringBuilder sb = new StringBuilder();
        sb.append("usage: ").append(prog);
        if (!optionArguments.isEmpty()) {
            sb.append(' ').append(formatOptionUsage());
        }
        if (!positionalArguments.isEmpty()) {
            sb.append(' ').append(formatPositionalUsage());
        }
        sb.append("\n\n").append(description).append('\n');
        if (!optionArguments.isEmpty()) {
            sb.append('\n').append(formatOptionHelp());
        }
        if (!positionalArguments.isEmpty()) {
            sb.append('\n').append(formatPositionalHelp());
        }
        if (getCommandGroup().isPresent()) {
            sb.append('\n').append(getCommandGroup().get().formatCommandsHelp());
        }
        return sb.toString();
    }

    /**
     * Formats a help message for a given command of the program. Includes a usage string, the
     * description of the command, and a listing of option/positional/command arguments for the
     * given command.
     *
     * @param command the command for which the help message is formatted
     * @return a help message for the command
     */
    public String formatHelp(Command command) {
        StringBuilder sb = new StringBuilder();
        sb.append("usage: ").append(prog);
        if (!optionArguments.isEmpty()) {
            sb.append(' ').append(formatOptionUsage());
        }
        if (!positionalArguments.isEmpty()) {
            sb.append(' ').append(formatPositionalUsage(command));
        }
        ArgumentParser commandParser = command.getArgumentParser();
        if (!commandParser.getOptionArguments().isEmpty()) {
            sb.append(' ').append(command.getArgumentParser().formatOptionUsage());
        }
        if (!commandParser.getPositionalArguments().isEmpty()) {
            sb.append(' ').append(commandParser.formatPositionalUsage());
        }
        sb.append("\n\n").append(command.getDescription()).append('\n');
        if (!commandParser.getOptionArguments().isEmpty()) {
            sb.append('\n').append(commandParser.formatOptionHelp());
        }
        if (!commandParser.getPositionalArguments().isEmpty()) {
            sb.append('\n').append(commandParser.formatPositionalHelp());
        }
        if (commandParser.getCommandGroup().isPresent()) {
            sb.append('\n').append(commandParser.getCommandGroup().get().formatCommandsHelp());
        }
        return sb.toString();
    }
}
