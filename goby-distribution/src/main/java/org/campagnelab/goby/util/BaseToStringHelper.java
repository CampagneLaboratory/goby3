package org.campagnelab.goby.util;

import it.unimi.dsi.fastutil.chars.Char2ObjectArrayMap;

/**
 * A helper class to speed up the conversion from base character to string. Character.valueOf is surprisingly slow when
 * used million of times.
 * Created by fac2003 on 12/14/16.
 */
public class BaseToStringHelper {
    private Char2ObjectArrayMap<String> bases = new Char2ObjectArrayMap<>();

    public String convert(char base) {
        switch (base) {
            case 'A':
                return "A";

            case 'C':
                return "C";
            case 'T':
                return "T";

            case 'G':
                return "G";
            case 'N':
                return "N";
            default:

                String value = bases.get(base);
                if (value == null) {
                    value = Character.toString(base);
                    bases.put(base, value);
                }
                return value;
        }
    }
}
