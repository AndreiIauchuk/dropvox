CREATE SCHEMA IF NOT EXISTS dropvox;

CREATE TABLE IF NOT EXISTS dropvox.files (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    size BIGINT NOT NULL,
    content_type VARCHAR(255),
    owner_id UUID NOT NULL,
    bucket VARCHAR(63) NOT NULL,
    s3_key VARCHAR(512) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE OR REPLACE FUNCTION dropvox.update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_update_updated_at ON dropvox.files;
CREATE TRIGGER trigger_update_updated_at
    BEFORE UPDATE ON dropvox.files
    FOR EACH ROW
    EXECUTE FUNCTION dropvox.update_updated_at_column();