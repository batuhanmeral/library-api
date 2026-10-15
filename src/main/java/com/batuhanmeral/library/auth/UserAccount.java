package com.batuhanmeral.library.auth;

import com.batuhanmeral.library.user.Role;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Spring Security'nin giriş kontrolü için kullanıcı. Sadece girişte kullanılır; User entity'si
 * UserDetails'e bağımlı olmasın diye ayrı.
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

    /** hasRole("ADMIN") "ROLE_" önekini arar. */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String toString() {
        return "UserAccount[id=%d, role=%s]".formatted(id, role);
    }
}
