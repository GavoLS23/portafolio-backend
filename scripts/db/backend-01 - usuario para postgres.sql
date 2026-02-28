-- Crear usuario
CREATE USER "portafolio-user" WITH PASSWORD 'Por7afoli02026';

-- Crear esquema si no existe
CREATE SCHEMA IF NOT EXISTS portafolio;

-- Otorgar permisos
GRANT CONNECT ON DATABASE "personal-projects" TO "portafolio-user";
GRANT ALL ON SCHEMA portafolio TO "portafolio-user";
GRANT CREATE ON SCHEMA portafolio TO "portafolio-user";
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA portafolio TO "portafolio-user";
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA portafolio TO "portafolio-user";

-- Permisos futuros
ALTER DEFAULT PRIVILEGES IN SCHEMA portafolio
    GRANT ALL ON TABLES TO "portafolio-user";
ALTER DEFAULT PRIVILEGES IN SCHEMA portafolio
    GRANT ALL ON SEQUENCES TO "portafolio-user";

-- Hacerlo dueño
ALTER SCHEMA portafolio OWNER TO "portafolio-user";