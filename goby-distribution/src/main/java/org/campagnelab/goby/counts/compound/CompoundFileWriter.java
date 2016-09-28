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

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Write a compound file. Only one thread should be writing to the compound
 * file at a time. NOT THREAD SAFE!
 * TODO: * Add a semaphore and make it thread safe?
 * TODO: * Utility to copy a set of files to a compound file, and extract a set of files
 * TODO: from a compound file
 * TODO: Work on reverting back to thread safe version!
 * @author Kevin Dorff
 */
public class CompoundFileWriter implements Closeable {

    /**
     * Used to log debug and informational messages.
     */
    private static final Log LOG = LogFactory.getLog(CompoundFileWriter.class);

    /**
     * The stream we are writing to.
     */
    private RandomAccessFile stream;

    /**
     * The current total number of files in this compound file.
     */
    private long totalNumberOfFiles;

    /**
     * This state denotes that a file in the compound file is normal
     * (i.e., NOT deleted).
     */
    public static final int FILE_STATE_NORMAL = 0;

    /**
     * This state denotes that a file in the compound file is deleted.
     */
    public static final int FILE_STATE_DELETED = 1;

    /**
     * A file reader, to scan the file at startup, etc.
     */
    private final CompoundFileReader compoundFileReader;

    /**
     * The filename of the compound file.
     */
    private final String filename;

    /**
     * The filename of the compound file.
     */
    private CompoundDirectoryEntry entryBeingAdded;

    /**
     * Create (if it doesn't exist) or append to (if it does exist)
     * a compound file.
     * @param physicalFilename the compound file to write to
     * @throws IOException problem opening the file
     */
    public CompoundFileWriter(final String physicalFilename) throws IOException {
        super();

        entryBeingAdded = null;
        this.filename = physicalFilename;
        stream = new RandomAccessFile(new File(physicalFilename), "rw");
        compoundFileReader = new CompoundFileReader(physicalFilename);
        stream.seek(0);
        if (stream.length() == 0) {
            stream.writeLong(0);
            totalNumberOfFiles = 0;
        } else {
            totalNumberOfFiles = stream.readLong();
        }
    }

    /**
     * Get the compoundFileReader associated with this compoundFileWriter.
     * @return the compoundFileReader associated with this compoundFileWriter.
     */
    public CompoundFileReader getCompoundFileReader() {
        return compoundFileReader;
    }

    /**
     * Add a file to the compound file. This needs to be "completed"
     * by calling CompoundDataOutput.close() before another
     * addFile() or deleteFile() can be called.
     * @param name the internal filename (any string is valid)
     * @throws IOException problem adding a file
     * @return a CompoundDataOutput which can be used to write the contents
     * of the file
     */
    public CompoundDataOutput addFile(final String name) throws IOException {
        if (stream == null) {
            throw new IllegalStateException("CompoundFileWriter is not open.");
        }

        if (StringUtils.isBlank(name)) {
            throw new IllegalArgumentException("The name specified was null or empty.");
        }

        //
        // Semaphore obtain logic progbably here
        //

        if (entryBeingAdded != null) {
            throw new IllegalStateException("addFile() called during before close() "
                    + "called on current addFile()");
        }

        if (compoundFileReader.containsFile(name)) {
            throw new IOException("The compound file " + filename
                    + " already contains a file named " + name);
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Adding a new file named " + name);
        }
        totalNumberOfFiles++;
        if (LOG.isDebugEnabled()) {
            LOG.debug("Seeking to 0 to write new totalNumberOfFiles " + totalNumberOfFiles);
        }
        stream.seek(0);
        stream.writeLong(totalNumberOfFiles);

        if (LOG.isDebugEnabled()) {
            LOG.debug("Seeking to " + stream.length());
        }
        final long fileStartPosition = stream.length();
        stream.seek(fileStartPosition);
        stream.writeInt(FILE_STATE_NORMAL);
        stream.writeUTF(name);
        stream.writeLong(0);  // Length will go here
        final long dataStartPosition = stream.length();
        if (LOG.isDebugEnabled()) {
            LOG.debug("Data starting at " + stream.length());
        }

        entryBeingAdded = new CompoundDirectoryEntry(name, fileStartPosition, dataStartPosition);

        return new CompoundDataOutput(stream, this);
    }

    /**
     * Delete a file with the given name. Note: this doesn't free up
     * the space taken in the compound file, just the file won't be read
     * again. To write a file with the same name, the previous version must
     * be deleted.
     * @param name the name of the file to delete.
     * @throws IOException problem deleting the file
     */
    public void deleteFile(final String name) throws IOException {
        if (entryBeingAdded != null) {
            throw new IllegalStateException("deleteFile() called during before close() "
                    + "called on current addFile()");
        }

        final CompoundDirectoryEntry entry = compoundFileReader.getDirectoryEntry(name);
        if (entry != null) {
            final long position =  entry.getStartPosition();
            if (LOG.isDebugEnabled()) {
                LOG.debug("Marking file deleted at position " + position);
            }
            stream.seek(position);
            stream.writeInt(FILE_STATE_DELETED);
            compoundFileReader.removeFromDirectory(name);
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Not deleting, not in compound file");
            }
        }
    }

    /**
     * Close the CompoundFileWriter.
     * @throws IOException problem closing, likely the problem is with
     * finishAddFile(), if so the CompoundFile is probably un-usable.
     */
    public void close() throws IOException {
        if (stream != null) {
            // Final chance to close, make the exception and close
            // the file if need be
            finishAddFile();
            stream.close();
            stream = null;
        }
    }

    /**
     * Finish adding a file to the compound file.
     * @throws IOException problem finishing addFile. The CompoundFile
     * is probably un-usable.
     */
   public void finishAddFile() throws IOException {
        if (entryBeingAdded == null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("skipping finish add...");
            }
            return;
        }
        try {
            if (LOG.isDebugEnabled()) {
                LOG.debug("running finish add...");
            }
            final long dataSize = stream.length() - entryBeingAdded.getDataPosition();
            entryBeingAdded.setFileSize(dataSize);
            if (dataSize > 0) {
                // The " - 8" is because the size of the data is written as one long
                // before the actual data
                final long dataSizePosition = entryBeingAdded.getDataPosition() - 8;
                if (LOG.isDebugEnabled()) {
                    LOG.debug("++ data size was " + dataSize + " writing at position "
                            + dataSizePosition);
                }
                stream.seek(dataSizePosition);
                stream.writeLong(dataSize);
            } else {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("++ ZERO data size.");
                }
            }
            compoundFileReader.addToDirectory(entryBeingAdded);
        } finally {
            entryBeingAdded = null;
        }
    }
}
