package org.campagnelab.goby.util;

import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.campagnelab.goby.algorithmic.algorithm.EquivalentIndelRegionCalculator;
import org.campagnelab.goby.reads.RandomAccessSequenceCache;
import org.campagnelab.goby.reads.RandomAccessSequenceInterface;
import org.campagnelab.goby.reads.RandomAccessSequenceTestSupport;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.List;

/**
 * Created by rct66 on 1/17/17.
 * helper class allows storing and looking up variants to/from a file. Intended for use by VCFToGenotypeMapMode and
 * AddTrueGenotypesMode.
 */
public class VariantMapHelper {




    private Object2ObjectOpenHashMap<String, Int2ObjectMap<Variant>> chMap;
    private RandomAccessSequenceInterface genome;
    private EquivalentIndelRegionCalculator equivalentIndelRegionCalculator;






    /**
     * Load map from path
     * @param pathToMap
     * @throws IOException
     * @throws ClassNotFoundException
     */
    public VariantMapHelper(String pathToMap, String genomePath) throws IOException, ClassNotFoundException {
        chMap = (Object2ObjectOpenHashMap<String, Int2ObjectMap<Variant>>) BinIO.loadObject(pathToMap);
    }

    /**
     * Generate a new empty variant map
     */
    public VariantMapHelper(RandomAccessSequenceInterface genome){
        chMap = new Object2ObjectOpenHashMap<String, Int2ObjectMap<Variant>>(40);
        this.genome = genome;
        this.equivalentIndelRegionCalculator = new EquivalentIndelRegionCalculator(genome);

    }

    /**
     * add variant to map with reference and true genotype strings.
     * @param chrom
     * @param pos
     * @param reference
     * @param trueAlleles
     */
    public void addVariant(int pos, String chrom, String reference, List<String> trueAlleles){
        //zero-based positions
        int gobyPos = pos-1;
        Variant var = new Variant(reference,trueAlleles,gobyPos,genome.getReferenceIndex(chrom));
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
//        chMap.
//        chMap.get(chrom).put(pos, var);
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
        if (chMap.containsKey(chrom)) {
            return chMap.get(chrom).get(pos);
        }
        return null;
    }


}

