package com.clearpath.security;

import com.clearpath.model.Officer;
import com.clearpath.repository.OfficerRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final OfficerRepository officerRepository;

    public CustomUserDetailsService(OfficerRepository officerRepository) {
        this.officerRepository = officerRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String officerId) throws UsernameNotFoundException {
        Officer officer = officerRepository.findByOfficerId(officerId)
                .orElseThrow(() -> new UsernameNotFoundException("Officer not found: " + officerId));

        return new User(
                officer.getOfficerId(),
                officer.getPasswordHash(),
                Collections.singletonList(new SimpleGrantedAuthority(officer.getRole()))
        );
    }
}
