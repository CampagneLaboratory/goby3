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
import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.VCFFileReader;
import htsjdk.variant.vcf.VCFHeader;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.logging.ProgressLogger;
import org.campagnelab.goby.reads.RandomAccessSequenceInterface;
import org.campagnelab.goby.util.Variant;
import org.campagnelab.goby.util.VariantMapCreator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;


/**
 * Creates a genotype map that can be used later to add calls to an sbi file
 *
 * @author Remi Torracinta
 */
public class VCFToGenotypeMapMode extends AbstractGobyMode {
    /**
     * Used to log debug and informational messages.
     */
    private static final Logger LOG = LoggerFactory.getLogger(VCFToGenotypeMapMode.class);

    /**
     * The input files.
     */
    private File vcfFile;
    private VariantMapCreator chMap;
    /**
     * The output map filename.
     */
    private String outputMapname;

    /**
     * goby format genome
     */
    private RandomAccessSequenceInterface genome;

    private final boolean PAD_ALLELES = true;
    /**
     * The mode name.
     */
    private static final String MODE_NAME = "vcf-to-genotype-map";

    /**
     * The mode description help text.
     */
    private static final String MODE_DESCRIPTION = "Create a genotype map from a VCF file. " +
            "The resulting map can be used to annotate an sbi dataset with true genotype labels. " +
            "See the variationanalysis project for details.";
    private String chrPrefix;
    private boolean addPrefix;
    private boolean removePrefix;
    // Name of the sample for which the genotype is sought:
    private String sample;
    private int numIndelsEncountered;
    private int numOtherVariations;


    @Override
    public String getModeName() {
        return MODE_NAME;
    }

    @Override
    public String getModeDescription() {
        return MODE_DESCRIPTION;
    }

    /**
     * Configure.
     *
     * @param args command line arguments
     * @return this object for chaining
     * @throws IOException   error parsing
     * @throws JSAPException error parsing
     */
    @Override
    public AbstractCommandLineMode configure(final String[] args)
            throws IOException, JSAPException {
        final JSAPResult jsapResult = parseJsapArguments(args);
        setInputFilenames(jsapResult.getString("vcfInput"));
        outputMapname = jsapResult.getString("output");
        chrPrefix = jsapResult.getString("chromosome-prefix");
        if (chrPrefix == null) {
            chrPrefix = "";
        } else {
            if (chrPrefix.startsWith("+")) {
                addPrefix = true;
                chrPrefix = chrPrefix.substring(1);
            }
            if (chrPrefix.startsWith("-")) {
                removePrefix = true;
                chrPrefix = chrPrefix.substring(1);
            }
        }
        genome = DiscoverSequenceVariantsMode.configureGenome(jsapResult);
        chMap = new VariantMapCreator(genome);
        return this;
    }


    /**
     * Clear the input files list.
     */
    public synchronized void clearInputFiles() {
        vcfFile = null;
    }

    /**
     * Set the input filenames.
     *
     * @param vcfInput the input filename
     */
    public synchronized void setInputFilenames(final String vcfInput) {
        clearInputFiles();
        vcfFile = new File(vcfInput);
    }

    /**
     * Get the input filenames.
     *
     * @return the input filenames
     */
    public synchronized String getInputFilenames() {
        String vcfString = "";
        if (vcfFile != null) {
            vcfString = vcfFile.toString();
        }
        return vcfString;
    }


    /**
     * Compare VCF files.
     *
     * @throws IOException
     */
    @Override
    public void execute() throws IOException {

        int numVcfItems = 0;

        VCFFileReader reader = new VCFFileReader(new File(vcfFile.getAbsolutePath()), false);
        VCFHeader header = reader.getFileHeader();


        final String sampleName = sample == null ? header.getSampleNamesInOrder().get(0) : sample;

        ProgressLogger pg = new ProgressLogger(LOG);
        pg.itemsName = "variants";

        String ref = null;

        pg.start();
        for (VariantContext item : reader) {
            String chromosomeName = adjustPrefix(item.getContig());
            final int positionVCF = item.getStart();
            ref = item.getReference().getBaseString();
            final List<Allele> allAlleles = item.getAlleles();
            String gt = item.getGenotype(sampleName).getGenotypeString();
            Set<String> genotype = getAlleles(gt);
            // VCF is one-based, Goby zero-based. We convert here:
            int positionGoby = positionVCF - 1;
            int maxLength = ref.length();

            int i = 0;
            final String[] alts = new String[allAlleles.size()];
            for (Allele alt : allAlleles) {
                alts[i] = alt.getBaseString();
                maxLength = Math.max(alts[i].length(), maxLength);
                i++;
            }
            // We need to pad by adding - characters since the VCF parser removes them when reading.
            String paddedRef = pad(ref, maxLength);
            String[] paddedAlts = new String[alts.length];
            for (i = 0; i < alts.length; i++) {
                paddedAlts[i] = pad(alts[i], maxLength);
            }

            ObjectArraySet<Variant.FromTo> alleleSet = new ObjectArraySet<>(paddedAlts.length);
            for (i = 0; i < paddedAlts.length; i++) {
                if (genotype.contains(alts[i])) {
                    alleleSet.add(new Variant.FromTo(paddedRef, paddedAlts[i]));
                }
            }
            if (genome.getReferenceIndex(chromosomeName) < 0) {
                System.out.printf("Unable to find chromosome %s in genome", chromosomeName);
                System.exit(1);
            }

            final char reference = genome.get(genome.getReferenceIndex(chromosomeName), positionGoby);
            chMap.addVariant(positionGoby, chromosomeName, reference, alleleSet);

            if (item.isIndel()) {
                numIndelsEncountered++;
            } else {
                numOtherVariations++;
            }
            pg.update();

        }
        pg.stop();
        chMap.saveMap(outputMapname);
        chMap.showStats();

    }

    private String adjustPrefix(String chrName) {
        if (addPrefix) {
            return chrPrefix + chrName;
        }
        if (removePrefix) {
            if (chrName.startsWith(chrPrefix)) {
                return chrName.substring(chrPrefix.length());
            }
        }
        return chrName;
    }


    public static Set<String> getAlleles(String genotype) {
        String trueGenotype = genotype.toUpperCase();
        ObjectSet<String> result = new ObjectArraySet<>();
        Collections.addAll(result, trueGenotype.split("[|/]"));
        result.remove("|");
        result.remove("/");
        result.remove("?");
        result.remove(".");
        result.remove("");
        return result;
    }

    private String pad(String allele, int len) {
        StringBuilder padAllele = new StringBuilder(allele);
        for (int i = 0; i < len - allele.length(); i++) {
            padAllele.append("-");
        }
        return padAllele.toString();
    }

    /**
     * @param args command line arguments
     * @throws java.io.IOException                    IO error
     * @throws com.martiansoftware.jsap.JSAPException command line parsing error.
     */
    public static void main(final String[] args) throws IOException, JSAPException {
        new VCFToGenotypeMapMode().configure(args).execute();
    }

}