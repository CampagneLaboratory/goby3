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

import java.io.IOException;

/**
 * Returns AlignmentReader instances.
 *
 * @author Fabien Campagne
 *         Date: Apr 9, 2011
 *         Time: 4:37:26 PM
 */
public class GobyAlignmentReaderFactory extends AlignmentReaderFactoryBase  implements AlignmentReaderFactory {
    public AlignmentReader createReader(String basename) throws IOException {
        return new AlignmentReaderImpl(basename);
    }

    @Override
    public AlignmentReader createReader(String basename, boolean upgrade) throws IOException {
        return new AlignmentReaderImpl(basename,upgrade);
    }

    public AlignmentReader[] createReaderArray(int numElements) throws IOException {
        return new AlignmentReaderImpl[numElements];
    }

    public AlignmentReader createReader(String basename, int startReferenceIndex,
                                        int startPosition, int endReferenceIndex, int endPosition) throws IOException {
        return new AlignmentReaderImpl(basename, startReferenceIndex, startPosition, endReferenceIndex, endPosition);
    }

    @Override
    public AlignmentReaderImpl createReader(String basename, long startOffset, long endOffset) throws IOException {
        return new AlignmentReaderImpl(startOffset, endOffset, basename);
    }

   }
