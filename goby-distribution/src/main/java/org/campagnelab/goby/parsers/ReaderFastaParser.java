package org.campagnelab.goby.parsers;

import it.unimi.dsi.io.FastBufferedReader;
import it.unimi.dsi.lang.MutableString;
import java.io.IOException;
import java.io.Reader;

/**
 * Imported from edu.cornell.med.icb.parsers (squil.jar)
 * Created by mas2182 on 9/28/16.
 */
public class ReaderFastaParser {
    private FastBufferedReader reader;
    private boolean hasNext;
    private final MutableString line;
    private MutableString previousDescriptionLine;

    public ReaderFastaParser(Reader fastaFileSource) throws IOException {
        this();
        this.setReader(fastaFileSource);
    }

    public ReaderFastaParser() {
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

    public boolean hasNextSequence() {
        return this.hasNext;
    }

    public boolean nextSequence(MutableString descriptionLine) {
        if(!this.hasNext) {
            return false;
        } else {
            descriptionLine.replace(this.previousDescriptionLine);
            return true;
        }
    }

    public Reader getBaseReader() {
        return new ReaderFastaParser.OneBaseAtATimeReader(this.reader);
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

    private class OneBaseAtATimeReader extends Reader {
        private final FastBufferedReader sequenceReader;

        OneBaseAtATimeReader(FastBufferedReader reader) {
            this.sequenceReader = reader;
        }

        public int read() throws IOException {
            int c = this.sequenceReader.read();
            if(c == -1) {
                ReaderFastaParser.this.hasNext = false;
                return -1;
            } else {
                char character = (char)c;
                if(c == 62) {
                    ReaderFastaParser.this.line.setLength(0);
                    this.sequenceReader.readLine(ReaderFastaParser.this.line);
                    ReaderFastaParser.this.previousDescriptionLine.replace(ReaderFastaParser.this.line);
                    ReaderFastaParser.this.hasNext = true;
                    return -1;
                } else {
                    return (c < 48 || c > 54) && (c < 65 || c > 90) && (c < 97 || c > 122)?this.read():c;
                }
            }
        }

        public int read(char[] chars, int i, int i1) throws IOException {
            throw new UnsupportedOperationException();
        }

        public void close() throws IOException {
        }
    }
}
