package com.smartmobility.auth.config;

import com.smartmobility.auth.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .csrf(csrf -> csrf.disable())


                .sessionManagement(session -> session
                        .sessionCreationPolicy(
                                org.springframework.security.config.http.SessionCreationPolicy.STATELESS
                        )
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/auth/**").permitAll()

                    .requestMatchers("/admin/**").hasRole("ADMIN")
                    .requestMatchers("/driver/**").hasRole("DRIVER")

                    .anyRequest().authenticated()
        );



        return http.build();
    }
}