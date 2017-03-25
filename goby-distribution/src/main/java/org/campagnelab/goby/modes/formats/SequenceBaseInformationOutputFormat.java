package org.campagnelab.goby.modes.formats;


import it.unimi.dsi.fastutil.ints.IntHeapPriorityQueue;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.util.XoRoShiRo128PlusRandom;
import org.apache.commons.io.IOUtils;
import org.campagnelab.dl.varanalysis.protobuf.BaseInformationRecords;
import org.campagnelab.goby.algorithmic.dsv.DiscoverVariantPositionData;
import org.campagnelab.goby.algorithmic.dsv.SampleCountInfo;
import org.campagnelab.goby.baseinfo.SequenceBaseInformationWriter;
import org.campagnelab.goby.modes.DiscoverSequenceVariantsMode;
import org.campagnelab.goby.modes.dsv.DiscoverVariantIterateSortedAlignments;
import org.campagnelab.goby.predictions.AddTrueGenotypeHelperI;
import org.campagnelab.goby.predictions.DummyTrueGenotypeHelper;
import org.campagnelab.goby.predictions.ProtoHelper;
import org.campagnelab.goby.reads.RandomAccessSequenceInterface;
import org.campagnelab.goby.util.OutputInfo;
import org.campagnelab.goby.util.WarningCounter;
import org.campagnelab.goby.util.dynoptions.DynamicOptionClient;
import org.campagnelab.goby.util.dynoptions.RegisterThis;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;


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
 * <p>
 * <p>
 * <p>
 * input convention:
 * paired: germline, somatic
 * trio: father, mother, somatic
 */


public class SequenceBaseInformationOutputFormat implements SequenceVariationOutputFormat {

    private int numberOfSamples;
    private String trueGenotypeMap;
    private boolean withGenotypeMap;
    private int genomicContextLength;
    private IntHeapPriorityQueue encounteredQeuue = new IntHeapPriorityQueue(100);
    private IntOpenHashSet encounteredSet = new IntOpenHashSet(100);
    private int duplicatePositions;
    int maxDupCount = 0;
    private int indelsAdded;


    public static final DynamicOptionClient doc() {
        return doc;
    }

    @RegisterThis
    public static final DynamicOptionClient doc = new DynamicOptionClient(SequenceBaseInformationOutputFormat.class,
            "sampling-rate:float, ratio of sites to write to output. The default writes all sites. Use 0.1 to sample 10% of sites.:1.0",
            "random-seed:long, random seed used for sampling sites.:2390239",
            "true-genotype-map:string, filename for the true genotype map (.varmap), produced by vcf-to-map.:nil",
            "sample-index:int, index of the sample to annotate (needed when map is provided).:0",
            "true-label-annotator:string, classname for the true genotype label annotator. The implementation is provided by " +
                    "the variationanalysis project's genotype.jar:org.campagnelab.dl.genotype.helpers.AddTrueGenotypeHelper",
            "genomic-context-length:int, length of genomic context to capture, centered around a base.:41"

    );


    static private Logger LOG = LoggerFactory.getLogger(SequenceBaseInformationOutputFormat.class);
    private SequenceBaseInformationWriter sbiWriter;

    private float samplingRate;
    private XoRoShiRo128PlusRandom randomGenerator;
    private int tumorGroupIndex;
    private int[] readerIndexToGroupIndex;
    private int[] samplePermutation;

    public void defineColumns(OutputInfo statsWriter, DiscoverSequenceVariantsMode mode) {
        samplePermutation = new int[mode.getSamples().length];
        readerIndexToGroupIndex = mode.getReaderIndexToGroupIndex();
        String[] groups = mode.getGroups();
        if (groups.length > 0) {
            for (int readerIndex = 0; readerIndex < readerIndexToGroupIndex.length; readerIndex++) {
                // the group name is the position into the sbi sample array (e.g., 0, 1 or 2)
                final String group = groups[readerIndexToGroupIndex[readerIndex]];
                try {
                    samplePermutation[readerIndex] = Integer.valueOf(group);
                } catch (NumberFormatException e) {
                    assert readerIndexToGroupIndex.length == 2 : "tumor/normal groups only supported for two samples. Use integer group ids to indicate order for trios.";
                    if ("tumor".equals(group)) {
                        samplePermutation[readerIndex] = 1;
                    }
                    if ("normal".equals(group)) {
                        samplePermutation[readerIndex] = 0;
                    }
                }
            }
        } else {
            for (int readerIndex = 0; readerIndex < readerIndexToGroupIndex.length; readerIndex++) {
                samplePermutation[readerIndex] = readerIndex;
            }
        }

        switch (numberOfSamples) {
            case 1:
                readerIdxs = new Integer[]{samplePermutation[0]};
                break;
            case 2:
                readerIdxs = new Integer[]{samplePermutation[0], samplePermutation[1]};
                break;
            default: //case 3, extend switch case in the future to handle more experimental designs.
                readerIdxs = new Integer[]{samplePermutation[0], samplePermutation[1], samplePermutation[2]};
                break;
        }
        int index = 0;
        tumorGroupIndex = -1;
        for (String group : groups) {
            if ("tumor".equals(group)) {
                tumorGroupIndex = index;
            }
            index++;
        }
        samplingRate = doc.getFloat("sampling-rate");
        int seed = doc.getInteger("random-seed");
        randomGenerator = new XoRoShiRo128PlusRandom(seed);
        try {
            sbiWriter = new SequenceBaseInformationWriter(statsWriter.getFilename());

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        trueGenotypeMap = doc().getString("true-genotype-map");
        withGenotypeMap = !"nil".equals(trueGenotypeMap);
    }

    Integer[] readerIdxs;

    public void allocateStorage(int numberOfSamples, int numberOfGroups) {
        this.numberOfSamples = numberOfSamples;
        genomicContextLength = doc.getInteger("genomic-context-length");
    }

    AddTrueGenotypeHelperI addTrueGenotypeHelper;
    WarningCounter emptyRefIdxs = new WarningCounter();
    int emptyRefIdxsCounter = 0;

    public void writeRecord(DiscoverVariantIterateSortedAlignments iterator, SampleCountInfo[] sampleCounts,
                            int referenceIndex, int position, DiscoverVariantPositionData list, int groupIndexA, int groupIndexB) {


        final RandomAccessSequenceInterface genome = iterator.getGenome();
        if (withGenotypeMap && (addTrueGenotypeHelper == null || addTrueGenotypeHelper instanceof DummyTrueGenotypeHelper)) {
            addTrueGenotypeHelper = configureTrueGenotypeHelper(genome, iterator.isCallIndels());
        }

        if (referenceIndex == -1) {
            emptyRefIdxs.warn(LOG, "referenceIndex was -1");
            emptyRefIdxsCounter++;
            return;
        }

        if (encounteredSet.contains(position)) {
            //don't write duplicate records for the same position
            duplicatePositions++;
            int skippedCounts = sampleCounts[0].getSumCounts();
            maxDupCount = Math.max(skippedCounts, maxDupCount);
            return;
        }
        encounteredSet.add(position);
        encounteredQeuue.enqueue(position);
        if (encounteredSet.size() > 100) {
            int toRm = encounteredQeuue.dequeueInt();
            encounteredSet.remove(toRm);
        }


        if (sampleCounts[0].hasIndels()) {
            indelsAdded++;
        }

        if (!withGenotypeMap && samplingRate < 1.0) {
            if (randomGenerator.nextFloat() > samplingRate) {
                // do not process the site.
                return;
            }
        }
        AddTrueGenotypeHelperI.WillKeepI willKeep = null;
        if (withGenotypeMap) {
            String alignmentReferenceId = iterator.getReferenceId(referenceIndex).toString();
            int genomeTargetIndex = iterator.getGenome().getReferenceIndex(alignmentReferenceId);
            String referenceBase = Character.toString(genome.get(genomeTargetIndex, position));
            willKeep = addTrueGenotypeHelper.willKeep(position, alignmentReferenceId, referenceBase);
            if (!willKeep.isKeep()) {
                // do not process this record at all.
                return;
            }
        }

        //trio (inputs father mother somatic), vs pair (inputs germline somatic)
        try {

            BaseInformationRecords.BaseInformation baseInfo = ProtoHelper.toProto(
                    iterator.getGenome(),
                    iterator.getReferenceId(referenceIndex).toString(),
                    sampleCounts,
                    referenceIndex, position, list,
                    readerIdxs,
                    genomicContextLength);

            if (withGenotypeMap && addTrueGenotypeHelper.addTrueGenotype(willKeep, baseInfo)) {
                baseInfo = addTrueGenotypeHelper.labeledEntry();
                assert baseInfo != null : " labeled entry cannot be null";
                sbiWriter.appendEntry(baseInfo);
            } else if (!withGenotypeMap) {
                sbiWriter.appendEntry(baseInfo);
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private AddTrueGenotypeHelperI configureTrueGenotypeHelper(RandomAccessSequenceInterface genome, boolean callIndels) {
        final String className = doc().getString("true-label-annotator");
        try {

            AddTrueGenotypeHelperI o = (AddTrueGenotypeHelperI) Class.forName(className).newInstance();
            o.configure(trueGenotypeMap,
                    genome,
                    doc.getInteger("sample-index"),
                    callIndels,
                    !callIndels,
                    samplingRate);
            this.addTrueGenotypeHelper = o;
            return o;
        } catch (ClassNotFoundException | IllegalAccessException | InstantiationException | NullPointerException e) {
            throw new RuntimeException("Unable to initialize true label annotator with classname: " + className);
        }

    }

    private BaseInformationRecords.BaseInformation applySamplePermutation(BaseInformationRecords.BaseInformation record) {
        BaseInformationRecords.BaseInformation.Builder builder = record.toBuilder();
        final int length = readerIndexToGroupIndex.length;
        int lastIndex = length - 1;
        for (int sampleIndex = 0; sampleIndex < length; sampleIndex++) {
            final BaseInformationRecords.SampleInfo.Builder sample = record.getSamples(samplePermutation[sampleIndex]).toBuilder();
            sample.setIsTumor(sampleIndex == lastIndex);
            builder.setSamples(sampleIndex, sample);
        }
        return builder.build();
    }

    private boolean minCountsFilter(SampleCountInfo[] sampleCounts) {
        for (int i = 0; i < 2; i++) {
            if (sampleCounts[i].getSumCounts() < 10) return true;
        }
        return false;
    }

    public void close() {
        //    pgReadWrite.stop();

        System.out.println(duplicatePositions + " duplicate positions skipped (because of indel moved backward");
        System.out.println(indelsAdded + " indels added");
        System.out.println(emptyRefIdxsCounter + " records skipped due to -1 reference index");
        try {
            if (withGenotypeMap) {
                sbiWriter.setCustomProperties(addTrueGenotypeHelper.getStatProperties());
                addTrueGenotypeHelper.printStats();
            }

            InputStream in = getClass().getResourceAsStream("/META-INF/GOBY_COMMIT.properties");
            if (in == null) {
                LOG.error("Goby commit properties file (/META-INF/GOBY_COMMIT.properties) not found in classpath. Unable to write info to sbip.");
                sbiWriter.addCustomProperties("goby_properties_file", "not_found_in_classpath");
            } else {
                Properties gobyProperties = new Properties();
                sbiWriter.getCustomProperties().putAll(gobyProperties);
            }
        } finally {
            IOUtils.closeQuietly(sbiWriter);
        }
    }

    public void setGenome(RandomAccessSequenceInterface genome) {

    }

    public void setGenomeReferenceIndex(int index) {

    }
}
