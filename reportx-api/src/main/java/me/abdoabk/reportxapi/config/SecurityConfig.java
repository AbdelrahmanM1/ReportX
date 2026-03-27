package me.abdoabk.reportxapi.config;

import me.abdoabk.reportxapi.security.JwtAuthFliter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
public class SecurityConfig {

    @Value("${reportx.cors.allowed-origins}")
    private String allowedOrigins;

    @Value("${reportx.plugin.secret:CHANGE_THIS_PLUGIN_SECRET}")
    private String pluginSecret;

    private final JwtAuthFliter  jwtAuthFliter;
    private final RateLimitFilter rateLimitFilter;

    public SecurityConfig(JwtAuthFliter jwtAuthFliter, RateLimitFilter rateLimitFilter) {
        this.jwtAuthFliter   = jwtAuthFliter;
        this.rateLimitFilter = rateLimitFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(c -> c.configurationSource(corsSource()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/exchange").permitAll()
                        .requestMatchers("/api/internal/poll-notifications").permitAll()
                        .requestMatchers("/api/auth/me").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/reports/stats").hasAnyRole("SENIOR_STAFF", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/audit-log").hasAnyRole("SENIOR_STAFF", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/**").hasAnyRole("STAFF", "SENIOR_STAFF", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/reports/*/notes").hasAnyRole("STAFF", "SENIOR_STAFF", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/**").hasAnyRole("SENIOR_STAFF", "ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/**").hasAnyRole("SENIOR_STAFF", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFliter, UsernamePasswordAuthenticationFilter.class)
                // Validate the plugin secret header for the internal endpoint
                .addFilterBefore(new PluginSecretFilter(pluginSecret), JwtAuthFliter.class)
                // Rate limiting: applied first before any auth processing
                .addFilterBefore(rateLimitFilter, PluginSecretFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsSource() {
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(origins);
        cfg.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cfg);
        return source;
    }
}
