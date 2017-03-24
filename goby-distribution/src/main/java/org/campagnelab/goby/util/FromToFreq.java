package org.campagnelab.goby.util;

/**
 * Created by rct66 on 3/1/17.
 */
public class FromToFreq extends Variant.FromTo {

    private Float freq;

    public FromToFreq(String from, String to, float freq) {
        super(from, to);
        this.freq = freq;
    }

    public Float getFreq() {
        return freq;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (!FromToFreq.class.isAssignableFrom(obj.getClass())) {
            return false;
        }
        final Variant.FromTo other = (Variant.FromTo) obj;
        return from.equals(other.from) && to.equals(other.to);
    }

    @Override
    public int hashCode() {
        return from.hashCode()^to.hashCode();
    }

    @Override
    public String toString() {
        return "from:" + from + " to:" + to + " freq:" + freq;
    }

}
