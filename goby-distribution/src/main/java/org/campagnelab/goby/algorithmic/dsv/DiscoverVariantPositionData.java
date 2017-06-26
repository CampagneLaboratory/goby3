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

package org.campagnelab.goby.algorithmic.dsv;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import org.campagnelab.goby.algorithmic.indels.EquivalentIndelRegion;
import org.campagnelab.goby.alignments.Alignments;
import org.campagnelab.goby.alignments.PositionBaseInfo;

import java.util.Collections;
import java.util.Random;

/**
 * Stores information collected about each genomic position inspected by IterateSortedAlignmentsImpl (used by
 * DiscoverSequenceVariantsMode).
 *
 * @author Fabien Campagne
 *         Date: 6/6/11
 *         Time: 3:27 PM
 */
public class DiscoverVariantPositionData extends ObjectArrayList<PositionBaseInfo> {
    private static final long serialVersionUID = 9212001398502402859L;
    private char referenceBase;
    private ObjectArraySet<EquivalentIndelRegion> candidateIndels;
    private int position;
    private ObjectArraySet<EquivalentIndelRegion> failedIndels;
    private static final ObjectArraySet<EquivalentIndelRegion> EMPTY_SET = new ObjectArraySet<EquivalentIndelRegion>();
    private int numObservations;
    public int SUB_SAMPLE_SIZE=Integer.MAX_VALUE;

    public int getZeroBasedPosition() {
        return position;
    }

    public DiscoverVariantPositionData() {
        super();
        position = -1;
        filtered = new ObjectArraySet[5];
        for (int baseIndex = 0; baseIndex < SampleCountInfo.BASE_MAX_INDEX; baseIndex++) {
            filtered[baseIndex] = new ObjectArraySet();
        }

    }

    @Override
    public void clear() {
        super.clear();
        if (candidateIndels != null) {
            candidateIndels.clear();
        }
        if (failedIndels != null) {
            failedIndels.clear();
        }
        if (filtered != null) {
            for (ObjectArraySet set : filtered) {
                set.clear();
            }
        }
        numObservations=0;
    }

    /**
     * Count of genotypes that were flagged for removal by some filter in this sample.
     */
    public ObjectArraySet filtered[];

    @Override
    public String toString() {
        return String.format("pos=%d #bases: %d #indels: %d", position, size(), hasCandidateIndels() ? getIndels().size() : 0);
    }

    public DiscoverVariantPositionData(final int position, char referenceBase) {
        this();
        this.position = position;
        this.referenceBase = referenceBase;
    }

    /**
     * This method is called if a candidate indel is observed whose start position overlaps with position.
     *  @param candidateIndel the candidate indels observed with the same startPosition == position
     * @param alignmentEntry
     */
    public void observeCandidateIndel(final EquivalentIndelRegion candidateIndel, Alignments.AlignmentEntry alignmentEntry) {
        if (candidateIndels == null) {
            candidateIndels = new ObjectArraySet<EquivalentIndelRegion>();
        }
        candidateIndel.supportingEntries.add(alignmentEntry);

        if (!candidateIndels.contains(candidateIndel)) {
            candidateIndels.add(candidateIndel);

            // System.out.println(candidateIndels);
            // assert candidateIndels.contains(candidateIndel) : "indel must have been added.";
        } else {
            for (final EquivalentIndelRegion eir : candidateIndels) {
                if (eir.equals(candidateIndel)) {
                    eir.mergeInto(candidateIndel,alignmentEntry);
                }
            }
        }
    }

    public ObjectArraySet<EquivalentIndelRegion> getIndels() {

        return candidateIndels;
    }

    /**
     * Mark an indel observation as failing genotype filters.
     *
     * @param indel the candidate indel that failed tests.
     */
    public void failIndel(final EquivalentIndelRegion indel) {
        if (candidateIndels != null) {
            candidateIndels.remove(indel);
        }
        if (failedIndels == null) {
            failedIndels = new ObjectArraySet<EquivalentIndelRegion>();
        }
        failedIndels.add(indel);
    }

    /**
     * Test if this set of genotype observation includes indels.
     *
     * @return True when the genotype observed include indels.
     */
    public boolean hasCandidateIndels() {
        return candidateIndels != null && !candidateIndels.isEmpty();
    }

    public char getReferenceBase() {
        return referenceBase;
    }

    public ObjectArraySet<EquivalentIndelRegion> getFailedIndels() {
        if (failedIndels == null) {
            return EMPTY_SET;
        } else {
            return failedIndels;
        }
    }

    public String completeToString() {
        return super.toString();

    }

    public void printAll() {
        System.out.println(completeToString());
    }

    /**
     * Sub-sample this list to keep the specified maximum number of elements.
     *
     * @param numberToKeep the number of elements to keep in the list after sub-sampling
     */
    public void subSample(int numberToKeep) {

        final int size = this.size();
        if (numberToKeep >= size) return;

        Collections.shuffle(this);
        for (int i = size - 1; i >= numberToKeep; --i) {
            this.remove(i);
        }
    }

    Random random = new Random();

    @Override
    public boolean add(PositionBaseInfo positionBaseInfo) {
       assert SUB_SAMPLE_SIZE!=0: "SUB_SAMPLE_SIZE cannot be zero";
        numObservations++;
        float samplingRate = 1;
        if (numObservations > SUB_SAMPLE_SIZE) {
            // make the sampling rate decrease with the total number of observations that would have been made so far:
            samplingRate = 1 / (1 + ((float)numObservations / (float)SUB_SAMPLE_SIZE));
            if (random.nextFloat()<samplingRate) {
                return super.add(positionBaseInfo);
            }
        }
        return super.add(positionBaseInfo);
    }

    public int numObservations() {
        return numObservations;
    }

    public void setReferenceBase(char referenceBase) {
        this.referenceBase = referenceBase;
    }
}
