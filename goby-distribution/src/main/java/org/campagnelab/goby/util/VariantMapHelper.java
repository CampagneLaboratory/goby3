package org.campagnelab.goby.util;

import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;

/**
 * Created by rct66 on 1/17/17.
 * helper class allows storing and looking up variants to/from a file. Intended for use by VCFToGenotypeMapMode and
 * AddTrueGenotypesMode.
 */
public class VariantMapHelper {




    private Object2ObjectOpenHashMap<String, Int2ObjectMap<Variant>> chMap;


    /**
     * Load map from path
     * @param pathToMap
     * @throws IOException
     * @throws ClassNotFoundException
     */
    public VariantMapHelper(String pathToMap) throws IOException, ClassNotFoundException {
        chMap = (Object2ObjectOpenHashMap<String, Int2ObjectMap<Variant>>) BinIO.loadObject(pathToMap);
    }

    /**
     * Generate a new empty variant map
     */
    public VariantMapHelper(){
        chMap = new Object2ObjectOpenHashMap<String, Int2ObjectMap<Variant>>(40);
    }

    /**
     * add variant to map with reference and true genotype strings.
     * @param chrom
     * @param pos
     * @param reference
     * @param trueAllele1
     * @param trueAllele2
     */
    public void addVariant(String chrom, int pos, String reference, String trueAllele1, String trueAllele2){
        Variant var = new Variant(reference,trueAllele1,trueAllele2);
        addVariant(chrom,pos,var);
    }


    /**
     * Add variant to map with var object
     * @param chrom
     * @param pos
     * @param var
     */
    public void addVariant(String chrom, int pos, Variant var){
        if (!chMap.containsKey(chrom)) {
            chMap.put(chrom, new Int2ObjectArrayMap<Variant>(50000));
        }
        chMap.get(chrom).put(pos, var);
    }


    /**
     * Save current map to specified file.
     * @param pathToMap
     * @throws IOException
     */
    public void saveMap(String pathToMap) throws IOException {
        BinIO.storeObject(chMap, new File(pathToMap));
    }


        /**
         * Get variant returns a variant located at the specified chrom and position. Returns null if there is no variant.
         * @param chrom
         * @param pos
         * @return
         */
    public Variant getVariant(String chrom, int pos){
        if (!chMap.containsKey(chrom)) {
            return chMap.get(chrom).get(pos);
        }
        return null;
    }


}

