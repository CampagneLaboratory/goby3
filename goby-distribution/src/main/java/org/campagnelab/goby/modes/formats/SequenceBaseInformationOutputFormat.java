package org.campagnelab.goby.modes.formats;


import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.lang.MutableString;
import it.unimi.dsi.util.XoRoShiRo128PlusRandom;
import org.campagnelab.dl.model.utils.ProtoPredictor;
import org.campagnelab.dl.varanalysis.protobuf.BaseInformationRecords;
import org.campagnelab.goby.algorithmic.data.SomaticModel;
import org.campagnelab.goby.alignments.PositionBaseInfo;
import org.campagnelab.goby.baseinfo.SequenceBaseInformationWriter;
import org.campagnelab.goby.modes.DiscoverSequenceVariantsMode;
import org.campagnelab.goby.modes.dsv.DiscoverVariantIterateSortedAlignments;
import org.campagnelab.goby.modes.dsv.DiscoverVariantPositionData;
import org.campagnelab.goby.modes.dsv.SampleCountInfo;
import org.campagnelab.goby.reads.RandomAccessSequenceInterface;
import org.campagnelab.goby.util.OutputInfo;
import org.campagnelab.goby.util.dynoptions.DynamicOptionClient;
import org.campagnelab.goby.util.dynoptions.RegisterThis;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;


/**
 * Created by Remi Torracinta on 5/16/16 on the goby2 parquet-proto-output branch.
 * Moved to Goby3 master on August 28 2016.
 */


/**
 * Example console input
 * -m
 * discover-sequence-variants
 * /Users/rct66/data/cfs/TILJHQN-MHFC-MHFC-85-CTL-A24-55_BCell.header
 * /Users/rct66/data/cfs/TSYEAZS-MHFC-MHFC-85-CTL-A24-55_NKCell.header
 * --format
 * SEQUENCE_BASE_INFORMATION
 * -o
 * /Users/rct66/data/cfs/genotypes_proto
 * --genome
 * human_g1k_v37
 *
 *
 *
 * input convention:
 * paired: germline, somatic
 * trio: father, mother, somatic
 */


public class SequenceBaseInformationOutputFormat implements SequenceVariationOutputFormat {

    public static final DynamicOptionClient doc() {
        return doc;
    }
    @RegisterThis
    public static final DynamicOptionClient doc = new DynamicOptionClient(SequenceBaseInformationOutputFormat.class,
            "sampling-rate:float, ratio of sites to write to output. The default writes all sites. Use 0.1 to sample 10% of sites.:1.0",
            "random-seed:long, random seed used for sampling sites.:2390239"
    );


    static private Logger LOG = LoggerFactory.getLogger(SequenceBaseInformationOutputFormat.class);
    private SequenceBaseInformationWriter sbiWriter;


    private float samplingRate;
    XoRoShiRo128PlusRandom randomGenerator;

    public void defineColumns(OutputInfo statsWriter, DiscoverSequenceVariantsMode mode) {
        samplingRate = doc.getFloat("sampling-rate");
        int seed = doc.getInteger("random-seed");
        randomGenerator = new XoRoShiRo128PlusRandom(seed);
        try {
            sbiWriter = new SequenceBaseInformationWriter(statsWriter.getFilename());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }
    Integer[] readerIdxs;

    public void allocateStorage(int numberOfSamples, int numberOfGroups) {
        readerIdxs  = numberOfSamples==3?(new Integer[]{0,1,2}):(new Integer[]{0,1});
    }




    public void writeRecord(DiscoverVariantIterateSortedAlignments iterator, SampleCountInfo[] sampleCounts,
                            int referenceIndex, int position, DiscoverVariantPositionData list, int groupIndexA, int groupIndexB) {
        if (samplingRate < 1.0) {
            if (randomGenerator.nextFloat() > samplingRate) {
                // do not process the site.
                return;
            }
        }
        //trio (inputs father mother somatic), vs pair (inputs germline somatic)
         try {
            sbiWriter.appendEntry(SomaticModel.toProto(iterator.getGenome(),
                    iterator.getReferenceId(referenceIndex).toString(),
                    sampleCounts, referenceIndex, position, list, readerIdxs));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


    }

    private boolean minCountsFilter(SampleCountInfo[] sampleCounts) {
        for (int i = 0; i < 2; i++) {
            if (sampleCounts[i].getSumCounts() < 10) return true;
        }
        return false;
    }

    public void close() {
        //    pgReadWrite.stop();
        try {
            sbiWriter.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void setGenome(RandomAccessSequenceInterface genome) {

    }

    public void setGenomeReferenceIndex(int index) {

    }
}
