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
        commitTime = properties.getProperty(prefix + ".git.commit.time");
        buildVersion = properties.getProperty(prefix + ".git.build.version");

    }

    @Override
    void setProperties(Properties properties) {
        if (commit != null) {
            properties.setProperty(prefix + ".git.commit", commit);
        }
        if (branch != null) {
            properties.setProperty(prefix + ".git.branch", branch);
        }
        if (commitTime != null) {
            properties.setProperty(prefix + ".commit.time", commitTime);
        }
        if (buildVersion != null) {
            properties.setProperty(prefix + ".git.build.version", buildVersion);
        }
    }

}
