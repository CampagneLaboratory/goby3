package org.campagnelab.goby.parsers;

import it.unimi.dsi.io.FastBufferedReader;
import it.unimi.dsi.lang.MutableString;
import java.io.IOException;
import java.io.Reader;
/**
 * Imported from edu.cornell.med.icb.parsers (squil.jar)
 * Created by mas2182 on 9/28/16.
 */
public class FastaParser {
    private FastBufferedReader reader;
    private boolean hasNext;
    private static final MutableString VALID_PROTEIN_RESIDUES = (new MutableString("ACTGLVISDEFHKMNPQRWBY-XZ")).compact();
    private MutableString line;
    private MutableString previousDescriptionLine;

    public FastaParser(Reader fastaFileSource) throws IOException {
        this();
        this.setReader(fastaFileSource);
    }

    public FastaParser() {
        this.line = new MutableString();
        this.previousDescriptionLine = new MutableString();
    }

    public void setReader(Reader reader) throws IOException {
        if(reader instanceof FastBufferedReader) {
            this.reader = (FastBufferedReader)reader;
        } else {
            this.reader = new FastBufferedReader(reader);
        }

        this.hasNext = this.readNextDescriptionLine(this.reader);
    }

    public boolean hasNext() {
        return this.hasNext;
    }

    public boolean next(MutableString descriptionLine, MutableString residues) throws IOException {
        if(!this.hasNext) {
            return false;
        } else {
            descriptionLine.replace(this.previousDescriptionLine);
            return this.readResidues(residues);
        }
    }

    public static void guessAccessionCode(CharSequence descriptionLine, MutableString accessionCode) {
        accessionCode.setLength(0);
        byte startIndex;
        if(descriptionLine.length() > 3 && descriptionLine.charAt(0) == 80 && descriptionLine.charAt(1) == 49 && descriptionLine.charAt(2) == 59) {
            startIndex = 3;
        } else {
            startIndex = 0;
        }

        for(int i = startIndex; i < descriptionLine.length(); ++i) {
            char c = descriptionLine.charAt(i);
            if(c == 32 || c == 9 || c == 124) {
                break;
            }

            accessionCode.append(c);
        }

    }

    public static void filterProteinResidues(CharSequence rawResidues, MutableString filteredResidues) {
        filteredResidues.setLength(rawResidues.length());
        int destIndex = 0;

        for(int i = 0; i < rawResidues.length(); ++i) {
            char residueCode = rawResidues.charAt(i);
            if(residueCode == 46) {
                residueCode = 45;
            } else {
                residueCode = Character.toUpperCase(residueCode);
            }

            if(VALID_PROTEIN_RESIDUES.indexOf(residueCode) != -1) {
                filteredResidues.setCharAt(destIndex, residueCode);
                ++destIndex;
            }
        }

        filteredResidues.setLength(destIndex);
    }

    private boolean readNextDescriptionLine(FastBufferedReader fastBufferedReader) throws IOException {
        do {
            this.previousDescriptionLine = fastBufferedReader.readLine(this.previousDescriptionLine);
            if(this.previousDescriptionLine == null) {
                return false;
            }
        } while(!this.previousDescriptionLine.startsWith(">"));

        this.previousDescriptionLine = this.removeBracket(this.previousDescriptionLine);
        return true;
    }

    private MutableString removeBracket(MutableString descriptionLine) {
        return descriptionLine.substring(1, descriptionLine.length());
    }

    private boolean readResidues(MutableString residues) throws IOException {
        residues.setLength(0);
        this.line.setLength(0);

        while(true) {
            this.line = this.reader.readLine(this.line);
            if(this.line == null) {
                this.hasNext = false;
                return this.hasNext;
            }

            if(this.line.startsWith(">")) {
                this.previousDescriptionLine.replace(this.line);
                this.previousDescriptionLine = this.removeBracket(this.previousDescriptionLine);
                return this.hasNext;
            }

            residues.append(this.line);
        }
    }
}
