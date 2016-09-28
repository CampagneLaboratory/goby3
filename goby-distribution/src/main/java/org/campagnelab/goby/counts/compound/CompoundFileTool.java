/*
 * Copyright (C) 2009-2010 Institute for Computational Biomedicine,
 *                    Weill Medical College of Cornell University
 *
 *  This file is part of the Goby IO API.
 *
 *     The Goby IO API is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Lesser General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     The Goby IO API is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Lesser General Public License for more details.
 *
 *     You should have received a copy of the GNU Lesser General Public License
 *     along with the Goby IO API.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.campagnelab.goby.counts.compound;

import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Switch;
import com.martiansoftware.jsap.UnflaggedOption;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

/**
 * Command line interface to Compound Files.
 * @author Kevin Dorff
 */
public class CompoundFileTool {

    public enum PROGRAM_MODE {
        LIST,
        ADD,
        EXTRACT,
        HELP
    }

    private String compoundFilename;
    private String[] filenames;

    public static void main(final String[] args) throws Exception {
        final CompoundFileTool tool = new CompoundFileTool();
        tool.run(args);
    }

    private JSAP configureJsap() throws JSAPException {
        final JSAP jsap = new JSAP();

        final UnflaggedOption compoundFileFlag = new UnflaggedOption("compound-file")
                .setRequired(true)
                .setStringParser(JSAP.STRING_PARSER)
                .setGreedy(false);
        compoundFileFlag.setHelp("The compound file to read/write");
        jsap.registerParameter(compoundFileFlag);

        final Switch listSwitch = new Switch("list")
                .setShortFlag('l')
                .setLongFlag("list");
        listSwitch.setHelp("List the contents of the compound file");
        jsap.registerParameter(listSwitch);

        final Switch addSwitch = new Switch("add")
                .setShortFlag('a')
                .setLongFlag("add");
        addSwitch.setHelp("Add files to the compound file");
        jsap.registerParameter(addSwitch);

        final Switch extractSwitch = new Switch("extract")
                .setShortFlag('x')
                .setLongFlag("extract");
        extractSwitch.setHelp("Extract files from the compound file");
        jsap.registerParameter(extractSwitch);

        final Switch helpSwitch = new Switch("help")
                .setShortFlag('h')
                .setLongFlag("help");
        helpSwitch.setHelp("This help information");
        jsap.registerParameter(helpSwitch);

        final UnflaggedOption filenamesUnflag = new UnflaggedOption("filenames")
                .setStringParser(JSAP.STRING_PARSER)
                .setGreedy(true);
        filenamesUnflag.setHelp("Filenames (separated by spaces) or pattern(s) to add to the compound file");
        jsap.registerParameter(filenamesUnflag);

        return jsap;
    }

    private void run(final String[] args) throws JSAPException, IOException {
        final JSAP jsap = configureJsap();
        final JSAPResult config = jsap.parse(args);
        if (!config.success()) {
            helpMode(jsap, config);
            System.exit(1);
        }

        compoundFilename = config.getString("compound-file");
        filenames = config.getStringArray("filenames");

        System.out.println("Compound file: " + compoundFilename);
        System.out.print("Filenames: (");
        int i = 0;
        for (final String filename : filenames) {
            if (i++ > 0) {
                System.out.print(",");
            }
            System.out.print(filename);
        }
        System.out.println(")");
        final PROGRAM_MODE mode = programMode(config);

        if (mode == PROGRAM_MODE.HELP) {
            helpMode(jsap, config);
            System.exit(1);
        } else if (mode == PROGRAM_MODE.LIST) {
            listMode();
        } else if (mode == PROGRAM_MODE.ADD) {
            addMode();
        } else if (mode == PROGRAM_MODE.EXTRACT) {
            extractMode();
        }
    }

    private PROGRAM_MODE programMode(final JSAPResult config) {
        if (config.getBoolean("help")) {
            return PROGRAM_MODE.HELP;
        } else if (config.getBoolean("list")) {
            return PROGRAM_MODE.LIST;
        } else if (config.getBoolean("add")) {
            return PROGRAM_MODE.ADD;
        } else if (config.getBoolean("extract")) {
            return PROGRAM_MODE.EXTRACT;
        } else {
            return PROGRAM_MODE.LIST;
        }
    }

    private void helpMode(final JSAP jsap, final JSAPResult config) {
        System.err.println();

        if (config != null) {
            for (java.util.Iterator errs = config.getErrorMessageIterator();
                    errs.hasNext();) {
                System.err.println("Error: " + errs.next());
            }
        }

        System.err.println();
        System.err.println("Usage:  java " + this.getClass().getName());
        System.err.println("                " + jsap.getUsage());
        System.err.println();
        System.err.println(jsap.getHelp());
        System.exit(1);
    }

    private void listMode() throws IOException {
        CompoundFileReader compoundFileReader = null;
        try {
            compoundFileReader = getExistingReader();
            if (compoundFileReader == null) {
                return;
            }

            final Collection<CompoundDirectoryEntry> files = compoundFileReader.getDirectory();
            System.out.println("Directory of compound file");
            for (final CompoundDirectoryEntry file : files) {
                System.out.println(file.getName() + "\t\t" + file.getFileSize());
            }
        } finally {
            if (compoundFileReader != null) {
                compoundFileReader.close();
            }
        }
    }

    private void addMode() {
        System.out.println("Add mode currently unsupported.");
    }

    private void extractMode() {
        System.out.println("Extract mode currently unsupported.");
    }

    private CompoundFileReader getExistingReader() throws IOException {
        if (new File(compoundFilename).exists()) {
            return new CompoundFileReader(compoundFilename);
        } else {
            System.out.println("Specified compound file '"
                    + compoundFilename + "' does not exist.");
            return null;
        }
    }
}
