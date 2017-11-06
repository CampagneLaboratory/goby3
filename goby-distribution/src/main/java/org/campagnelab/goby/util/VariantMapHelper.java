package org.campagnelab.goby.util;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Created by rct66 on 1/17/17.
 * helper class allows storing and looking up variants to/from a file. Intended for use by VCFToGenotypeMapMode and
 * AddTrueGenotypesMode.
 */
public class VariantMapHelper {

    public int numOverlaps = 0;
    protected Object2ObjectOpenHashMap<String, Int2ObjectMap<Variant>> chMap;
    protected static final Logger LOG = LoggerFactory.getLogger(VariantMapHelper.class);

    /**
     * Load map from path
     *
     * @param pathToMap
     * @throws IOException
     * @throws ClassNotFoundException
     */
    public VariantMapHelper(String pathToMap) throws IOException, ClassNotFoundException {
        chMap = (Object2ObjectOpenHashMap<String, Int2ObjectMap<Variant>>) BinIO.loadObject(pathToMap);
    }

    /**
     * Generate a new empty variant map, should only be used by subclass VariantMapCreator
     */
    protected VariantMapHelper() {}


    /**
     * Get variant returns a variant located at the specified chrom and position. Returns null if there is no variant.
     *
     * @param chrom
     * @param pos
     * @return
     */
    public Variant getVariant(String chrom, int pos) {
        if (chMap.containsKey(chrom)) {
            return chMap.get(chrom).get(pos);
        }
        return null;
    }

    /**
     * Get all the variants for the chromosome .
     *
     * @param chrom
     * @return
     */
    public ObjectIterator<Variant> getAllVariants(String chrom) {
        return chMap.get(chrom).values().iterator();
    }

    /**
     * Get all the chromosomes in the map.
     *
     * @return
     */
    public ObjectIterator<String> getAllChromosomes() {
        return chMap.keySet().iterator();
    }

}

