package com.studyplanner.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Email không được để trống.")
        @Email(message = "Email không đúng định dạng.")
        @Size(max = 254, message = "Email tối đa 254 ký tự.")
        String email,

        @NotBlank(message = "Mật khẩu không được để trống.")
        @Size(min = 6, max = 100, message = "Mật khẩu phải có từ 6 đến 100 ký tự.")
        String password
) {}
