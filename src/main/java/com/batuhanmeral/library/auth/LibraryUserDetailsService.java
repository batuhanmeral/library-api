package com.batuhanmeral.library.auth;

import com.batuhanmeral.library.user.User;
import com.batuhanmeral.library.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Giriş için hesabı bulur. Bilinmeyen e-posta da yanlış şifre gibi BadCredentialsException olur ve
 * şifre yine hash'lenir; ne cevap ne süre hesabın varlığını ele verir.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LibraryUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) {
        User user =
                userRepository
                        .findByEmail(User.normalizeEmail(email))
                        .orElseThrow(() -> new UsernameNotFoundException("No such user"));
        return new UserAccount(user.getId(), user.getEmail(), user.getPassword(), user.getRole());
    }
}
