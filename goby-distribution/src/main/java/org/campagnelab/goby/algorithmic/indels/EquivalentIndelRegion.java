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

package org.campagnelab.goby.algorithmic.indels;

import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import it.unimi.dsi.lang.MutableString;
import org.campagnelab.goby.alignments.Alignments;

import java.util.Set;

/**
 * Stores information about the span of equivalent indels.
 *
 * @author Fabien Campagne
 *         Date: 6/7/11
 *         Time: 6:15 PM
 */
public class EquivalentIndelRegion {
    /**
     * Index of the reference sequence on which the indel is observed.
     */
    public int referenceIndex;
    /**
     * Start position (zero-based) of the position after which the indel occurs.
     * ACT^{GG} bases   in this case, the start position of the GG insertion is 2 and its
     * 0123456  pos     end position is 3
     */
    public int startPosition;
    public int endPosition;
    public String from;
    public String to;
    public String flankLeft;
    public String flankRight;
    public Set<Alignments.AlignmentEntry> supportingEntries=new ObjectArraySet<>();
    /**
     * The number of times the candidate indel was observed. Start at one, increment as needed.
     */
    private int forwardFrequency = 0;
    private int reverseFrequency = 0;
    /**
     * The index of the sample where these indels were observed.
     */
    public int sampleIndex;
    private boolean matchesReference;
    private boolean matchesRefCached;
    /**
     * same as above for only forward read indices.
     */
    public IntArraySet forwardReadIndices = new IntArraySet();
    /**
     * Same as above for only reverse read indices.
     */
    public IntArraySet reverseReadIndices = new IntArraySet();
    /**
    /**
     * Quality scores across the length of the indel.
     */
    public Set<byte[]> forwardQualityScores = new ObjectArraySet<>();
     /**
     * Quality scores across the length of the indel.
     */
    public Set<byte[]> reverseQualityScores = new ObjectArraySet<>();
    /**
     * Indicate that this indel was filtered out.
     */
    private boolean filtered;

    /**
     * Return the from bases, surrounded by flankLeft and flankRight bases.
     *
     * @return from bases in context of the flanking sequence.
     */
    public String fromInContext() {
        MutableString fromC = new MutableString();
        fromC.append(flankLeft);
        fromC.append(from);
        fromC.append(flankRight);
        return fromC.toString();
    }

    /**
     * Return the to bases, surrounded by flankLeft and flankRight bases.
     *
     * @return to bases in context of the flanking sequence.
     */
    public String toInContext() {
        MutableString toC = new MutableString();
        toC.append(flankLeft);
        toC.append(to);
        toC.append(flankRight);
        return toC.toString();
    }

    /**
     * Compares startPosition, endPosition, referenceIndex and sampleIndex.
     *
     * @param o other object
     * @return True if the other object matches location and sampleIndex, False otherwise.
     */
    @Override
    public final boolean equals(final Object o) {
        if (!(o instanceof EquivalentIndelRegion)) {
            return false;
        }
        final EquivalentIndelRegion other = (EquivalentIndelRegion) o;
        return startPosition == other.startPosition
                && endPosition == other.endPosition
                && referenceIndex == other.referenceIndex
                && sampleIndex == other.sampleIndex
                && from.equals(other.from)
                && to.equals(other.to);

    }

    @Override
    public final int hashCode() {
        return (sampleIndex * 31 + (referenceIndex * 31 + endPosition)) * 31 + startPosition;
    }

    /**
     * Returns a string representation of the eir, in the format "flankingLeft from/to flankingRight start-end"
     *
     * @return a string
     */
    @Override

    public String toString() {
        return String.format("indel count=%d,%d %s %s/%s %s %d-%d filtered=%b",
                getForwardFrequency(), getReverseFrequency(), flankLeft, from, to, flankRight,
                startPosition, endPosition, filtered);
    }

    /**
     * Make a copy of this indel, with zero count.
     *
     * @return copy of this indel region.
     */
    public EquivalentIndelRegion copy() {
        final EquivalentIndelRegion result = new EquivalentIndelRegion();
        result.from = from;
        result.to = to;
        result.flankLeft = flankLeft;
        result.flankRight = flankRight;
        result.referenceIndex = referenceIndex;
        result.startPosition = startPosition;
        result.endPosition = endPosition;
        result.sampleIndex = sampleIndex;
        result.supportingEntries.addAll(supportingEntries);
        result.forwardFrequency=forwardFrequency;
        result.reverseFrequency=reverseFrequency;
        return result;

    }

    public boolean matchesReference() {
        if (!matchesRefCached) {
            matchesReference = from.equals(to);
            matchesRefCached = true;
        }
        return matchesReference;
    }

    /**
     * Indicate that this indel was filtered out. getFrequency() will subsequently return zero.
     */
    public void markFiltered() {
        filtered = true;
    }

    /**
     * Remove the filtered flag from an indel.
     */
    public void removeFiltered() {
        filtered = false;
    }

    public boolean isFiltered() {
        return filtered;
    }


    public void incrementForwardFrequency() {
        this.forwardFrequency++;
    }

    public void incrementReverseFrequency() {
        this.reverseFrequency++;
    }


    public void setForwardFrequency(int frequency) {
        this.forwardFrequency = frequency;
    }

    public void setReverseFrequency(int frequency) {
        this.reverseFrequency = frequency;
    }

    /**
     * Return the forward frequency of this indel. Filtered indels always return zero.
     *
     * @return frequency of the indel.
     */
    public int getReverseFrequency() {
        return filtered ? 0 : reverseFrequency;
    }

    /**
     * Return the forward frequency of this indel. Filtered indels always return zero.
     *
     * @return frequency of the indel.
     */
    public int getForwardFrequency() {
        return filtered ? 0 : forwardFrequency;
    }

    /**
     * Return the total frequency of this indel. Filtered indels always return zero.
     *
     * @return frequency of the indel.
     */
    public int getFrequency() {
        return getForwardFrequency()+getReverseFrequency();
    }


    /**
     * Merge information from candidateIndel into this indel.
     * @param candidateIndel
     * @param alignmentEntry
     */
    public void mergeInto(EquivalentIndelRegion candidateIndel, Alignments.AlignmentEntry alignmentEntry) {
        if (alignmentEntry.getMatchingReverseStrand()){
            incrementReverseFrequency();
        } else {
            incrementForwardFrequency();
        }
        supportingEntries.addAll(candidateIndel.supportingEntries);
        supportingEntries.add(alignmentEntry);
        // since the EIR match, increase the number of distinct read indices observed for the overlap:
        forwardReadIndices.addAll(candidateIndel.forwardReadIndices);
        reverseReadIndices.addAll(candidateIndel.reverseReadIndices);
        forwardQualityScores.addAll(candidateIndel.forwardQualityScores);
        reverseQualityScores.addAll(candidateIndel.reverseQualityScores);


    }
}
