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

package org.campagnelab.goby.modes.formats;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.lang.MutableString;
import org.campagnelab.goby.algorithmic.dsv.DiscoverVariantPositionData;
import org.campagnelab.goby.algorithmic.dsv.SampleCountInfo;
import org.campagnelab.goby.modes.DiscoverSequenceVariantsMode;
import org.campagnelab.goby.modes.dsv.DiscoverVariantIterateSortedAlignments;
import org.campagnelab.goby.predictions.FormatIndelVCF;
import org.campagnelab.goby.predictions.FormatIndelVCF2;
import org.campagnelab.goby.predictions.GenotypePredictor;
import org.campagnelab.goby.predictions.MergeIndelFrom;
import org.campagnelab.goby.readers.vcf.ColumnType;
import org.campagnelab.goby.reads.RandomAccessSequenceInterface;
import org.campagnelab.goby.stats.VCFWriter;
import org.campagnelab.goby.util.OutputInfo;
import org.campagnelab.goby.util.Variant;
import org.campagnelab.goby.util.dynoptions.DynamicOptionClient;
import org.campagnelab.goby.util.dynoptions.RegisterThis;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

/**
 * @author Fabien Campagne
 *         Date: Mar 21, 2011
 *         Time: 2:37:43 PM
 */
public class GenotypesOutputFormat implements SequenceVariationOutputFormat {
    private static GenotypePredictor predictor;

    private static String GOBY_HOME;
    private String modelPrefix;
    private String modelPath;
    private RandomAccessSequenceInterface genome;
    private int genomeReferenceIndex;
    private double[] modelProbabilities;
    private int modelCallIndex;
    private int modelProbabilityIndex;
    private int altGenotypeCount;
    private float minimumP;
    private float stringentP;

    public static final DynamicOptionClient doc() {
        return doc;
    }

    @RegisterThis
    public static final DynamicOptionClient doc = new DynamicOptionClient(GenotypesOutputFormat.class,
            "model-path:string, path to a variationanalysis deep learning model to call genotypes:${GOBY_HOME}/models/genotyping/1495043324474/bestscore-ComputationGraph.bin",
            "minimum-p:float, minimum probability in any sample to report a genotype.:0.0",
            "stringent-p:float, minimum probability in all samples to report a genotype.:0.0"
    );
    private int positionColumnIndex;
    private int numberOfGroups;
    private int numberOfSamples;
    private int[] refCountsPerSample;
    private int[] variantsCountPerSample;
    protected VCFWriter statsWriter;
    String[] samples;
    private int chromosomeColumnIndex;
    private int idColumnIndex;
    protected int biomartFieldIndex;

    public int getGenotypeFieldIndex() {
        return genotypeFieldIndex;
    }

    private int genotypeFieldIndex;

    public int getBaseCountFieldIndex() {
        return baseCountFieldIndex;
    }

    public int baseCountFieldIndex;
    private int zygFieldIndex;
    private int altCountsIndex;
    private int arrayCountsIndex;

    public int getFailBaseCountFieldIndex() {
        return failBaseCountFieldIndex;
    }

    private int failBaseCountFieldIndex;

    public int getGoodBaseCountFieldIndex() {
        return goodBaseCountFieldIndex;
    }

    private int goodBaseCountFieldIndex;
    private String[] singleton = new String[1];
    private int indelFlagFieldIndex = -1;
    private boolean siteObserved;


    //only for internal use
    private boolean ALT_FORMAT = false;


    public void defineColumns(OutputInfo writer, DiscoverSequenceVariantsMode mode) {

        // load the predictor.
        ServiceLoader<GenotypePredictor> predictorLoader;
        predictorLoader = ServiceLoader.load(GenotypePredictor.class);
        predictorLoader.reload();
        final Iterator<GenotypePredictor> iterator = predictorLoader.iterator();
        if (iterator.hasNext()) {
            predictor = iterator.next();
        }
        if (predictor == null) {
            throw new RuntimeException("The genotype.jar file was not found in the classpath. " +
                    "Unable to call genotypes with a deep learning model. " +
                    "Prefer to run goby with the shell wrapper (distribution/goby) to configure this dependency.");
        }
        if (iterator.hasNext()) {
            LOG.warn("At least two implementations of GenotypePredictor have been found. Make sure a single provider exists in the classpath.");
        }


        //get model ready
        String customPath = doc.getString("model-path");
        if (customPath.contains("${GOBY_HOME}")) {
            GOBY_HOME = System.getenv("GOBY_HOME");
            if (GOBY_HOME == null) {
                System.out.println("Goby can't find the GOBY_HOME folder. Are you running goby with the goby bash script?");
                throw new RuntimeException("GOBY_HOME path variable not defined in java environment. Please run goby with its bash script.");
            }
            customPath = customPath.replace("${GOBY_HOME}", GOBY_HOME);
        }
        minimumP = doc.getFloat("minimum-p");
        stringentP = doc.getFloat("stringent-p");
        //extract prefix and model directory from model path input.
        modelPrefix = predictor.getModelPrefix(customPath);
        modelPath = predictor.getModelPath(customPath);
        if (modelPath == null || modelPath == null) {
            throw new RuntimeException("Unable to determine modelPath or prefix/label  with " + customPath);
        }
        try {
            predictor.loadModel(modelPath, modelPrefix);
            System.out.println("model at " + modelPath + " loaded");
            if (!predictor.modelIsLoaded()) {
                throw new IOException("Model not loaded");
            }
        } catch (IOException e) {
            throw new RuntimeException(String.format("Unable to load genotype model %s with path %s ", modelPrefix,
                    modelPath), e);
        }


        samples = mode.getSamples();
        this.statsWriter = new VCFWriter(writer.getPrintWriter());

        biomartFieldIndex = statsWriter.defineField("INFO", "BIOMART_COORDS", 1, ColumnType.String, "Coordinates for use with Biomart.", "biomart");
        defineInfoFields(statsWriter);
        defineGenotypeField(statsWriter);

        if (ALT_FORMAT) {
            altCountsIndex = statsWriter.defineField("FORMAT", "AltCounts", 1, ColumnType.String, "AltCounts", "altcounts");
            arrayCountsIndex = statsWriter.defineField("FORMAT", "ArrayCounts", 1, ColumnType.String, "ArrayCounts", "arraycounts");
        }
        zygFieldIndex = statsWriter.defineField("FORMAT", "Zygosity", 1, ColumnType.String, "Zygosity", "zygosity");
        statsWriter.defineSamples(samples);
        statsWriter.writeHeader("##modelPath=" + modelPath, "##modelTag=" + predictor.getModelProperties().get("tag"));
    }

    public void defineInfoFields(VCFWriter statsWriter) {
        indelFlagFieldIndex = statsWriter.defineField("INFO", "INDEL", 1, ColumnType.Flag, "Indicates that the variation is an indel.", "indel");
    }

    public void defineGenotypeField(VCFWriter statsWriter) {
        genotypeFieldIndex = statsWriter.defineField("FORMAT", "GT", 1, ColumnType.String, "Genotype", "genotype");
        baseCountFieldIndex = statsWriter.defineField("FORMAT", "BC", 5, ColumnType.String, "Base counts in format A=?;T=?;C=?;G=?;N=?.", "base-calls");
        goodBaseCountFieldIndex = statsWriter.defineField("FORMAT", "GB", 1, ColumnType.String, "Number of bases that pass base filters in this sample, or ignore string.", "good-bases");
        failBaseCountFieldIndex = statsWriter.defineField("FORMAT", "FB", 1, ColumnType.String, "Number of bases that failed base filters in this sample, or ignore string.", "failed-bases");
        if (predictor != null && predictor.modelIsLoaded()) {
            modelCallIndex = statsWriter.defineField("FORMAT", "MC", 1, ColumnType.String, "Model Calls.", "genotype");
            modelProbabilityIndex = statsWriter.defineField("FORMAT", "P", 1, ColumnType.Float, "Model proability.", "genotype");
        }
    }

    public void allocateStorage(int numberOfSamples, int numberOfGroups) {
        this.numberOfGroups = numberOfGroups;
        this.numberOfSamples = numberOfSamples;

        refCountsPerSample = new int[numberOfSamples];
        variantsCountPerSample = new int[numberOfSamples];
        modelGenotypes = new String[numberOfSamples];
        modelProbabilities = new double[numberOfSamples];
    }

    IntArrayList decreasingCounts = new IntArrayList();
    ObjectArraySet<String> sampleAlleleSet = new ObjectArraySet<String>();
    ObjectArraySet<String> alleleSet = new ObjectArraySet<String>();
    MutableString genotypeBuffer = new MutableString();

    @Override
    public void writeRecord(final DiscoverVariantIterateSortedAlignments iterator, final SampleCountInfo[] sampleCounts,
                            final int referenceIndex, int position, final DiscoverVariantPositionData list,
                            final int groupIndexA, final int groupIndexB) {

        int positionFormat = position + 1;  // report  1-based position
        fillVariantCountArrays(sampleCounts);

        CharSequence currentReferenceId = iterator.getReferenceId(referenceIndex);
        statsWriter.setId(".");
        statsWriter.setInfo(biomartFieldIndex,
                String.format("%s:%d:%d", currentReferenceId, positionFormat,
                        positionFormat));
        statsWriter.setChromosome(currentReferenceId);

        statsWriter.setPosition(positionFormat);
        /*    //   int location = 8930385;
       int location = 8930369;
       if (position == location || position - 1 == location || position + 1 == location) {
           System.out.println("STOP");
       } */
        predictGenotypes(sampleCounts, currentReferenceId.toString(), position, referenceIndex, list);
        writeGenotypes(statsWriter, sampleCounts, positionFormat);

        writeZygozity(sampleCounts);

        // Do not write record if alleleSet is empty, IGV VCF track cannot handle that.
        if (!alleleSet.isEmpty()) {
            // do not write sites that only match the reference. Save storage since we know the answer.
            if (altGenotypeCount > 0 && maxProbability() >= minimumP && minProbability() > stringentP) {

                statsWriter.writeRecord();
            }
        }

        if (ALT_FORMAT) {
            writeAltCounts(sampleCounts);
            writeCountsArray(sampleCounts);
        }
    }

    private double minProbability() {
        // calculate the minimum probability observed over all samples:
        double min = Double.POSITIVE_INFINITY;
        for (double p : modelProbabilities) {
            min = Math.min(p, min);
        }
        return min;
    }

    private double maxProbability() {
        // calculate the maximum probability observed over all samples:
        double max = Double.NEGATIVE_INFINITY;
        for (double p : modelProbabilities) {
            max = Math.max(p, max);
        }
        return max;
    }

    String modelGenotypes[];

    protected void predictGenotypes(SampleCountInfo[] sampleCounts, String referenceId, int position, int referenceIndex, DiscoverVariantPositionData list) {
        if (!predictor.modelIsLoaded()) {
            Arrays.fill(modelGenotypes, null);
            Arrays.fill(modelProbabilities, 0);
            return;
        }
        int sampleIndex = 0;
        for (SampleCountInfo sample : sampleCounts) {
            predictor.predict(genome, referenceId, sampleCounts, referenceIndex, position, list, new int[]{sampleIndex});
            modelGenotypes[sampleIndex] = predictor.getCalledGenotype();
            modelProbabilities[sampleIndex] = predictor.getProbabilityOfCalledGenotype();
            statsWriter.setSampleValue(modelCallIndex, sampleIndex, modelGenotypes[sampleIndex]);
            statsWriter.setSampleValue(modelProbabilityIndex, sampleIndex, modelProbabilities[sampleIndex]);
            sampleIndex++;
        }
    }

    protected void writeAltCounts(SampleCountInfo[] sampleCounts) {
        for (int sampleIndex = 0; sampleIndex < numberOfSamples; sampleIndex++) {
            SampleCountInfo sci = sampleCounts[sampleIndex];
            String alt = sci.toString().replace("\n", "");
            statsWriter.setSampleValue(altCountsIndex, sampleIndex, alt);
        }
    }


    protected void writeCountsArray(SampleCountInfo[] sampleCounts) {
        for (int sampleIndex = 0; sampleIndex < numberOfSamples; sampleIndex++) {
            SampleCountInfo sci = sampleCounts[sampleIndex];
            int numCounts = sci.getGenotypeMaxIndex();
            int[] counts = new int[numCounts];
            for (int genotypeIndex = 0; genotypeIndex < numCounts; genotypeIndex++) {
                counts[genotypeIndex] += sci.getGenotypeCount(genotypeIndex, true) + sci.getGenotypeCount(genotypeIndex, false);
            }
            statsWriter.setSampleValue(arrayCountsIndex, sampleIndex, Arrays.toString(counts));
        }
    }

    protected void writeZygozity(SampleCountInfo[] sampleCounts) {
        for (int sampleIndex = 0; sampleIndex < numberOfSamples; sampleIndex++) {
            int alleleCount = 0;

            if (hasModelGenotype(sampleIndex)) {
                final ObjectSet<String> alleles = new ObjectArraySet<>();
                Collections.addAll(alleles, modelGenotypes[sampleIndex].split("[/|]"));
                alleleCount = alleles.size();
            } else {
                SampleCountInfo sci = sampleCounts[sampleIndex];
                for (int genotypeIndex = 0; genotypeIndex < sci.getGenotypeMaxIndex(); ++genotypeIndex) {
                    final int count = sci.getGenotypeCount(genotypeIndex);
                    if (count > 0) {
                        ++alleleCount;
                    }
                }
            }
            String zygozity;
            switch (alleleCount) {
                case 0:
                    zygozity = "not-typed";
                    break;
                case 1:
                    zygozity = "homozygous";
                    break;
                case 2:
                    zygozity = "heterozygous";
                    break;
                default:

                    zygozity = "Mixture";
                    break;
            }

            statsWriter.setSampleValue(zygFieldIndex, sampleIndex, zygozity);

        }

    }

    private boolean hasModelGenotype(int sampleIndex) {
        return modelGenotypes[sampleIndex] != null;
    }

    private IntArrayList samplesMatchingRef = new IntArrayList();
    ObjectArraySet<String> referenceSet = new ObjectArraySet<String>();

    public void writeGenotypes(VCFWriter statsWriter, SampleCountInfo[] sampleCounts, int position) {
        boolean referenceAlleleSetForIndel = false;
        siteObserved = false;
        boolean siteHasIndel = false;
        referenceSet.clear();
        alleleSet.clear();
        statsWriter.clearAlternateAlleles();
        altGenotypeCount = 0;

        // determine the common reference across all samples:

        ObjectSet<String> allelesPerSample[] = new ObjectSet[numberOfSamples];
        Set<Variant.FromTo> fromTos = new ObjectArraySet<>();
        for (int sampleIndex = 0; sampleIndex < numberOfSamples; sampleIndex++) {

            sampleAlleleSet.clear();
            SampleCountInfo sci = sampleCounts[sampleIndex];
            allelesPerSample[sampleIndex] = new ObjectArraySet();
            boolean modelAvailable = hasModelGenotype(sampleIndex);
            final ObjectSet<String> calledAlleles = new ObjectArraySet<>();

            Collections.addAll(calledAlleles, modelGenotypes[sampleIndex].split("[/|]"));
            String sampleFrom = null;
            String sampleTo = null;
            for (int genotypeIndex = 0; genotypeIndex < sci.getGenotypeMaxIndex(); ++genotypeIndex) {
                final int sampleCount = sci.getGenotypeCount(genotypeIndex);
                String genotype = sci.getGenotypeString(genotypeIndex);
                // when a model is available, we used the called alleles to determine if a given genotype is present in
                // the sample. Otherwise, we default to Goby1-2's sampleCount>0 behavior.
                if ((modelAvailable ? calledAlleles.contains(genotype) && sampleCount > 0 : sampleCount > 0) &&
                        genotypeIndex != SampleCountInfo.BASE_OTHER_INDEX) {
                    siteObserved = true;
                    sampleFrom = sci.getReferenceGenotype();
                    sampleTo = genotype;
                    fromTos.add(new Variant.FromTo(sampleFrom, sampleTo, sampleIndex));
                    if (sci.isIndel(genotypeIndex)) {
                        siteHasIndel = true;
                    }
                    allelesPerSample[sampleIndex].add(genotype);
                }
            }
        }
        // normalize the alleles across all samples, define  alleles in the writer:
        String commonReference = null;
        if (siteObserved && fromTos.size() > 0) {

            // combine reference sequences across samples:
            MergeIndelFrom merger = new MergeIndelFrom(fromTos);
            String from = merger.getFrom();
            Set<String> to = merger.getTos();
            // normalize indel sequences:
            FormatIndelVCF formatIndelVCF = new FormatIndelVCF(from, to, from.charAt(0));
            commonReference = formatIndelVCF.fromVCF;

            // set alleles in writer:
            statsWriter.setReferenceAllele(commonReference);
            statsWriter.clearAlternateAlleles();
            for (String alternateAllele : formatIndelVCF.toVCF) {
                if (!formatIndelVCF.fromVCF.equals(alternateAllele)) {
                    statsWriter.addAlternateAllele(alternateAllele);
                }
            }

            for (int sampleIndex = 0; sampleIndex < numberOfSamples; sampleIndex++) {
                final ObjectSet<String> alleles = allelesPerSample[sampleIndex];
                Optional<String> vcfGenotype = alleles.stream().map(

                    originalAllele ->     formatIndelVCF.mapped(merger.mapped(originalAllele))
                ).filter(s->s!=null).reduce((s, s2) -> s + "/" + s2);

                if (vcfGenotype.isPresent()) {

                    statsWriter.setSampleValue(genotypeFieldIndex, sampleIndex, statsWriter.codeGenotype(vcfGenotype.get()));
                } else {
                    statsWriter.setSampleValue(genotypeFieldIndex, sampleIndex, "./.");
                }
                alleleSet.addAll(alleles);
            }
        }
        // write the summary counts, good bases, failed bases:

        for (int sampleIndex = 0; sampleIndex < numberOfSamples; sampleIndex++) {

            sampleAlleleSet.clear();
            SampleCountInfo sci = sampleCounts[sampleIndex];
            int totalCount = 0;
            for (int genotypeIndex = 0; genotypeIndex < sci.getGenotypeMaxIndex(); ++genotypeIndex) {
                final int sampleCount = sci.getGenotypeCount(genotypeIndex);
                totalCount += sampleCount;
            }
            //  System.out.printf("totalCount %d failedCount %d%n",totalCount,sci.failedCount);
            statsWriter.setSampleValue(goodBaseCountFieldIndex, sampleIndex, totalCount);
            statsWriter.setSampleValue(failBaseCountFieldIndex, sampleIndex, sci.failedCount);

            int baseIndex = 0;
            genotypeBuffer.setLength(0);

            final MutableString baseCountString = new MutableString();

            for (int genotypeIndex = 0; genotypeIndex < sci.getGenotypeMaxIndex(); ++genotypeIndex) {
                final int sampleCount = sci.getGenotypeCount(genotypeIndex);
                String genotype = sci.getGenotypeString(genotypeIndex);
                // when a model is available, we used the called alleles to determine if a given genotype is present in
                // the sample. Otherwise, we default to Goby1-2's sampleCount>0 behavior.
                if (sampleCount > 0 && genotypeIndex != SampleCountInfo.BASE_OTHER_INDEX) {

                    genotypeBuffer.append(genotype);
                    genotypeBuffer.append('/');

                }
                if (sampleCount > 0) {
                    baseCountString.append(genotype);
                    baseCountString.append('=');
                    baseCountString.append(Integer.toString(sampleCount));
                    baseCountString.append(',');
                }
            }
            if (baseCountString.length() >= 1) {
                baseCountString.setLength(baseCountString.length() - 1);
            }
            statsWriter.setSampleValue(baseCountFieldIndex, sampleIndex, baseCountString);
        }

        if (siteObserved) {
            Set<String> altAlleles = new ObjectArraySet<>();
            altAlleles.addAll(alleleSet);
            altAlleles.remove(commonReference);
            altGenotypeCount = altAlleles.size();
        }
        if (indelFlagFieldIndex != -1) {
            // set indel flag only when the field is defined (i.e., client has called setInfoFields)
            statsWriter.setFlag(indelFlagFieldIndex, siteHasIndel);
        }
    }

    /**
     * Determine if the candidate reference genotype is new, and keep only those genotypes not already described by
     * longer genotypes.
     *
     * @param genotype
     */

    private void updateReferenceSet(String genotype) {
        if (!isIncludedIn(genotype, referenceSet)) {
            referenceSet.add(genotype);
        }

        for (final String refGenotype : referenceSet) {
            if (refGenotype != null && !genotype.equals(refGenotype) && isIncludedIn(refGenotype, genotype)) {
                referenceSet.remove(refGenotype);
                alleleSet.remove(refGenotype);
                sampleAlleleSet.remove(refGenotype);
            }
        }
    }

    private boolean isIncludedIn(String genotype, ObjectArraySet<String> referenceSet) {
        for (String g : referenceSet) {
            if (isIncludedIn(genotype, g)) return true;
        }
        return false;
    }

    /**
     * Returns true if genotype a is included in b.
     *
     * @param a
     * @param b
     * @return
     */
    private boolean isIncludedIn(String a, String b) {
        return b.startsWith(a);
    }


    public void close() {
        statsWriter.close();
    }

    @Override
    public void setGenome(RandomAccessSequenceInterface genome) {
        this.genome = genome;
    }

    @Override
    public void setGenomeReferenceIndex(int index) {
        this.genomeReferenceIndex = index;
    }


    protected void fillVariantCountArrays
            (SampleCountInfo[] sampleCounts) {


        for (SampleCountInfo csi : sampleCounts) {
            final int sampleIndex = csi.sampleIndex;
            variantsCountPerSample[sampleIndex] = csi.varCount;
            refCountsPerSample[sampleIndex] = csi.refCount;
        }

    }

    /**
     * Used to log debug and informational messages.
     */
    private static final Logger LOG = LoggerFactory.getLogger(GenotypesOutputFormat.class);


}
