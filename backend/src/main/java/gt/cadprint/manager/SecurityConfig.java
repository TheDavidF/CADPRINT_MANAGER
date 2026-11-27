package gt.cadprint.manager;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService users(
            PasswordEncoder encoder,
            @Value("${CADPRINT_ADMIN_PASSWORD}") String adminPassword,
            @Value("${CADPRINT_OPERATOR_PASSWORD}") String operatorPassword) {
        if (adminPassword.isBlank() || operatorPassword.isBlank()) {
            throw new IllegalArgumentException("Configura las dos contraseñas de CADPRINT");
        }
        return new InMemoryUserDetailsManager(
                User.withUsername("admin").password(encoder.encode(adminPassword)).roles("ADMIN").build(),
                User.withUsername("operador").password(encoder.encode(operatorPassword)).roles("OPERADOR").build());
    }

    @Bean
    SecurityFilterChain filter(HttpSecurity http, UrlBasedCorsConfigurationSource corsSource) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(rules -> rules
                        .requestMatchers(HttpMethod.DELETE, "/api/products/*/image").hasRole("ADMIN")
                        .requestMatchers("/api/products/*/image").hasAnyRole("ADMIN", "OPERADOR")
                        .requestMatchers(HttpMethod.DELETE, "/api/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST,
                                "/api/purchases/*/cancel", "/api/purchases/*/return-to-draft").hasRole("ADMIN")
                        .requestMatchers("/api/purchases", "/api/purchases/**").hasAnyRole("ADMIN", "OPERADOR")
                        .requestMatchers(HttpMethod.POST, "/api/product-categories").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/product-categories/*", "/api/product-categories/*/status",
                                "/api/products/*/status", "/api/products/*/variants/*/status").hasRole("ADMIN")
                        .requestMatchers("/api/products", "/api/products/**",
                                "/api/product-categories", "/api/product-categories/**").hasAnyRole("ADMIN", "OPERADOR")
                        .requestMatchers(HttpMethod.POST, "/api/production-costs/profiles",
                                "/api/production-costs/machines").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/production-costs/profiles/*",
                                "/api/production-costs/machines/*").hasRole("ADMIN")
                        .requestMatchers("/api/production-costs/**").hasAnyRole("ADMIN", "OPERADOR")
                        .requestMatchers("/api/cash/daily").hasAnyRole("ADMIN", "OPERADOR")
                        .requestMatchers("/api/customers", "/api/customers/**").hasAnyRole("ADMIN", "OPERADOR")
                        .requestMatchers(HttpMethod.POST, "/api/quotations/*/revisions/*/cancel").hasRole("ADMIN")
                        .requestMatchers("/api/quotations/*/revisions/*/order/profitability").hasRole("ADMIN")
                        .requestMatchers("/api/quotations", "/api/quotations/**").hasAnyRole("ADMIN", "OPERADOR")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .httpBasic(Customizer.withDefaults())
                .build();
    }

    @Bean
    UrlBasedCorsConfigurationSource corsConfigurationSource(
            @Value("${cadprint.cors.allowed-origins}") List<String> allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}