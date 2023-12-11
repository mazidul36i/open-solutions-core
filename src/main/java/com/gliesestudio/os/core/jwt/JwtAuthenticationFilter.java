package com.gliesestudio.os.core.jwt;

import com.gliesestudio.os.core.dto.auth.JwtTokenSubject;
import com.gliesestudio.os.core.service.user.UserRoleService;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Jwt token authentication filter sets authentication context for the user, and it intercepts on every API call
 *
 * @author MazidulIslam
 * @since 09-12-2023
 */
@Component
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    private JwtTokenHelper jwtTokenHelper;

    @Autowired
    private UserRoleService userRoleService;

    /**
     * The main method of authentication filter chain
     *
     * @param request     {@link HttpServletRequest}
     * @param response    {@link HttpServletResponse}
     * @param filterChain {@link FilterChain}
     * @throws ServletException thrown by internal spring security
     * @throws IOException      any custom or internal spring security exception
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain) throws ServletException, IOException {
        final String authHeader = request.getHeader("Authorization");
        final String token;

        if (authHeader == null || !authHeader.startsWith("Bearer")) {
            log.warn("Request without Bearer token: {}", request.getRequestURI());
            filterChain.doFilter(request, response);
            return;
        }

        token = authHeader.split("Bearer ")[1];
        try {
            JwtTokenSubject jwtSubject = jwtTokenHelper.getTokenSubject(token);
            String username = jwtSubject.getUsername();
            UUID userId = jwtSubject.getUserId();

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                if (jwtTokenHelper.isTokenValid(token, userDetails)) {
                    UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(userDetails, null, userRoleService.getAuthorities(userId));
                    authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authenticationToken);
                }
            }

            filterChain.doFilter(request, response);
            return;
        } catch (ExpiredJwtException e) {
            log.error("Token expired: {}", token);
        } catch (UsernameNotFoundException e) {
            log.warn("Username not found: {}", e.getMessage());
        } catch (Throwable t) {
            log.error("Token validation exception: {}", t.getMessage());
        }
        filterChain.doFilter(request, response);
    }

}