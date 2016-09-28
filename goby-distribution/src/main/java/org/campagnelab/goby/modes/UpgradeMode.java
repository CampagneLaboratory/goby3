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

package org.campagnelab.goby.modes;

import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import org.campagnelab.goby.GobyVersion;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.logging.ProgressLogger;
import org.campagnelab.goby.alignments.*;
import org.campagnelab.goby.modes.core.UpgradeModeCore;

import java.io.IOException;

/**
 * Upgrade goby files to a new version of Goby. We try to devise Goby format to avoid upgrade steps, but sometimes
 * upgrading the data structures cannot be avoided (e.g., when we fix bugs that existed in earlier versions).
 * This tool converts data structures to the latest Goby format.
 *
 * @author Fabien Campagne
 */
public class UpgradeMode extends AbstractGobyMode {
    /**
     * The mode name.
     */
    private static final String MODE_NAME = "upgrade";

    /**
     * The mode description help text.
     */
    private static final String MODE_DESCRIPTION = "Upgrade goby files to a new version of Goby. We try to devise Goby format to avoid upgrade steps, but sometimes upgrading the data structures cannot be avoided (e.g., when we fix bugs that existed in earlier versions). This tool converts data structures to the latest Goby format.";


    UpgradeModeCore delegate = new UpgradeModeCore();


    @Override
    public String getModeName() {
        return MODE_NAME;
    }

    @Override
    public String getModeDescription() {
        return MODE_DESCRIPTION;
    }


    /**
     * Configure.
     *
     * @param args command line arguments
     * @return this object for chaining
     * @throws java.io.IOException error parsing
     * @throws com.martiansoftware.jsap.JSAPException
     *                             error parsing
     */
    @Override
    public AbstractCommandLineMode configure(final String[] args)
            throws IOException, JSAPException {
        final JSAPResult jsapResult = parseJsapArguments(args);

        final String[] inputFiles = jsapResult.getStringArray("input");
        delegate.setBasenames(AlignmentReaderImpl.getBasenames(inputFiles));
        //   check = jsapResult.getBoolean("check");
        return this;


    }

    public void execute() throws IOException {
        delegate.execute();
    }

    /**
     * Upgrade a Goby alignment as needed.
     *
     * @param basename Basename of the alignment.
     */
    public void upgrade(String basename) {
        delegate.upgrade(basename);
    }

    public void check(String basename) {
        delegate.check(basename);
    }

    /**
     * Main method.
     *
     * @param args command line args.
     * @throws com.martiansoftware.jsap.JSAPException
     *                             error parsing
     * @throws java.io.IOException error parsing or executing.
     */

    public static void main(final String[] args) throws JSAPException, IOException {
        new UpgradeMode().configure(args).execute();
    }

    public void setSilent(boolean silent) {
        delegate.setSilent(silent);
    }
}