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
import edu.cornell.med.icb.identifier.IndexedIdentifier;
import it.unimi.dsi.lang.MutableString;
import org.campagnelab.goby.alignments.ConcatAlignmentReader;
import org.campagnelab.goby.reads.DualRandomAccessSequenceCache;
import org.campagnelab.goby.reads.RandomAccessSequenceInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Converts a VCF file to tab delimited format.
 *
 * @author Fabien Campagne
 * @since Goby 1.9.8
 */
public class CompareAlignmentToGenomeMode extends AbstractGobyMode {
    /**
     * The mode name.
     */
    private static final String MODE_NAME = "compare-alignment-to-genome";

    /**
     * The mode description help text.
     */
    private static final String MODE_DESCRIPTION = "Compare the sequences named in an alignment to a genome. Report sequences missing in the genome.";


    private String[] inputFiles;

    private static final Logger LOG = LoggerFactory.getLogger(CompareAlignmentToGenomeMode.class);
    private String genomeBasename;


    public CompareAlignmentToGenomeMode() {
    }


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

        inputFiles = jsapResult.getStringArray("input");
        genomeBasename = jsapResult.getString("genome");

        return this;
    }



    @Override
    public void execute() throws IOException {
        ConcatAlignmentReader concat = new ConcatAlignmentReader(inputFiles);
        concat.readHeader();

        IndexedIdentifier ids = concat.getTargetIdentifiers();
        RandomAccessSequenceInterface genome = null;
        if (genomeBasename != null) {
            System.err.println("Loading genome " + genomeBasename);
            final DualRandomAccessSequenceCache aGenome = new DualRandomAccessSequenceCache();
            try {
                aGenome.load(genomeBasename);
                genome = aGenome;
            } catch (ClassNotFoundException e) {
                System.err.println("Unable to load genome.");
                System.exit(1);
            }
            System.err.println("Done loading genome ");
        }
        if (genome==null) {
            System.err.println("Unable to load genome");
            System.exit(1);
        }
        for (MutableString targetSequence : ids.keySet()) {
            if (genome.getReferenceIndex(targetSequence.toString()) == -1) {
                System.out.println("Missing reference in genome: " + targetSequence);
            }
        }
    }


    /**
     * Main method.
     *
     * @param args command line args.
     * @throws JSAPException error parsing
     * @throws IOException   error parsing or executing.
     */

    public static void main
    (
            final String[] args) throws JSAPException, IOException {
        new CompareAlignmentToGenomeMode().configure(args).execute();
    }
}
