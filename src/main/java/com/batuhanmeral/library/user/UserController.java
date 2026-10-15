package com.batuhanmeral.library.user;

import com.batuhanmeral.library.auth.AuthenticatedUser;
import com.batuhanmeral.library.common.web.PageResponse;
import com.batuhanmeral.library.common.web.SortableFields;
import com.batuhanmeral.library.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Users")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController {

    /** "password" yok: bir sütuna göre sıralamak, sütun görünmese de sırasını sızdırır. */
    private static final SortableFields SORTABLE =
            SortableFields.of("id", "email", "fullName", "createdAt");

    private final UserService userService;

    /** Çağıranın kendi profili; yolda değiştirilebilecek id yok. */
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
        return userService.findById(user.id());
    }

    /** Sadece admin (SecurityConfig). */
    @GetMapping
    public PageResponse<UserResponse> findAll(
            @ParameterObject @PageableDefault(sort = "id") Pageable pageable) {
        return PageResponse.from(userService.findAll(SORTABLE.check(pageable)));
    }
}
