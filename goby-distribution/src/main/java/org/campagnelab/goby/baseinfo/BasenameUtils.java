package org.campagnelab.goby.baseinfo;

import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.apache.commons.lang.StringUtils;

/**
 * Utils to manipulate basenames.
 *
 * @author manuele
 */
class BasenameUtils {

    /**
     * Return the basenames corresponding to the input filenames. Less basename than filenames
     * may be returned (if several filenames reduce to the same baseline after removing
     * the extension).
     *
     * @param filenames The names of the files to get the basnames for
     * @return An array of basenames
     */
    protected static String[] getBasenames(final String[] exts, final String... filenames) {
        final ObjectSet<String> result = new ObjectArraySet<String>();
        if (filenames != null) {
            for (final String filename : filenames) {
                result.add(getBasename(filename, exts));
            }
        }
        return result.toArray(new String[result.size()]);
    }

    /**
     * Return the basename corresponding to the input reads filename.  Note
     * that if the filename does have the extension known to be a compact read
     * the returned value is the original filename
     *
     * @param filename The name of the file to get the basename for
     * @return basename for the alignment file
     */
    protected static String getBasename(final String filename, String[] exts) {
        for (final String ext : exts) {
            if (StringUtils.endsWith(filename, ext)) {
                return StringUtils.removeEnd(filename, ext);
            }
        }

        // perhaps the input was a basename already.
        return filename;
    }
}
