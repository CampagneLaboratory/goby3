package edu.cornell.med.icb.goby.readers.sam;

import edu.cornell.med.icb.goby.alignments.perms.ReadNameToIndex;
import edu.cornell.med.icb.goby.reads.QualityEncoding;
import edu.cornell.med.icb.goby.reads.RandomAccessSequenceInterface;

/**
 * Holds configuration needed when converting SAM/BAM alignments to Goby representation.
 * Created by fac2003 on 5/8/16.
 */
public class ConversionConfig {
    /**
     * Flag to indicate if log4j was configured.
     */
    public boolean debug;
    public boolean preserveAllTags;
    public boolean preserveAllMappedQuals;
    public boolean storeReadOrigin;
    public boolean preserveReadName;
    public boolean readNamesAreQueryIndices;
    public RandomAccessSequenceInterface genome;

    public QualityEncoding qualityEncoding = QualityEncoding.SANGER;
    public boolean runningFromCommandLine = false;
    public boolean preserveSoftClips;
    public int numberOfReads;
    public int numberOfReadsFromCommandLine;
    public int largestQueryIndex;
    public int smallestQueryIndex;
    public boolean thirdPartyInput = true;
    public int mParameter = 1;
    public boolean sortedInput;

    public ConversionConfig() {
        nameToQueryIndices = new ReadNameToIndex("ignore-this-for-now");
    }

    public ReadNameToIndex nameToQueryIndices;
}
