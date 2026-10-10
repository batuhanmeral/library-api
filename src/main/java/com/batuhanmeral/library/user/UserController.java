package com.batuhanmeral.library.user;

import com.batuhanmeral.library.auth.AuthenticatedUser;
import com.batuhanmeral.library.common.web.PageResponse;
import com.batuhanmeral.library.common.web.SortableFields;
import com.batuhanmeral.library.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController {

    /** No "password": sorting by a column leaks its order, even when the column is not shown. */
    private static final SortableFields SORTABLE =
            SortableFields.of("id", "email", "fullName", "createdAt");

    private final UserService userService;

    /**
     * The caller's own profile. {@code @AuthenticationPrincipal} hands over the principal that
     * JwtAuthenticationFilter put into the SecurityContext; there is no id in the path to tamper
     * with.
     */
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
        return userService.findById(user.id());
    }

    /** Admins only (SecurityConfig). */
    @GetMapping
    public PageResponse<UserResponse> findAll(@PageableDefault(sort = "id") Pageable pageable) {
        return PageResponse.from(userService.findAll(SORTABLE.check(pageable)));
    }
}
