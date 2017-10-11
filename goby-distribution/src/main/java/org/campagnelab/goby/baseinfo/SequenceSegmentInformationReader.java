package org.campagnelab.goby.baseinfo;

import org.campagnelab.dl.varanalysis.protobuf.SegmentInformationRecords;
import org.campagnelab.goby.compression.MessageChunksReader;

import java.io.Closeable;
import java.io.IOException;
import java.util.Iterator;
import java.util.Properties;

/**
 *  Reads sequence segment information files produce by the SBIToSSI converter.
 *
 * @author manuele
 */
public class SequenceSegmentInformationReader implements Iterator<SegmentInformationRecords.SegmentInformation>,
        Iterable<SegmentInformationRecords.SegmentInformation>,
        Closeable {


    private MessageChunksReader reader;
    private String basename;
    private String sbiPath;
    private SegmentInformationRecords.SegmentInformationCollection collection;
    private final Properties properties = new Properties();
    private int recordLoadedSoFar;
    private long totalRecords;

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

    }

    /**
     * Returns an iterator over elements of type {@code T}.
     *
     * @return an Iterator.
     */
    @Override
    public Iterator<SegmentInformationRecords.SegmentInformation> iterator() {
        return null;
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
        return false;
    }

    /**
     * Returns the next element in the iteration.
     *
     * @return the next element in the iteration
     * @throws java.util.NoSuchElementException if the iteration has no more elements
     */
    @Override
    public SegmentInformationRecords.SegmentInformation next() {
        return null;
    }
}
