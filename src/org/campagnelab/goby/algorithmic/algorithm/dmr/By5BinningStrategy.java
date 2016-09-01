/*
 * Copyright (C) 2009-2012 Institute for Computational Biomedicine,
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

package org.campagnelab.goby.algorithmic.algorithm.dmr;

/**
 * @author Fabien Campagne
 *         Date: 5/4/12
 *         Time: 10:54 AM
 */
public class By5BinningStrategy implements BinningStrategy {
    @Override
    public int getBinIndex(double covariate) {
        return (int) Math.round(covariate/5);
    }

    @Override
    public int getLowerBound(int binIndex) {
        return ((binIndex-1)*5)+1;
    }

    @Override
    public int getUpperBound(int binIndex) {
     return ((binIndex)*5);
    }

    @Override
    public int getMidpoint(int binIndex) {
        return (getUpperBound(binIndex)-getLowerBound(binIndex))/2;
    }

    @Override
    public String getName() {
        return  "by-5";
    }
}