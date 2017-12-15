package org.campagnelab.goby.predictions;

import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.*;

/**
 * Created by rct66 on 2/7/17.
 */
public class FormatIndelVCFTest {


    //IE: from: GTAC to: G--C/G-AC -> from: GTA to: G/GA
    @Test
    public void formatIndel_Insertion_Deletion() throws Exception {

        // G----GTGTGTGTGTGTGTGTGTGTGTGTGCGTTG/GGTGTGTGTGTGTGTGTGTGTGTGTGTGTGTGCGT
        // G----GTGTGTGTGTGTGTGTGTGTGTGTGCGTTG/G--GTGTGTGTGTGTGTGTGTGTGTGTGTGCGT
        String from = "GGTGTGTGTGTGTGTGTGTGTGTGTGTGTGCGTTG";
        Set<String> to = new ObjectArraySet<>();
        to.add("G----GTGTGTGTGTGTGTGTGTGTGTGTGCGTTG");
        to.add("GGTGTGTGTGTGTGTGTGTGTGTGTGTGTGTGCGT");
        to.add("G----GTGTGTGTGTGTGTGTGTGTGTGTGCGTTG");
        to.add("G--GTGTGTGTGTGTGTGTGTGTGTGTGTGCGTTG");
        Set<String> allOriginalAlleles=new ObjectArraySet<>();
        allOriginalAlleles.add(from);
        allOriginalAlleles.addAll(to);
        FormatIndelVCF format = new FormatIndelVCF(from,to,'G');
      //  assertEquals("",format.fromVCF);
     //   assertEquals(2,format.toVCF.size());
     //   assertTrue(format.toVCF.contains(""));
     //   assertTrue(format.toVCF.contains(""));
        for (String original: allOriginalAlleles) {
            System.out.printf("original %s mapped: %s %n",original, format.mapped(original));
           assertNotNull( format.mapped(original));
        }
    }

    //IE: from: GTAC to: G--C/G-AC -> from: GTA to: G/GA
    @Test
    public void formatIndelVCF() throws Exception {


        String from = "GTAC";
        Set<String> to = new ObjectArraySet<>();
        to.add("G--C");
        to.add("G-AC");
        FormatIndelVCF3 format = new FormatIndelVCF3(from,to,'G');
        assertEquals("GTA",format.fromVCF);
        assertEquals(2,format.toVCF.size());
        assertTrue(format.toVCF.contains("G"));
        assertTrue(format.toVCF.contains("GA"));
    }

    //TGG to: T/T-G  -> from: TG to: TG/T
    @Test
    public void formatIndelVCF2() throws Exception {
        String from = "TGG";
        Set<String> to = new ObjectArraySet<>();
        to.add("T");
        to.add("T-G");
        FormatIndelVCF3 format = new FormatIndelVCF3(from,to, 'T');
        assertEquals("TG",format.fromVCF);
        assertEquals(2,format.toVCF.size());
        assertTrue(format.toVCF.contains("TG"));
        assertTrue(format.toVCF.contains("T"));
    }

    //A to: A/T  -> from: A to: A/T
    @Test
    public void formatIndelVCF3() throws Exception {
        String from = "A";
        Set<String> to = new ObjectArraySet<>();
        to.add("A");
        to.add("T");
        FormatIndelVCF3 format = new FormatIndelVCF3(from,to, 'A');
        assertEquals("A",format.fromVCF);
        assertEquals(2,format.toVCF.size());
        assertTrue(format.toVCF.contains("A"));
        assertTrue(format.toVCF.contains("T"));
    }

    //A to: A/A  -> from: A to: A/A
    @Test
    public void formatIndelVCF4() throws Exception {
        String from = "A";
        Set<String> to = new ObjectArraySet<>();
        to.add("A");
        FormatIndelVCF3 format = new FormatIndelVCF3(from,to, 'A');
        assertEquals("A",format.fromVCF);
        assertEquals(1,format.toVCF.size());
        assertTrue(format.toVCF.contains("A"));
    }

    //handle snp and del at same pos
    //from: G-C to: T/GTC  -> from: G to: T/GT
    @Test
    public void formatIndelVCF5() throws Exception {
        String from = "G-C";
        Set<String> to = new ObjectArraySet<>();
        to.add("T");
        to.add("GTC");
        FormatIndelVCF3 format = new FormatIndelVCF3(from,to, 'G');
        assertEquals("G",format.fromVCF);
        assertEquals(2,format.toVCF.size());
        assertTrue(format.toVCF.contains("T"));
        assertTrue(format.toVCF.contains("GT"));

    }


    //handle snp and insertion at same pos
    //from: GAC to: T/G-C  -> from: GA to: TA/G
    @Test
    public void formatIndelVCF6() throws Exception {
        String from = "GAC";
        Set<String> to = new ObjectArraySet<>();
        to.add("T");
        to.add("G-C");
        FormatIndelVCF3 format = new FormatIndelVCF3(from,to, 'G');
        assertEquals("GA",format.fromVCF);
        assertEquals(2,format.toVCF.size());
        assertTrue(format.toVCF.contains("TA"));
        assertTrue(format.toVCF.contains("G"));

    }



    //handle simple snp! don't want to accidentally change something that doesn't need fixing
    //from: A to: C/A  -> from: A  to: C/A
    @Test
    public void formatIndelVCF7() throws Exception {
        String from = "A";
        Set<String> to = new ObjectArraySet<>();
        to.add("A");
        to.add("C");
        FormatIndelVCF3 format = new FormatIndelVCF3(from,to, 'A');
        assertEquals("A",format.fromVCF);
        assertEquals(2,format.toVCF.size());
        assertTrue(format.toVCF.contains("A"));
        assertTrue(format.toVCF.contains("C"));

    }

    // Real data test case where current approach is failing:
    @Test
    public void formatIndelVCF8() throws Exception {
        String from = "C----CATCC";
        Set<String> to = new ObjectArraySet<>();
        to.add("CCCATCATCC");
        to.add("CCCATCCATCATCCATCC");
        FormatIndelVCF format = new FormatIndelVCF(from,to, 'C');
        assertEquals("C",format.fromVCF);
        assertEquals(2,format.toVCF.size());
        assertTrue(format.toVCF.contains("CCCAT"));
        assertTrue(format.toVCF.contains("CCCATCCATCATC"));

    }

    @Test
    public void formatIndelVCF9() throws Exception {


        String from = "A-TTA";
        Set<String> to = new ObjectArraySet<>();
        to.add("ATTTT");
        to.add("A-TTA");
        FormatIndelVCF3 format = new FormatIndelVCF3(from,to,'G');
        assertEquals("ATTA",format.fromVCF);
        assertEquals(2,format.toVCF.size());
        assertTrue(format.toVCF.contains("ATTTT"));
        assertTrue(format.toVCF.contains("ATTA"));
    }
    @Test
    public void formatIndelVCF10() throws Exception {

        String from = "A-T";
        Set<String> to = new ObjectArraySet<>();
        to.add("ATTTT");
        to.add("A-TTA");
        FormatIndelVCF3 format = new FormatIndelVCF3(from,to,'G');
        assertEquals("AT",format.fromVCF);
        assertEquals(2,format.toVCF.size());
        assertTrue(format.toVCF.contains("ATTTT"));
        assertTrue(format.toVCF.contains("ATTA"));
    }
}