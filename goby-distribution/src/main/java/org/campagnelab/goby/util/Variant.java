package org.campagnelab.goby.util;

import java.io.Serializable;


/**
 * This class stores the information from a VCF pertinent to one variant.
 * Will be stored in a map for use by AddTrueGenotypes.
 */
public class Variant implements Serializable {
    public final String reference;
    public final String trueAllele1;
    public final String trueAllele2;
    public final boolean isIndel;

    public Variant(String reference, String trueAllele1, String trueAllele2) {
        this.reference = reference;
        this.trueAllele1 = trueAllele1;
        this.trueAllele2 = trueAllele2;
        this.isIndel = (reference.length() > 1 || trueAllele1.length() > 1 || trueAllele2.length() > 1);
    }
}
