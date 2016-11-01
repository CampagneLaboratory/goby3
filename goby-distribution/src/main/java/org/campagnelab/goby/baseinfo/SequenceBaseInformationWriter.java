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
import org.campagnelab.dl.varanalysis.protobuf.BaseInformationRecords;
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
    private ConstantAccumulator contextSize = new ConstantAccumulator("contextSize", BaseInformationRecords.BaseInformationOrBuilder::getGenomicSequenceContext);
    private StatAccumulatorBaseQuality baseQualityStats = new StatAccumulatorBaseQuality();
    private StatAccumulatorReadMappingQuality readMappingQualityStats = new StatAccumulatorReadMappingQuality();
    private StatAccumulatorNumVariationsInRead readVariationNumberStats = new StatAccumulatorNumVariationsInRead();
    private StatAccumulatorInsertSizes readInsertSizeStats = new StatAccumulatorInsertSizes();

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
        Properties p = new Properties();
        contextSize.setProperties(p,Integer -> Integer.toString());
        baseQualityStats.setProperties(p);
        readMappingQualityStats.setProperties(p);
        readVariationNumberStats.setProperties(p);
        writeProperties(basename, recordIndex,p);
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
        ConstantAccumulator cs = new ConstantAccumulator("contextSize", BaseInformationRecords.BaseInformationOrBuilder::getGenomicSequenceContext);
        StatAccumulatorBaseQuality bq = new StatAccumulatorBaseQuality();
        StatAccumulatorReadMappingQuality rmq = new StatAccumulatorReadMappingQuality();
        StatAccumulatorNumVariationsInRead nvir = new StatAccumulatorNumVariationsInRead();
        StatAccumulatorInsertSizes is = new StatAccumulatorInsertSizes();
        long numTotal = 0;
        for (Properties p : properties) {
            try {
                cs.mergeWith(p,String -> Integer.parseInt(String));
            } catch (IOException e) {
                System.err.println("unparseable Genomic Context value in sbi properties file.");
                e.printStackTrace();
            }
            bq.mergeWith(p);
            rmq.mergeWith(p);
            nvir.mergeWith(p);
            is.mergeWith(p);

            numTotal += Long.parseLong(p.get("numRecords").toString());
        }
        Properties merged = new Properties();
        merged.setProperty("numRecords", Long.toString(numTotal));
        merged.setProperty("goby.version", VersionUtils.getImplementationVersion(SequenceBaseInformationWriter.class));
        bq.setProperties(merged);
        rmq.setProperties(merged);
        nvir.setProperties(merged);
        is.setProperties(merged);
        cs.setProperties(merged,Integer -> Integer.toString());

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
        contextSize.observe(baseInfo);
        baseQualityStats.observe(baseInfo);
        readMappingQualityStats.observe(baseInfo);
        readVariationNumberStats.observe(baseInfo);
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
