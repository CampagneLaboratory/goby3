/*
 * Copyright (C) 2009-2010 Institute for Computational Biomedicine,
 *                    Weill Medical College of Cornell University
 *
 *  This file is part of the Goby IO API.
 *
 *     The Goby IO API is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Lesser General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     The Goby IO API is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Lesser General Public License for more details.
 *
 *     You should have received a copy of the GNU Lesser General Public License
 *     along with the Goby IO API.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.campagnelab.goby.alignments.filters;

import edu.cornell.med.icb.identifier.IndexedIdentifier;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.io.FastBufferedReader;
import it.unimi.dsi.io.LineIterator;
import it.unimi.dsi.lang.MutableString;

import java.io.FileNotFoundException;
import java.io.FileReader;

/**
 * @author Fabien Campagne Date: Jul 26, 2007 Time: 12:53:01 PM
 */
public class GeneTranscriptRelationships {
    protected final IndexedIdentifier geneId2Index;
    final Int2ObjectMap<MutableString> geneIndex2GeneId;
    final Int2ObjectOpenHashMap<IntSet> gene2TranscriptIndices;
    private int geneIndexCounter;
    private final Int2IntMap transcriptIndex2GeneIndex;

    /**
     * Returns the number of genes associated with transcripts.
     *
     * @return the number of genes associated with transcripts.
     */
    public int getNumberOfGenes() {
        return geneId2Index.size();
    }

    public GeneTranscriptRelationships() {
        super();
        geneId2Index = new IndexedIdentifier();
        this.geneId2Index.defaultReturnValue(-1);
        gene2TranscriptIndices = new Int2ObjectOpenHashMap<IntSet>();
        transcriptIndex2GeneIndex = new Int2IntOpenHashMap();
        transcriptIndex2GeneIndex.defaultReturnValue(-1);
        geneIndex2GeneId = new Int2ObjectOpenHashMap<MutableString>();
    }

    public GeneTranscriptRelationships(final IndexedIdentifier geneIndices) {
        super();
        geneId2Index = geneIndices;
        this.geneId2Index.defaultReturnValue(-1);
        gene2TranscriptIndices = new Int2ObjectOpenHashMap<IntSet>();
        transcriptIndex2GeneIndex = new Int2IntOpenHashMap();
        geneIndex2GeneId = new Int2ObjectOpenHashMap<MutableString>();
    }


    public void addRelationship(final MutableString geneId, final int transcriptIndex) {
        final int geneIndex = getGeneIndex(geneId);
        addRelationship(geneIndex, transcriptIndex);
    }

    public void addRelationship(final int geneIndex, final int transcriptIndex) {
        IntSet transcriptIds = gene2TranscriptIndices.get(geneIndex);
        if (transcriptIds == null) {
            transcriptIds = new IntArraySet();
            gene2TranscriptIndices.put(geneIndex, transcriptIds);
        }
        transcriptIds.add(transcriptIndex);
        transcriptIndex2GeneIndex.put(transcriptIndex, geneIndex);
    }

    /**
     * Get the index of a gene. Registers the gene if it did not exist. Always
     * returns a valid index.
     *
     * @param geneId Identifier of the gene.
     * @return Index of the gene.
     */
    protected int getGeneIndex(final MutableString geneId) {
        int index = geneId2Index.getInt(geneId);
        if (index == -1) {
            geneId2Index.put(geneId, geneIndexCounter);
            this.geneIndex2GeneId.put(geneIndexCounter, geneId);
            index = geneIndexCounter;
            ++geneIndexCounter;
        }
        return index;
    }

    /**
     * Obtains the gene id from a gene index.
     *
     * @param geneIndex Index of the gene.
     */
    public MutableString getGeneId(final int geneIndex) {
        return geneIndex2GeneId.get(geneIndex);
    }

    /**
     * Get the set of transcript indices for that that gene. Returns transcripts
     * coded by this gene.
     *
     * @param geneId Gene id
     * @return Set of transcript indices
     */
    public IntSet getTranscriptSet(final MutableString geneId) {
        return getTranscriptSet(getGeneIndex(geneId));
    }

    /**
     * Get the set of transcript indices for that that gene.
     *
     * @param geneIndex Index of the gene
     * @return Set of transcript indices
     */
    public IntSet getTranscriptSet(final int geneIndex) {
        return this.gene2TranscriptIndices.get(geneIndex);
    }

    public IntSet transcript2Genes(final int[] transcriptIndices) {
        final IntSet geneSet = new IntOpenHashSet();
        for (final int transcriptIndex : transcriptIndices) {
            geneSet.add(transcript2Gene(transcriptIndex));
        }
        return geneSet;
    }

    public int transcript2Gene(final int transcriptIndex) {
        return transcriptIndex2GeneIndex.get(transcriptIndex);
    }


    /**
     * Converts a set of genes to a set of transcripts.
     *
     * @param genes Genes to convert.
     * @return set of transcripts coded by genes.
     */
    public IntSet gene2Transcripts(final IntSet genes) {
        final IntSet trancriptSet = new IntOpenHashSet();
        for (final int geneIndex : genes) {
            final IntSet transcripts = getTranscriptSet(geneIndex);
            if (transcripts != null) {
                trancriptSet.addAll(transcripts);
            }
        }
        return trancriptSet;
    }

    /**
     * Loads the relationships from a tab delimited file. Format is geneId
     * transcriptid. Lines starting with # are ignored.
     *
     * @param geneTranscriptRelFilename
     * @return The  transcript identifiers found in the input.
     * @throws FileNotFoundException If the relationship file is not found.
     */
    public IndexedIdentifier load(final String geneTranscriptRelFilename) throws FileNotFoundException {
        final FastBufferedReader reader;
        final IndexedIdentifier transcriptIds = new IndexedIdentifier();

        reader = new FastBufferedReader(new FileReader(geneTranscriptRelFilename));
        final LineIterator lit = new LineIterator(reader);
        while (lit.hasNext()) {
            final MutableString line = lit.next();
            if (line.startsWith("#")) {
                continue;
            }

            final String[] tokens = line.toString().split("[\t ]");
            final MutableString geneId = new MutableString(tokens[0]).compact();
            if (tokens.length < 2) {
                continue;
            }
            final String transcriptId = tokens[1];
            final int transcriptIndex =
                    transcriptIds.registerIdentifier(new MutableString(transcriptId).compact());
            addRelationship(geneId, transcriptIndex);

        }
        return transcriptIds;
    }
}
