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
import org.campagnelab.goby.modes.core.TabToColumnInfoModeCore;
import org.campagnelab.goby.readers.vcf.ColumnType;
import edu.cornell.med.icb.io.TsvToFromMap;
import edu.cornell.med.icb.iterators.TsvLineIterator;
import edu.cornell.med.icb.maps.LinkedHashToMultiTypeMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Read the data from TSV files to determine the the column types (Float/Integer/String).
 * Write a .colinfo file detailing the column names and types.
 */
public class TabToColumnInfoMode extends AbstractGobyMode {
    /**
     * Used to log debug and informational messages.
     */
    private static final Log LOG = LogFactory.getLog(TabToColumnInfoMode.class);

    /**
     * The mode name.
     */
    private static final String MODE_NAME = "tab-to-column-info";

    /**
     * The mode description help text.
     */
    private static final String MODE_DESCRIPTION = "Read the data from TSV files to determine the the column types " +
            "(Float/Integer/String). Write a .colinfo file detailing the column names and types.";

    /**
     * Delegate for core operations.
     */
    private TabToColumnInfoModeCore delegate = new TabToColumnInfoModeCore();


    /**
     * Mode name.
     * @return Mode name.
     */
    @Override
    public String getModeName() {
        return MODE_NAME;
    }

    /**
     * Mode description.
     * @return Mode description.
     */
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
        delegate.setInputFilenames(jsapResult.getStringArray("input"));
        delegate.numberOfLinesToProcess = jsapResult.getInt("number-of-lines");
        delegate.createCache = !jsapResult.getBoolean("do-not-create-cache");
        delegate.display = jsapResult.getBoolean("display");
        delegate.readFromCache = jsapResult.getBoolean("read-from-cache");
        delegate.verbose = jsapResult.getBoolean("verbose");
        delegate.setApiMode(false);
        return this;
    }

    /**
     * Add an input filename.
     *
     * @param inputFilename the input filename to add.
     */
    public void addInputFilename(final String inputFilename) {
        delegate.addInputFilename(inputFilename);
    }

    /**
     * Add an input file.
     *
     * @param inputFile the input file to add.
     */
    public void addInputFile(final File inputFile) {
        delegate.addInputFile(inputFile);
    }

    /**
     * Clear the input files list.
     */
    public void clearInputFilenames() {
        delegate.clearInputFilenames();
    }

    /**
     * Set the input filenames.
     *
     * @param inputFilenames the input filename
     */
    public void setInputFilenames(final String[] inputFilenames) {
        delegate.setInputFilenames(inputFilenames);
    }

    /**
     * Set the input filenames.
     *
     * @param inputFiles the input filename
     */
    public void setInputFiles(final File[] inputFiles) {
        delegate.setInputFiles(inputFiles);
    }

    /**
     * Get if createCache mode is enabled. If true, no output files are written. Default is false.
     * @return the value of createCache
     */
    public boolean isCreateCache() {
        return delegate.isCreateCache();
    }

    /**
     * Set if createCache mode is enabled. If true, no output files are written. Default is false.
     * @param createCache the new value of createCache
     */
    public void setCreateCache(final boolean createCache) {
        delegate.setCreateCache(createCache);
    }

    /**
     * Output the results to stdout.
     * @return if display enabled
     */
    public boolean isDisplay() {
        return delegate.isDisplay();
    }

    /**
     * Output the results to stdout.
     * @param display new value for display
     */
    public void setDisplay(final boolean display) {
        delegate.setDisplay(display);
    }

    /**
     * Get the number of input lines to read or set to <= 0 to read the entire file.
     * @return the value of numberOfLinesToProcess
     */
    public int getNumberOfLinesToProcess() {
        return delegate.getNumberOfLinesToProcess();
    }

    /**
     * Set the number of input lines to read or set to <= 0 to read the entire file.
     * @param numberOfLinesToProcess the new value of numberOfLinesToProcess
     */
    public void setNumberOfLinesToProcess(final int numberOfLinesToProcess) {
        delegate.setNumberOfLinesToProcess(numberOfLinesToProcess);
    }

    /**
     * If true, the [filename].colinfo file already exists, if this is true it will be used.
     * @return if readFromCache is enabled.
     */
    public boolean isReadFromCache() {
        return delegate.isReadFromCache();
    }

    /**
     * If true, the [filename].colinfo file already exists, if this is true it will be used.
     * @param readFromCache if readFromCache is enabled.
     */
    public void setReadFromCache(final boolean readFromCache) {
        delegate.setReadFromCache(readFromCache);
    }

    /**
     * Verbose.
     * @return verbose value
     */
    public boolean isVerbose() {
        return delegate.isVerbose();
    }

    /**
     * Verbose.
     * @param verbose new verbose value
     */
    public void setVerbose(final boolean verbose) {
        delegate.setVerbose(verbose);
    }

    /**
     * Get the map of filenames -> (columnName -> columnType map).
     * @return the map of files to column details.
     */
    public Map<String, Map<String, ColumnType>> getFilenameToDetailsMap() {
        return delegate.getFilenameToDetailsMap();
    }

    /**
     * Get a specific column details based on the order of the input filenames given.
     * @param index the index of results to retrieve
     * @return the details for the specified index
     * @throws IndexOutOfBoundsException if index provided is too large
     */
    public Map<String, ColumnType> getDetailsAtIndex(final int index) throws IndexOutOfBoundsException {
       return delegate.getDetailsAtIndex(index);
    }

    /**
     * Gather column info for provided input files.
     *
     * @throws java.io.IOException
     */
    @Override
    public void execute() throws IOException {
        delegate.execute();
    }

    /**
     * Process one file
     * @param filename the filename to process
     * @throws IOException an error processing the file.
     */
    public void processOneFile(final String filename) throws IOException {
        delegate.processOneFile(filename);
    }

    /**
     * Check the type of value, is it an Integer, Float, or a String.
     * @param value the value to check
     * @return the column type that is suitable for value
     */
    public ColumnType typeFromValue(final String value) {
        return delegate.typeFromValue(value);
    }
}
