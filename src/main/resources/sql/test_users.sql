-- Usuarios de prueba, uno por rol. Contraseña de todos: admin123
-- Ejecutar DESPUÉS de haber arrancado la app una vez (RoleAndPermissionSeeder crea los roles).

INSERT IGNORE INTO tb_usuario (nombres, apellidos, correo, nombre_usuario, contrasena) VALUES
    ('Admin',    'Prueba', 'admin@sunat-service.local',    'admin',    '$2a$10$OXX7aKTLxfu7khVPZaD1secD3TnXvWftD/twl0H8l7z/LM8ZTziKm'),
    ('Contador', 'Prueba', 'contador@sunat-service.local', 'contador', '$2a$10$OXX7aKTLxfu7khVPZaD1secD3TnXvWftD/twl0H8l7z/LM8ZTziKm'),
    ('Vendedor', 'Prueba', 'vendedor@sunat-service.local', 'vendedor', '$2a$10$OXX7aKTLxfu7khVPZaD1secD3TnXvWftD/twl0H8l7z/LM8ZTziKm');

INSERT IGNORE INTO tb_usuarios_roles (id_usuario, id_rol)
SELECT u.id_usuario, r.id_rol FROM tb_usuario u
JOIN tb_rol r ON r.nombre = UPPER(u.nombre_usuario)
WHERE u.nombre_usuario IN ('admin', 'contador', 'vendedor');
