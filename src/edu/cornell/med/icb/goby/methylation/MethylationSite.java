/*
 * Copyright (C) 2009-2010 Institute for Computational Biomedicine,
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

package edu.cornell.med.icb.goby.methylation;

import java.io.Serializable;

/**
 * @author Fabien Campagne
 *         Date: Oct 24, 2010
 *         Time: 11:39:49 AM
 */
public class MethylationSite implements Serializable {
    public int chromosome;
    public char strand;
    public int position;
    public int methylatedReadCount;
    public int totalCount;


    public float getMethylationRate() {
        return ((float)methylatedReadCount/ (float)totalCount);
    }
   // public static final long serialVersionUID = 5664745795898488209L;

}
