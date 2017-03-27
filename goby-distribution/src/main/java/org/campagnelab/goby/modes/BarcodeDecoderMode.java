/*
 * Copyright (C) 2009-2010 Institute for Computational Biomedicine,
 *                    Weill Medical College of Cornell University
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.campagnelab.goby.modes;

import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import org.campagnelab.goby.readers.FastXEntry;
import org.campagnelab.goby.readers.FastXReader;
import org.campagnelab.goby.reads.*;
import org.campagnelab.goby.util.barcode.BarcodeMatcher;
import org.campagnelab.goby.util.barcode.BarcodeMatcherResult;
import org.campagnelab.goby.util.barcode.PostBarcodeMatcher;
import org.campagnelab.goby.util.barcode.PreBarcodeMatcher;
import edu.cornell.med.icb.io.TSVReader;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.bytes.ByteList;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.lang.MutableString;
import it.unimi.dsi.logging.ProgressLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;

/**
 * @author Fabien Campagne
 *         Date: May 18, 2010
 *         Time: 3:10:59 PM
 */
public class BarcodeDecoderMode extends AbstractGobyMode {

    /**
     * Used to log debug and informational messages.
     */
    private static final Logger LOG = LoggerFactory.getLogger(BarcodeDecoderMode.class);

    /**
     * For logging progress.
     */
    ProgressLogger progress;

    /**
     * The mode name.
     */
    private static final String MODE_NAME = "barcode-decoder";

    /**
     * The mode description help text.
     */
    private static final String MODE_DESCRIPTION = "Process .compact-reads or fasta/fastq file and decode barcodes. " +
            "Will either produce a large compact-reads file where matching reads are copied (in this " +
            "case the barcodeIndex attribute is set appropriately on each read), or will produce a " +
            "set of compact reads files, one for each sample indicates in barcode-info. The second " +
            "option is used if no output file is provided on the command line. " +
            "This mode does not currently support paired end input when processing either .compact-reads or " +
            "fasta/fastq." +
            "In the barcode-info file, the barcodeIndex column values should " +
            "be integers from 0 to the number of entries-1 in the barcode-info file. NOTE: If processing " +
            ".compact-reads file(s) the .compact-reads read-index values will only be retained if processing " +
            "a SINGLE .compact-reads file, otherwise new read-index values will be assigned. ";
    /**
     * The compact reads file to process.
     */
    private String[] inputFilenames;
    /**
     * The filename to the tab delimited file with barcodes and sample ids.
     */
    private String barcodeInfoFilename;
    private Int2ObjectMap<String> barcodeIndexToSampleId;
    private String[] barcodes;
    private int maxMismatches;

    private String outputFilename;
    private int minimalMatchLength;
    private int trim5Prime;

    @Override
    public String getModeName() {
        return MODE_NAME;
    }

    @Override
    public String getModeDescription() {
        return MODE_DESCRIPTION;
    }

    boolean is3Prime;

    private boolean apiMode = true;

    /**
     * Quality encoding to use, only for fastq processing.
     */
    private QualityEncoding qualityEncoding = QualityEncoding.ILLUMINA;

    /**
     * Include descriptions in the compact output.
     */
    private boolean includeDescriptions = false;

    /**
     * Include identifiers in the compact output.
     */
    private boolean includeIdentifiers = false;

    /**
     * Configure.
     *
     * @param args command line arguments
     * @return this object for chaining
     * @throws java.io.IOException                    error parsing
     * @throws com.martiansoftware.jsap.JSAPException error parsing
     */
    @Override
    public AbstractCommandLineMode configure(final String[] args) throws IOException, JSAPException {
        apiMode = false;
        final JSAPResult jsapResult = parseJsapArguments(args);

        inputFilenames = jsapResult.getStringArray("input");
        outputFilename = jsapResult.getString("output");
        barcodeInfoFilename = jsapResult.getString("barcode-info");
        includeDescriptions = jsapResult.getBoolean("include-descriptions");
        includeIdentifiers = jsapResult.getBoolean("include-identifiers");
        qualityEncoding = QualityEncoding.valueOf(jsapResult.getString("quality-encoding").toUpperCase());
        trim5Prime = jsapResult.getInt("trim-5-prime");
        final String extremity = jsapResult.getString("extremity");
        if ("3_PRIME".equalsIgnoreCase(extremity)) {
            is3Prime = true;
        } else if ("5_PRIME".equalsIgnoreCase(extremity)) {
            is3Prime = false;
        } else {
            System.err.println("argument --extremity can only be 3_PRIME or 5_PRIME");
            System.exit(1);
        }
        minimalMatchLength = jsapResult.getInt("minimal-match-length");
        maxMismatches = jsapResult.getInt("max-mismatches");

        return this;
    }

    @Override
    public void execute() throws IOException {
        progress = new ProgressLogger(LOG);
        loadBarcodeInfo(barcodeInfoFilename);


        ReadsWriter singleWriter = null;
        final ReadsWriterImpl[] writers = new ReadsWriterImpl[barcodeIndexToSampleId.size()];
        if (outputFilename == null) {
            for (int i = 0; i < writers.length; i++) {
                final String s = barcodeIndexToSampleId.get(i);
                if (s != null) {
                    writers[i] = new ReadsWriterImpl(new FileOutputStream(s.trim() + ".compact-reads"));
                } else {
                    System.out.println("Cannot find information for barcode index " + i);
                }
            }
        } else {
            singleWriter = new ReadsWriterImpl(new FileOutputStream(outputFilename));
        }

        final BarcodeMatcher matcher = is3Prime ? new PostBarcodeMatcher(barcodes, minimalMatchLength, maxMismatches) :
                new PreBarcodeMatcher(barcodes, minimalMatchLength, maxMismatches);

        final MutableString sequence = new MutableString();
        final ByteList qualitiesNoBarcode = new ByteArrayList();
        try {
            int countMatched = 0;
            int countNoMatch = 0;
            int countAmbiguous = 0;
            final boolean retainReadIndex = inputFilenames.length == 1;
            progress.displayFreeMemory = true;
            for (final String inputReadsFilename : inputFilenames) {
                if (inputReadsFilename.toLowerCase().endsWith(".compact-reads")) {
                    progress.start("Progressing .compact-reads file " + inputReadsFilename);
                    for (final Reads.ReadEntry readEntry : new ReadsReader(inputReadsFilename)) {
                        ReadsReader.decodeSequence(readEntry, sequence);
                        MutableString trimmed = trim5Prime > 0 ? sequence.substring(trim5Prime, sequence.length() - trim5Prime) : sequence;

                        final BarcodeMatcherResult match = matcher.matchSequence(trimmed);
                        if (match != null) {
                            // remove the barcode from the sequence:
                            final int barcodeIndex = match.getBarcodeIndex();
                            if (match.isAmbiguous()) {
                                ++countAmbiguous;
                            }
                            final ReadsWriter writer = outputFilename == null ? writers[barcodeIndex] : singleWriter;
                            assert writer != null : "writer cannot be null. Make sure barcode indices start at zero.";
                            writer.setSequence(match.sequenceOf(trimmed));
                            writer.setBarcodeIndex(barcodeIndex);

                            if (readEntry.hasDescription()) {
                                writer.setDescription(readEntry.getDescription());
                            }
                            if (readEntry.hasReadIdentifier()) {
                                writer.setIdentifier(readEntry.getReadIdentifier());
                            }
                            if (readEntry.hasQualityScores()) {
                                qualitiesNoBarcode.clear();
                                qualitiesNoBarcode.addElements(0, readEntry.getQualityScores().toByteArray(),
                                        match.getSequenceStartPosition() + trim5Prime,
                                        match.getSequenceStartPosition() + trim5Prime + match.getSequenceLength() - match.getBarcodeMatchLength());
                                writer.setQualityScores(qualitiesNoBarcode.toByteArray());
                            }
                            if (readEntry.hasSequencePair()) {
                                ReadsReader.decodeSequence(readEntry, sequence, true);
                                writer.setPairSequence(sequence);
                                if (readEntry.hasQualityScoresPair()) {
                                    final byte[] qualityScores = readEntry.getQualityScoresPair().toByteArray();
                                    writer.setQualityScoresPair(qualityScores);
                                    assert sequence.length() == qualityScores.length : "pair sequence lenght must match pair quality score length";
                                }
                            }
                            if (retainReadIndex) {
                                writer.appendEntry(readEntry.getReadIndex());
                            } else {
                                writer.appendEntry();
                            }
                            ++countMatched;
                        } else {
                            ++countNoMatch;
                        }
                        progress.lightUpdate();
                    }
                    progress.stop();
                } else {
                    final FastXReader fastxReader = new FastXReader(inputReadsFilename);
                    fastxReader.setUseCasavaQualityFilter(true);
                    progress.start("Progressing fasta/fastq file " + inputReadsFilename);
                    for (final FastXEntry readEntry : new FastXReader(inputReadsFilename)) {
                        final BarcodeMatcherResult match = matcher.matchSequence(readEntry.getSequence());
                        if (match != null) {
                            // remove the barcode from the sequence:
                            final int barcodeIndex = match.getBarcodeIndex();
                            if (match.isAmbiguous()) {
                                ++countAmbiguous;
                            }
                            final ReadsWriter writer = outputFilename == null ? writers[barcodeIndex] : singleWriter;
                            writer.setSequence(match.sequenceOf(readEntry.getSequence()));
                            writer.setBarcodeIndex(barcodeIndex);

                            if (includeDescriptions) {
                                writer.setDescription(readEntry.getEntryHeader());
                            }
                            if (includeIdentifiers) {
                                final MutableString description = readEntry.getEntryHeader();
                                final String identifier = description.toString().split("[\\s]")[0];
                                writer.setIdentifier(identifier);
                            }
                            if (readEntry.getQuality().length() > 0) {
                                writer.setQualityScores(FastaToCompactMode.convertQualityScores(qualityEncoding,
                                        readEntry.getQuality().subSequence(
                                                match.getSequenceStartPosition(),
                                                match.getSequenceStartPosition() + match.getSequenceLength()),
                                        false, apiMode));
                            }
                            writer.appendEntry();
                            ++countMatched;
                        } else {
                            ++countNoMatch;
                        }
                        progress.lightUpdate();
                    }
                    progress.stop();
                }
            }
            System.out.format("barcode found in %g %% of the reads %n", percent(countMatched, countMatched + countNoMatch));
            System.out.format("Found %g %% ambiguous matches %n", percent(countAmbiguous, countMatched));
        } finally {
            for (int i = 0; i < writers.length; i++) {
                if (writers[i] != null) {
                    writers[i].close();
                }
            }
            if (outputFilename != null) {
                singleWriter.close();
            }

        }

    }

    private double percent(final int countMatched, final int total) {
        return (double) countMatched / (double) total * 100d;
    }

    private void loadBarcodeInfo(final String barcodeInfoFilename) {
        try {
            final ObjectArrayList<String> barcodes = new ObjectArrayList<String>();
            barcodeIndexToSampleId = new Int2ObjectOpenHashMap<String>();
            final TSVReader reader = new TSVReader(new FileReader(barcodeInfoFilename), '\t');
            while (reader.hasNext()) {
                reader.next();
                final String sampleId = reader.getString();
                final int barcodeIndex = reader.getInt();
                final String barcode = reader.getString();
                barcodes.add(barcode);
                barcodeIndexToSampleId.put(barcodeIndex, sampleId);
            }
            this.barcodes = barcodes.toArray(new String[barcodes.size()]);
        } catch (Exception e) {
            System.err.println("Cannot load barcode information from file " + barcodeInfoFilename);
            System.exit(1);
        }
    }

    /**
     * Get the quality encoding scale used for the input fastq file.
     *
     * @return the quality encoding scale used for the input fastq file
     */
    public QualityEncoding getQualityEncoding() {
        return qualityEncoding;
    }

    /**
     * Set the quality encoding scale to be used for the input fastq file.
     * Acceptable values are "Illumina", "Sanger", and "Solexa".
     *
     * @param qualityEncoding the quality encoding scale to be used for the input fastq file
     */
    public void setQualityEncoding(final QualityEncoding qualityEncoding) {
        this.qualityEncoding = qualityEncoding;
    }

    /**
     * Set the quality encoding scale to be used for the input fastq file.
     * Acceptable values are "Illumina", "Sanger", and "Solexa".
     *
     * @param qualityEncoding the quality encoding scale to be used for the input fastq file
     */
    public void setQualityEncoding(final String qualityEncoding) {
        this.qualityEncoding = QualityEncoding.valueOf(qualityEncoding.toUpperCase());
    }

    /**
     * Main method.
     *
     * @param args command line args.
     * @throws com.martiansoftware.jsap.JSAPException error parsing
     * @throws java.io.IOException                    error parsing or executing.
     */
    public static void main
    (
            final String[] args) throws JSAPException, IOException {
        new BarcodeDecoderMode().configure(args).execute();
    }
}
