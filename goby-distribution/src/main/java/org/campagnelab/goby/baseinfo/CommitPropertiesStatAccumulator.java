package org.campagnelab.goby.baseinfo;

import java.util.Properties;

/**
 * Merge commit properties. (Keep only the first since all are assumed to be the same).
 * *  Created by fac2003 on 3/25/17.
 */
public class CommitPropertiesStatAccumulator extends StatAccumulator {
    private final String prefix;

    public CommitPropertiesStatAccumulator(String prefix) {
        super(null, baseInformation -> {
            return 0f;
        });
        this.prefix = prefix;
    }

    String commit;
    String branch;
    String commitTime;
    String buildVersion;

    @Override
    void mergeWith(Properties properties) {

        commit = properties.getProperty(prefix + ".git.commit");
        branch = properties.getProperty(prefix + ".git.branch");
        commitTime = properties.getProperty(prefix + ".commit.time");
        buildVersion = properties.getProperty(prefix + ".git.build.version");

    }

    @Override
    void setProperties(Properties properties) {
        properties.setProperty(prefix + ".git.commit", commit);
        properties.setProperty(prefix + ".git.branch", branch);
        properties.setProperty(prefix + ".commit.time", commitTime);
        properties.setProperty(prefix + ".git.build.version", buildVersion);
    }
}
