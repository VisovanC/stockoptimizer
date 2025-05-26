package com.cv.stockoptimizer.controller;

import com.cv.stockoptimizer.model.dto.request.LoginRequest;
import com.cv.stockoptimizer.model.dto.request.SignupRequest;
import com.cv.stockoptimizer.model.dto.response.JwtResponse;
import com.cv.stockoptimizer.model.dto.response.MessageResponse;
import com.cv.stockoptimizer.model.entity.User;
import com.cv.stockoptimizer.repository.UserRepository;
import com.cv.stockoptimizer.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.HashSet;
import java.util.Set;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    @Autowired
    AuthenticationManager authenticationManager;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    JwtTokenProvider jwtTokenProvider;

    @PostMapping("/signin")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        System.out.println("=== LOGIN ATTEMPT ===");
        System.out.println("Username: " + loginRequest.getUsername());
        System.out.println("Password provided: " + (loginRequest.getPassword() != null ? "Yes" : "No"));
        System.out.println("Password length: " + (loginRequest.getPassword() != null ? loginRequest.getPassword().length() : 0));

        try {
            // Check if user exists first
            User user = userRepository.findByUsername(loginRequest.getUsername()).orElse(null);
            if (user == null) {
                System.out.println("✗ User not found: " + loginRequest.getUsername());
                return ResponseEntity.status(401).body(new MessageResponse("User not found"));
            }

            System.out.println("✓ User found: " + user.getUsername());
            System.out.println("✓ User enabled: " + user.isEnabled());
            System.out.println("✓ Stored password hash: " + user.getPassword().substring(0, Math.min(20, user.getPassword().length())) + "...");

            // Test password matching
            boolean passwordMatches = encoder.matches(loginRequest.getPassword(), user.getPassword());
            System.out.println("✓ Password matches: " + passwordMatches);

            if (!passwordMatches) {
                System.out.println("✗ Password does not match stored hash");
                return ResponseEntity.status(401).body(new MessageResponse("Invalid password"));
            }

            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getUsername(),
                            loginRequest.getPassword()));

            SecurityContextHolder.getContext().setAuthentication(authentication);
            String jwt = jwtTokenProvider.generateToken(authentication);

            System.out.println("✓ Authentication successful for: " + loginRequest.getUsername());
            System.out.println("✓ JWT token generated: " + jwt.substring(0, Math.min(30, jwt.length())) + "...");

            return ResponseEntity.ok(new JwtResponse(jwt));
        } catch (Exception e) {
            System.out.println("✗ Authentication failed: " + e.getClass().getSimpleName());
            System.out.println("✗ Error message: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(401).body(new MessageResponse("Authentication failed: " + e.getMessage()));
        }
    }

    @PostMapping("/signup")
    public ResponseEntity<?> registerUser(@Valid @RequestBody SignupRequest signupRequest) {
        System.out.println("=== SIGNUP ATTEMPT ===");
        System.out.println("Username: " + signupRequest.getUsername());
        System.out.println("Email: " + signupRequest.getEmail());
        System.out.println("Password provided: " + (signupRequest.getPassword() != null ? "Yes" : "No"));

        if (userRepository.existsByUsername(signupRequest.getUsername())) {
            System.out.println("✗ Username already exists: " + signupRequest.getUsername());
            return ResponseEntity
                    .badRequest()
                    .body(new MessageResponse("Error: Username is already taken!"));
        }

        if (userRepository.existsByEmail(signupRequest.getEmail())) {
            System.out.println("✗ Email already exists: " + signupRequest.getEmail());
            return ResponseEntity
                    .badRequest()
                    .body(new MessageResponse("Error: Email is already in use!"));
        }

        // Encode password
        String encodedPassword = encoder.encode(signupRequest.getPassword());
        System.out.println("✓ Password encoded: " + encodedPassword.substring(0, Math.min(20, encodedPassword.length())) + "...");

        User user = new User(
                signupRequest.getUsername(),
                signupRequest.getEmail(),
                encodedPassword);

        Set<String> roles = new HashSet<>();
        roles.add("ROLE_USER");
        user.setRoles(roles);

        try {
            User savedUser = userRepository.save(user);
            System.out.println("✓ User saved successfully: " + savedUser.getUsername());
            System.out.println("✓ User ID: " + savedUser.getId());
            return ResponseEntity.ok(new MessageResponse("User registered successfully!"));
        } catch (Exception e) {
            System.out.println("✗ Error saving user: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(new MessageResponse("Error saving user: " + e.getMessage()));
        }
    }

    // Debug endpoint - remove in production
    @GetMapping("/debug/users")
    public ResponseEntity<?> getAllUsers() {
        try {
            return ResponseEntity.ok(userRepository.findAll());
        } catch (Exception e) {
            return ResponseEntity.status(500).body(new MessageResponse("Error: " + e.getMessage()));
        }
    }
}