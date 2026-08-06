INSERT INTO organizations (id, slug, display_name)
VALUES
    ('10000000-0000-0000-0000-000000000001', 'acme', 'Acme Corporation'),
    ('10000000-0000-0000-0000-000000000002', 'globex', 'Globex Corporation')
ON CONFLICT (id) DO NOTHING;

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
    )
ON CONFLICT (id) DO NOTHING;

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
    )
ON CONFLICT (id) DO NOTHING;

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
    )
ON CONFLICT (id) DO NOTHING;
