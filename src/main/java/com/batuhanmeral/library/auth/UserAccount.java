package com.batuhanmeral.library.auth;

import com.batuhanmeral.library.user.Role;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * A user as Spring Security's login check needs it: the address, the password hash and the
 * authorities. Only used while checking a login; requests after that carry an AuthenticatedUser
 * read from the token. The User entity itself does not implement UserDetails, so the entity does
 * not depend on Spring Security.
 */
record UserAccount(Long id, String email, String passwordHash, Role role) implements UserDetails {

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    /** "ROLE_" is the prefix hasRole("ADMIN") looks for. */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String toString() {
        return "UserAccount[id=%d, role=%s]".formatted(id, role);
    }
}
