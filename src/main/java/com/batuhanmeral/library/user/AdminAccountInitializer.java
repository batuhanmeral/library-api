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
 * Creates the administrator of AdminAccountProperties once the application has started, if no
 * account has that address yet. An ApplicationRunner runs after the context is ready (Flyway has
 * migrated, the beans exist) and before the application reports itself started.
 *
 * <p>An existing account is never changed: restarting must not reset a password the administrator
 * has changed since, nor make a member an administrator because the address matches. If the address
 * belongs to a member, the application does not start.
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
                                // Someone registered the address first. Going on would leave the
                                // library without the administrator it was configured with.
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
                            // The id, not the address: the log needs no personal data.
                            log.info(
                                    "Created the administrator account (user id {})",
                                    admin.getId());
                        });
    }

    /** The same rules as registration; a weak password stops the application from starting. */
    private static void checkPassword(String password) {
        if (password == null
                || password.length() < 8
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException(
                    "library.security.admin.password must be 8 characters to 72 bytes long");
        }
    }
}
