Project: Pulse, an uptime monitoring service with public status pages.
Stack: Java 21, Spring Boot 3.3, Maven, PostgreSQL 16, Flyway, Spring Data JPA, Spring Security with JWT (jjwt), Apache HttpClient 5 with virtual threads and custom SSRF-safe DnsResolver, JavaMailSender, Testcontainers, JUnit 5.
Base package: dev.preethi.pulse
Rules: constructor injection only; request and response objects are Java records; no business logic in controllers; validate input with jakarta validation; all errors return JSON {timestamp,status,error,message,path}; no secrets in code, read them from environment variables; write tests for every service; do not add features I did not ask for.
After every task, list the files you changed and the command to run the tests.
Tables: users, monitors, check_results, incidents, alert_channels (details in the migrations).
