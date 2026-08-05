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
    workspace_id UUID NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    role VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT memberships_role_allowed CHECK (role IN ('TENANT_ADMIN', 'MEMBER', 'AUDITOR'))
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

INSERT INTO organizations (id, slug, display_name)
VALUES
    ('10000000-0000-0000-0000-000000000001', 'acme', 'Acme Corporation'),
    ('10000000-0000-0000-0000-000000000002', 'globex', 'Globex Corporation');

INSERT INTO workspaces (id, organization_id, slug, display_name)
VALUES
    (
        '20000000-0000-0000-0000-000000000001',
        '10000000-0000-0000-0000-000000000001',
        'operations',
        'Acme Operations'
    ),
    (
        '20000000-0000-0000-0000-000000000002',
        '10000000-0000-0000-0000-000000000002',
        'research',
        'Globex Research'
    );

INSERT INTO user_profiles (id, identity_subject, email, display_name)
VALUES
    (
        '30000000-0000-0000-0000-000000000001',
        '00000000-0000-0000-0000-000000000001',
        'admin@example.com',
        'Platform Admin'
    ),
    (
        '30000000-0000-0000-0000-000000000002',
        '00000000-0000-0000-0000-000000000002',
        'member@example.com',
        'Acme Member'
    ),
    (
        '30000000-0000-0000-0000-000000000003',
        '00000000-0000-0000-0000-000000000003',
        'other@example.com',
        'Globex Member'
    );

INSERT INTO memberships (id, user_profile_id, organization_id, workspace_id, role)
VALUES
    (
        '40000000-0000-0000-0000-000000000001',
        '30000000-0000-0000-0000-000000000001',
        '10000000-0000-0000-0000-000000000001',
        NULL,
        'TENANT_ADMIN'
    ),
    (
        '40000000-0000-0000-0000-000000000002',
        '30000000-0000-0000-0000-000000000002',
        '10000000-0000-0000-0000-000000000001',
        NULL,
        'MEMBER'
    ),
    (
        '40000000-0000-0000-0000-000000000003',
        '30000000-0000-0000-0000-000000000003',
        '10000000-0000-0000-0000-000000000002',
        NULL,
        'MEMBER'
    );
