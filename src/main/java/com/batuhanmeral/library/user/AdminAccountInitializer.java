package com.batuhanmeral.library.user;

import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Uygulama başladıktan sonra, o e-postada hesap yoksa admin hesabını oluşturur. Var olan hesaba
 * dokunmaz; e-posta bir üyeye aitse uygulama başlamaz.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminAccountInitializer implements ApplicationRunner {

    private static final String DEFAULT_NAME = "Administrator";

    private final AdminAccountProperties properties;

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(properties.email())) {
            log.debug("No administrator account configured (library.security.admin.email)");
            return;
        }
        checkPassword(properties.password());
        String email = User.normalizeEmail(properties.email());
        userRepository
                .findByEmail(email)
                .ifPresentOrElse(
                        existing -> {
                            if (existing.getRole() != Role.ADMIN) {
                                // Adresi önce başkası kaydetti; devam etmek kütüphaneyi admin'siz
                                // bırakırdı.
                                throw new IllegalStateException(
                                        "library.security.admin.email belongs to an account that"
                                                + " is not an administrator; choose another"
                                                + " address");
                            }
                        },
                        () -> {
                            User admin =
                                    userRepository.save(
                                            new User(
                                                    email,
                                                    passwordEncoder.encode(properties.password()),
                                                    StringUtils.hasText(properties.fullName())
                                                            ? properties.fullName()
                                                            : DEFAULT_NAME,
                                                    Role.ADMIN));
                            // Adres değil id: log'a kişisel veri gerekmez.
                            log.info(
                                    "Created the administrator account (user id {})",
                                    admin.getId());
                        });
    }

    /** Kayıttaki kurallar; zayıf şifre uygulamayı başlatmaz. */
    private static void checkPassword(String password) {
        if (password == null
                || password.length() < 8
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException(
                    "library.security.admin.password must be 8 characters to 72 bytes long");
        }
    }
}
