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
import it.unimi.dsi.logging.ProgressLogger;
import org.campagnelab.goby.alignments.processors.ObservedIndel;
import org.campagnelab.goby.util.KnownIndelSetCreator;
import org.campagnelab.goby.util.Variant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;


/**
 * Creates a genotype map that can be used later to add calls to an sbi file
 *
 * @author Remi Torracinta
 */
public class VCFToKnownIndelsMode extends AbstractGobyMode {
    /**
     * Used to log debug and informational messages.
     */
    private static final Logger LOG = LoggerFactory.getLogger(VCFToKnownIndelsMode.class);

    /**
     * The input files.
     */
    private File vcfFile;
    private KnownIndelSetCreator knownIndelSets;
    /**
     * The output map filename.
     */
    private String outputSetName;


    private static final String MODE_NAME = "vcf-to-indel-set";

    /**
     * The mode description help text.
     */
    private static final String MODE_DESCRIPTION = "Create a an indel set from a VCF file. " +
            "The resulting set can be used to know what indels exist at nearby positions when realigning near indels.";
    private String chrPrefix;
    private boolean addPrefix;
    private boolean removePrefix;
    private int addedIndels;


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
        outputSetName = jsapResult.getString("output");
        knownIndelSets = new KnownIndelSetCreator();
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

        VCFFileReader parser = new VCFFileReader(vcfFile);
        ProgressLogger pg = new ProgressLogger(LOG);
        pg.itemsName = "variants";
        // we can't easily estimate the number of lines when the file is compressed (typically is).
        Iterator<VariantContext> vcfIterater = parser.iterator();

        pg.start();
        while (vcfIterater.hasNext()) {
            VariantContext var = vcfIterater.next();
            pg.update();
            if (!var.isIndel()){
                continue;
            }
            String chromosomeName = var.getContig();
            for (Allele allele : var.getAlternateAlleles()){
                Variant.FromTo vcfFromTo = new Variant.FromTo(var.getReference().getBaseString(),allele.getBaseString());
                Variant.GobyIndelFromVCF gobyIndel = new Variant.GobyIndelFromVCF(vcfFromTo,var.getStart());
                ObservedIndel indel = new ObservedIndel(gobyIndel.getAllelePos(),gobyIndel.getGobyFromTo().getFrom(),gobyIndel.getGobyFromTo().getTo());
                knownIndelSets.addIndel(chromosomeName,indel);
                addedIndels++;
            }
        }
        pg.stop();
        knownIndelSets.saveSet(outputSetName);
        System.out.println(addedIndels + " indels added to known set, located in: \n" + outputSetName);
    }

    /**
     * @param args command line arguments
     * @throws IOException                    IO error
     * @throws JSAPException command line parsing error.
     */
    public static void main(final String[] args) throws IOException, JSAPException {
        new VCFToKnownIndelsMode().configure(args).execute();
    }

}