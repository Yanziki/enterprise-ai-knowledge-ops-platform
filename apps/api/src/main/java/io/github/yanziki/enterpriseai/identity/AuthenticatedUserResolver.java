package io.github.yanziki.enterpriseai.identity;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class AuthenticatedUserResolver {

    private final UserProfileRepository userProfileRepository;

    public AuthenticatedUserResolver(UserProfileRepository userProfileRepository) {
        this.userProfileRepository = userProfileRepository;
    }

    public AuthenticatedUser resolve(JwtAuthenticationToken authentication) {
        String subject = authentication.getToken().getSubject();
        if (subject == null
                || subject.isBlank()
                || userProfileRepository.findByIdentitySubject(subject).isEmpty()) {
            throw new AccessDeniedException("Authenticated identity is not provisioned");
        }

        Set<String> roles =
                authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .filter(authority -> authority.startsWith("ROLE_"))
                        .map(authority -> authority.substring("ROLE_".length()))
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        return new AuthenticatedUser(subject, Set.copyOf(roles));
    }
}
