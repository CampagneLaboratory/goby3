package org.campagnelab.goby.baseinfo;

import it.unimi.dsi.fastutil.io.FastBufferedInputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.campagnelab.dl.varanalysis.protobuf.SegmentInformationRecords;
import org.campagnelab.goby.compression.*;
import org.campagnelab.goby.exception.GobyRuntimeException;
import org.campagnelab.goby.reads.ReadCodec;
import org.campagnelab.goby.util.FileExtensionHelper;

import java.io.*;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Properties;

/**
 * Reads sequence segment information files produce by the SBIToSSI converter.
 *
 * @author manuele
 */
public class SequenceSegmentInformationReader implements Iterator<SegmentInformationRecords.SegmentInformation>,
        Iterable<SegmentInformationRecords.SegmentInformation>,
        Closeable {


    private MessageChunksReader reader;
    private String basename;
    private String ssiPath;
    private SegmentInformationRecords.SegmentInformationCollection collection;
    private final Properties properties = new Properties();
    private int recordLoadedSoFar;
    private long totalRecords;
    /**
     * Optional codec.
     */
    private ReadCodec codec;

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
    public SequenceSegmentInformationReader(final String path) throws IOException {
        this(BasenameUtils.getBasename(path, FileExtensionHelper.COMPACT_SEQUENCE_BASE_INFORMATION),
                FileUtils.openInputStream(new File(BasenameUtils.getBasename(path, FileExtensionHelper.COMPACT_SEQUENCE_BASE_INFORMATION) + ".ssi")));
    }

    /**
     * Initialize the reader.
     *
     * @param file The input file
     * @throws IOException If an error occurs reading the input
     */
    public SequenceSegmentInformationReader(final File file) throws IOException {
        this(BasenameUtils.getBasename(file.getCanonicalPath(), FileExtensionHelper.COMPACT_SEQUENCE_BASE_INFORMATION),
                FileUtils.openInputStream(file));
    }

    /**
     * Initialize the reader.
     *
     * @param basename
     * @param stream   Stream over the input
     */
    public SequenceSegmentInformationReader(String basename, final InputStream stream) {
        super();
        this.basename = basename;
        this.ssiPath = basename + ".ssi";
        reset(basename, stream);
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
    public SequenceSegmentInformationReader(final long start, final long end, final String path) throws IOException {
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
    public SequenceSegmentInformationReader(final long start, final long end, final FastBufferedInputStream stream)
            throws IOException {
        super();
        reader = new FastBufferedMessageChunksReader(start, end, stream);
        reader.setHandler(new SequenceSegmentInfoCollectionHandler());
    }

    private void reset(String basename, InputStream stream) {
        reader = new MessageChunksReader(stream);
        reader.setHandler(new SequenceSegmentInfoCollectionHandler());
        codec = null;
        this.collection = null;
        try {
            FileInputStream propertiesStream = new FileInputStream(basename+".ssip");
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
     * Closes this stream and releases any system resources associated
     * with it. If the stream is already closed then invoking this
     * method has no effect.
     * <p>
     * <p> As noted in {@link AutoCloseable#close()}, cases where the
     * close may fail require careful attention. It is strongly advised
     * to relinquish the underlying resources and to internally
     * <em>mark</em> the {@code Closeable} as closed, prior to throwing
     * the {@code IOException}.
     *
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void close() throws IOException {
        reader.close();

    }

    /**
     * Returns an iterator over elements of type {@code T}.
     *
     * @return an Iterator.
     */
    @Override
    public Iterator<SegmentInformationRecords.SegmentInformation> iterator() {
        try {
            IOUtils.closeQuietly(reader);
            reset(basename, FileUtils.openInputStream(new File(ssiPath)));
        } catch (IOException e) {
            throw new RuntimeException("Unable to reset iterator", e);
        }
        return this;
    }

    /**
     * Returns {@code true} if the iteration has more elements.
     * (In other words, returns {@code true} if {@link #next} would
     * return an element rather than throwing an exception.)
     *
     * @return {@code true} if the iteration has more elements
     */
    @Override
    public boolean hasNext() {
        final boolean hasNext =
                reader.hasNext(collection, collection != null ? collection.getRecordsCount() : 0);
        final byte[] compressedBytes = reader.getCompressedBytes();
        final ChunkCodec chunkCodec = reader.getChunkCodec();
        try {
            if (compressedBytes != null) {
                collection = (SegmentInformationRecords.SegmentInformationCollection) chunkCodec.decode(compressedBytes);
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
        return hasNext;    }

    /**
     * Returns the next segment in the iteration.
     *
     * @return the next segment in the iteration
     * @throws java.util.NoSuchElementException if the iteration has no more segments
     */
    @Override
    public SegmentInformationRecords.SegmentInformation next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        SegmentInformationRecords.SegmentInformation record = collection.getRecords(reader.incrementEntryIndex());
        recordLoadedSoFar += 1;
        return record;
    }
}
