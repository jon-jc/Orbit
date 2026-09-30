package com.orbit.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import jakarta.servlet.DispatcherType;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    @Bean SecurityContextRepository securityContextRepository() { return new HttpSessionSecurityContextRepository(); }
    @Bean CsrfTokenRepository csrfTokenRepository() { return new HttpSessionCsrfTokenRepository(); }
    @Bean CookieSerializer cookieSerializer(@Value("${server.servlet.session.cookie.secure:false}") boolean secure) {
        var serializer=new DefaultCookieSerializer();
        serializer.setCookieName("ORBIT_SESSION"); serializer.setCookiePath("/");
        serializer.setUseHttpOnlyCookie(true); serializer.setUseSecureCookie(secure); serializer.setSameSite("Lax");
        return serializer;
    }
    @Bean UserDetailsService userDetailsService(JdbcTemplate jdbc) {
        return email -> jdbc.query("SELECT email,password_hash FROM app_user WHERE email=?", (rs,n) ->
                User.withUsername(rs.getString("email")).password(rs.getString("password_hash")).roles("USER").build(),email)
                .stream().findFirst().orElseThrow(() -> new UsernameNotFoundException("Invalid email or password."));
    }
    @Bean AuthenticationManager authenticationManager(UserDetailsService users, PasswordEncoder encoder) {
        var provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(List.of(provider));
    }
    @Bean @Order(1)
    SecurityFilterChain management(HttpSecurity http, @Value("${management.server.port:0}") int managementPort) throws Exception {
        return http.securityMatcher(request -> managementPort > 0 && request.getLocalPort() == managementPort)
                .authorizeHttpRequests(auth -> auth.requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/prometheus").permitAll().anyRequest().denyAll())
                .csrf(csrf -> csrf.disable()).build();
    }
    @Bean @Order(2)
    SecurityFilterChain application(HttpSecurity http, SecurityContextRepository contexts, CsrfTokenRepository csrf,
                                    AuthRateLimitFilter rateLimit) throws Exception {
        http.securityContext(context -> context.securityContextRepository(contexts).requireExplicitSave(true))
            .csrf(config -> config.csrfTokenRepository(csrf))
            .authorizeHttpRequests(auth -> auth
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers("/api/auth/csrf", "/api/auth/config", "/api/auth/register", "/api/auth/login").permitAll()
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/actuator/**").denyAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll())
            .requestCache(cache -> cache.disable())
            .httpBasic(basic -> basic.disable())
            .formLogin(form -> form.disable())
            .logout(logout -> logout.logoutUrl("/api/auth/logout").invalidateHttpSession(true).clearAuthentication(true)
                    .deleteCookies("ORBIT_SESSION").logoutSuccessHandler((request,response,authentication) -> response.setStatus(204)))
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request,response,exception) -> JsonErrors.write(response,401,"Unauthorized","Sign in to continue."))
                .accessDeniedHandler((request,response,exception) -> JsonErrors.write(response,403,"Forbidden","Your session or permission does not allow this action. Refresh and try again.")))
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'; object-src 'none'"))
                .frameOptions(frame -> frame.deny())
                .referrerPolicy(referrer -> referrer.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN)))
            .addFilterBefore(rateLimit,CsrfFilter.class);
        return http.build();
    }
}
