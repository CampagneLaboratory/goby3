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
import edu.cornell.med.icb.iterators.TsvLineIterator;
import it.unimi.dsi.fastutil.chars.CharSemiIndirectHeaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.*;
import it.unimi.dsi.logging.ProgressLogger;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.campagnelab.goby.readers.vcf.VCFParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import it.unimi.dsi.fastutil.io.BinIO;

import java.io.*;
import java.util.Collection;
import java.util.Iterator;
import java.util.zip.GZIPOutputStream;


/**
 * Creates a genotype map that can be used later to add calls to an sbi file
 *
 * @author Remi Torracinta
 */
public class VCFToMapMode extends AbstractGobyMode {
    /**
     * Used to log debug and informational messages.
     */
    private static final Logger LOG = LoggerFactory.getLogger(VCFToMapMode.class);

    /**
     * The input files.
     */
    private File vcfFile;
    private Object2ObjectMap<String, Int2ObjectMap<String>> chMap;
    /**
     * The output map filename.
     */
    private String outputMapname;


    /**
     * The mode name.
     */
    private static final String MODE_NAME = "genotype-map-mode";

    /**
     * The mode description help text.
     */
    private static final String MODE_DESCRIPTION = "Create a genotype map from VCF file. Map is used to make a genotyping sbi dataset for neural network testing/training.";


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

        chMap = new Object2ObjectOpenHashMap<String, Int2ObjectMap<String>>(40);

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
        VCFParser parser = new VCFParser(new FileReader(vcfFile));
        try {
            parser.readHeader();
        } catch (VCFParser.SyntaxException e) {
            e.printStackTrace();
        }

        final int chromosomeColumnIndex = parser.getGlobalFieldIndex("CHROM", "VALUE");
        final int positionColumnIndex = parser.getGlobalFieldIndex("POS", "VALUE");
        final int refColumnIndex = parser.getGlobalFieldIndex("REF", "VALUE");
        final int altColumnIndex = parser.getGlobalFieldIndex("ALT", "VALUE");
        //final int indelFlagColumnIndex = parser.getGlobalFieldIndex("INFO", "INDEL");
        final String sampleName = parser.getColumnNamesUsingFormat()[0];
        final int globalFieldIndexSample = parser.getGlobalFieldIndex(sampleName, "GT");


        ProgressLogger pg = new ProgressLogger(LOG);
        pg.itemsName = "variants";

        String positionStr = null;
        CharSequence ref = null;
        pg.start();
        while (parser.hasNextDataLine()) {
            String chromosomeName = parser.getColumnValue(chromosomeColumnIndex).toString();
            String posOld = positionStr;
            CharSequence refOld = ref;

            positionStr = parser.getColumnValue(positionColumnIndex).toString();
            ref = parser.getColumnValue(refColumnIndex);
            //check that position is actually iterating
//            if (ref == refOld && positionStr == posOld){
//                break;
//            }
            final CharSequence alt = parser.getColumnValue(altColumnIndex);
            final CharSequence gt = parser.getStringFieldValue(globalFieldIndexSample - 1);
            final String paddedAlt = alt + ",N";
            final String[] alts = paddedAlt.toString().split(",");
            if (!chMap.containsKey(chromosomeName)) {
                chMap.put(chromosomeName, new Int2ObjectArrayMap<String>(50000));
            }
            String expandedGT = convertGT(gt.toString(), ref.toString(), alts[0], alts[1]);
            final int positionVCF = Integer.parseInt(positionStr);
            // VCF is one-based, Goby zero-based. We convert here:
            int positionGoby=positionVCF-1;
            chMap.get(chromosomeName).put(positionGoby, expandedGT);
            parser.next();
            pg.update();
        }
        pg.stop();
        BinIO.storeObject(chMap, new File(outputMapname));
    }


    /*
    * Convert vcf gt format, ie 1|0 , to expanded format, ie ACCCCT|G
    * This is only compatible with VCF's with ONE SAMPLE, as in the platinum genome vcf's.
     */
    private String convertGT(String origGT, String ref, String alt1, String alt2) {
        //operation below assumes that genotypes and delimiters never contain characters 0,1,or 2.
        return origGT.replace("0", ref).replace("1", alt1).replace("2", alt2);
    }


    /**
     * @param args command line arguments
     * @throws java.io.IOException                    IO error
     * @throws com.martiansoftware.jsap.JSAPException command line parsing error.
     */
    public static void main(final String[] args) throws IOException, JSAPException {
        new VCFToMapMode().configure(args).execute();
    }

}