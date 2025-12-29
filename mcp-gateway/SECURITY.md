# Security Documentation

This document describes the threat model, security defaults, and production deployment guidance for the Enterprise MCP Gateway.

## Threat Model

### Assets Protected

1. **File System Resources**: Local files and directories
2. **Network Resources**: External HTTP endpoints
3. **Database Resources**: PostgreSQL database contents
4. **Audit Integrity**: Audit log completeness and accuracy

### Threat Actors

1. **Malicious AI Prompts**: Attempts to bypass security through crafted tool invocations
2. **Prompt Injection**: Indirect attacks through manipulated data sources
3. **Privilege Escalation**: Attempts to access resources beyond allowed scope
4. **Data Exfiltration**: Attempts to extract sensitive data through allowed channels

### Attack Vectors

| Vector | Mitigation |
|--------|------------|
| Path Traversal | Strict path allowlist, normalization, `..` detection |
| SQL Injection | SELECT-only queries, dangerous keyword blocking |
| Command Injection | No shell execution, no dynamic class loading |
| SSRF | Domain allowlist, scheme validation (HTTP/HTTPS only) |
| DoS via Large Results | Result size limits, timeout enforcement |
| Policy Bypass | Default deny, explicit allowlists |

## Security Defaults

### Default Deny Policy

The gateway operates on a **default deny** principle:

```yaml
mcp:
  policy:
    default-deny: true
```

All tools must be explicitly added to `allow-tools` or have a per-tool policy with `allow: true`.

### No Dynamic Execution

- No `Runtime.exec()` or `ProcessBuilder`
- No reflection-based method invocation
- No dynamic class loading
- No script evaluation (JavaScript, Groovy, etc.)

### Strict Allowlists

Every connector enforces allowlists:

- **FileSystem**: Only paths explicitly in `allowed-paths` are accessible
- **HTTP**: Only domains in `allowed-domains` can be fetched
- **PostgreSQL**: Only SELECT queries; dangerous keywords are blocked

### Read-Only Database Access

The PostgreSQL connector:

- Establishes connections with `setReadOnly(true)`
- Blocks SQL keywords: INSERT, UPDATE, DELETE, DROP, ALTER, CREATE, TRUNCATE, GRANT, REVOKE, EXECUTE, CALL, COPY, LOAD, IMPORT, EXPORT
- Requires queries to start with SELECT or WITH (for CTEs)
- Blocks multiple statements (semicolon detection)

### Resource Limits

All tool executions have enforced limits:

| Limit | Default | Purpose |
|-------|---------|---------|
| Timeout | 30 seconds | Prevent DoS via slow operations |
| Max Result Size | 1 MB | Prevent memory exhaustion |
| Max File Size | 10 MB | Limit file read operations |
| Max HTTP Response | 5 MB | Limit network data ingestion |
| Max Query Rows | 1000 | Limit database result sets |

### Audit Logging

Every invocation is logged with:

- Timestamp
- Tool name
- Actor identifier
- SHA-256 hash of arguments (prevents sensitive data in logs)
- Decision (ALLOW/DENY)
- Reason for decision
- Execution duration
- Error code (if applicable)

## Production Deployment Guidance

### Infrastructure Security

1. **Network Isolation**
   - Deploy in a private subnet
   - Use network policies to restrict egress
   - Place behind a reverse proxy for HTTP transport

2. **Container Security**
   - Run as non-root user
   - Use read-only filesystem where possible
   - Drop all capabilities except necessary ones

3. **Secrets Management**
   - Use environment variables or secret managers for credentials
   - Never commit secrets to version control
   - Rotate database credentials regularly

### Configuration Hardening

```yaml
# Production configuration example
mcp:
  server:
    stdio-enabled: false  # Disable if not needed
    http-enabled: true

  filesystem:
    enabled: true
    allowed-paths:
      - /app/data  # Specific application directory only
    max-file-size-bytes: 5242880  # 5MB limit

  http-fetch:
    enabled: true
    allowed-domains:
      - api.internal.company.com  # Internal APIs only
    max-response-size-bytes: 1048576
    timeout-seconds: 10

  postgres:
    enabled: true
    url: jdbc:postgresql://db.internal:5432/readonly_db
    username: mcp_readonly
    password: ${POSTGRES_PASSWORD}
    max-query-rows: 500
    query-timeout-seconds: 15

  policy:
    default-deny: true
    default-timeout-ms: 15000
    default-max-result-bytes: 524288  # 512KB

    allow-tools:
      - fs.readFile  # Only enable what's needed
      - db.query

    per-tool:
      fs.readFile:
        allow: true
        timeout-ms: 5000
        max-result-bytes: 1048576
        allowed-args:
          - path

      db.query:
        allow: true
        timeout-ms: 15000
        max-result-bytes: 524288
        allowed-args:
          - sql
```

### Monitoring and Alerting

1. **Audit Log Monitoring**
   - Forward audit logs to SIEM
   - Alert on DENY decisions
   - Alert on error codes
   - Monitor for unusual patterns

2. **Performance Monitoring**
   - Track invocation latency
   - Monitor timeout events
   - Track result size distributions

3. **Security Alerts**
   - Path traversal attempts
   - Blocked SQL keywords
   - Domain allowlist violations
   - Policy denials

### Access Control

1. **HTTP Transport Security**
   - Enable TLS (terminate at reverse proxy)
   - Implement authentication (add authentication filter)
   - Consider mutual TLS for service-to-service

2. **Database Access**
   - Use a dedicated read-only database user
   - Grant SELECT only on necessary tables
   - Use row-level security if available

3. **Filesystem Access**
   - Principle of least privilege for allowed paths
   - Consider chroot or container isolation
   - Regular audit of allowed paths

### Incident Response

1. **Logging Retention**
   - Retain audit logs for compliance period
   - Secure log storage
   - Immutable log destination if possible

2. **Kill Switch**
   - Ability to disable specific tools quickly
   - Ability to block specific patterns
   - Graceful shutdown capability

3. **Investigation**
   - Correlation ID in audit events (`invocationId`)
   - Request tracing through system
   - Arguments are hashed but original can be reconstructed if needed

## Security Checklist

### Pre-Deployment

- [ ] Review and minimize `allowed-paths`
- [ ] Review and minimize `allowed-domains`
- [ ] Set appropriate timeouts and size limits
- [ ] Configure TLS for HTTP transport
- [ ] Set up authentication if needed
- [ ] Configure audit log forwarding
- [ ] Test all deny scenarios
- [ ] Review database user permissions

### Post-Deployment

- [ ] Verify audit logs are being generated
- [ ] Confirm policy denials are logged
- [ ] Test alerting on security events
- [ ] Schedule regular access reviews
- [ ] Plan for credential rotation
- [ ] Document incident response procedures

## Vulnerability Disclosure

If you discover a security vulnerability, please report it to security@example.com. Do not create public issues for security vulnerabilities.

## Compliance Considerations

- **SOC 2**: Audit logging supports control evidence
- **HIPAA**: Ensure no PHI in tool arguments; arguments are hashed
- **GDPR**: Consider data residency for log storage
- **PCI DSS**: Restrict access to cardholder data paths/tables
