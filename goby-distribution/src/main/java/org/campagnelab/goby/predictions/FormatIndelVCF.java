package org.campagnelab.goby.predictions;

import it.unimi.dsi.fastutil.objects.*;
import org.campagnelab.dl.varanalysis.protobuf.BaseInformationRecords;
import org.campagnelab.goby.util.Variant;

import java.util.Collections;
import java.util.Set;
import java.util.SortedSet;

/**
 * Generate an indel compatible with VCF v4.1
 * Three steps:
 * 1. Set ref bases in to sequence set to the from sequence. This is because goby treats ref bases differently from VCF, doesn't extend them to match the indel's from sequence.
 * IE: from: TGG to: T,T-G  -> from: TGG to: TGG,T-G
 * 2. trim all alleles to index of last dash any allele,
 * IE: from: GTAC to: G--C,G-AC -> from: GTA to: G--,G-A
 * 3. delete dashes
 * IE: from: GTA to: G--,G-A -> from: GTA to: G,GA
 * Created by rct66 on 2/7/16.
 */
public class FormatIndelVCF {

    public String fromVCF;
    public Set<String> toVCF;

    public Object2ObjectMap<String, String> mapFrom = new Object2ObjectArrayMap<>();
    public Object2ObjectMap<String, String> mapTo = new Object2ObjectArrayMap<>();

    public FormatIndelVCF(String from, Set<String> to, char refBase) {

        //step 1. extend ref or snp to include remainder ref string
        for (String alt : to) {
            if (alt.length() == 1) {
                to.remove(alt);
                String refOrSnp = alt + (from.length() > 1 ? from.substring(1, from.length()) : "");
                to.add(refOrSnp);
                mapTo.put(alt, refOrSnp);
            }
        }
        //find newlen for step 2
        int newLen = 1;
        int maxLen = -1;
        for (String alt : to) {
            if (alt.length() > maxLen) {
                maxLen = alt.length();
            }
        }
        maxLen = Math.max(maxLen, from.length());
        for (int i = 0; i < maxLen; i++) {
            if (from.length() > i && from.charAt(i) == '-') {
                newLen = i + 1;
            }
            for (String alt : to) {
                if (alt.length() > i && alt.charAt(i) == '-') {
                    newLen = i + 1;
                }
            }
        }
        String postfix = newLen < from.length() ? from.substring(newLen, from.length()) : "";

        //apply step 2 and 3
        String newRef = trimPostfix(from, postfix);
        newRef = newRef.replace("-", "");
        fromVCF = newRef;
        mapFrom.put(from, newRef);
        toVCF = new ObjectArraySet<>();
        for (String alt : to) {
            String newAlt = trimPostfix(alt, postfix);
            newAlt = newAlt.replace("-", "");
            toVCF.add(newAlt);
            mapTo.put(alt, newAlt);
        }


    }

    private String trimPostfix(String from, String postfix) {
        if (from.endsWith(postfix)) {
            from = from.substring(0, from.length() - postfix.length());
        }
        return from;
    }


    public String mappedFrom(String value) {
        return mapFrom.get(value);
    }

    public String mappedTo(String value) {
        return mapTo.get(value);
    }

}
