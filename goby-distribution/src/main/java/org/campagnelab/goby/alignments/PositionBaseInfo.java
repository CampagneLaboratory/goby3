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

package org.campagnelab.goby.alignments;

/**
 * Base info for class IterateSortedAlignmentsListImpl.
 *
 * @author Fabien Campagne
 *         Date: Mar 21, 2011
 *         Time: 1:53:47 PM
 */
public class PositionBaseInfo {
    public PositionBaseInfo() {

    }

    public PositionBaseInfo(PositionBaseInfo other) {
        this.readIndex = other.readIndex;
        this.readerIndex = other.readerIndex;
        this.qualityScore = other.qualityScore;
        this.readMappingQuality = other.readMappingQuality;
        this.matchesReference = other.matchesReference;
        this.from = other.from;
        this.to = other.to;
        this.position = other.position;
        this.matchesForwardStrand = other.matchesForwardStrand;
        this.numVariationsInRead = other.numVariationsInRead;
        this.insertSize = other.insertSize;
        this.alignmentEntry = other.alignmentEntry;
    }

    public int readIndex;
    public int readerIndex;
    public byte qualityScore = Byte.MIN_VALUE;
    public byte readMappingQuality = Byte.MIN_VALUE;
    public boolean matchesReference;
    public char from = ' ';
    public char to = ' ';
    /**
     * Zero-based position on the reference sequence.
     */
    public int position;
    public boolean matchesForwardStrand;
    /**
     * The number of variations encountered in the read that supports this variation.
     */
    public int numVariationsInRead;
    /**
     * The insert size between the two strands of the read
     */
    public int insertSize;
    public Alignments.AlignmentEntry alignmentEntry;

    @Override
    public String toString() {
        final char strand = matchesForwardStrand ? '+' : '-';
        return matchesReference ? String.format("%c ref: %c s=%d", strand, from, readerIndex) :
                String.format("%c %c/%c q=%d s=%d", strand, from, to, qualityScore, readerIndex);
    }

    public boolean isInsertionOrDeletion() {
        return from == '-' || to == '-';
    }


}
