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

package org.campagnelab.goby.baseinfo;

import org.apache.commons.io.IOUtils;
import org.campagnelab.goby.compression.ChunkCodec;
import org.campagnelab.goby.compression.FastBufferedMessageChunksReader;
import org.campagnelab.goby.compression.MessageChunksReader;
import org.campagnelab.goby.compression.SequenceBaseInfoCollectionHandler;
import org.campagnelab.goby.exception.GobyRuntimeException;
import org.campagnelab.goby.reads.ReadCodec;
import org.campagnelab.goby.util.FileExtensionHelper;
import it.unimi.dsi.fastutil.io.FastBufferedInputStream;
import org.apache.commons.io.FileUtils;
import org.campagnelab.dl.varanalysis.protobuf.BaseInformationRecords;

import java.io.*;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Consumer;

/**
 * Reads sequence base information files produced by the SBI output format of the discover sequence variation mode.
 *
 * @author Fabien Campagne
 *         Date: Aug 27, 2016
 */
public class SequenceBaseInformationReader implements Iterator<BaseInformationRecords.BaseInformation>,
        Iterable<BaseInformationRecords.BaseInformation>,
        Closeable {


    private MessageChunksReader reader;
    private String basename;
    private String sbiPath;
    private BaseInformationRecords.BaseInformationCollection collection;
    private final Properties properties = new Properties();
    private int recordLoadedSoFar;
    private long totalRecords;

    /**
     * Return the properties defined in the .bip file.
     *
     * @return
     */
    public Properties getProperties() {
        return properties;
    }

    /**
     * Initialize the reader.
     *
     * @param path Path to the input file
     * @throws IOException If an error occurs reading the input
     */
    public SequenceBaseInformationReader(final String path) throws IOException {
        this(BasenameUtils.getBasename(path, FileExtensionHelper.COMPACT_SEQUENCE_BASE_INFORMATION),
                FileUtils.openInputStream(new File(BasenameUtils.getBasename(path,FileExtensionHelper.COMPACT_SEQUENCE_BASE_INFORMATION) + ".sbi")));
    }

    /**
     * Initialize the reader.
     *
     * @param file The input file
     * @throws IOException If an error occurs reading the input
     */
    public SequenceBaseInformationReader(final File file) throws IOException {
        this(BasenameUtils.getBasename(file.getCanonicalPath(),FileExtensionHelper.COMPACT_SEQUENCE_BASE_INFORMATION),
                FileUtils.openInputStream(file));
    }

    @Override
    public void forEach(Consumer<? super BaseInformationRecords.BaseInformation> action) {
        Objects.requireNonNull(action);
        while (this.hasNext()) {
            BaseInformationRecords.BaseInformation next = this.next();
            action.accept(next);
        }
    }

    /**
     * Initialize the reader.
     *
     * @param basename
     * @param stream   Stream over the input
     */
    public SequenceBaseInformationReader(String basename, final InputStream stream) {
        super();
        this.basename = basename;
        this.sbiPath = basename + ".sbi";
        reset(basename, stream);
    }

    private void reset(String basename, InputStream stream) {
        reader = new MessageChunksReader(stream);
        reader.setHandler(new SequenceBaseInfoCollectionHandler());
        codec = null;
        this.collection = null;
        try {
            FileInputStream propertiesStream = new FileInputStream(basename+".sbip");
            try {
                properties.load(propertiesStream);
                totalRecords = Integer.parseInt(properties.getProperty("numRecords"));
            } finally {
                IOUtils.closeQuietly(propertiesStream);
            }
        } catch (IOException e) {
            throw new RuntimeException("Unable to load properties for " + basename, e);
        }
    }

    /**
     * Gets the number of records read so far.
     *
     * @return records loaded
     */
    public long getRecordsLoadedSoFar() {
        return this.recordLoadedSoFar;
    }

    /**
     * Gets the total number of records.
     *
     * @return total records
     */
    public long getTotalRecords() {
        return this.totalRecords;
    }

    /**
     * Initialize the reader to read a segment of the input. Sequences represented by a
     * collection which starts between the input position start and end will be returned
     * upon subsequent calls to {@link #hasNext()} and {@link #next()}.
     *
     * @param start Start offset in the input file
     * @param end   End offset in the input file
     * @param path  Path to the input file
     * @throws IOException If an error occurs reading the input
     */
    public SequenceBaseInformationReader(final long start, final long end, final String path) throws IOException {
        this(start, end, new FastBufferedInputStream(FileUtils.openInputStream(new File(path))));
    }

    /**
     * Initialize the reader to read a segment of the input. Sequences represented by a
     * collection which starts between the input position start and end will be returned
     * upon subsequent calls to {@link #hasNext()} and {@link #next()}.
     *
     * @param start  Start offset in the input file
     * @param end    End offset in the input file
     * @param stream Stream over the input file
     * @throws IOException If an error occurs reading the input.
     */
    public SequenceBaseInformationReader(final long start, final long end, final FastBufferedInputStream stream)
            throws IOException {
        super();
        reader = new FastBufferedMessageChunksReader(start, end, stream);
        reader.setHandler(new SequenceBaseInfoCollectionHandler());
    }

    /**
     * Returns true if the input has more sequences.
     *
     * @return true if the input has more sequences, false otherwise.
     */
    public boolean hasNext() {
        final boolean hasNext =
                reader.hasNext(collection, collection != null ? collection.getRecordsCount() : 0);
        final byte[] compressedBytes = reader.getCompressedBytes();
        final ChunkCodec chunkCodec = reader.getChunkCodec();
        try {
            if (compressedBytes != null) {

                collection = (BaseInformationRecords.BaseInformationCollection) chunkCodec.decode(compressedBytes);
                if (codec != null) {
                    codec.newChunk();
                }
                if (collection == null || collection.getRecordsCount() == 0) {
                    return false;
                }
            }
        } catch (IOException e) {
            throw new GobyRuntimeException(e);
        }
        return hasNext;
    }

    /**
     * Returns the next read entry from the input stream.
     *
     * @return the next read entry from the input stream.
     */
    public final BaseInformationRecords.BaseInformation next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        final BaseInformationRecords.BaseInformation record = collection.getRecords(reader.incrementEntryIndex());
        recordLoadedSoFar += 1;
        return record;
    }

    /**
     * Optional codec.
     */
    private ReadCodec codec;

    boolean first = true;

    /**
     * This operation is not supported.
     */
    public void remove() {
        throw new UnsupportedOperationException("Cannot remove from a reader.");
    }


    /**
     * Make the reader "iterable" for java "for each" loops and such. If an iteration was in progress, it will be
     * canceled. The new iteration starts from the beginning of the iterator.
     *
     * @return this object
     */
    public Iterator<BaseInformationRecords.BaseInformation> iterator() {
        try {
            IOUtils.closeQuietly(reader);
            reset(basename, FileUtils.openInputStream(new File(sbiPath)));
        } catch (IOException e) {
            throw new RuntimeException("Unable to reset iterator", e);
        }
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public void close() throws IOException {
        reader.close();
    }

    /**
     * Return the basename corresponding to the input reads filename.  Note
     * that if the filename does have the extension known to be a compact read
     * the returned value is the original filename
     *
     * @param filename The name of the file to get the basename for
     * @return basename for the alignment file
     */
    public static String getBasename(final String filename) {
        return BasenameUtils.getBasename(filename,FileExtensionHelper.COMPACT_SEQUENCE_BASE_INFORMATION);
    }

    /**
     * Return the basenames corresponding to the input filenames. Less basename than filenames
     * may be returned (if several filenames reduce to the same baseline after removing
     * the extension).
     *
     * @param filenames The names of the files to get the basnames for
     * @return An array of basenames
     */
    public static String[] getBasenames(final String... filenames) {
        return BasenameUtils.getBasenames(FileExtensionHelper.COMPACT_SEQUENCE_BASE_INFORMATION, filenames);
    }

}
