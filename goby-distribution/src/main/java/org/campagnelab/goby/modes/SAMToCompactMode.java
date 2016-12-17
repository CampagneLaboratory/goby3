/*
 * Copyright (C) 2009-2011 Institute for Computational Biomedicine,
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
import it.unimi.dsi.fastutil.ints.Int2ByteAVLTreeMap;
import org.campagnelab.goby.alignments.*;
import org.campagnelab.goby.alignments.perms.QueryIndexPermutation;
import org.campagnelab.goby.alignments.perms.ReadNameToIndex;
import org.campagnelab.goby.compression.MessageChunksWriter;
import org.campagnelab.goby.readers.sam.ConversionConfig;
import org.campagnelab.goby.readers.sam.SAMRecordIterable;
import org.campagnelab.goby.readers.sam.SamRecordParser;
import org.campagnelab.goby.reads.DualRandomAccessSequenceCache;
import org.campagnelab.goby.reads.QualityEncoding;
import org.campagnelab.goby.reads.RandomAccessSequenceInterface;
import org.campagnelab.goby.util.dynoptions.DynamicOptionClient;
import org.campagnelab.goby.util.dynoptions.DynamicOptionRegistry;
import org.campagnelab.goby.util.dynoptions.RegisterThis;
import edu.cornell.med.icb.identifier.IndexedIdentifier;
import it.unimi.dsi.fastutil.ints.Int2ByteMap;
import it.unimi.dsi.fastutil.ints.Int2ByteOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.lang.MutableString;
import it.unimi.dsi.logging.ProgressLogger;
import htsjdk.samtools.*;
import org.campagnelab.goby.readers.sam.ConvertSamBAMReadToGobyAlignment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Converts alignments in the SAM or BAM format to the compact alignment format.
 *
 * @author Kevin Dorff
 * @author Fabien Campagne
 */
public class SAMToCompactMode extends AbstractGobyMode {

    /**
     * Used to log debug and informational messages.
     */
    private static final Logger LOG = LoggerFactory.getLogger(SAMToCompactMode.class);

    /**
     * The mode name.
     */
    private static final String MODE_NAME = "sam-to-compact";

    /**
     * The mode description help text.
     */
    private static final String MODE_DESCRIPTION = "Converts alignments in the BAM or SAM "
            + "format to the compact alignment format (new version that uses SamRecordParser).";

    /**
     * Native reads output from aligner.
     */
    protected String samBinaryFilename;

    private int dummyQueryIndex;


    private ConversionConfig config;


    @RegisterThis
    public static DynamicOptionClient doc = new DynamicOptionClient(SAMToCompactMode.class,
            "ignore-read-origin:boolean, When this flag is true do not import read groups.:false"
    );

    private String inputFile;
    private String outputFile;


    public static DynamicOptionClient doc() {
        return doc;
    }

    public String getSamBinaryFilename() {
        return samBinaryFilename;
    }

    public void setSamBinaryFilename(final String samBinaryFilename) {
        this.samBinaryFilename = samBinaryFilename;
    }


    @Override
    public String getModeName() {
        return MODE_NAME;
    }

    @Override
    public String getModeDescription() {
        return MODE_DESCRIPTION;
    }

    /**
     * Get the quality encoding scale used for the input fastq file.
     *
     * @return the quality encoding scale used for the input fastq file
     */
    public QualityEncoding getQualityEncoding() {
        return config.qualityEncoding;
    }

    /**
     * Set the quality encoding scale to be used for the input fastq file.
     * Acceptable values are "Illumina", "Sanger", and "Solexa".
     *
     * @param qualityEncoding the quality encoding scale to be used for the input fastq file
     */
    public void setQualityEncoding(final QualityEncoding qualityEncoding) {
        this.config.qualityEncoding = qualityEncoding;
    }

    /**
     * Get if the SAM/BAM readName should be preserved into the compact-alignment file.
     * Generally this is not recommended but for testing this can be useful.
     *
     * @return if..
     */
    public boolean isPreserveReadName() {
        return config.preserveReadName;
    }

    /**
     * Set if the SAM/BAM readName should be preserved into the compact-alignment file.
     * Generally this is not recommended but for testing this can be useful.
     *
     * @param preserveReadName if..
     */
    public void setPreserveReadName(final boolean preserveReadName) {
        this.config.preserveReadName = preserveReadName;
    }

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
        // configure baseclass

        final JSAPResult jsapResult = parseJsapArguments(args);
        inputFile = jsapResult.getString("input");
        outputFile = jsapResult.getString("output");
        config = new ConversionConfig();
        config.readNamesAreQueryIndices = jsapResult.getBoolean("read-names-are-query-indices");
        config.preserveSoftClips = jsapResult.getBoolean("preserve-soft-clips");
        config.preserveAllTags = jsapResult.getBoolean("preserve-all-tags");
        config.preserveAllMappedQuals = jsapResult.getBoolean("preserve-all-mapped-qualities");
        config.storeReadOrigin = !doc().getBoolean("ignore-read-origin");
        config.preserveReadName = jsapResult.getBoolean("preserve-read-name");

        config.nameToQueryIndices = new ReadNameToIndex("ignore-this-for-now");

        System.out.printf("Store read origin: %b%n", config.storeReadOrigin);
        final String genomeFilename = jsapResult.getString("input-genome");
        if (genomeFilename != null) {
            System.err.println("Loading genome " + genomeFilename);
            final DualRandomAccessSequenceCache aGenome = new DualRandomAccessSequenceCache();
            try {
                aGenome.load(genomeFilename);
                config.genome = aGenome;
            } catch (ClassNotFoundException e) {
                System.err.println("Unable to load genome.");
                System.exit(1);
            }
            System.err.println("Done loading genome ");
        }
        config.mParameter = jsapResult.getInt("ambiguity-threshold");
        config.numberOfReadsFromCommandLine = jsapResult.getInt("number-of-reads");
        config.qualityEncoding = QualityEncoding.valueOf(jsapResult.getString("quality-encoding").toUpperCase());
        config.sortedInput = jsapResult.getBoolean("sorted");
        config.largestQueryIndex = config.numberOfReadsFromCommandLine;
        config.smallestQueryIndex = 0;
        // don't even dare go through the debugging code if log4j was not configured. The debug code
        // is way too slow to run unintentionally in production!
        config.debug = false;
        DynamicOptionRegistry.register(MessageChunksWriter.doc());
        DynamicOptionRegistry.register(AlignmentWriterImpl.doc());
        DynamicOptionRegistry.register(QueryIndexPermutation.doc());
        config.runningFromCommandLine = true;
        return this;
    }

    @Override
    public void execute() throws IOException {
        // read target/query identifier lookup table, and initialize output alignment
        // file with this information

        // initialize too-many-hits output file
        final AlignmentTooManyHitsWriter tmhWriter =
                new AlignmentTooManyHitsWriter(outputFile, config.mParameter);

        try {
            scan(tmhWriter);


        } finally {

            tmhWriter.close();
        }
    }


    private int scan(final AlignmentTooManyHitsWriter tmhWriter)
            throws IOException {
        int numAligns = 0;
        final IndexedIdentifier targetIds = new IndexedIdentifier();
        final AlignmentWriter destinationWriter = new AlignmentWriterImpl(outputFile);
        final AlignmentWriter writer = config.sortedInput ? new BufferedSortingAlignmentWriter(destinationWriter, 10000) : destinationWriter;
        final ProgressLogger progress = new ProgressLogger(LOG);
        progress.displayFreeMemory = true;
        // the following is required to set validation to SILENT before loading the header (done in the SAMFileReader constructor)
        SAMFileReader.setDefaultValidationStringency(ValidationStringency.SILENT);

        final InputStream stream = "-".equals(inputFile) ? System.in : new FileInputStream(inputFile);
        final SAMFileReader parser = new SAMFileReader(stream);
        // transfer read groups to Goby header:
        final SAMFileHeader samHeader = parser.getFileHeader();
        final IndexedIdentifier readGroups = new IndexedIdentifier();

        importReadGroups(samHeader, readGroups);
        boolean hasPaired = false;
        progress.start();

        config.numberOfReads = 0;

        // int stopEarly = 0;
        SAMRecord prevRecord = null;


        if (samHeader.getSequenceDictionary().isEmpty()) {
            System.err.println("SAM/BAM file/input appear to have no target sequences. If reading from stdin, please check you are feeding this mode actual SAM/BAM content and that the header of the SAM file is included.");
            if (config.runningFromCommandLine) {
                System.exit(0);
            }
        } else {
            // register all targets ids:
            if (config.genome != null) {
                // register target indices in the order they appear in the genome. This makes alignment target indices compatible
                // with the genome indices.
                for (int genomeTargetIndex = 0; genomeTargetIndex < config.genome.size(); genomeTargetIndex++) {
                    getTargetIndex(targetIds, config.genome.getReferenceName(genomeTargetIndex), config.thirdPartyInput);
                }
            }
            final int numTargets = samHeader.getSequenceDictionary().size();
            for (int i = 0; i < numTargets; i++) {
                final SAMSequenceRecord seq = samHeader.getSequence(i);
                getTargetIndex(targetIds, seq.getSequenceName(), config.thirdPartyInput);
            }
        }

        if (!targetIds.isEmpty() && targetIds.size() != samHeader.getSequenceDictionary().size()) {
            LOG.warn("targets: " + targetIds.size() + ", records: " + samHeader.getSequenceDictionary().size());
        }

        final int numTargets = Math.max(samHeader.getSequenceDictionary().size(), targetIds.size());
        final int[] targetLengths = new int[numTargets];
        for (int i = 0; i < numTargets; i++) {
            final SAMSequenceRecord seq = samHeader.getSequence(i);
            if (seq != null) {
                final int targetIndex = getTargetIndex(targetIds, seq.getSequenceName(), config.thirdPartyInput);
                if (targetIndex < targetLengths.length) {
                    targetLengths[targetIndex] = seq.getSequenceLength();
                }
            }
        }

        writer.setTargetLengths(targetLengths);
        if (config.sortedInput) {
            // if the input is sorted, request creation of the index when writing the alignment.
            writer.setSorted(true);
        }
        final Int2ByteMap queryIndex2NextFragmentIndex = new Int2ByteAVLTreeMap();


        final ObjectArrayList<Alignments.AlignmentEntry.Builder> builders = new ObjectArrayList<Alignments.AlignmentEntry.Builder>();

        final SamRecordParser samRecordParser = new SamRecordParser();
        samRecordParser.setQualityEncoding(config.qualityEncoding);
        samRecordParser.setGenome(config.genome);
        ConvertSamBAMReadToGobyAlignment convertReads = new ConvertSamBAMReadToGobyAlignment(targetIds, readGroups,
                 queryIndex2NextFragmentIndex, builders, samRecordParser);
        convertReads.setConfig(config);
        for (final SAMRecord samRecord : new SAMRecordIterable(parser.iterator())) {
            config.numberOfReads++;
            convertReads.setSamRecord(samRecord);
            convertReads.invoke();
            if (!convertReads.hasResult())
                continue;
            final boolean readIsPaired = samRecord.getReadPairedFlag();
            for (final Alignments.AlignmentEntry.Builder builder : convertReads.getBuilders()) {
                int queryIndex = convertReads.getQueryIndex();
                if (convertReads.getNumTotalHits() <= config.mParameter) {
                    if (readIsPaired) {

                        if (!samRecord.getMateUnmappedFlag()) {
                            assert convertReads.getFirstFragmentIndex() >= 0 : " firstFragmentIndex cannot be negative";
                            // some BAM files indicate pair is in the p
                            if (convertReads.getMateFragmentIndex() >= 0) {
                                final Alignments.RelatedAlignmentEntry.Builder relatedBuilder =
                                        Alignments.RelatedAlignmentEntry.newBuilder();

                                final int mateTargetIndex = getTargetIndex(targetIds, samRecord.getMateReferenceName(), config.thirdPartyInput);
                                final int mateAlignmentStart = samRecord.getMateAlignmentStart() - 1; // samhelper returns zero-based positions compatible with Goby.
                                relatedBuilder.setFragmentIndex(convertReads.getMateFragmentIndex());
                                relatedBuilder.setPosition(mateAlignmentStart);
                                relatedBuilder.setTargetIndex(mateTargetIndex);
                                builder.setPairAlignmentLink(relatedBuilder);
                            }
                        } else {
                            // mate is unmapped.

                        }
                    }

                    writer.appendEntry(builder.build());
                    numAligns += convertReads.getMultiplicity();
                    if (config.debug && LOG.isDebugEnabled()) {
                        LOG.debug(String.format("Added queryIdndex=%d to alignment", queryIndex));
                    }
                } else {

                    // TMH writer adds the alignment entry only if hits > thresh
                    tmhWriter.append(queryIndex, convertReads.getNumTotalHits(), convertReads.getGobySamRecord().getQueryLength());
                    if (config.debug && LOG.isDebugEnabled()) {
                        LOG.debug(String.format("Added queryIndex=%d to TMH", queryIndex));
                    }
                    // remove the query name from memory since we are not writing these entries anyway
                    while (queryIndex == convertReads.getQueryIndex(0, samRecord.getReadName())) {
                        //do nothing
                    }
                }
            }
            numAligns = convertReads.getNumAligns();
            progress.lightUpdate();
        }

        if (!targetIds.isEmpty()) {
            // we collected target ids, let's write them to the header:
            writer.setTargetIdentifiers(targetIds);
        }
        writer.putStatistic("number-of-entries-written", numAligns);
        writer.setNumQueries(Math.max(config.numberOfReads, config.numberOfReadsFromCommandLine));
        writer.printStats(System.out);

        // write information from SAM file header
        final SAMSequenceDictionary samSequenceDictionary = samHeader.getSequenceDictionary();
        final List<SAMSequenceRecord> samSequenceRecords = samSequenceDictionary.getSequences();

        writer.setReadOriginInfo(readOriginInfoBuilderList);
        progress.stop();
        writer.close();
        return numAligns;
    }

    private int getTargetIndex(final IndexedIdentifier targetIds, final String sequenceName, final boolean thirdPartyInput) {
        int targetIndex = -1;

        targetIndex = targetIds.registerIdentifier(new MutableString(sequenceName));
        return targetIndex;
    }


    private final ObjectArrayList<Alignments.ReadOriginInfo.Builder> readOriginInfoBuilderList = new ObjectArrayList<Alignments.ReadOriginInfo.Builder>();
    DateFormat dateFormatter = new SimpleDateFormat("dd:MMM:yyyy");

    private void importReadGroups(final SAMFileHeader samHeader, final IndexedIdentifier readGroups) {
        if (!samHeader.getReadGroups().isEmpty() && config.storeReadOrigin) {
            for (final SAMReadGroupRecord rg : samHeader.getReadGroups()) {
                final String sample = rg.getSample();
                final String library = rg.getLibrary();
                final String platform = rg.getPlatform();
                final String platformUnit = rg.getPlatformUnit();
                final Date date = rg.getRunDate();
                final String id = rg.getId();
                final int readGroupIndex = readGroups.registerIdentifier(new MutableString(id));
                final Alignments.ReadOriginInfo.Builder roi = Alignments.ReadOriginInfo.newBuilder();
                roi.setOriginIndex(readGroupIndex);
                roi.setOriginId(id);
                if (library != null) {
                    roi.setLibrary(library);
                }
                if (platform != null) {
                    roi.setPlatform(platform);
                }
                if (platformUnit != null) {
                    roi.setPlatformUnit(platformUnit);
                }
                if (sample != null) {
                    roi.setSample(sample);
                }
                if (date != null) {
                    roi.setRunDate(dateFormatter.format(date));
                }
                readOriginInfoBuilderList.add(roi);
            }
        }
    }




    /**
     * Main method.
     *
     * @param args command line args.
     * @throws com.martiansoftware.jsap.JSAPException error parsing
     * @throws java.io.IOException                    error parsing or executing.
     */
    public static void main(final String[] args) throws JSAPException, IOException {

        final SAMToCompactMode processor = new SAMToCompactMode();
        processor.configure(args);
        processor.config.runningFromCommandLine = true;
        processor.execute();
    }

    public void setGenome(final RandomAccessSequenceInterface genome) {
        ensureConfigExists();
        this.config.genome = genome;
    }

    private void ensureConfigExists() {

        if (this.config == null) {
            this.config = new ConversionConfig();
        }
    }

    public void setPreserveSoftClips(final boolean flag) {
        ensureConfigExists();
        config.preserveSoftClips = flag;
    }

    public void setPreserveReadQualityScores(final boolean flag) {
        config.preserveAllMappedQuals = flag;
    }

    public void setPreserveAllTags(final boolean flag) {
        config.preserveAllTags = flag;
    }

    public void setInputFile(final String s) {
        inputFile = s;
    }

    public void setOutputFile(final String outputFilename) {
        outputFile = outputFilename;
    }

}
