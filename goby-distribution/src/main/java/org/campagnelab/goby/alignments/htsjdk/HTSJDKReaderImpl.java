package org.campagnelab.goby.alignments.htsjdk;

import edu.cornell.med.icb.identifier.DoubleIndexedIdentifier;
import edu.cornell.med.icb.identifier.IndexedIdentifier;
import htsjdk.samtools.*;
import it.unimi.dsi.fastutil.ints.Int2ByteAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ByteMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.lang.MutableString;
import org.apache.commons.io.FilenameUtils;
import org.campagnelab.goby.alignments.AlignmentReader;
import org.campagnelab.goby.alignments.Alignments;
import org.campagnelab.goby.alignments.ReadOriginInfo;
import org.campagnelab.goby.alignments.ReferenceLocation;
import org.campagnelab.goby.readers.sam.ConversionConfig;
import org.campagnelab.goby.readers.sam.ConvertSamBAMReadToGobyAlignment;
import org.campagnelab.goby.readers.sam.SamRecordParser;
import org.campagnelab.goby.util.dynoptions.DynamicOptionClient;
import org.campagnelab.goby.util.dynoptions.RegisterThis;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Adapter to expose SAM/BAM/CRAM aligments to the Goby framework.
 * Created by fac2003 on 5/8/16.
 */
public class HTSJDKReaderImpl implements AlignmentReader {
    private SAMRecordIterator samRecordIterator;
    private int[] targetLengths;
    private String filename;
    public static final DynamicOptionClient doc() {
        return doc;
    }

    @RegisterThis
    public static final DynamicOptionClient doc = new DynamicOptionClient(HTSJDKReaderImpl.class,
            "force-sorted:boolean, when true, assume all alignments are sorted. " +
                    "When false, check the SO:coordinate flag in the header. Use this option only when you " +
                    "know that each input alignment has been sorted:false"
    );
    /**
     * Used to log debug and informational messages.
     */
    private static final Logger LOG = LoggerFactory.getLogger(HTSJDKReaderImpl.class);

    DateFormat dateFormatter = new SimpleDateFormat("dd:MMM:yyyy");
    private boolean sorted;
    private IndexedIdentifier readGroups;
    private SamReader.Indexing index;
    private List<Alignments.ReadOriginInfo> readOriginInfoList = new ArrayList<>();
    private int numberOfAlignedReads;

    private int lastPosition = -1;
    private int lastTargetIndex = -1;


    public HTSJDKReaderImpl(String basename, int startReferenceIndex, int startPosition, int endReferenceIndex, int endPosition) throws IOException {
        this(basename);
        if (!parser.hasIndex()) {
            throw new UnsupportedOperationException("Cannot use slices when an alignment is not indexed. Check index file is present: .crai, .bai");
        }
        // We need the following lines because another iterator is opened by the this(basename) constructor:
        if (samRecordIterator != null) {
            samRecordIterator.close();
        }

        int startSamPositionOneBased = startPosition + 1;
        int endSamPositionOneBased = endPosition + 1;
        QueryInterval[] intervals = constructQueryIntervals(startReferenceIndex, startSamPositionOneBased, endReferenceIndex, endSamPositionOneBased);
        this.samRecordIterator = parser.query(intervals, false);

    }

    public HTSJDKReaderImpl(String basename, long startOffset, long endOffset) {
        throw new UnsupportedOperationException("Currently not implemented");
    }

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
                readOriginInfoList.add(roi.build());
            }
        }
    }

    private int getTargetIndex(final IndexedIdentifier targetIds, final String sequenceName, final boolean thirdPartyInput) {
        int targetIndex = -1;

        targetIndex = targetIds.registerIdentifier(new MutableString(sequenceName));
        return targetIndex;
    }

    private ConversionConfig config = new ConversionConfig();
    private SamReader parser;
    private ConvertSamBAMReadToGobyAlignment convertReads;
    private IndexedIdentifier targetIds;
    private DoubleIndexedIdentifier reverseTargetIndex2Id;

    public HTSJDKReaderImpl(String basename) throws IOException {
        this.filename = getFilename(basename);
        ;
        final InputStream stream = "-".equals(filename) ? System.in : new FileInputStream(filename);
        File indexFile = new File(filename + ".bai");
        boolean indexFileExists = indexFile.exists();
        SamReaderFactory readerFactory = SamReaderFactory.make();

        readerFactory.validationStringency(ValidationStringency.LENIENT);

        parser = indexFileExists ? readerFactory.open(new File(filename)) :  readerFactory.open( SamInputResource.of(stream));
        targetIds = new IndexedIdentifier();
        queryIndex2NextFragmentIndex        = new Int2ByteAVLTreeMap();

        final ObjectArrayList<Alignments.AlignmentEntry.Builder> builders = new ObjectArrayList<Alignments.AlignmentEntry.Builder>();

        final SamRecordParser samRecordParser = new SamRecordParser();
        samRecordParser.setQualityEncoding(config.qualityEncoding);
        samRecordParser.setGenome(config.genome);

        readHeader();
        convertReads = new ConvertSamBAMReadToGobyAlignment(targetIds, readGroups,
                queryIndex2NextFragmentIndex, builders, samRecordParser);
        convertReads.setConfig(config);
        samRecordIterator = parser.iterator();
    }
    final Int2ByteMap queryIndex2NextFragmentIndex;
    @Override
    public boolean isIndexed() {
        try {
            if (!new File(filename + ".bai").exists() && !new File(filename + ".crai").exists()) return false;
            return parser.indexing() != null && parser.indexing().getIndex() != null;
        } catch (SAMException e) {
            return false;
        }
    }

    @Override
    public String basename() {
        return FilenameUtils.removeExtension(filename);
    }

    private List<Alignments.AlignmentEntry.Builder> builders = new ArrayList<>();

    @Override
    public boolean hasNext() {
        if (hasMoreCached()) {
            return true;
        }
        if (samRecordIterator.hasNext()) {

            try {
                cacheMore(samRecordIterator);
            } catch (IOException e) {
                LOG.error("Exception caught while iterating " + filename, e);
            }
        }
        return hasMoreCached();
    }

    private boolean hasMoreCached() {
        return !builders.isEmpty();
    }

    private void cacheMore(SAMRecordIterator samRecordIterator) throws IOException {
        do {
            final SAMRecord samRecord = samRecordIterator.next();
            if (samRecord != null) {
                config.numberOfReads++;
                convertReads.setSamRecord(samRecord);
                convertReads.invoke();
                if (convertReads.hasResult()) {
                    builders.addAll(convertReads.getBuilders());
                    // all Goby Reads are mapped by definition, the rest are not in the entries file:
                    numberOfAlignedReads += builders.size();
                }
            } else {
                //    System.out.println("cacheMore cannot find more records.");
                if (!samRecordIterator.hasNext()) {
                    // nothing left to make builders from
                    return;
                }
            }
        } while (builders.size() == 0);

    }

    @Override
    public Alignments.AlignmentEntry next() {
        final Alignments.AlignmentEntry alignmentEntry = builders.remove(0).build();
        if (alignmentEntry.getTargetIndex()!=lastTargetIndex) {
            // forget association with past fragments, a new reference is being processed.
            queryIndex2NextFragmentIndex.clear();
        }
        this.lastTargetIndex = alignmentEntry.getTargetIndex();
        this.lastPosition = alignmentEntry.getPosition();
        //    System.out.printf("ref: %d position: %d%n",alignmentEntry.getTargetIndex(), alignmentEntry.getPosition());
        return alignmentEntry;
    }

    @Override
    public Alignments.AlignmentEntry skipTo(int targetIndex, int position) throws IOException {
        boolean doesNotUseIndex = targetIndex == lastTargetIndex;
        if (doesNotUseIndex) {
            return slowSkipTo(targetIndex, position);
        } else {
            reposition(targetIndex, position);
            if (hasNext()) return next();
            else {
                return null;
            }
        }
    }

    /**
     * This method implements skipTo by iterating one entry at a time. It does not use the index.
     * The BAM API is not efficient to move through entries with the index (because the file is reopened when a
     * new iterator is created with query for a new interval), so we have to use slowSkipTo
     * when we are already close to the entry.
     *
     * @param targetIndex
     * @param position
     * @return
     * @throws IOException
     */
    public Alignments.AlignmentEntry slowSkipTo(int targetIndex, int position) throws IOException {
        Alignments.AlignmentEntry entry = null;
        while (hasNext()) {
            entry = next();
            if (targetIndex < entry.getTargetIndex()) {
                return entry;
            }
            if (entry.getTargetIndex() == targetIndex && position <= entry.getPosition()) {
                return entry;
            }
        }
        return null;
    }

    @Override
    public void reposition(int targetIndex, int position) throws IOException {

        if (!parser.hasIndex()) {
            throw new UnsupportedOperationException("Cannot use skipTo/reposition when an alignment is not indexed. Check index file is present: .crai, .bai");
        }

        if (samRecordIterator != null) {
            samRecordIterator.close();
        }
        int samPositionOneBased = position + 1;
        QueryInterval[] intervals = constructQueryIntervalsStartingAt(targetIndex, samPositionOneBased);
        this.samRecordIterator = parser.query(intervals, false);
        // eliminate any previously cached alignments if they fall before the new start position:
        List<Alignments.AlignmentEntry.Builder> toRemove = new ArrayList<>();
        for (Alignments.AlignmentEntry.Builder builder : builders) {
            if (builder.getTargetIndex() < targetIndex) {
                toRemove.add(builder);
            } else if (builder.getTargetIndex() == targetIndex && builder.getPosition() < samPositionOneBased) {
                toRemove.add(builder);
            }
        }
        builders.removeAll(toRemove);
    }

    private QueryInterval[] constructQueryIntervalsStartingAt(int targetIndex, int samPositionOneBased) {
        int numElements = targetIds.size() - targetIndex;
        QueryInterval[] intervals = new QueryInterval[numElements];
        for (int i = 0; i < numElements; i++) {
            // for any reference but the first, include from start (1):
            final int startPosition = i == 0 ? samPositionOneBased : 1;
            intervals[i] = new QueryInterval(i + targetIndex,
                    startPosition,
                    -1 /* end of sequence */); // open ended interval, finishing
            // at end of ref.
        }
        return QueryInterval.optimizeIntervals(intervals);
    }

    private QueryInterval[] constructQueryIntervals(int startTargetIndex, int startSamPositionOneBased, int endTargetIndex, int endSamPositionOneBased) {
        int numElements = endTargetIndex - startTargetIndex + 1;
        QueryInterval[] intervals = new QueryInterval[numElements];
        for (int i = 0; i < numElements; i++) {
            // for any reference but the first, include from start (1):
            final int startPosition = i == 0 ? startSamPositionOneBased : 1;
            final int endPosition = (i == endTargetIndex - startTargetIndex) ? endSamPositionOneBased : -1 /* end of target sequence*/;
            intervals[i] = new QueryInterval(i + startTargetIndex, startPosition, endPosition);
            // at end of ref.
        }
        return QueryInterval.optimizeIntervals(intervals);
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException("remove not implemented");
    }

    @Override
    public void readHeader() throws IOException {
        // transfer read groups to Goby header:
        final SAMFileHeader samHeader = parser.getFileHeader();
        readGroups = new IndexedIdentifier();

        importReadGroups(samHeader, readGroups);
        boolean hasPaired = false;
        targetIds = new IndexedIdentifier();


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
        this.targetLengths = targetLengths;
        this.reverseTargetIndex2Id = new DoubleIndexedIdentifier(targetIds);
    }

    @Override
    public void readIndex() throws IOException {
        this.index = parser.indexing();
    }

    @Override
    public void close() {
        try {
            parser.close();
        } catch (IOException e) {
            LOG.error("Exception when attempting to close SAM/BAM/CRAM parser", e);
        }
    }

    @Override
    public Iterator<Alignments.AlignmentEntry> iterator() {
        return this;
    }

    @Override
    public Properties getStatistics() {
        // BAM/SAM/CRAM have no Goby stats.
        return new Properties();
    }

    @Override
    public int getNumberOfAlignedReads() {
        return numberOfAlignedReads;
    }

    @Override
    public ObjectList<ReferenceLocation> getLocations(int modulo) throws IOException {
        // simulate locations for a BAM/CRAM alignemnt:
        ObjectList<ReferenceLocation> locations = new ObjectArrayList<>();
        int targetIndex = 0;
        for (int targetLength : getTargetLength()) {
            for (int position = 0; position < targetLength; position += modulo) {
                ReferenceLocation location = new ReferenceLocation(targetIndex, position);
                locations.add(location);
            }
            targetIndex++;
        }
        return locations;
    }

    @Override
    public ReferenceLocation getMinLocation() throws IOException {
        return new ReferenceLocation(0,0);
    }

    @Override
    public ReferenceLocation getMaxLocation() throws IOException {
        final int lastTarget = getNumberOfTargets() - 1;
        return new ReferenceLocation(lastTarget,getTargetLength()[lastTarget]);
    }

    @Override
    public ObjectList<ReferenceLocation> getLocationsByBytes(int bytesPerSlice) throws IOException {
     throw new UnsupportedOperationException("Locations by byte are not supported for SAM/BAM/CRAM formats.");
    }

    @Override
    public boolean isQueryLengthStoredInEntries() {

        // SAM/BAM/CRAM have no concept of fixed read length encoded in a header.
        return true;
    }

    @Override
    public String getAlignerName() {
        List<SAMProgramRecord> records = parser.getFileHeader().getProgramRecords();
        return "unknown";
    }

    @Override
    public String getAlignerVersion() {
        return "unknown";
    }

    @Override
    public int getSmallestSplitQueryIndex() {
        return 0;
    }

    @Override
    public int getLargestSplitQueryIndex() {
        return 0;
    }

    @Override
    public IndexedIdentifier getTargetIdentifiers() {
        return targetIds;
    }

    @Override
    public int[] getTargetLength() {
        return targetLengths;
    }

    @Override
    public int getNumberOfTargets() {
        return targetIds.size();
    }

    @Override
    public int getNumberOfQueries() {
        return 0;
    }

    @Override
    public boolean isConstantQueryLengths() {
        return false;
    }

    @Override
    public int getConstantQueryLength() {
        return 0;
    }

    @Override
    public IndexedIdentifier getQueryIdentifiers() {
        throw new UnsupportedOperationException("Read names can be obtained from alignemnt entry directly");
    }

    @Override
    public long getStartByteOffset(int startReferenceIndex, int startPosition) {
        return 0;
    }

    @Override
    public boolean getQueryIndicesWerePermuted() {
        return false;
    }

    @Override
    public boolean hasQueryIndexOccurrences() {
        return false;
    }

    @Override
    public boolean hasAmbiguity() {
        return false;
    }

    @Override
    public long getEndByteOffset(int startReferenceIndex, int startPosition, int endReferenceIndex,
                                 int endPosition) {
        return 0;
    }

    @Override
    public ReadOriginInfo getReadOriginInfo() {
        return new ReadOriginInfo(readOriginInfoList);
    }


    @Override
    public boolean isSorted() {
        Boolean aBoolean = doc().getBoolean("force-sorted");
        if (aBoolean!=null && aBoolean) {
            return true;
        }
        return parser.getFileHeader().getSortOrder() != SAMFileHeader.SortOrder.unsorted;
    }

    /**
     * Determine if this reader can read the file (using the extension of the file to make the determination).
     *
     * @param basename basename without or without the extension
     * @return True when the reader should be able to read files with this extension.
     */
    public static boolean canRead(String basename) {
        String filename = basename;
        filename = getFilename(basename);

        return filename.endsWith(".bam") || filename.endsWith(".sam") || filename.endsWith(".cram");
    }

    private static String getFilename(String basename) {
        String filename = basename;
        if (!new File(basename).exists()) {
            String[] extensions = {".bam", ".cram", ".sam"};
            for (String extension : extensions) {
                if (new File((basename + extension)).exists()) {
                    filename = basename + extension;
                    break;
                }
                final String pathname = FilenameUtils.removeExtension(basename) + extension;
                if (new File(pathname).exists()) {
                    filename = pathname;
                    break;
                }
            }

        } else {
            filename = basename;
        }
        return filename;
    }

    public static String[] getBasenames(String[] inputFilenames) {
        Set<String> basenames = new ObjectArraySet<>();
        for (String filename : inputFilenames) {
            String[] extensions = {".bam", ".cram", ".sam"};
            for (String extension : extensions) {
                if (filename.endsWith(extension)) {
                    // do not remove the extension, because sometimes you see .bam.cram, which would reduce to a
                    //file that does not exist. Don't ask me why people do this.
                    basenames.add(filename);
                }

            }
        }
        return basenames.toArray(new String[basenames.size()]);
    }
}
