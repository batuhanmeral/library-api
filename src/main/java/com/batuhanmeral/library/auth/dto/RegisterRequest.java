package com.batuhanmeral.library.auth.dto;

import com.batuhanmeral.library.common.validation.MaxUtf8Bytes;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of POST /auth/register. There is no role field: registration always creates a member, and a
 * "role" sent anyway is ignored like any unknown field.
 *
 * @param phone optional, in international form (E.164: "+", country code, number), for the SMS
 *     reminders of overdue loans
 */
public record RegisterRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 8) @MaxUtf8Bytes(72) String password,
        @NotBlank @Size(max = 200) String fullName,
        @Pattern(regexp = "\\+[1-9][0-9]{6,14}", message = "must be like +905551234567")
                String phone) {

    /** The password stays out of logs and error messages that print the request. */
    @Override
    public String toString() {
        return "RegisterRequest[email=%s, fullName=%s, phone=%s]".formatted(email, fullName, phone);
    }
}
