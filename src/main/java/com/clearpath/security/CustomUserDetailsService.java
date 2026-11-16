package com.clearpath.security;

import com.clearpath.model.Civilian;
import com.clearpath.model.Officer;
import com.clearpath.repository.CivilianRepository;
import com.clearpath.repository.OfficerRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * Loads user details for both officers (by officerId) and civilians (by phone number).
 * The JWT filter resolves role from the token and prefixes the subject with "CIV:" for civilians.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    public static final String CIVILIAN_PREFIX = "CIV:";

    private final OfficerRepository officerRepository;
    private final CivilianRepository civilianRepository;

    public CustomUserDetailsService(OfficerRepository officerRepository, CivilianRepository civilianRepository) {
        this.officerRepository = officerRepository;
        this.civilianRepository = civilianRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        if (username.startsWith(CIVILIAN_PREFIX)) {
            // Civilian — username is "CIV:<phoneNumber>"
            String phone = username.substring(CIVILIAN_PREFIX.length());
            Civilian civilian = civilianRepository.findByPhoneNumber(phone)
                    .orElseThrow(() -> new UsernameNotFoundException("Civilian not found: " + phone));
            // Civilians have no password in the system; we pass a placeholder (no password auth flow for them)
            return new User(
                    username,
                    "{noop}OTP_AUTHENTICATED",
                    Collections.singletonList(new SimpleGrantedAuthority(civilian.getRole()))
            );
        } else {
            // Officer
            Officer officer = officerRepository.findByOfficerId(username)
                    .orElseThrow(() -> new UsernameNotFoundException("Officer not found: " + username));
            return new User(
                    officer.getOfficerId(),
                    officer.getPasswordHash(),
                    Collections.singletonList(new SimpleGrantedAuthority(officer.getRole()))
            );
        }
    }
}
