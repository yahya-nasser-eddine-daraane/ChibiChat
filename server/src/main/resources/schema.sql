-- ============================================================
--  LAN Messenger — Updated Schema (username-based)
--  Run this to drop and recreate tables cleanly.
-- ============================================================
IF DB_ID('LanMessenger') IS NULL
CREATE DATABASE LanMessenger;
GO

IF OBJECT_ID('Sessions',  'U') IS NOT NULL DROP TABLE Sessions;
IF OBJECT_ID('Contacts',  'U') IS NOT NULL DROP TABLE Contacts;
IF OBJECT_ID('OtpCodes',  'U') IS NOT NULL DROP TABLE OtpCodes;
IF OBJECT_ID('Users',     'U') IS NOT NULL DROP TABLE Users;
GO

IF OBJECT_ID('Users', 'U') IS NULL
CREATE TABLE Users (
    user_id        UNIQUEIDENTIFIER  DEFAULT NEWID() PRIMARY KEY,
    username       VARCHAR(32)       NOT NULL UNIQUE,
    display_name   NVARCHAR(100)     NOT NULL,
    password_hash  VARCHAR(256)      NOT NULL,
    password_salt  VARCHAR(64)       NOT NULL,
    public_key     VARCHAR(512)      NULL,

    -- Added for peer messaging
    lan_ip         VARCHAR(45)       NULL,
    tcp_port       INT               NULL,

    created_at     DATETIME2         DEFAULT GETUTCDATE(),
    last_seen      DATETIME2         NULL
);
GO

IF OBJECT_ID('Contacts', 'U') IS NULL
CREATE TABLE Contacts (
    contact_id      INT               IDENTITY(1,1) PRIMARY KEY,
    owner_id        UNIQUEIDENTIFIER  NOT NULL REFERENCES Users(user_id),
    contact_user_id UNIQUEIDENTIFIER  NOT NULL REFERENCES Users(user_id),
    nickname        NVARCHAR(100)     NULL,
    added_at        DATETIME2         DEFAULT GETUTCDATE(),
    CONSTRAINT UQ_Contact UNIQUE (owner_id, contact_user_id),
    CONSTRAINT CHK_NoSelf CHECK  (owner_id <> contact_user_id)
);
GO

IF OBJECT_ID('Sessions', 'U') IS NULL
CREATE TABLE Sessions (
    session_id  UNIQUEIDENTIFIER  DEFAULT NEWID() PRIMARY KEY,
    user_id     UNIQUEIDENTIFIER  NOT NULL REFERENCES Users(user_id),
    token_hash  VARCHAR(256)      NOT NULL UNIQUE,
    created_at  DATETIME2         DEFAULT GETUTCDATE(),
    expires_at  DATETIME2         NOT NULL,
    last_used   DATETIME2         DEFAULT GETUTCDATE()
);
GO

CREATE INDEX IF NOT EXISTS IX_Users_Username ON Users(username);
CREATE INDEX IF NOT EXISTS IX_Sessions_Token ON Sessions(token_hash);
CREATE INDEX IF NOT EXISTS IX_Contacts_Owner ON Contacts(owner_id);
GO

CREATE OR ALTER PROCEDURE CleanupExpired AS
BEGIN
    DELETE FROM Sessions WHERE expires_at < GETUTCDATE();
END
GO



PRINT 'Schema updated successfully.';
GO
