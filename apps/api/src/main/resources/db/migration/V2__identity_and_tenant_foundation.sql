CREATE TABLE organizations (
    id UUID PRIMARY KEY,
    slug VARCHAR(63) NOT NULL UNIQUE,
    display_name VARCHAR(160) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT organizations_slug_format CHECK (slug ~ '^[a-z0-9]+(?:-[a-z0-9]+)*$'),
    CONSTRAINT organizations_display_name_not_blank CHECK (btrim(display_name) <> '')
);

CREATE TABLE workspaces (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    slug VARCHAR(63) NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT workspaces_organization_slug_unique UNIQUE (organization_id, slug),
    CONSTRAINT workspaces_id_organization_unique UNIQUE (id, organization_id),
    CONSTRAINT workspaces_slug_format CHECK (slug ~ '^[a-z0-9]+(?:-[a-z0-9]+)*$'),
    CONSTRAINT workspaces_display_name_not_blank CHECK (btrim(display_name) <> '')
);

CREATE INDEX workspaces_organization_id_idx ON workspaces (organization_id);

CREATE TABLE user_profiles (
    id UUID PRIMARY KEY,
    identity_subject VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(320) NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT user_profiles_identity_subject_not_blank CHECK (btrim(identity_subject) <> ''),
    CONSTRAINT user_profiles_email_not_blank CHECK (btrim(email) <> ''),
    CONSTRAINT user_profiles_display_name_not_blank CHECK (btrim(display_name) <> '')
);

CREATE UNIQUE INDEX user_profiles_email_lower_unique_idx ON user_profiles (lower(email));

CREATE TABLE memberships (
    id UUID PRIMARY KEY,
    user_profile_id UUID NOT NULL REFERENCES user_profiles(id) ON DELETE CASCADE,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    workspace_id UUID NULL,
    role VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT memberships_role_allowed CHECK (role IN ('TENANT_ADMIN', 'MEMBER', 'AUDITOR')),
    CONSTRAINT memberships_workspace_organization_fk
        FOREIGN KEY (workspace_id, organization_id)
        REFERENCES workspaces (id, organization_id)
        ON DELETE CASCADE
);

CREATE INDEX memberships_user_profile_id_idx ON memberships (user_profile_id);
CREATE INDEX memberships_organization_id_idx ON memberships (organization_id);
CREATE INDEX memberships_workspace_id_idx ON memberships (workspace_id) WHERE workspace_id IS NOT NULL;
CREATE UNIQUE INDEX memberships_organization_level_unique_idx
    ON memberships (user_profile_id, organization_id)
    WHERE workspace_id IS NULL;
CREATE UNIQUE INDEX memberships_workspace_level_unique_idx
    ON memberships (user_profile_id, organization_id, workspace_id)
    WHERE workspace_id IS NOT NULL;
