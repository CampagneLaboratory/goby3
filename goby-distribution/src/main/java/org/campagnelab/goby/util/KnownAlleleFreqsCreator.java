//package org.campagnelab.goby.util;
//
//import it.unimi.dsi.fastutil.io.BinIO;
//import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
//import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
//import org.campagnelab.goby.alignments.processors.ObservedIndel;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.io.File;
//import java.io.IOException;
//import java.util.Set;
//
///**
// * Created by rct66 on 2/17/17.
// * helper class allows storing of known indels to use by the realigner.
// */
//public class KnownAlleleFreqsCreator {
//
//    protected Object2ObjectOpenHashMap<String, Set<FromToFreq>> indelSet;
//    protected static final Logger LOG = LoggerFactory.getLogger(KnownAlleleFreqsCreator.class);
//
//    /**
//     * Load map from path
//     *
//     * @param pathToIndelSet
//     * @throws IOException
//     * @throws ClassNotFoundException
//     */
//    public KnownAlleleFreqsCreator(String pathToIndelSet) throws IOException, ClassNotFoundException {
//        indelSet = (Object2ObjectOpenHashMap<String, Set<ObservedIndel>>) BinIO.loadObject(pathToIndelSet);
//    }
//
//
//    /**
//     * Generate a new empty variant map
//     */
//    public KnownAlleleFreqsCreator() {
//        indelSet = new Object2ObjectOpenHashMap<>(40);
//
//    }
//
//
//    /**
//     * add variant to map with reference and true genotype strings.
//     *
//     * @param chrom
//     * @param knownIndel
//     */
//    public void addIndel(String chrom, ObservedIndel knownIndel){
//        //make sure there is a map for this chromosome
//        if (!indelSet.containsKey(chrom)) {
//            indelSet.put(chrom, new ObjectOpenHashSet<ObservedIndel>(50000));
//        }
//        indelSet.get(chrom).add(knownIndel);
//
//    }
//
//
//    public Set<ObservedIndel> getAllIndelsInChrom(String chrom) {
//        return indelSet.get(chrom);
//    }
//
//
//    /**
//     * Save current map to specified file.
//     *
//     * @param pathToSet
//     * @throws IOException
//     */
//    public void saveSet(String pathToSet) throws IOException {
//        BinIO.storeObject(indelSet, new File(pathToSet));
//    }
//
//}
//
