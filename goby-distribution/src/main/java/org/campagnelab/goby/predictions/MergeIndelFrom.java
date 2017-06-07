package org.campagnelab.goby.predictions;

import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import org.apache.commons.lang.StringUtils;
import org.campagnelab.goby.util.Variant;

import java.io.Serializable;
import java.util.Set;

/**
 * Represents genotypes at a given site. Using this class makes the from alleles consistent across all possible tos.
 * This is necessary when a single from must be generated across several possible genotypes at a site.
 * Example:
 * A -> T
 * A -> A
 * ATTTGC -> A-TTGC
 * A--TTTG -> ATTTTG
 * <p>
 * Step 1: get longest tail after indel: TTGC
 * Step 2: replace all tails with longest tail
 * <p>
 * ATTTGC TTTTGC
 * ATTTGC ATTTGC
 * ATTTGC A-TTGC
 * A--TTTGC ATTTTGC
 * <p>
 * Step 3: get max number of dashes in from: 2
 * Step 4:  append dashes to ins and del fields until all have maxFromDash
 * <p>
 * A--TTTGC T--TTTGC
 * A--TTTGC A--TTTGC
 * A--TTTGC A---TTGC
 * A--TTTGC ATTTTGC
 * <p>
 * Approach:
 * Split every indel into:
 * refBase, ins, del, tail
 * (one of ins, del will contain only dashes.)
 * Then process steps above
 */
public class MergeIndelFrom {


    Set<String> tos = new ObjectArraySet<>();
    String from;


    public Set<String> getTos() {
        return tos;
    }

    public String getFrom() {
        return from;
    }

    static public class SplitIndel {

        char baseFrom;
        char baseTo;
        String insFrom; //empty or contains only dashes
        String delFrom; //bases in ref genome that are deleted
        String insTo; //new bases introduced
        String delTo; //empty or contains only dashes
        String tail;
        //number of bases from genome represented by concatenating delFrom and tail.
        int lenDelFromAndTail = 0;
        //number of deleted bases
        int delLen = 0;
        //number of inserted bases
        int insLen = 0;


        /**
         * @param fromTo Does not support multiple indels at once, ie from:ATTTTGC -> A-TTT-C
         *               Probably does not support already padded indels (ie indels with dashes in both to and from fields.
         *               Assumes that from and to field are the same length
         *               Assumes that the the first base of both are the position before the first "-".
         */
        public SplitIndel(Variant.FromTo fromTo) {
            final String from = fromTo.getFrom();
            insFrom = "";
            delFrom = "";
            insTo = "";
            delTo = "";
            baseFrom = from.charAt(0);
            final String to = fromTo.getTo();
            baseTo = to.charAt(0);
            delLen = StringUtils.countMatches(to, "-");
            insLen = StringUtils.countMatches(from, "-");
            tail="";
            if (to.length()>1) {
                this.tail = to.substring(1);
            }
            if (delLen == 0 && insLen == 0) {
                //handle snp or ref
                return;
            }

            //general approach: from = base + insFrom + delFrom + tail, to = base + insTo + delTo + tail.
            //we will split, and later be able to padd all fields as needed and reconcat.
            if (from.length() > insLen + 1 && from.length() > 1) {
                insFrom = from.substring(1, insLen + 1);
            }
            if (to.length() > insLen + 1 && from.length() > 1) {
                insTo = to.substring(1, insLen + 1);
            }
            if (from.length() > 1 + insLen && from.length() > 1 + insLen + delLen) {
                delFrom = from.substring(1 + insLen, 1 + insLen + delLen);
            }
            if (to.length() > insLen + 1 && from.length() > insLen + delLen) {
                delTo = to.substring(1 + insLen, 1 + insLen + delLen);
            }
            if (to.length() > 1 + insLen + delLen) {
                tail = to.substring(1 + insLen + delLen, to.length());
            }

            lenDelFromAndTail = delFrom.length() + tail.length();
        }


        String getFrom() {
            return baseFrom + insFrom + delFrom + tail;
        }

        String getTo() {
            return baseTo + insTo + delTo + tail;
        }
    }

    /**
     * Contract assumptions:
     * Refs and snps are of the form from:a to:b.
     * Insertions are of the form from:a--defg to:abcdefg
     * Deletions are of the form from:abcdefg to: a--defg
     *
     * @param fromTos
     */

    public MergeIndelFrom(Set<Variant.FromTo> fromTos) {
        assert fromTos.size() > 0 : "a non-empty set is expected.";
        Set<SplitIndel> splits = new ObjectArraySet<>(fromTos.size());
        //split up each indel into component strings
        for (Variant.FromTo fromTo : fromTos) {

            splits.add(new SplitIndel(fromTo));
        }

        //find longest delFrom+tail and replace all genotypes' tails with the longest
        String longestDfAndTail = "";
        int maxLen = 0;
        for (SplitIndel split : splits) {
            if (split.lenDelFromAndTail > maxLen) {
                maxLen = split.lenDelFromAndTail;
                longestDfAndTail = split.delFrom + split.tail;
            }
        }
        for (SplitIndel split : splits) {
            if (split.lenDelFromAndTail < maxLen) {
                split.tail = longestDfAndTail.substring(split.delFrom.length());
            }
        }


        //get max dashes ins:from (aka length of ins:from)
        int maxDashLen = 0;
        for (SplitIndel split : splits) {
            maxDashLen = Math.max(maxDashLen, split.insLen);
        }

        String maxDashToAppend = new String(new char[maxDashLen]).replace("\0", "-");
        //append dashes to all inserts and dels so that "from" fields have same dash count
        for (SplitIndel split : splits) {
            int numDashToAdd = maxDashLen - split.insLen;
            split.insFrom = maxDashToAppend.substring(0, numDashToAdd) + split.insFrom;
            split.insTo = maxDashToAppend.substring(0, numDashToAdd) + split.insTo;
        }


        //populate fields for output from split indels
        for (SplitIndel split : splits) {
            tos.add(split.getTo());
            if (from == null) {
                from = split.getFrom();
            }
        }

    }
}
