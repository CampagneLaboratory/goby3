package org.campagnelab.goby.predictions;

import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;

import java.util.Set;

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
public class FormatIndelVCF2 {

    public String fromVCF;
    public Set<String> toVCF;

    public Object2ObjectMap<String, String> map = new Object2ObjectArrayMap<>();

    public FormatIndelVCF2(String from, Set<String> to, char refBase) {

        map.put(from, from.replaceAll("-", ""));
        fromVCF = map.get(from);

        toVCF = new ObjectArraySet<>();
        //simply remove the gaps:
        for (String alt : to) {

            map.put(alt, alt.replaceAll("-", ""));
            toVCF.add(map.get(alt));
        }


    }


    public String mapped(String value) {
        return map.get(value);
    }
}
