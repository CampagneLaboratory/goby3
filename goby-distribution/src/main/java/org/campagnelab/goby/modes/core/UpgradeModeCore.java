/*
 * Copyright (C) 2009-2011 Institute for Computational Biomedicine,
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

package org.campagnelab.goby.modes.core;


import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.logging.ProgressLogger;
import org.campagnelab.goby.GobyVersion;
import org.campagnelab.goby.alignments.*;

import java.io.IOException;

/**
 * Upgrade goby files to a new version of Goby. We try to devise Goby format to avoid upgrade steps, but sometimes
 * upgrading the data structures cannot be avoided (e.g., when we fix bugs that existed in earlier versions).
 * This tool converts data structures to the latest Goby format.
 *
 * @author Fabien Campagne
 */
public class UpgradeModeCore {

    /**
     * The basename of the compact alignment.
     */
    private String[] basenames;
    private boolean silent;
    private boolean check;


    public void setBasenames(String[] basenames) {
        this.basenames = basenames;
    }

    public void execute() throws IOException {
        for (String basename : basenames) {
            upgrade(basename);
            if (check) {
                check(basename);
            }
        }
    }

    /**
     * Upgrade a Goby alignment as needed.
     *
     * @param basename Basename of the alignment.
     */
    public void upgrade(String basename) {
        try {
            AlignmentReaderImpl reader = new AlignmentReaderImpl(basename, false);
            reader.readHeader();
            String version = reader.getGobyVersion();
            if (!silent) {
                System.out.printf("processing %s with version %s %n", basename, version);
            }
            if (GobyVersion.isOlder(version, "goby_1.9.6")) {
                if (reader.isIndexed()) {
                    System.err.println("This alignment requires upgrading, please download goby 2 and use its concatenate to upgrade.");
                    System.exit(1);
                }
            }
            if (GobyVersion.isOlder(version, "goby_1.9.8.2")) {
                if (reader.isIndexed()) {
                    System.err.println("This alignment requires upgrading, please download goby 2 and use its concatenate tool to upgrade.");
                    System.exit(1);
                }
            }
        } catch (IOException e) {
            System.err.println("Could not read alignment " + basename);
            e.printStackTrace();
        }
    }

    public void check(String basename) {
        try {
            AlignmentReaderImpl reader = new AlignmentReaderImpl(basename, false);
            reader.readHeader();
            String version = reader.getGobyVersion();
            if (!silent) {
                System.out.printf("processing %s with version %s %n", basename, version);
            }
            if (GobyVersion.isMoreRecent(version, "1.9.6")) {
                if (reader.isIndexed()) {
                    ObjectList<ReferenceLocation> locations = reader.getLocations(1000);
                    System.out.println("Checking..");
                    ProgressLogger progress = new ProgressLogger();
                    progress.expectedUpdates = locations.size();
                    //  progress.priority = Level.INFO;
                    progress.start();
                    for (ReferenceLocation location : locations) {
                        Alignments.AlignmentEntry entry = reader.skipTo(location.targetIndex, location.position);
                        if (entry == null) {
                            System.err.printf("Entry must be found at position (t=%d,p=%d) %n", location.targetIndex,
                                    location.position);
                            System.exit(1);
                        }
                        if (entry.getTargetIndex() < location.targetIndex) {
                            System.err.printf("Entry must be found on reference >%d for position (t=%d,p=%d) %n",
                                    location.targetIndex, location.targetIndex,
                                    location.position);
                            System.exit(1);
                        }
                        if (entry.getPosition() < location.position) {
                            System.err.printf("Entry must be found at position >=%d for position (t=%d,p=%d) %n",
                                    location.position, entry.getTargetIndex()
                                    ,
                                    entry.getPosition());
                            System.exit(1);
                        }
                        progress.lightUpdate();
                    }
                    progress.stop();
                    System.out.printf("Checked %d skipTo calls", locations.size());
                }

            }
        } catch (IOException e) {
            System.err.println("Could not read alignment " + basename);
            e.printStackTrace();
        }
    }

    public void setSilent(boolean silent) {
        this.silent = silent;
    }
}