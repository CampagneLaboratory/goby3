package org.campagnelab.goby.predictions;

import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import org.campagnelab.goby.util.Variant;
import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.testng.Assert.*;

/**
 * Created by rct66 on 2/21/17.
 */

public class MergeIndelFromTest {

/*
Example 1: longest tail in deletion
from	to
A	T
A	A
ATCTTG 	A----C
A-TTTG 	ATTTTG

populate split indels
base:from	base:to	ins:from	ins:to	del:to	del:from	tail
A	A
A	T
A	A			----	TTTG	C
A	A	-	T			TTTG


find longest del:from + tail	TTTGC
replace del:from + tails
A	A					TTTGC
A	T					TTTGC
A	A			----	TTTG	C
A	A	-	T			TTTGC



get  max len of ins:from	1
add - to all ins:from and ins:to so all ins:from have max
A	A	-	-			TTTGC
A	T	-	-			TTTGC
A	A	-	-	----	TTTG	C
A	A	-	T			TTTGC




concat: from = base + ins:from + del:from + tail	concat: to = base + ins:to + del:to + tail
A-TTTGC	A-TTTGC
T-TTTGC	T-TTTGC
A-TTTGC	A-----C
A-TTTGC	ATTTTGC

*/


    @Test
    public void longestTailInDeletion() {
        final Set<Variant.FromTo> fromTos = new ObjectArraySet<>(4);
        fromTos.add(new Variant.FromTo("A","T"));
        fromTos.add(new Variant.FromTo("A","A"));
        fromTos.add(new Variant.FromTo("ATTTGC","A----C"));
        fromTos.add(new Variant.FromTo("A-TTTG","ATTTTG"));

        MergeIndelFrom merge = new MergeIndelFrom(fromTos);
        assertEquals("A-TTTGC",merge.from);
        assertEquals(4,merge.tos.size());
        assertTrue(merge.tos.contains("A-TTTGC"));
        assertTrue(merge.tos.contains("T-TTTGC"));
        assertTrue(merge.tos.contains("A-----C"));
        assertTrue(merge.tos.contains("ATTTTGC"));

    }






/*

Example 2: longest tail in insertion
from	to
A	A
A	T
ATT	A-T
A----TTT	ATTTTTTT
A-TT	ATTT

populate split indels
base 	base:to	ins:from	ins:to	del:to	del:from	tail
A	A
A	T
A	A			-	T	T
A	A	----	TTTT			TTT
A	A	-	T			TT

find longest del:from + tail	TTT
replace del:from + tails
A	A					TTT
A	T					TTT
A	A			-	T	TT
A	A	----	TTTT			TTT
A	A	-	T			TTT


get  max len of ins:from	4
add - to all ins:from and ins:to so all ins:from have max
A	A	----	----			TTT
A	T	----	----			TTT
A	A	----	----	-	T	TT
A	A	----	TTTT			TTT
A	A	----	---T			TTT



concat: from = base + ins:from + del:from + tail	concat: to = base + ins:to + del:to + tail
A----TTT	A----TTT
A----TTT	T----TTT
A----TTT	A-----TT
A----TTT	ATTTTTTT
A----TTT	A---TTTT
 */


    @Test
    public void longestTailInInsertion() {
        final Set<Variant.FromTo> fromTos = new ObjectArraySet<>(4);
        fromTos.add(new Variant.FromTo("A","T"));
        fromTos.add(new Variant.FromTo("A","A"));
        fromTos.add(new Variant.FromTo("ATT","A-T"));
        fromTos.add(new Variant.FromTo("A----TTT","ATTTTTTT"));
        fromTos.add(new Variant.FromTo("A-TT","ATTT"));

        MergeIndelFrom merge = new MergeIndelFrom(fromTos);
        assertEquals("A----TTT",merge.from);
        assertEquals(5,merge.tos.size());
        assertTrue(merge.tos.contains("A----TTT"));
        assertTrue(merge.tos.contains("T----TTT"));
        assertTrue(merge.tos.contains("A-----TT"));
        assertTrue(merge.tos.contains("ATTTTTTT"));
        assertTrue(merge.tos.contains("A---TTTT"));

    }


    @Test
    public void longerRefNoIndels() {
        final Set<Variant.FromTo> fromTos = new ObjectArraySet<>(4);
        fromTos.add(new Variant.FromTo("GTTTTTTTTTTTTTTAAA","GTTTTTTTTTTTTTTTAA"));
        fromTos.add(new Variant.FromTo("GTTTTTTTTTTTTTTAA","TTTTTTTTTTTTTTTAA"));

        MergeIndelFrom merge = new MergeIndelFrom(fromTos);
        assertEquals("GTTTTTTTTTTTTTTAAA",merge.from);
        assertEquals(2,merge.tos.size());
        assertTrue(merge.tos.contains("GTTTTTTTTTTTTTTTAA"));
        assertTrue(merge.tos.contains("TTTTTTTTTTTTTTTAAA"));

    }
}