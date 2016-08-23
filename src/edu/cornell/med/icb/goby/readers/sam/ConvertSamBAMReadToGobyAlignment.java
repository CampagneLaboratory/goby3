package edu.cornell.med.icb.goby.readers.sam;

import com.google.protobuf.ByteString;
import edu.cornell.med.icb.goby.alignments.AlignmentTooManyHitsWriter;
import edu.cornell.med.icb.goby.alignments.AlignmentWriter;
import edu.cornell.med.icb.goby.alignments.Alignments;
import edu.cornell.med.icb.goby.modes.SAMToCompactMode;
import edu.cornell.med.icb.identifier.IndexedIdentifier;
import htsjdk.samtools.SAMRecord;
import it.unimi.dsi.fastutil.ints.Int2ByteMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.lang.MutableString;
import org.apache.commons.logging.impl.Log4JLogger;
import org.apache.log4j.Logger;

import java.io.IOException;

/**
 * This class does most of the work of converting SAM/BAM alignments to Goby representation. Originally developed for
 * sam-to-compact, it has been made standalone to allow reuse in other parts of the framework.
 * <p>
 * Created by fac2003 on 5/8/16.
 */
public class ConvertSamBAMReadToGobyAlignment {
    /**
     * Used to log debug and informational messages.
     */
    private static final Logger LOG = Logger.getLogger(ConvertSamBAMReadToGobyAlignment.class);

    private boolean hasResult;
    private AlignmentTooManyHitsWriter tmhWriter;
    private int numAligns;
    private IndexedIdentifier targetIds;
    private AlignmentWriter writer;
    private IndexedIdentifier readGroups;
    private boolean hasPaired;
    private SAMRecord prevRecord;
    private Int2ByteMap queryIndex2NextFragmentIndex;
    private ObjectArrayList<Alignments.AlignmentEntry.Builder> builders;
    private SamRecordParser samRecordParser;
    private SAMRecord samRecord;
    private int numTotalHits;
    private boolean readPaired;
    private ConversionConfig config;

    public void setConfig(ConversionConfig config) {
        this.config = config;
    }

    public GobySamRecord getGobySamRecord() {
        return gobySamRecord;
    }

    private GobySamRecord gobySamRecord;

    public int getQueryIndex() {
        return queryIndex;
    }

    private int queryIndex;

    public int getMultiplicity() {
        return multiplicity;
    }

    private int multiplicity;

    public int getMateFragmentIndex() {
        return mateFragmentIndex;
    }

    private int mateFragmentIndex;

    public int getFirstFragmentIndex() {
        return firstFragmentIndex;
    }

    private int firstFragmentIndex;

    public ConvertSamBAMReadToGobyAlignment(IndexedIdentifier targetIds, IndexedIdentifier readGroups, Int2ByteMap queryIndex2NextFragmentIndex, ObjectArrayList<Alignments.AlignmentEntry.Builder> builders, SamRecordParser samRecordParser) {
        config = new ConversionConfig();


        this.targetIds = targetIds;

        this.readGroups = readGroups;

        this.queryIndex2NextFragmentIndex = queryIndex2NextFragmentIndex;
        this.builders = builders;
        this.samRecordParser = samRecordParser;
    }

    public void setSamRecord(SAMRecord samRecord) {

        this.samRecord = samRecord;
    }

    public boolean hasResult() {
        return hasResult;
    }

    public int getNumAligns() {
        return numAligns;
    }

    public boolean isHasPaired() {
        return hasPaired;
    }

    public SAMRecord getPrevRecord() {
        return prevRecord;
    }

    private int getTargetIndex(final IndexedIdentifier targetIds, final String sequenceName, final boolean thirdPartyInput) {
        int targetIndex = -1;

        targetIndex = targetIds.registerIdentifier(new MutableString(sequenceName));
        return targetIndex;
    }

    public ConvertSamBAMReadToGobyAlignment invoke() throws IOException {
        builders.clear();

        final GobySamRecord gobySamRecord = samRecordParser.processRead(samRecord);
        if (gobySamRecord == null) {
            if (config.debug && LOG.isDebugEnabled()) {
                LOG.debug(String.format("NOT keeping unmapped read %s", samRecord.getReadName()));
            }
            hasResult = false;
            return this;
        }
        if (gobySamRecord.getTargetAlignedLength() + gobySamRecord.getNumInserts() !=
                gobySamRecord.getQueryAlignedLength() + gobySamRecord.getNumDeletes()) {
            LOG.error(String.format("targetAlignedLength+inserts != queryAlignedLength+deletes for read %s",
                    samRecord.getReadName()));
            hasResult = false;
            return this;
        }
        final int targetIndex = getTargetIndex(targetIds, samRecord.getReferenceName(), config.thirdPartyInput);

        if (config.sortedInput) {
            // check that input entries are indeed in sort order. Abort otherwise.
            if (prevRecord != null && prevRecord.getReferenceIndex() == targetIndex) {
                final int compare = prevRecord.getAlignmentStart() - samRecord.getAlignmentStart();//  samComparator.compare(prevRecord, samRecord);
                if (compare > 0) {
                    final String message = String.format("record %s has position before previous record: %s",
                            samRecord.toString(), prevRecord.toString());
                    System.err.println("You cannot specify --sorted when the input file is not sorted. For instance: " + message);

                    LOG.warn(message);
                    // we continue because it is possible BufferedSortingAlignmentWriter will succeed in sorting the file
                    // nevertheless. It will set sorted to false if the file cannot be locally sorted.
                }
            }
        }

        prevRecord = samRecord;

        // try to determine readMaxOccurence, the maximum number of times a read name occurs in a complete alignment.
        int readMaxOccurence = 1;
        final boolean readIsPaired = samRecord.getReadPairedFlag();
        final boolean anotherPair = readIsPaired && !samRecord.getMateUnmappedFlag();
        if (anotherPair) {
            hasPaired = true;
            // if the reads are paired, we expect to see the read name  at least twice.
            readMaxOccurence++;
            // Unfortunately the SAM/BAM format does not provide the exact number of times
            // a read matched the reference sequence. We could find this number in an non sorted BAM file
            // by counting how many times the same read name appears in a continuous block of constant read name.
            // However, for sorted input, we need this number to know when to stop
            // keep a given read name in memory with its associated query index.
            // Since we can't keep all the read names in memory continuously (these are strings and
            // consume much memory), it is unclear how to determine  query-index-occurrences in a sorted
            // SAM/BAM file that contains multiple best hits for read or mate. We can handle these cases
            // correctly when working directly in the aligner and writing Goby format because the information
            // is available at the time of alignment, but discarded afterwards.
        }

        final Object xoString = samRecord.getAttribute("X0");

        // in the following, we consider paired end alignment to always map a single time. This may
        // not be true, but there is no way to tell from the SAM format (the X0 field indicates how
        // many times the segment occurs in the genome, not the pair of read placed by the aligner).
        // Single reads typically have the field X0 set to the number of times the read appears in the
        // genome, there is no problem there, so we use X0 to initialize TMH.
        final int numTotalHits = xoString == null ? 1 : hasPaired ? 1 : (Integer) xoString;
        this.numTotalHits = numTotalHits;

        // Q: samHelper hasn't been set to anything since .reset(). This will always be 1. ??
        // Q: Also, readMaxOccurence is *2 for paired and *2 for splice, but splices can be N pieces, not just 2.
        //    so readMaxOccurence isn't always correct it seems.
        final int numEntries = gobySamRecord.getNumSegments();
        final boolean readIsSpliced = numEntries > 1;
        if (hasPaired) {
            // file has paired end reads, check if this read is paired to use 1 occurrence:

            readMaxOccurence = readIsPaired ? 2 : 1;
        } else {
            // single end, use numTotalHits to remember read name and initialize TMH
            readMaxOccurence = numTotalHits;
        }
        readMaxOccurence *= readIsSpliced ? 2 : 1;
        /* While STAR uses NH to store readMaxOccurence, GSNAP seems to put some other values in that field so we
         can't really trust these values in general. Disable for now on the stable branch.
        final Integer nh = samRecord.getIntegerAttribute("NH");
        // NH:i indicates: NH i Number of reported alignments that contains the query in the current record
        if (nh != null) {
            // used by STAR, for instance, to encode readMaxOccurence
            readMaxOccurence = nh;
        } */
        final String readName = samRecord.getReadName();

        final int queryIndex = getQueryIndex(readMaxOccurence, readName);
        assert queryIndex >= 0 : " Query index must never be negative.";

        int ambiguity = 1;
        final Object ihString = samRecord.getAttribute("IH");
        if (ihString != null) {
            ambiguity = Integer.parseInt((String) ihString);
        }

        final int multiplicity = 1;

        config.largestQueryIndex = Math.max(queryIndex, config.largestQueryIndex);
        config.smallestQueryIndex = Math.min(queryIndex, config.smallestQueryIndex);
        final int genomeTargetIndex = config.genome == null ? -1 : config.genome.getReferenceIndex(SAMToCompactMode.chromosomeNameMapping(config.genome, samRecord.getReferenceName()));
        if (config.genome != null && genomeTargetIndex == -1) {
            System.out.println("genomeTargetIndex==-1, name=" + samRecord.getReferenceName());
            System.out.println("mapping=" + SAMToCompactMode.chromosomeNameMapping(config.genome, samRecord.getReferenceName()));
            System.exit(10);
        }
        int segmentIndex = 0;
        for (final GobySamSegment gobySamSegment : gobySamRecord.getSegments()) {
            // the record represents a mapped read..
            final Alignments.AlignmentEntry.Builder currentEntry = Alignments.AlignmentEntry.newBuilder();

            if (multiplicity > 1) {
                currentEntry.setMultiplicity(multiplicity);
            }
            currentEntry.setAmbiguity(ambiguity);
            if (config.preserveReadName) {
                currentEntry.setReadName(gobySamRecord.getReadName().toString());
            }
            currentEntry.setQueryIndex(queryIndex);
            currentEntry.setTargetIndex(targetIndex);
            currentEntry.setPosition(gobySamSegment.getPosition());     // samhelper returns zero-based positions compatible with Goby.
            currentEntry.setQueryPosition(gobySamSegment.getQueryPosition());

            currentEntry.setQueryLength(gobySamRecord.getQueryLength());
            //currentEntry.setScore(samHelper.getScore());  BAM does not have the concept of a score.
            currentEntry.setMatchingReverseStrand(gobySamRecord.isReverseStrand());
            currentEntry.setQueryAlignedLength(gobySamSegment.getQueryAlignedLength());
            currentEntry.setTargetAlignedLength(gobySamSegment.getTargetAlignedLength());
            currentEntry.setMappingQuality(samRecord.getMappingQuality());
            if (config.preserveSoftClips) {
                final int leftTrim = gobySamSegment.getSoftClippedBasesLeft().length();
                if (leftTrim > 0) {
                    currentEntry.setSoftClippedBasesLeft(convertBases(
                            genomeTargetIndex, gobySamSegment.getPosition() - leftTrim, samRecord.getReadBases(), 0, leftTrim));
                    currentEntry.setSoftClippedQualityLeft(gobySamSegment.getSoftClippedQualityLeft());
                }
                final int queryAlignedLength = gobySamSegment.getQueryAlignedLength();
                final int rightTrim = gobySamSegment.getSoftClippedBasesRight().length();
                final int queryPosition = gobySamSegment.getQueryPosition();
                if (rightTrim > 0) {
                    final int startIndex = queryPosition + queryAlignedLength;
                    final int endIndex = startIndex + rightTrim;
                    currentEntry.setSoftClippedBasesRight(convertBases(genomeTargetIndex,
                            gobySamSegment.getPosition() + gobySamSegment.getTargetAlignedLength(),
                            samRecord.getReadBases(), startIndex, endIndex));
                    currentEntry.setSoftClippedQualityRight(gobySamSegment.getSoftClippedQualityRight());
                }
            }

            if (config.preserveAllMappedQuals && segmentIndex == 0) {
                final byte[] sourceQualAsBytes = gobySamRecord.getReadQualitiesAsBytes();
                // we only store the full quality score on the first entry with a given query index:
                if (sourceQualAsBytes != null) {
                    currentEntry.setReadQualityScores(ByteString.copyFrom(sourceQualAsBytes));
                }
            }
            addSamAttributes(samRecord, currentEntry);

            // Always store the sam flags when converting from sam/bam
            currentEntry.setPairFlags(samRecord.getFlags());
            if (hasPaired) {
                final int inferredInsertSize = samRecord.getInferredInsertSize();
                if (inferredInsertSize != 0) {   // SAM specification indicates that zero means no insert size.
                    currentEntry.setInsertSize(inferredInsertSize);
                }
            }

            for (final GobyQuickSeqvar variation : gobySamSegment.getSequenceVariations()) {
                appendNewSequenceVariation(currentEntry, variation, gobySamRecord.getQueryLength());
                if (config.debug && LOG.isDebugEnabled()) {
                    LOG.debug(String.format("Added seqvar=%s for queryIndex=%d to alignment", variation.toString(), queryIndex));
                }
            }
            final String readGroup = samRecord.getStringAttribute("RG");
            if (readGroup != null && config.storeReadOrigin) {
                final int readOriginIndex = readGroups.getInt(new MutableString(readGroup).compact());
                if (readOriginIndex == -1) {
                    System.err.printf("Read group identifier %s is used in alignment record (read-name=%s), " +
                            "but was not found in the header. Ignoring this read group.%n", readGroup, samRecord.getReadName());
                } else {
                    currentEntry.setReadOriginIndex(readOriginIndex);
                }
            }
            builders.add(currentEntry);
            segmentIndex++;
        }
        final int numFragments = builders.size();
        for (final Alignments.AlignmentEntry.Builder builder : builders) {

            builder.setFragmentIndex(nextFragmentIndex(queryIndex, queryIndex2NextFragmentIndex));
        }
        if (numFragments > 1) {
            for (int j = 0; j < numFragments + 1; j++) {

                linkSplicedEntries(j - 1 >= 0 ? builders.get(j - 1) : null, j < numFragments ? builders.get(j) : null);
            }
        }
        final int fragmentIndex;
        final int firstFragmentIndex = builders.get(0).getFragmentIndex();
        final int mateFragmentIndex;
        if (readIsPaired) {
            if (samRecord.getFirstOfPairFlag()) {
                fragmentIndex = firstFragmentIndex;
                if (pairBefore(samRecord)) {
                    mateFragmentIndex = firstFragmentIndex - 1;
                } else {
                    mateFragmentIndex = nextFragmentIndex(queryIndex, queryIndex2NextFragmentIndex);
                    // fragment index is used as reference, but not own by this entry, we uncomsume it:
                    uncomsumeFragmentIndex(queryIndex, queryIndex2NextFragmentIndex);
                }

            } else {
                fragmentIndex = firstFragmentIndex;
                mateFragmentIndex = pairBefore(samRecord) ? firstFragmentIndex - 1 : firstFragmentIndex + 1;
            }
        } else {
            fragmentIndex = firstFragmentIndex;
            mateFragmentIndex = nextFragmentIndex(queryIndex, queryIndex2NextFragmentIndex);
            // fragment index is used as reference, but not own by this entry, we uncomsume it:
            uncomsumeFragmentIndex(queryIndex, queryIndex2NextFragmentIndex);
        }
        this.firstFragmentIndex = firstFragmentIndex;
        this.mateFragmentIndex = mateFragmentIndex;
        hasResult = true;
        this.multiplicity = multiplicity;
        this.queryIndex = queryIndex;
        this.gobySamRecord = gobySamRecord;
        return this;
    }

    public int getQueryIndex(final int readMaxOccurence, final String readName) {
        return config.readNamesAreQueryIndices ? Integer.parseInt(readName) :
                config.nameToQueryIndices.getQueryIndex(readName, readMaxOccurence);
    }

    private void addSamAttributes(final SAMRecord samRecord, final Alignments.AlignmentEntry.Builder currentEntry) {
        if (config.preserveAllTags) {
            final String[] tokens = samRecord.getSAMString().split("\t");
            final int size = tokens.length;
            for (int i = 11; i < size; i++) {
                final String token = tokens[i];
                if (!token.startsWith("MD:Z") && !token.startsWith("RG:Z")) {
                    // ignore MD and RG since we store them natively..
                    //    System.out.printf("Preserving token=%s%n", token);
                    currentEntry.addBamAttributes(token.replaceAll("\n", ""));
                }
            }
        }

    }

    private int nextFragmentIndex(final int queryIndex, final Int2ByteMap queryIndex2NextFragmentIndex) {
        final int fragmentIndex = queryIndex2NextFragmentIndex.get(queryIndex);
        queryIndex2NextFragmentIndex.put(queryIndex, (byte) (fragmentIndex + 1));
        //       System.out.printf("queryIndex=%d returning fragmentIndex=%d %n", queryIndex, fragmentIndex);
        return fragmentIndex;
    }

    private void linkSplicedEntries(final Alignments.AlignmentEntry.Builder a, final Alignments.AlignmentEntry.Builder b) {
        if (a == null || b == null) {
            return;
        }
        // System.out.printf("Adding splice links between a=%s b=%s %n", a.build().toString(), b.build().toString());
        final Alignments.RelatedAlignmentEntry.Builder forwardSpliceLink = Alignments.RelatedAlignmentEntry.newBuilder();
        forwardSpliceLink.setFragmentIndex(b.getFragmentIndex());
        forwardSpliceLink.setPosition(b.getPosition());
        forwardSpliceLink.setTargetIndex(b.getTargetIndex());

        a.setSplicedForwardAlignmentLink(forwardSpliceLink);

        final Alignments.RelatedAlignmentEntry.Builder backwardSpliceLink = Alignments.RelatedAlignmentEntry.newBuilder();
        backwardSpliceLink.setFragmentIndex(a.getFragmentIndex());
        backwardSpliceLink.setPosition(a.getPosition());
        backwardSpliceLink.setTargetIndex(a.getTargetIndex());

        b.setSplicedBackwardAlignmentLink(backwardSpliceLink);
        //   System.out.printf("Linked queryIndex=%d forward: %d>%d %n", a.getQueryIndex(), a.getFragmentIndex(), forwardSpliceLink.getFragmentIndex());
        //  System.out.printf("Linked queryIndex=%d backward: %d<%d %n", a.getQueryIndex(), backwardSpliceLink.getFragmentIndex(), b.getFragmentIndex());
    }

    // determine if the pair occurs before the primary in genomic position:
    private boolean pairBefore(final SAMRecord samRecord) {
        final int pairOrder = samRecord.getMateReferenceName().compareTo(samRecord.getReferenceName());
        if (pairOrder > 0) {
            return false;
        }
        if (pairOrder == 0) {
            //same reference, check positions:
            return samRecord.getMateAlignmentStart() < samRecord.getAlignmentStart();
        }
        return true;
    }

    /**
     * Unconsume one fragment index.
     *
     * @param queryIndex                   the query index
     * @param queryIndex2NextFragmentIndex the map query index to next fragment indexes
     */
    private void uncomsumeFragmentIndex(final int queryIndex, final Int2ByteMap queryIndex2NextFragmentIndex) {
        final int fragmentIndex = queryIndex2NextFragmentIndex.get(queryIndex);
        queryIndex2NextFragmentIndex.put(queryIndex, (byte) (fragmentIndex - 1));
        //    System.out.printf("unconsumed fragmentIndex=%d for queryIndex=%d %n", fragmentIndex - 1, queryIndex);

    }

    public ObjectArrayList<Alignments.AlignmentEntry.Builder> getBuilders() {
        return builders;
    }

    public int getNumTotalHits() {
        return numTotalHits;
    }

    public boolean isReadPaired() {
        return readPaired;
    }

    MutableString convertBasesBuffer = new MutableString();
    private final MutableString bases = new MutableString();

    public String convertBases(
            final int referenceIndex, final int positionStartOfRead,
            final byte[] readBases, final int startIndex, final int endIndex) {
        if (config.genome != null) {
            int actualPositionStartOfRead = positionStartOfRead;
            int numPrepend = 0;
            int numAppend = 0;
            int actualLength = endIndex - startIndex;
            if (actualPositionStartOfRead < 0) {
                numPrepend = -actualPositionStartOfRead;
                actualPositionStartOfRead = 0;
                actualLength -= numPrepend;
            }
            final int referenceLength = config.genome.getLength(referenceIndex);
            if (actualPositionStartOfRead + actualLength > referenceLength) {
                numAppend = actualPositionStartOfRead + actualLength - referenceLength;
                actualLength -= numAppend;
            }
            config.genome.getRange(referenceIndex, actualPositionStartOfRead, actualLength, bases);
            for (int i = 0; i < numPrepend; i++) {
                bases.insert(0, "N");
            }
            for (int i = 0; i < numAppend; i++) {
                bases.append("N");
            }
        }
        convertBasesBuffer.setLength(endIndex - startIndex);
        int j = 0;
        for (int i = startIndex; i < endIndex; i++) {
            final char readBase = (char) readBases[i];
            final char refBase = config.genome != null ? bases.charAt(i - startIndex) : '!';
            convertBasesBuffer.setCharAt(j, refBase == readBase ? '=' : readBase);
            j++;
        }

        return convertBasesBuffer.toString();
    }

    static void appendNewSequenceVariation(
            final Alignments.AlignmentEntry.Builder currentEntry,
            final GobyQuickSeqvar variation, final int queryLength) {

        final int readIndex = variation.getReadIndex();
        if (readIndex > queryLength) {
            if (readIndex > queryLength) {
                System.out.println("STOP6");
            }
            assert readIndex <= queryLength : String.format(" readIndex %d must be smaller than read length %d .",
                    readIndex, queryLength);
            LOG.warn(String.format(
                    "Ignoring sequence variations for a read since readIndex %d must be smaller than read length %d. query index=%d reference index=%d%n",
                    readIndex, queryLength, currentEntry.getQueryIndex(), currentEntry.getTargetIndex()));
            return;
        }

        final Alignments.SequenceVariation.Builder sequenceVariation =
                Alignments.SequenceVariation.newBuilder();

        sequenceVariation.setFrom(variation.getFrom());
        sequenceVariation.setTo(variation.getTo());
        sequenceVariation.setPosition(variation.getPosition());
        sequenceVariation.setReadIndex(readIndex);  // readIndex starts at 1
        final byte[] toQuality = variation.getToQualitiesAsBytes();
        if (toQuality != null && toQuality.length > 0) {
            sequenceVariation.setToQuality(ByteString.copyFrom(toQuality));
        }
        currentEntry.addSequenceVariations(sequenceVariation);
    }


}
