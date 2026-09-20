package br.fiap.daiji.controller;

import br.fiap.daiji.dto.LoginRequest;
import br.fiap.daiji.dto.LoginResponse;
import br.fiap.daiji.service.AuthService;
import java.sql.SQLException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request) throws SQLException {
        return authService.login(request);
    }
}
