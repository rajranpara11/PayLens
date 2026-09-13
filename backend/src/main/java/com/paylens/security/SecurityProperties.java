package com.paylens.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "paylens.security")
public class SecurityProperties {

    /**
     * HR Manager username. From {@code PAYLENS_HR_USERNAME}.
     */
    private String hrUsername = "";

    /**
     * BCrypt hash of the HR password. From {@code PAYLENS_HR_PASSWORD_HASH}.
     * Preferred over {@link #hrPassword}.
     */
    private String hrPasswordHash = "";

    /**
     * Raw HR password from env for local/demo bootstrap only ({@code PAYLENS_HR_PASSWORD}).
     * Encoded at startup and never persisted by the app.
     */
    private String hrPassword = "";

    public String getHrUsername() {
        return hrUsername;
    }

    public void setHrUsername(String hrUsername) {
        this.hrUsername = hrUsername;
    }

    public String getHrPasswordHash() {
        return hrPasswordHash;
    }

    public void setHrPasswordHash(String hrPasswordHash) {
        this.hrPasswordHash = hrPasswordHash;
    }

    public String getHrPassword() {
        return hrPassword;
    }

    public void setHrPassword(String hrPassword) {
        this.hrPassword = hrPassword;
    }

    public boolean hasPasswordHash() {
        if (!StringUtils.hasText(hrPasswordHash)) {
            return false;
        }
        // Reject accidental plaintext (e.g. defaulting hash to "admin") — must look like BCrypt.
        return hrPasswordHash.startsWith("$2a$")
                || hrPasswordHash.startsWith("$2b$")
                || hrPasswordHash.startsWith("$2y$");
    }

    public boolean hasPassword() {
        return StringUtils.hasText(hrPassword);
    }
}
