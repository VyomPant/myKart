-- PostgreSQL init script: creates 4 databases for myKart services
-- Runs once on container first boot

CREATE DATABASE auth_db;
CREATE DATABASE order_db;
CREATE DATABASE inventory_db;
CREATE DATABASE payment_db;

GRANT ALL PRIVILEGES ON DATABASE auth_db TO mykart;
GRANT ALL PRIVILEGES ON DATABASE order_db TO mykart;
GRANT ALL PRIVILEGES ON DATABASE inventory_db TO mykart;
GRANT ALL PRIVILEGES ON DATABASE payment_db TO mykart;
