package org.campagnelab.goby.baseinfo;

import edu.cornell.med.icb.util.VersionUtils;
import org.apache.commons.io.IOUtils;
import org.campagnelab.dl.varanalysis.protobuf.SegmentInformationRecords;
import org.campagnelab.goby.compression.MessageChunksWriter;
import org.campagnelab.goby.compression.SequenceSegmentInfoCollectionHandler;
import org.campagnelab.goby.util.FileExtensionHelper;
import org.campagnelab.goby.util.commits.CommitPropertyHelper;

import java.io.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;

/**
 * Writer for sequence segment information.
 *
 * @author manuele
 */
public class SequenceSegmentInformationWriter implements Closeable {

    private final SegmentInformationRecords.SegmentInformationCollection.Builder collectionBuilder;
    private String basename;
    private final MessageChunksWriter messageChunkWriter;
    private long recordIndex;
    private Properties customProperties = new Properties();
    private long maxNumOfBases = 0L;
    private long maxNumOfLabels = 0L;
    private long maxNumOfFeatures = 0L;


    public SequenceSegmentInformationWriter(final String outputFile) throws FileNotFoundException {
        this(new FileOutputStream(BasenameUtils.getBasename(outputFile, FileExtensionHelper.COMPACT_SEQUENCE_SEGMENT_INFORMATION) + ".ssi"));
        this.basename = BasenameUtils.getBasename(outputFile, FileExtensionHelper.COMPACT_SEQUENCE_SEGMENT_INFORMATION);
    }


    public SequenceSegmentInformationWriter(final OutputStream output) {
        this.collectionBuilder = SegmentInformationRecords.SegmentInformationCollection.newBuilder();
        messageChunkWriter = new MessageChunksWriter(output);
        messageChunkWriter.setParser(new SequenceSegmentInfoCollectionHandler());
        setNumEntriesPerChunk(1000);
        recordIndex = 0;

    }


    /**
     * Define custom properties to be written with in the .sbip along with all properties handled by the
     * writer. The method ignores a null customProperties argument.
     *
     * @param customProperties
     */
    public void setCustomProperties(Properties customProperties) {
        if (customProperties != null) {
            this.customProperties = customProperties;
        }
    }

    /**
     * Adds a custom property to the properties object. Returns false if the properties object was null.
     *
     * @param key
     * @param value
     */
    public void addCustomProperties(String key, String value) {
        this.customProperties.setProperty(key, value);
    }

    /**
     * Append the properties of this reader to the writer properties.
     * @param p
     */
    public void appendProperties(Properties p) {
        for (Enumeration<Object> keys = p.keys(); keys.hasMoreElements();) {
            final String key = (String) keys.nextElement();
            this.customProperties.setProperty(key, (String) p.get(key));
        }

    }

    public Properties getCustomProperties() {
        return customProperties;
    }

    /**
     * Write the sbip file with the  information obtained by merging properties.
     *
     * @param basename   basename of the .sbi file.
     * @param properties List of properties that should be written.
     * @throws FileNotFoundException
     */
    public static void writeProperties(String basename, List<Properties> properties) throws FileNotFoundException {
        FileOutputStream out = new FileOutputStream(basename + ".ssip");
        Properties merged = new Properties();
        if (properties.size() >= 1) merged.putAll(properties.get(0));
        merged.setProperty("goby.version", VersionUtils.getImplementationVersion(SequenceBaseInformationWriter.class));
        CommitPropertyHelper.appendCommitInfo(SequenceBaseInformationWriter.class, "/GOBY_COMMIT.properties", merged);
        merged.save(out, basename);
        IOUtils.closeQuietly(out);
    }

    public static void writeProperties(String basename, long numberOfRecords, long maxNumOfLabels, long maxNumOfBases,
                                       long maxNumOfFeatures) throws FileNotFoundException {
        Properties p = new Properties();
        writeProperties(basename, numberOfRecords, maxNumOfLabels, maxNumOfBases, maxNumOfFeatures, p);
    }

    private static void writeProperties(String basename, long numberOfSegments,
                                        long maxNumOfLabels, long maxNumOfBases,
                                        long maxNumOfFeatures, Properties p) throws FileNotFoundException {
        p.setProperty("numSegments", Long.toString(numberOfSegments));
        p.setProperty("maxNumOfLabels", Long.toString(maxNumOfLabels));
        p.setProperty("maxNumOfBases", Long.toString(maxNumOfBases));
        p.setProperty("maxNumOfFeatures", Long.toString(maxNumOfFeatures));
        List<Properties> lp = new ArrayList<>();
        lp.add(p);
        writeProperties(basename, lp);
    }

    public void setEntryBases(long numOfBases) {
        if (this.maxNumOfBases < numOfBases)
            this.maxNumOfBases = numOfBases;
    }

    public void setEntryLabels(long numOfLabels) {
        if (this.maxNumOfLabels < numOfLabels)
            this.maxNumOfLabels = numOfLabels;
    }

    public void setEntryFeatures(long numOfFeatures) {
        if (this.maxNumOfFeatures < numOfFeatures)
            this.maxNumOfFeatures = numOfFeatures;
    }

    /**
     * Append a base information record.
     *
     * @throws IOException If an error occurs while writing the file.
     */

    public void appendEntry(SegmentInformationRecords.SegmentInformation segmentInformation) {
        collectionBuilder.addRecords(segmentInformation);
        setEntryBases(segmentInformation.getLength());
        if (segmentInformation.getSampleCount() > 0 && segmentInformation.getSample(0).getBaseCount() > 0) {
            setEntryLabels(segmentInformation.getSample(0).getBase(0).getLabelsCount());
            setEntryFeatures(segmentInformation.getSample(0).getBase(0).getFeaturesCount());
        }

        messageChunkWriter.writeAsNeeded(collectionBuilder);
        recordIndex += 1;
    }


    public void setNumEntriesPerChunk(final int numEntriesPerChunk) {
        messageChunkWriter.setNumEntriesPerChunk(numEntriesPerChunk);
    }


    public synchronized void printStats(final PrintStream out) {
        messageChunkWriter.printStats(out);
        out.println("Number of bytes/baseInformation record " +
                (messageChunkWriter.getTotalBytesWritten()) / (double) recordIndex);
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
        messageChunkWriter.close(collectionBuilder);
        Properties p = getCustomProperties();
        writeProperties(basename, recordIndex, maxNumOfLabels, maxNumOfBases, maxNumOfFeatures, p);
    }


}
