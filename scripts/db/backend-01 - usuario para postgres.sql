-- 1️⃣ Asegurar que el usuario existe
CREATE USER "portafolio-user" WITH PASSWORD 'Por7afoli02026';

-- 2️⃣ Asegurar que la base existe
CREATE DATABASE "personal-projects";

-- 3️⃣ Permitir que el usuario cree schemas (🔥 CLAVE 🔥)
GRANT CREATE ON DATABASE "personal-projects" TO "portafolio-user";

-- 4️⃣ Permitir conexión
GRANT CONNECT ON DATABASE "personal-projects" TO "portafolio-user";
