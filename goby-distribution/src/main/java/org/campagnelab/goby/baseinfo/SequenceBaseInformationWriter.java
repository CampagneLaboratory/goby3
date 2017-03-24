package org.campagnelab.goby.baseinfo;
/*
 * Copyright (C) 2009-2012 Institute for Computational Biomedicine,
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

import edu.cornell.med.icb.util.VersionUtils;
import org.apache.commons.io.IOUtils;
import org.campagnelab.dl.varanalysis.protobuf.BaseInformationRecords.BaseInformation;
import org.campagnelab.dl.varanalysis.protobuf.BaseInformationRecords.BaseInformationCollection;
import org.campagnelab.goby.compression.MessageChunksWriter;
import org.campagnelab.goby.compression.SequenceBaseInfoCollectionHandler;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Write for sequence base information.
 *
 * @author Fabien Campagne
 *         Created by fac2003 on 8/27/16.
 */
public class SequenceBaseInformationWriter implements Closeable {

    private final BaseInformationCollection.Builder collectionBuilder;
    private String basename;
    private final MessageChunksWriter messageChunkWriter;
    private long recordIndex;
    public List<StatAccumulator> ACCUMULATORS = new ArrayList<>();

    {
        ACCUMULATORS.add(new ConstantAccumulator("stats.genomicContextSize", baseInformation -> (float) baseInformation.getGenomicSequenceContext().length()));
        ACCUMULATORS.add(new StatAccumulatorReadMappingQuality());
        ACCUMULATORS.add(new StatAccumulatorNumVariationsInRead());
        ACCUMULATORS.add(new StatAccumulatorBaseQuality());
        ACCUMULATORS.add(new StatAccumulatorInsertSizes());
        ACCUMULATORS.add(new StatAccumulatorDistancesToReadVariations());
        ACCUMULATORS.add(new StatAccumulatorTargetAlignedLength());
        ACCUMULATORS.add(new StatAccumulatorQueryAlignedLength());
        ACCUMULATORS.add(new StatAccumulatorQueryPosition());
        ACCUMULATORS.add(new StatAccumulatorPairFlags());
        // NB: must modify accumulator in static methods below as well.
    }


    public SequenceBaseInformationWriter(final String basename) throws FileNotFoundException {
        this(new FileOutputStream(SequenceBaseInformationReader.getBasename(basename) + ".sbi"));
        this.basename = SequenceBaseInformationReader.getBasename(basename);
    }

    public SequenceBaseInformationWriter(final OutputStream output) {
        collectionBuilder = BaseInformationCollection.newBuilder();
        messageChunkWriter = new MessageChunksWriter(output);
        messageChunkWriter.setParser(new SequenceBaseInfoCollectionHandler());
        recordIndex = 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() throws IOException {
        messageChunkWriter.close(collectionBuilder);
        Properties p = getCustomProperties();
        for (StatAccumulator accumulator : ACCUMULATORS) {
            accumulator.setProperties(p);
        }
        writeProperties(basename, recordIndex, p);
    }

    private Properties customProperties;

    /**
     * Define custom properties to be written with in the .sbip along with all properties handled by the
     * writer.
     *
     * @param customProperties
     */
    public void setCustomProperties(Properties customProperties) {
        this.customProperties = customProperties;
    }


    /**
     * Adds a custom property to the properties object. Returns false if the properties object was null.
     *
     * @param key
     * @param value
     */
    public boolean addCustomProperties(String key, String value) {
        if (this.customProperties == null){
            return false;
        }
        this.customProperties.setProperty(key,value);
        return true;
    }

    private Properties getCustomProperties() {
        if (customProperties != null) {
            return customProperties;
        } else {
            return new Properties();
        }
    }

    /**
     * Write the sbip file with the  information obtained by merging properties.
     *
     * @param basename   basename of the .sbi file.
     * @param properties List of properties that should be written.
     * @throws FileNotFoundException
     */
    public static void writeProperties(String basename, List<Properties> properties) throws FileNotFoundException {


        FileOutputStream out = new FileOutputStream(basename + ".sbip");

        List<StatAccumulator> accumulators = new ArrayList<>();
        accumulators.add(new ConstantAccumulator("stats.genomicContextSize", baseInformation -> (float) baseInformation.getGenomicSequenceContext().length()));
        accumulators.add(new StatAccumulatorBaseQuality());
        accumulators.add(new StatAccumulatorReadMappingQuality());
        accumulators.add(new StatAccumulatorNumVariationsInRead());
        accumulators.add(new StatAccumulatorDistancesToReadVariations());
        accumulators.add(new StatAccumulatorInsertSizes());
        accumulators.add(new StatAccumulatorTargetAlignedLength());
        accumulators.add(new StatAccumulatorQueryAlignedLength());
        accumulators.add(new StatAccumulatorQueryPosition());
        accumulators.add(new StatAccumulatorPairFlags());
        // NB: Add new accumulators here as well.

        long numTotal = 0;
        for (Properties p : properties) {
            for (StatAccumulator accumulator : accumulators) {
                accumulator.mergeWith(p);
            }
            // TODO implement custom merge of values for matching property keys
            numTotal += Long.parseLong(p.get("numRecords").toString());
        }
        Properties merged = new Properties();
        // if at least one properties in list, transfer its values:

        // TODO implement custom merge of values for matching property keys (e.g., true-genotype keys)
        if (properties.size() >= 1) merged.putAll(properties.get(0));
        merged.setProperty("numRecords", Long.toString(numTotal));
        merged.setProperty("goby.version", VersionUtils.getImplementationVersion(SequenceBaseInformationWriter.class));
        // give accumulators a chance to update their stats:
        for (StatAccumulator accumulator : accumulators) {
            accumulator.setProperties(merged);
        }
        merged.save(out, basename);
        IOUtils.closeQuietly(out);
    }

    public static void writeProperties(String basename, long numberOfRecords) throws FileNotFoundException {
        Properties p = new Properties();
        writeProperties(basename, numberOfRecords, p);
    }

    private static void writeProperties(String basename, long numberOfRecords, Properties p) throws FileNotFoundException {
        p.setProperty("numRecords", Long.toString(numberOfRecords));
        List<Properties> lp = new ArrayList<>();
        lp.add(p);
        writeProperties(basename, lp);
    }

    /**
     * Append a base information record.
     *
     * @throws IOException If an error occurs while writing the file.
     */

    public synchronized void appendEntry(BaseInformation baseInfo) throws IOException {
        collectionBuilder.addRecords(baseInfo);
        for (StatAccumulator accumulator : ACCUMULATORS) {
            accumulator.observe(baseInfo);
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

}
