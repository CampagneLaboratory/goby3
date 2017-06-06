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
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.VCFFileReader;
import htsjdk.variant.vcf.VCFHeader;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import it.unimi.dsi.logging.ProgressLogger;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.ReaderInputStream;
import org.campagnelab.goby.readers.vcf.VCFParser;
import org.campagnelab.goby.reads.RandomAccessSequenceInterface;
import org.campagnelab.goby.util.Variant;
import org.campagnelab.goby.util.VariantMapCreator;
import org.campagnelab.goby.util.VariantMapHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.List;


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

    private final boolean PAD_ALLELES = false;
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

        VCFFileReader reader = new VCFFileReader(new File(vcfFile.getAbsolutePath()),false);
        VCFHeader header = reader.getFileHeader();


        final String sampleName =sample==null? header.getSampleNamesInOrder().get(0):sample;

        ProgressLogger pg = new ProgressLogger(LOG);
        pg.itemsName = "variants";

        String ref = null;

        pg.start();
        for (VariantContext item : reader) {

            String chromosomeName = adjustPrefix(item.getContig());
            final int positionVCF = item.getStart();
            ref = item.getReference().getBaseString();
            final List<Allele> alternateAlleles = item.getAlternateAlleles();
            String gt = item.getGenotype(sampleName).getGenotypeString();
            int i = 0;
            final String[] alts = new String[alternateAlleles.size()];
            for (Allele alt : alternateAlleles) {
                alts[i++] = alt.getBaseString();
            }
            String expandedGT = convertGT(gt, ref, alts[0], alts.length==2?alts[1]:"");
            String[] expandedAlleles = expandedGT.split("\\||\\\\|/");

            // VCF is one-based, Goby zero-based. We convert here:
            int positionGoby = positionVCF - 1;
            ObjectArraySet<Variant.FromTo> alleleSet = new ObjectArraySet<>(expandedAlleles.length);
            for (i = 0; i < expandedAlleles.length; i++) {
                alleleSet.add(new Variant.FromTo(ref, expandedAlleles[i]));
            }
            if (genome.getReferenceIndex(chromosomeName) < 0) {
                System.out.printf("Unable to find chromosome %s in genome", chromosomeName);
                System.exit(1);
            }
            chMap.addVariant(positionGoby, chromosomeName, genome.get(genome.getReferenceIndex(chromosomeName), positionGoby), alleleSet);

            pg.update();
        }
        pg.stop();

        chMap.saveMap(outputMapname);
        System.out.println("NumIndels Encountered: " + Variant.numIndelsEncountered +
                "\nNumber of indels ignored due to variant overlap: " + chMap.numOverlaps +
                "\nNumber of ignored, mis-matching 'from's of indels: " + Variant.numFromMistmaches);
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


    /*
    * Convert vcf gt format, ie 1|0 , to expanded format, ie ACCCCT|G
    * This is only compatible with VCF's with ONE SAMPLE, as in the platinum genome vcf's.
     */
    private String convertGT(String origGT, String ref, String alt1, String alt2) {
        int maxLength = Math.max(ref.length(), Math.max(alt1.length(), alt2.length()));
        String padRef = ref;
        String padAlt1 = alt1;
        String padAlt2 = alt2;
        if (PAD_ALLELES) {
            padRef = pad(ref, maxLength);
            padAlt1 = pad(alt1, maxLength);
            padAlt2 = pad(alt2, maxLength);
        }
        //operation below assumes that genotypes and delimiters never contain characters 0,1,or 2.
        return origGT.replace("0", padRef).replace("1", padAlt1).replace("2", padAlt2);
    }


    private String pad(String allele, int len) {
        StringBuilder padAllele = new StringBuilder(allele);
        for (int i = 1; i <= len; i++) {
            if (allele.length() < len) {
                padAllele.append("-");
            }
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