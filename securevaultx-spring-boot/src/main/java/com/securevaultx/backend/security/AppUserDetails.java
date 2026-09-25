package com.securevaultx.backend.security;

import com.securevaultx.backend.entities.User;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * The authenticated principal kept in the HTTP session. Extends Spring's User so the password hash is erased
 * after authentication (CredentialsContainer). Carries the internal user id for ownership checks.
 */
public class AppUserDetails extends org.springframework.security.core.userdetails.User implements UserDetails {

    private static final long serialVersionUID = 1L;

    private final long id;

    public AppUserDetails(User user) {
        super(user.getEmail(), user.getPasswordHash(), AuthorityUtils.NO_AUTHORITIES);
        this.id = user.getId();
    }

    /** Owner id for every ownership check. Always taken from here, never from request data. */
    public long getId() {
        return id;
    }
}
