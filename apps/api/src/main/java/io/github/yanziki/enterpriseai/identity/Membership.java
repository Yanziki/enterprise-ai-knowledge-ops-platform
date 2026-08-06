package io.github.yanziki.enterpriseai.identity;

import io.github.yanziki.enterpriseai.tenant.Organization;
import io.github.yanziki.enterpriseai.tenant.Workspace;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "memberships")
public class Membership {

    @Id private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_profile_id", nullable = false)
    private UserProfile userProfile;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id")
    private Workspace workspace;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MembershipRole role;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Membership() {}

    public UUID getId() {
        return id;
    }

    public UserProfile getUserProfile() {
        return userProfile;
    }

    public Organization getOrganization() {
        return organization;
    }

    public Workspace getWorkspace() {
        return workspace;
    }

    public MembershipRole getRole() {
        return role;
    }
}
