package com.trailsutra.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean UserDetailsService userDetailsService(PasswordEncoder encoder,
            @Value("${trailsutra.users.bank-analyst-password}") String bank,
            @Value("${trailsutra.users.officer-password}") String officer,
            @Value("${trailsutra.users.reviewer-password}") String reviewer,
            @Value("${trailsutra.users.auditor-password}") String auditor) {
        return new InMemoryUserDetailsManager(
            User.withUsername("bank-analyst").password(encoder.encode(bank)).roles("BANK_ANALYST").build(),
            User.withUsername("officer").password(encoder.encode(officer)).roles("INVESTIGATING_OFFICER").build(),
            User.withUsername("reviewer").password(encoder.encode(reviewer)).roles("REVIEWER").build(),
            User.withUsername("auditor").password(encoder.encode(auditor)).roles("AUDITOR").build());
    }
    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(a -> a.requestMatchers("/css/**", "/js/**", "/login", "/403").permitAll()
                .requestMatchers("/overview", "/settings").hasAnyRole("INVESTIGATING_OFFICER", "REVIEWER", "AUDITOR", "BANK_ANALYST")
                .requestMatchers("/intake").hasRole("INVESTIGATING_OFFICER")
                .requestMatchers("/trail").hasAnyRole("INVESTIGATING_OFFICER", "REVIEWER")
                .requestMatchers("/triage").hasAnyRole("INVESTIGATING_OFFICER", "REVIEWER", "BANK_ANALYST")
                .requestMatchers("/review").hasRole("REVIEWER")
                .requestMatchers("/reports").hasAnyRole("INVESTIGATING_OFFICER", "AUDITOR")
                .requestMatchers("/api/cases/*/match", "/api/cases/*/confirm", "/api/detokenise")
                    .hasRole("INVESTIGATING_OFFICER")
                .requestMatchers("/api/cases/*/transactions").hasAnyRole("INVESTIGATING_OFFICER", "REVIEWER")
                .requestMatchers("/api/reports/**").hasAnyRole("INVESTIGATING_OFFICER", "AUDITOR")
                .requestMatchers("/api/reviews/**").hasRole("REVIEWER")
                .requestMatchers("/api/audit/**").hasAnyRole("AUDITOR", "INVESTIGATING_OFFICER")
                .anyRequest().authenticated())
            .formLogin(f -> f.loginPage("/login").permitAll()).logout(l -> l.logoutSuccessUrl("/login?logout").permitAll())
            .exceptionHandling(e -> e.accessDeniedPage("/403"))
            .csrf(Customizer.withDefaults()).headers(h -> h.frameOptions(f -> f.sameOrigin()).contentTypeOptions(Customizer.withDefaults()).httpStrictTransportSecurity(Customizer.withDefaults())).build();
    }
}
