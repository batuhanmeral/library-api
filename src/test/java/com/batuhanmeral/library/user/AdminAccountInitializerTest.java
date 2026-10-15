package com.batuhanmeral.library.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class AdminAccountInitializerTest {

    /** Strength 4, en düşüğü: hash gerçek ama 100 ms yerine milisaniyeler sürer. */
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);

    private final UserRepository users = mock(UserRepository.class);

    @Test
    void createsTheAdministratorWhenNoAccountHasTheAddress() throws Exception {
        when(users.findByEmail("admin@example.com")).thenReturn(Optional.empty());
        when(users.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        run(new AdminAccountProperties(" Admin@Example.com ", "a long password", "Librarian"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("admin@example.com");
        assertThat(saved.getValue().getRole()).isEqualTo(Role.ADMIN);
        assertThat(saved.getValue().getFullName()).isEqualTo("Librarian");
        assertThat(encoder.matches("a long password", saved.getValue().getPassword())).isTrue();
    }

    @Test
    void leavesAnExistingAdministratorAsItIs() throws Exception {
        // Yeniden başlatma, admin'in sonradan değiştirdiği şifreyi sıfırlamamalı.
        when(users.findByEmail("admin@example.com"))
                .thenReturn(
                        Optional.of(new User("admin@example.com", "{noop}x", "Ada", Role.ADMIN)));

        run(new AdminAccountProperties("admin@example.com", "a long password", null));

        verify(users, never()).save(any());
    }

    @Test
    void refusesToStartWhenTheAddressBelongsToAMember() {
        // Adresi önce başkası kaydetti; yine de başlamak kütüphaneyi admin'siz bırakırdı.
        when(users.findByEmail("admin@example.com"))
                .thenReturn(
                        Optional.of(new User("admin@example.com", "{noop}x", "Eve", Role.MEMBER)));

        assertThatThrownBy(() ->
                        run(
                                new AdminAccountProperties(
                                        "admin@example.com", "a long password", null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("library.security.admin.email");
        verify(users, never()).save(any());
    }

    @Test
    void doesNothingWhenNoAddressIsConfigured() throws Exception {
        run(new AdminAccountProperties("", "", null));
        run(new AdminAccountProperties(null, null, null));

        verify(users, never()).findByEmail(any());
        verify(users, never()).save(any());
    }

    @Test
    void refusesToStartWithAWeakOrMissingPassword() {
        for (String password : new String[] {null, "", "short"}) {
            assertThatThrownBy(() ->
                            run(new AdminAccountProperties("admin@example.com", password, null)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("library.security.admin.password");
        }
        verify(users, never()).save(any());
    }

    private void run(AdminAccountProperties properties) throws Exception {
        new AdminAccountInitializer(properties, users, encoder)
                .run(new DefaultApplicationArguments());
    }
}
