# Cloud SQL Sync Setup

This guide explains how to configure direct SQL sync for the password manager.

## At a glance

| Database | Minimum version | Default port |
|---|---:|---:|
| MySQL | 5.7+ | 3306 |
| MariaDB | 10.3+ | 3306 |
| SQLite (local) | Android built-in | — |

## Before you start (MySQL / MariaDB)

### 1) Create database and sync user

```sql
CREATE DATABASE passwords_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER 'pw_sync'@'%' IDENTIFIED BY 'your_strong_password';

GRANT SELECT, INSERT, UPDATE, DELETE, CREATE ON passwords_db.* TO 'pw_sync'@'%';
FLUSH PRIVILEGES;
```

### 2) Allow remote access safely

> [!WARNING]
> Open port `3306` only for trusted IPs. Never expose it publicly in production.

`/etc/mysql/mysql.conf.d/mysqld.cnf`

```ini
bind-address = 0.0.0.0
```

```bash
sudo systemctl restart mysql
```

UFW example:

```bash
sudo ufw allow from <your-ip> to any port 3306
```

### 3) Enable TLS (recommended)

Set `useSSL=true` in `DbType.MYSQL.urlTemplate` inside `SqlSyncConfig.kt`.

## App configuration

1. Open **Settings → Cloud SQL Sync**.
2. Choose database type (**MySQL** or **SQLite**).
3. Fill all fields.
4. Click **Test connection**.
5. Click **Save configuration**.
6. Click **Sync now**.

| Field | Example | Meaning |
|---|---|---|
| Host / IP | `db.example.com` | Database host |
| Port | `3306` | Database port |
| Database name | `passwords_db` | Target schema |
| Database user | `pw_sync` | Dedicated DB user |
| Database password | `your_password` | Stored encrypted on device |

## Created table schema

```sql
CREATE TABLE IF NOT EXISTS passwords (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(255)  NOT NULL DEFAULT '',
    url        VARCHAR(500)  NOT NULL DEFAULT '',
    username   VARCHAR(255)  NOT NULL DEFAULT '',
    password   TEXT          NOT NULL,
    note       TEXT,
    synced_at  DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

## Sync behavior

- **Sync now**: uploads current local data to DB (full replace).
- **Sync from cloud**: downloads DB data to local encrypted storage.
- **Auto-Sync**: triggers push after local changes and periodic pull when enabled.

## SQLite mode

SQLite sync file is stored at:

`/data/data/org.css_apps_m3.password_manager/files/passwords_sync.db`

> [!CAUTION]
> Keep this file protected. If exported, treat it as sensitive data.

## Security notes

- Use a dedicated DB user, never `root`.
- Restrict firewall rules to known IPs.
- Prefer TLS for remote DB connections.
- App-side DB credentials are stored with `EncryptedSharedPreferences`.

## Troubleshooting

| Error | What to check |
|---|---|
| `Connection refused` | Port, firewall, `bind-address` |
| `Access denied for user` | Username, password, grants |
| `Unknown database` | Database name |
| `Communications link failure` | Host/IP reachability |
| `SSL connection error` | SSL URL parameters |
| `JDBC driver not found` | Reinstall app |
| `java.net.SocketTimeoutException` | Server route/latency, timeout values |
