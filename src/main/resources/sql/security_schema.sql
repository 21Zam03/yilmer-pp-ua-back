-- Tablas de seguridad (usuarios, roles, permisos). Ejecutar manualmente en sunat_service_db:
-- la app corre con ddl-auto: validate, así que no las crea sola.

CREATE TABLE IF NOT EXISTS tb_permiso (
    id_permiso INT AUTO_INCREMENT PRIMARY KEY,
    nombre     VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS tb_rol (
    id_rol INT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS tb_roles_permisos (
    id_rol     INT NOT NULL,
    id_permiso INT NOT NULL,
    PRIMARY KEY (id_rol, id_permiso),
    FOREIGN KEY (id_rol) REFERENCES tb_rol (id_rol),
    FOREIGN KEY (id_permiso) REFERENCES tb_permiso (id_permiso)
);

CREATE TABLE IF NOT EXISTS tb_usuario (
    id_usuario                     INT AUTO_INCREMENT PRIMARY KEY,
    nombres                        VARCHAR(255),
    apellidos                      VARCHAR(255),
    correo                         VARCHAR(50)  NOT NULL UNIQUE,
    nombre_usuario                 VARCHAR(255) NOT NULL UNIQUE,
    contrasena                     VARCHAR(100),
    activo                         BIT(1) NOT NULL DEFAULT 1,
    cuenta_expirada                BIT(1) NOT NULL DEFAULT 0,
    cuenta_bloqueada               BIT(1) NOT NULL DEFAULT 0,
    credenciales_expiradas         BIT(1) NOT NULL DEFAULT 0,
    reseteo_contrasena             BIT(1) NOT NULL DEFAULT 0,
    fecha_actualizacion_contrasena DATETIME(6)
);

CREATE TABLE IF NOT EXISTS tb_usuarios_roles (
    id_usuario INT NOT NULL,
    id_rol     INT NOT NULL,
    PRIMARY KEY (id_usuario, id_rol),
    FOREIGN KEY (id_usuario) REFERENCES tb_usuario (id_usuario),
    FOREIGN KEY (id_rol) REFERENCES tb_rol (id_rol)
);

-- Los roles y permisos los crea RoleAndPermissionSeeder al arrancar la app.
-- Usuarios de prueba: ver test_users.sql.
